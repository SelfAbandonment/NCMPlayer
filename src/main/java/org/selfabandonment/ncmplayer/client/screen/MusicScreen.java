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
import org.selfabandonment.ncmplayer.config.ModConfig;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;
import org.selfabandonment.ncmplayer.util.I18n;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;

/**
 * 网易云音乐播放器界面
 *
 * @author SelfAbandonment
 */
public final class MusicScreen extends Screen {

    private enum Tab { SEARCH, QR_LOGIN }

    private final String baseUrl;
    private Tab tab = Tab.SEARCH;

    private ScheduledExecutorService exec;
    private volatile String infoText = "";
    private volatile String errorText = "";

    // 搜索组件
    private EditBox keywordBox;
    private Button searchBtn;
    private Button pauseBtn;
    private Button stopBtn;
    private Button toQrBtn;
    private Button clearSessionBtn;
    private final List<Button> songButtons = new ArrayList<>();
    private List<NcmApiClient.SearchSong> currentSongs = new ArrayList<>();
    private int scrollOffset = 0;

    // 扫码登录组件
    private Button backToSearchBtn;
    private Button refreshQrBtn;
    private volatile String unikey;
    private volatile int lastCode = -1;
    private volatile String qrStatus;
    private ScheduledFuture<?> pollFuture;

    @Nullable private DynamicTexture qrTexture;
    @Nullable private ResourceLocation qrTextureLocation;
    private int qrW = 0, qrH = 0;

    // 主题颜色
    private static final int COLOR_BG_DARK = FastColor.ARGB32.color(220, 20, 20, 25);
    private static final int COLOR_BG_PANEL = FastColor.ARGB32.color(200, 35, 35, 45);
    private static final int COLOR_ACCENT = FastColor.ARGB32.color(255, 225, 60, 80);
    private static final int COLOR_ACCENT_LIGHT = FastColor.ARGB32.color(255, 255, 100, 120);
    private static final int COLOR_TEXT_PRIMARY = 0xFFFFFF;
    private static final int COLOR_TEXT_SECONDARY = 0xBBBBBB;
    private static final int COLOR_TEXT_ERROR = 0xFF6B6B;
    private static final int COLOR_TEXT_SUCCESS = 0x6BFF6B;
    private static final int COLOR_BORDER = FastColor.ARGB32.color(255, 60, 60, 70);

    // 布局常量
    private static final int HEADER_HEIGHT = 50;
    private static final int FOOTER_HEIGHT = 45;
    private static final int SIDE_MARGIN = 20;

    public MusicScreen(String baseUrl) {
        super(I18n.translate(I18n.MUSIC_TITLE));
        this.baseUrl = baseUrl;
        this.qrStatus = I18n.translateString(I18n.MUSIC_QR_NOT_STARTED);
    }

    @Override
    protected void init() {
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ncmplayer-music-ui");
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

        toQrBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_QR_LOGIN), b -> setTab(Tab.QR_LOGIN))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(toQrBtn);

        backToSearchBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_BACK_SEARCH), b -> setTab(Tab.SEARCH))
                .bounds(SIDE_MARGIN, 15, 100, 20).build();
        addRenderableWidget(backToSearchBtn);

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

        int btnWidth = 75;
        int btnGap = 10;
        int totalBtnWidth = btnWidth * 3 + btnGap * 2;
        int btnStartX = cx - totalBtnWidth / 2;

        pauseBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_PAUSE), b -> MusicController.togglePause())
                .bounds(btnStartX, footerY, btnWidth, 20).build();
        addRenderableWidget(pauseBtn);

        stopBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_STOP), b -> MusicController.stop())
                .bounds(btnStartX + btnWidth + btnGap, footerY, btnWidth, 20).build();
        addRenderableWidget(stopBtn);

        clearSessionBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_LOGOUT), b -> clearSession())
                .bounds(btnStartX + (btnWidth + btnGap) * 2, footerY, btnWidth, 20).build();
        addRenderableWidget(clearSessionBtn);

        refreshQrBtn = Button.builder(I18n.translate(I18n.MUSIC_BTN_REFRESH_QR), b -> refreshQrAsync())
                .bounds(cx - 60, footerY, 120, 22).build();
        addRenderableWidget(refreshQrBtn);
    }

    private void setTab(Tab t) {
        this.tab = t;
        this.errorText = "";

        boolean search = (t == Tab.SEARCH);
        boolean qr = (t == Tab.QR_LOGIN);

        toQrBtn.visible = search;
        backToSearchBtn.visible = qr;
        keywordBox.visible = search;
        searchBtn.visible = search;
        pauseBtn.visible = search;
        stopBtn.visible = search;
        clearSessionBtn.visible = search;
        for (Button b : songButtons) b.visible = search;
        refreshQrBtn.visible = qr;

        if (qr) {
            if (qrTextureLocation == null) refreshQrAsync();
        } else {
            stopPolling();
        }
        refreshInfo();
    }

    private void refreshInfo() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            infoText = I18n.translateString(I18n.MUSIC_INFO_WELCOME);
            return;
        }
        boolean has = CookieSanitizer.hasMusicU(session.cookieForApi());
        infoText = has ? I18n.translateString(I18n.MUSIC_INFO_LOGGED_IN) : I18n.translateString(I18n.MUSIC_INFO_LOGIN_INCOMPLETE);
    }

    private void clearSongButtons() {
        for (Button b : songButtons) removeWidget(b);
        songButtons.clear();
    }

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

    private void renderSongButtons(List<NcmApiClient.SearchSong> songs) {
        clearSongButtons();
        currentSongs = new ArrayList<>(songs);
        scrollOffset = 0;
        rebuildSongButtons();
    }

    private void rebuildSongButtons() {
        for (Button b : songButtons) removeWidget(b);
        songButtons.clear();

        if (currentSongs.isEmpty()) return;

        int cx = this.width / 2;
        int listWidth = Math.min(360, this.width - 40);
        int x = cx - listWidth / 2;
        int y = HEADER_HEIGHT + 38;
        int h = 20;
        int gap = 2;

        int availableHeight = this.height - FOOTER_HEIGHT - y - 5;
        int maxVisible = availableHeight / (h + gap);
        int maxScroll = Math.max(0, currentSongs.size() - maxVisible);
        if (scrollOffset > maxScroll) scrollOffset = maxScroll;
        if (scrollOffset < 0) scrollOffset = 0;

        int endIndex = Math.min(scrollOffset + maxVisible, currentSongs.size());

        for (int i = scrollOffset; i < endIndex; i++) {
            var s = currentSongs.get(i);
            String artist = s.artist().isBlank() ? "" : " - " + s.artist();
            String label = "♪ " + s.name() + artist;

            int maxLen = listWidth / 6;
            if (label.length() > maxLen) label = label.substring(0, maxLen) + "...";

            int yy = y + (i - scrollOffset) * (h + gap);

            final String songName = s.name();
            Button btn = Button.builder(Component.literal(label), b -> {
                        MusicController.playSongId(s.id());
                        infoText = I18n.translateString(I18n.MUSIC_INFO_NOW_PLAYING, songName);
                    })
                    .bounds(x, yy, listWidth, h)
                    .build();
            songButtons.add(btn);
            addRenderableWidget(btn);
        }

        if (currentSongs.size() > maxVisible) {
            infoText = I18n.translateString(I18n.MUSIC_INFO_FOUND_SONGS_SCROLL, currentSongs.size(), scrollOffset + 1, endIndex);
        } else {
            infoText = I18n.translateString(I18n.MUSIC_INFO_FOUND_SONGS, currentSongs.size());
        }

        for (Button b : songButtons) b.visible = (tab == Tab.SEARCH);
    }

    private void refreshQrAsync() {
        stopPolling();
        qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATING);
        errorText = "";
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
                        errorText = I18n.translateString(I18n.MUSIC_QR_RENDER_FAILED, e.getMessage());
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_RENDER_FAILED_SHORT);
                    }
                });

                startPolling(client);
            } catch (Exception e) {
                errorText = e.getClass().getSimpleName() + ": " + e.getMessage();
                qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATE_FAILED);
            }
        }, exec);
    }

    private void startPolling(NcmApiClient client) {
        if (exec == null || unikey == null || unikey.isBlank()) return;

        pollFuture = exec.scheduleAtFixedRate(() -> {
            try {
                JsonObject check = client.qrCheck(unikey);
                int code = check.get("code").getAsInt();
                lastCode = code;

                String cookieRaw = check.has("cookie") && !check.get("cookie").isJsonNull()
                        ? check.get("cookie").getAsString() : "";

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
        qrTextureLocation = ResourceLocation.fromNamespaceAndPath("ncmplayer", "ncm_qr/" + UUID.randomUUID());
        tm.register(qrTextureLocation, qrTexture);
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
            int scrollAmount = (int) -scrollY;
            int newOffset = scrollOffset + scrollAmount;

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
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void renderTransparentBackground(GuiGraphics graphics) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, COLOR_BG_DARK);

        int cx = this.width / 2;

        g.fill(0, 0, this.width, HEADER_HEIGHT, COLOR_BG_PANEL);
        g.fill(0, HEADER_HEIGHT - 2, this.width, HEADER_HEIGHT, COLOR_ACCENT);
        g.drawCenteredString(this.font, this.title, cx, 6, COLOR_TEXT_PRIMARY);

        int infoColor = COLOR_TEXT_SECONDARY;
        if (infoText.contains("✅")) infoColor = COLOR_TEXT_SUCCESS;
        else if (infoText.contains("🔍") || infoText.contains("🎵") || infoText.contains("✨")) infoColor = COLOR_ACCENT_LIGHT;
        g.drawCenteredString(this.font, infoText, cx, 22, infoColor);

        if (!errorText.isBlank()) {
            g.drawCenteredString(this.font, "❌ " + errorText, cx, 36, COLOR_TEXT_ERROR);
        }

        int footerTop = this.height - FOOTER_HEIGHT;
        g.fill(0, footerTop, this.width, this.height, COLOR_BG_PANEL);
        g.fill(0, footerTop, this.width, footerTop + 2, COLOR_BORDER);

        if (tab == Tab.QR_LOGIN) drawQrPanel(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawQrPanel(GuiGraphics g) {
        int cx = this.width / 2;

        int refreshY = (refreshQrBtn != null ? refreshQrBtn.getY() : (this.height - 28));
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

        int borderSize = 3;
        g.fill(boxX - borderSize, top - borderSize,
               boxX + boxSize + borderSize, top + boxSize + borderSize, COLOR_ACCENT);
        g.fill(boxX, top, boxX + boxSize, top + boxSize, FastColor.ARGB32.color(255, 255, 255, 255));

        if (qrTextureLocation != null && qrW > 0 && qrH > 0) {
            RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            float scaleF = Math.min((float) inner / qrW, (float) inner / qrH);
            int drawW = Math.max(1, Math.round(qrW * scaleF));
            int drawH = Math.max(1, Math.round(qrH * scaleF));

            int dx = boxX + padding + (inner - drawW) / 2;
            int dy = top + padding + (inner - drawH) / 2;

            g.blit(qrTextureLocation, dx, dy, drawW, drawH, 0, 0, qrW, qrH, qrW, qrH);
            RenderSystem.enableBlend();
        } else {
            g.drawCenteredString(this.font, I18n.translateString(I18n.MUSIC_QR_LOADING), cx, top + boxSize / 2 - 4, 0x888888);
        }

        int statusColor = COLOR_TEXT_SECONDARY;
        String statusIcon = "📱 ";

        if (lastCode == 803) { statusColor = COLOR_TEXT_SUCCESS; statusIcon = "✅ "; }
        else if (lastCode == 800 || qrStatus.contains("failed") || qrStatus.contains("失败")) { statusColor = COLOR_TEXT_ERROR; statusIcon = "❌ "; }
        else if (lastCode == 801 || lastCode == 802) { statusColor = COLOR_ACCENT_LIGHT; statusIcon = "📲 "; }

        g.drawCenteredString(this.font, statusIcon + qrStatus, cx, statusTextY, statusColor);
    }
}