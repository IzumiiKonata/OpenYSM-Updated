package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.IAnimationPredicate;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidGameRecordManager;
import net.minecraft.world.entity.LivingEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

/**
 * 棋局结果与祈求动画，移植自 OpenYSM 原版 MaidGameStateAnimationPredicate。
 * 官方动画清单里的 {@code game_win} / {@code game_lost} / {@code beg} 三项由本类负责。
 * <p>
 * 胜负只在女仆坐于 {@code EntitySit}（棋盘/桌前坐垫）时判定——离座即不再播放结果动画，
 * 这是基准语义。{@code beg} 与坐乘无关（玩家持蛋糕靠近即触发）。
 */
@Environment(EnvType.CLIENT)
public class MaidGameStateAnimationPredicate implements IAnimationPredicate<MaidAnimatable> {
    public static final String[] GAME_STATE_ANIMATIONS = {"game_win", "game_lost", "beg"};

    @Override
    public PlayState predicate(AnimationEvent<MaidAnimatable> event, ExpressionEvaluator<?> evaluator) {
        EntityMaid maid = event.getAnimatable().getEntity();
        if (maid == null || event.getAnimatable() instanceof IPreviewAnimatable) {
            return PlayState.STOP;
        }
        if (((LivingEntity) maid).getVehicle() instanceof EntitySit) {
            MaidGameRecordManager gameRecordManager = maid.getGameRecordManager();
            if (gameRecordManager.isWin()) {
                return IAnimationPredicate.playLoopAnimation(event, "game_win");
            }
            if (gameRecordManager.isLost()) {
                return IAnimationPredicate.playLoopAnimation(event, "game_lost");
            }
        }
        if (maid.isBegging()) {
            return IAnimationPredicate.playLoopAnimation(event, "beg");
        }
        return PlayState.STOP;
    }
}
