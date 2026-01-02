package org.demo.portalteleport.event;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

/**
 * 睡前故事：玩家睡醒后获得轻微的短时增益，并显示随机提示。
 * 优化：
 * - 至少间隔 2 分钟触发一次，避免刷屏。
 * - 每天仅赋予一次 60s 再生 I；当天后续醒来仅显示文本。
 * - 若玩家已拥有再生效果，则不重复叠加。
 */
public class SleepStoryHandler {
    private static final Random RNG = new Random();
    private static final String[] STORIES = new String[]{
            "昨夜好梦，今天精神满满！",
            "窗外鸟鸣，新的开始。",
            "清风拂面，心情舒畅。",
            "阳光透过窗棂，世界在微笑。",
            "温暖的床铺让你恢复了活力。",
            "家的气息让人安心。",
            "炉火尚温，早餐的香味仿佛在等你。",
            "今天不妨去看看新的风景吧。",
            "也许下一次转角就是小惊喜。",
            "地图上的空白在呼唤冒险。",
            "慢慢来，一切都会刚刚好。",
            "今天也要记得笑一笑。",
            "愿你脚步轻快，心事释然。",
            "睡前小曲仍在耳畔，心情舒畅。",
            "轻柔的旋律，伴你开始美好的一天。",
            "一杯热茶，一缕晨光，刚刚好。",
            "把烦恼折成纸飞机，放飞。",
            "愿好运像清晨的光，悄悄降临。"
    };
    private static final long MIN_INTERVAL_MS = 120_000L; // 2 分钟

    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String msg = STORIES[RNG.nextInt(STORIES.length)];
        var tag = player.getPersistentData();
        long now = System.currentTimeMillis();
        long last = tag.getLong("pt_sleep_story_last");
        if (now - last < MIN_INTERVAL_MS) {
            player.sendSystemMessage(Component.literal(msg));
            return;
        }
        tag.putLong("pt_sleep_story_last", now);

        player.sendSystemMessage(Component.literal(msg));

        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        String lastDay = tag.getString("pt_sleep_story_day");
        boolean firstToday = !String.valueOf(today).equals(lastDay);
        if (firstToday) {
            tag.putString("pt_sleep_story_day", String.valueOf(today));
            // 轻微增益：优先再生或幸运，小概率速度；避免叠加已存在效果
            int roll = RNG.nextInt(100); // 0-99
            if (roll < 50) {
                if (!player.hasEffect(MobEffects.REGENERATION)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60 * 20, 0, false, true));
                }
            } else if (roll < 90) {
                if (!player.hasEffect(MobEffects.LUCK)) {
                    player.addEffect(new MobEffectInstance(MobEffects.LUCK, 30 * 20, 0, false, true));
                }
            } else {
                if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 0, false, true));
                }
            }
        }
    }
}
