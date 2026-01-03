package org.selfabandonment.ncmplayer.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.NcmPlayer;
import org.selfabandonment.ncmplayer.config.ModConfig;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;
import org.selfabandonment.ncmplayer.ncm.SongUrlProvider;
import org.selfabandonment.ncmplayer.util.I18n;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * 音乐播放控制器
 *
 * @author SelfAbandonment
 */
public final class MusicController {

    private static final Logger LOGGER = LoggerFactory.getLogger(NcmPlayer.MODID);
    private static final StreamingMp3Player PLAYER = new StreamingMp3Player();

    private static SongUrlProvider provider;
    private static boolean volumeInitialized = false;

    private MusicController() {
    }

    /**
     * 获取播放器实例
     */
    @SuppressWarnings("unused")
    public static StreamingMp3Player player() {
        return PLAYER;
    }

    /**
     * 每帧更新
     */
    public static void tick() {
        if (!volumeInitialized) {
            try {
                float defaultVol = ModConfig.COMMON.musicDefaultVolume.get().floatValue();
                PLAYER.setVolume(defaultVol);
                volumeInitialized = true;
            } catch (Exception ignored) {
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
     * 切换暂停/播放
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
     */
    @SuppressWarnings("unused")
    public static float getVolume() {
        return PLAYER.getVolume();
    }

    /**
     * 设置音量
     */
    @SuppressWarnings("unused")
    public static void setVolume(float volume) {
        PLAYER.setVolume(volume);
    }

    /**
     * 播放指定歌曲
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

    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[NCM Player] " + message), false);
        }
    }
}

