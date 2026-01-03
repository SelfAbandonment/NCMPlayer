package org.selfabandonment.ncmplayer.ncm;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 网易云音乐 API 客户端
 *
 * @author SelfAbandonment
 */
public final class NcmApiClient {
    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final HttpClient http;

    public NcmApiClient(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String baseUrl() { return baseUrl; }

    /**
     * 检查 API 服务器是否可用
     * @return true 如果服务器可用
     */
    public boolean checkHealth() {
        try {
            URI uri = URI.create(baseUrl);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (NCM Player NeoForge Mod)")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() >= 200 && resp.statusCode() < 400;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 API 服务器不可用的原因
     * @return 错误信息，如果服务器可用则返回 null
     */
    public String getHealthError() {
        try {
            URI uri = URI.create(baseUrl);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (NCM Player NeoForge Mod)")
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
                return null;
            }
            return "HTTP " + resp.statusCode();
        } catch (java.net.ConnectException e) {
            return "无法连接到服务器";
        } catch (java.net.UnknownHostException e) {
            return "服务器地址无效";
        } catch (java.net.SocketTimeoutException e) {
            return "连接超时";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + ": " + e.getMessage();
        }
    }

    public JsonObject getJson(String pathAndQuery) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + pathAndQuery);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (NCM Player NeoForge Mod)")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " " + uri + " body=" + resp.body());
        }
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    // ==================== 扫码登录 ====================

    public String qrKey() throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        JsonObject obj = getJson("/login/qr/key?timestamp=" + ts);
        return obj.getAsJsonObject("data").get("unikey").getAsString();
    }

    public JsonObject qrCreate(String unikey, boolean includeImg) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String qrimg = includeImg ? "1" : "0";
        return getJson("/login/qr/create?key=" + url(unikey) + "&qrimg=" + qrimg + "&timestamp=" + ts);
    }

    public JsonObject qrCheck(String unikey) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        return getJson("/login/qr/check?key=" + url(unikey) + "&timestamp=" + ts);
    }

    // ==================== 用户信息 ====================

    /**
     * 获取用户账号信息
     */
    public UserAccount getUserAccount(String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/user/account?cookie=" + url(cookieForApi) + "&timestamp=" + ts;

        JsonObject obj = getJson(q);
        if (!obj.has("profile") || obj.get("profile").isJsonNull()) {
            return null;
        }

        JsonObject profile = obj.getAsJsonObject("profile");
        long userId = profile.has("userId") ? profile.get("userId").getAsLong() : 0;
        String nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
        String avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
        int vipType = profile.has("vipType") ? profile.get("vipType").getAsInt() : 0;

        return new UserAccount(userId, nickname, avatarUrl, vipType);
    }

    /**
     * 获取用户详情
     */
    public UserDetail getUserDetail(long userId, String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/user/detail?uid=" + userId + "&cookie=" + url(cookieForApi) + "&timestamp=" + ts;

        JsonObject obj = getJson(q);

        int level = obj.has("level") ? obj.get("level").getAsInt() : 0;
        int listenSongs = obj.has("listenSongs") ? obj.get("listenSongs").getAsInt() : 0;

        String signature = "";
        String nickname = "";
        String avatarUrl = "";
        int vipType = 0;
        long createTime = 0;

        if (obj.has("profile") && !obj.get("profile").isJsonNull()) {
            JsonObject profile = obj.getAsJsonObject("profile");
            nickname = profile.has("nickname") ? profile.get("nickname").getAsString() : "";
            avatarUrl = profile.has("avatarUrl") ? profile.get("avatarUrl").getAsString() : "";
            signature = profile.has("signature") && !profile.get("signature").isJsonNull()
                    ? profile.get("signature").getAsString() : "";
            vipType = profile.has("vipType") ? profile.get("vipType").getAsInt() : 0;
            createTime = profile.has("createTime") ? profile.get("createTime").getAsLong() : 0;
        }

        return new UserDetail(userId, nickname, avatarUrl, signature, level, listenSongs, vipType, createTime);
    }

    /**
     * 获取用户歌单、收藏等数量
     */
    public UserSubcount getUserSubcount(String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/user/subcount?cookie=" + url(cookieForApi) + "&timestamp=" + ts;

        JsonObject obj = getJson(q);

        int playlistCount = obj.has("createdPlaylistCount") ? obj.get("createdPlaylistCount").getAsInt() : 0;
        int subPlaylistCount = obj.has("subPlaylistCount") ? obj.get("subPlaylistCount").getAsInt() : 0;
        int artistCount = obj.has("artistCount") ? obj.get("artistCount").getAsInt() : 0;
        int mvCount = obj.has("mvCount") ? obj.get("mvCount").getAsInt() : 0;
        int djRadioCount = obj.has("djRadioCount") ? obj.get("djRadioCount").getAsInt() : 0;

        return new UserSubcount(playlistCount, subPlaylistCount, artistCount, mvCount, djRadioCount);
    }

    /**
     * 用户账号信息
     */
    public record UserAccount(long userId, String nickname, String avatarUrl, int vipType) {
        public String vipTypeString() {
            return switch (vipType) {
                case 0 -> "普通用户";
                case 10 -> "黑胶VIP";
                case 11 -> "黑胶SVIP";
                default -> "VIP";
            };
        }
    }

    /**
     * 用户详情
     */
    public record UserDetail(long userId, String nickname, String avatarUrl, String signature,
                             int level, int listenSongs, int vipType, long createTime) {
        public String vipTypeString() {
            return switch (vipType) {
                case 0 -> "普通用户";
                case 10 -> "黑胶VIP";
                case 11 -> "黑胶SVIP";
                default -> "VIP";
            };
        }
    }

    /**
     * 用户统计数量
     */
    public record UserSubcount(int playlistCount, int subPlaylistCount, int artistCount,
                               int mvCount, int djRadioCount) {}

    // ==================== 歌曲 URL ====================

    public SongUrlResult songUrlV1(long id, String level, String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/song/url/v1?id=" + id
                + "&level=" + url(level)
                + "&cookie=" + url(cookieForApi)
                + "&timestamp=" + ts;

        JsonObject obj = getJson(q);
        var dataArr = obj.getAsJsonArray("data");
        if (dataArr == null || dataArr.isEmpty()) {
            return new SongUrlResult(id, level, null, 0, 0, 0, 0, "no data");
        }
        var item = dataArr.get(0).getAsJsonObject();

        String u = item.has("url") && !item.get("url").isJsonNull() ? item.get("url").getAsString() : null;
        int code = item.has("code") ? item.get("code").getAsInt() : -1;
        int expi = item.has("expi") ? item.get("expi").getAsInt() : 0;
        int br = item.has("br") ? item.get("br").getAsInt() : 0;
        long size = item.has("size") ? item.get("size").getAsLong() : 0;
        String type = item.has("type") && !item.get("type").isJsonNull() ? item.get("type").getAsString() : null;

        return new SongUrlResult(id, level, u, code, expi, br, size, type);
    }

    public record SongUrlResult(long songId, String requestedLevel, String url, int code, int expiSeconds, int br, long size, String type) {
        public boolean ok() { return url != null && !url.isBlank() && code == 200; }
        public long expiresAtEpochMs(long nowEpochMs) { return nowEpochMs + (Math.max(1, expiSeconds) * 1000L); }
    }

    // ==================== 搜索 ====================

    public List<SearchSong> search(String keywords, int limit, String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/search?keywords=" + url(keywords)
                + "&limit=" + limit
                + "&cookie=" + url(cookieForApi)
                + "&timestamp=" + ts;

        JsonObject obj = getJson(q);
        JsonObject result = obj.has("result") && obj.get("result").isJsonObject() ? obj.getAsJsonObject("result") : null;
        if (result == null) return List.of();

        JsonArray songs = result.has("songs") && result.get("songs").isJsonArray() ? result.getAsJsonArray("songs") : null;
        if (songs == null) return List.of();

        List<SearchSong> out = new ArrayList<>();
        for (int i = 0; i < songs.size(); i++) {
            JsonObject s = songs.get(i).getAsJsonObject();
            long id = s.get("id").getAsLong();
            String name = s.has("name") ? s.get("name").getAsString() : ("#" + id);

            // 获取歌曲时长（毫秒）
            long duration = 0;
            if (s.has("duration")) {
                duration = s.get("duration").getAsLong();
            } else if (s.has("dt")) {
                duration = s.get("dt").getAsLong();
            }

            String artist = "";
            if (s.has("artists") && s.get("artists").isJsonArray()) {
                JsonArray ar = s.getAsJsonArray("artists");
                if (!ar.isEmpty() && ar.get(0).isJsonObject()) {
                    JsonObject a0 = ar.get(0).getAsJsonObject();
                    if (a0.has("name")) artist = a0.get("name").getAsString();
                }
            } else if (s.has("ar") && s.get("ar").isJsonArray()) {
                JsonArray ar = s.getAsJsonArray("ar");
                if (!ar.isEmpty() && ar.get(0).isJsonObject()) {
                    JsonObject a0 = ar.get(0).getAsJsonObject();
                    if (a0.has("name")) artist = a0.get("name").getAsString();
                }
            }

            out.add(new SearchSong(id, name, artist, duration));
        }
        return out;
    }

    /**
     * 获取歌曲详情（包含时长）
     */
    public SongDetail getSongDetail(long songId, String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/song/detail?ids=" + songId
                + "&cookie=" + url(cookieForApi)
                + "&timestamp=" + ts;

        JsonObject obj = getJson(q);
        JsonArray songs = obj.has("songs") && obj.get("songs").isJsonArray() ? obj.getAsJsonArray("songs") : null;
        if (songs == null || songs.isEmpty()) {
            return new SongDetail(songId, "", "", 0);
        }

        JsonObject s = songs.get(0).getAsJsonObject();
        String name = s.has("name") ? s.get("name").getAsString() : "";
        long duration = s.has("dt") ? s.get("dt").getAsLong() : 0;

        String artist = "";
        if (s.has("ar") && s.get("ar").isJsonArray()) {
            JsonArray ar = s.getAsJsonArray("ar");
            if (!ar.isEmpty() && ar.get(0).isJsonObject()) {
                JsonObject a0 = ar.get(0).getAsJsonObject();
                if (a0.has("name")) artist = a0.get("name").getAsString();
            }
        }

        return new SongDetail(songId, name, artist, duration);
    }

    /**
     * 歌曲搜索结果（包含时长）
     */
    public record SearchSong(long id, String name, String artist, long durationMs) {}

    /**
     * 歌曲详情
     */
    public record SongDetail(long id, String name, String artist, long durationMs) {}

    private static String url(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}

