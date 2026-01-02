package org.demo.portalteleport.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 国际化翻译工具类
 * 提供便捷的方法来获取翻译后的文本组件
 */
public final class I18n {

    private I18n() {}

    /**
     * 获取翻译组件
     * @param key 翻译键
     * @return 翻译后的组件
     */
    public static MutableComponent translate(String key) {
        return Component.translatable(key);
    }

    /**
     * 获取带参数的翻译组件
     * @param key 翻译键
     * @param args 参数
     * @return 翻译后的组件
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 获取翻译后的字符串（仅客户端可用）
     * 注意：在服务端使用时会返回翻译键本身
     * @param key 翻译键
     * @return 翻译后的字符串
     */
    public static String translateString(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * 获取带参数的翻译字符串（仅客户端可用）
     * @param key 翻译键
     * @param args 参数
     * @return 翻译后的字符串
     */
    public static String translateString(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ============ 常用翻译键常量 ============

    // GUI - Music Screen
    public static final String MUSIC_TITLE = "gui.portalteleport.music.title";
    public static final String MUSIC_BTN_QR_LOGIN = "gui.portalteleport.music.btn.qr_login";
    public static final String MUSIC_BTN_BACK_SEARCH = "gui.portalteleport.music.btn.back_search";
    public static final String MUSIC_BTN_SEARCH = "gui.portalteleport.music.btn.search";
    public static final String MUSIC_BTN_PAUSE = "gui.portalteleport.music.btn.pause";
    public static final String MUSIC_BTN_STOP = "gui.portalteleport.music.btn.stop";
    public static final String MUSIC_BTN_LOGOUT = "gui.portalteleport.music.btn.logout";
    public static final String MUSIC_BTN_REFRESH_QR = "gui.portalteleport.music.btn.refresh_qr";
    public static final String MUSIC_HINT_SEARCH = "gui.portalteleport.music.hint.search";
    public static final String MUSIC_INFO_WELCOME = "gui.portalteleport.music.info.welcome";
    public static final String MUSIC_INFO_LOGGED_IN = "gui.portalteleport.music.info.logged_in";
    public static final String MUSIC_INFO_LOGIN_INCOMPLETE = "gui.portalteleport.music.info.login_incomplete";
    public static final String MUSIC_INFO_LOGGED_OUT = "gui.portalteleport.music.info.logged_out";
    public static final String MUSIC_INFO_SEARCHING = "gui.portalteleport.music.info.searching";
    public static final String MUSIC_INFO_SEARCH_FAILED = "gui.portalteleport.music.info.search_failed";
    public static final String MUSIC_INFO_NO_RESULTS = "gui.portalteleport.music.info.no_results";
    public static final String MUSIC_INFO_FOUND_SONGS = "gui.portalteleport.music.info.found_songs";
    public static final String MUSIC_INFO_FOUND_SONGS_SCROLL = "gui.portalteleport.music.info.found_songs_scroll";
    public static final String MUSIC_INFO_NOW_PLAYING = "gui.portalteleport.music.info.now_playing";
    public static final String MUSIC_ERROR_NOT_LOGGED_IN = "gui.portalteleport.music.error.not_logged_in";
    public static final String MUSIC_ERROR_LOGIN_EXPIRED = "gui.portalteleport.music.error.login_expired";
    public static final String MUSIC_ERROR_EMPTY_KEYWORD = "gui.portalteleport.music.error.empty_keyword";
    public static final String MUSIC_ERROR_CLEAR_FAILED = "gui.portalteleport.music.error.clear_failed";
    public static final String MUSIC_QR_NOT_STARTED = "gui.portalteleport.music.qr.not_started";
    public static final String MUSIC_QR_GENERATING = "gui.portalteleport.music.qr.generating";
    public static final String MUSIC_QR_SCAN_CONFIRM = "gui.portalteleport.music.qr.scan_confirm";
    public static final String MUSIC_QR_RENDER_FAILED = "gui.portalteleport.music.qr.render_failed";
    public static final String MUSIC_QR_RENDER_FAILED_SHORT = "gui.portalteleport.music.qr.render_failed_short";
    public static final String MUSIC_QR_GENERATE_FAILED = "gui.portalteleport.music.qr.generate_failed";
    public static final String MUSIC_QR_WAITING_SCAN = "gui.portalteleport.music.qr.waiting_scan";
    public static final String MUSIC_QR_SCANNED_CONFIRM = "gui.portalteleport.music.qr.scanned_confirm";
    public static final String MUSIC_QR_EXPIRED_REFRESH = "gui.portalteleport.music.qr.expired_refresh";
    public static final String MUSIC_QR_LOGIN_SUCCESS_SAVING = "gui.portalteleport.music.qr.login_success_saving";
    public static final String MUSIC_QR_STATUS = "gui.portalteleport.music.qr.status";
    public static final String MUSIC_QR_SAVED_OK = "gui.portalteleport.music.qr.saved_ok";
    public static final String MUSIC_QR_SAVED_MISSING = "gui.portalteleport.music.qr.saved_missing";
    public static final String MUSIC_QR_SAVE_FAILED = "gui.portalteleport.music.qr.save_failed";
    public static final String MUSIC_QR_SAVE_FAILED_SHORT = "gui.portalteleport.music.qr.save_failed_short";
    public static final String MUSIC_QR_LOADING = "gui.portalteleport.music.qr.loading";

    // Commands - Gate
    public static final String GATE_NO_PERMISSION = "command.portalteleport.gate.no_permission";
    public static final String GATE_ENABLED = "command.portalteleport.gate.enabled";
    public static final String GATE_DISABLED = "command.portalteleport.gate.disabled";
    public static final String GATE_STATUS = "command.portalteleport.gate.status";
    public static final String GATE_STATUS_ENABLED = "command.portalteleport.gate.status.enabled";
    public static final String GATE_STATUS_DISABLED = "command.portalteleport.gate.status.disabled";
    public static final String GATE_STATUS_ON = "command.portalteleport.gate.status.on";
    public static final String GATE_STATUS_OFF = "command.portalteleport.gate.status.off";
    public static final String GATE_INFO = "command.portalteleport.gate.info";
    public static final String GATE_INFO_ITEMS = "command.portalteleport.gate.info.items";
    public static final String GATE_INFO_BOATS = "command.portalteleport.gate.info.boats";
    public static final String GATE_INFO_MINECARTS = "command.portalteleport.gate.info.minecarts";
    public static final String GATE_RELOADED = "command.portalteleport.gate.reloaded";
    public static final String GATE_BLOCKED = "command.portalteleport.gate.blocked";

    // Events - Fishing
    public static final String FISHING_BONUS = "event.portalteleport.fishing.bonus";

    // Events - Sleep
    public static final String SLEEP_EFFECT_REGENERATION = "event.portalteleport.sleep.effect.regeneration";
    public static final String SLEEP_EFFECT_LUCK = "event.portalteleport.sleep.effect.luck";
    public static final String SLEEP_EFFECT_SPEED = "event.portalteleport.sleep.effect.speed";

    // Messages - Music
    public static final String MSG_MUSIC_PLAYING = "message.portalteleport.music.playing";
    public static final String MSG_MUSIC_PLAY_FAILED = "message.portalteleport.music.play_failed";

    /**
     * 获取睡前故事翻译键
     * @param index 故事索引 (1-18)
     * @return 翻译键
     */
    public static String getSleepStoryKey(int index) {
        return "event.portalteleport.sleep.story." + index;
    }
}

