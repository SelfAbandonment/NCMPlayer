package org.demo.portalteleport.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 国际化翻译工具类
 * 提供便捷的方法来获取翻译后的文本组件，以及所有翻译键的常量定义。
 * 翻译文件位于：
 * - assets/portalteleport/lang/en_us.json - 英文
 * - assets/portalteleport/lang/zh_cn.json - 简体中文
 * 使用示例：
 *   // 获取翻译组件（用于发送给玩家）
 *   player.sendSystemMessage(I18n.translate(I18n.FISHING_BONUS, count, itemName));
 *   // 获取翻译字符串（用于日志或 GUI 显示）
 *   String text = I18n.translateString(I18n.MUSIC_INFO_SEARCHING);
 *
 * @author SelfAbandonment
 */
public final class I18n {

    private I18n() {
        // 工具类，禁止实例化
    }

    // ==================== 翻译方法 ====================

    /**
     * 获取翻译组件
     *
     * @param key 翻译键
     * @return 翻译后的组件
     */
    public static MutableComponent translate(String key) {
        return Component.translatable(key);
    }

    /**
     * 获取带参数的翻译组件
     *
     * @param key  翻译键
     * @param args 参数（对应翻译文本中的 %s, %d 等占位符）
     * @return 翻译后的组件
     */
    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }

    /**
     * 获取翻译后的字符串
     * <p>
     * 注意：在服务端使用时可能无法正确翻译，建议仅在客户端使用。
     *
     * @param key 翻译键
     * @return 翻译后的字符串
     */
    public static String translateString(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * 获取带参数的翻译字符串
     *
     * @param key  翻译键
     * @param args 参数
     * @return 翻译后的字符串
     */
    public static String translateString(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * 获取睡前故事翻译键
     *
     * @param index 故事索引 (1-18)
     * @return 翻译键
     */
    public static String getSleepStoryKey(int index) {
        return "event.portalteleport.sleep.story." + index;
    }

    // ==================== 翻译键常量 ====================

    // -------------------- 音乐播放器 GUI --------------------

    /** 音乐界面标题 */
    public static final String MUSIC_TITLE = "gui.portalteleport.music.title";

    /** 按钮：扫码登录 */
    public static final String MUSIC_BTN_QR_LOGIN = "gui.portalteleport.music.btn.qr_login";

    /** 按钮：返回搜索 */
    public static final String MUSIC_BTN_BACK_SEARCH = "gui.portalteleport.music.btn.back_search";

    /** 按钮：搜索 */
    public static final String MUSIC_BTN_SEARCH = "gui.portalteleport.music.btn.search";

    /** 按钮：暂停 */
    public static final String MUSIC_BTN_PAUSE = "gui.portalteleport.music.btn.pause";

    /** 按钮：停止 */
    public static final String MUSIC_BTN_STOP = "gui.portalteleport.music.btn.stop";

    /** 按钮：登出 */
    public static final String MUSIC_BTN_LOGOUT = "gui.portalteleport.music.btn.logout";

    /** 按钮：刷新二维码 */
    public static final String MUSIC_BTN_REFRESH_QR = "gui.portalteleport.music.btn.refresh_qr";

    /** 搜索框提示 */
    public static final String MUSIC_HINT_SEARCH = "gui.portalteleport.music.hint.search";

    /** 信息：欢迎 */
    public static final String MUSIC_INFO_WELCOME = "gui.portalteleport.music.info.welcome";

    /** 信息：已登录 */
    public static final String MUSIC_INFO_LOGGED_IN = "gui.portalteleport.music.info.logged_in";

    /** 信息：登录不完整 */
    public static final String MUSIC_INFO_LOGIN_INCOMPLETE = "gui.portalteleport.music.info.login_incomplete";

    /** 信息：已登出 */
    public static final String MUSIC_INFO_LOGGED_OUT = "gui.portalteleport.music.info.logged_out";

    /** 信息：搜索中 */
    public static final String MUSIC_INFO_SEARCHING = "gui.portalteleport.music.info.searching";

    /** 信息：搜索失败 */
    public static final String MUSIC_INFO_SEARCH_FAILED = "gui.portalteleport.music.info.search_failed";

    /** 信息：无结果 */
    public static final String MUSIC_INFO_NO_RESULTS = "gui.portalteleport.music.info.no_results";

    /** 信息：找到歌曲 */
    public static final String MUSIC_INFO_FOUND_SONGS = "gui.portalteleport.music.info.found_songs";

    /** 信息：找到歌曲（带滚动提示） */
    public static final String MUSIC_INFO_FOUND_SONGS_SCROLL = "gui.portalteleport.music.info.found_songs_scroll";

    /** 信息：正在播放 */
    public static final String MUSIC_INFO_NOW_PLAYING = "gui.portalteleport.music.info.now_playing";

    /** 错误：未登录 */
    public static final String MUSIC_ERROR_NOT_LOGGED_IN = "gui.portalteleport.music.error.not_logged_in";

    /** 错误：登录过期 */
    public static final String MUSIC_ERROR_LOGIN_EXPIRED = "gui.portalteleport.music.error.login_expired";

    /** 错误：关键词为空 */
    public static final String MUSIC_ERROR_EMPTY_KEYWORD = "gui.portalteleport.music.error.empty_keyword";

    /** 错误：清除失败 */
    public static final String MUSIC_ERROR_CLEAR_FAILED = "gui.portalteleport.music.error.clear_failed";

    /** 二维码：未开始 */
    public static final String MUSIC_QR_NOT_STARTED = "gui.portalteleport.music.qr.not_started";

    /** 二维码：生成中 */
    public static final String MUSIC_QR_GENERATING = "gui.portalteleport.music.qr.generating";

    /** 二维码：请扫码确认 */
    public static final String MUSIC_QR_SCAN_CONFIRM = "gui.portalteleport.music.qr.scan_confirm";

    /** 二维码：渲染失败 */
    public static final String MUSIC_QR_RENDER_FAILED = "gui.portalteleport.music.qr.render_failed";

    /** 二维码：渲染失败（短） */
    public static final String MUSIC_QR_RENDER_FAILED_SHORT = "gui.portalteleport.music.qr.render_failed_short";

    /** 二维码：生成失败 */
    public static final String MUSIC_QR_GENERATE_FAILED = "gui.portalteleport.music.qr.generate_failed";

    /** 二维码：等待扫码 */
    public static final String MUSIC_QR_WAITING_SCAN = "gui.portalteleport.music.qr.waiting_scan";

    /** 二维码：已扫码确认中 */
    public static final String MUSIC_QR_SCANNED_CONFIRM = "gui.portalteleport.music.qr.scanned_confirm";

    /** 二维码：已过期刷新中 */
    public static final String MUSIC_QR_EXPIRED_REFRESH = "gui.portalteleport.music.qr.expired_refresh";

    /** 二维码：登录成功保存中 */
    public static final String MUSIC_QR_LOGIN_SUCCESS_SAVING = "gui.portalteleport.music.qr.login_success_saving";

    /** 二维码：状态码 */
    public static final String MUSIC_QR_STATUS = "gui.portalteleport.music.qr.status";

    /** 二维码：保存成功 */
    public static final String MUSIC_QR_SAVED_OK = "gui.portalteleport.music.qr.saved_ok";

    /** 二维码：保存但缺少 MUSIC_U */
    public static final String MUSIC_QR_SAVED_MISSING = "gui.portalteleport.music.qr.saved_missing";

    /** 二维码：保存失败 */
    public static final String MUSIC_QR_SAVE_FAILED = "gui.portalteleport.music.qr.save_failed";

    /** 二维码：保存失败（短） */
    public static final String MUSIC_QR_SAVE_FAILED_SHORT = "gui.portalteleport.music.qr.save_failed_short";

    /** 二维码：加载中 */
    public static final String MUSIC_QR_LOADING = "gui.portalteleport.music.qr.loading";

    // -------------------- Gate 命令 --------------------

    /** 命令：无权限 */
    public static final String GATE_NO_PERMISSION = "command.portalteleport.gate.no_permission";

    /** 命令：已开启 */
    public static final String GATE_ENABLED = "command.portalteleport.gate.enabled";

    /** 命令：已关闭 */
    public static final String GATE_DISABLED = "command.portalteleport.gate.disabled";

    /** 命令：状态 */
    public static final String GATE_STATUS = "command.portalteleport.gate.status";

    /** 状态：启用 */
    public static final String GATE_STATUS_ENABLED = "command.portalteleport.gate.status.enabled";

    /** 状态：禁用 */
    public static final String GATE_STATUS_DISABLED = "command.portalteleport.gate.status.disabled";

    /** 状态：开启 */
    public static final String GATE_STATUS_ON = "command.portalteleport.gate.status.on";

    /** 状态：关闭 */
    public static final String GATE_STATUS_OFF = "command.portalteleport.gate.status.off";

    /** 命令：信息 */
    public static final String GATE_INFO = "command.portalteleport.gate.info";

    /** 信息：掉落物 */
    public static final String GATE_INFO_ITEMS = "command.portalteleport.gate.info.items";

    /** 信息：船 */
    public static final String GATE_INFO_BOATS = "command.portalteleport.gate.info.boats";

    /** 信息：矿车 */
    public static final String GATE_INFO_MINECARTS = "command.portalteleport.gate.info.minecarts";

    /** 命令：已重载 */
    public static final String GATE_RELOADED = "command.portalteleport.gate.reloaded";

    /** 消息：已阻止传送 */
    public static final String GATE_BLOCKED = "command.portalteleport.gate.blocked";

    // -------------------- 事件 --------------------

    /** 钓鱼：额外奖励 */
    public static final String FISHING_BONUS = "event.portalteleport.fishing.bonus";

    /** 睡眠效果：再生 */
    public static final String SLEEP_EFFECT_REGENERATION = "event.portalteleport.sleep.effect.regeneration";

    /** 睡眠效果：幸运 */
    public static final String SLEEP_EFFECT_LUCK = "event.portalteleport.sleep.effect.luck";

    /** 睡眠效果：速度 */
    public static final String SLEEP_EFFECT_SPEED = "event.portalteleport.sleep.effect.speed";

    // -------------------- 音乐消息 --------------------

    /** 音乐：正在播放 */
    public static final String MSG_MUSIC_PLAYING = "message.portalteleport.music.playing";

    /** 音乐：播放失败 */
    public static final String MSG_MUSIC_PLAY_FAILED = "message.portalteleport.music.play_failed";
}

