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
import org.demo.portalteleport.ncm.CookieSanitizer;
import org.demo.portalteleport.ncm.NcmApiClient;
import org.demo.portalteleport.ncm.SessionStore;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;

public final class PortalTeleportMusicScreen extends Screen {

    private enum Tab { SEARCH, QR_LOGIN }

    private final String baseUrl;
    private Tab tab = Tab.SEARCH;

    // shared
    private ScheduledExecutorService exec;

    private volatile String infoText = "";
    private volatile String errorText = "";

    // ---- SEARCH tab widgets ----
    private EditBox keywordBox;
    private Button searchBtn;
    private Button pauseBtn;
    private Button stopBtn;
    private Button toQrBtn;
    private Button clearSessionBtn;

    private final List<Button> songButtons = new ArrayList<>();
    private List<NcmApiClient.SearchSong> currentSongs = new ArrayList<>();  // 保存搜索结果
    private int scrollOffset = 0;  // 滚动偏移量

    // ---- QR tab widgets/state ----
    private Button backToSearchBtn;
    private Button refreshQrBtn;

    private volatile String unikey;
    private volatile int lastCode = -1;
    private volatile String qrStatus = "未开始";
    private ScheduledFuture<?> pollFuture;

    @Nullable private DynamicTexture qrTexture;
    @Nullable private ResourceLocation qrTextureLocation;
    private int qrW = 0, qrH = 0;

    // ---- UI Theme Colors ----
    private static final int COLOR_BG_DARK = FastColor.ARGB32.color(220, 20, 20, 25);
    private static final int COLOR_BG_PANEL = FastColor.ARGB32.color(200, 35, 35, 45);
    private static final int COLOR_ACCENT = FastColor.ARGB32.color(255, 225, 60, 80);
    private static final int COLOR_ACCENT_LIGHT = FastColor.ARGB32.color(255, 255, 100, 120);
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xBBBBBB;
    private static final int COLOR_TEXT_ERROR = 0xFF6B6B;
    private static final int COLOR_TEXT_SUCCESS = 0x6BFF6B;
    private static final int COLOR_BORDER = FastColor.ARGB32.color(255, 60, 60, 70);

    // Layout constants
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 45;
    private static final int SIDE_MARGIN = 20;

    public PortalTeleportMusicScreen(String baseUrl) {
        super(Component.literal("♪ 网易云音乐"));
        this.baseUrl = baseUrl;
    }

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

    @Override
    public void onClose() {
        super.onClose();
        stopPolling();
        deleteQrTexture();
        if (exec != null) exec.shutdownNow();
        exec = null;
    }

    private void buildWidgets() {
        int cx = this.width / 2;
        int contentTop = HEADER_HEIGHT + 10;
        int footerY = this.height - FOOTER_HEIGHT + 10;

        // Tab buttons - styled
        toQrBtn = Button.builder(Component.literal("🔐 扫码登录"), b -> setTab(Tab.QR_LOGIN))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(toQrBtn);

        backToSearchBtn = Button.builder(Component.literal("← 返回搜索"), b -> setTab(Tab.SEARCH))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(backToSearchBtn);

        // SEARCH tab - search bar with button on the right
        int searchBarWidth = Math.min(240, this.width - 120);
        int searchBtnWidth = 70;
        int totalSearchWidth = searchBarWidth + 5 + searchBtnWidth;
        int searchStartX = cx - totalSearchWidth / 2;

        keywordBox = new EditBox(this.font, searchStartX, contentTop, searchBarWidth, 22, Component.literal("搜索歌曲..."));
        keywordBox.setHint(Component.literal("输入歌曲、歌手或专辑名..."));
        keywordBox.setValue("");
        keywordBox.setMaxLength(100);
        addRenderableWidget(keywordBox);

        searchBtn = Button.builder(Component.literal("🔍 搜索"), b -> doSearchAsync())
                .bounds(searchStartX + searchBarWidth + 5, contentTop, searchBtnWidth, 22).build();
        addRenderableWidget(searchBtn);

        // Footer buttons - evenly spaced
        int btnWidth = 75;
        int btnGap = 10;
        int totalBtnWidth = btnWidth * 3 + btnGap * 2;
        int btnStartX = cx - totalBtnWidth / 2;

        pauseBtn = Button.builder(Component.literal("⏯ 暂停"), b -> ClientMusicController.togglePause())
                .bounds(btnStartX, footerY, btnWidth, 20).build();
        addRenderableWidget(pauseBtn);

        stopBtn = Button.builder(Component.literal("⏹ 停止"), b -> ClientMusicController.stop())
                .bounds(btnStartX + btnWidth + btnGap, footerY, btnWidth, 20).build();
        addRenderableWidget(stopBtn);

        clearSessionBtn = Button.builder(Component.literal("🚪 登出"), b -> clearSession())
                .bounds(btnStartX + (btnWidth + btnGap) * 2, footerY, btnWidth, 20).build();
        addRenderableWidget(clearSessionBtn);

        // QR tab
        refreshQrBtn = Button.builder(Component.literal("🔄 刷新二维码"), b -> refreshQrAsync())
                .bounds(cx - 60, footerY, 120, 22).build();
        addRenderableWidget(refreshQrBtn);
    }

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

    private void refreshInfo() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            infoText = "✨ 欢迎使用！请先扫码登录以播放完整歌曲";
            return;
        }
        boolean has = CookieSanitizer.hasMusicU(session.cookieForApi());
        infoText = has ? "✅ 已登录，可以搜索并播放歌曲" : "⚠ 登录信息不完整，请重新登录";
    }

    // ---------------- SEARCH ----------------

    private void clearSongButtons() {
        for (Button b : songButtons) removeWidget(b);
        songButtons.clear();
    }

    private void clearSession() {
        try {
            var p = SessionStore.debugPath();
            if (Files.exists(p)) Files.delete(p);
            refreshInfo();
            infoText = "✅ 已成功登出";
        } catch (Exception e) {
            errorText = "清除失败: " + e.getMessage();
        }
    }

    private void doSearchAsync() {
        errorText = "";
        clearSongButtons();

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            errorText = "请先点击左上角「扫码登录」";
            return;
        }
        String cookie = session.cookieForApi();
        if (!CookieSanitizer.hasMusicU(cookie)) {
            errorText = "登录信息已过期，请重新扫码登录";
            return;
        }

        String keywords = keywordBox.getValue().trim();
        if (keywords.isEmpty()) {
            errorText = "请输入搜索关键词";
            return;
        }

        NcmApiClient client = new NcmApiClient(session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl : session.baseUrl());

        infoText = "🔍 搜索中...";
        CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(keywords, 20, cookie);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, exec).whenComplete((songs, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) {
                errorText = (err.getCause() != null ? err.getCause().getMessage() : err.getMessage());
                infoText = "搜索失败";
                return;
            }
            if (songs == null || songs.isEmpty()) {
                infoText = "😕 没有找到相关歌曲";
                return;
            }
            infoText = "🎵 找到 " + songs.size() + " 首歌曲，点击播放";
            renderSongButtons(songs);
        }));
    }

    private void renderSongButtons(List<NcmApiClient.SearchSong> songs) {
        clearSongButtons();
        currentSongs = new ArrayList<>(songs);  // 保存搜索结果
        scrollOffset = 0;  // 重置滚动位置
        rebuildSongButtons();
    }

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
                        infoText = "🎵 正在播放: " + songName;
                    })
                    .bounds(x, yy, listWidth, h)
                    .build();
            songButtons.add(btn);
            addRenderableWidget(btn);
        }

        // 更新提示信息
        if (currentSongs.size() > maxVisible) {
            infoText = "🎵 找到 " + currentSongs.size() + " 首歌曲 (显示 " + (scrollOffset + 1) + "-" + endIndex + "，滚轮翻页)";
        } else {
            infoText = "🎵 找到 " + currentSongs.size() + " 首歌曲，点击播放";
        }

        // ensure visibility matches current tab
        for (Button b : songButtons) b.visible = (tab == Tab.SEARCH);
    }

    // ---------------- QR LOGIN ----------------

    private void refreshQrAsync() {
        stopPolling();
        qrStatus = "正在生成二维码...";
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
                        qrStatus = "请扫码并在手机确认";
                    } catch (Exception e) {
                        errorText = "二维码渲染失败: " + e.getMessage();
                        qrStatus = "渲染失败";
                    }
                });

                // 3) start polling
                startPolling(client);

            } catch (Exception e) {
                errorText = e.getClass().getSimpleName() + ": " + e.getMessage();
                qrStatus = "生成失败";
            }
        }, exec);
    }

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
                    case 801 -> qrStatus = "等待扫码...";
                    case 802 -> qrStatus = "已扫码，手机确认中...";
                    case 800 -> {
                        qrStatus = "二维码过期，正在刷新...";
                        Minecraft.getInstance().execute(this::refreshQrAsync);
                    }
                    case 803 -> {
                        qrStatus = "登录成功，保存中...";
                        onLoginSuccess(cookieRaw);
                    }
                    default -> qrStatus = "状态: " + code;
                }
            } catch (Exception e) {
                errorText = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void onLoginSuccess(String cookieRaw) {
        stopPolling();

        String cookieForApi = CookieSanitizer.sanitizeForApi(cookieRaw);
        boolean ok = CookieSanitizer.hasMusicU(cookieForApi);

        try {
            SessionStore.save(new SessionStore.Session(baseUrl, cookieForApi, System.currentTimeMillis()));
            refreshInfo();
            qrStatus = ok ? "已保存 (MUSIC_U OK)" : "已保存，但缺少 MUSIC_U";
        } catch (Exception e) {
            errorText = "保存失败: " + e.getMessage();
            qrStatus = "保存失败";
            return;
        }

        // After success, go back to search tab
        Minecraft.getInstance().execute(() -> setTab(Tab.SEARCH));
    }

    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(true);
            pollFuture = null;
        }
    }
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

    private void deleteQrTexture() {
        qrTextureLocation = null;
        if (qrTexture != null) {
            try { qrTexture.close(); } catch (Exception ignored) {}
            qrTexture = null;
        }
        qrW = 0;
        qrH = 0;
    }

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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty: keep background clear for QR scanning (no dim overlay)
    }

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {
        // Intentionally empty: keep background clear for QR scanning
    }

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

        // info 文字 - 根据内容选择颜色
        int infoColor = COLOR_TEXT_SECONDARY;
        if (infoText.contains("✅") || infoText.contains("成功")) {
            infoColor = COLOR_TEXT_SUCCESS;
        } else if (infoText.contains("🔍") || infoText.contains("🎵")) {
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
            g.drawCenteredString(this.font, "⏳ 加载中...", cx, top + boxSize / 2 - 4, 0x888888);
        }

        // 状态文字 - 在刷新按钮上方
        int statusColor = COLOR_TEXT_SECONDARY;
        String statusIcon = "📱 ";
        if (qrStatus.contains("成功")) {
            statusColor = COLOR_TEXT_SUCCESS;
            statusIcon = "✅ ";
        } else if (qrStatus.contains("失败") || qrStatus.contains("过期")) {
            statusColor = COLOR_TEXT_ERROR;
            statusIcon = "❌ ";
        } else if (qrStatus.contains("扫码") || qrStatus.contains("确认")) {
            statusColor = COLOR_ACCENT_LIGHT;
            statusIcon = "📲 ";
        }

        String statusText = statusIcon + qrStatus;
        g.drawCenteredString(this.font, statusText, cx, statusTextY, statusColor);
    }
}
