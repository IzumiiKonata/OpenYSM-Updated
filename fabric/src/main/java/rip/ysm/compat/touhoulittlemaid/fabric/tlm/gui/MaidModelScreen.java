package rip.ysm.compat.touhoulittlemaid.fabric.tlm.gui;

import com.elfmcys.yesstevemodel.client.ClientModelManager;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import com.elfmcys.yesstevemodel.client.gui.ModelInfoScreen;
import com.elfmcys.yesstevemodel.client.gui.ModelMetadataPresenter;
import com.elfmcys.yesstevemodel.client.gui.PlayerModelScreen;
import com.elfmcys.yesstevemodel.client.gui.button.ModelButton;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.resource.models.Metadata;
import com.elfmcys.yesstevemodel.util.FileTypeUtil;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidAnimatable;
import rip.ysm.compat.touhoulittlemaid.fabric.tlm.MaidRenderStore;

import java.util.List;
import java.util.Objects;

/**
 * 模型选择屏（女仆版），移植自 OpenYSM 原版 TouhouMaidModelScreen。
 * TLM 侧女仆界面的按钮触发 {@code OpenYsmMaidScreenEvent}，由 {@code MaidClientSetup} 接到并打开本屏。
 * <p>
 * 四个覆写点各自把「谁的模型」从本地玩家换成这只女仆：格子、材质屏、模型信息屏、左侧预览。
 * <p>
 * <b>预览用的是女仆实体本体</b>（`renderEntityInInventoryFollowsMouse` 直接喂 maid），
 * 因此左栏里看到的就是渲染底座真实渲染的结果——这也让本屏顺带成为渲染链的最快验证入口。
 * 1.21.11 的该方法签名比基准多了 x1/y1 与 yOffset，此处照 fork 现行 PlayerModelScreen 的调法。
 */
@Environment(EnvType.CLIENT)
public class MaidModelScreen extends PlayerModelScreen {
    private final EntityMaid maid;

    public MaidModelScreen(EntityMaid maid) {
        this.maid = maid;
    }

    @Override
    public ModelButton createModelButton(int x, int y, boolean isAuthLocked, PlayerPreviewEntity previewEntity,
                                         ModelAssembly modelAssembly) {
        return new MaidModelButton(x, y, isAuthLocked, previewEntity, modelAssembly, this.maid);
    }

    @Override
    public Screen createTextureScreen(PlayerModelScreen other, String modelId, ModelAssembly modelAssembly) {
        return new MaidTextureScreen(other, modelId, resolveAssembly(modelAssembly), this.maid);
    }

    @Override
    public Screen createModelInfoScreen(PlayerModelScreen other, ModelAssembly modelAssembly) {
        return new ModelInfoScreen(other, resolveAssembly(modelAssembly));
    }

    /**
     * 基准语义：优先用女仆当前渲染态里的 assembly，没有才回落到调用方给的那份。
     * 这样打开材质/信息屏时展示的是女仆正在用的模型，而不是列表里被选中的那个。
     */
    private ModelAssembly resolveAssembly(ModelAssembly fallback) {
        // getOrCreate 而非 get：本屏在女仆尚未是 YSM 模型时就会被打开（那正是它存在的目的），
        // 那时惰性渲染态还没建立。基准里 capability 恒在场，故无此区分。
        ModelAssembly current = MaidRenderStore.getOrCreate(this.maid).getModelAssembly();
        return Objects.requireNonNullElse(current, fallback);
    }

    @Override
    public void renderModelPreview(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(guiGraphics,
                this.guiLeft + 5, this.guiTop + 29, this.guiLeft + 130, this.guiTop + 200,
                70, 0.0625F, mouseX, mouseY, this.maid);

        MaidAnimatable animatable = MaidRenderStore.getOrCreate(this.maid);
        {
            List<FormattedCharSequence> lines = this.font.split(FormattedText.of(
                    ClientModelManager.getModelContext(animatable.getModelId()).map(context -> {
                        Metadata metadata = context.getModelData().getExtraInfo();
                        if (metadata != null) {
                            return ModelMetadataPresenter.getLocalizedModelString(context, "metadata.name", metadata.getName());
                        }
                        return StringPool.EMPTY;
                    }).filter(StringUtils::isNoneBlank)
                            .orElse(FileTypeUtil.getNameWithoutArchiveExtension(animatable.getModelId()))), 125);

            int lineY = this.guiTop + 205;
            for (FormattedCharSequence line : lines) {
                guiGraphics.drawString(this.font, line,
                        this.guiLeft + ((135 - this.font.width(line)) / 2), lineY, 15986656);
                lineY += 10;
            }
        }
    }
}
