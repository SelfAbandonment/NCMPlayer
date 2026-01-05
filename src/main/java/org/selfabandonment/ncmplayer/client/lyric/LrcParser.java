package org.selfabandonment.ncmplayer.client.lyric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC 歌词解析器
 *
 * @author SelfAbandonment
 */
public final class LrcParser {

    // LRC 时间标签正则: [mm:ss.xx] 或 [mm:ss:xx] 或 [mm:ss]
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})([.:]?(\\d{1,3}))?]");

    private LrcParser() {
    }

    /**
     * 解析 LRC 格式歌词
     *
     * @param lrcContent LRC 歌词内容
     * @return 按时间排序的歌词行列表
     */
    public static List<LyricLine> parse(String lrcContent) {
        if (lrcContent == null || lrcContent.isBlank()) {
            return Collections.emptyList();
        }

        List<LyricLine> lines = new ArrayList<>();
        String[] rawLines = lrcContent.split("\n");

        for (String rawLine : rawLines) {
            rawLine = rawLine.trim();
            if (rawLine.isEmpty()) continue;

            // 查找所有时间标签
            Matcher matcher = TIME_PATTERN.matcher(rawLine);
            List<Long> times = new ArrayList<>();
            int lastMatchEnd = 0;

            while (matcher.find()) {
                int minutes = Integer.parseInt(matcher.group(1));
                int seconds = Integer.parseInt(matcher.group(2));
                int millis = 0;

                String millisStr = matcher.group(4);
                if (millisStr != null && !millisStr.isEmpty()) {
                    millis = Integer.parseInt(millisStr);
                    // 如果是两位数，转换为毫秒
                    if (millisStr.length() == 2) {
                        millis *= 10;
                    } else if (millisStr.length() == 1) {
                        millis *= 100;
                    }
                }

                long timeMs = minutes * 60 * 1000L + seconds * 1000L + millis;
                times.add(timeMs);
                lastMatchEnd = matcher.end();
            }

            // 提取歌词文本（时间标签之后的部分）
            String text = "";
            if (lastMatchEnd > 0 && lastMatchEnd < rawLine.length()) {
                text = rawLine.substring(lastMatchEnd).trim();
            }

            // 为每个时间标签创建歌词行
            for (Long time : times) {
                if (!text.isEmpty()) {
                    lines.add(new LyricLine(time, text));
                }
            }
        }

        // 按时间排序
        Collections.sort(lines);
        return lines;
    }

    /**
     * 根据当前播放时间获取当前歌词行索引
     *
     * @param lyrics      歌词列表
     * @param currentTime 当前播放时间（毫秒）
     * @return 当前歌词行索引，如果没有找到返回 -1
     */
    public static int getCurrentLineIndex(List<LyricLine> lyrics, long currentTime) {
        if (lyrics == null || lyrics.isEmpty()) {
            return -1;
        }

        int index = -1;
        for (int i = 0; i < lyrics.size(); i++) {
            if (lyrics.get(i).timeMs() <= currentTime) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    /**
     * 根据当前播放时间获取当前歌词文本
     *
     * @param lyrics      歌词列表
     * @param currentTime 当前播放时间（毫秒）
     * @return 当前歌词文本，如果没有找到返回空字符串
     */
    public static String getCurrentLyric(List<LyricLine> lyrics, long currentTime) {
        int index = getCurrentLineIndex(lyrics, currentTime);
        if (index >= 0 && index < lyrics.size()) {
            return lyrics.get(index).text();
        }
        return "";
    }
}

