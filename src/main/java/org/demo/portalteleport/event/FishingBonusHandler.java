package org.demo.portalteleport.event;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.util.I18n;

import java.util.Random;

/**
 * 钓鱼加成事件处理器
 * 功能：玩家成功钓到物品时，有一定几率获得额外奖励物品。
 * 可配置项（在 portalteleport-common.toml 中）：
 * - enabled - 是否启用此功能
 * - bonusChance - 获得奖励的几率 (0.0~1.0)
 * - bonusItem - 奖励物品的 ID（如 minecraft:cod）
 * - bonusCount - 奖励物品的数量
 *
 * @author SelfAbandonment
 */
public class FishingBonusHandler {

    /** 随机数生成器 */
    private static final Random RNG = new Random();

    /**
     * 处理玩家钓鱼成功事件
     *
     * @param event 钓鱼成功事件
     */
    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        // 仅处理服务端玩家
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.fishingBonusEnabled.get()) {
            return;
        }

        // 检查几率
        double chance = cfg.fishingBonusChance.get();
        if (RNG.nextFloat() >= chance) {
            return;
        }

        // 获取配置的物品并发放奖励
        String itemId = cfg.fishingBonusItem.get();
        Item bonusItem = parseItem(itemId);
        int count = cfg.fishingBonusCount.get();

        ItemStack bonus = new ItemStack(bonusItem, count);
        player.addItem(bonus);

        // 发送奖励消息
        String itemName = bonus.getHoverName().getString();
        player.sendSystemMessage(I18n.translate(I18n.FISHING_BONUS, count, itemName));
    }

    /**
     * 解析物品 ID 字符串为物品对象
     *
     * @param itemId 物品 ID（如 "minecraft:cod"）
     * @return 对应的物品对象，解析失败时返回鳕鱼
     */
    private Item parseItem(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item != Items.AIR) {
                return item;
            }
        } catch (Exception ignored) {
            // 解析失败，使用默认物品
        }
        return Items.COD;
    }
}

