package org.selfabandonment.ncmplayer.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 国际化翻译工具类
 *
 * @author SelfAbandonment
 */
public final class I18n {

    private I18n() {
    }

    /**
     * 获取翻译组件
     */
    public static MutableComponent translate(String key) {
        return Component.translatable(key);
    }

    /**
     * 获取带参数的翻译组件
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 获取翻译字符串
     */
    public static String translateString(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * 获取带参数的翻译字符串
     */
    public static String translateString(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ==================== 音乐播放器 GUI ====================

    public static final String MUSIC_TITLE = "gui.ncmplayer.music.title";
    public static final String MUSIC_BTN_QR_LOGIN = "gui.ncmplayer.music.btn.qr_login";
    public static final String MUSIC_BTN_BACK_SEARCH = "gui.ncmplayer.music.btn.back_search";
    public static final String MUSIC_BTN_SEARCH = "gui.ncmplayer.music.btn.search";
    public static final String MUSIC_BTN_PAUSE = "gui.ncmplayer.music.btn.pause";
    public static final String MUSIC_BTN_STOP = "gui.ncmplayer.music.btn.stop";
    public static final String MUSIC_BTN_LOGOUT = "gui.ncmplayer.music.btn.logout";
    public static final String MUSIC_BTN_REFRESH_QR = "gui.ncmplayer.music.btn.refresh_qr";
    public static final String MUSIC_HINT_SEARCH = "gui.ncmplayer.music.hint.search";
    public static final String MUSIC_INFO_WELCOME = "gui.ncmplayer.music.info.welcome";
    public static final String MUSIC_INFO_LOGGED_IN = "gui.ncmplayer.music.info.logged_in";
    public static final String MUSIC_INFO_LOGIN_INCOMPLETE = "gui.ncmplayer.music.info.login_incomplete";
    public static final String MUSIC_INFO_LOGGED_OUT = "gui.ncmplayer.music.info.logged_out";
    public static final String MUSIC_INFO_SEARCHING = "gui.ncmplayer.music.info.searching";
    public static final String MUSIC_INFO_SEARCH_FAILED = "gui.ncmplayer.music.info.search_failed";
    public static final String MUSIC_INFO_NO_RESULTS = "gui.ncmplayer.music.info.no_results";
    public static final String MUSIC_INFO_FOUND_SONGS = "gui.ncmplayer.music.info.found_songs";
    public static final String MUSIC_INFO_FOUND_SONGS_SCROLL = "gui.ncmplayer.music.info.found_songs_scroll";
    public static final String MUSIC_INFO_NOW_PLAYING = "gui.ncmplayer.music.info.now_playing";
    public static final String MUSIC_ERROR_NOT_LOGGED_IN = "gui.ncmplayer.music.error.not_logged_in";
    public static final String MUSIC_ERROR_LOGIN_EXPIRED = "gui.ncmplayer.music.error.login_expired";
    public static final String MUSIC_ERROR_EMPTY_KEYWORD = "gui.ncmplayer.music.error.empty_keyword";
    public static final String MUSIC_ERROR_CLEAR_FAILED = "gui.ncmplayer.music.error.clear_failed";
    public static final String MUSIC_QR_NOT_STARTED = "gui.ncmplayer.music.qr.not_started";
    public static final String MUSIC_QR_GENERATING = "gui.ncmplayer.music.qr.generating";
    public static final String MUSIC_QR_SCAN_CONFIRM = "gui.ncmplayer.music.qr.scan_confirm";
    public static final String MUSIC_QR_RENDER_FAILED = "gui.ncmplayer.music.qr.render_failed";
    public static final String MUSIC_QR_RENDER_FAILED_SHORT = "gui.ncmplayer.music.qr.render_failed_short";
    public static final String MUSIC_QR_GENERATE_FAILED = "gui.ncmplayer.music.qr.generate_failed";
    public static final String MUSIC_QR_WAITING_SCAN = "gui.ncmplayer.music.qr.waiting_scan";
    public static final String MUSIC_QR_SCANNED_CONFIRM = "gui.ncmplayer.music.qr.scanned_confirm";
    public static final String MUSIC_QR_EXPIRED_REFRESH = "gui.ncmplayer.music.qr.expired_refresh";
    public static final String MUSIC_QR_LOGIN_SUCCESS_SAVING = "gui.ncmplayer.music.qr.login_success_saving";
    public static final String MUSIC_QR_STATUS = "gui.ncmplayer.music.qr.status";
    public static final String MUSIC_QR_SAVED_OK = "gui.ncmplayer.music.qr.saved_ok";
    public static final String MUSIC_QR_SAVED_MISSING = "gui.ncmplayer.music.qr.saved_missing";
    public static final String MUSIC_QR_SAVE_FAILED = "gui.ncmplayer.music.qr.save_failed";
    public static final String MUSIC_QR_SAVE_FAILED_SHORT = "gui.ncmplayer.music.qr.save_failed_short";
    public static final String MUSIC_QR_LOADING = "gui.ncmplayer.music.qr.loading";

    public static final String MSG_MUSIC_PLAYING = "message.ncmplayer.music.playing";
    public static final String MSG_MUSIC_PLAY_FAILED = "message.ncmplayer.music.play_failed";
}

