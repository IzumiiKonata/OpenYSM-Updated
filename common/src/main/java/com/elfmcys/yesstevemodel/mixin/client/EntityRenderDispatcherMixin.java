package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.access.IEntityRenderDispatcher;
import com.elfmcys.yesstevemodel.client.renderer.CustomFishingHookRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomProjectileRenderer;
import com.elfmcys.yesstevemodel.client.renderer.CustomVehicleRenderer;
import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.client.renderer.VehicleRenderer;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin implements IEntityRenderDispatcher {

    @Override
    @Unique
    public Entity ysm$getEntityForState(EntityRenderState state) {
        return EntityRenderStateBindings.get(state);
    }

    @WrapWithCondition(
            method = "submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V"
            )
    )
    private boolean ysm$wrapEntityRendererSubmit(
            EntityRenderer<?, ?> renderer,
            EntityRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            CameraRenderState cameraState
    ) {
        if (!YesSteveModel.isAvailable()) {
            return true;
        }
        Entity entity = EntityRenderStateBindings.get(state);
        if (entity == null) {
            return true;
        }

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        int packedLight = state.lightCoords;

        RenderContext.enter(collector, cameraState);
        try {
            if (entity instanceof Projectile projectile) {
                if (!GeneralConfig.DISABLE_PROJECTILE_MODEL.get()) {
                    boolean callOriginal;
                    if (projectile instanceof FishingHook fishingHook) {
                        callOriginal = CustomFishingHookRenderer.tryRenderCustomHook(fishingHook, state, partialTick, poseStack, bufferSource, packedLight);
                    } else {
                        callOriginal = CustomProjectileRenderer.renderProjectile(projectile, state, partialTick, poseStack, bufferSource, packedLight);
                    }
                    return callOriginal;
                }
                return true;
            }
            if (!GeneralConfig.DISABLE_VEHICLE_MODEL.get()) {
                // 门禁必须在副作用之前：下面三步会把调用方备好的 render state 用 VehicleRenderer
                // 重新 extract 一遍，并在渲染中途拿共享 bufferSource 做即时渲染。而真正决定"要不要
                // 接管"的判据在 CustomVehicleRenderer.renderVehicle 内部（载具组件在场且模型就绪），
                // 此前无条件执行等于对**每一个实体**都覆写一次 state。
                // 世界渲染看不出问题（state 本就是那样提取的），但任何在 GUI 里渲染实体的第三方
                // ——TLM 的图标缓存、手办物品渲染等——其 state 是精心构造的，被覆写后模型渲染成全黑剪影。
                // 2026-07-28 用户实测：装上 YSM 后 TLM 的皮肤选择界面与创造栏手办页整片变黑；
                // 移除 YSM 即恢复。故把内部判据提到副作用之前，没有 YSM 载具模型的实体一概不碰。
                if (!CustomVehicleRenderer.hasReadyVehicleModel(entity)) {
                    return true;
                }
                VehicleRenderer vehicleRenderer = RendererManager.getVehicleRenderer();
                vehicleRenderer.extractRenderState(entity, state, partialTick);
                ModelPreviewRenderer.renderVehicleModel(entity, poseStack, partialTick);
                return CustomVehicleRenderer.renderVehicle(entity, state, entity.getRotationVector().x, partialTick, poseStack, bufferSource, packedLight);
            }
            return true;
        } finally {
            RenderContext.exit();
        }
    }
}
