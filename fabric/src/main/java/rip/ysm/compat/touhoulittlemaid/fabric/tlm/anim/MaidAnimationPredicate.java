package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.AnimationState;
import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

/**
 * 女仆主动画选择器，移植自 OpenYSM 原版 {@code client.animation.predicate.TouhouMaidAnimationPredicate}。
 * fork 在移植到 1.21.11 时删掉了这个类，本轮补回。
 * <p>
 * 五级优先级桶（{@code Priority.HIGHEST..LOWEST} = 0..4），按桶序取第一个命中的状态。
 * <p>
 * 三条提前退出（基准原样，每条都有理由，勿"精简"）：
 * ① 预览动画体（模型选择界面）不走实体动画；
 * ② {@code renderState != ENTITY} 即雕像/手办，交给 {@link MaidStatusAnimationPredicate}；
 * ③ <b>骑乘中一律 STOP</b>——坐乘动画由 {@code MaidInteractionAnimHandler} 负责，
 * 若此处不让位，走/跑/跳等状态会与坐乘动画抢同一个控制器。
 * <p>
 * 基准在命中后还会依次问 SlashBlade 与 TACZ 兼容层要不要改写动画；这两个 compat
 * 在本 fork 属永久归档范围，故直接播放选中动画（行为等价于两者均未安装）。
 */
@Environment(EnvType.CLIENT)
public class MaidAnimationPredicate implements IAnimationPredicate<MaidAnimatable> {
    private static final int PRIORITY_BUCKETS = 5;

    @SuppressWarnings("unchecked")
    private static final ReferenceArrayList<AnimationState<EntityMaid, MaidAnimatable>>[] PRIORITY_HANDLERS =
            new ReferenceArrayList[PRIORITY_BUCKETS];

    static {
        for (int i = 0; i < PRIORITY_HANDLERS.length; i++) {
            PRIORITY_HANDLERS[i] = new ReferenceArrayList<>(6);
        }
    }

    public static void registerHandler(AnimationState<EntityMaid, MaidAnimatable> animationState) {
        PRIORITY_HANDLERS[animationState.getPriority()].add(animationState);
    }

    @Override
    public PlayState predicate(AnimationEvent<MaidAnimatable> event, ExpressionEvaluator<?> evaluator) {
        EntityMaid entity = event.getAnimatable().getEntity();
        if (entity == null || event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (entity.renderState != MaidRenderState.ENTITY) {
            return PlayState.STOP;
        }
        Entity vehicle = ((LivingEntity) entity).getVehicle();
        if (vehicle != null && vehicle.isAlive()) {
            return PlayState.STOP;
        }
        for (int priority = 0; priority < PRIORITY_BUCKETS; priority++) {
            for (AnimationState<EntityMaid, MaidAnimatable> animationState : PRIORITY_HANDLERS[priority]) {
                if (animationState.getPredicate().test(entity, event)) {
                    String name = animationState.getAnimationName();
                    ILoopType loopType = animationState.getLoopType();
                    return IAnimationPredicate.playAnimationWithLoop(event, name, loopType);
                }
            }
        }
        return PlayState.STOP;
    }
}
