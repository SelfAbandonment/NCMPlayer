package org.selfabandonment.ncmplayer.ncm;

import java.util.*;

/**
 * Cookie 清洗工具
 *
 * @author SelfAbandonment
 */
public final class CookieSanitizer {

    private static final Set<String> ATTRS = Set.of(
            "max-age", "expires", "path", "domain", "secure", "httponly", "samesite", "priority"
    );

    private CookieSanitizer() {}

    public static String sanitizeForApi(String cookieRaw) {
        if (cookieRaw == null) return "";

        String[] parts = cookieRaw.split(";");
        Map<String, String> cookies = new LinkedHashMap<>();

        for (String part : parts) {
            String t = part.trim();
            if (t.isEmpty()) continue;
            int eq = t.indexOf('=');
            if (eq <= 0) continue;

            String key = t.substring(0, eq).trim();
            String val = t.substring(eq + 1).trim();

            if (key.isEmpty()) continue;
            if (ATTRS.contains(key.toLowerCase(Locale.ROOT))) continue;
            if (val.isEmpty()) continue;

            cookies.put(key, val);
        }

        StringBuilder sb = new StringBuilder();
        for (var e : cookies.entrySet()) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    public static boolean hasMusicU(String cookieForApi) {
        if (cookieForApi == null) return false;
        return cookieForApi.contains("MUSIC_U=");
    }
}

