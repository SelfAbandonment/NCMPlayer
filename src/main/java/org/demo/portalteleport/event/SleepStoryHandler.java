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
 * 睡前故事事件处理器
 * 功能：玩家睡醒后显示随机温馨提示，并有机会获得轻微的药水增益。
 * 机制：
 * - 每次睡醒显示随机故事（共 18 条）
 * - 受最小触发间隔限制，避免刷屏
 * - 每天首次睡醒有机会获得药水效果（再生/幸运/速度）
 * - 不会叠加已存在的效果
 * 可配置项（在 portalteleport-common.toml 中）：
 * - enabled - 是否启用此功能
 * - minIntervalSeconds - 最小触发间隔（秒）
 * - effectsEnabled - 是否给予药水效果
 *
 * @author SelfAbandonment
 */
public class SleepStoryHandler {

    /** 随机数生成器 */
    private static final Random RNG = new Random();

    /** 故事总数（对应语言文件中的 sleep.story.1 ~ sleep.story.18） */
    private static final int STORY_COUNT = 18;

    /** 玩家数据标签：上次触发时间 */
    private static final String TAG_LAST_TIME = "pt_sleep_story_last";

    /** 玩家数据标签：上次触发日期 */
    private static final String TAG_LAST_DAY = "pt_sleep_story_day";

    /** 再生效果持续时间（tick） */
    private static final int REGENERATION_DURATION = 60 * 20;

    /** 幸运效果持续时间（tick） */
    private static final int LUCK_DURATION = 30 * 20;

    /** 速度效果持续时间（tick） */
    private static final int SPEED_DURATION = 20 * 20;

    /**
     * 处理玩家睡醒事件
     *
     * @param event 玩家睡醒事件
     */
    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        // 仅处理服务端玩家
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        var cfg = ModConfig.COMMON;

        // 检查功能是否启用
        if (!cfg.sleepStoryEnabled.get()) {
            return;
        }

        // 随机选择一个故事 (1-18)
        int storyIndex = RNG.nextInt(STORY_COUNT) + 1;
        var tag = player.getPersistentData();
        long now = System.currentTimeMillis();
        long last = tag.getLong(TAG_LAST_TIME);
        long minIntervalMs = cfg.sleepMinIntervalSeconds.get() * 1000L;

        // 如果在冷却时间内，只显示故事不给效果
        if (now - last < minIntervalMs) {
            player.sendSystemMessage(I18n.translate(I18n.getSleepStoryKey(storyIndex)));
            return;
        }

        tag.putLong(TAG_LAST_TIME, now);
        player.sendSystemMessage(I18n.translate(I18n.getSleepStoryKey(storyIndex)));

        // 检查是否启用药水效果
        if (!cfg.sleepEffectsEnabled.get()) {
            return;
        }

        // 每天首次睡醒给予药水效果
        applyDailyEffect(player, tag, now);
    }

    /**
     * 应用每日首次效果
     *
     * @param player 玩家
     * @param tag    玩家持久化数据
     * @param now    当前时间戳
     */
    private void applyDailyEffect(ServerPlayer player, net.minecraft.nbt.CompoundTag tag, long now) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate();
        String lastDay = tag.getString(TAG_LAST_DAY);
        boolean firstToday = !today.toString().equals(lastDay);

        if (!firstToday) {
            return;
        }

        tag.putString(TAG_LAST_DAY, today.toString());

        // 随机选择效果：50% 再生，40% 幸运，10% 速度
        int roll = RNG.nextInt(100);

        if (roll < 50) {
            applyRegeneration(player);
        } else if (roll < 90) {
            applyLuck(player);
        } else {
            applySpeed(player);
        }
    }

    /**
     * 应用再生效果
     */
    private void applyRegeneration(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.REGENERATION)) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGENERATION_DURATION, 0, false, true));
            player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_REGENERATION));
        }
    }

    /**
     * 应用幸运效果
     */
    private void applyLuck(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.LUCK)) {
            player.addEffect(new MobEffectInstance(MobEffects.LUCK, LUCK_DURATION, 0, false, true));
            player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_LUCK));
        }
    }

    /**
     * 应用速度效果
     */
    private void applySpeed(ServerPlayer player) {
        if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION, 0, false, true));
            player.sendSystemMessage(I18n.translate(I18n.SLEEP_EFFECT_SPEED));
        }
    }
}
