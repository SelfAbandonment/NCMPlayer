package org.selfabandonment.ncmplayer.ncm;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 会话存储
 *
 * @author SelfAbandonment
 */
public final class SessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * 会话信息
     */
    public record Session(String baseUrl, String cookieForApi, long savedAtEpochMs,
                          Long userId, String nickname, String avatarUrl, Integer vipType) {

        /**
         * 旧版构造器（向后兼容）
         */
        public Session(String baseUrl, String cookieForApi, long savedAtEpochMs) {
            this(baseUrl, cookieForApi, savedAtEpochMs, null, null, null, null);
        }

        /**
         * 是否有用户信息
         */
        public boolean hasUserInfo() {
            return userId != null && nickname != null && !nickname.isBlank();
        }

        /**
         * 获取显示名称
         */
        public String displayName() {
            if (nickname != null && !nickname.isBlank()) {
                return nickname;
            }
            return "用户" + (userId != null ? userId : "");
        }

        /**
         * VIP 类型文字
         */
        public String vipTypeString() {
            if (vipType == null) return "";
            return switch (vipType) {
                case 0 -> "";
                case 10 -> " [黑胶VIP]";
                case 11 -> " [黑胶SVIP]";
                default -> " [VIP]";
            };
        }
    }

    private SessionStore() {}

    private static Path filePath() {
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config")
                .resolve("ncmplayer_session.json");
    }

    public static Path debugPath() {
        return filePath();
    }

    public static void save(Session session) throws IOException {
        Files.createDirectories(filePath().getParent());
        Files.writeString(filePath(), GSON.toJson(session), StandardCharsets.UTF_8);
    }

    public static Session loadOrNull() {
        try {
            Path p = filePath();
            if (!Files.exists(p)) return null;
            String json = Files.readString(p, StandardCharsets.UTF_8);
            return GSON.fromJson(json, Session.class);
        } catch (Exception e) {
            return null;
        }
    }
}

