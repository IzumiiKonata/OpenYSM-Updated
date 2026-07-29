package rip.ysm.compat.touhoulittlemaid.fabric.tlm.gui;

import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.PlayerModelScreen;
import com.elfmcys.yesstevemodel.client.gui.PlayerTextureScreen;
import com.elfmcys.yesstevemodel.client.gui.button.TextureButton;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

/**
 * 材质选择屏（女仆版），移植自 OpenYSM 原版 TouhouMaidTextureScreen。
 * <p>
 * <b>与基准的签名差异是 1.21.11 GUI 改制导致的，不是行为改动</b>：基准的
 * {@code renderTexturePreview(GuiGraphics, int scissorX, int scissorY, int w, int h, float)} +
 * 手工 {@code RenderSystem.enableScissor} + {@code renderEntityPreview(...)} 这一套，在 fork 上
 * 已改成 {@code renderTexturePreview(GuiGraphics, float)} + {@code submitTexturePreview(...)}
 * （后者自带 scissor）。故此处照 fork 现行的 {@code PlayerTextureScreen.renderTexturePreview}
 * 抄框架，只把「取谁的模型」换成女仆的渲染态。预览区矩形与锚点数值与玩家版一致。
 */
@Environment(EnvType.CLIENT)
public class MaidTextureScreen extends PlayerTextureScreen {
    private final EntityMaid maid;

    public MaidTextureScreen(PlayerModelScreen modelScreen, String modelId, ModelAssembly modelAssembly, EntityMaid maid) {
        super(modelScreen, modelId, modelAssembly);
        this.maid = maid;
    }

    @Override
    public TextureButton createTextureButton(int x, int y, PlayerPreviewEntity previewEntity, int textureIndex) {
        return new MaidTextureButton(x, y, previewEntity, this.maid, textureIndex, this.renderContext);
    }

    @Override
    public void renderTexturePreview(GuiGraphics guiGraphics, float partialTick) {
        // getOrCreate 同 MaidModelScreen：本屏可在女仆还不是 YSM 模型时被打开
        MaidAnimatable animatable = MaidRenderStore.getOrCreate(this.maid);
        {
            this.modelHolder.initModelWithTexture(animatable.getModelId(), animatable.getCurrentTextureName());

            int x0 = this.guiLeft + 93;
            int y0 = this.guiTop;
            int x1 = this.guiLeft + 299;
            int y1 = this.guiTop + 235;
            float anchorX = this.guiLeft + 149.5f + 40.0f + this.offsetX;
            float anchorY = this.guiTop + 117.5f + 80.0f + this.offsetY;

            ModelPreviewRenderer.submitTexturePreview(
                    guiGraphics,
                    x0, y0, x1, y1,
                    anchorX, anchorY,
                    this.zoom,
                    this.pitch,
                    this.yaw,
                    this.modelHolder,
                    this.showGround,
                    partialTick
            );
        }
    }
}
