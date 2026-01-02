package org.demo.portalteleport.client.screen;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;

import org.demo.portalteleport.client.audio.ClientMusicController;
import org.demo.portalteleport.config.ModConfig;
import org.demo.portalteleport.ncm.CookieSanitizer;
import org.demo.portalteleport.ncm.NcmApiClient;
import org.demo.portalteleport.ncm.SessionStore;
import org.demo.portalteleport.util.I18n;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;

/**
 * 网易云音乐播放器界面
 * 功能：
 * - 搜索歌曲并播放
 * - 扫码登录网易云账号
 * - 播放控制（暂停/停止）
 * - 登出账号
 * @author SelfAbandonment
 */
public final class PortalTeleportMusicScreen extends Screen {

    /** 界面标签页枚举 */
    private enum Tab { SEARCH, QR_LOGIN }

    /** API 服务器地址 */
    private final String baseUrl;

    /** 当前标签页 */
    private Tab tab = Tab.SEARCH;

    // ==================== 共享状态 ====================

    /** 异步任务执行器 */
    private ScheduledExecutorService exec;

    /** 信息提示文本 */
    private volatile String infoText = "";

    /** 错误提示文本 */
    private volatile String errorText = "";

    // ==================== 搜索标签页组件 ====================

    /** 搜索关键词输入框 */
    private EditBox keywordBox;

    /** 搜索按钮 */
    private Button searchBtn;

    /** 暂停按钮 */
    private Button pauseBtn;

    /** 停止按钮 */
    private Button stopBtn;

    /** 跳转到扫码登录按钮 */
    private Button toQrBtn;

    /** 登出按钮 */
    private Button clearSessionBtn;

    /** 歌曲列表按钮 */
    private final List<Button> songButtons = new ArrayList<>();

    /** 当前搜索结果 */
    private List<NcmApiClient.SearchSong> currentSongs = new ArrayList<>();

    /** 列表滚动偏移量 */
    private int scrollOffset = 0;

    // ==================== 扫码登录标签页组件/状态 ====================

    /** 返回搜索按钮 */
    private Button backToSearchBtn;

    /** 刷新二维码按钮 */
    private Button refreshQrBtn;

    /** 二维码唯一标识 */
    private volatile String unikey;

    /** 上次轮询返回的状态码 */
    private volatile int lastCode = -1;

    /** 二维码状态文本 */
    private volatile String qrStatus;

    /** 轮询任务 Future */
    private ScheduledFuture<?> pollFuture;

    /** 二维码纹理 */
    @Nullable private DynamicTexture qrTexture;

    /** 二维码纹理资源位置 */
    @Nullable private ResourceLocation qrTextureLocation;

    /** 二维码宽度 */
    private int qrW = 0;

    /** 二维码高度 */
    private int qrH = 0;

    // ==================== UI 主题颜色 ====================

    /** 深色背景 */
    private static final int COLOR_BG_DARK = FastColor.ARGB32.color(220, 20, 20, 25);

    /** 面板背景 */
    private static final int COLOR_BG_PANEL = FastColor.ARGB32.color(200, 35, 35, 45);

    /** 强调色（红色） */
    private static final int COLOR_ACCENT = FastColor.ARGB32.color(255, 225, 60, 80);

    /** 浅强调色 */
    private static final int COLOR_ACCENT_LIGHT = FastColor.ARGB32.color(255, 255, 100, 120);

    /** 主要文字颜色 */
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;

    /** 次要文字颜色 */
    private static final int COLOR_TEXT_SECONDARY = 0xBBBBBB;

    /** 错误文字颜色 */
    private static final int COLOR_TEXT_ERROR = 0xFF6B6B;

    /** 成功文字颜色 */
    private static final int COLOR_TEXT_SUCCESS = 0x6BFF6B;

    /** 边框颜色 */
    private static final int COLOR_BORDER = FastColor.ARGB32.color(255, 60, 60, 70);

    // ==================== 布局常量 ====================

    /** 顶部区域高度 */
    private static final int HEADER_HEIGHT = 50;

    /** 底部区域高度 */
    private static final int FOOTER_HEIGHT = 45;

    /** 侧边距 */
    private static final int SIDE_MARGIN = 20;

    /**
     * 构造函数
     *
     * @param baseUrl API 服务器地址
     */
    public PortalTeleportMusicScreen(String baseUrl) {
        super(I18n.translate(I18n.MUSIC_TITLE));
        this.baseUrl = baseUrl;
        this.qrStatus = I18n.translateString(I18n.MUSIC_QR_NOT_STARTED);
    }

    /**
     * 初始化界面
     */
    @Override
    protected void init() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "portalteleport-music-ui");
            t.setDaemon(true);
            return t;
        });

        buildWidgets();
        refreshInfo();
        setTab(Tab.SEARCH);
    }

    /**
     * 关闭界面时清理资源
     */
    @Override
    public void onClose() {
        super.onClose();
        stopPolling();
        deleteQrTexture();
        if (exec != null) {
            exec.shutdownNow();
        }
        exec = null;
    }

    /**
     * 构建界面组件
     */
    private void buildWidgets() {
        int cx = this.width / 2;
        int contentTop = HEADER_HEIGHT + 10;
        int footerY = this.height - FOOTER_HEIGHT + 10;

        // Tab buttons - styled
        toQrBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_QR_LOGIN), b -> setTab(Tab.QR_LOGIN))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(toQrBtn);

        backToSearchBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_BACK_SEARCH), b -> setTab(Tab.SEARCH))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(backToSearchBtn);

        // SEARCH tab - search bar with button on the right
        int searchBarWidth = Math.min(240, this.width - 120);
        int searchBtnWidth = 70;
        int totalSearchWidth = searchBarWidth + 5 + searchBtnWidth;
        int searchStartX = cx - totalSearchWidth / 2;

        keywordBox = new EditBox(this.font, searchStartX, contentTop, searchBarWidth, 22, Component.literal(""));
        keywordBox.setHint(I18n.translate(I18n.MUSIC_HINT_SEARCH));
        keywordBox.setValue("");
        keywordBox.setMaxLength(100);
        addRenderableWidget(keywordBox);

        searchBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_SEARCH), b -> doSearchAsync())
                .bounds(searchStartX + searchBarWidth + 5, contentTop, searchBtnWidth, 22).build();
        addRenderableWidget(searchBtn);

        // Footer buttons - evenly spaced
        int btnWidth = 75;
        int btnGap = 10;
        int totalBtnWidth = btnWidth * 3 + btnGap * 2;
        int btnStartX = cx - totalBtnWidth / 2;

        pauseBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_PAUSE), b -> ClientMusicController.togglePause())
                .bounds(btnStartX, footerY, btnWidth, 20).build();
        addRenderableWidget(pauseBtn);

        stopBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_STOP), b -> ClientMusicController.stop())
                .bounds(btnStartX + btnWidth + btnGap, footerY, btnWidth, 20).build();
        addRenderableWidget(stopBtn);

        clearSessionBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_LOGOUT), b -> clearSession())
                .bounds(btnStartX + (btnWidth + btnGap) * 2, footerY, btnWidth, 20).build();
        addRenderableWidget(clearSessionBtn);

        // QR tab
        refreshQrBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_REFRESH_QR), b -> refreshQrAsync())
                .bounds(cx - 60, footerY, 120, 22).build();
        addRenderableWidget(refreshQrBtn);
    }

    /**
     * 切换标签页
     *
     * @param t 目标标签页
     */
    private void setTab(Tab t) {
        this.tab = t;
        this.errorText = "";

        boolean search = (t == Tab.SEARCH);
        boolean qr = (t == Tab.QR_LOGIN);

        // tab buttons
        toQrBtn.visible = search;
        backToSearchBtn.visible = qr;

        // search widgets
        keywordBox.visible = search;
        searchBtn.visible = search;
        pauseBtn.visible = search;
        stopBtn.visible = search;
        clearSessionBtn.visible = search;

        // song list buttons visibility
        for (Button b : songButtons) b.visible = search;

        // qr widgets
        refreshQrBtn.visible = qr;

        if (qr) {
            // start/refresh QR when entering QR tab
            if (qrTextureLocation == null) refreshQrAsync();
        } else {
            // stop polling when leaving QR tab
            stopPolling();
        }

        refreshInfo();
    }

    /**
     * 刷新登录状态信息
     */
    private void refreshInfo() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            infoText = I18n.translateString(I18n.MUSIC_INFO_WELCOME);
            return;
        }
        boolean has = CookieSanitizer.hasMusicU(session.cookieForApi());
        infoText = has ? I18n.translateString(I18n.MUSIC_INFO_LOGGED_IN) : I18n.translateString(I18n.MUSIC_INFO_LOGIN_INCOMPLETE);
    }

    // ==================== 搜索功能 ====================

    /**
     * 清除歌曲列表按钮
     */
    private void clearSongButtons() {
        for (Button b : songButtons) {
            removeWidget(b);
        }
        songButtons.clear();
    }

    /**
     * 清除登录会话（登出）
     */
    private void clearSession() {
        try {
            var p = SessionStore.debugPath();
            if (Files.exists(p)) Files.delete(p);
            refreshInfo();
            infoText = I18n.translateString(I18n.MUSIC_INFO_LOGGED_OUT);
        } catch (Exception e) {
            errorText = I18n.translateString(I18n.MUSIC_ERROR_CLEAR_FAILED, e.getMessage());
        }
    }

    /**
     * 异步执行搜索
     */
    private void doSearchAsync() {
        errorText = "";
        clearSongButtons();

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            errorText = I18n.translateString(I18n.MUSIC_ERROR_NOT_LOGGED_IN);
            return;
        }
        String cookie = session.cookieForApi();
        if (!CookieSanitizer.hasMusicU(cookie)) {
            errorText = I18n.translateString(I18n.MUSIC_ERROR_LOGIN_EXPIRED);
            return;
        }

        String keywords = keywordBox.getValue().trim();
        if (keywords.isEmpty()) {
            errorText = I18n.translateString(I18n.MUSIC_ERROR_EMPTY_KEYWORD);
            return;
        }

        NcmApiClient client = new NcmApiClient(session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl : session.baseUrl());

        int searchLimit = ModConfig.COMMON.musicSearchLimit.get();
        infoText = I18n.translateString(I18n.MUSIC_INFO_SEARCHING);
        CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(keywords, searchLimit, cookie);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, exec).whenComplete((songs, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) {
                errorText = (err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
                infoText = I18n.translateString(I18n.MUSIC_INFO_SEARCH_FAILED);
                return;
            }
            if (songs == null || songs.isEmpty()) {
                infoText = I18n.translateString(I18n.MUSIC_INFO_NO_RESULTS);
                return;
            }
            infoText = I18n.translateString(I18n.MUSIC_INFO_FOUND_SONGS, songs.size());
            renderSongButtons(songs);
        }));
    }

    /**
     * 渲染歌曲列表按钮
     *
     * @param songs 搜索结果
     */
    private void renderSongButtons(List<NcmApiClient.SearchSong> songs) {
        clearSongButtons();
        currentSongs = new ArrayList<>(songs);
        scrollOffset = 0;
        rebuildSongButtons();
    }

    /**
     * 重建歌曲列表按钮（用于滚动）
     */
    private void rebuildSongButtons() {
        // 清除现有按钮
        for (Button b : songButtons) removeWidget(b);
        songButtons.clear();

        if (currentSongs.isEmpty()) return;

        int cx = this.width / 2;
        int listWidth = Math.min(360, this.width - 40);
        int x = cx - listWidth / 2;
        int y = HEADER_HEIGHT + 38;  // 搜索栏下方，留出合适间距
        int h = 20;
        int gap = 2;

        // 计算可用高度，预留底部按钮空间
        int availableHeight = this.height - FOOTER_HEIGHT - y - 5;
        int maxVisible = availableHeight / (h + gap);

        // 限制滚动范围
        int maxScroll = Math.max(0, currentSongs.size() - maxVisible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int endIndex = Math.min(scrollOffset + maxVisible, currentSongs.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            var s = currentSongs.get(i);
            String artist = s.artist().isBlank() ? "" : " - " + s.artist();
            String label = "♪ " + s.name() + artist;

            // 根据列表宽度截断
            int maxLen = listWidth / 6;
            if (label.length() > maxLen) label = label.substring(0, maxLen) + "...";

            int yy = y + (i - scrollOffset) * (h + gap);

            final String songName = s.name();
            Button btn = Button.builder(Component.literal(label), b -> {
                        ClientMusicController.playSongId(s.id());
                        infoText = I18n.translateString(I18n.MUSIC_INFO_NOW_PLAYING, songName);
                    })
                    .bounds(x, yy, listWidth, h)
                    .build();
            songButtons.add(btn);
            addRenderableWidget(btn);
        }

        // 更新提示信息
        if (currentSongs.size() > maxVisible) {
            infoText = I18n.translateString(I18n.MUSIC_INFO_FOUND_SONGS_SCROLL, currentSongs.size(), scrollOffset + 1, endIndex);
        } else {
            infoText = I18n.translateString(I18n.MUSIC_INFO_FOUND_SONGS, currentSongs.size());
        }

        // ensure visibility matches current tab
        for (Button b : songButtons) b.visible = (tab == Tab.SEARCH);
    }

    // ==================== 扫码登录功能 ====================

    /**
     * 异步刷新二维码
     */
    private void refreshQrAsync() {
        stopPolling();
        qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATING);
        errorText = "";
        lastCode = -1;

        // Use a fresh client each time; avoids stale baseUrl state
        NcmApiClient client = new NcmApiClient(baseUrl);

        CompletableFuture.runAsync(() -> {
            try {
                // 1) key
                String key = client.qrKey();
                this.unikey = key;

                // 2) create with qrimg
                JsonObject create = client.qrCreate(key, true);
                String qrimg = create.getAsJsonObject("data").get("qrimg").getAsString();

                // upload texture on main thread
                Minecraft.getInstance().execute(() -> {
                    try {
                        loadQrTexture(qrimg);
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_SCAN_CONFIRM);
                    } catch (Exception e) {
                        errorText = I18n.translateString(I18n.MUSIC_QR_RENDER_FAILED, e.getMessage());
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_RENDER_FAILED_SHORT);
                    }
                });

                // 3) start polling
                startPolling(client);

            } catch (Exception e) {
                errorText = e.getClass().getSimpleName() + ": " + e.getMessage();
                qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATE_FAILED);
            }
        }, exec);
    }

    /**
     * 开始轮询二维码状态
     *
     * @param client API 客户端
     */
    private void startPolling(NcmApiClient client) {
        if (exec == null) return;
        if (unikey == null || unikey.isBlank()) return;

        pollFuture = exec.scheduleAtFixedRate(() -> {
            try {
                JsonObject check = client.qrCheck(unikey);
                int code = check.get("code").getAsInt();
                lastCode = code;

                // cookie is top-level field
                String cookieRaw = check.has("cookie") && !check.get("cookie").isJsonNull()
                        ? check.get("cookie").getAsString()
                        : "";

                switch (code) {
                    case 801 -> qrStatus = I18n.translateString(I18n.MUSIC_QR_WAITING_SCAN);
                    case 802 -> qrStatus = I18n.translateString(I18n.MUSIC_QR_SCANNED_CONFIRM);
                    case 800 -> {
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_EXPIRED_REFRESH);
                        Minecraft.getInstance().execute(this::refreshQrAsync);
                    }
                    case 803 -> {
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_LOGIN_SUCCESS_SAVING);
                        onLoginSuccess(cookieRaw);
                    }
                    default -> qrStatus = I18n.translateString(I18n.MUSIC_QR_STATUS, code);
                }
            } catch (Exception e) {
                errorText = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    /**
     * 登录成功回调
     *
     * @param cookieRaw 原始 Cookie
     */
    private void onLoginSuccess(String cookieRaw) {
        stopPolling();

        String cookieForApi = CookieSanitizer.sanitizeForApi(cookieRaw);
        boolean ok = CookieSanitizer.hasMusicU(cookieForApi);

        try {
            SessionStore.save(new SessionStore.Session(baseUrl, cookieForApi, System.currentTimeMillis()));
            refreshInfo();
            qrStatus = ok ? I18n.translateString(I18n.MUSIC_QR_SAVED_OK) : I18n.translateString(I18n.MUSIC_QR_SAVED_MISSING);
        } catch (Exception e) {
            errorText = I18n.translateString(I18n.MUSIC_QR_SAVE_FAILED, e.getMessage());
            qrStatus = I18n.translateString(I18n.MUSIC_QR_SAVE_FAILED_SHORT);
            return;
        }

        // After success, go back to search tab
        Minecraft.getInstance().execute(() -> setTab(Tab.SEARCH));
    }

    /**
     * 停止轮询
     */
    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }

    /**
     * 加载二维码纹理
     *
     * @param dataUrl Base64 编码的图片数据
     * @throws Exception 加载失败
     */
    private void loadQrTexture(String dataUrl) throws Exception {
        deleteQrTexture();

        String prefix = "data:image/png;base64,";
        String b64 = dataUrl.startsWith(prefix) ? dataUrl.substring(prefix.length()) : dataUrl;

        byte[] png = Base64.getDecoder().decode(b64);
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            NativeImage src = NativeImage.read(in);

            qrW = src.getWidth();
            qrH = src.getHeight();
            qrTexture = new DynamicTexture(src);
        }

        TextureManager tm = Minecraft.getInstance().getTextureManager();
        qrTextureLocation = ResourceLocation.fromNamespaceAndPath("portalteleport", "ncm_qr/" + UUID.randomUUID());
        tm.register(qrTextureLocation, qrTexture);

        // nearest-neighbor, no mipmap => QR stays crisp when scaled
        tm.getTexture(qrTextureLocation).setFilter(false, false);
    }

    /**
     * 删除二维码纹理
     */
    private void deleteQrTexture() {
        qrTextureLocation = null;
        if (qrTexture != null) {
            try {
                qrTexture.close();
            } catch (Exception ignored) {
            }
            qrTexture = null;
        }
        qrW = 0;
        qrH = 0;
    }

    /**
     * 鼠标滚轮事件处理
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.SEARCH && !currentSongs.isEmpty()) {
            // scrollY > 0 表示向上滚动，< 0 表示向下滚动
            int scrollAmount = (int) -scrollY;
            int newOffset = scrollOffset + scrollAmount;

            // 计算最大可滚动范围
            int y = HEADER_HEIGHT + 38;
            int h = 20;
            int gap = 2;
            int availableHeight = this.height - FOOTER_HEIGHT - y - 5;
            int maxVisible = availableHeight / (h + gap);
            int maxScroll = Math.max(0, currentSongs.size() - maxVisible);

            newOffset = Math.max(0, Math.min(maxScroll, newOffset));

            if (newOffset != scrollOffset) {
                scrollOffset = newOffset;
                rebuildSongButtons();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 是否暂停游戏
     */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * 渲染背景（留空以保持二维码清晰）
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 留空：不渲染默认背景
    }

    /**
     * 渲染透明背景（留空以保持二维码清晰）
     */
    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        // 留空：不渲染透明背景
    }

    /**
     * 渲染界面
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 绘制半透明深色背景
        g.fill(0, 0, this.width, this.height, COLOR_BG_DARK);

        int cx = this.width / 2;

        // 标题区域背景
        g.fill(0, 0, this.width, HEADER_HEIGHT, COLOR_BG_PANEL);
        // 标题底部装饰线
        g.fill(0, HEADER_HEIGHT - 2, this.width, HEADER_HEIGHT, COLOR_ACCENT);

        // 标题文字
        g.drawCenteredString(this.font, this.title, cx, 6, COLOR_TEXT_PRIMARY);

        // info 文字 - 根据内容的 emoji 选择颜色
        int infoColor = COLOR_TEXT_SECONDARY;
        if (infoText.contains("✅")) {
            infoColor = COLOR_TEXT_SUCCESS;
        } else if (infoText.contains("🔍") || infoText.contains("🎵") || infoText.contains("✨")) {
            infoColor = COLOR_ACCENT_LIGHT;
        }
        g.drawCenteredString(this.font, infoText, cx, 22, infoColor);

        // error 文字
        if (!errorText.isBlank()) {
            g.drawCenteredString(this.font, "❌ " + errorText, cx, 36, COLOR_TEXT_ERROR);
        }

        // 底部区域背景
        int footerTop = this.height - FOOTER_HEIGHT;
        g.fill(0, footerTop, this.width, this.height, COLOR_BG_PANEL);
        // 底部顶部装饰线
        g.fill(0, footerTop, this.width, footerTop + 2, COLOR_BORDER);

        if (tab == Tab.QR_LOGIN) {
            drawQrPanel(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    /**
     * 绘制二维码面板
     *
     * @param g 图形上下文
     */
    private void drawQrPanel(GuiGraphics g) {
        int cx = this.width / 2;

        int refreshY = (refreshQrBtn != null ? refreshQrBtn.getY() : (this.height - 28));
        // 状态文字在刷新按钮上方，留出足够空间
        int statusTextY = refreshY - 18;
        int bottomLimit = statusTextY - 8;

        int topMin = 56;
        int maxBoxSizeByHeight = Math.max(120, bottomLimit - topMin);
        int preferred = 180;
        int boxSize = Math.min(preferred, maxBoxSizeByHeight);

        int boxX = cx - boxSize / 2;
        int top = topMin + (bottomLimit - topMin - boxSize) / 2;
        if (top < topMin) top = topMin;

        int padding = 2;
        int inner = boxSize - padding * 2;

        // 外框 - 红色主题
        int borderSize = 3;
        g.fill(boxX - borderSize, top - borderSize,
               boxX + boxSize + borderSize, top + boxSize + borderSize,
               COLOR_ACCENT);

        // 白色面板 (best for scanning)
        g.fill(boxX, top, boxX + boxSize, top + boxSize,
                FastColor.ARGB32.color(255, 255, 255, 255));

        if (qrTextureLocation != null && qrW > 0 && qrH > 0) {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            float scaleF = Math.min((float) inner / qrW, (float) inner / qrH);
            int drawW = Math.max(1, Math.round(qrW * scaleF));
            int drawH = Math.max(1, Math.round(qrH * scaleF));

            int dx = boxX + padding + (inner - drawW) / 2;
            int dy = top + padding + (inner - drawH) / 2;

            g.blit(qrTextureLocation,
                    dx, dy,
                    drawW, drawH,
                    0, 0,
                    qrW, qrH,
                    qrW, qrH);

            RenderSystem.enableBlend();
        } else {
            g.drawCenteredString(this.font, I18n.translateString(I18n.MUSIC_QR_LOADING), cx, top + boxSize / 2 - 4, 0x888888);
        }

        // 状态文字 - 在刷新按钮上方
        int statusColor = COLOR_TEXT_SECONDARY;
        String statusIcon = "📱 ";

        // 根据状态码判断颜色
        if (lastCode == 803) {
            statusColor = COLOR_TEXT_SUCCESS;
            statusIcon = "✅ ";
        } else if (lastCode == 800 || qrStatus.contains("failed") || qrStatus.contains("失败") || qrStatus.contains("expired") || qrStatus.contains("过期")) {
            statusColor = COLOR_TEXT_ERROR;
            statusIcon = "❌ ";
        } else if (lastCode == 801 || lastCode == 802) {
            statusColor = COLOR_ACCENT_LIGHT;
            statusIcon = "📲 ";
        }

        String statusText = statusIcon + qrStatus;
        g.drawCenteredString(this.font, statusText, cx, statusTextY, statusColor);
    }
}
