package com.elfmcys.yesstevemodel.capability;

import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.sync.PlayerStateSynchronizer;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.util.StringPool;
import com.elfmcys.yesstevemodel.network.message.S2CSetModelAndTexturePacket;
import com.elfmcys.yesstevemodel.network.message.FeedbackData;
import com.google.common.collect.Queues;
import dev.architectury.injectables.annotations.ExpectPlatform;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Consumer;

public class ModelInfoCapability {

    @ExpectPlatform
    public static Optional<ModelInfoCapability> get(Player player) {
        throw new AssertionError();
    }

    private String modelId;

    private String selectTexture;

    private boolean mandatory;

    private Int2ReferenceOpenHashMap<Object2FloatOpenHashMap<String>> molangStorage;

    private PlayerStateSynchronizer animSync;

    private boolean disabled;

    private boolean dirty;

    private final Queue<Consumer<Object2FloatOpenHashMap<String>>> pendingCallbacks;

    public ModelInfoCapability() {
        // 构造期不得读 SERVER 配置。本组件由 Cardinal Components 挂在**每一个** Player 上，
        // 包含客户端的 LocalPlayer / RemotePlayer；而客户端的 SERVER 配置只有在服务端把
        // openysm-server.toml 推给它之后才是已加载的（Forge Config API Port 仅在配置阶段
        // 推送服务端自己注册过的 SERVER 配置）。服务端没装 YSM 时那份配置永远不会到达，
        // spec.childConfig 恒为 null：开发环境下 ConfigValue.get() 直接抛 IllegalStateException，
        // 客户端在 handleLogin 建 LocalPlayer 时当场崩（正式环境静默返回默认值，不影响玩家）。
        // 故默认值改为首次读取时解析，见 resolveDefaults()。
        this.molangStorage = new Int2ReferenceOpenHashMap<>();
        this.animSync = new PlayerStateSynchronizer();
        this.pendingCallbacks = Queues.newArrayDeque();
        this.disabled = false;
    }

    /**
     * 惰性补齐默认模型与材质。凡读取 modelId / selectTexture 的路径都必须先调用本方法。
     * 服务端首次读取必在 SERVER 配置加载之后；客户端要么从不读，要么在服务端同步之后读。
     * 存档玩家走 deserializeNBT 直接覆盖两个字段，根本不会触发这里的配置读取。
     */
    private void resolveDefaults() {
        if (this.modelId != null) {
            return;
        }
        Pair<String, String> defaultModelConfig = ServerModelManager.getDefaultModelConfig();
        this.modelId = defaultModelConfig.getLeft();
        this.selectTexture = defaultModelConfig.getRight();
    }

    public void setModelAndTexture(String str, String str2) {
        resolveDefaults();
        if (this.modelId.equals(str) && this.selectTexture.equals(str2)) {
            return;
        }
        this.modelId = str;
        this.selectTexture = str2;
        markDirty();
    }

    public void resetToDefault() {
        Pair<String, String> pair = ServerModelManager.getDefaultModelConfig();
        setModelAndTexture(pair.getLeft(), pair.getRight());
    }

    public void copyFrom(ModelInfoCapability source) {
        this.molangStorage = source.molangStorage;
        this.modelId = source.modelId;
        this.selectTexture = source.selectTexture;
        this.mandatory = source.mandatory;
        this.animSync = source.animSync;
        this.pendingCallbacks.addAll(source.pendingCallbacks);
        this.disabled = source.disabled;
        source.pendingCallbacks.clear();
        markDirty();
    }

    public String getModelId() {
        resolveDefaults();
        return this.modelId;
    }

    public String getSelectTexture() {
        resolveDefaults();
        return this.selectTexture;
    }

    public void setSelectTexture(String str) {
        // 必须先补齐：本方法只写 selectTexture，若此时 modelId 仍为 null，
        // 之后任何一次 resolveDefaults() 都会把这里刚写进去的材质覆盖掉。
        resolveDefaults();
        this.selectTexture = str;
        markDirty();
    }

    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            markDirty();
        }
    }

    public void playAnimation(ServerPlayer serverPlayer, String str) {
        this.animSync.syncModelSwitch(serverPlayer, !this.dirty, str);
    }

    public void stopAnimation(ServerPlayer serverPlayer) {
        this.animSync.syncModelSwitch(serverPlayer, !this.dirty, StringPool.EMPTY);
    }

    public Optional<S2CSetModelAndTexturePacket> createSyncMessage(ServerPlayer serverPlayer, boolean fullSync) {
        resolveDefaults();
        return ServerModelManager.getModelDefinition(this.modelId).map(it -> {
            Object2FloatOpenHashMap<String> object2FloatOpenHashMap = this.molangStorage.computeIfAbsent(it.getLoadedModelData().getHashId(), i -> new Object2FloatOpenHashMap<>(0));
            while (true) {
                Consumer<Object2FloatOpenHashMap<String>> consumerPoll = this.pendingCallbacks.poll();
                if (consumerPoll != null) {
                    consumerPoll.accept(object2FloatOpenHashMap);
                } else {
                    return new S2CSetModelAndTexturePacket(serverPlayer.getId(), this.modelId, this.selectTexture, this.disabled, this.animSync.buildFullSyncMessage(serverPlayer, fullSync).setMolangVars(it.getLoadedModelData().getHashId(), object2FloatOpenHashMap));
                }
            }
        });
    }

    public void withMolangVars(Consumer<Object2FloatOpenHashMap<String>> consumer) {
        resolveDefaults();
        ServerModelManager.getModelDefinition(this.modelId).ifPresentOrElse(value -> {
            consumer.accept(this.molangStorage.computeIfAbsent(value.getLoadedModelData().getHashId(), i -> {
                return new Object2FloatOpenHashMap(0);
            }));
        }, () -> {
            this.pendingCallbacks.add(consumer);
        });
    }

    public Optional<Object2FloatOpenHashMap<String>> getMolangVars() {
        resolveDefaults();
        return ServerModelManager.getModelDefinition(this.modelId).map(serverModelData -> {
            return (Object2FloatOpenHashMap) this.molangStorage.computeIfAbsent(serverModelData.getLoadedModelData().getHashId(), i -> {
                return new Object2FloatOpenHashMap(0);
            });
        });
    }

    public void applyFeedback(ServerPlayer serverPlayer, FeedbackData feedbackData) {
        this.molangStorage.compute(feedbackData.entityId(), (num, object2FloatOpenHashMap) -> {
            if (object2FloatOpenHashMap != null) {
                object2FloatOpenHashMap.putAll(feedbackData.stringValues());
                return object2FloatOpenHashMap;
            }
            return new Object2FloatOpenHashMap(feedbackData.stringValues());
        });
        this.animSync.syncMolangVars(serverPlayer, !this.dirty, feedbackData.entityId(), feedbackData.stringValues());
    }

    public void retainAnimationKeys(IntSet intSet) {
        ObjectIterator objectIteratorFastIterator = this.molangStorage.int2ReferenceEntrySet().fastIterator();
        while (objectIteratorFastIterator.hasNext()) {
            if (!intSet.contains(((Int2ReferenceMap.Entry) objectIteratorFastIterator.next()).getIntKey())) {
                objectIteratorFastIterator.remove();
            }
        }
    }

    public PlayerStateSynchronizer getAnimSync() {
        return this.animSync;
    }

    public boolean isDisabled() {
        return this.disabled;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void clearDirty() {
        this.dirty = false;
    }

    public void setMandatory(boolean mandatory) {
        if (this.mandatory != mandatory) {
            this.mandatory = mandatory;
            markDirty();
        }
    }

    public boolean isMandatory() {
        return this.mandatory;
    }

    public CompoundTag serializeNBT() {
        resolveDefaults();
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("model_id", this.modelId);
        compoundTag.putString("select_texture", this.selectTexture);
        compoundTag.putBoolean("mandatory", this.mandatory);
        compoundTag.putBoolean("disabled", this.disabled);
        CompoundTag compoundTag2 = new CompoundTag();
        this.molangStorage.int2ReferenceEntrySet().fastForEach(entry -> {
            CompoundTag compoundTag3 = new CompoundTag();
            entry.getValue().object2FloatEntrySet().fastForEach(entry2 -> {
                compoundTag3.putFloat(entry2.getKey(), entry2.getFloatValue());
            });
            compoundTag2.put(String.valueOf(entry.getIntKey()), compoundTag3);
        });
        compoundTag.put("molang_storage", compoundTag2);
        return compoundTag;
    }

    public void deserializeNBT(CompoundTag compoundTag) throws NumberFormatException {
        this.modelId = compoundTag.getStringOr("model_id", "");
        this.selectTexture = compoundTag.getStringOr("select_texture", "");
        if (this.selectTexture.length() > 4 && this.selectTexture.toLowerCase().endsWith(".png")) {
            this.selectTexture = this.selectTexture.substring(0, this.selectTexture.length() - 4);
        }
        this.mandatory = compoundTag.getBooleanOr("mandatory", false);
        this.disabled = compoundTag.getBooleanOr("disabled", false);
        this.molangStorage.clear();
        CompoundTag compound = compoundTag.getCompoundOrEmpty("molang_storage");
        for (String str : compound.keySet()) {
            CompoundTag compound2 = compound.getCompoundOrEmpty(str);
            int i = Integer.parseInt(str);
            Set<String> allKeys = compound2.keySet();
            Object2FloatOpenHashMap object2FloatOpenHashMap = this.molangStorage.computeIfAbsent(i, i2 -> {
                return new Object2FloatOpenHashMap(allKeys.size());
            });
            for (String str2 : allKeys) {
                object2FloatOpenHashMap.put(str2, compound2.getFloatOr(str2, 0.0f));
            }
        }
    }
}