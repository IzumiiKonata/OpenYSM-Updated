package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.client.entity.LivingEntityFrameState;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;

/**
 * 女仆位置/帧状态追踪器，逐字移植自 OpenYSM 原版（Forge 1.20.1）的 MaidFrameState。
 * 基类 {@code LivingEntityFrameState<T extends LivingEntity>} 与基准逐字相同。
 * 类加载安全约束同 {@link MaidEventHandler}。
 */
public class MaidFrameState extends LivingEntityFrameState<EntityMaid> {
    public MaidFrameState(EntityMaid entityMaid) {
        super(entityMaid);
    }

    @Override
    public void reset() {
        super.reset();
    }
}
