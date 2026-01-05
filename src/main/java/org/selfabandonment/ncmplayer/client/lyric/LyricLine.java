package org.selfabandonment.ncmplayer.client.lyric;

/**
 * 歌词行
 *
 * @param timeMs 时间戳（毫秒）
 * @param text   歌词文本
 * @author SelfAbandonment
 */
public record LyricLine(long timeMs, String text) implements Comparable<LyricLine> {

    @Override
    public int compareTo(LyricLine other) {
        return Long.compare(this.timeMs, other.timeMs);
    }
}