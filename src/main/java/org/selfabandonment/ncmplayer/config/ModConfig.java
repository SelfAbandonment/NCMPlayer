package org.selfabandonment.ncmplayer.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NCM Player 模组配置类
 *
 * 配置文件位于：config/ncmplayer-common.toml
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
    }

    /**
     * 通用配置
     */
    public static class CommonConfig {

        /** 网易云 API 服务器地址 */
        public final ModConfigSpec.ConfigValue<String> musicApiUrl;

        /** 默认音量 (0.0 ~ 1.0) */
        public final ModConfigSpec.DoubleValue musicDefaultVolume;

        /** 搜索结果数量限制 */
        public final ModConfigSpec.IntValue musicSearchLimit;

        /**
         * 构造函数
         *
         * @param builder 配置构建器
         */
        public CommonConfig(ModConfigSpec.Builder builder) {
            builder.comment("网易云音乐播放器配置")
                    .push("music");

            musicApiUrl = builder
                    .comment("网易云 API 服务器地址 (需要自建，参考: https://github.com/NeteaseCloudMusicApiEnhanced)")
                    .define("apiUrl", "http://localhost:3000");

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

