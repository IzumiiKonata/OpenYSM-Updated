package rip.ysm.compat.touhoulittlemaid.fabric.tlm.gui;

import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.button.ModelButton;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.util.ComponentUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.network.message.YsmMaidModelPackage;
import net.minecraft.world.entity.Entity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

/**
 * 模型选择格子（女仆版），移植自 OpenYSM 原版 TouhouMaidModelButton。
 * <p>
 * 点击后做两件事，顺序照基准：先改本地渲染态（立即出画面），再发 C2S 让服务端落库。
 * 基准走 Forge 的 {@code NetworkHandler.CHANNEL.sendToServer(new YsmMaidModelMessage(...))}；
 * 本移植走 TLM 的 Fabric payload {@code YsmMaidModelPackage}（TLM 已恢复其 C2S 注册，见 d604795e0——
 * <b>没有那次恢复，这里 send 会直接崩网络线程并踢玩家</b>）。
 */
@Environment(EnvType.CLIENT)
public class MaidModelButton extends ModelButton {
    private final EntityMaid maid;

    public MaidModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity previewEntity,
                           ModelAssembly modelAssembly, EntityMaid maid) {
        super(x, y, isAuthLocked, previewEntity, modelAssembly);
        this.maid = maid;
    }

    // 1.21.11 起 vanilla Button.onPress 带 InputWithModifiers 参数（基准 1.20.1 是无参），签名照 fork 现行
    @Override
    public void onPress(InputWithModifiers input) {
        // 星标（未授权）模型不可选，基准同款前置返回
        if (this.isStarred) {
            return;
        }
        String modelId = this.modelIdHolder.getModelId();
        String textureName = this.modelIdHolder.getCurrentTextureName();
        Component displayName = ComponentUtil.getDisplayName(this.renderContext, modelId);

        // getOrCreate：点这个格子的时刻女仆通常还不是 YSM 模型，正是要在此把她变成 YSM 模型
        MaidRenderStore.getOrCreate(this.maid).setYsmModel(modelId, textureName);
        ClientPlayNetworking.send(new YsmMaidModelPackage(((Entity) this.maid).getId(), modelId, textureName, displayName));
    }
}
