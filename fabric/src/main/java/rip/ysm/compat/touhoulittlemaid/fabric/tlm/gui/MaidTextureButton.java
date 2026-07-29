package rip.ysm.compat.touhoulittlemaid.fabric.tlm.gui;

import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.button.TextureButton;
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
import org.jetbrains.annotations.Nullable;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

/**
 * 材质选择格子（女仆版），移植自 OpenYSM 原版 TouhouMaidTextureButton。
 * <p>
 * <b>只记 id 不持实体</b>：基准在此处 new 了一只临时 EntityMaid 当预览载体，只为拿到
 * {@code setYsmModel} 的落点；真女仆只留 {@code getId()}（字段名 index）。本移植去掉那只临时实体
 * ——它在基准里唯一的用途是被 setYsmModel 写一遍然后丢掉，纯属无用副作用；
 * 预览由 {@link MaidTextureScreen} 的 {@code modelHolder}（PlayerPreviewEntity）负责，与它无关。
 * 保留的是**真正有效果的两件事**：写本地渲染态 + 发 C2S。
 */
@Environment(EnvType.CLIENT)
public class MaidTextureButton extends TextureButton {
    private final int maidId;

    @Nullable
    private final String modelId;
    @Nullable
    private final String textureName;
    @Nullable
    private final Component displayName;

    public MaidTextureButton(int x, int y, PlayerPreviewEntity previewEntity, EntityMaid maid,
                             int textureIndex, ModelAssembly modelAssembly) {
        super(x, y, previewEntity, modelAssembly);
        this.maidId = ((Entity) maid).getId();

        // getOrCreate：材质页可在女仆还不是 YSM 模型时打开，此时惰性渲染态尚未建立
        var animatable = MaidRenderStore.getOrCreate(maid);
        ModelAssembly assembly = animatable.getModelAssembly();
        this.modelId = animatable.getModelId();
        this.displayName = ComponentUtil.getDisplayName(assembly, this.modelId);
        this.textureName = assembly.getAnimationBundle().getTextures().getKeyAt(textureIndex);
        previewEntity.initModelWithTexture(this.modelId, this.textureName);
    }

    // 签名同 MaidModelButton：1.21.11 的 Button.onPress 带 InputWithModifiers
    @Override
    public void onPress(InputWithModifiers input) {
        if (this.modelId == null || this.textureName == null || this.displayName == null) {
            return;
        }
        ClientPlayNetworking.send(new YsmMaidModelPackage(this.maidId, this.modelId, this.textureName, this.displayName));
    }
}
