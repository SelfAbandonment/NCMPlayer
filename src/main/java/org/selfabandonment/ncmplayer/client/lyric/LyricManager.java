package org.selfabandonment.ncmplayer.client.lyric;

import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 歌词管理器
 *
 * 负责加载、缓存和获取当前歌词
 *
 * @author SelfAbandonment
 */
public final class LyricManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("ncmplayer");

    // 歌词缓存 (songId -> lyrics)
    private static final ConcurrentHashMap<Long, List<LyricLine>> LYRICS_CACHE = new ConcurrentHashMap<>();

    // 当前歌曲 ID
    private static volatile long currentSongId = -1;

    // 当前歌词列表
    private static volatile List<LyricLine> currentLyrics = Collections.emptyList();

    // 是否正在加载
    private static volatile boolean loading = false;

    private LyricManager() {
    }

    /**
     * 加载歌词
     *
     * @param songId  歌曲 ID
     * @param baseUrl API 服务器地址
     */
    public static void loadLyrics(long songId, String baseUrl) {
        // 先清除当前歌词，避免显示旧歌词
        if (songId != currentSongId) {
            currentSongId = songId;
            currentLyrics = Collections.emptyList();
        }

        // 检查缓存
        if (LYRICS_CACHE.containsKey(songId)) {
            currentLyrics = LYRICS_CACHE.get(songId);
            LOGGER.debug("Loaded lyrics from cache for songId={}", songId);
            return;
        }

        if (loading) return;
        loading = true;

        // 异步加载 - 使用较高优先级
        Thread loader = new Thread(() -> {
            try {
                NcmApiClient client = new NcmApiClient(baseUrl);
                String lrcContent = client.getLyric(songId);

                if (lrcContent != null && !lrcContent.isBlank()) {
                    List<LyricLine> lyrics = LrcParser.parse(lrcContent);
                    LYRICS_CACHE.put(songId, lyrics);
                    // 只有当前歌曲ID匹配时才更新
                    if (currentSongId == songId) {
                        currentLyrics = lyrics;
                    }
                    LOGGER.info("Loaded {} lyric lines for songId={}", lyrics.size(), songId);
                } else {
                    if (currentSongId == songId) {
                        currentLyrics = Collections.emptyList();
                    }
                    LOGGER.debug("No lyrics found for songId={}", songId);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to load lyrics for songId={}: {}", songId, e.getMessage());
                currentSongId = songId;
                currentLyrics = Collections.emptyList();
            } finally {
                loading = false;
            }
        }, "ncm-lyric-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * 获取当前歌词（根据播放时间）
     *
     * @param currentTimeMs 当前播放时间（毫秒）
     * @return 当前歌词文本
     */
    public static String getCurrentLyric(long currentTimeMs) {
        return LrcParser.getCurrentLyric(currentLyrics, currentTimeMs);
    }

    /**
     * 获取当前和下一行歌词
     *
     * @param currentTimeMs 当前播放时间（毫秒）
     * @return [当前歌词, 下一行歌词]
     */
    public static String[] getCurrentAndNextLyric(long currentTimeMs) {
        int index = LrcParser.getCurrentLineIndex(currentLyrics, currentTimeMs);
        String current = "";
        String next = "";

        if (index >= 0 && index < currentLyrics.size()) {
            current = currentLyrics.get(index).text();
        }
        if (index + 1 < currentLyrics.size()) {
            next = currentLyrics.get(index + 1).text();
        }

        return new String[]{current, next};
    }

    /**
     * 获取当前歌词列表
     */
    public static List<LyricLine> getCurrentLyrics() {
        return currentLyrics;
    }

    /**
     * 获取当前歌曲 ID
     */
    public static long getCurrentSongId() {
        return currentSongId;
    }

    /**
     * 是否有歌词
     */
    public static boolean hasLyrics() {
        return !currentLyrics.isEmpty();
    }

    /**
     * 是否正在加载
     */
    public static boolean isLoading() {
        return loading;
    }

    /**
     * 清除当前歌词
     */
    public static void clear() {
        currentSongId = -1;
        currentLyrics = Collections.emptyList();
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        LYRICS_CACHE.clear();
        clear();
    }
}

