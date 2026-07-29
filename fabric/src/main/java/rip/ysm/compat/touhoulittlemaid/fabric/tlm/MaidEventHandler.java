package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntityChair;
import com.github.tartaricacid.touhoulittlemaid.entity.item.EntitySit;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.item.ItemHakureiGohei;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;

/**
 * TLM 实体/物品判定，移植自 OpenYSM 原版（Forge 1.20.1）的 MaidEventHandler，编译目标为
 * Touhou Little Maid: Tsumugi（1.21.11 Fabric fork）。
 * <p>
 * 本类只保留纯判定。原版此类还承担渲染器注入（registerMaidRenderer →
 * {@code EntityMaidRenderer.YSM_ENTITY_MAID_RENDERER}）与三个事件监听的注册，
 * 它们依赖 MaidCapability/ModelAssembly 渲染链，随渲染链切片一并移植。
 * <p>
 * 类加载安全：本类 import 了 TLM 类，任何入口都必须位于
 * {@code TouhouLittleMaidCompatImpl.isLoaded()} 之后，TLM 未安装时不得触碰本类。
 */
public final class MaidEventHandler {
    private MaidEventHandler() {
    }

    public static boolean isMaid(Entity entity) {
        return entity instanceof EntityMaid;
    }

    public static boolean isYsmModelMaid(Entity entity) {
        // 基准写的是「capability 在场 && isYsmModel()」，但基准的 capability 挂在每只客户端女仆上、
        // 恒为真，故该条件实质只有 isYsmModel()。本移植的渲染态是惰性的，若照抄"在场"会引入
        // 基准没有的时序依赖（与开屏死锁同源，见 MaidClientSetup.registerScreenHandler）。
        return entity instanceof EntityMaid maid && maid.isYsmModel();
    }

    public static boolean isChair(Entity entity) {
        return entity instanceof EntityChair;
    }

    public static boolean isSit(Entity entity) {
        return entity instanceof EntitySit;
    }

    public static String getChairModelId(Entity entity) {
        if (entity instanceof EntityChair chair) {
            return chair.getModelId();
        }
        return StringPool.EMPTY;
    }

    public static boolean isMaidFishing(LivingEntity livingEntity) {
        return livingEntity instanceof EntityMaid maid && maid.fishing != null;
    }

    public static boolean isGohei(Item item) {
        return item instanceof ItemHakureiGohei;
    }
}
