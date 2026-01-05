package org.selfabandonment.ncmplayer.client.screen.tab;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;
import org.selfabandonment.ncmplayer.util.I18n;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.selfabandonment.ncmplayer.client.screen.UIConstants.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 扫码登录页面
 *
 * @author SelfAbandonment
 */
public class QrLoginTab extends AbstractTab {

    private static final Logger LOGGER = LoggerFactory.getLogger("ncmplayer");

    private Button backBtn;
    private Button refreshQrBtn;

    private volatile String unikey;
    private volatile int lastCode = -1;
    private volatile String qrStatus;
    private ScheduledFuture<?> pollFuture;

    @Nullable
    private DynamicTexture qrTexture;
    @Nullable
    private ResourceLocation qrTextureLocation;
    private int qrW = 0, qrH = 0;

    public QrLoginTab(MusicScreenContext ctx) {
        super(ctx);
        this.qrStatus = I18n.translateString(I18n.MUSIC_QR_NOT_STARTED);
    }

    @Override
    public void init() {
        int cx = width() / 2;
        int contentW = Math.min(360, width() - 30);
        int contentL = cx - contentW / 2;

        backBtn = ctx.addWidget(Button.builder(Component.literal("← 返回"),
                        b -> ctx.switchTab(MusicScreenContext.TabType.PLAYER))
                .bounds(contentL, 10, 60, 20).build());

        refreshQrBtn = ctx.addWidget(Button.builder(Component.literal("刷新二维码"),
                        b -> refreshQrAsync())
                .bounds(cx - 45, height() - 35, 90, 20).build());
    }

    @Override
    public void onActivate() {
        LOGGER.info("QrLoginTab.onActivate() called, qrTextureLocation={}", qrTextureLocation);
        if (qrTextureLocation == null) {
            refreshQrAsync();
        }
    }

    @Override
    public void onDeactivate() {
        stopPolling();
    }

    @Override
    public void cleanup() {
        stopPolling();
        deleteQrTexture();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;

        g.drawCenteredString(font(), "扫码登录", cx, 14, COLOR_TEXT);

        // 二维码（居中显示）
        int qrSize = Math.min(160, Math.min(width() - 60, height() - 120));
        int qrX = cx - qrSize / 2;
        int qrY = (height() - qrSize) / 2 - 20;

        // 白色背景 + 红色边框
        g.fill(qrX - 4, qrY - 4, qrX + qrSize + 4, qrY + qrSize + 4, COLOR_ACCENT);
        g.fill(qrX, qrY, qrX + qrSize, qrY + qrSize, 0xFFFFFFFF);

        if (qrTextureLocation != null && qrW > 0 && qrH > 0) {
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
            g.drawCenteredString(font(), "⏳", cx, qrY + qrSize / 2, COLOR_TEXT_DIM);
        }

        // 状态文字（二维码下方）
        int statusY = qrY + qrSize + 15;
        int statusColor = switch (lastCode) {
            case 803 -> COLOR_TEXT_SUCCESS;
            case 800 -> 0xFF6666;
            case 802 -> 0xFFFF66;
            default -> COLOR_TEXT_DIM;
        };
        g.drawCenteredString(font(), qrStatus, cx, statusY, statusColor);
    }

    @Override
    public List<AbstractWidget> getWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(backBtn);
        widgets.add(refreshQrBtn);
        return widgets;
    }

    private void refreshQrAsync() {
        LOGGER.info("refreshQrAsync() called, baseUrl={}", baseUrl());
        stopPolling();
        deleteQrTexture();
        qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATING);
        lastCode = -1;

        NcmApiClient client = new NcmApiClient(baseUrl());

        CompletableFuture.supplyAsync(() -> {
            LOGGER.info("Fetching QR key...");
            try {
                String key = client.qrKey();
                LOGGER.info("Got QR key: {}", key);
                JsonObject create = client.qrCreate(key, true);
                String base64 = create.getAsJsonObject("data").get("qrimg").getAsString();
                LOGGER.info("Got QR image, base64 length: {}", base64.length());
                return new String[]{key, base64};
            } catch (Exception e) {
                LOGGER.error("QR create failed", e);
                throw new RuntimeException("QR create failed: " + e.getMessage(), e);
            }
        }, exec()).whenComplete((arr, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null) {
                qrStatus = I18n.translateString(I18n.MUSIC_QR_GENERATE_FAILED) + ": " + err.getMessage();
                LOGGER.error("QR generation failed", err);
                return;
            }
            LOGGER.info("Loading QR image...");
            unikey = arr[0];
            loadQrImage(arr[1]);
        }));
    }

    private void loadQrImage(String base64) {
        LOGGER.info("loadQrImage called, base64 length: {}", base64.length());
        try {
            String data = base64.contains(",") ? base64.split(",")[1] : base64;
            byte[] imgBytes = Base64.getDecoder().decode(data);
            LOGGER.info("Decoded image bytes: {}", imgBytes.length);

            // 读取 PNG 图像
            NativeImage srcImage = NativeImage.read(new ByteArrayInputStream(imgBytes));
            int w = srcImage.getWidth();
            int h = srcImage.getHeight();
            LOGGER.info("NativeImage created: {}x{}, format={}", w, h, srcImage.format());

            // 删除旧纹理
            deleteQrTexture();

            // 设置尺寸
            qrW = w;
            qrH = h;

            // 创建动态纹理 - 直接使用原始图像
            qrTexture = new DynamicTexture(srcImage);
            qrTexture.upload();  // 确保上传到 GPU

            TextureManager tm = Minecraft.getInstance().getTextureManager();
            qrTextureLocation = ResourceLocation.fromNamespaceAndPath("ncmplayer", "qr_dynamic_" + System.nanoTime());
            tm.register(qrTextureLocation, qrTexture);
            LOGGER.info("QR texture registered: {}, qrW={}, qrH={}", qrTextureLocation, qrW, qrH);

            // 成功加载后更新状态并开始轮询
            qrStatus = I18n.translateString(I18n.MUSIC_QR_WAITING_SCAN);
            startPolling();
        } catch (Exception e) {
            qrStatus = I18n.translateString(I18n.MUSIC_QR_RENDER_FAILED_SHORT) + ": " + e.getMessage();
            LOGGER.error("QR image load failed", e);
        }
    }

    private void startPolling() {
        stopPolling();
        NcmApiClient client = new NcmApiClient(baseUrl());

        pollFuture = exec().scheduleWithFixedDelay(() -> {
            if (unikey == null) return;
            try {
                JsonObject check = client.qrCheck(unikey);
                int code = check.get("code").getAsInt();
                lastCode = code;

                switch (code) {
                    case 800 -> qrStatus = I18n.translateString(I18n.MUSIC_QR_EXPIRED_REFRESH);
                    case 801 -> qrStatus = I18n.translateString(I18n.MUSIC_QR_WAITING_SCAN);
                    case 802 -> qrStatus = I18n.translateString(I18n.MUSIC_QR_SCANNED_CONFIRM);
                    case 803 -> {
                        qrStatus = I18n.translateString(I18n.MUSIC_QR_LOGIN_SUCCESS_SAVING);
                        String cookie = check.get("cookie").getAsString();
                        saveSession(cookie);
                        stopPolling();
                    }
                    default -> qrStatus = I18n.translateString(I18n.MUSIC_QR_STATUS, code);
                }
            } catch (Exception ignored) {
            }
        }, 0, 2, TimeUnit.SECONDS);
    }

    private void stopPolling() {
        if (pollFuture != null) {
            pollFuture.cancel(false);
            pollFuture = null;
        }
    }

    private void saveSession(String cookie) {
        try {
            SessionStore.Session session = new SessionStore.Session(
                    baseUrl(), cookie, System.currentTimeMillis(),
                    0L, null, null, 0
            );
            SessionStore.save(session);
            qrStatus = I18n.translateString(I18n.MUSIC_QR_SAVED_OK);
            Minecraft.getInstance().execute(ctx::updateLoginStatus);
        } catch (Exception e) {
            qrStatus = I18n.translateString(I18n.MUSIC_QR_SAVE_FAILED_SHORT);
        }
    }

    private void deleteQrTexture() {
        if (qrTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(qrTextureLocation);
            qrTextureLocation = null;
        }
        if (qrTexture != null) {
            qrTexture.close();
            qrTexture = null;
        }
        qrW = 0;
        qrH = 0;
    }
}

