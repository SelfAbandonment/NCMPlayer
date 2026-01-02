package org.demo.portalteleport.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;

import java.util.Random;

/**
 * 轻松玩法：钓鱼加成
 * 玩家成功钓到物品时，有一定几率获得一条额外的鱼，并发送提示消息。
 */
public class FishingBonusHandler {
    private static final Random RNG = new Random();

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (RNG.nextFloat() < 0.25f) {
            ItemStack bonus = new ItemStack(Items.COD, 1); // 额外奖励：鳕鱼
            player.addItem(bonus);
            player.sendSystemMessage(Component.literal("今天手气不错！额外获得一条鱼"));
        }
    }
}

