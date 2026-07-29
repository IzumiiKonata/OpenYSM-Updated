package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.client.animation.molang.TLMBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.context.IContext;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.variable.IValueEvaluator;
import com.github.tartaricacid.touhoulittlemaid.api.client.render.MaidRenderState;
import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.MaidGomokuAI;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.MaidGameRecordManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.function.Function;

/**
 * TLM 女仆状态 → molang 变量的真实绑定，移植自 OpenYSM 原版 MaidBinding（Forge 1.20.1）。
 * 与原版的仅有差异：注册表查询 ForgeRegistries.ITEMS → BuiltInRegistries.ITEM。
 * 16 个变量的 EntityMaid API 已逐一在 Tsumugi 1.21.11 树上核对存在。
 * 类加载安全约束同 {@link MaidEventHandler}。
 */
public final class MaidBinding {
    private MaidBinding() {
    }

    public static void registerBindings(TLMBinding binding) {
        binding.livingEntityVar("is_begging", createMaidEvaluable(EntityMaid::isBegging));
        binding.livingEntityVar("is_sitting", createMaidEvaluable(EntityMaid::isMaidInSittingPose));
        binding.livingEntityVar("has_backpack", createMaidEvaluable(EntityMaid::hasBackpack));
        binding.livingEntityVar("favorability_point", createMaidEvaluable(EntityMaid::getFavorability));
        binding.livingEntityVar("favorability_level", createMaidEvaluable(maid -> maid.getFavorabilityManager().getLevel()));
        binding.livingEntityVar("task_id", createMaidEvaluable(maid -> maid.getTask().getUid()));
        binding.livingEntityVar("schedule", createMaidEvaluable(maid -> maid.getSchedule().name().toLowerCase(Locale.ENGLISH)));
        binding.livingEntityVar("activity", createMaidEvaluable(maid -> maid.getScheduleDetail().getName()));
        binding.livingEntityVar("gomoku_win_count", createMaidEvaluable(maid -> maid.getGameRecordManager().getGomokuWinCount()));
        binding.livingEntityVar("gomoku_rank", createMaidEvaluable(MaidGomokuAI::getRank));
        binding.livingEntityVar("game_statue", createMaidEvaluable(MaidBinding::getMaidTask));
        binding.livingEntityVar("backpack_type", createMaidEvaluable(maid -> maid.getMaidBackpackType().getId().toString()));
        binding.livingEntityVar("is_entity", createMaidEvaluable(maid -> maid.renderState == MaidRenderState.ENTITY));
        binding.livingEntityVar("is_statue", createMaidEvaluable(maid -> maid.renderState == MaidRenderState.STATUE));
        binding.livingEntityVar("is_garage_kit", createMaidEvaluable(maid -> maid.renderState == MaidRenderState.GARAGE_KIT));
        binding.livingEntityVar("show_item", createMaidEvaluable(MaidBinding::getMaidSchedule));
    }

    @NotNull
    private static IValueEvaluator<Object, IContext<LivingEntity>> createMaidEvaluable(Function<EntityMaid, Object> function) {
        return ctx -> {
            if (ctx.entity() instanceof EntityMaid maid) {
                return function.apply(maid);
            }
            return 0;
        };
    }

    private static String getMaidTask(EntityMaid maid) {
        if (((LivingEntity) maid).getVehicle() instanceof EntitySit) {
            MaidGameRecordManager gameRecordManager = maid.getGameRecordManager();
            if (gameRecordManager.isWin()) {
                return "win";
            }
            if (gameRecordManager.isLost()) {
                return "lost";
            }
            return StringPool.EMPTY;
        }
        return StringPool.EMPTY;
    }

    private static String getMaidSchedule(EntityMaid entityMaid) {
        ItemStack backpackShowItem = entityMaid.getBackpackShowItem();
        if (backpackShowItem.isEmpty()) {
            return StringPool.EMPTY;
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(backpackShowItem.getItem());
        return key.toString();
    }
}
