package org.selfabandonment.ncmplayer.ncm;

import java.util.List;

/**
 * 歌曲 URL 提供者
 *
 * @author SelfAbandonment
 */
public final class SongUrlProvider {

    private static final List<String> LEVELS = List.of("lossless", "exhigh", "higher", "standard");

    private final NcmApiClient api;
    private final String cookieForApi;

    private long cachedSongId = -1;
    private String cachedUrl = null;
    private long cachedExpiresAt = 0;

    public SongUrlProvider(NcmApiClient api, String cookieForApi) {
        this.api = api;
        this.cookieForApi = cookieForApi;
    }

    public synchronized String getPlayableMp3Url(long songId) throws Exception {
        long now = System.currentTimeMillis();

        if (songId == cachedSongId && cachedUrl != null && now < (cachedExpiresAt - 5000)) {
            return cachedUrl;
        }

        Exception last = null;
        for (String level : LEVELS) {
            try {
                var r = api.songUrlV1(songId, level, cookieForApi);
                if (!r.ok()) continue;

                if (r.type() != null && !r.type().equalsIgnoreCase("mp3")) {
                    continue;
                }

                cachedSongId = songId;
                cachedUrl = r.url();
                cachedExpiresAt = r.expiresAtEpochMs(now);
                return cachedUrl;
            } catch (Exception e) {
                last = e;
            }
        }

        if (last != null) throw last;
        throw new IllegalStateException("No playable URL for songId=" + songId);
    }
}

