package org.selfabandonment.ncmplayer.client.screen.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.selfabandonment.ncmplayer.ncm.CookieSanitizer;
import org.selfabandonment.ncmplayer.ncm.NcmApiClient;
import org.selfabandonment.ncmplayer.ncm.SessionStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.selfabandonment.ncmplayer.client.screen.UIConstants.*;

/**
 * 用户信息页面
 *
 * @author SelfAbandonment
 */
public class UserInfoTab extends AbstractTab {

    private Button backBtn;
    private Button logoutBtn;

    private volatile NcmApiClient.UserDetail userDetail;
    private volatile NcmApiClient.UserSubcount userSubcount;
    private volatile boolean loadingUserInfo = false;

    public UserInfoTab(MusicScreenContext ctx) {
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

        logoutBtn = ctx.addWidget(Button.builder(Component.literal("退出登录"),
                        b -> logout())
                .bounds(contentL + contentW - 70, 10, 70, 20).build());
    }

    @Override
    public void onActivate() {
        loadUserInfoAsync();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int cx = width() / 2;
        int contentW = Math.min(320, width() - 40);
        int contentL = cx - contentW / 2;

        g.drawCenteredString(font(), "用户信息", cx, 14, COLOR_TEXT);

        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            g.drawCenteredString(font(), "未登录", cx, height() / 2 - 20, COLOR_TEXT_DIM);
            g.drawCenteredString(font(), "请先扫码登录", cx, height() / 2, COLOR_TEXT_DIM);
            return;
        }

        int y = 45;
        int lineH = 18;

        if (userDetail != null) {
            String nickname = userDetail.nickname();
            String vip = userDetail.vipTypeString();
            int vipColor = userDetail.vipType() > 0 ? 0xFFD700 : COLOR_TEXT_DIM;

            g.drawCenteredString(font(), nickname, cx, y, COLOR_TEXT);
            y += lineH;

            if (userDetail.vipType() > 0) {
                g.drawCenteredString(font(), vip, cx, y, vipColor);
                y += lineH;
            }

            y += 5;

            g.drawString(font(), "等级: Lv." + userDetail.level(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;

            g.drawString(font(), "累计听歌: " + userDetail.listenSongs() + " 首", contentL, y, COLOR_TEXT_DIM);
            y += lineH;

            if (userDetail.signature() != null && !userDetail.signature().isBlank()) {
                String sig = truncate(userDetail.signature(), 30);
                g.drawString(font(), "签名: " + sig, contentL, y, COLOR_TEXT_DIM);
                y += lineH;
            }
        } else if (loadingUserInfo) {
            g.drawCenteredString(font(), "加载中...", cx, y + 30, COLOR_TEXT_DIM);
        }

        if (userSubcount != null) {
            y += 10;
            g.fill(contentL, y, contentL + contentW, y + 1, COLOR_ACCENT_DIM);
            y += 10;

            g.drawString(font(), "创建歌单: " + userSubcount.playlistCount(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;
            g.drawString(font(), "收藏歌单: " + userSubcount.subPlaylistCount(), contentL, y, COLOR_TEXT_DIM);
            y += lineH;
            g.drawString(font(), "收藏歌手: " + userSubcount.artistCount(), contentL, y, COLOR_TEXT_DIM);
        }
    }

    @Override
    public List<AbstractWidget> getWidgets() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(backBtn);
        widgets.add(logoutBtn);
        return widgets;
    }

    private void loadUserInfoAsync() {
        SessionStore.Session session = SessionStore.loadOrNull();
        if (session == null || !CookieSanitizer.hasMusicU(session.cookieForApi())) {
            return;
        }
        if (loadingUserInfo) return;
        loadingUserInfo = true;

        String apiUrl = session.baseUrl() == null || session.baseUrl().isBlank() ? baseUrl() : session.baseUrl();
        NcmApiClient client = new NcmApiClient(apiUrl);
        String cookie = session.cookieForApi();

        CompletableFuture.runAsync(() -> {
            try {
                var account = client.getUserAccount(cookie);
                if (account != null && account.userId() > 0) {
                    userDetail = client.getUserDetail(account.userId(), cookie);
                    userSubcount = client.getUserSubcount(cookie);

                    SessionStore.Session newSession = new SessionStore.Session(
                            session.baseUrl(), session.cookieForApi(), session.savedAtEpochMs(),
                            account.userId(), account.nickname(), account.avatarUrl(), account.vipType()
                    );
                    SessionStore.save(newSession);
                    Minecraft.getInstance().execute(ctx::updateLoginStatus);
                }
            } catch (Exception ignored) {
            } finally {
                loadingUserInfo = false;
            }
        }, exec());
    }

    private void logout() {
        SessionStore.clear();
        userDetail = null;
        userSubcount = null;
        ctx.updateLoginStatus();
        ctx.switchTab(MusicScreenContext.TabType.PLAYER);
    }
}

