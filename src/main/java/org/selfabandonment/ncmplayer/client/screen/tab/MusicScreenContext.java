package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.gui.Font;

import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;

/**
 * Tab 页面上下文
 *
 * 提供 Tab 页面所需的共享资源和回调
 *
 * @author SelfAbandonment
 */
public interface MusicScreenContext {

    /**
     * 获取字体
     */
    Font font();

    /**
     * 获取屏幕宽度
     */
    int width();

    /**
     * 获取屏幕高度
     */
    int height();

    /**
     * 获取执行器
     */
    ScheduledExecutorService exec();

    /**
     * 获取 API 基础 URL
     */
    String baseUrl();

    /**
     * 切换到指定 Tab
     */
    void switchTab(TabType tab);

    /**
     * 添加可渲染组件
     */
    <T extends net.minecraft.client.gui.components.AbstractWidget> T addWidget(T widget);

    /**
     * 更新登录状态
     */
    void updateLoginStatus();

    /**
     * Tab 类型枚举
     */
    enum TabType {
        PLAYER,
        PLAYLIST,
        USER_INFO,
        QR_LOGIN,
        NOW_PLAYING
    }
}

