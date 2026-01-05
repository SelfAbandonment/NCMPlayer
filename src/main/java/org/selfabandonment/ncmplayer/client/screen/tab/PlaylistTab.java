package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.client.audio.Playlist;
import org.selfabandonment.ncmplayer.client.screen.UIConstants;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 播放列表页面
 *
 * @author SelfAbandonment
 */
public class PlaylistTab extends AbstractTab {

    private Button backBtn;
    private final List<Button> playlistButtons = new ArrayList<>();
    private int playlistScrollOffset = 0;

    private int listX, listY, listW, listH;

    public PlaylistTab(MusicScreenContext ctx) {
        super(ctx);
    }

    @Override
    public void init() {
        int cx = width();
        int contentW = Math.min(360, width() - 30);
        int contentL = cx / 2 - contentW / 2;

        backBtn = ctx.addWidget(Button.builder(Component.literal("← 返回"),
                b -> ctx.switchTab(MusicScreenContext.TabType.PLAYER))
                .bounds(contentL, 10, 60, 20).build());

        listX = contentL;
        listY = 45;
        listW = contentW;
        listH = height() - 80;
    }

    @Override
    public void onActivate() {
        rebuildPlaylistButtons();
    }

    @Override
    public void onDeactivate() {
        // 切换 Tab 时隐藏所有播放列表按钮
        for (Button btn : playlistButtons) {
            btn.visible = false;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;
        g.drawCenteredString(font(), "播放列表", cx, 14, UIConstants.COLOR_TEXT);

        if (Playlist.size() == 0) {
            g.drawCenteredString(font(), "播放列表为空", cx, height() / 2, UIConstants.COLOR_TEXT_DIM);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        playlistScrollOffset -= (int) scrollY;
        rebuildPlaylistButtons();
        return true;
    }

    @Override
    public List<AbstractWidget> getWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(backBtn);
        widgets.addAll(playlistButtons);
        return widgets;
    }

    private void rebuildPlaylistButtons() {
        // 先让所有按钮不可见
        for (Button btn : playlistButtons) {
            btn.visible = false;
        }
        playlistButtons.clear();

        List<NcmApiClient.SearchSong> songs = Playlist.getSongs();
        if (songs.isEmpty()) return;

        int visibleCount = listH / 25;
        playlistScrollOffset = Math.max(0, Math.min(playlistScrollOffset, songs.size() - visibleCount));

        int currentIndex = Playlist.getCurrentIndex();

        for (int i = 0; i < visibleCount && i + playlistScrollOffset < songs.size(); i++) {
            int idx = i + playlistScrollOffset;
            var song = songs.get(idx);
            boolean isCurrent = (idx == currentIndex);

            String prefix = isCurrent ? "▶ " : "   ";
            String label = UIConstants.truncate(prefix + song.name() + " - " + song.artist(), 30);

            final int finalIdx = idx;

            // 播放按钮
            Button playBtn = Button.builder(Component.literal(label), b -> {
                Playlist.playAt(finalIdx);
            }).bounds(listX, listY + i * 25, listW - 25, 22).build();
            playlistButtons.add(ctx.addWidget(playBtn));

            // 删除按钮
            Button delBtn = Button.builder(Component.literal("×"), b -> {
                Playlist.remove(finalIdx);
                rebuildPlaylistButtons();
            }).bounds(listX + listW - 22, listY + i * 25, 20, 22).build();
            playlistButtons.add(ctx.addWidget(delBtn));
        }
    }
}

