package org.demo.portalteleport.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.demo.portalteleport.Portalteleport;
import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.ncm.CookieSanitizer;
import org.demo.portalteleport.ncm.NcmApiClient;
import org.demo.portalteleport.ncm.SessionStore;
import org.demo.portalteleport.ncm.SongUrlProvider;
import org.demo.portalteleport.util.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * 客户端音乐控制器
 * 提供网易云音乐播放的核心控制功能：
 * - 播放指定歌曲（通过 songId）
 * - 暂停/继续播放
 * - 停止播放
 * - 音量控制
 * 使用方法：
 *   ClientMusicController.playSongId(12345);  // 播放歌曲
 *   ClientMusicController.togglePause();       // 暂停/继续
 *   ClientMusicController.stop();              // 停止
 *   ClientMusicController.setVolume(0.5f);     // 设置音量
 *
 * @author SelfAbandonment
 */
public final class ClientMusicController {

    private static final Logger LOGGER = LoggerFactory.getLogger(Portalteleport.MODID);

    /** 流式 MP3 播放器实例 */
    private static final StreamingMp3Player PLAYER = new StreamingMp3Player();

    /** 歌曲 URL 提供者（延迟初始化） */
    private static SongUrlProvider provider;

    /** 是否已初始化默认音量 */
    private static boolean volumeInitialized = false;

    private ClientMusicController() {
        // 工具类，禁止实例化
    }

    /**
     * 获取播放器实例
     *
     * @return 流式 MP3 播放器
     */
    @SuppressWarnings("unused") // 公开 API，供外部或未来使用
    public static StreamingMp3Player player() {
        return PLAYER;
    }

    /**
     * 每帧更新（由客户端 tick 事件调用）
     * 负责：
     * - 首次初始化默认音量
     * - 更新播放器状态
     */
    public static void tick() {
        // 首次初始化音量
        if (!volumeInitialized) {
            try {
                float defaultVol = ModConfig.COMMON.musicDefaultVolume.get().floatValue();
                PLAYER.setVolume(defaultVol);
                volumeInitialized = true;
            } catch (Exception ignored) {
                // 配置可能还未加载
            }
        }
        PLAYER.tick();
    }

    /**
     * 停止播放
     */
    public static void stop() {
        PLAYER.stop();
    }

    /**
     * 切换暂停/播放状态
     */
    public static void togglePause() {
        var state = PLAYER.getState();
        if (state == StreamingMp3Player.State.PAUSED) {
            PLAYER.resume();
        } else if (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING) {
            PLAYER.pause();
        }
    }

    /**
     * 获取当前音量
     *
     * @return 音量值 (0.0 ~ 1.0)
     */
    @SuppressWarnings("unused") // 公开 API，供 GUI 音量滑块使用
    public static float getVolume() {
        return PLAYER.getVolume();
    }

    /**
     * 设置音量
     *
     * @param volume 音量值 (0.0 ~ 1.0)
     */
    @SuppressWarnings("unused") // 公开 API，供 GUI 音量滑块使用
    public static void setVolume(float volume) {
        PLAYER.setVolume(volume);
    }

    /**
     * 播放指定歌曲
     * <p>
     * 需要先通过 QR 登录保存会话 cookie。
     *
     * @param songId 网易云音乐歌曲 ID
     */
    public static void playSongId(long songId) {
        try {
            ensureProvider();

            String url = provider.getPlayableMp3Url(songId);
            PLAYER.play(URI.create(url));

            sendMessage(I18n.translateString(I18n.MSG_MUSIC_PLAYING, songId));
        } catch (Exception e) {
            sendMessage(I18n.translateString(I18n.MSG_MUSIC_PLAY_FAILED, e.getMessage()));
            LOGGER.error("Failed to play song {}", songId, e);
        }
    }

    /**
     * 确保 URL 提供者已初始化
     *
     * @throws IllegalStateException 如果未登录或登录信息无效
     */
    private static void ensureProvider() {
        if (provider != null) {
            return;
        }

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            throw new IllegalStateException("No saved session. Do QR login first.");
        }

        String cookieForApi = session.cookieForApi();
        if (!CookieSanitizer.hasMusicU(cookieForApi)) {
            throw new IllegalStateException("Session cookie missing MUSIC_U.");
        }

        String baseUrl = session.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ModConfig.COMMON.musicApiUrl.get();
        }

        NcmApiClient api = new NcmApiClient(baseUrl);
        provider = new SongUrlProvider(api, cookieForApi);
    }

    /**
     * 向玩家发送聊天消息
     *
     * @param message 消息内容
     */
    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[PortalTeleport] " + message), false);
        }
    }
}