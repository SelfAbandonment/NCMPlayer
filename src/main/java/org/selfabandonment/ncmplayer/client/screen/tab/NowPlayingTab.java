package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.client.audio.MusicController;
import org.selfabandonment.ncmplayer.client.audio.Playlist;
import org.selfabandonment.ncmplayer.client.audio.StreamingMp3Player;
import org.selfabandonment.ncmplayer.client.lyric.LrcParser;
import org.selfabandonment.ncmplayer.client.lyric.LyricManager;

import java.util.ArrayList;
import java.util.List;

import static org.selfabandonment.ncmplayer.client.screen.UIConstants.*;

/**
 * 正在播放详情页（歌词显示）
 *
 * @author SelfAbandonment
 */
public class NowPlayingTab extends AbstractTab {

    private Button backBtn;

    // 播放控制
    private Button prevBtn;
    private Button playPauseBtn;
    private Button nextBtn;
    private Button shuffleBtn;
    private Button repeatBtn;
    private Button playlistBtn;

    // 进度条
    private int progressBarX, progressBarY, progressBarW, progressBarH;
    private int volumeSliderX, volumeSliderY, volumeSliderW, volumeSliderH;
    private boolean draggingProgress = false;
    private boolean draggingVolume = false;
    private float dragProgress = 0f;

    public NowPlayingTab(MusicScreenContext ctx) {
        super(ctx);
    }

    @Override
    public void init() {
        int cx = width() / 2;
        int contentW = Math.min(360, width() - 30);
        int contentL = cx - contentW / 2;

        backBtn = ctx.addWidget(Button.builder(Component.literal("← 返回"),
                b -> ctx.switchTab(MusicScreenContext.TabType.PLAYER))
                .bounds(contentL, 10, 60, 20).build());

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

        volumeSliderW = sliderW;
        volumeSliderH = 4;
        volumeSliderX = startX + (btnW + gap) * 6;
        volumeSliderY = btnY + 8;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;
        int contentW = Math.min(360, width() - 30);
        int contentL = cx - contentW / 2;

        // 获取当前歌曲
        var currentSong = Playlist.getCurrentSong();
        if (currentSong == null) {
            g.drawCenteredString(font(), "暂无播放", cx, height() / 2, COLOR_TEXT_DIM);
            return;
        }

        // 歌曲信息
        int topY = 40;
        String songName = truncate(currentSong.name(), 30);
        g.drawCenteredString(font(), songName, cx, topY, COLOR_TEXT);

        String artistName = truncate(currentSong.artist(), 25);
        g.drawCenteredString(font(), artistName, cx, topY + 15, COLOR_TEXT_DIM);

        // 歌词区域
        int lyricTop = topY + 45;
        int lyricBottom = height() - FOOTER_HEIGHT - 10;
        int lyricHeight = lyricBottom - lyricTop;

        g.fill(contentL, lyricTop, contentL + contentW, lyricBottom, 0x40000000);

        // 使用 scissor 裁剪，确保歌词不会超出区域
        g.enableScissor(contentL, lyricTop, contentL + contentW, lyricBottom);
        renderLyrics(g, cx, lyricTop, lyricHeight, contentW);
        g.disableScissor();

        // 底部面板
        int footerTop = height() - FOOTER_HEIGHT;
        g.fill(0, footerTop, width(), height(), COLOR_PANEL);

        // 进度条
        drawProgressBar(g, mouseX, mouseY);

        // 音量
        drawVolumeSlider(g);

        // 模式指示器
        drawModeIndicators(g);

        // 更新按钮
        updateButtons();
    }

    private void renderLyrics(GuiGraphics g, int cx, int lyricTop, int lyricHeight, int contentW) {
        var lyrics = LyricManager.getCurrentLyrics();
        long currentTime = MusicController.getPlayedMs();

        // 歌词最大宽度 = 内容宽度 - 左右各10像素边距
        int maxWidth = contentW - 20;

        if (lyrics.isEmpty()) {
            if (LyricManager.isLoading()) {
                g.drawCenteredString(font(), "歌词加载中...", cx, lyricTop + lyricHeight / 2, COLOR_TEXT_DIM);
            } else {
                g.drawCenteredString(font(), "暂无歌词", cx, lyricTop + lyricHeight / 2, COLOR_TEXT_DIM);
            }
            return;
        }

        int currentIndex = LrcParser.getCurrentLineIndex(lyrics, currentTime);
        int lineHeight = 18;
        int visibleLines = lyricHeight / lineHeight;
        int centerY = lyricTop + lyricHeight / 2;

        int startLine = Math.max(0, currentIndex - visibleLines / 2);
        int endLine = Math.min(lyrics.size(), startLine + visibleLines);

        for (int i = startLine; i < endLine; i++) {
            var line = lyrics.get(i);
            int offsetFromCenter = i - currentIndex;
            int y = centerY + offsetFromCenter * lineHeight;

            boolean isCurrent = (i == currentIndex);
            int color = isCurrent ? COLOR_ACCENT : COLOR_TEXT_DIM;

            // 使用像素宽度截断歌词
            String text = truncateByWidth(line.text(), maxWidth);
            g.drawCenteredString(font(), text, cx, y, color);
        }
    }

    /**
     * 根据像素宽度截断文本
     */
    private String truncateByWidth(String text, int maxWidth) {
        if (text == null || text.isEmpty()) return "";

        int textWidth = font().width(text);
        if (textWidth <= maxWidth) {
            return text;
        }

        // 需要截断
        String ellipsis = "...";
        int ellipsisWidth = font().width(ellipsis);
        int targetWidth = maxWidth - ellipsisWidth;

        StringBuilder sb = new StringBuilder();
        int currentWidth = 0;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int charWidth = font().width(String.valueOf(c));
            if (currentWidth + charWidth > targetWidth) {
                break;
            }
            sb.append(c);
            currentWidth += charWidth;
        }

        return sb + ellipsis;
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
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isInRect(mouseX, mouseY, progressBarX - 5, progressBarY - 8, progressBarW + 10, progressBarH + 16)) {
                if (MusicController.canSeek() && MusicController.getDurationMs() > 0) {
                    draggingProgress = true;
                    updateProgressFromMouse(mouseX);
                    return true;
                }
            }

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
        widgets.add(backBtn);
        widgets.add(playlistBtn);
        widgets.add(shuffleBtn);
        widgets.add(prevBtn);
        widgets.add(playPauseBtn);
        widgets.add(nextBtn);
        widgets.add(repeatBtn);
        return widgets;
    }
}

