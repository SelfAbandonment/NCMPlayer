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
 * 轻松玩法：钓鱼加成
 * 玩家成功钓到物品时，有一定几率获得额外奖励，并发送提示消息。
 * 所有参数均可在配置文件中调整。
 */
public class FishingBonusHandler {
    private static final Random RNG = new Random();

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.fishingBonusEnabled.get()) return;

        // 检查几率
        double chance = cfg.fishingBonusChance.get();
        if (RNG.nextFloat() >= chance) return;

        // 获取配置的物品
        String itemId = cfg.fishingBonusItem.get();
        Item bonusItem = parseItem(itemId);
        int count = cfg.fishingBonusCount.get();

        ItemStack bonus = new ItemStack(bonusItem, count);
        player.addItem(bonus);

        String itemName = bonus.getHoverName().getString();
        player.sendSystemMessage(I18n.translate(I18n.FISHING_BONUS, count, itemName));
    }

    /**
     * 解析物品ID字符串为物品对象
     */
    private Item parseItem(String itemId) {
        try {
            ResourceLocation loc = ResourceLocation.parse(itemId);
            Item item = BuiltInRegistries.ITEM.get(loc);
            if (item != Items.AIR) {
                return item;
            }
        } catch (Exception ignored) {}

        // 默认返回鳕鱼
        return Items.COD;
    }
}

