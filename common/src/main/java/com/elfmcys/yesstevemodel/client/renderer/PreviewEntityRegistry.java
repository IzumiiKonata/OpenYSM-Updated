package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

public final class PreviewEntityRegistry {

    @FunctionalInterface
    public interface SceneryRenderer {
        void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);
    }

    /**
     * @param beforeEntity 布景（载具、床、地面）。**不**继承 poseYOffset：它们都是
     *                     「地面上的物件」，而 poseYOffset 描述人物相对地面的高度。
     * @param poseYOffset  人物的动画偏移，**必须在旋转之后的内层坐标系里施加**。
     *                     基准是 {@code mulPose(rotationZ)} 之后才 translate 的；
     *                     而 GuiEntityRenderer 是「先 translate 再 mulPose」，
     *                     把它折进外层 translation 会因 {@code rotateZ(180°)} 把 Y 取反、
     *                     并被 cameraTilt 掺进一个 Z 分量——取任何数值都不可能等价。
     */
    public record Entry(
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity,
            float poseYOffset
    ) {
    }

    private static final Map<EntityRenderState, Entry> ENTRIES = new IdentityHashMap<>();

    private PreviewEntityRegistry() {
    }

    public static void register(EntityRenderState state, CustomPlayerEntity animatable) {
        ENTRIES.put(state, new Entry(animatable, null, null, 0.0f));
    }

    public static void register(
            EntityRenderState state,
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity
    ) {
        ENTRIES.put(state, new Entry(animatable, beforeEntity, afterEntity, 0.0f));
    }

    public static void register(
            EntityRenderState state,
            CustomPlayerEntity animatable,
            @Nullable SceneryRenderer beforeEntity,
            @Nullable SceneryRenderer afterEntity,
            float poseYOffset
    ) {
        ENTRIES.put(state, new Entry(animatable, beforeEntity, afterEntity, poseYOffset));
    }

    @Deprecated
    @Nullable
    public static CustomPlayerEntity get(EntityRenderState state) {
        Entry entry = ENTRIES.get(state);
        return entry == null ? null : entry.animatable();
    }

    @Nullable
    public static Entry getEntry(EntityRenderState state) {
        return ENTRIES.get(state);
    }

    public static void remove(EntityRenderState state) {
        ENTRIES.remove(state);
    }
}
