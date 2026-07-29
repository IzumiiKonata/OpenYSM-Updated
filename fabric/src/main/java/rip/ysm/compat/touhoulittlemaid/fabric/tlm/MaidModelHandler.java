package rip.ysm.compat.touhoulittlemaid.fabric.tlm;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.resource.GeckoLibCache;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.molang.parser.ParseException;
import com.elfmcys.yesstevemodel.resource.models.ModelProperties;
import com.elfmcys.yesstevemodel.util.data.OrderedStringMap;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 女仆模型/动画的运行期操作，移植自 OpenYSM 原版 TouhouMaidModelHandler 的可移植部分。
 * 类加载安全约束同 {@link MaidEventHandler}。
 */
public final class MaidModelHandler {
    private MaidModelHandler() {
    }

    /**
     * 执行一段 molang（YSM 的脚本通道），仅对已切到 YSM 模型的女仆生效。
     * <p>
     * 走 {@code MaidRenderStore.get} 而非 getOrCreate：渲染态不存在意味着这只女仆还没被渲染过，
     * 此时执行表达式没有作用对象——基准同样是 ifPresent，不新建。
     */
    public static void executeMaidMolang(Entity entity, String expression) {
        if (!(entity instanceof EntityMaid maid) || !maid.isYsmModel()) {
            return;
        }
        MaidRenderStore.get(maid).ifPresent(animatable -> {
            try {
                animatable.executeExpression(GeckoLibCache.parseSimpleExpression(expression), true, false, null);
            } catch (ParseException e) {
                YesSteveModel.LOGGER.error("Failed to execute molang {}", expression, e);
            }
        });
    }

    /**
     * 激活/停止轮盘动画。{@code index == -1} 为停止，否则按分类表取第 index 个额外动画。
     * <p>
     * 官方 wiki：轮盘动画放在 {@code extra.animation.json}，自 2.2.1 起不限数量且名称可自定义，
     * 故这里只能按**下标**取名，不能假定名字形如 {@code extra0..7}。
     * 分类为空或不存在时回落到未分类的 {@code getExtraAnimation()}——基准语义。
     */
    public static void activateRouletteAnimation(Entity entity, String classify, int index) {
        if (!(entity instanceof EntityMaid maid) || !maid.isYsmModel()) {
            return;
        }
        if (index == -1) {
            maid.stopRouletteAnim();
            return;
        }
        ServerModelManager.getModelDefinition(maid.getYsmModelId()).ifPresent(data -> {
            ModelProperties modelProperties = data.getLoadedModelData().getModelProperties();
            Map<String, OrderedStringMap<String, String>> classified = modelProperties.getExtraAnimationClassify();
            OrderedStringMap<String, String> rouletteAnims;
            if (StringUtils.isNotBlank(classify) && classified.containsKey(classify)) {
                rouletteAnims = classified.get(classify);
            } else {
                rouletteAnims = modelProperties.getExtraAnimation();
            }
            if (rouletteAnims.size() > index) {
                maid.playRouletteAnim(rouletteAnims.getKeyAt(index));
            }
        });
    }
}
