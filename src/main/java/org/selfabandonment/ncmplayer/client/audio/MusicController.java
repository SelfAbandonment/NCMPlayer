package org.selfabandonment.ncmplayer.client.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.NcmPlayer;
import org.selfabandonment.ncmplayer.client.lyric.LyricManager;
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
    private static boolean wasPlaying = false;
    private static boolean manualStop = false;  // 标记是否是手动停止（不触发自动下一首）

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

        // 检测播放结束，自动播放下一首
        var state = PLAYER.getState();

        // 当前是否在播放
        boolean isPlaying = (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING);
        // 是否已停止（播放结束）- 同时检查 STOPPED 和 IDLE
        boolean isStopped = (state == StreamingMp3Player.State.STOPPED || state == StreamingMp3Player.State.IDLE);

        // 先检查是否需要自动下一首（之前在播放，现在停止了，且不是手动停止）
        if (wasPlaying && isStopped) {
            wasPlaying = false;

            // 如果是手动停止，不自动下一首
            if (manualStop) {
                manualStop = false;
                LOGGER.info("Manual stop, not auto-playing next");
            } else {
                LOGGER.info("Song finished, state={}, playlist size={}", state, Playlist.size());
                autoPlayNext();
            }
        }
        // 再更新播放状态
        else if (isPlaying) {
            wasPlaying = true;
            manualStop = false;  // 开始播放时重置标志
        }
        // 暂停状态不改变 wasPlaying
    }

    /**
     * 自动播放下一首
     */
    private static void autoPlayNext() {
        if (Playlist.size() <= 0) {
            LOGGER.info("autoPlayNext: playlist is empty");
            return;
        }

        var repeatMode = Playlist.getRepeatMode();
        boolean shuffle = Playlist.isShuffle();
        int currentIdx = Playlist.getCurrentIndex();
        int size = Playlist.size();

        LOGGER.info("autoPlayNext: repeatMode={}, shuffle={}, currentIdx={}, size={}", repeatMode, shuffle, currentIdx, size);

        if (repeatMode == Playlist.RepeatMode.ONE) {
            // 单曲循环：重新播放当前歌曲
            if (currentIdx >= 0 && currentIdx < size) {
                LOGGER.info("autoPlayNext: single repeat, playing index {}", currentIdx);
                Playlist.playAt(currentIdx);
            }
        } else if (shuffle) {
            // 随机播放：随机选择一首歌（排除当前歌曲）
            int nextIdx;
            if (size == 1) {
                nextIdx = 0;
            } else {
                // 从除当前歌曲外的歌曲中随机选择
                int randomOffset = (int) (Math.random() * (size - 1));
                nextIdx = (currentIdx + 1 + randomOffset) % size;
            }
            LOGGER.info("autoPlayNext: shuffle, playing index {}", nextIdx);
            Playlist.playAt(nextIdx);
        } else if (repeatMode == Playlist.RepeatMode.ALL) {
            // 列表循环：播放下一首（自动循环到第一首）
            int nextIdx = (currentIdx + 1) % size;
            LOGGER.info("autoPlayNext: list repeat, playing index {}", nextIdx);
            Playlist.playAt(nextIdx);
        } else {
            // 不循环：还有下一首就播放
            if (currentIdx < size - 1) {
                LOGGER.info("autoPlayNext: no repeat, playing index {}", currentIdx + 1);
                Playlist.playAt(currentIdx + 1);
            } else {
                LOGGER.info("autoPlayNext: no repeat, reached end of playlist");
            }
        }
    }

    /**
     * 停止播放（自然停止，可能触发自动下一首）
     */
    public static void stop() {
        PLAYER.stop();
    }

    /**
     * 手动停止播放（不触发自动下一首）
     */
    public static void stopManually() {
        manualStop = true;
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
     * 暂停播放
     */
    public static void pause() {
        PLAYER.pause();
    }

    /**
     * 继续播放
     */
    public static void resume() {
        PLAYER.resume();
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
    public static void setVolume(float volume) {
        PLAYER.setVolume(volume);
    }

    /**
     * 获取播放状态
     */
    public static StreamingMp3Player.State getState() {
        return PLAYER.getState();
    }

    /**
     * 是否正在播放
     */
    public static boolean isPlaying() {
        return PLAYER.isPlaying();
    }

    /**
     * 是否暂停中
     */
    public static boolean isPaused() {
        return PLAYER.getState() == StreamingMp3Player.State.PAUSED;
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    public static long getPlayedMs() {
        return PLAYER.getPlayedMs();
    }

    /**
     * 获取预估总时长（毫秒）
     */
    public static long getDurationMs() {
        return PLAYER.getDurationMs();
    }

    /**
     * 是否有已知的精确时长
     */
    public static boolean hasKnownDuration() {
        return PLAYER.hasKnownDuration();
    }

    /**
     * 检查解码是否完成
     */
    public static boolean isDecodingComplete() {
        return PLAYER.isDecodingComplete();
    }

    /**
     * 获取播放进度（0.0 ~ 1.0）
     */
    public static float getProgress() {
        return PLAYER.getProgress();
    }

    /**
     * 跳转到指定进度
     * @param progress 进度 (0.0 ~ 1.0)
     */
    public static void seekToProgress(float progress) {
        PLAYER.seekToProgress(progress);
    }

    /**
     * 跳转到指定时间
     * @param targetMs 目标时间（毫秒）
     */
    public static void seek(long targetMs) {
        PLAYER.seek(targetMs);
    }

    /**
     * 是否支持跳转
     */
    public static boolean canSeek() {
        return PLAYER.canSeek();
    }

    /**
     * 格式化时间为 mm:ss
     */
    public static String formatTime(long ms) {
        if (ms < 0) ms = 0;
        long totalSeconds = ms / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    /**
     * 播放指定歌曲（带时长）
     */
    public static void playSongId(long songId, long durationMs) {
        try {
            ensureProvider();

            String url = provider.getPlayableMp3Url(songId);
            PLAYER.play(URI.create(url));

            // 设置已知的精确时长
            if (durationMs > 0) {
                PLAYER.setKnownDuration(durationMs);
            }

            // 加载歌词
            loadLyricsForSong(songId);

            // 不再在聊天中显示播放消息
            // sendMessage(I18n.translateString(I18n.MSG_MUSIC_PLAYING, songId));
        } catch (Exception e) {
            sendMessage(I18n.translateString(I18n.MSG_MUSIC_PLAY_FAILED, e.getMessage()));
            LOGGER.error("Failed to play song {}", songId, e);
        }
    }

    /**
     * 播放指定歌曲（不带时长，向后兼容）
     */
    public static void playSongId(long songId) {
        playSongId(songId, 0);
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

    /**
     * 加载歌词
     */
    private static void loadLyricsForSong(long songId) {
        String baseUrl = ModConfig.COMMON.musicApiUrl.get();
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session != null && session.baseUrl() != null && !session.baseUrl().isBlank()) {
            baseUrl = session.baseUrl();
        }
        LyricManager.loadLyrics(songId, baseUrl);
    }

    /**
     * 获取当前歌词
     */
    public static String getCurrentLyric() {
        long playedMs = getPlayedMs();
        return LyricManager.getCurrentLyric(playedMs);
    }

    /**
     * 获取当前和下一行歌词
     */
    public static String[] getCurrentAndNextLyric() {
        long playedMs = getPlayedMs();
        return LyricManager.getCurrentAndNextLyric(playedMs);
    }

    /**
     * 是否有歌词
     */
    public static boolean hasLyrics() {
        return LyricManager.hasLyrics();
    }

    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("[NCM Player] " + message), false);
        }
    }
}