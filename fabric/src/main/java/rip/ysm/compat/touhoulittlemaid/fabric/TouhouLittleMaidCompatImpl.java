package rip.ysm.compat.touhoulittlemaid.fabric;

import com.elfmcys.yesstevemodel.client.animation.molang.TLMBinding;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.client.model.ModelResourceBundle;
import com.elfmcys.yesstevemodel.client.model.PlayerModelBundle;
import com.elfmcys.yesstevemodel.geckolib3.core.enums.PlayState;
import com.elfmcys.yesstevemodel.geckolib3.core.event.predicate.AnimationEvent;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimationRoulette;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidBinding;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidEventHandler;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidInteractionAnimHandler;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.anim.MaidAnimationController;

/**
 * Touhou Little Maid 兼容的 Fabric 平台实现，编译目标 = Tsumugi fork（1.21.11 Fabric，libs/ 内 jar）。
 * <p>
 * ⚠️ 命名陷阱（源自原版的伪装式命名，语义以本注释为准）：
 * {@code isMaidRideable} = 「是 YSM 模型女仆」；{@code isSimplePlanesEntity} = 「是 TLM 椅子」；
 * {@code isImmersiveAircraftEntity} = 「是 TLM 坐垫（EntitySit）」；{@code isMaidItem} = 「是博丽御币」；
 * {@code getMaidEntityId} = 「椅子模型 id」；{@code isMaidSitting} = 「女仆正在钓鱼」；
 * {@code isMaidChatAvailable / openMaidChat} = 「动画轮盘可开 / 打开动画轮盘」。
 * <p>
 * 类加载安全：本类不 import 任何 TLM 类型；TLM 类型只存在于 {@code tlm} 子包，
 * 且只在 {@link #isLoaded()} 为真的分支里被触碰。
 * <p>
 * 本类入口已全部接通真实现（渲染链切片二完成）。仍与原版有差集的只有 {@code TouhouMaidCompatImpl}
 * 那侧的投掷物/载具模型继承与 {@code MaidInteractionEvent}，以及基准本身就是空实现的 applyFeedback。
 */
public final class TouhouLittleMaidCompatImpl {
    private static final String MOD_ID = "touhou_little_maid";
    private static final boolean IS_LOADED = FabricLoader.getInstance().isModLoaded(MOD_ID);

    private TouhouLittleMaidCompatImpl() {
    }

    public static boolean isLoaded() {
        return IS_LOADED;
    }

    /**
     * 客户端装配入口，由 fabric client initializer 调用。
     * <p>
     * 本方法自身不 import 任何 TLM 类型；真正触碰 TLM 的 {@code MaidClientSetup} 只在
     * {@code isLoaded()} 为真的分支里被引用，故 TLM 未安装时该类不会被加载。
     */
    public static void initClient() {
        if (isLoaded()) {
            rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidClientSetup.init();
        }
    }

    public static boolean isMaidEntity(Entity entity) {
        return isLoaded() && MaidEventHandler.isMaid(entity);
    }

    public static boolean isMaidRideable(Entity entity) {
        return isLoaded() && MaidEventHandler.isYsmModelMaid(entity);
    }

    public static boolean isSimplePlanesEntity(Entity entity) {
        return isLoaded() && MaidEventHandler.isChair(entity);
    }

    public static boolean isImmersiveAircraftEntity(Entity entity) {
        return isLoaded() && MaidEventHandler.isSit(entity);
    }

    public static boolean isMaidItem(Item item) {
        return isLoaded() && MaidEventHandler.isGohei(item);
    }

    public static String getMaidEntityId(Entity entity) {
        return isLoaded() ? MaidEventHandler.getChairModelId(entity) : StringPool.EMPTY;
    }

    public static boolean isMaidSitting(LivingEntity livingEntity) {
        return isLoaded() && MaidEventHandler.isMaidFishing(livingEntity);
    }

    public static void registerMaidAnimStates(TLMBinding tlmBinding) {
        if (isLoaded()) {
            MaidBinding.registerBindings(tlmBinding);
        } else {
            registerDummyBindings(tlmBinding);
        }
    }

    private static void registerDummyBindings(TLMBinding tlmBinding) {
        tlmBinding.livingEntityVar("is_begging", ctx -> false);
        tlmBinding.livingEntityVar("is_sitting", ctx -> false);
        tlmBinding.livingEntityVar("has_backpack", ctx -> false);
        tlmBinding.livingEntityVar("favorability_point", ctx -> 0);
        tlmBinding.livingEntityVar("favorability_level", ctx -> 0);
        tlmBinding.livingEntityVar("task_id", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("schedule", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("activity", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("gomoku_win_count", ctx -> 0);
        tlmBinding.livingEntityVar("gomoku_rank", ctx -> 1);
        tlmBinding.livingEntityVar("game_statue", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("backpack_type", ctx -> StringPool.EMPTY);
        tlmBinding.livingEntityVar("is_entity", ctx -> true);
        tlmBinding.livingEntityVar("is_statue", ctx -> false);
        tlmBinding.livingEntityVar("is_garage_kit", ctx -> false);
        tlmBinding.livingEntityVar("show_item", ctx -> StringPool.EMPTY);
    }

    public static PlayState handleMaidInteraction(AnimationEvent<LivingAnimatable<?>> event, LivingEntity livingEntity, Entity entity) {
        if (isLoaded()) {
            return MaidInteractionAnimHandler.handleMaidInteractionAnim(event, livingEntity, entity);
        }
        return null;
    }

    /**
     * 名不符实（见类注释的语义对照表）：实为「动画轮盘是否可开」。
     * 调用方是轮盘快捷键，它先问这里、否则回落玩家轮盘——**判宽了会抢掉玩家的轮盘键**。
     */
    public static boolean isMaidChatAvailable() {
        return isLoaded() && MaidAnimationRoulette.canOpenRoulette();
    }

    /** 实为「打开动画轮盘」，与上一方法必须同批开启 */
    public static void openMaidChat() {
        if (isLoaded()) {
            MaidAnimationRoulette.openRouletteScreen();
        }
    }

    public static Object buildControllers(PlayerModelBundle modelBundle, ModelResourceBundle resourceBundle) {
        if (!isLoaded()) {
            return null;
        }
        // 返回 Object 是 common 侧签名要求（它不能 import 女仆类型）；实际类型是
        // Consumer<MaidAnimatable>，由 MaidAnimatable.registerAnimationControllers 转型后 accept。
        return MaidAnimationController.buildControllers(modelBundle, resourceBundle);
    }
}
