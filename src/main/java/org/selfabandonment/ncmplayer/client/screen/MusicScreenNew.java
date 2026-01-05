package org.selfabandonment.ncmplayer.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.client.screen.tab.*;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.selfabandonment.ncmplayer.client.screen.UIConstants.*;

/**
 * 网易云音乐播放器主界面
 *
 * 采用 Tab 架构，各页面独立实现
 *
 * @author SelfAbandonment
 */
public class MusicScreenNew extends Screen implements MusicScreenContext {

    private static final Logger LOG = LoggerFactory.getLogger("ncmplayer");

    private final String baseUrl;
    private MusicScreenContext.TabType currentTab = MusicScreenContext.TabType.PLAYER;
    private ScheduledExecutorService exec;

    // Tab 页面
    private final Map<MusicScreenContext.TabType, AbstractTab> tabs = new EnumMap<>(MusicScreenContext.TabType.class);

    public MusicScreenNew(String baseUrl) {
        super(Component.literal("NCM Player"));
        this.baseUrl = baseUrl;
    }

    @Override
    protected void init() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ncmplayer-ui");
            t.setDaemon(true);
            return t;
        });

        // 创建所有 Tab
        tabs.put(MusicScreenContext.TabType.PLAYER, new PlayerTab(this));
        tabs.put(MusicScreenContext.TabType.PLAYLIST, new PlaylistTab(this));
        tabs.put(MusicScreenContext.TabType.QR_LOGIN, new QrLoginTab(this));
        tabs.put(MusicScreenContext.TabType.USER_INFO, new UserInfoTab(this));
        tabs.put(MusicScreenContext.TabType.NOW_PLAYING, new NowPlayingTab(this));

        // 初始化所有 Tab
        for (AbstractTab tab : tabs.values()) {
            tab.init();
            // 初始化后先隐藏所有组件
            tab.setVisible(false);
        }

        // 设置初始 Tab
        currentTab = MusicScreenContext.TabType.PLAYER;

        // 显示当前 Tab
        AbstractTab playerTab = tabs.get(currentTab);
        if (playerTab != null) {
            playerTab.setVisible(true);
            playerTab.onActivate();
        }
    }

    @Override
    public void onClose() {
        super.onClose();

        // 清理所有 Tab
        for (AbstractTab tab : tabs.values()) {
            tab.cleanup();
        }

        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 背景
        g.fill(0, 0, width, height, COLOR_BG);

        // 渲染当前 Tab
        AbstractTab tab = tabs.get(currentTab);
        if (tab != null) {
            tab.render(g, mouseX, mouseY, partialTick);
        }

        // 渲染组件
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 不渲染默认背景
    }

    @Override
    public void renderTransparentBackground(GuiGraphics g) {
        // 不渲染透明背景
    }

    // ==================== 输入处理 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        AbstractTab tab = tabs.get(currentTab);
        if (tab != null && tab.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        AbstractTab tab = tabs.get(currentTab);
        if (tab != null && tab.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        AbstractTab tab = tabs.get(currentTab);
        if (tab != null && tab.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        AbstractTab tab = tabs.get(currentTab);
        if (tab != null && tab.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ==================== MusicScreenContext 实现 ====================

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }

    @Override
    public ScheduledExecutorService exec() {
        return exec;
    }

    @Override
    public String baseUrl() {
        return baseUrl;
    }

    @Override
    public void switchTab(MusicScreenContext.TabType tab) {
        LOG.info("switchTab called: {} -> {}", currentTab, tab);
        if (currentTab == tab) return;

        // 停用当前 Tab
        AbstractTab current = tabs.get(currentTab);
        if (current != null) {
            current.setVisible(false);
            current.onDeactivate();
        }

        // 切换
        currentTab = tab;

        // 激活新 Tab
        AbstractTab next = tabs.get(tab);
        if (next != null) {
            LOG.info("Activating tab: {}", tab);
            next.setVisible(true);
            next.onActivate();
        }
    }

    @Override
    public <T extends AbstractWidget> T addWidget(T widget) {
        return addRenderableWidget(widget);
    }

    @Override
    public void updateLoginStatus() {
        // 可以在这里更新登录状态显示
        SessionStore.Session session = SessionStore.loadOrNull();
        boolean loggedIn = session != null && CookieSanitizer.hasMusicU(session.cookieForApi());
        // 状态已通过 SessionStore 管理
    }
}

