package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.AnimationState;
import com.elfmcys.yesstevemodel.client.animation.Priority;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

import java.util.function.BiPredicate;

/**
 * 女仆主动画状态表，移植自 OpenYSM 原版 {@code MaidAnimation}。
 * 13 个状态与优先级逐字对齐基准；顺序即注册顺序，同优先级桶内先注册者先命中。
 */
@Environment(EnvType.CLIENT)
public final class MaidAnimationStates {
    private static final float MOVEMENT_THRESHOLD = 0.05f;

    private MaidAnimationStates() {
    }

    public static void register() {
        registerState("death", ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.HIGHEST, (maid, event) -> vanilla(maid).isDeadOrDying());
        registerLoopState("sleep", Priority.HIGHEST, (maid, event) -> vanilla(maid).getPose() == Pose.SLEEPING);
        registerLoopState("swim", Priority.HIGHEST, (maid, event) -> vanilla(maid).isSwimming());
        registerLoopState("ladder_up", Priority.HIGHEST, (maid, event) -> vanilla(maid).onClimbable() && getVerticalSpeed(maid) > 0.0f);
        registerLoopState("ladder_stillness", Priority.HIGHEST, (maid, event) -> vanilla(maid).onClimbable() && getVerticalSpeed(maid) == 0.0f);
        registerLoopState("ladder_down", Priority.HIGHEST, (maid, event) -> vanilla(maid).onClimbable() && getVerticalSpeed(maid) < 0.0f);
        registerLoopState("sit", Priority.HIGH, (maid, event) -> maid.isMaidInSittingPose());
        registerLoopState("swim_stand", Priority.NORMAL, (maid, event) -> vanilla(maid).isInWater() && !vanilla(maid).onGround());
        registerState("attacked", ILoopType.EDefaultLoopTypes.PLAY_ONCE, Priority.NORMAL, (maid, event) -> vanilla(maid).hurtTime > 0);
        registerLoopState("jump", Priority.NORMAL, (maid, event) -> !vanilla(maid).onGround() && !vanilla(maid).isInWater());
        registerLoopState("run", Priority.LOW, (maid, event) -> vanilla(maid).onGround() && vanilla(maid).isSprinting());
        registerLoopState("walk", Priority.LOW, (maid, event) -> vanilla(maid).onGround() && event.getLimbSwingAmount() > MOVEMENT_THRESHOLD);
        registerLoopState("idle", Priority.LOWEST, (maid, event) -> true);
    }

    private static void registerState(String name, ILoopType loopType, int priority,
                                      BiPredicate<EntityMaid, AnimationEvent<MaidAnimatable>> predicate) {
        MaidAnimationPredicate.registerHandler(new AnimationState<>(name, loopType, priority, predicate));
    }

    private static void registerLoopState(String name, int priority,
                                          BiPredicate<EntityMaid, AnimationEvent<MaidAnimatable>> predicate) {
        registerState(name, ILoopType.EDefaultLoopTypes.LOOP, priority, predicate);
    }

    /**
     * 把女仆当成 vanilla {@code LivingEntity} 看。
     * <p>
     * <b>不是多余的转型</b>：Fabric 在开发环境重映射 mod jar 时，若调用点的 owner 是别的 mod 的类
     * （这里是 {@code EntityMaid}）而方法其实继承自 vanilla，重映射器解析不出继承链就会把
     * intermediary 名原样留下，运行期炸 {@code NoSuchMethodError: EntityMaid.method_5854()}
     * （= {@code getVehicle}）。经 vanilla 静态类型调用后 owner 就是 vanilla 类，dev 与生产都对。
     * 2026-07-28 实测踩到：模型选择屏一开就刷这个异常，点击也因此发不出包。
     */
    private static LivingEntity vanilla(EntityMaid maid) {
        return maid;
    }

    /**
     * 基准原式：{@code 20 * (position().y - yo)}。注意用的是 {@code yo}（上一 tick 的 y）而非
     * {@code yOld}，且乘 20 换成每秒速度——爬梯三态靠它的正负零分档，勿改。
     */
    private static float getVerticalSpeed(LivingEntity livingEntity) {
        return 20.0f * ((float) (livingEntity.position().y - livingEntity.yo));
    }
}
