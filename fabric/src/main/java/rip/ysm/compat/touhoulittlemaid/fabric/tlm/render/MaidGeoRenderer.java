package rip.ysm.compat.touhoulittlemaid.fabric.tlm.render;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.IGeoRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.model.provider.data.EntityModelData;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.state.EntityMaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.GeoLayerRenderer;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

import java.util.List;

/**
 * 女仆 YSM 模型渲染底座。TLM 侧把它注入 {@code EntityMaidRenderer.YSM_ENTITY_MAID_RENDERER}，
 * 女仆一旦切到 YSM 模型，TLM 就把整帧渲染全权移交本类。
 * <p>
 * <b>为什么不继承 fork 的 {@code GeoReplacedEntityRenderer}</b>：它已被收窄为
 * {@code <TEntity extends Player, …, S extends AvatarRenderState>} + {@code PlayerModel}，
 * 而 vanilla 的 {@code PlayerModel extends HumanoidModel<AvatarRenderState>}（javap 证）、
 * {@code LivingEntityRenderer<T,S,M extends EntityModel<? super S>>}——要让它吃女仆 render state
 * 就得连模型类型参数一起泛化成四参泛型，牵动 {@code CustomPlayerRenderer} 与 AvatarRenderer mixin。
 * 女仆是新增功能、玩家渲染是 fork 的主功能，**风险不对称**，故另建底座、宁可重复。
 * <p>
 * 代价是要自己写编排；机械面（{@code renderWithBone} / {@code renderWithBoneAndRenderType} /
 * {@code renderEarly} / {@code getRenderType} / {@code getRenderColor} / 渲染周期存取）
 * 全是 {@link IGeoRenderer} 的 default 方法，直接复用。
 * <p>
 * <b>名字牌不在本类职责内</b>：TLM 侧在移交前后自行 {@code submitNameTag}（TLM 提交 7ca21de41）。
 * 拴绳按基准同样不提交。
 */
@Environment(EnvType.CLIENT)
public class MaidGeoRenderer implements IGeoRenderer<MaidAnimatable>, IGeoEntityRenderer<EntityMaidRenderState> {
    /** TLM 侧的 Geo layer，由 TLM 在 initYsmModelRenderer 里交接过来 */
    private final List<GeoLayerRenderer<?, ?>> tlmLayerRenderers = new java.util.ArrayList<>();

    /** 与 fork 底座同款：用 .set 复用矩阵，避免每帧 new */
    private final Matrix4f dispatchedMat = new Matrix4f();
    private final Matrix4f renderEarlyMat = new Matrix4f();

    private MultiBufferSource rtb;
    private IRenderCycle currentModelRenderCycle = EModelRenderCycle.INITIAL;

    @Override
    public IGeoEntity getGeoEntity(EntityMaidRenderState state) {
        // TLM 契约：这里必须给出可用实例（TLM 随后就要 setYsmModel），故走 getOrCreate 而非 get
        EntityMaid maid = state.maid;
        return MaidRenderStore.getOrCreate(maid);
    }

    @Override
    public void addGeoLayerRenderer(GeoLayerRenderer<?, ?> layerRenderer) {
        this.tlmLayerRenderers.add(layerRenderer);
    }

    @Override
    public void geoRender(EntityMaidRenderState state, float entityYaw, float partialTick,
                          PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        EntityMaid maid = state.maid;
        if (maid == null) {
            return;
        }
        MaidAnimatable animatable = MaidRenderStore.getOrCreate(maid);

        // submit → immediate-mode 桥接，五步照 fork 自己的 ReplacePlayerRenderEvent:48-59：
        // 取 bufferSource → 存 collector/camera 进 ThreadLocal → 跑 immediate 渲染 → endBatch → exit。
        // 缺任何一步的后果：不 enter 则内部取不到 collector（名字牌等旁路失效）；不 endBatch 则本帧
        // 顶点留在 buffer 里下一帧才吐出（表现为模型延迟一帧/闪烁）；不 exit 则 ThreadLocal 泄漏到别的渲染。
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderContext.enter(collector, state.camera);
        try {
            setCurrentRTB(bufferSource);
            renderMaid(animatable, state, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            bufferSource.endBatch();
        } finally {
            RenderContext.exit();
        }
    }

    /**
     * 渲染主体，逐项对齐 fork 的 {@code GeoReplacedEntityRenderer.renderEntityWithTexture}。
     */
    private void renderMaid(MaidAnimatable animatable, EntityMaidRenderState state, float entityYaw, float partialTick,
                            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        EntityMaid maid = animatable.getEntity();
        if (maid == null) {
            return;
        }

        // 预览态（模型选择界面）要临时把实体朝向对齐到 render state，跑完必须复原——
        // 基准用 try-finally 保证异常也复原，勿改成顺序写法。
        // 见 MaidAnimationStates#vanilla：dev 重映射解析不出「别的 mod 的类继承 vanilla 成员」，
        // 故实体与 render state 两侧的 vanilla 成员都必须经 vanilla 静态类型访问。
        // 2026-07-28 实测第二次踩到，这次是 state.hasPose → method_62613，渲染时崩。
        LivingEntity vanillaMaid = maid;
        LivingEntityRenderState vanillaState = state;
        boolean syncRotationsForPreview = ModelPreviewRenderer.isPreview();
        float savedYBodyRot = 0.0f, savedYBodyRotO = 0.0f, savedYHeadRot = 0.0f, savedYHeadRotO = 0.0f;
        float savedYRot = 0.0f, savedYRotO = 0.0f, savedXRot = 0.0f, savedXRotO = 0.0f;
        if (syncRotationsForPreview) {
            savedYBodyRot = vanillaMaid.yBodyRot;
            savedYBodyRotO = vanillaMaid.yBodyRotO;
            savedYHeadRot = vanillaMaid.yHeadRot;
            savedYHeadRotO = vanillaMaid.yHeadRotO;
            savedYRot = vanillaMaid.getYRot();
            savedYRotO = vanillaMaid.yRotO;
            savedXRot = vanillaMaid.getXRot();
            savedXRotO = vanillaMaid.xRotO;

            float bodyRot = vanillaState.bodyRot;
            float headYaw = vanillaState.bodyRot + vanillaState.yRot;
            vanillaMaid.yBodyRot = bodyRot;
            vanillaMaid.yBodyRotO = bodyRot;
            vanillaMaid.yHeadRot = headYaw;
            vanillaMaid.yHeadRotO = headYaw;
            vanillaMaid.setYRot(headYaw);
            vanillaMaid.yRotO = headYaw;
            vanillaMaid.setXRot(vanillaState.xRot);
            vanillaMaid.xRotO = vanillaState.xRot;
        }

        AnimationEvent<?> event;
        try {
            event = animatable.processAnimation(partialTick);
        } finally {
            if (syncRotationsForPreview) {
                vanillaMaid.yBodyRot = savedYBodyRot;
                vanillaMaid.yBodyRotO = savedYBodyRotO;
                vanillaMaid.yHeadRot = savedYHeadRot;
                vanillaMaid.yHeadRotO = savedYHeadRotO;
                vanillaMaid.setYRot(savedYRot);
                vanillaMaid.yRotO = savedYRotO;
                vanillaMaid.setXRot(savedXRot);
                vanillaMaid.xRotO = savedXRotO;
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (event == null || minecraft.player == null) {
            return;
        }

        EntityModelData modelData = event.getModelData();
        this.dispatchedMat.set(poseStack.last().pose());
        setCurrentModelRenderCycle(EModelRenderCycle.INITIAL);

        poseStack.pushPose();
        // 睡眠时按床朝向把模型挪到床上（基准原式：眼高 -0.1 作为平移量）
        if (vanillaState.hasPose(Pose.SLEEPING)) {
            Direction bedOrientation = vanillaState.bedOrientation;
            if (bedOrientation != null) {
                float eyeHeight = vanillaMaid.getEyeHeight(Pose.STANDING) - 0.1f;
                poseStack.translate(-bedOrientation.getStepX() * eyeHeight, 0.0f, -bedOrientation.getStepZ() * eyeHeight);
            }
        }

        setupRotations(state, poseStack, modelData.lerpBodyRot, 1.0f);
        preRenderCallback(poseStack);
        poseStack.translate(0.0f, 0.01f, 0.0f);

        AnimatedGeoModel geoModel = animatable.getCurrentModel();
        int textureIndex = animatable.getTextureIndex();
        Identifier texture = animatable.getTextureLocation();
        boolean bodyVisible = !vanillaState.isInvisible;
        RenderType renderType = getRenderType(texture, bodyVisible,
                minecraft.shouldEntityAppearGlowing(vanillaMaid),
                geoModel.getGeoModel().isTranslucentTexture(textureIndex));

        boolean layersFirst = animatable.isRenderLayersFirst();
        Color color = getRenderColor(animatable, partialTick, poseStack, bufferSource, null, packedLight);
        int overlay = packOverlayCoords(state);

        // 两趟骨骼渲染（无 renderType 一趟 + 有 renderType 一趟），layer 趟的先后由模型属性决定
        renderWithBone(geoModel, animatable, partialTick, poseStack, bufferSource, null, packedLight, overlay,
                color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        if (layersFirst) {
            renderTlmLayers(state, poseStack, bufferSource, packedLight, event, modelData);
        }
        if (renderType != null) {
            renderWithBoneAndRenderType(geoModel, animatable, partialTick, renderType, poseStack, bufferSource,
                    textureIndex, null, packedLight, overlay,
                    color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
        }
        if (!layersFirst) {
            renderTlmLayers(state, poseStack, bufferSource, packedLight, event, modelData);
        }
        poseStack.popPose();
    }

    /**
     * TLM 交接过来的 Geo layer（手持物 / 头顶方块 / 背包 / 背部物品 / 背旗，共 5 个，
     * 由 TLM 的 {@code GeckoEntityMaidRenderer} 构造时 addLayer）。
     * <p>
     * <b>本方法有意不调用它们，原因不是省事：它们在 YSM 路径上无法运行。</b>
     * 这些 layer 的签名要 {@code GeckoMaidRenderData}，而内部走
     * {@code data.modelState.visitLocatorGroup(GeoLocatorType.BACKPACK, …)}——依赖的是
     * <b>TLM 自己那套 gecko 模型状态</b>。女仆切到 YSM 模型后，渲染的是 YSM 的模型，
     * TLM 的 modelState 根本不存在，传 null 会直接 NPE（2026-07-28 实测崩在
     * GeckoLayerMaidBackpack:24），造一个假的更是无从下手。
     * <p>
     * 基准（Forge 1.20.1）能做这件事，是因为它 {@code layerRenderer.copy(ysmRenderer)} 把 layer
     * 重绑到 YSM 渲染器，且当年的 layer 走 TLM 的 {@code IGeoEntity#getGeoModel()} →
     * {@code ILocationModel} 定位组接口（正是我们骨骼桥实现的那个）。
     * <b>TLM 移植到 1.21.11 时把 layer 改成用自己的 modelState，`ILocationModel` 在 TLM 里
     * 已成零消费者接口</b>——通道还在，两端却接不上了。
     * <p>
     * ⇒ 后果：YSM 模型女仆暂时不渲染 TLM 挂件（背包 / 手持物 / 背旗 / 头顶方块）。
     * 这是 TLM 侧的移植漂移，修在 TLM（让 layer 能消费 ILocationModel 定位组），不在此处兜底。
     */
    private void renderTlmLayers(EntityMaidRenderState state, PoseStack poseStack, MultiBufferSource bufferSource,
                                 int packedLight, AnimationEvent<?> event, EntityModelData modelData) {
        // 见方法注释：TLM 的 gecko layer 需要 TLM 自己的 modelState，YSM 路径上不存在。
    }

    /**
     * vanilla {@code LivingEntityRenderer.setupRotations} 的等价物（1.21.11 起它读 render state
     * 而非实体，故此处可纯用 state 复刻）。
     * <p>
     * <b>与 vanilla 的两处有意差异，是基准意图</b>：死亡旋转与旋风斩旋转都不施加。基准（Forge 1.20.1）
     * 靠临时把实体的 {@code deathTime} 归零、清 autoSpinAttack 标志来压制它们，让 YSM 自己的
     * {@code death} 动画负责表现。⚠️ 那套手法在 1.21.11 **已失效**（vanilla 改读 state，改实体没用），
     * fork 的玩家链照搬后同样失效——本类改为直接不写这两个分支，等价于基准的原意图。
     */
    private void setupRotations(LivingEntityRenderState state, PoseStack poseStack, float bodyRot, float scale) {
        float rot = bodyRot;
        if (state.isFullyFrozen) {
            rot += (float) (Math.cos(Mth.floor(state.ageInTicks) * 3.25f) * Math.PI * 0.4f);
        }
        boolean sleeping = state.hasPose(Pose.SLEEPING);
        if (!sleeping) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - rot));
        }
        if (sleeping) {
            Direction bedOrientation = state.bedOrientation;
            float sleepRot = bedOrientation != null ? sleepDirectionToRotation(bedOrientation) : rot;
            poseStack.mulPose(Axis.YP.rotationDegrees(sleepRot));
            poseStack.mulPose(Axis.ZP.rotationDegrees(FLIP_DEGREES));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0f));
        } else if (state.isUpsideDown) {
            poseStack.translate(0.0f, (state.boundingBoxHeight + 0.1f) / scale, 0.0f);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
        }
    }

    private static final float FLIP_DEGREES = 90.0f;

    private static float sleepDirectionToRotation(Direction direction) {
        return switch (direction) {
            case SOUTH -> 90.0f;
            case WEST -> 0.0f;
            case NORTH -> 270.0f;
            case EAST -> 180.0f;
            default -> 0.0f;
        };
    }

    /**
     * 受伤时的红色叠加。基准（1.20.1）自己算 {@code hurtTime > 0 || deathTime > 0}；
     * 1.21.11 已把这个判定预算进 render state 的 {@code hasRedOverlay}
     * （vanilla `LivingEntityRenderer.getOverlayCoords` 用的就是它），故直接用它——
     * 语义等价且不必再摸实体。
     */
    private static int packOverlayCoords(LivingEntityRenderState state) {
        return OverlayTexture.pack(OverlayTexture.u(0.0f), OverlayTexture.v(state.hasRedOverlay));
    }

    /** 基准留的扩展点，女仆侧无额外变换 */
    private void preRenderCallback(PoseStack poseStack) {
    }

    @Override
    public void renderEarly(MaidAnimatable animatable, PoseStack poseStack, float partialTick,
                            @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                            int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        // 与 fork 底座同款：用 .set 复用矩阵，避免每帧 new Matrix4f
        this.renderEarlyMat.set(poseStack.last().pose());
        IGeoRenderer.super.renderEarly(animatable, poseStack, partialTick, bufferSource, buffer,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public MultiBufferSource getCurrentRTB() {
        return this.rtb;
    }

    @Override
    public void setCurrentRTB(MultiBufferSource bufferSource) {
        this.rtb = bufferSource;
    }

    @Override
    public IRenderCycle getCurrentModelRenderCycle() {
        return this.currentModelRenderCycle;
    }

    @Override
    public void setCurrentModelRenderCycle(IRenderCycle cycle) {
        this.currentModelRenderCycle = cycle;
    }
}
