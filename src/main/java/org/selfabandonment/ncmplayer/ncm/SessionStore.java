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

    public record Session(String baseUrl, String cookieForApi, long savedAtEpochMs) {}

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

