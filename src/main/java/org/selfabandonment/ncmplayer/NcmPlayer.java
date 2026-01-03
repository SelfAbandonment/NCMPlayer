package org.selfabandonment.ncmplayer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig.Type;

import org.selfabandonment.ncmplayer.config.ModConfig;

/**
 * NCM Player 模组主类
 *
 * 网易云音乐播放器 - 在 Minecraft 中播放网易云音乐
 *
 * 功能：
 * - 扫码登录网易云账号
 * - 搜索歌曲
 * - 流式播放音乐
 * - 音量控制
 *
 * @author SelfAbandonment
 */
@Mod(NcmPlayer.MODID)
public class NcmPlayer {

    /** 模组 ID */
    public static final String MODID = "ncmplayer";

    /**
     * 模组构造函数
     *
     * @param modEventBus  模组事件总线
     * @param modContainer 模组容器
     */
    @SuppressWarnings("unused")
    public NcmPlayer(IEventBus modEventBus, ModContainer modContainer) {
        // 注册配置
        modContainer.registerConfig(Type.COMMON, ModConfig.COMMON_SPEC);
    }
}

