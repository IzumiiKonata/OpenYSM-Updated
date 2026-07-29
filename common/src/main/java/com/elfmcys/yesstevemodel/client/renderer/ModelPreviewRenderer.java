package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.VehicleCapability;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import com.elfmcys.yesstevemodel.client.animation.AnimationTracker;
import com.elfmcys.yesstevemodel.client.entity.LivingAnimatable;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.processor.IBone;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.elfmcys.yesstevemodel.client.entity.IPreviewAnimatable;
import com.elfmcys.yesstevemodel.util.AnimatableCacheUtil;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.NonNullList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.concurrent.ExecutionException;
import com.mojang.math.Axis;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;

public final class ModelPreviewRenderer {

    private static boolean isPreviewMode = false;

    private static boolean isExtraPlayerMode = false;

    private static boolean isFirstPersonMode = false;

    public static void setPreviewMode(boolean previewMode) {
        isPreviewMode = previewMode;
    }

    public static boolean isPreview() {
        return isPreviewMode;
    }

    public static void setExtraPlayerMode(boolean extraPlayerMode) {
        isExtraPlayerMode = extraPlayerMode;
    }

    public static boolean isExtraPlayer() {
        return isExtraPlayerMode;
    }

    public static void setFirstPersonMode(boolean firstPersonMode) {
        isFirstPersonMode = firstPersonMode;
    }

    public static boolean isFirstPerson() {
        return isFirstPersonMode || OculusCompat.isPBRActive() || FirstPersonCompat.isFirstPersonActive();
    }

    public static boolean isFirstPersonOnRenderThread() {
        RenderSystem.assertOnRenderThread();
        return isFirstPersonMode && !FirstPersonCompat.isFirstPersonActive();
    }

    public static void renderVehicleModel(Entity entity, PoseStack poseStack, float partialTick) {
        Entity vehicle = entity.getVehicle();
        if (vehicle != null) {
            VehicleCapability.get(vehicle).ifPresent(cap -> {
                int index;
                AnimatedGeoModel model;
                List<IBone> list;
                if (!cap.isModelInitialized() || !cap.isModelReady() || (index = vehicle.getPassengers().indexOf(entity)) < 0 || (model = cap.getCurrentModel()) == null || model.passengerGroupChains().isEmpty() || index >= model.passengerGroupChains().size() || (list = model.passengerGroupChains().get(index)) == null) {
                    return;
                }
                float bodyRotation = CustomVehicleRenderer.getBodyRotation(vehicle, Mth.lerp(partialTick, vehicle.yRotO, vehicle.getYRot()), partialTick);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - bodyRotation));
                RenderUtils.prepMatrixForLocator(poseStack, list);
                poseStack.mulPose(Axis.YN.rotationDegrees(180.0f - bodyRotation));
                double myRidingOffset = -(vehicle.getPassengerRidingPosition(entity).y - vehicle.getY());
                if (((entity instanceof Player) && PlayerCapability.get(entity).isPresent()) || TouhouLittleMaidCompat.isMaidRideable(entity)) {
                    myRidingOffset -= 0.5d;
                }
                poseStack.translate(0.0d, myRidingOffset, 0.0d);
            });
        }
    }

    // 动画测试界面的模型
    public static void renderEntityPreview(float x, float y, float scale, float pitch, float yaw, float partialTick, AnimatableEntity animatableEntity, AvatarRenderState state, GeoReplacedEntityRenderer renderer, boolean renderGround) {
        setPreviewMode(true);
        LivingEntity livingEntity = (LivingEntity) animatableEntity.getEntity();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1250.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees((-10.0f) + pitch);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        Pose oldPose = livingEntity.getPose();
        livingEntity.yBodyRot = -yaw;
        livingEntity.yBodyRotO = -yaw;
        livingEntity.setYRot(180.0f);
        livingEntity.yRotO = 180.0f;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = -yaw;
        livingEntity.yHeadRotO = -yaw;

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        poseStack.mulPose(rotationX);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();
        if (animationTracker.isCurrentAnimation("sleep")) {
            poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0f));
            poseStack.translate(0.5d, 0.5625d, 0.0d);
            livingEntity.setPose(Pose.SLEEPING);
        }
        if (animationTracker.isCurrentAnimation("swim") || animationTracker.isCurrentAnimation("swim_stand")) {
            livingEntity.setPose(Pose.SWIMMING);
        }
        if (animationTracker.isCurrentAnimation("sneak") || animationTracker.isCurrentAnimation("sneaking")) {
            livingEntity.setPose(Pose.CROUCHING);
        }
        if (animationTracker.isCurrentAnimation("sit")) {
            poseStack.translate(0.0d, -0.5d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("ride")) {
            poseStack.translate(0.0d, 0.85d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("ride_pig")) {
            poseStack.translate(0.0d, 0.3125d, 0.0d);
        }
        if (animationTracker.isCurrentAnimation("boat")) {
            poseStack.translate(0.0d, -0.45d, 0.0d);
        }
        try {
            renderVehicleForAnimation(yaw, animatableEntity, partialTick, poseStack, entityRenderDispatcher, bufferSource);
            if (animationTracker.isCurrentAnimation("sleep")) {
                renderBedPreview(scale, pitch, yaw, bufferSource);
            }
            if (renderGround) {
                renderGroundPreview(scale, pitch, yaw, bufferSource);
            }
            renderer.renderEntity((LivingAnimatable) animatableEntity, state, 0.0f, partialTick, poseStack, bufferSource, 15728880);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }

        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        livingEntity.setPose(oldPose);

        modelViewStack.popMatrix();
        setPreviewMode(false);
    }

    private static void renderBedPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.RED_BED.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderGroundPreview(float scale, float pitch, float yaw, MultiBufferSource.BufferSource bufferSource) {
        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0d, 0.8d, 0.0d);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        rotationZ.mul(Axis.XP.rotationDegrees((-10.0f) + pitch));
        poseStack.mulPose(rotationZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0f, 0.0f, 1.0f);
                Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.GRASS_BLOCK.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
            }
            poseStack.translate(1.0f, 0.0f, -3.0f);
        }

        poseStack.translate(-1.0f, 1.0f, 1.0f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.SHORT_GRASS.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
        poseStack.translate(0.0f, 0.0f, 1.0f);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(Blocks.RED_TULIP.defaultBlockState(), poseStack, bufferSource, 15728880, OverlayTexture.NO_OVERLAY);
    }

    private static void renderVehicleForAnimation(float yaw, AnimatableEntity animatableEntity, float partialTick, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource) throws ExecutionException {
        Entity entity = animatableEntity.getEntity();
        AnimationTracker animationTracker = ((IPreviewAnimatable) animatableEntity).getAnimationStateMachine();

        if (animationTracker.isCurrentAnimation("ride")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.HORSE), () -> EntityType.HORSE.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        } else if (animationTracker.isCurrentAnimation("ride_pig")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.PIG), () -> EntityType.PIG.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        } else if (animationTracker.isCurrentAnimation("boat")) {
            renderVehicleEntity(yaw, entity, poseStack, entityRenderDispatcher, bufferSource, AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(EntityType.OAK_BOAT), () -> EntityType.OAK_BOAT.create(entity.level(), net.minecraft.world.entity.EntitySpawnReason.LOAD)), partialTick);
        }
    }

    private static void renderVehicleEntity(float yaw, Entity riderEntity, PoseStack poseStack, EntityRenderDispatcher entityRenderDispatcher, MultiBufferSource.BufferSource bufferSource, Entity vehicleEntity, float partialTick) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.popPose();
    }

    // 模型预览页面
    public static <T extends Player, TAnimatable extends LivingAnimatable<T>, S extends AvatarRenderState> void renderLivingEntityPreview(float x, float y, float scale, float partialTick, TAnimatable animatable, S state, GeoReplacedEntityRenderer<T, TAnimatable, S> renderer, boolean disablePreviewRotation, boolean hideEquipment) {
        ItemStack[] savedEquipment;
        setPreviewMode(true);
        LivingEntity livingEntity = animatable.getEntity();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate(x, y, 1050.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0d, disablePreviewRotation ? 5.5d : 0.0d, 1000.0d);
        poseStack.scale(scale, scale, scale);
        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.0f);
        Quaternionf rotationX = Axis.XP.rotationDegrees(disablePreviewRotation ? 0.0f : -10.0f);
        rotationZ.mul(rotationX);
        poseStack.mulPose(rotationZ);

        float oldBodyRot = livingEntity.yBodyRot;
        float oldBodyRotO = livingEntity.yBodyRotO;
        float oldYRot = livingEntity.getYRot();
        float oldYRotO = livingEntity.yRotO;
        float oldXRot = livingEntity.getXRot();
        float oldXRotO = livingEntity.xRotO;
        float oldHeadRotO = livingEntity.yHeadRotO;
        float oldHeadRot = livingEntity.yHeadRot;
        if (hideEquipment && (livingEntity instanceof Player player)) {
            savedEquipment = new ItemStack[EquipmentSlot.values().length];
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                savedEquipment[slotIndex] = player.getItemBySlot(equipmentSlot).copy();
                player.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                slotIndex++;
            }
        } else {
            savedEquipment = null;
        }

        float previewYaw = disablePreviewRotation ? 180.0f : 200.0f;
        livingEntity.yBodyRot = previewYaw;
        livingEntity.yBodyRotO = previewYaw;
        livingEntity.setYRot(previewYaw);
        livingEntity.yRotO = previewYaw;
        livingEntity.setXRot(0.0f);
        livingEntity.xRotO = 0.0f;
        livingEntity.yHeadRot = livingEntity.getYRot();
        livingEntity.yHeadRotO = livingEntity.getYRot();

        Entity vehicle = livingEntity.getVehicle();
        if (vehicle instanceof LivingEntity) {
            float vehicleYaw = vehicle.getYRot();
            poseStack.mulPose(Axis.YP.rotationDegrees(vehicleYaw - previewYaw));
            livingEntity.yHeadRot = vehicleYaw;
            livingEntity.yHeadRotO = vehicleYaw;
        }

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationX.conjugate();
        poseStack.mulPose(rotationX);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        renderer.renderEntity(animatable, state, 0.0f, partialTick, poseStack, bufferSource, 15728880);

        livingEntity.yBodyRot = oldBodyRot;
        livingEntity.yBodyRotO = oldBodyRotO;
        livingEntity.setYRot(oldYRot);
        livingEntity.yRotO = oldYRotO;
        livingEntity.setXRot(oldXRot);
        livingEntity.xRotO = oldXRotO;
        livingEntity.yHeadRotO = oldHeadRotO;
        livingEntity.yHeadRot = oldHeadRot;
        if (savedEquipment != null) {
            Player player = (Player) livingEntity;
            int slotIndex = 0;
            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                ItemStack itemStack = savedEquipment[slotIndex];
                player.setItemSlot(equipmentSlot, itemStack);
                slotIndex++;
            }
        }

        modelViewStack.popMatrix();
        setPreviewMode(false);
    }

    // 纸娃娃
    public static void renderPlayerOverlay(GuiGraphics guiGraphics, LocalPlayer localPlayer, double x, double y, float scale, float yawOffset, int zDepth, float partialTick) {
        setExtraPlayerMode(true);

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        modelViewStack.translate((float) (x + (scale * 0.5d)), (float) (y + (scale * 2.0f)), 0.0f);
        modelViewStack.scale(1.0f, 1.0f, -1.0f);

        PoseStack poseStack = new PoseStack();
        poseStack.translate(0.0f, 0.0f, -zDepth);
        poseStack.scale(scale, scale, scale);

        Quaternionf rotationZ = Axis.ZP.rotationDegrees(180.1f);
        Quaternionf rotationY = Axis.YP.rotationDegrees((Mth.lerp(partialTick, localPlayer.yBodyRotO, localPlayer.yBodyRot) + yawOffset) - 180.0f);
        rotationZ.mul(rotationY);
        poseStack.mulPose(rotationZ);

        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        rotationY.conjugate();
        poseStack.mulPose(rotationY);
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        modelViewStack.popMatrix();
        setExtraPlayerMode(false);
    }

    public static void submitLivingEntityPreview(
            GuiGraphics guiGraphics,
            int x0, int y0, int x1, int y1,
            int displaySize,
            float partialTick,
            PlayerPreviewEntity animatable,
            boolean disablePreviewRotation,
            boolean hideEquipment
    ) {
        LivingEntity entity = animatable.getEntity();
        if (entity == null) {
            return;
        }

        ItemStack[] savedEquipment = null;
        if (hideEquipment && entity instanceof Player player) {
            EquipmentSlot[] slots = EquipmentSlot.values();
            savedEquipment = new ItemStack[slots.length];
            for (int i = 0; i < slots.length; i++) {
                savedEquipment[i] = player.getItemBySlot(slots[i]).copy();
                player.setItemSlot(slots[i], ItemStack.EMPTY);
            }
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState((Player) entity, state, partialTick);
        state.lightCoords = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        float previewYaw = disablePreviewRotation ? 180.0F : 200.0F;
        state.bodyRot = previewYaw;
        state.yRot = 0.0F;
        state.xRot = 0.0F;

        PreviewEntityRegistry.register(state, animatable);

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraTilt = null;
        if (!disablePreviewRotation) {
            cameraTilt = new Quaternionf().rotateX((float) (-10.0 * Math.PI / 180.0));
            rotation.mul(cameraTilt);
        }

        float entityScale = entity.getScale();
        float yOffsetPx = disablePreviewRotation ? 5.5F : 0.0F;
        float yOffsetModel = yOffsetPx * entityScale / (float) displaySize;
        Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F + yOffsetModel, 0.0F);
        float submitScale = (float) displaySize / entityScale;

        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.submitEntityRenderState(state, submitScale, translation, rotation, cameraTilt, x0, y0, x1, y1);
        guiGraphics.disableScissor();

        if (savedEquipment != null) {
            Player player = (Player) entity;
            EquipmentSlot[] slots = EquipmentSlot.values();
            for (int i = 0; i < slots.length; i++) {
                player.setItemSlot(slots[i], savedEquipment[i]);
            }
        }
    }

    public static void submitPlayerOverlay(
            GuiGraphics guiGraphics,
            LocalPlayer localPlayer,
            double x, double y,
            float scale,
            float yawOffset,
            float partialTick
    ) {
        PlayerCapability cap = PlayerCapability.get(localPlayer).orElse(null);
        if (cap == null) {
            return;
        }
        cap.tickModel();

        float cx = (float) (x + scale * 0.6F);
        float cy = (float) (y + scale * 1.0F);
        int halfW = (int) (scale * 2.0F);
        int halfH = (int) (scale * 2.5F);
        int x0 = (int) cx - halfW;
        int x1 = (int) cx + halfW;
        int y0 = (int) cy - halfH;
        int y1 = (int) cy + halfH;

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState(localPlayer, state, partialTick);
        state.lightCoords = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        PreviewEntityRegistry.register(state, cap);

        state.bodyRot = 180.0F;

        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI);
        float entityScale = localPlayer.getScale();
        Vector3f translation = new Vector3f(0.0F, localPlayer.getBbHeight() / 2.0F, 0.0F);
        float submitScale = scale / entityScale;

        guiGraphics.submitEntityRenderState(state, submitScale, translation, rotation, null, x0, y0, x1, y1);
    }

    public static void submitTexturePreview(
            GuiGraphics guiGraphics,
            int x0, int y0, int x1, int y1,
            float anchorX, float anchorY,
            float zoom,
            float pitch,
            float yaw,
            PlayerPreviewEntity animatable,
            boolean renderGround,
            float partialTick
    ) {
        LivingEntity entity = animatable.getEntity();
        if (entity == null) {
            return;
        }

        AnimationTracker tracker = animatable.getAnimationStateMachine();
        Pose oldPose = entity.getPose();
        Pose newPose = oldPose;
        float poseYOffset = 0.0F;
        if (tracker.isCurrentAnimation("sleep")) {
            newPose = Pose.SLEEPING;
            // 人物要躺在**床面**上，而不是草地上。床高 9/16 = 0.5625
            // （与 BedRenderer.preparePose 里那个 0.5625 是同一个量）。
            // 与载具同理：内层坐标系里地面顶面是 y=0，故直接抬高一个床高即可。
            poseYOffset = 0.5625F;
        } else if (tracker.isCurrentAnimation("swim") || tracker.isCurrentAnimation("swim_stand")) {
            newPose = Pose.SWIMMING;
        } else if (tracker.isCurrentAnimation("sneak") || tracker.isCurrentAnimation("sneaking")) {
            newPose = Pose.CROUCHING;
        } else if (tracker.isCurrentAnimation("sit")) {
            poseYOffset = -0.5F;
        } else if (tracker.isCurrentAnimation("ride")) {
            poseYOffset = 0.85F;
        } else if (tracker.isCurrentAnimation("ride_pig")) {
            poseYOffset = 0.3125F;
        } else if (tracker.isCurrentAnimation("boat")) {
            poseYOffset = -0.45F;
        }
        boolean poseChanged = newPose != oldPose;
        if (poseChanged) {
            entity.setPose(newPose);
        }

        CustomPlayerRenderer renderer = RendererManager.getPlayerRenderer();
        AvatarRenderState state = new AvatarRenderState();
        renderer.extractRenderState((Player) entity, state, partialTick);
        state.lightCoords = net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;

        state.bodyRot = -yaw;
        state.yRot = Mth.wrapDegrees(180.0F + yaw);
        state.xRot = 0.0F;

        final float capturedYaw = yaw;
        final boolean wantGround = renderGround;
        final boolean wantBed = tracker.isCurrentAnimation("sleep");
        final boolean wantHorse = tracker.isCurrentAnimation("ride");
        final boolean wantPig = tracker.isCurrentAnimation("ride_pig");
        final boolean wantBoat = tracker.isCurrentAnimation("boat");
        // 布景（载具/床/地面）一律**不继承**人物的动画偏移：它们都是「地面上的物件」，
        // 而 poseYOffset 描述的是人物相对地面的高度。两者互不影响。
        PreviewEntityRegistry.SceneryRenderer scenery = null;
        if (wantGround || wantBed || wantHorse || wantPig || wantBoat) {
            scenery = (poseStack, bufferSource, packedLight) -> {
                if (wantHorse) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityType.HORSE);
                } else if (wantPig) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityType.PIG);
                } else if (wantBoat) {
                    renderVehicleScenery(poseStack, bufferSource, packedLight, capturedYaw, partialTick, entity, EntityType.OAK_BOAT);
                }
                if (wantBed) {
                    renderBedScenery(poseStack, bufferSource, packedLight, capturedYaw);
                }
                if (wantGround) {
                    renderGroundScenery(poseStack, bufferSource, packedLight, capturedYaw);
                }
            };
        }

        if (scenery != null || poseYOffset != 0.0f) {
            PreviewEntityRegistry.register(state, animatable, scenery, null, poseYOffset);
        } else {
            PreviewEntityRegistry.register(state, animatable);
        }

        Quaternionf cameraTilt = new Quaternionf().rotateX((float) Math.toRadians(-10.0 + pitch));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(cameraTilt);

        float entityScale = entity.getScale();
        float submitScale = zoom / entityScale;
        float rectCenterX = (x0 + x1) / 2.0F;
        float rectCenterY = (y0 + y1) / 2.0F;
        float translationX = (anchorX - rectCenterX) / submitScale;
        // 注意：**不要**把 poseYOffset 加进来。基准只有 0.8 那一项在旋转之前，
        // 动画偏移是在 mulPose(rotationZ) 之后施加的；而 GuiEntityRenderer 是
        // 「先 translate 再 mulPose」，折进这里会被 rotateZ(180°) 取反并掺入 Z 分量。
        // poseYOffset 改由 GuiEntityRendererMixin 在内层坐标系施加，见 PreviewEntityRegistry.Entry。
        float translationY = (anchorY - rectCenterY) / submitScale + 0.8F;
        if (wantBed) {
            state.bodyRot = yaw - 90;
        }

        Vector3f translation = new Vector3f(translationX, translationY, 0.0F);

        guiGraphics.enableScissor(x0, y0, x1, y1);
        guiGraphics.submitEntityRenderState(state, submitScale, translation, rotation, cameraTilt, x0, y0, x1, y1);
        guiGraphics.disableScissor();

        if (poseChanged) {
            entity.setPose(oldPose);
        }
    }

    private static void renderGroundScenery(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float yaw) {
        net.minecraft.client.renderer.block.BlockRenderDispatcher blockRenderer = Minecraft.getInstance().getBlockRenderer();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        poseStack.translate(-1.5d, -1.0d, -2.5d);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                poseStack.translate(0.0f, 0.0f, 1.0f);
                blockRenderer.renderSingleBlock(Blocks.GRASS_BLOCK.defaultBlockState(), poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            }
            poseStack.translate(1.0f, 0.0f, -3.0f);
        }
        poseStack.translate(-1.0f, 1.0f, 1.0f);
        blockRenderer.renderSingleBlock(Blocks.SHORT_GRASS.defaultBlockState(), poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.translate(0.0f, 0.0f, 1.0f);
        blockRenderer.renderSingleBlock(Blocks.RED_TULIP.defaultBlockState(), poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static void renderBedScenery(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float yaw) {
        // 床画不出来的成因：床的**方块模型是空的**——assets/minecraft/models/block/bed.json 里
        // 只有一个 particle 贴图，没有任何 elements 几何（已在 1.21.11 的 jar 里实证）。
        // 所以 renderSingleBlock 对床是彻底的空操作，与 render shape 无关
        // （1.21.11 的 RenderShape 只剩 INVISIBLE / MODEL，床走的是 MODEL）。
        // 床的真实几何在 BedRenderer 里，对外经 BedSpecialRenderer 暴露，
        // 由 SpecialBlockModelRenderer.renderByBlock 提交——这正是 README「已知问题」里
        // sleep 那一半的成因，且它在 1.20.1 基准上同样存在（基准也是 renderSingleBlock）。
        SubmitNodeCollector collector = RenderContext.collector();
        if (collector == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw + 180.0f));
        poseStack.translate(-0.5d, 0.0d, 0.5d);
        // 末三个 int 依次是 light / overlay / color（字节码实证：renderByBlock 转发给
        // SpecialModelRenderer.submit(data, ctx, pose, collector, light, overlay, glint=false, color)）。
        Minecraft.getInstance().getModelManager().specialBlockModelRenderer().renderByBlock(
                Blocks.RED_BED,
                ItemDisplayContext.NONE,
                poseStack,
                collector,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                -1);
        poseStack.popPose();
    }

    /** 取（并缓存）用于预览摆拍的载具实体；拿不到就返回 null，调用方自行降级。 */
    @org.jetbrains.annotations.Nullable
    private static Entity cachedVehicle(LivingEntity rider, EntityType<? extends Entity> vehicleType) {
        if (rider.level() == null) {
            return null;
        }
        try {
            return AnimatableCacheUtil.ENTITIES_CACHE.get(EntityType.getKey(vehicleType),
                    () -> vehicleType.create(rider.level(), EntitySpawnReason.LOAD));
        } catch (java.util.concurrent.ExecutionException e) {
            return null;
        }
    }

    private static void renderVehicleScenery(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            float yaw,
            float partialTick,
            LivingEntity rider,
            EntityType<? extends Entity> vehicleType
    ) {
        Entity vehicle = cachedVehicle(rider, vehicleType);
        if (vehicle == null) {
            return;
        }
        // 1.21.11 起 GUI 里的实体渲染改走 submit / 渲染图，没有方块那样的立即模式接口
        // （床与地面能活下来正是因为 renderSingleBlock 仍是立即模式）。所需的
        // SubmitNodeCollector 与 CameraRenderState 由 GuiEntityRendererMixin 在调用本回调
        // **之前**经 RenderContext.enter(...) 放好，这里取出来即可，无需改 SceneryRenderer 接口。
        SubmitNodeCollector collector = RenderContext.collector();
        CameraRenderState cameraState = RenderContext.camera();
        if (collector == null || cameraState == null) {
            return;
        }
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
        // 基准（Forge 1.20.1 的 OpenYSM/OpenYSM）把偏移作为 render(...) 的 y 实参传入，值 =
        // (-vehicle.getPassengersRidingOffset()) - rider.getMyRidingOffset()。1.21.11 的
        // getPassengerRidingPosition 已把这两项合并——本仓 renderVehicleModel 用的就是同一映射。
        // submit(...) 的 x/y/z 同样被加上 getRenderOffset 后 translate 进 poseStack（字节码实证），
        // 与基准的 render(...) 语义一致，故照样按实参传，不要改成 poseStack.translate。
        // 载具直接落在地面：内层坐标系里地面顶面正好是 y=0
        // （renderGroundScenery 的方块占 [-1,0]），且本回调**不继承人物的动画偏移**，
        // 所以传 0 即可。
        //
        // ⚠️ 勿改成「按座高摆放」。座高（getPassengerRidingPosition / PASSENGER 挂点，
        // 马 1.44375、猪 0.86875、船 0.1875）是**原版骑乘**的挂点语义；而预览里人物播的是
        // 模型作者做的坐姿动画，重心与原版挂点不是一回事。2026-07-29 实测：
        // 用座高决定人物高度，三种载具的人物一律**浮在载具上方**。
        // 人物高度归 poseYOffset（基准常量）管，载具只管落地，两者不要互相推导。
        double yOffset = 0.0d;
        EntityRenderState vehicleState = dispatcher.extractEntity(vehicle, partialTick);
        // 等价基准写死的 15728880（全亮）；packedLight 由 submitTexturePreview 设为 FULL_BRIGHT。
        vehicleState.lightCoords = packedLight;
        // 等价基准在预览前后成对调用的 setRenderShadow(false/true)。1.21.11 把阴影搬进了渲染态，
        // 而 submit 的判据是 shadowPieces.isEmpty()——只清半径不生效，必须清空列表。
        vehicleState.shadowRadius = 0.0f;
        vehicleState.shadowPieces.clear();
        dispatcher.submit(vehicleState, cameraState, 0.0d, yOffset, 0.0d, poseStack, collector);
        poseStack.popPose();
    }
}
