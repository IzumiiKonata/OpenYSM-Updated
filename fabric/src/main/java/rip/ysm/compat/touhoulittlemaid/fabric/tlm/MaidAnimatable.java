package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.client.entity.GeoEntity;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.molang.runtime.Struct;
import com.github.tartaricacid.touhoulittlemaid.api.entity.IMaid;
import com.github.tartaricacid.touhoulittlemaid.client.resource.pojo.MaidModelInfo;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.IGeoEntity;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 单只女仆的 YSM 渲染态，移植自 OpenYSM 原版的 {@code MaidCapability}。
 * <p>
 * 改名理由：原版靠 Forge capability 承载它，故叫 Capability；本移植查证基准后确认它
 * <b>零持久化、零同步、纯客户端、随实体消亡</b>（原版 Provider 实现的是 {@code ICapabilityProvider}
 * 而非 {@code ICapabilitySerializable}，且两者都带 {@code @OnlyIn(Dist.CLIENT)}），
 * 因此在 Fabric 侧不该做成 cardinal component（那会凭空添上存档与同步语义），
 * 而是由 {@link MaidRenderStore} 做客户端 per-entity 缓存——名字随之改为如实描述其角色。
 * <p>
 * 它同时是 TLM 契约 {@code IGeoEntity} 的实现方：我们注入 TLM 的女仆渲染器通过该接口
 * 设置 YSM 模型、推送 roaming 变量并取回 {@code ILocationModel}。
 */
@Environment(EnvType.CLIENT)
public class MaidAnimatable extends LivingAnimatable<EntityMaid> implements IGeoEntity {
    private MaidModelInfo maidModelInfo;

    public MaidAnimatable(EntityMaid entityMaid, boolean isActive) {
        super(entityMaid, isActive);
        this.maidModelInfo = new MaidModelInfo();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerAnimationControllers() {
        ((Consumer) getModelAssembly().getAnimationBundle().getMaidControllerInstaller()).accept(this);
    }

    @Override
    @NotNull
    public GeoEntity.ModelWrapper buildRenderShape(ModelAssembly modelAssembly, boolean isActive) {
        return new TexturedModelWrapper(modelAssembly, isActive, true, true, 600);
    }

    @Override
    public MaidFrameState createPositionTracker(EntityMaid entityMaid) {
        return new MaidFrameState(entityMaid);
    }

    @Override
    public MaidFrameState getPositionTracker() {
        return (MaidFrameState) super.getPositionTracker();
    }

    // 以下四个方法的语义由基准定义：轮盘动画的脏标记与播放状态寄存在 EntityMaid 自己的字段上
    // （rouletteAnimDirty / rouletteAnimPlaying / rouletteAnim），由 TLM 的 SyncYsmMaidDataPackage 同步。
    public boolean hasModel() {
        return this.entity.rouletteAnimDirty;
    }

    public void refreshModel() {
        this.entity.rouletteAnimDirty = false;
    }

    public boolean isModelAvailable() {
        return this.entity.rouletteAnimPlaying;
    }

    public String getModelTextureId() {
        return this.entity.rouletteAnim;
    }

    public void setMolangVars(Object2FloatOpenHashMap<String> molangVars) {
        // 基准为空实现：女仆侧的 molang 变量走 TLMBinding 直读 EntityMaid，不经此通道
    }

    @Override
    public void updateRoamingVars(Object2FloatOpenHashMap<String> roamingVars) {
        // 基准为空实现，与 setMolangVars 同因
    }

    public Struct getPropertyContainer() {
        // 基准返回 null：女仆没有服务端下发的自定义属性容器
        return null;
    }

    @Override
    public void setupAnim(float seekTime, boolean isFirstPerson) {
        super.setupAnim(seekTime, isFirstPerson);
        getEvaluationContext().setRoamingProperties(getPropertyContainer());
    }

    @Override
    public IMaid getMaid() {
        return this.entity;
    }

    @Override
    public MaidModelInfo getMaidInfo() {
        return this.maidModelInfo;
    }

    @Override
    public void setMaidInfo(MaidModelInfo maidModelInfo) {
        if (this.maidModelInfo != maidModelInfo) {
            this.maidModelInfo = maidModelInfo;
        }
    }

    @Override
    public ILocationModel getGeoModel() {
        return getCurrentModel().getTouhouMaidData();
    }

    @Override
    public void setYsmModel(String modelId, String texture) {
        initModelWithTexture(modelId, texture);
    }
}
