package rip.ysm.compat.touhoulittlemaid.fabric;

import com.elfmcys.yesstevemodel.network.message.FeedbackData;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidEventHandler;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidModelHandler;

/**
 * TLM 兼容第二入口（服务端/通用侧）的 Fabric 平台实现。
 * 原版（Forge 1.20.1）的其余四个入口全部依赖 capability 链
 * （TouhouMaidModelHandler：投掷物模型继承 / 轮盘动画激活 / 反馈应用 / molang 执行）
 * 与 MaidInteractionEvent 事件注册，随渲染链切片移植；落地前保持 no-op。
 * 类加载安全约束同 {@link TouhouLittleMaidCompatImpl}。
 */
public final class TouhouMaidCompatImpl {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean IS_LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private TouhouMaidCompatImpl() {
    }

    public static boolean isLoaded() {
        return IS_LOADED;
    }

    public static void init() {
        // 渲染链切片开启：MaidInteractionEvent 的 Fabric 事件映射
    }

    public static boolean isMaidEntity(Entity entity) {
        return isLoaded() && MaidEventHandler.isMaid(entity);
    }

    public static void handleProjectileOwner(Projectile projectile, Entity entity) {
        // 渲染链切片开启：TouhouMaidModelHandler.applyProjectileModelFromMaid（需 projectile_model 组件）
    }

    public static void registerAnimationRoulette(Entity entity, String classify, int index) {
        if (isLoaded()) {
            MaidModelHandler.activateRouletteAnimation(entity, classify, index);
        }
    }

    /**
     * <b>基准本身就是空实现</b>——原版 {@code TouhouMaidModelHandler.handleMaidFeedback} 的方法体
     * 只有一个空 if（{@code if (!(entity instanceof EntityMaid) || !isYsmModel()) { }}），
     * 没有任何语句。故此处保持 no-op 即与基准行为等价，**不是待办**。
     */
    public static void applyFeedback(Entity entity, FeedbackData message) {
    }

    @Environment(EnvType.CLIENT)
    public static void playMaidAnimation(Entity entity, String expression) {
        if (isLoaded()) {
            MaidModelHandler.executeMaidMolang(entity, expression);
        }
    }
}
