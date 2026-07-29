package rip.ysm.compat.touhoulittlemaid.fabric;

import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidBoneBridge;

/**
 * common 侧 {@code TouhouMaidBoneProcessor} 的 Fabric 实现。
 * <p>
 * 调用点是 {@code AnimatedGeoModel.getTouhouMaidData()} 与 {@code AnimatedGeoBone.getTouhouMaidBone()}，
 * 二者都带惰性缓存字段，因此这里每个模型/骨骼只会被调一次。
 * 返回 {@code Object} 是必须的——common 不能 import TLM 类型，由调用方泛型转型。
 * <p>
 * TLM 未安装时**这两个方法不会被调**：调用方只在渲染 YSM 女仆模型的路径上取定位数据，
 * 而该路径的前提是 TLM 在场。仍保留守卫以防将来出现新调用点。
 */
public final class TouhouMaidBoneProcessorImpl {

    private TouhouMaidBoneProcessorImpl() {
    }

    public static Object createLocationBone(AnimatedGeoBone bone) {
        if (!TouhouLittleMaidCompatImpl.isLoaded()) {
            return null;
        }
        return MaidBoneBridge.createLocationBone(bone);
    }

    public static Object createLocationModel(AnimatedGeoModel model) {
        if (!TouhouLittleMaidCompatImpl.isLoaded()) {
            return null;
        }
        return MaidBoneBridge.createLocationModel(model);
    }
}
