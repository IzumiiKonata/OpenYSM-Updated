package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * 女仆 YSM 渲染态的客户端缓存，取代原版的 Forge capability provider。
 * <p>
 * 为什么不是 cardinal component：基准的 {@code MaidCapabilityProvider} 实现
 * {@code ICapabilityProvider}（非 Serializable）且带 {@code @OnlyIn(Dist.CLIENT)}——
 * 首次访问懒建、不落盘、不联网、随实体消亡。component 会把存档与同步语义强加进来。
 * <p>
 * 用弱键 map 而非 Fabric attachment：attachment 的默认语义偏向"实体数据"（可持久化、可同步），
 * 而这里要的恰是"渲染缓存"。弱键让条目在实体不可达后随 GC 消失，等价于 capability 随实体消亡。
 * <p>
 * 线程约束：只在渲染线程访问（渲染器与 debug overlay），故不加锁；如将来出现渲染线程外的
 * 调用点，必须改成同步容器而不是"看起来没炸就算了"。
 */
@Environment(EnvType.CLIENT)
public final class MaidRenderStore {
    private static final Map<EntityMaid, MaidAnimatable> CACHE = new WeakHashMap<>();

    private MaidRenderStore() {
    }

    /**
     * 取或建该女仆的渲染态。基准语义：provider 首次被访问时才 new，isActive 恒为 true。
     */
    public static MaidAnimatable getOrCreate(EntityMaid maid) {
        return CACHE.computeIfAbsent(maid, m -> new MaidAnimatable(m, true));
    }

    /**
     * 只读查询；非女仆一律 empty。
     * <p>
     * ⚠️ <b>「不存在」不是一个可依赖的状态判据</b>（2026-07-28 用户实测踩到）：基准把 capability 通过
     * {@code AttachCapabilitiesEvent} 挂在每一只客户端女仆身上，恒在场，所以基准里的
     * {@code isPresent()} 实质只是「这是客户端女仆」。本移植改成惰性缓存后，唯一创建时机
     * （TLM tick 事件）被 {@code isYsmModel()} 门住，于是「不存在」既可能是「不是女仆」，
     * 也可能是「还不是 YSM 模型的女仆」——拿它当门禁就会把后者一律拒掉（曾导致选择屏打不开）。
     * <p>
     * 因此：<b>凡在女仆可能还不是 YSM 模型时运行的路径，必须用 {@link #getOrCreate}</b>；
     * 本方法只用于「有就顺带用、没有就跳过」且不构成功能门禁的场合（如调试覆盖层）。
     */
    public static Optional<MaidAnimatable> get(Entity entity) {
        if (entity instanceof EntityMaid maid) {
            return Optional.ofNullable(CACHE.get(maid));
        }
        return Optional.empty();
    }

    /**
     * 退出世界时清空。弱键虽能自然回收，但退出世界后整批渲染态已无意义，
     * 立即清掉可避免旧世界的实体被 GC 前继续占着模型与纹理句柄。
     */
    public static void clear() {
        CACHE.clear();
    }
}
