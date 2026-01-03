package org.selfabandonment.ncmplayer.client.screen;

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

import org.selfabandonment.ncmplayer.client.audio.MusicController;
import org.selfabandonment.ncmplayer.client.audio.Playlist;
import org.selfabandonment.ncmplayer.client.audio.StreamingMp3Player;
import org.selfabandonment.ncmplayer.config.ModConfig;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;
import org.selfabandonment.ncmplayer.util.I18n;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;

/**
 * 网易云音乐播放器界面
 *
 * @author SelfAbandonment
 */
public final class MusicScreen extends Screen {

    private enum Tab { PLAYER, PLAYLIST, USER_INFO, QR_LOGIN }

    private final String baseUrl;
    private Tab tab = Tab.PLAYER;

    private ScheduledExecutorService exec;
    private String loginStatus = "";

    // 搜索组件
    private EditBox keywordBox;
    private Button searchBtn;
    private Button loginBtn;
    private Button playlistBtn;
    private final List<Button> searchResultButtons = new ArrayList<>();
    private final List<Button> playlistButtons = new ArrayList<>();
    private List<NcmApiClient.SearchSong> searchResults = new ArrayList<>();
    private int searchScrollOffset = 0;
    private int playlistScrollOffset = 0;

    // 播放控制
    private Button prevBtn;
    private Button playPauseBtn;
    private Button nextBtn;
    private Button shuffleBtn;
    private Button repeatBtn;

    // 音量和进度条区域
    private boolean draggingVolume = false;
    private boolean draggingProgress = false;
    private float dragProgress = 0f;
    private int volumeSliderX, volumeSliderY, volumeSliderW, volumeSliderH;
    private int progressBarX, progressBarY, progressBarW, progressBarH;

    // 搜索列表区域
    private int listX, listY, listW, listH;

    // 扫码登录
    private Button backBtn;
    private Button refreshQrBtn;
    private volatile String unikey;
    private volatile int lastCode = -1;
    private volatile String qrStatus;
    private ScheduledFuture<?> pollFuture;

    @Nullable private DynamicTexture qrTexture;
    @Nullable private ResourceLocation qrTextureLocation;
    private int qrW = 0, qrH = 0;

    // 播放列表页面
    private Button backFromPlaylistBtn;

    // 用户信息页面
    private Button backFromUserBtn;
    private Button userBtn;
    private Button logoutBtn;
    private volatile NcmApiClient.UserDetail userDetail;
    private volatile NcmApiClient.UserSubcount userSubcount;
    private volatile boolean loadingUserInfo = false;

    // 主题颜色
    private static final int COLOR_BG = FastColor.ARGB32.color(245, 24, 24, 28);
    private static final int COLOR_PANEL = FastColor.ARGB32.color(255, 32, 32, 38);
    private static final int COLOR_ACCENT = FastColor.ARGB32.color(255, 236, 65, 65);
    private static final int COLOR_ACCENT_DIM = FastColor.ARGB32.color(255, 180, 50, 50);
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_TEXT_DIM = 0x888888;
    private static final int COLOR_TEXT_SUCCESS = 0x66FF66;
    private static final int COLOR_SLIDER_BG = FastColor.ARGB32.color(200, 50, 50, 55);
    private static final int COLOR_SLIDER_HANDLE = FastColor.ARGB32.color(255, 255, 255, 255);

    // 布局
    private static final int HEADER_HEIGHT = 35;
    private static final int FOOTER_HEIGHT = 65;
    private static final int LIST_ITEM_HEIGHT = 22;

    public MusicScreen(String baseUrl) {
        super(I18n.translate(I18n.MUSIC_TITLE));
        this.baseUrl = baseUrl;
        this.qrStatus = I18n.translateString(I18n.MUSIC_QR_NOT_STARTED);
    }

    @Override
    protected void init() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ncmplayer-ui");
            t.setDaemon(true);
            return t;
        });

        buildWidgets();
        updateLoginStatus();
        setTab(Tab.PLAYER);
    }

    @Override
    public void onClose() {
        super.onClose();
        stopPolling();
        deleteQrTexture();
        if (exec != null) exec.shutdownNow();
        exec = null;
    }

    private void updateLoginStatus() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            loginStatus = "未登录";
        } else {
            boolean valid = CookieSanitizer.hasMusicU(session.cookieForApi());
            if (valid) {
                if (session.hasUserInfo()) {
                    // 显示用户昵称
                    String name = session.nickname();
                    if (name.length() > 8) name = name.substring(0, 8) + "..";
                    loginStatus = name;
                } else {
                    loginStatus = "已登录";
                }
            } else {
                loginStatus = "登录失效";
            }
        }
    }

    private void buildWidgets() {
        int cx = this.width / 2;
        int contentW = Math.min(360, this.width - 30);
        int contentL = cx - contentW / 2;

        // === 播放器页面 ===

        // 右上角按钮（紧凑排列，使用统一样式）
        int topBtnY = 6;
        int topBtnH = 16;
        int topBtnW = 20;
        int topBtnGap = 2;
        int rightEdge = contentL + contentW;

        // 登录按钮（最右边）
        loginBtn = Button.builder(Component.literal("🔐"), b -> setTab(Tab.QR_LOGIN))
                .bounds(rightEdge - topBtnW, topBtnY, topBtnW, topBtnH).build();
        addRenderableWidget(loginBtn);

        // 用户信息按钮（登录按钮左边）
        userBtn = Button.builder(Component.literal("👤"), b -> setTab(Tab.USER_INFO))
                .bounds(rightEdge - topBtnW * 2 - topBtnGap, topBtnY, topBtnW, topBtnH).build();
        addRenderableWidget(userBtn);

        // 搜索栏
        int searchY = HEADER_HEIGHT + 5;
        keywordBox = new EditBox(this.font, contentL, searchY, contentW - 55, 20, Component.literal(""));
        keywordBox.setHint(Component.literal("搜索歌曲..."));
        keywordBox.setMaxLength(100);
        addRenderableWidget(keywordBox);

        searchBtn = Button.builder(Component.literal("🔍"), b -> doSearchAsync())
                .bounds(contentL + contentW - 50, searchY, 50, 20).build();
        addRenderableWidget(searchBtn);

        // 搜索结果列表区域
        listX = contentL;
        listY = HEADER_HEIGHT + 32;
        listW = contentW;
        listH = this.height - listY - FOOTER_HEIGHT - 5;

        // 底部控制区
        int footerTop = this.height - FOOTER_HEIGHT;

        // 进度条（居中，留出时间显示空间）
        progressBarW = contentW - 80;
        progressBarH = 4;
        progressBarX = contentL + 40;
        progressBarY = footerTop + 12;

        // 底部按钮行 - 所有按钮居中排列
        int btnY = footerTop + 35;
        int btnH = 20;
        int btnW = 24;
        int gap = 6;

        // 按钮顺序: [📋] [🔀] [⏮] [▶] [⏭] [🔁] [🔊━━]
        int sliderW = 40;
        int totalW = btnW * 6 + gap * 6 + sliderW;
        int startX = cx - totalW / 2;

        playlistBtn = Button.builder(Component.literal("📋"), b -> setTab(Tab.PLAYLIST))
                .bounds(startX, btnY, btnW, btnH).build();
        addRenderableWidget(playlistBtn);

        shuffleBtn = Button.builder(Component.literal("🔀"), b -> Playlist.toggleShuffle())
                .bounds(startX + btnW + gap, btnY, btnW, btnH).build();
        addRenderableWidget(shuffleBtn);

        prevBtn = Button.builder(Component.literal("⏮"), b -> Playlist.previous())
                .bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build();
        addRenderableWidget(prevBtn);

        playPauseBtn = Button.builder(Component.literal("▶"), b -> togglePlayPause())
                .bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build();
        addRenderableWidget(playPauseBtn);

        nextBtn = Button.builder(Component.literal("⏭"), b -> Playlist.next())
                .bounds(startX + (btnW + gap) * 4, btnY, btnW, btnH).build();
        addRenderableWidget(nextBtn);

        repeatBtn = Button.builder(Component.literal("🔁"), b -> Playlist.toggleRepeatMode())
                .bounds(startX + (btnW + gap) * 5, btnY, btnW, btnH).build();
        addRenderableWidget(repeatBtn);

        // 音量滑块
        volumeSliderW = sliderW;
        volumeSliderH = 4;
        volumeSliderX = startX + (btnW + gap) * 6;
        volumeSliderY = btnY + 8;


        // === 扫码登录页面 ===
        backBtn = Button.builder(Component.literal("← 返回"), b -> setTab(Tab.PLAYER))
                .bounds(contentL, 10, 60, 20).build();
        addRenderableWidget(backBtn);

        refreshQrBtn = Button.builder(Component.literal("刷新二维码"), b -> refreshQrAsync())
                .bounds(cx - 45, this.height - 35, 90, 20).build();
        addRenderableWidget(refreshQrBtn);

        // === 播放列表页面 ===
        backFromPlaylistBtn = Button.builder(Component.literal("← 返回"), b -> setTab(Tab.PLAYER))
                .bounds(contentL, 10, 60, 20).build();
        addRenderableWidget(backFromPlaylistBtn);

        // === 用户信息页面 ===
        backFromUserBtn = Button.builder(Component.literal("← 返回"), b -> setTab(Tab.PLAYER))
                .bounds(contentL, 10, 60, 20).build();
        addRenderableWidget(backFromUserBtn);

        logoutBtn = Button.builder(Component.literal("退出登录"), b -> doLogout())
                .bounds(cx - 40, this.height - 40, 80, 20).build();
        addRenderableWidget(logoutBtn);
    }

    private void doLogout() {
        try {
            java.nio.file.Files.deleteIfExists(SessionStore.debugPath());
        } catch (Exception ignored) {}
        userDetail = null;
        userSubcount = null;
        updateLoginStatus();
        setTab(Tab.PLAYER);
    }

    private void togglePlayPause() {
        var state = MusicController.getState();
        if (state == StreamingMp3Player.State.PAUSED) {
            MusicController.togglePause();
        } else if (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING) {
            MusicController.togglePause();
        } else if (state == StreamingMp3Player.State.STOPPED || state == StreamingMp3Player.State.IDLE) {
            // 如果停止了，从播放列表开始播放
            if (Playlist.size() > 0) {
                int idx = Playlist.getCurrentIndex();
                if (idx < 0) idx = 0;
                Playlist.playAt(idx);
            }
        }
    }

    private void setTab(Tab t) {
        this.tab = t;

        boolean player = (t == Tab.PLAYER);
        boolean playlist = (t == Tab.PLAYLIST);
        boolean userInfo = (t == Tab.USER_INFO);
        boolean qr = (t == Tab.QR_LOGIN);

        // 播放器页面
        loginBtn.visible = player;
        userBtn.visible = player;
        keywordBox.visible = player;
        searchBtn.visible = player;
        prevBtn.visible = player;
        playPauseBtn.visible = player;
        nextBtn.visible = player;
        shuffleBtn.visible = player;
        repeatBtn.visible = player;
        playlistBtn.visible = player;
        for (Button b : searchResultButtons) b.visible = player;

        // 播放列表页面
        backFromPlaylistBtn.visible = playlist;
        for (Button b : playlistButtons) b.visible = playlist;

        // 用户信息页面
        backFromUserBtn.visible = userInfo;
        logoutBtn.visible = userInfo;

        // 扫码页面
        backBtn.visible = qr;
        refreshQrBtn.visible = qr;

        if (qr && qrTextureLocation == null) {
            refreshQrAsync();
        }
        if (playlist) {
            rebuildPlaylistButtons();
        }
        if (userInfo) {
            loadUserInfoAsync();
        }

        updateLoginStatus();
    }

    private void loadUserInfoAsync() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            return;
        }
        if (loadingUserInfo) return;
        loadingUserInfo = true;

        String apiUrl = session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl : session.baseUrl();
        NcmApiClient client = new NcmApiClient(apiUrl);
        String cookie = session.cookieForApi();

        CompletableFuture.runAsync(() -> {
            try {
                var account = client.getUserAccount(cookie);
                if (account != null && account.userId() > 0) {
                    userDetail = client.getUserDetail(account.userId(), cookie);
                    userSubcount = client.getUserSubcount(cookie);

                    // 保存用户信息
                    SessionStore.Session newSession = new SessionStore.Session(
                            session.baseUrl(), session.cookieForApi(), session.savedAtEpochMs(),
                            account.userId(), account.nickname(), account.avatarUrl(), account.vipType()
                    );
                    SessionStore.save(newSession);
                    Minecraft.getInstance().execute(this::updateLoginStatus);
                }
            } catch (Exception ignored) {
            } finally {
                loadingUserInfo = false;
            }
        }, exec);
    }

    // ==================== 搜索 ====================

    private void doSearchAsync() {
        clearSearchButtons();

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            return;
        }

        String keywords = keywordBox.getValue().trim();
        if (keywords.isEmpty()) return;

        NcmApiClient client = new NcmApiClient(
                session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl : session.baseUrl());

        int limit = ModConfig.COMMON.musicSearchLimit.get();

        CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(keywords, limit, session.cookieForApi());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, exec).whenComplete((songs, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null || songs == null || songs.isEmpty()) return;
            searchResults = new ArrayList<>(songs);
            searchScrollOffset = 0;
            rebuildSearchButtons();
        }));
    }

    private void clearSearchButtons() {
        for (Button b : searchResultButtons) removeWidget(b);
        searchResultButtons.clear();
    }

    private void rebuildSearchButtons() {
        clearSearchButtons();
        if (searchResults.isEmpty()) return;

        int maxVisible = listH / LIST_ITEM_HEIGHT;
        int maxScroll = Math.max(0, searchResults.size() - maxVisible);
        searchScrollOffset = Math.max(0, Math.min(searchScrollOffset, maxScroll));

        int endIdx = Math.min(searchScrollOffset + maxVisible, searchResults.size());

        for (int i = searchScrollOffset; i < endIdx; i++) {
            var song = searchResults.get(i);
            int yy = listY + (i - searchScrollOffset) * LIST_ITEM_HEIGHT;

            String label = truncate(song.name() + " - " + song.artist(), listW / 6);
            Button btn = Button.builder(Component.literal(label), b -> Playlist.play(song)).bounds(listX, yy, listW - 30, LIST_ITEM_HEIGHT - 2).build();
            searchResultButtons.add(btn);
            addRenderableWidget(btn);

            // 添加到列表
            Button addBtn = Button.builder(Component.literal("+"), b -> Playlist.add(song))
                    .bounds(listX + listW - 26, yy, 24, LIST_ITEM_HEIGHT - 2).build();
            searchResultButtons.add(addBtn);
            addRenderableWidget(addBtn);
        }

        for (Button b : searchResultButtons) b.visible = (tab == Tab.PLAYER);
    }

    // ==================== 播放列表 ====================

    private void clearPlaylistButtons() {
        for (Button b : playlistButtons) removeWidget(b);
        playlistButtons.clear();
    }

    private void rebuildPlaylistButtons() {
        clearPlaylistButtons();
        var songs = Playlist.getSongs();
        if (songs.isEmpty()) return;

        int maxVisible = (this.height - HEADER_HEIGHT - 50) / LIST_ITEM_HEIGHT;
        int maxScroll = Math.max(0, songs.size() - maxVisible);
        playlistScrollOffset = Math.max(0, Math.min(playlistScrollOffset, maxScroll));

        int endIdx = Math.min(playlistScrollOffset + maxVisible, songs.size());
        int currentIdx = Playlist.getCurrentIndex();

        int cx = this.width / 2;
        int w = Math.min(340, this.width - 40);
        int x = cx - w / 2;

        for (int i = playlistScrollOffset; i < endIdx; i++) {
            var song = songs.get(i);
            int yy = HEADER_HEIGHT + 10 + (i - playlistScrollOffset) * LIST_ITEM_HEIGHT;

            String prefix = (i == currentIdx) ? "▶ " : "    ";
            String label = prefix + truncate(song.name() + " - " + song.artist(), (w - 30) / 6);

            final int idx = i;
            Button btn = Button.builder(Component.literal(label), b -> {
                Playlist.playAt(idx);
                rebuildPlaylistButtons();
            }).bounds(x, yy, w - 30, LIST_ITEM_HEIGHT - 2).build();
            playlistButtons.add(btn);
            addRenderableWidget(btn);

            Button removeBtn = Button.builder(Component.literal("×"), b -> {
                Playlist.remove(idx);
                rebuildPlaylistButtons();
            }).bounds(x + w - 26, yy, 24, LIST_ITEM_HEIGHT - 2).build();
            playlistButtons.add(removeBtn);
            addRenderableWidget(removeBtn);
        }

        for (Button b : playlistButtons) b.visible = (tab == Tab.PLAYLIST);
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }

    // ==================== 扫码登录 ====================

    private void refreshQrAsync() {
        stopPolling();
        qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATING);
        lastCode = -1;

        NcmApiClient client = new NcmApiClient(baseUrl);

        CompletableFuture.runAsync(() -> {
            try {
                String key = client.qrKey();
                this.unikey = key;

                JsonObject create = client.qrCreate(key, true);
                String qrimg = create.getAsJsonObject("data").get("qrimg").getAsString();

                Minecraft.getInstance().execute(() -> {
                    try {
                        loadQrTexture(qrimg);
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_SCAN_CONFIRM);
                    } catch (Exception e) {
                        qrStatus = "渲染失败";
                    }
                });

                startPolling(client);
            } catch (Exception e) {
                qrStatus = "生成失败: " + e.getMessage();
            }
        }, exec);
    }

    private void startPolling(NcmApiClient client) {
        if (exec == null || unikey == null) return;

        pollFuture = exec.scheduleAtFixedRate(() -> {
            try {
                JsonObject check = client.qrCheck(unikey);
                int code = check.get("code").getAsInt();
                lastCode = code;

                String cookieRaw = check.has("cookie") && !check.get("cookie").isJsonNull()
                        ? check.get("cookie").getAsString() : "";

                switch (code) {
                    case 801 -> qrStatus = "等待扫码...";
                    case 802 -> qrStatus = "已扫码，请确认";
                    case 800 -> {
                        qrStatus = "二维码过期";
                        Minecraft.getInstance().execute(this::refreshQrAsync);
                    }
                    case 803 -> {
                        qrStatus = "登录成功！";
                        onLoginSuccess(cookieRaw);
                    }
                    default -> qrStatus = "状态: " + code;
                }
            } catch (Exception ignored) {}
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void onLoginSuccess(String cookieRaw) {
        stopPolling();
        String cookieForApi = CookieSanitizer.sanitizeForApi(cookieRaw);
        try {
            SessionStore.save(new SessionStore.Session(baseUrl, cookieForApi, System.currentTimeMillis()));
            updateLoginStatus();
            Minecraft.getInstance().execute(() -> setTab(Tab.PLAYER));
        } catch (Exception ignored) {}
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
        qrTextureLocation = ResourceLocation.fromNamespaceAndPath("ncmplayer", "qr/" + UUID.randomUUID());
        tm.register(qrTextureLocation, qrTexture);
        tm.getTexture(qrTextureLocation).setFilter(false, false);
    }

    private void deleteQrTexture() {
        qrTextureLocation = null;
        if (qrTexture != null) {
            try { qrTexture.close(); } catch (Exception ignored) {}
            qrTexture = null;
        }
        qrW = qrH = 0;
    }

    // ==================== 输入 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.PLAYER && mouseY >= listY && mouseY <= listY + listH) {
            searchScrollOffset -= (int) scrollY;
            rebuildSearchButtons();
            return true;
        }
        if (tab == Tab.PLAYLIST) {
            playlistScrollOffset -= (int) scrollY;
            rebuildPlaylistButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && tab == Tab.PLAYER) {
            // 进度条 - 只有在有歌曲播放且可以跳转时才允许拖动
            if (isInRect(mouseX, mouseY, progressBarX - 5, progressBarY - 8, progressBarW + 10, progressBarH + 16)) {
                if (MusicController.canSeek() && MusicController.getDurationMs() > 0) {
                    draggingProgress = true;
                    updateProgressFromMouse(mouseX);
                    return true;
                }
            }
            // 音量
            if (isInRect(mouseX, mouseY, volumeSliderX - 3, volumeSliderY - 6, volumeSliderW + 6, volumeSliderH + 12)) {
                draggingVolume = true;
                updateVolumeFromMouse(mouseX);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingProgress) {
                MusicController.seekToProgress(dragProgress);
                draggingProgress = false;
            }
            draggingVolume = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (draggingVolume) {
                updateVolumeFromMouse(mouseX);
                return true;
            }
            if (draggingProgress) {
                updateProgressFromMouse(mouseX);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private boolean isInRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void updateVolumeFromMouse(double mouseX) {
        float v = (float) (mouseX - volumeSliderX) / volumeSliderW;
        MusicController.setVolume(Math.max(0, Math.min(1, v)));
    }

    private void updateProgressFromMouse(double mouseX) {
        dragProgress = (float) (mouseX - progressBarX) / progressBarW;
        dragProgress = Math.max(0, Math.min(1, dragProgress));
    }

    // ==================== 渲染 ====================

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float pt) {}

    @Override
    public void renderTransparentBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        // 背景
        g.fill(0, 0, width, height, COLOR_BG);

        if (tab == Tab.PLAYER) {
            renderPlayerTab(g, mouseX, mouseY);
        } else if (tab == Tab.PLAYLIST) {
            renderPlaylistTab(g);
        } else if (tab == Tab.USER_INFO) {
            renderUserInfoTab(g);
        } else {
            renderQrTab(g);
        }

        super.render(g, mouseX, mouseY, pt);
    }

    private void renderPlayerTab(GuiGraphics g, int mouseX, int mouseY) {
        int cx = width / 2;

        // 标题（居中）
        g.drawCenteredString(font, "♪ 网易云音乐", cx, 12, COLOR_TEXT);

        // 底部面板
        int footerTop = height - FOOTER_HEIGHT;
        g.fill(0, footerTop, width, height, COLOR_PANEL);

        // 进度条
        drawProgressBar(g, mouseX, mouseY);

        // 音量条
        drawVolumeSlider(g);

        // 更新播放按钮图标
        var state = MusicController.getState();
        String icon = (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING) ? "⏸" : "▶";
        playPauseBtn.setMessage(Component.literal(icon));

        // 更新模式按钮状态
        updateModeButtons();
    }

    private void updateModeButtons() {
        // 循环模式反馈
        String repeatIcon = switch (Playlist.getRepeatMode()) {
            case NONE -> "🔁";
            case ALL -> "🔂";  // 列表循环用不同图标
            case ONE -> "🔂";  // 单曲循环
        };
        repeatBtn.setMessage(Component.literal(repeatIcon));
    }

    private void drawProgressBar(GuiGraphics g, int mouseX, int mouseY) {
        var state = MusicController.getState();
        long durationMs = MusicController.getDurationMs();

        boolean hasPlayback = (state == StreamingMp3Player.State.PLAYING ||
                               state == StreamingMp3Player.State.BUFFERING ||
                               state == StreamingMp3Player.State.PAUSED) && durationMs > 0;

        // 进度条背景
        g.fill(progressBarX, progressBarY, progressBarX + progressBarW, progressBarY + progressBarH, COLOR_SLIDER_BG);

        if (!hasPlayback) {
            // 显示 --:-- / --:--
            g.drawString(font, "--:--", progressBarX - 28, progressBarY - 2, COLOR_TEXT_DIM);
            g.drawString(font, "--:--", progressBarX + progressBarW + 3, progressBarY - 2, COLOR_TEXT_DIM);
            return;
        }

        long playedMs = MusicController.getPlayedMs();
        float progress;
        if (draggingProgress) {
            progress = dragProgress;
            playedMs = (long) (durationMs * dragProgress);
        } else {
            progress = MusicController.getProgress();
        }

        // 时间文本（小字）
        String played = MusicController.formatTime(playedMs);
        String total = MusicController.formatTime(durationMs);
        g.drawString(font, played, progressBarX - 28, progressBarY - 2, COLOR_TEXT_DIM);
        g.drawString(font, total, progressBarX + progressBarW + 3, progressBarY - 2, COLOR_TEXT_DIM);

        // 进度填充
        int filledW = (int) (progressBarW * progress);
        if (filledW > 0) {
            g.fill(progressBarX, progressBarY, progressBarX + filledW, progressBarY + progressBarH, COLOR_ACCENT);
        }

        // 滑块（小圆点，悬停时显示）
        boolean hover = isInRect(mouseX, mouseY, progressBarX - 5, progressBarY - 6, progressBarW + 10, 16);
        if (hover || draggingProgress) {
            int hx = progressBarX + filledW - 3;
            g.fill(hx, progressBarY - 2, hx + 6, progressBarY + progressBarH + 2, COLOR_SLIDER_HANDLE);
        }
    }

    private void drawVolumeSlider(GuiGraphics g) {

        // 背景
        g.fill(volumeSliderX, volumeSliderY, volumeSliderX + volumeSliderW, volumeSliderY + volumeSliderH, COLOR_SLIDER_BG);

        // 填充
        float vol = MusicController.getVolume();
        int filledW = (int) (volumeSliderW * vol);
        if (filledW > 0) {
            g.fill(volumeSliderX, volumeSliderY, volumeSliderX + filledW, volumeSliderY + volumeSliderH,
                    FastColor.ARGB32.color(255, 100, 180, 100));
        }

        // 手柄
        int hx = volumeSliderX + filledW - 3;
        g.fill(hx, volumeSliderY - 2, hx + 6, volumeSliderY + volumeSliderH + 2, COLOR_SLIDER_HANDLE);
    }

    private void renderPlaylistTab(GuiGraphics g) {
        int cx = width / 2;
        g.drawCenteredString(font, "播放列表 (" + Playlist.size() + ")", cx, 14, COLOR_TEXT);

        if (Playlist.isEmpty()) {
            g.drawCenteredString(font, "播放列表为空", cx, height / 2, COLOR_TEXT_DIM);
        }
    }

    private void renderQrTab(GuiGraphics g) {
        int cx = width / 2;

        // 标题
        g.drawCenteredString(font, "扫码登录", cx, 12, COLOR_TEXT);

        // 二维码（居中显示）
        int qrSize = Math.min(160, Math.min(width - 60, height - 120));
        int qrX = cx - qrSize / 2;
        int qrY = (height - qrSize) / 2 - 20;

        // 白色背景 + 红色边框
        g.fill(qrX - 4, qrY - 4, qrX + qrSize + 4, qrY + qrSize + 4, COLOR_ACCENT);
        g.fill(qrX, qrY, qrX + qrSize, qrY + qrSize, 0xFFFFFFFF);

        if (qrTextureLocation != null && qrW > 0) {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);

            float scale = Math.min((float) qrSize / qrW, (float) qrSize / qrH);
            int dw = Math.round(qrW * scale);
            int dh = Math.round(qrH * scale);
            int dx = qrX + (qrSize - dw) / 2;
            int dy = qrY + (qrSize - dh) / 2;

            g.blit(qrTextureLocation, dx, dy, dw, dh, 0, 0, qrW, qrH, qrW, qrH);
            RenderSystem.enableBlend();
        } else {
            g.drawCenteredString(font, "⏳", cx, qrY + qrSize / 2, COLOR_TEXT_DIM);
        }

        // 状态文字（二维码下方）
        int statusY = qrY + qrSize + 15;
        int statusColor = switch (lastCode) {
            case 803 -> COLOR_TEXT_SUCCESS;
            case 800 -> 0xFF6666;
            case 802 -> 0xFFFF66;
            default -> COLOR_TEXT_DIM;
        };
        g.drawCenteredString(font, qrStatus, cx, statusY, statusColor);
    }

    private void renderUserInfoTab(GuiGraphics g) {
        int cx = width / 2;
        int contentW = Math.min(320, width - 40);
        int contentL = cx - contentW / 2;

        // 标题
        g.drawCenteredString(font, "用户信息", cx, 14, COLOR_TEXT);

        // 检查登录状态
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            g.drawCenteredString(font, "未登录", cx, height / 2 - 20, COLOR_TEXT_DIM);
            g.drawCenteredString(font, "请先扫码登录", cx, height / 2, COLOR_TEXT_DIM);
            return;
        }

        int y = 45;
        int lineH = 18;

        // 用户基本信息
        if (userDetail != null) {
            // 昵称和VIP
            String nickname = userDetail.nickname();
            String vip = userDetail.vipTypeString();
            int vipColor = userDetail.vipType() > 0 ? 0xFFD700 : COLOR_TEXT_DIM;

            g.drawCenteredString(font, nickname, cx, y, COLOR_TEXT);
            y += lineH;

            if (userDetail.vipType() > 0) {
                g.drawCenteredString(font, vip, cx, y, vipColor);
                y += lineH;
            }

            y += 5;

            // 等级
            g.drawString(font, "等级: Lv." + userDetail.level(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;

            // 累计听歌
            g.drawString(font, "累计听歌: " + userDetail.listenSongs() + " 首", contentL, y, COLOR_TEXT_DIM);
            y += lineH;

            // 签名
            if (userDetail.signature() != null && !userDetail.signature().isBlank()) {
                y += 5;
                String sig = userDetail.signature();
                if (sig.length() > 30) sig = sig.substring(0, 30) + "...";
                g.drawString(font, "签名: " + sig, contentL, y, COLOR_TEXT_DIM);
                y += lineH;
            }
        } else if (loadingUserInfo) {
            g.drawCenteredString(font, "加载中...", cx, y + 30, COLOR_TEXT_DIM);
        } else if (session.hasUserInfo()) {
            // 从 session 显示基本信息
            g.drawCenteredString(font, session.nickname(), cx, y, COLOR_TEXT);
            y += lineH;
            if (session.vipType() != null && session.vipType() > 0) {
                g.drawCenteredString(font, session.vipTypeString().trim(), cx, y, 0xFFD700);
                y += lineH;
            }
        }

        // 统计信息
        if (userSubcount != null) {
            y += 10;
            g.fill(contentL, y, contentL + contentW, y + 1, COLOR_ACCENT_DIM);
            y += 10;

            g.drawString(font, "创建歌单: " + userSubcount.playlistCount(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;
            g.drawString(font, "收藏歌单: " + userSubcount.subPlaylistCount(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;
            g.drawString(font, "收藏歌手: " + userSubcount.artistCount(), contentL, y, COLOR_TEXT_DIM);
        }
    }
}