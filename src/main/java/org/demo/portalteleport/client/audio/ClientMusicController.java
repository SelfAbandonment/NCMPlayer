package org.demo.portalteleport.client.audio;

import net.minecraft.client.Minecraft;
import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.ncm.CookieSanitizer;
import org.demo.portalteleport.ncm.NcmApiClient;
import org.demo.portalteleport.ncm.SessionStore;
import org.demo.portalteleport.ncm.SongUrlProvider;
import org.demo.portalteleport.util.I18n;

import java.net.URI;

public final class ClientMusicController {

    private static final StreamingMp3Player PLAYER = new StreamingMp3Player();

    private static SongUrlProvider provider;
    private static boolean volumeInitialized = false;

    private ClientMusicController() {}

    public static StreamingMp3Player player() { return PLAYER; }

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

    public static void stop() { PLAYER.stop(); }

    public static void togglePause() {
        var st = PLAYER.getState();
        if (st == StreamingMp3Player.State.PAUSED) PLAYER.resume();
        else if (st == StreamingMp3Player.State.PLAYING || st == StreamingMp3Player.State.BUFFERING) PLAYER.pause();
    }

    public static float getVolume() {
        return PLAYER.getVolume();
    }

    public static void setVolume(float volume) {
        PLAYER.setVolume(volume);
    }

    /**
     * Minimal: play by songId. Requires session cookie already saved.
     */
    public static void playSongId(long songId) {
        try {
            ensureProvider();

            String url = provider.getPlayableMp3Url(songId);
            PLAYER.play(URI.create(url));

            msg(I18n.translateString(I18n.MSG_MUSIC_PLAYING, songId));
        } catch (Exception e) {
            msg(I18n.translateString(I18n.MSG_MUSIC_PLAY_FAILED, e.getMessage()));
            e.printStackTrace();
        }
    }

    private static void ensureProvider() throws Exception {
        if (provider != null) return;

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            throw new IllegalStateException("No saved session. Do QR login first and save cookieForApi.");
        }

        String cookieForApi = session.cookieForApi();
        if (!CookieSanitizer.hasMusicU(cookieForApi)) {
            throw new IllegalStateException("Session cookie missing MUSIC_U. QR login not completed or cookie sanitize failed.");
        }

        String baseUrl = session.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = ModConfig.COMMON.musicApiUrl.get();
        }
        NcmApiClient api = new NcmApiClient(baseUrl);
        provider = new SongUrlProvider(api, cookieForApi);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("[PortalTeleport] " + s), false);
        }
    }
}