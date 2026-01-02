package org.demo.portalteleport.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.demo.portalteleport.client.audio.StreamingMp3Player;
import org.demo.portalteleport.ncm.CookieSanitizer;
import org.demo.portalteleport.ncm.NcmApiClient;
import org.demo.portalteleport.ncm.SessionStore;
import org.demo.portalteleport.ncm.SongUrlProvider;

import java.net.URI;

public final class ClientMusicController {

    private static final StreamingMp3Player PLAYER = new StreamingMp3Player();

    // your API
    private static final String DEFAULT_BASE_URL = "http://101.35.114.214:3000";

    private static SongUrlProvider provider;

    private ClientMusicController() {}

    public static StreamingMp3Player player() { return PLAYER; }

    public static void tick() { PLAYER.tick(); }

    public static void stop() { PLAYER.stop(); }

    public static void togglePause() {
        var st = PLAYER.getState();
        if (st == StreamingMp3Player.State.PAUSED) PLAYER.resume();
        else if (st == StreamingMp3Player.State.PLAYING || st == StreamingMp3Player.State.BUFFERING) PLAYER.pause();
    }

    /**
     * Minimal: play by songId. Requires session cookie already saved.
     */
    public static void playSongId(long songId) {
        try {
            ensureProvider();

            String url = provider.getPlayableMp3Url(songId);
            PLAYER.play(URI.create(url));

            msg("Playing songId=" + songId);
        } catch (Exception e) {
            msg("Play failed: " + e.getMessage());
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

        String baseUrl = (session.baseUrl() == null || session.baseUrl().isBlank()) ? DEFAULT_BASE_URL : session.baseUrl();
        NcmApiClient api = new NcmApiClient(baseUrl);
        provider = new SongUrlProvider(api, cookieForApi);
    }

    private static void msg(String s) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[PortalTeleport] " + s), false);
        }
    }
}