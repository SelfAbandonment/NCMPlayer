package org.demo.portalteleport.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/*** PortalTeleport 模组配置类
 * 使用 NeoForge 的配置系统，配置文件位于：
 *   config/portalteleport-common.toml
 * 配置分为四个部分：
 * - [gate] - 传送门限制配置
 * - [fishing] - 钓鱼加成配置
 * - [sleep] - 睡前故事配置
 * - [music] - 网易云音乐配置
 *
 * @author SelfAbandonment
 */
public final class ModConfig {

    /** 通用配置实例 */
    public static final CommonConfig COMMON;

    /** 通用配置规范 */
    public static final ModConfigSpec COMMON_SPEC;

    static {
        Pair<CommonConfig, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = pair.getLeft();
        COMMON_SPEC = pair.getRight();
    }

    private ModConfig() {
        // 工具类，禁止实例化
    }

    /**
     * 通用配置（服务端和客户端共享）
     */
    public static class CommonConfig {

        // ==================== 传送门限制 ====================

        /** 是否启用传送门限制功能 */
        public final ModConfigSpec.BooleanValue gateBlockEnabled;

        /** 是否阻止掉落物穿越传送门 */
        public final ModConfigSpec.BooleanValue gateBlockItems;

        /** 是否阻止船穿越传送门 */
        public final ModConfigSpec.BooleanValue gateBlockBoats;

        /** 是否阻止矿车穿越传送门 */
        public final ModConfigSpec.BooleanValue gateBlockMinecarts;

        /** 通知玩家的半径（方块） */
        public final ModConfigSpec.DoubleValue gateNotifyRadius;

        /** 通知冷却时间（毫秒） */
        public final ModConfigSpec.IntValue gateNotifyCooldownMs;

        // ==================== 钓鱼加成 ====================

        /** 是否启用钓鱼加成功能 */
        public final ModConfigSpec.BooleanValue fishingBonusEnabled;

        /** 获得额外奖励的几率 (0.0 ~ 1.0) */
        public final ModConfigSpec.DoubleValue fishingBonusChance;

        /** 额外奖励的物品 ID */
        public final ModConfigSpec.ConfigValue<String> fishingBonusItem;

        /** 额外奖励的数量 */
        public final ModConfigSpec.IntValue fishingBonusCount;

        // ==================== 睡前故事 ====================

        /** 是否启用睡前故事功能 */
        public final ModConfigSpec.BooleanValue sleepStoryEnabled;

        /** 最小触发间隔（秒） */
        public final ModConfigSpec.IntValue sleepMinIntervalSeconds;

        /** 是否给予药水效果 */
        public final ModConfigSpec.BooleanValue sleepEffectsEnabled;

        // ==================== 音乐播放器 ====================

        /** 网易云 API 服务器地址 */
        public final ModConfigSpec.ConfigValue<String> musicApiUrl;

        /** 默认音量 (0.0 ~ 1.0) */
        public final ModConfigSpec.DoubleValue musicDefaultVolume;

        /** 搜索结果数量限制 */
        public final ModConfigSpec.IntValue musicSearchLimit;

        /**
         * 构造函数，定义所有配置项
         *
         * @param builder 配置构建器
         */
        public CommonConfig(ModConfigSpec.Builder builder) {
            // ==================== 传送门限制 ====================
            builder.comment("传送门限制配置 - 控制实体穿越地狱门/末地门的行为")
                    .push("gate");

            gateBlockEnabled = builder
                    .comment("是否启用传送门限制功能")
                    .define("enabled", true);

            gateBlockItems = builder
                    .comment("是否阻止掉落物穿越传送门")
                    .define("blockItems", true);

            gateBlockBoats = builder
                    .comment("是否阻止船穿越传送门")
                    .define("blockBoats", true);

            gateBlockMinecarts = builder
                    .comment("是否阻止矿车穿越传送门")
                    .define("blockMinecarts", true);

            gateNotifyRadius = builder
                    .comment("通知玩家的半径（方块）")
                    .defineInRange("notifyRadius", 24.0, 1.0, 128.0);

            gateNotifyCooldownMs = builder
                    .comment("通知冷却时间（毫秒）")
                    .defineInRange("notifyCooldownMs", 5000, 1000, 60000);

            builder.pop();

            // ==================== 钓鱼加成 ====================
            builder.comment("钓鱼加成配置 - 钓鱼时有几率获得额外奖励")
                    .push("fishing");

            fishingBonusEnabled = builder
                    .comment("是否启用钓鱼加成功能")
                    .define("enabled", true);

            fishingBonusChance = builder
                    .comment("获得额外奖励的几率 (0.0 ~ 1.0)")
                    .defineInRange("bonusChance", 0.25, 0.0, 1.0);

            fishingBonusItem = builder
                    .comment("额外奖励的物品ID (如 minecraft:cod, minecraft:salmon)")
                    .define("bonusItem", "minecraft:cod");

            fishingBonusCount = builder
                    .comment("额外奖励的数量")
                    .defineInRange("bonusCount", 1, 1, 64);

            builder.pop();

            // ==================== 睡前故事 ====================
            builder.comment("睡前故事配置 - 睡醒后显示温馨提示和增益效果")
                    .push("sleep");

            sleepStoryEnabled = builder
                    .comment("是否启用睡前故事功能")
                    .define("enabled", true);

            sleepMinIntervalSeconds = builder
                    .comment("最小触发间隔（秒）")
                    .defineInRange("minIntervalSeconds", 120, 10, 3600);

            sleepEffectsEnabled = builder
                    .comment("是否给予药水效果")
                    .define("effectsEnabled", true);

            builder.pop();

            // ==================== 音乐播放器 ====================
            builder.comment("网易云音乐播放器配置")
                    .push("music");

            musicApiUrl = builder
                    .comment("网易云 API 服务器地址")
                    .define("apiUrl", "http://101.35.114.214:3000");

            musicDefaultVolume = builder
                    .comment("默认音量 (0.0 ~ 1.0)")
                    .defineInRange("defaultVolume", 0.8, 0.0, 1.0);

            musicSearchLimit = builder
                    .comment("搜索结果数量限制")
                    .defineInRange("searchLimit", 30, 5, 100);

            builder.pop();
        }
    }
}

