package org.demo.portalteleport.ncm;

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

public final class NcmApiClient {
    private static final Gson GSON = new Gson();

    private final String baseUrl; // e.g. http://101.35.114.214:3000
    private final HttpClient http;

    public NcmApiClient(String baseUrl) {
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public String baseUrl() { return baseUrl; }

    public JsonObject getJson(String pathAndQuery) throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + pathAndQuery);
        HttpRequest req = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (PortalTeleport NeoForge Mod)")
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " " + uri + " body=" + resp.body());
        }
        return GSON.fromJson(resp.body(), JsonObject.class);
    }

    // ---------------- QR login ----------------

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

    // ---------------- song url ----------------

    public SongUrlResult songUrlV1(long id, String level, String cookieForApi) throws IOException, InterruptedException {
        long ts = System.currentTimeMillis();
        String q = "/song/url/v1?id=" + id
                + "&level=" + url(level)
                + "&cookie=" + url(cookieForApi)
                + "&timestamp=" + ts;

        JsonObject obj = getJson(q);
        var dataArr = obj.getAsJsonArray("data");
        if (dataArr == null || dataArr.size() == 0) {
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

    // ---------------- search ----------------

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

            out.add(new SearchSong(id, name, artist));
        }
        return out;
    }

    public record SearchSong(long id, String name, String artist) {}

    private static String url(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}