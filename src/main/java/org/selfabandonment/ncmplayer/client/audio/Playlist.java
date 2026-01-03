package org.selfabandonment.ncmplayer.client.audio;

import org.selfabandonment.ncmplayer.ncm.NcmApiClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 播放列表管理器
 *
 * @author SelfAbandonment
 */
public final class Playlist {

    private static final List<NcmApiClient.SearchSong> songs = new ArrayList<>();
    private static int currentIndex = -1;
    private static boolean shuffle = false;
    private static RepeatMode repeatMode = RepeatMode.NONE;

    /**
     * 循环模式
     */
    public enum RepeatMode {
        NONE,       // 不循环
        ALL,        // 列表循环
        ONE         // 单曲循环
    }

    private Playlist() {}

    /**
     * 清空播放列表
     */
    public static void clear() {
        songs.clear();
        currentIndex = -1;
    }

    /**
     * 添加歌曲到播放列表
     */
    public static void add(NcmApiClient.SearchSong song) {
        if (!contains(song.id())) {
            songs.add(song);
        }
    }

    /**
     * 添加多首歌曲
     */
    public static void addAll(List<NcmApiClient.SearchSong> newSongs) {
        for (var song : newSongs) {
            add(song);
        }
    }

    /**
     * 移除歌曲
     */
    public static void remove(int index) {
        if (index >= 0 && index < songs.size()) {
            boolean wasCurrentSong = (index == currentIndex);
            songs.remove(index);

            // 调整当前索引
            if (currentIndex > index) {
                currentIndex--;
            } else if (currentIndex >= songs.size()) {
                currentIndex = songs.size() - 1;
            }

            // 如果移除的是当前播放的歌曲，停止播放
            if (wasCurrentSong) {
                MusicController.stop();
                if (songs.isEmpty()) {
                    currentIndex = -1;
                }
            }
        }
    }

    /**
     * 检查是否包含歌曲
     */
    public static boolean contains(long songId) {
        return songs.stream().anyMatch(s -> s.id() == songId);
    }

    /**
     * 获取播放列表
     */
    public static List<NcmApiClient.SearchSong> getSongs() {
        return Collections.unmodifiableList(songs);
    }

    /**
     * 获取列表大小
     */
    public static int size() {
        return songs.size();
    }

    /**
     * 是否为空
     */
    public static boolean isEmpty() {
        return songs.isEmpty();
    }

    /**
     * 获取当前索引
     */
    public static int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * 设置当前索引
     */
    public static void setCurrentIndex(int index) {
        if (index >= -1 && index < songs.size()) {
            currentIndex = index;
        }
    }

    /**
     * 获取当前歌曲
     */
    public static NcmApiClient.SearchSong getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < songs.size()) {
            return songs.get(currentIndex);
        }
        return null;
    }

    /**
     * 播放指定索引的歌曲
     */
    public static void playAt(int index) {
        if (index >= 0 && index < songs.size()) {
            currentIndex = index;
            var song = songs.get(index);
            MusicController.playSongId(song.id(), song.durationMs());
        }
    }

    /**
     * 播放指定歌曲（如果不在列表中则添加）
     */
    public static void play(NcmApiClient.SearchSong song) {
        int idx = indexOf(song.id());
        if (idx < 0) {
            add(song);
            idx = songs.size() - 1;
        }
        playAt(idx);
    }

    /**
     * 查找歌曲索引
     */
    public static int indexOf(long songId) {
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id() == songId) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 播放下一首
     */
    public static void next() {
        if (songs.isEmpty()) return;

        if (repeatMode == RepeatMode.ONE) {
            // 单曲循环：重新播放当前歌曲
            if (currentIndex >= 0) {
                playAt(currentIndex);
            }
            return;
        }

        int nextIndex;
        if (shuffle) {
            // 随机播放：随机选择一首（排除当前歌曲）
            if (songs.size() == 1) {
                nextIndex = 0;
            } else {
                // 从除当前歌曲外的歌曲中随机选择
                int randomOffset = (int) (Math.random() * (songs.size() - 1));
                nextIndex = (currentIndex + 1 + randomOffset) % songs.size();
            }
        } else {
            nextIndex = currentIndex + 1;

            // 检查是否超出范围
            if (nextIndex >= songs.size()) {
                if (repeatMode == RepeatMode.ALL) {
                    nextIndex = 0;
                } else {
                    return; // 播放完毕
                }
            }
        }

        playAt(nextIndex);
    }

    /**
     * 播放上一首
     */
    public static void previous() {
        if (songs.isEmpty()) return;

        int prevIndex;
        if (shuffle) {
            // 随机播放：随机选择一首（排除当前歌曲）
            if (songs.size() == 1) {
                prevIndex = 0;
            } else {
                int randomOffset = (int) (Math.random() * (songs.size() - 1));
                prevIndex = (currentIndex + 1 + randomOffset) % songs.size();
            }
        } else {
            prevIndex = currentIndex - 1;

            if (prevIndex < 0) {
                if (repeatMode == RepeatMode.ALL) {
                    prevIndex = songs.size() - 1;
                } else {
                    prevIndex = 0;
                }
            }
        }

        playAt(prevIndex);
    }

    /**
     * 是否有下一首
     */
    public static boolean hasNext() {
        if (songs.isEmpty()) return false;
        if (repeatMode != RepeatMode.NONE) return true;
        return currentIndex < songs.size() - 1;
    }

    /**
     * 是否有上一首
     */
    public static boolean hasPrevious() {
        if (songs.isEmpty()) return false;
        if (repeatMode != RepeatMode.NONE) return true;
        return currentIndex > 0;
    }

    /**
     * 获取/设置随机播放
     */
    public static boolean isShuffle() { return shuffle; }
    public static void setShuffle(boolean value) { shuffle = value; }
    public static void toggleShuffle() { shuffle = !shuffle; }

    /**
     * 获取/设置循环模式
     */
    public static RepeatMode getRepeatMode() { return repeatMode; }
    public static void setRepeatMode(RepeatMode mode) { repeatMode = mode; }

    /**
     * 切换循环模式
     */
    public static void toggleRepeatMode() {
        repeatMode = switch (repeatMode) {
            case NONE -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.NONE;
        };
    }
}

