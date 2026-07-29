package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.client.gui.AnimationRouletteScreen;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.TamableAnimal;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 女仆动画轮盘，移植自 OpenYSM 原版 MaidAnimationRoulette。
 * <p>
 * 触发路径：轮盘快捷键（{@code AnimationRouletteKey}）先问 {@code isMaidChatAvailable()}，
 * 命中则开女仆轮盘，否则回落玩家自己的轮盘。所以判据必须严格——判宽了会**抢掉玩家的轮盘键**。
 * <p>
 * <b>不需要女仆专用屏</b>：fork 的 {@link AnimationRouletteScreen} 构造已泛化到
 * {@code AnimatableEntity<?>}，{@link MaidAnimatable} 直接可用。基准当年也是复用同一个屏。
 */
@Environment(EnvType.CLIENT)
public final class MaidAnimationRoulette {
    private MaidAnimationRoulette() {
    }

    /**
     * 三个条件全中才算可开：准星指着的是女仆 · 她已切到 YSM 模型 · 且属主是本地玩家。
     * 基准原样——尤其属主判定不能省，否则能给别人的女仆放动画。
     */
    public static boolean canOpenRoulette() {
        EntityMaid maid = lookedAtOwnedYsmMaid();
        return maid != null;
    }

    public static void openRouletteScreen() {
        EntityMaid maid = lookedAtOwnedYsmMaid();
        if (maid == null) {
            return;
        }
        MaidRenderStore.get(maid).ifPresent(animatable -> {
            ModelAssembly modelAssembly = animatable.getModelAssembly();
            if (modelAssembly == null
                    || modelAssembly.getModelData().getModelProperties().getExtraAnimation().isEmpty()) {
                // 该模型没有额外动画，开了也是空轮盘——基准同样静默返回
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen == null) {
                minecraft.setScreen(new AnimationRouletteScreen(animatable.getModelId(), modelAssembly, animatable));
            } else if (minecraft.screen instanceof AnimationRouletteScreen) {
                // 再按一次关掉（基准的开关式行为）
                minecraft.setScreen(null);
            }
        });
    }

    @Nullable
    private static EntityMaid lookedAtOwnedYsmMaid() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) {
            return null;
        }
        HitResult hitResult = minecraft.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHitResult)) {
            return null;
        }
        Entity entity = entityHitResult.getEntity();
        if (!(entity instanceof EntityMaid maid) || !maid.isYsmModel()) {
            return null;
        }
        // getOwnerUUID 已被 vanilla 1.21.11 删除，改按属主引用取裸 UUID（与 MaidClientSetup 同源写法）
        EntityReference<LivingEntity> ownerRef = ((TamableAnimal) maid).getOwnerReference();
        UUID ownerUuid = ownerRef != null ? ownerRef.getUUID() : null;
        return localPlayer.getUUID().equals(ownerUuid) ? maid : null;
    }
}
