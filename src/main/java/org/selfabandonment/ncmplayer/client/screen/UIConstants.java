package org.selfabandonment.ncmplayer.client.screen;

import net.minecraft.util.FastColor;

/**
 * UI 常量和颜色定义
 *
 * @author SelfAbandonment
 */
public final class UIConstants {

    private UIConstants() {
    }

    // 主题颜色
    public static final int COLOR_BG = FastColor.ARGB32.color(245, 24, 24, 28);
    public static final int COLOR_PANEL = FastColor.ARGB32.color(255, 32, 32, 38);
    public static final int COLOR_ACCENT = FastColor.ARGB32.color(255, 236, 65, 65);
    public static final int COLOR_ACCENT_DIM = FastColor.ARGB32.color(255, 180, 50, 50);
    public static final int COLOR_TEXT = 0xFFFFFF;
    public static final int COLOR_TEXT_DIM = 0x888888;
    public static final int COLOR_TEXT_SUCCESS = 0x66FF66;
    public static final int COLOR_SLIDER_BG = FastColor.ARGB32.color(200, 50, 50, 55);
    public static final int COLOR_SLIDER_HANDLE = FastColor.ARGB32.color(255, 255, 255, 255);

    // 布局
    public static final int HEADER_HEIGHT = 35;
    public static final int FOOTER_HEIGHT = 65;

    /**
     * 截断文本
     */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 2) + "…" : s;
    }

    /**
     * 检查点是否在矩形内
     */
    public static boolean isInRect(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }
}

