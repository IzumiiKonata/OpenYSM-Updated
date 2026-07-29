package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.geckolib3.core.processor.IBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.core.processor.ILocationBone;
import com.github.tartaricacid.touhoulittlemaid.geckolib3.geo.animated.ILocationModel;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * YSM 骨骼/模型 → TLM 定位契约（{@code ILocationBone} / {@code ILocationModel}）的适配桥，
 * 移植自 OpenYSM 原版 TouhouMaidBoneProcessor。
 * <p>
 * 它是女仆能挂 TLM 侧物件的根据：TLM 靠这些定位组把手持物、腰挂、背包、头部附加件
 * 摆到 YSM 模型的对应骨骼上。common 侧的 {@code AnimatedGeoModel.getTouhouMaidData()} 与
 * {@code AnimatedGeoBone.getTouhouMaidBone()} 已在调用本桥（经 {@code TouhouMaidBoneProcessor}
 * 的 {@code @ExpectPlatform}），二者都带惰性缓存，故本桥只在首次访问时构造一次。
 * <p>
 * ⚠️ <b>基准里的左右互换按原样保留</b>：{@code extraLeftHandBones()} 取的是
 * {@code model.rightHandChain()}，{@code extraRightHandBones()} 取的是 {@code model.leftHandChains()}。
 * 看着像上游写反了，但模型作者的动画是照现行行为调的——纪律「行为对基准」，不在移植里"顺手修"。
 * 若将来确证是 bug，须与上游一起改，并作为破坏性变更公告。
 * <p>
 * 另一处基准语义：{@code backpackBones()} 为空时回落到 {@code elytraBones()}——
 * 让没有专门背包定位组的模型复用鞘翅定位组。
 */
@Environment(EnvType.CLIENT)
public final class MaidBoneBridge {
    private MaidBoneBridge() {
    }

    public static ILocationBone createLocationBone(final AnimatedGeoBone bone) {
        return new ILocationBone() {
            @Override
            public float getRotationX() {
                return bone.getRotationX();
            }

            @Override
            public float getRotationY() {
                return bone.getRotationY();
            }

            @Override
            public float getRotationZ() {
                return bone.getRotationZ();
            }

            @Override
            public float getPositionX() {
                return bone.getPositionX();
            }

            @Override
            public float getPositionY() {
                return bone.getPositionY();
            }

            @Override
            public float getPositionZ() {
                return bone.getPositionZ();
            }

            @Override
            public float getScaleX() {
                return bone.getScaleX();
            }

            @Override
            public float getScaleY() {
                return bone.getScaleY();
            }

            @Override
            public float getScaleZ() {
                return bone.getScaleZ();
            }

            @Override
            public float getPivotX() {
                return bone.getPivotX();
            }

            @Override
            public float getPivotY() {
                return bone.getPivotY();
            }

            @Override
            public float getPivotZ() {
                return bone.getPivotZ();
            }
        };
    }

    public static ILocationModel createLocationModel(final AnimatedGeoModel model) {
        return new ILocationModel() {
            @Override
            public List<ILocationBone> leftHandBones() {
                return toTlmBones(model.leftHandBones());
            }

            @Override
            public List<List<? extends ILocationBone>> extraLeftHandBones() {
                // 基准原样：这里取 rightHandChain()，见类注释
                ReferenceArrayList<List<? extends ILocationBone>> chains = new ReferenceArrayList<>();
                model.rightHandChain().forEach(list -> chains.add(toTlmBones(list)));
                return chains;
            }

            @Override
            public List<ILocationBone> rightHandBones() {
                return toTlmBones(model.rightHandBones());
            }

            @Override
            public List<List<? extends ILocationBone>> extraRightHandBones() {
                // 基准原样：这里取 leftHandChains()，见类注释
                ReferenceArrayList<List<? extends ILocationBone>> chains = new ReferenceArrayList<>();
                model.leftHandChains().forEach(list -> chains.add(toTlmBones(list)));
                return chains;
            }

            @Override
            public List<ILocationBone> leftWaistBones() {
                return toTlmBones(model.leftWaistBones());
            }

            @Override
            public List<ILocationBone> rightWaistBones() {
                return toTlmBones(model.rightWaistBones());
            }

            @Override
            public List<ILocationBone> backpackBones() {
                List<IBone> backpack = model.backpackBones();
                if (backpack.isEmpty()) {
                    return toTlmBones(model.elytraBones());
                }
                return toTlmBones(backpack);
            }

            @Override
            public List<ILocationBone> tacPistolBones() {
                return toTlmBones(model.tacPistolBones());
            }

            @Override
            public List<ILocationBone> tacRifleBones() {
                return toTlmBones(model.tacRifleBones());
            }

            @Override
            public List<ILocationBone> headBones() {
                return toTlmBones(model.headBones());
            }
        };
    }

    private static List<ILocationBone> toTlmBones(List<IBone> bones) {
        return bones.stream()
                .map(bone -> (ILocationBone) ((AnimatedGeoBone) bone).<ILocationBone>getTouhouMaidBone())
                .toList();
    }
}
