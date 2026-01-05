package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.client.audio.MusicController;
import org.selfabandonment.ncmplayer.client.audio.Playlist;
import org.selfabandonment.ncmplayer.client.audio.StreamingMp3Player;
import org.selfabandonment.ncmplayer.client.screen.UIConstants;
import org.selfabandonment.ncmplayer.config.ModConfig;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.selfabandonment.ncmplayer.client.screen.UIConstants.*;

/**
 * 播放器主页面
 *
 * @author SelfAbandonment
 */
public class PlayerTab extends AbstractTab {

    // 搜索组件
    private EditBox keywordBox;
    private Button searchBtn;
    private Button loginBtn;
    private Button userBtn;
    private Button playlistBtn;
    private final List<Button> searchResultButtons = new ArrayList<>();
    private List<NcmApiClient.SearchSong> searchResults = new ArrayList<>();
    private int searchScrollOffset = 0;

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

    // API 状态
    private volatile boolean apiAvailable = true;
    private volatile String apiError = null;

    public PlayerTab(MusicScreenContext ctx) {
        super(ctx);
    }

    @Override
    public void onDeactivate() {
        // 切换 Tab 时隐藏所有搜索结果按钮
        for (Button btn : searchResultButtons) {
            btn.visible = false;
        }
    }

    @Override
    public void init() {
        int cx = width() / 2;
        int contentW = Math.min(360, width() - 30);
        int contentL = cx - contentW / 2;

        // 右上角按钮
        int topBtnY = 6;
        int topBtnH = 16;
        int topBtnW = 20;
        int topBtnGap = 2;
        int rightEdge = contentL + contentW;

        loginBtn = ctx.addWidget(Button.builder(Component.literal("🔐"),
                b -> ctx.switchTab(MusicScreenContext.TabType.QR_LOGIN))
                .bounds(rightEdge - topBtnW, topBtnY, topBtnW, topBtnH).build());

        userBtn = ctx.addWidget(Button.builder(Component.literal("👤"),
                b -> ctx.switchTab(MusicScreenContext.TabType.USER_INFO))
                .bounds(rightEdge - topBtnW * 2 - topBtnGap, topBtnY, topBtnW, topBtnH).build());

        // 搜索栏
        int searchY = HEADER_HEIGHT + 5;
        int boxW = contentW - 55;
        keywordBox = new EditBox(font(), contentL, searchY, boxW, 18, Component.literal(""));
        keywordBox.setHint(Component.literal("搜索歌曲..."));
        keywordBox.setMaxLength(100);
        ctx.addWidget(keywordBox);

        searchBtn = ctx.addWidget(Button.builder(Component.literal("🔍"), b -> doSearchAsync())
                .bounds(contentL + boxW + 5, searchY, 45, 18).build());

        // 搜索列表区域
        listX = contentL;
        listY = searchY + 28;
        listW = contentW;
        listH = height() - HEADER_HEIGHT - FOOTER_HEIGHT - 50;

        // 进度条
        progressBarW = contentW - 80;
        progressBarH = 4;
        progressBarX = cx - progressBarW / 2;
        int footerTop = height() - FOOTER_HEIGHT;
        progressBarY = footerTop + 12;

        // 底部按钮
        int btnY = footerTop + 35;
        int btnH = 20;
        int btnW = 24;
        int gap = 6;
        int sliderW = 40;
        int totalW = btnW * 6 + gap * 6 + sliderW;
        int startX = cx - totalW / 2;

        playlistBtn = ctx.addWidget(Button.builder(Component.literal("♫"),
                b -> ctx.switchTab(MusicScreenContext.TabType.PLAYLIST))
                .bounds(startX, btnY, btnW, btnH).build());

        shuffleBtn = ctx.addWidget(Button.builder(Component.literal("🔀"),
                b -> Playlist.toggleShuffle())
                .bounds(startX + btnW + gap, btnY, btnW, btnH).build());

        prevBtn = ctx.addWidget(Button.builder(Component.literal("⏮"),
                b -> Playlist.previous())
                .bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH).build());

        playPauseBtn = ctx.addWidget(Button.builder(Component.literal("▶"),
                b -> togglePlayPause())
                .bounds(startX + (btnW + gap) * 3, btnY, btnW, btnH).build());

        nextBtn = ctx.addWidget(Button.builder(Component.literal("⏭"),
                b -> Playlist.next())
                .bounds(startX + (btnW + gap) * 4, btnY, btnW, btnH).build());

        repeatBtn = ctx.addWidget(Button.builder(Component.literal("🔁"),
                b -> Playlist.toggleRepeatMode())
                .bounds(startX + (btnW + gap) * 5, btnY, btnW, btnH).build());

        // 音量滑块
        volumeSliderW = sliderW;
        volumeSliderH = 4;
        volumeSliderX = startX + (btnW + gap) * 6;
        volumeSliderY = btnY + 8;

        // 检查 API
        checkApiHealth();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;
        int contentW = Math.min(360, width() - 30);
        int contentL = cx - contentW / 2;

        // 标题
        g.drawCenteredString(font(), "♪ 网易云音乐", cx, 12, COLOR_TEXT);

        // API 状态警告
        if (!apiAvailable && apiError != null) {
            int warnY = HEADER_HEIGHT + 35;
            g.fill(contentL, warnY, contentL + contentW, warnY + 35, 0xCC442222);
            g.drawCenteredString(font(), "⚠ API 服务器不可用", cx, warnY + 5, 0xFFFF6666);
            String errMsg = apiError.length() > 35 ? apiError.substring(0, 35) + "..." : apiError;
            g.drawCenteredString(font(), errMsg, cx, warnY + 18, COLOR_TEXT_DIM);
        }

        // 底部面板
        int footerTop = height() - FOOTER_HEIGHT;
        g.fill(0, footerTop, width(), height(), COLOR_PANEL);

        // 歌曲信息
        drawSongInfo(g, cx, footerTop);

        // 进度条
        drawProgressBar(g, mouseX, mouseY);

        // 音量条
        drawVolumeSlider(g);

        // 模式指示器
        drawModeIndicators(g);

        // 更新按钮
        updateButtons();
    }

    private void drawSongInfo(GuiGraphics g, int cx, int footerTop) {
        var state = MusicController.getState();
        boolean isPlaying = (state == StreamingMp3Player.State.PLAYING ||
                state == StreamingMp3Player.State.BUFFERING ||
                state == StreamingMp3Player.State.PAUSED);

        var currentSong = Playlist.getCurrentSong();
        if (!isPlaying || currentSong == null) {
            return;
        }

        String songInfo = truncate(currentSong.name() + " - " + currentSong.artist(), 40);
        g.drawCenteredString(font(), songInfo, cx, footerTop + 3, COLOR_TEXT_DIM);
    }

    private void drawProgressBar(GuiGraphics g, int mouseX, int mouseY) {
        var state = MusicController.getState();
        long durationMs = MusicController.getDurationMs();

        boolean hasPlayback = (state == StreamingMp3Player.State.PLAYING ||
                state == StreamingMp3Player.State.BUFFERING ||
                state == StreamingMp3Player.State.PAUSED) && durationMs > 0;

        g.fill(progressBarX, progressBarY, progressBarX + progressBarW, progressBarY + progressBarH, COLOR_SLIDER_BG);

        if (!hasPlayback) {
            g.drawString(font(), "--:--", progressBarX - 28, progressBarY - 2, COLOR_TEXT_DIM);
            g.drawString(font(), "--:--", progressBarX + progressBarW + 3, progressBarY - 2, COLOR_TEXT_DIM);
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

        String played = MusicController.formatTime(playedMs);
        String total = MusicController.formatTime(durationMs);
        g.drawString(font(), played, progressBarX - 28, progressBarY - 2, COLOR_TEXT_DIM);
        g.drawString(font(), total, progressBarX + progressBarW + 3, progressBarY - 2, COLOR_TEXT_DIM);

        int filledW = (int) (progressBarW * Math.min(1f, Math.max(0f, progress)));
        g.fill(progressBarX, progressBarY, progressBarX + filledW, progressBarY + progressBarH, COLOR_ACCENT);

        int handleX = progressBarX + filledW - 3;
        int handleY = progressBarY - 2;
        g.fill(handleX, handleY, handleX + 6, handleY + progressBarH + 4, COLOR_SLIDER_HANDLE);
    }

    private void drawVolumeSlider(GuiGraphics g) {
        g.fill(volumeSliderX, volumeSliderY, volumeSliderX + volumeSliderW, volumeSliderY + volumeSliderH, COLOR_SLIDER_BG);
        float vol = MusicController.getVolume();
        int filledW = (int) (volumeSliderW * vol);
        g.fill(volumeSliderX, volumeSliderY, volumeSliderX + filledW, volumeSliderY + volumeSliderH, COLOR_ACCENT);

        int handleX = volumeSliderX + filledW - 2;
        g.fill(handleX, volumeSliderY - 2, handleX + 4, volumeSliderY + volumeSliderH + 2, COLOR_SLIDER_HANDLE);
    }

    private void drawModeIndicators(GuiGraphics g) {
        if (Playlist.isShuffle()) {
            int dotX = shuffleBtn.getX() + shuffleBtn.getWidth() / 2 - 2;
            int dotY = shuffleBtn.getY() + shuffleBtn.getHeight() + 2;
            g.fill(dotX, dotY, dotX + 4, dotY + 3, COLOR_ACCENT);
        }

        if (Playlist.getRepeatMode() != Playlist.RepeatMode.NONE) {
            int dotX = repeatBtn.getX() + repeatBtn.getWidth() / 2 - 2;
            int dotY = repeatBtn.getY() + repeatBtn.getHeight() + 2;
            int color = Playlist.getRepeatMode() == Playlist.RepeatMode.ONE ? 0xFF00FFFF : COLOR_ACCENT;
            g.fill(dotX, dotY, dotX + 4, dotY + 3, color);
        }
    }

    private void updateButtons() {
        var state = MusicController.getState();
        String icon = (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING) ? "⏸" : "▶";
        playPauseBtn.setMessage(Component.literal(icon));

        shuffleBtn.setMessage(Component.literal("🔀"));
        String repeatIcon = switch (Playlist.getRepeatMode()) {
            case NONE -> "🔁";
            case ALL -> "🔁";
            case ONE -> "🔂";
        };
        repeatBtn.setMessage(Component.literal(repeatIcon));
    }

    private void togglePlayPause() {
        var state = MusicController.getState();
        if (state == StreamingMp3Player.State.PLAYING || state == StreamingMp3Player.State.BUFFERING) {
            MusicController.pause();
        } else if (state == StreamingMp3Player.State.PAUSED) {
            MusicController.resume();
        } else if (Playlist.size() > 0) {
            Playlist.playAt(Playlist.getCurrentIndex() >= 0 ? Playlist.getCurrentIndex() : 0);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int footerTop = height() - FOOTER_HEIGHT;
            int cx = width() / 2;

            // 点击歌曲信息进入详情
            if (isInRect(mouseX, mouseY, cx - 150, footerTop, 300, 15) && Playlist.getCurrentSong() != null) {
                ctx.switchTab(MusicScreenContext.TabType.NOW_PLAYING);
                return true;
            }

            // 进度条
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
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (draggingProgress) {
                MusicController.seekToProgress(dragProgress);
                draggingProgress = false;
                return true;
            }
            if (draggingVolume) {
                draggingVolume = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            if (draggingProgress) {
                updateProgressFromMouse(mouseX);
                return true;
            }
            if (draggingVolume) {
                updateVolumeFromMouse(mouseX);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= listY && mouseY <= listY + listH) {
            searchScrollOffset -= (int) scrollY;
            rebuildSearchButtons();
            return true;
        }
        return false;
    }

    private void updateProgressFromMouse(double mouseX) {
        float p = (float) (mouseX - progressBarX) / progressBarW;
        dragProgress = Math.max(0f, Math.min(1f, p));
    }

    private void updateVolumeFromMouse(double mouseX) {
        float v = (float) (mouseX - volumeSliderX) / volumeSliderW;
        v = Math.max(0f, Math.min(1f, v));
        MusicController.setVolume(v);
    }

    @Override
    public List<AbstractWidget> getWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(keywordBox);
        widgets.add(searchBtn);
        widgets.add(loginBtn);
        widgets.add(userBtn);
        widgets.add(playlistBtn);
        widgets.add(prevBtn);
        widgets.add(playPauseBtn);
        widgets.add(nextBtn);
        widgets.add(shuffleBtn);
        widgets.add(repeatBtn);
        widgets.addAll(searchResultButtons);
        return widgets;
    }

    // 搜索相关方法
    private void doSearchAsync() {
        clearSearchButtons();

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            return;
        }

        String keywords = keywordBox.getValue().trim();
        if (keywords.isEmpty()) return;

        NcmApiClient client = new NcmApiClient(
                session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl() : session.baseUrl());

        int limit = ModConfig.COMMON.musicSearchLimit.get();

        CompletableFuture.supplyAsync(() -> {
            try {
                return client.search(keywords, limit, session.cookieForApi());
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, exec()).whenComplete((songs, err) -> Minecraft.getInstance().execute(() -> {
            if (err != null || songs == null || songs.isEmpty()) return;
            searchResults = new ArrayList<>(songs);
            searchScrollOffset = 0;
            rebuildSearchButtons();
        }));
    }

    private void clearSearchButtons() {
        // 先让所有按钮不可见
        for (Button btn : searchResultButtons) {
            btn.visible = false;
        }
        searchResultButtons.clear();
    }

    private void rebuildSearchButtons() {
        clearSearchButtons();
        if (searchResults.isEmpty()) return;

        int visibleCount = 5;
        searchScrollOffset = Math.max(0, Math.min(searchScrollOffset, searchResults.size() - visibleCount));

        for (int i = 0; i < visibleCount && i + searchScrollOffset < searchResults.size(); i++) {
            int idx = i + searchScrollOffset;
            var song = searchResults.get(idx);
            String label = truncate(song.name() + " - " + song.artist(), 35);

            Button btn = Button.builder(Component.literal(label), b -> {
                Playlist.add(song);
                Playlist.play(song);
            }).bounds(listX, listY + i * 22, listW, 20).build();

            searchResultButtons.add(ctx.addWidget(btn));
        }
    }

    private void checkApiHealth() {
        CompletableFuture.runAsync(() -> {
            NcmApiClient client = new NcmApiClient(baseUrl());
            String error = client.getHealthError();
            apiAvailable = (error == null);
            apiError = error;
        }, exec());
    }
}

