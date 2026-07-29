package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

/**
 * 雕像/手办渲染态动画，移植自 OpenYSM 原版 MaidStatusAnimationPredicate。
 * 官方动画清单里的 {@code statue} 与 {@code garage_kit} 两项由本类负责。
 * 与 {@link MaidAnimationPredicate} 互补：那边只在 {@code renderState == ENTITY} 时工作。
 */
@Environment(EnvType.CLIENT)
public class MaidStatusAnimationPredicate implements IAnimationPredicate<MaidAnimatable> {
    public static final String[] RENDER_STATES = {"statue", "garage_kit"};

    @Override
    public PlayState predicate(AnimationEvent<MaidAnimatable> event, ExpressionEvaluator<?> evaluator) {
        EntityMaid entityMaid = event.getAnimatable().getEntity();
        if (entityMaid == null || event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (entityMaid.renderState == MaidRenderState.STATUE) {
            return IAnimationPredicate.playLoopAnimation(event, "statue");
        }
        if (entityMaid.renderState == MaidRenderState.GARAGE_KIT) {
            return IAnimationPredicate.playLoopAnimation(event, "garage_kit");
        }
        return PlayState.STOP;
    }
}
