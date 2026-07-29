package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.OpenYsmMaidScreenEvent;
import com.github.tartaricacid.touhoulittlemaid.compat.ysm.event.YsmMaidClientTickEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.TamableAnimal;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim.MaidAnimationStates;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.gui.MaidModelScreen;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.render.MaidGeoRenderer;

import java.util.UUID;

/**
 * TLM 兼容的客户端装配，对应 OpenYSM 原版 {@code TouhouLittleMaidCompat.init()} 里的
 * {@code registerMaidRenderer()} 与 {@code MaidAnimation.registerAnimationStates()} 两步。
 * <p>
 * <b>时机是本类唯一的技术风险</b>：TLM 的 {@code EntityMaidRenderer} 在构造时读取静态钩子
 * {@code YSM_ENTITY_MAID_RENDERER}（TLM 那边的注释写明「不能用事件初始化，会受先后顺序影响」），
 * 而渲染器由 {@code EntityRenderDispatcher} 在客户端启动后期与每次资源重载时构造。
 * 因此赋值必须发生在 client initializer 阶段（本类的调用点），**晚于它就是静默失效**：
 * 编译、打包、启动全部正常，只是女仆永远不切 YSM 模型。
 * 本项目已为同类静默失效面吃过八次教训（mixins.json / entrypoint / brain memory 等），
 * 故此处附一条启动日志作为「钩子确实装上了」的唯一可见证据。
 * <p>
 * 每次资源重载都会重新构造渲染器并再取一次钩子，故本方法只需在客户端初始化时跑一次。
 */
@Environment(EnvType.CLIENT)
public final class MaidClientSetup {
    @Nullable
    private static MaidGeoRenderer maidRenderer;

    private MaidClientSetup() {
    }

    public static void init() {
        // context 参数有意忽略：本渲染底座不继承 LivingEntityRenderer，无需 vanilla 渲染上下文
        EntityMaidRenderer.YSM_ENTITY_MAID_RENDERER = context -> {
            maidRenderer = new MaidGeoRenderer();
            return maidRenderer;
        };
        MaidAnimationStates.register();
        registerTickHandler();
        registerScreenHandler();
        YesSteveModel.LOGGER.info("[YSM-TLM] 女仆渲染钩子已装载，动画状态 / tick / 模型选择屏均已接线");
    }

    /**
     * 接 TLM 的「打开 YSM 模型选择屏」事件——TLM 女仆界面里那个按钮
     * （{@code AbstractMaidContainerGui:290}）只负责触发事件，接收方在这里；
     * 没有本处接线，按钮点了没反应，而且玩家将无从给女仆选 YSM 模型（整条功能不可达）。
     * <p>
     * <b>不得用「渲染态已存在」当前置</b>（用户 2026-07-28 实测：按钮点了没反应）。
     * 基准写的是「capability 已存在」，但基准把 capability 通过 {@code AttachCapabilitiesEvent}
     * 挂在**每一只客户端女仆**身上，所以那个条件恒为真、实质等于「这是客户端女仆」。
     * 本移植改用惰性缓存，而唯一的创建时机（TLM 的 tick 事件）在 {@code EntityMaid:592} 被
     * {@code isYsmModel()} 门住——于是普通女仆永远没有渲染态，前置永远不满足，
     * 玩家根本无法进入选择屏去把她变成 YSM 模型：鸡生蛋死锁。
     */
    private static void registerScreenHandler() {
        OpenYsmMaidScreenEvent.CALLBACK.register(event ->
                Minecraft.getInstance().setScreen(new MaidModelScreen(event.getMaid())));
    }

    /**
     * 接 TLM 的女仆客户端 tick 事件（TLM 在 {@code EntityMaid} tick 里对 YSM 模型女仆触发）。
     * <p>
     * <b>它的作用是"确保渲染态存在"，不是"推进动画"</b>——这点容易看错：基准的
     * {@code MaidClientTickEvent.tickMaidModel} 方法体是 {@code ifPresent(cap -> {})}，
     * 空 lambda，真正起作用的是 {@code getCapability} 的**惰性新建副作用**。
     * 我们的等价物就是 {@code getOrCreate}。
     * <p>
     * 所有者过滤同基准：只为本地玩家自己的女仆预建；别人的女仆等首次渲染时由渲染器新建。
     */
    private static void registerTickHandler() {
        YsmMaidClientTickEvent.CALLBACK.register(event -> {
            LocalPlayer localPlayer = Minecraft.getInstance().player;
            if (localPlayer == null) {
                return;
            }
            EntityMaid maid = event.getMaid();
            // 基准写 localPlayer.getUUID().equals(maid.getOwnerUUID())，但 getOwnerUUID 已被
            // vanilla 1.21.11 删除（TLM 的 getOwner() 注释专门记了这点）。改按属主引用取裸 UUID
            // 直接比较——与基准语义完全一致，且不经实体解析（客户端上实体解析可能失败）。
            EntityReference<LivingEntity> ownerRef = ((TamableAnimal) maid).getOwnerReference();
            UUID ownerUuid = ownerRef != null ? ownerRef.getUUID() : null;
            if (localPlayer.getUUID().equals(ownerUuid)) {
                MaidRenderStore.getOrCreate(maid);
            }
        });
    }

    /**
     * 最近一次被 TLM 构造出来的女仆渲染器；TLM 未构造过则为 null。
     * 对应基准的 {@code MaidEventHandler.getMaidRenderer()}，供后续轮盘/调试路径取用。
     */
    @Nullable
    public static MaidGeoRenderer getMaidRenderer() {
        return maidRenderer;
    }
}
