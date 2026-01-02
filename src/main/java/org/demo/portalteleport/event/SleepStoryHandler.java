package org.demo.portalteleport.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.util.I18n;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Random;

/**
 * 睡前故事：玩家睡醒后获得轻微的短时增益，并显示随机提示。
 * 优化：
 * - 可配置的触发间隔
 * - 可配置是否给予药水效果
 * - 每天仅赋予一次增益效果
 */
public class SleepStoryHandler {
    private static final Random RNG = new Random();
    private static final int STORY_COUNT = 18;  // 故事总数

    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.sleepStoryEnabled.get()) return;

        // 随机选择一个故事 (1-18)
        int storyIndex = RNG.nextInt(STORY_COUNT) + 1;
        var tag = player.getPersistentData();
        long now = System.currentTimeMillis();
        long last = tag.getLong("pt_sleep_story_last");
        long minIntervalMs = cfg.sleepMinIntervalSeconds.get() * 1000L;

        if (now - last < minIntervalMs) {
            player.sendSystemMessage(I18n.translate(I18n.getSleepStoryKey(storyIndex)));
            return;
        }
        tag.putLong("pt_sleep_story_last", now);

        player.sendSystemMessage(I18n.translate(I18n.getSleepStoryKey(storyIndex)));

        // 检查是否启用药水效果
        if (!cfg.sleepEffectsEnabled.get()) return;

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
                    player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_REGENERATION));
                }
            } else if (roll < 90) {
                if (!player.hasEffect(MobEffects.LUCK)) {
                    player.addEffect(new MobEffectInstance(MobEffects.LUCK, 30 * 20, 0, false, true));
                    player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_LUCK));
                }
            } else {
                if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 20, 0, false, true));
                    player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_SPEED));
                }
            }
        }
    }
}
