package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

/**
 * 轮盘动画通道，移植自 OpenYSM 原版 MaidIdleAnimPredicate。
 * <p>
 * 名字沿用基准（叫 Idle 但实际负责轮盘额外动画），因为 fork/基准双方的读者都按此名找它。
 * 两条路径：模型选择界面的预览动画体走状态机；实机女仆走
 * {@code rouletteAnimPlaying/Dirty/rouletteAnim} 三元组——它们由 TLM 的
 * {@code SyncYsmMaidDataPackage} 从服务端同步而来（本项目已恢复该包的双向注册，见 TLM 侧 d604795e0）。
 * <p>
 * {@code hasModel()} 为真表示"轮盘选择刚变过"，此时先 {@code refreshModel()} 清脏标记再
 * {@code stopTransition()}——**顺序不能反**：不清标记会每帧都打断过渡，动画永远停在第一帧。
 */
@Environment(EnvType.CLIENT)
public class MaidIdleAnimPredicate implements IAnimationPredicate<MaidAnimatable> {
    @Override
    public PlayState predicate(AnimationEvent<MaidAnimatable> event, ExpressionEvaluator<?> evaluator) {
        MaidAnimatable animatable = event.getAnimatable();
        if (animatable instanceof IPreviewAnimatable previewAnimatable) {
            if (previewAnimatable.getAnimationStateMachine().hasAnimation()) {
                return IAnimationPredicate.playLoopAnimation(event, previewAnimatable.getAnimationStateMachine().getCurrentAnimation());
            }
            return PlayState.STOP;
        }
        if (animatable.isModelAvailable()) {
            if (animatable.hasModel()) {
                animatable.refreshModel();
                event.getController().stopTransition();
            }
            return IAnimationPredicate.predicate(event, animatable.getModelTextureId());
        }
        return PlayState.STOP;
    }
}
