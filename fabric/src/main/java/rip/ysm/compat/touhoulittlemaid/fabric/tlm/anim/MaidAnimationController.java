package rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim;

import com.elfmcys.yesstevemodel.client.animation.StopAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.condition.ConditionArmor;
import com.elfmcys.yesstevemodel.client.animation.predicate.ArmorPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.InteractionHandAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.ItemHoldAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.LivingMovementAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.MainHandHoldPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.NamedAnimationPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.OffHandHoldPredicate;
import com.elfmcys.yesstevemodel.client.animation.predicate.OffhandAttackAnimationPredicate;
import com.elfmcys.yesstevemodel.client.model.AnimationDataProvider;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.model.PlayerModelBundle;
import com.elfmcys.yesstevemodel.client.model.processor.ArmorSlotProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ControllerSlotBinder;
import com.elfmcys.yesstevemodel.client.model.processor.ModelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.NamedModelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ParallelProcessor;
import com.elfmcys.yesstevemodel.client.model.processor.ProcessorPipeline;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.Animation;
import com.elfmcys.yesstevemodel.geckolib3.core.builder.AnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.CompositeAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.IAnimationController;
import com.elfmcys.yesstevemodel.geckolib3.core.controller.PredicateBasedController;
import it.unimi.dsi.fastutil.objects.Object2ReferenceMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.EquipmentSlot;
import org.apache.commons.lang3.function.TriFunction;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * 女仆动画控制器装配表，移植自 OpenYSM 原版 MaidAnimationController。
 * <p>
 * 与 fork 现行的 {@code PlayerAnimationController} 是孪生结构（控制器名、顺序、过渡时长逐项对齐），
 * 差别只在三处：{@code main} 槽用 {@link MaidAnimationPredicate} 而非玩家的 AnimationManager ·
 * 多出 {@code misc}（棋局/祈求）与 {@code statue}（雕像/手办）两个具名控制器 ·
 * {@code cap} 槽用 {@link MaidIdleAnimPredicate}（轮盘）。
 * <p>
 * <b>前缀不对称是基准语义，勿"统一"</b>：具名控制器用 {@code maid.} 前缀，其余全用 {@code player.}
 * ——因为女仆复用玩家模型的动画入口表，模型作者写的键是 {@code player.main} 等；
 * 只有女仆专属的两个（misc/statue）才走 {@code maid.} 前缀。
 * <p>
 * 基准里 TACZ 的 {@code fire} 控制器按 {@code ItemUseAnimationPredicate.isLoaded()} 条件注册；
 * 该 compat 在本 fork 属永久归档，故整条省去——等价于 TACZ 未安装。
 * 同理基准没有玩家侧的 CarryOn / gui_hover / gui_focus 三项，女仆也不需要。
 */
@Environment(EnvType.CLIENT)
public final class MaidAnimationController {
    private static final String PLAYER_PREFIX = "player";
    private static final String MAID_PREFIX = "maid";

    private static final ProcessorPipeline<MaidAnimatable, PlayerModelBundle> REGISTRY = new ProcessorPipeline<>();

    private MaidAnimationController() {
    }

    public static Consumer<MaidAnimatable> buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        if (REGISTRY.isEmpty()) {
            registerControllers();
        }
        return REGISTRY.buildAll(modelBundle, resourceBundle);
    }

    private static void registerControllers() {
        registerParallelController("pre_parallel", (key, animatable, linkedName) ->
                new CompositeAnimationController(animatable, key, 0.0f,
                        linkedName != null ? new NamedAnimationPredicate(linkedName) : StopAnimationPredicate.INSTANCE));

        registerController("vehicle", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new LivingMovementAnimationPredicate()));

        registerSlotController("pre_main", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerController("main", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new MaidAnimationPredicate()));

        registerSlotController("post_main", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerSlotController("pre_hold", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerController("hold_offhand", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new OffHandHoldPredicate()));

        registerController("hold_mainhand", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new MainHandHoldPredicate()));

        registerSlotController("post_hold", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerSlotController("pre_swing", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerController("swing", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new ItemHoldAnimationPredicate()));

        registerSlotController("post_swing", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerSlotController("pre_use", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerController("use", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new InteractionHandAnimationPredicate()));

        registerSlotController("post_use", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new StopAnimationPredicate()));

        registerNamedController("misc", MaidGameStateAnimationPredicate.GAME_STATE_ANIMATIONS, true, (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new MaidGameStateAnimationPredicate()));

        registerController("passenger", (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.1f, new OffhandAttackAnimationPredicate()));

        registerController("cap", (key, animatable) ->
                new PredicateBasedController(animatable, key, 0.0f, new MaidIdleAnimPredicate()));

        registerParallelController("parallel", (key, animatable, linkedName) ->
                new CompositeAnimationController(animatable, key, 0.0f,
                        linkedName != null ? new NamedAnimationPredicate(linkedName) : StopAnimationPredicate.INSTANCE, true));

        registerArmorController("armor", (key, animatable, equipmentSlot) ->
                new CompositeAnimationController(animatable, key, 0.0f, new ArmorPredicate(equipmentSlot)));

        registerNamedController("statue", MaidStatusAnimationPredicate.RENDER_STATES, true, (key, animatable) ->
                new CompositeAnimationController(animatable, key, 0.0f, new MaidStatusAnimationPredicate()));
    }

    private static void registerController(String controllerName,
                                           BiFunction<String, MaidAnimatable, IAnimationController<MaidAnimatable>> controllerFactory) {
        String controllerKey = String.format("%s.%s", PLAYER_PREFIX, controllerName);
        ModelProcessor<MaidAnimatable, PlayerModelBundle> processor =
                (modelBundle, resourceBundle) -> (animatable, consumer) ->
                        consumer.accept(controllerFactory.apply(controllerKey, animatable));
        REGISTRY.register(processor);
    }

    private static void registerSlotController(String slotName,
                                               BiFunction<String, MaidAnimatable, IAnimationController<MaidAnimatable>> controllerFactory) {
        REGISTRY.register(new ControllerSlotBinder<>(PLAYER_PREFIX, slotName, MaidAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerNamedController(String slotName, String[] requiredAnimations, boolean checkAnimationEntries,
                                                BiFunction<String, MaidAnimatable, IAnimationController<MaidAnimatable>> controllerFactory) {
        REGISTRY.register(new NamedModelProcessor<>(MAID_PREFIX, slotName, requiredAnimations, checkAnimationEntries,
                MaidAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerParallelController(String slotName,
                                                   TriFunction<String, MaidAnimatable, String, IAnimationController<MaidAnimatable>> controllerFactory) {
        REGISTRY.register(new ParallelProcessor<>(PLAYER_PREFIX, slotName, true, MaidAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static void registerArmorController(String category,
                                                TriFunction<String, MaidAnimatable, EquipmentSlot, IAnimationController<MaidAnimatable>> controllerFactory) {
        REGISTRY.register(new ArmorSlotProcessor<>(PLAYER_PREFIX, category, MaidAnimationDataProvider.INSTANCE, controllerFactory));
    }

    private static final class MaidAnimationDataProvider implements AnimationDataProvider<PlayerModelBundle> {
        static final MaidAnimationDataProvider INSTANCE = new MaidAnimationDataProvider();

        private MaidAnimationDataProvider() {
        }

        @Override
        public Object2ReferenceMap<String, AnimationController> getAnimationEntries(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getAnimationEntries();
        }

        @Override
        public Object2ReferenceMap<String, Animation> getAnimations(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getMainAnimations();
        }

        @Override
        public ConditionArmor getConditionArmor(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
            return modelBundle.getConditionManager().getArmor();
        }
    }
}
