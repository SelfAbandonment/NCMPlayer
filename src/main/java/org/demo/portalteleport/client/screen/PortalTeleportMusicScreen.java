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

    public PortalTeleportMusicScreen(String baseUrl) {
        super(Component.literal("PortalTeleport - NCM"));
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

        // Tab buttons
        toQrBtn = Button.builder(Component.literal("二维码登录"), b -> setTab(Tab.QR_LOGIN))
                .bounds(10, 10, 90, 20).build();
        addRenderableWidget(toQrBtn);

        backToSearchBtn = Button.builder(Component.literal("返回搜索"), b -> setTab(Tab.SEARCH))
                .bounds(10, 10, 90, 20).build();
        addRenderableWidget(backToSearchBtn);

        // SEARCH tab
        keywordBox = new EditBox(this.font, cx - 120, 40, 240, 20, Component.literal("关键词"));
        keywordBox.setValue("周杰伦");
        addRenderableWidget(keywordBox);

        searchBtn = Button.builder(Component.literal("搜索"), b -> doSearchAsync())
                .bounds(cx - 40, 66, 80, 20).build();
        addRenderableWidget(searchBtn);

        pauseBtn = Button.builder(Component.literal("暂停/继续"), b -> ClientMusicController.togglePause())
                .bounds(cx - 120, this.height - 28, 80, 20).build();
        addRenderableWidget(pauseBtn);

        stopBtn = Button.builder(Component.literal("停止"), b -> ClientMusicController.stop())
                .bounds(cx - 35, this.height - 28, 60, 20).build();
        addRenderableWidget(stopBtn);

        clearSessionBtn = Button.builder(Component.literal("清除登录"), b -> clearSession())
                .bounds(cx + 35, this.height - 28, 80, 20).build();
        addRenderableWidget(clearSessionBtn);

        // QR tab
        refreshQrBtn = Button.builder(Component.literal("刷新二维码"), b -> refreshQrAsync())
                .bounds(cx - 50, this.height - 28, 100, 20).build();
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
            infoText = "未登录（需要二维码登录后才能播放会员/高音质）";
            return;
        }
        boolean has = CookieSanitizer.hasMusicU(session.cookieForApi());
        infoText = "已保存登录态: " + (has ? "MUSIC_U OK" : "缺少 MUSIC_U");
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
            infoText = "已清除登录态";
        } catch (Exception e) {
            errorText = "清除失败: " + e.getMessage();
        }
    }

    private void doSearchAsync() {
        errorText = "";
        clearSongButtons();

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null) {
            errorText = "未登录：请点左上角“二维码登录”";
            return;
        }
        String cookie = session.cookieForApi();
        if (!CookieSanitizer.hasMusicU(cookie)) {
            errorText = "cookie 缺少 MUSIC_U：请重新二维码登录";
            return;
        }

        String keywords = keywordBox.getValue().trim();
        if (keywords.isEmpty()) {
            errorText = "关键词不能为空";
            return;
        }

        NcmApiClient client = new NcmApiClient(session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl : session.baseUrl());

        infoText = "搜索中...";
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
                infoText = "无结果";
                return;
            }
            infoText = "点击条目播放";
            renderSongButtons(songs);
        }));
    }

    private void renderSongButtons(List<NcmApiClient.SearchSong> songs) {
        clearSongButtons();

        int cx = this.width / 2;
        int x = cx - 160;
        int y = 94;
        int w = 320;
        int h = 20;
        int gap = 2;

        for (int i = 0; i < songs.size(); i++) {
            var s = songs.get(i);
            String label = s.name() + (s.artist().isBlank() ? "" : " - " + s.artist());

            if (label.length() > 60) label = label.substring(0, 60) + "...";
            int yy = y + i * (h + gap);

            Button btn = Button.builder(Component.literal(label), b -> {
                        ClientMusicController.playSongId(s.id());
                        infoText = "请求播放: " + s.name();
                    })
                    .bounds(x, yy, w, h)
                    .build();
            songButtons.add(btn);
            addRenderableWidget(btn);
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

    // Padding added to the texture to prevent edge sampling artifacts.
    // We keep it small and DO NOT cut into the original QR content.
    private static final int QR_PAD = 1;

    private void loadQrTexture(String dataUrl) throws Exception {
        deleteQrTexture();

        String prefix = "data:image/png;base64,";
        String b64 = dataUrl.startsWith(prefix) ? dataUrl.substring(prefix.length()) : dataUrl;

        byte[] png = Base64.getDecoder().decode(b64);
        try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
            NativeImage src = NativeImage.read(in);

            int sw = src.getWidth();
            int sh = src.getHeight();

            // Create a new image with a tiny border. Border pixels repeat edge color, not pure white,
            // so even if sampling hits it, it won't create a visible "white frame".
            NativeImage padded = new NativeImage(sw + QR_PAD * 2, sh + QR_PAD * 2, false);

            // Copy edge-extended padding (replicate nearest edge pixels)
            for (int y = 0; y < padded.getHeight(); y++) {
                int sy = Math.min(sh - 1, Math.max(0, y - QR_PAD));
                for (int x = 0; x < padded.getWidth(); x++) {
                    int sx = Math.min(sw - 1, Math.max(0, x - QR_PAD));
                    padded.setPixelRGBA(x, y, src.getPixelRGBA(sx, sy));
                }
            }

            src.close();

            qrW = padded.getWidth();
            qrH = padded.getHeight();
            qrTexture = new DynamicTexture(padded);
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
        int cx = this.width / 2;
        g.drawCenteredString(this.font, this.title, cx, 10, 0xFFFFFF);

        // info/error line
        g.drawCenteredString(this.font, infoText, cx, 26, 0xAAAAAA);
        if (!errorText.isBlank()) {
            g.drawCenteredString(this.font, errorText, cx, 36, 0xFF5555);
        }

        if (tab == Tab.QR_LOGIN) {
            drawQrPanel(g);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawQrPanel(GuiGraphics g) {
        int cx = this.width / 2;

        int refreshY = (refreshQrBtn != null ? refreshQrBtn.getY() : (this.height - 28));
        int bottomLimit = refreshY - 10;

        int topMin = 52;
        int maxBoxSizeByHeight = Math.max(140, bottomLimit - topMin);
        int preferred = 260;
        int boxSize = Math.min(preferred, maxBoxSizeByHeight);

        int boxX = cx - boxSize / 2;
        int top = bottomLimit - boxSize;
        if (top < topMin) top = topMin;

        int padding = 8;                 // bigger padding so QR never touches border
        int inner = boxSize - padding * 2;

        // light gray border + white panel (best for scanning)
        g.fill(boxX - 2, top - 2, boxX + boxSize + 2, top + boxSize + 2,
                FastColor.ARGB32.color(255, 220, 220, 220));
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

            // blit(location, x, y, width, height, uOffset, vOffset, uWidth, vHeight, texW, texH)
            // Draw the FULL texture (0,0 to qrW,qrH) scaled into (dx,dy to dx+drawW, dy+drawH)
            g.blit(qrTextureLocation,
                    dx, dy,              // screen position
                    drawW, drawH,        // draw size on screen
                    0, 0,                // UV offset in texture
                    qrW, qrH,            // UV region size (full texture)
                    qrW, qrH);           // texture dimensions

            RenderSystem.enableBlend();
        } else {
            g.drawCenteredString(this.font, "加载中...", cx, top + boxSize / 2 - 4, 0xAAAAAA);
        }

        g.drawCenteredString(this.font,
                qrStatus + (lastCode == -1 ? "" : (" (code=" + lastCode + ")")),
                cx, bottomLimit + 2, 0xFFFFFF);
    }
}
