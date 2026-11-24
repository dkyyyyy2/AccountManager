package accountmanager.account;

import accountmanager.auth.AlteningUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class PastedCredentials {
    public final String accessToken;
    public final String refreshToken;
    public final String microsoftAccessToken;
    public final String username;
    public final String uuid;
    public String alteningToken = "";

    private PastedCredentials(String accessToken, String refreshToken, String username, String uuid) {
        this(accessToken, refreshToken, username, uuid, "");
    }

    private PastedCredentials(String accessToken, String refreshToken, String username, String uuid,
                              String microsoftAccessToken) {
        this.microsoftAccessToken = microsoftAccessToken == null ? "" : microsoftAccessToken.trim();
        this.accessToken = accessToken == null ? "" : accessToken.trim();
        this.refreshToken = refreshToken == null ? "" : refreshToken.trim();
        this.username = username == null ? "" : username.trim();
        this.uuid = uuid == null ? "" : uuid.trim();
    }

    public static PastedCredentials parse(String pasted) {
        String value = normalize(pasted);
        if (value.isEmpty()) {
            return new PastedCredentials("", "", "", "");
        }

        if (value.startsWith("{")) {
            try {
                JsonObject json = new JsonParser().parse(value).getAsJsonObject();
                return new PastedCredentials(
                        string(json, "accessToken", "access_token", "mcToken", "token"),
                        string(json, "refreshToken", "refresh_token"),
                        string(json, "name", "username", "displayName"),
                        string(json, "uuid", "id", "profileId"));
            } catch (Throwable ignored) {
                return new PastedCredentials("", "", "", "");
            }
        }

        if (AlteningUtil.isAltToken(value)) {
            PastedCredentials altening = new PastedCredentials("", "", "", "");
            altening.alteningToken = value.trim();
            return altening;
        }
        if (isAccessToken(value)) {
            return new PastedCredentials(value, "", "", "");
        }
        if (isMicrosoftAccessToken(value)) {
            return new PastedCredentials("", "", "", "", value);
        }
        if (value.contains(" ") || value.length() < 20) {
            return new PastedCredentials("", "", "", "");
        }
        return new PastedCredentials("", value, "", "");
    }

    public static String normalize(String pasted) {
        if (pasted == null) {
            return "";
        }
        String value = pasted;
        value = stripWrapping(value, "```", "```");
        value = stripWrapping(value, "`", "`");
        value = stripWrapping(value, "\"", "\"");
        value = stripWrapping(value, "'", "'");

        int start = 0;
        int end = value.length();
        while (start < end && isTrimmable(value.charAt(start))) {
            start++;
        }
        while (end > start && isTrimmable(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static String stripWrapping(String value, String open, String close) {
        String trimmed = value.trim();
        if (trimmed.length() >= open.length() + close.length()
                && trimmed.startsWith(open) && trimmed.endsWith(close)) {
            return trimmed.substring(open.length(), trimmed.length() - close.length());
        }
        return value;
    }

    private static boolean isTrimmable(char c) {
        if (c <= ' ' || Character.isWhitespace(c) || Character.isSpaceChar(c)) {
            return true;
        }
        return c == '​' || c == '‌' || c == '‍' || c == '﻿'
                || Character.getType(c) == Character.FORMAT;
    }

    public static boolean isAccessToken(String value) {
        return value != null && value.startsWith("ey") && value.split("\\.", -1).length == 3;
    }

    public static boolean isMicrosoftAccessToken(String value) {
        return value != null && value.startsWith("Ew") && value.indexOf('.') < 0 && value.length() > 100;
    }

    public boolean hasMicrosoftAccessToken() {
        return !microsoftAccessToken.isEmpty();
    }

    public boolean hasRefreshToken() {
        return !refreshToken.isEmpty();
    }

    public boolean hasAccessToken() {
        return !accessToken.isEmpty();
    }

    public boolean hasAlteningToken() {
        return !alteningToken.isEmpty();
    }

    public boolean hasAnything() {
        return hasAlteningToken() || hasRefreshToken() || hasAccessToken() || hasMicrosoftAccessToken();
    }

    private static String string(JsonObject json, String... keys) {
        for (String key : keys) {
            if (json.has(key) && json.get(key).isJsonPrimitive()) {
                String value = json.get(key).getAsString();
                if (!value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return "";
    }
}
