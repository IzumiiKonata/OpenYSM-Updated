package rip.ysm.compat.touhoulittlemaid.fabric;

import net.minecraft.world.entity.Entity;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

import java.util.Optional;

/**
 * common 侧 {@code MaidCapabilityBridge} 的 Fabric 实现（消费方：{@code AnimationDebugOverlay}）。
 * <p>
 * 返回类型必须保持 {@code Optional<Object>}——common 拿到后自行 cast 成 {@code GeoEntity<?>}，
 * 因为 common 不能 import TLM 类型。只读查询、不新建（见 {@code MaidRenderStore#get} 注释）。
 */
public final class MaidCapabilityBridgeImpl {

    private MaidCapabilityBridgeImpl() {
    }

    public static Optional<Object> get(Entity entity) {
        if (!TouhouLittleMaidCompatImpl.isLoaded()) {
            return Optional.empty();
        }
        return MaidRenderStore.get(entity).map(state -> state);
    }
}
