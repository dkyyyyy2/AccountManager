package accountmanager.auth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ProfileApi {
    private static final String PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";
    private static final String ACTIVE_CAPE_URL = PROFILE_URL + "/capes/active";
    private static final String CHANGE_SKIN_URL = PROFILE_URL + "/skins";
    private static final String NAME_URL = PROFILE_URL + "/name/";
    private static final String NAME_CHANGE_INFO_URL = PROFILE_URL + "/namechange";

    private ProfileApi() {
    }

    public static void getCapesAsync(String token, CapeCallback callback) {
        async("AccountManager-Capes", () -> {
            ProfileResult result = getProfileInfoWithRateLimit(token);
            if (result.rateLimited) {
                callback.onResult(CapeResult.rateLimited(result.retryAfterSeconds));
            } else if (result.success) {
                callback.onResult(CapeResult.success(result.capes != null ? result.capes : new ArrayList<>()));
            } else {
                callback.onResult(CapeResult.error(result.errorMessage));
            }
        });
    }

    public static void setCapeAsync(String token, String capeId, SimpleCallback callback) {
        async("AccountManager-Set-Cape", () -> {
            try {
                int status = send(ACTIVE_CAPE_URL, "PUT", token, "{\"capeId\":\"" + escapeJson(capeId) + "\"}").status;
                if (ok(status)) {
                    callback.onResult(true, "Cape changed.");
                } else if (status == 400) {
                    callback.onResult(false, "Invalid cape id.");
                } else {
                    callback.onResult(false, commonError(status));
                }
            } catch (Exception e) {
                callback.onResult(false, "Error: " + safeMessage(e));
            }
        });
    }

    public static void removeCapeAsync(String token, SimpleCallback callback) {
        async("AccountManager-Remove-Cape", () -> {
            try {
                int status = send(ACTIVE_CAPE_URL, "DELETE", token, null).status;
                callback.onResult(ok(status), ok(status) ? "Cape removed." : commonError(status));
            } catch (Exception e) {
                callback.onResult(false, "Error: " + safeMessage(e));
            }
        });
    }

    public static void changeSkinAsync(String url, String token, String variant, SimpleCallback callback) {
        async("AccountManager-Skin", () -> {
            try {
                String normalized = normalizeSkinUrl(url);
                String safeVariant = variant == null || variant.trim().isEmpty() ? "classic" : variant.trim().toLowerCase();
                String body = "{\"variant\":\"" + escapeJson(safeVariant) + "\",\"url\":\"" + escapeJson(normalized) + "\"}";
                Http result = send(CHANGE_SKIN_URL, "POST", token, body);
                int status = result.status;
                if (ok(status)) {
                    callback.onResult(true, "Skin changed to " + ("slim".equals(safeVariant) ? "Slim" : "Classic") + ".");
                } else if (status == 403) {
                    callback.onResult(false, "This account is not allowed to change its skin.");
                } else if (status == 400) {
                    callback.onResult(false, serviceMessage(result.body, "The skin URL was rejected. Use a direct public PNG URL."));
                } else if (status == 415) {
                    callback.onResult(false, "The URL did not return a supported PNG skin.");
                } else if (status == 401 || status == 404 || status == 429) {
                    callback.onResult(false, commonError(status));
                } else {
                    callback.onResult(false, serviceMessage(result.body, "Skin change failed: HTTP " + status));
                }
            } catch (IllegalArgumentException e) {
                callback.onResult(false, safeMessage(e));
            } catch (Exception e) {
                callback.onResult(false, "Error: " + safeMessage(e));
            }
        });
    }

    public static void changeNameAsync(String newName, String token, SimpleCallback callback) {
        async("AccountManager-Name", () -> {
            try {
                Http result = send(NAME_URL + URLEncoder.encode(newName, "UTF-8"), "PUT", token, null);
                int status = result.status;
                if (ok(status)) {
                    callback.onResult(true, "Name changed to " + newName + ".");
                } else if (status == 400) {
                    callback.onResult(false, "That name is not valid.");
                } else if (status == 403) {
                    callback.onResult(false, serviceMessage(result.body, "Name is taken or was changed too recently."));
                } else {
                    callback.onResult(false, commonError(status));
                }
            } catch (Exception e) {
                callback.onResult(false, "Error: " + safeMessage(e));
            }
        });
    }

    public static void checkNameAsync(String name, String token, java.util.function.Consumer<String> callback) {
        async("AccountManager-Name-Check", () -> {
            try {
                Http result = send(NAME_URL + URLEncoder.encode(name, "UTF-8") + "/available", "GET", token, null);
                JsonObject json = ok(result.status) ? parseObject(result.body) : null;
                callback.accept(json != null && json.has("status") ? json.get("status").getAsString() : null);
            } catch (Exception e) {
                callback.accept(null);
            }
        });
    }

    public static void getNameChangeInfoAsync(String token, java.util.function.Consumer<NameChangeInfo> callback) {
        async("AccountManager-Name-Info", () -> {
            try {
                Http result = send(NAME_CHANGE_INFO_URL, "GET", token, null);
                JsonObject json = ok(result.status) ? parseObject(result.body) : null;
                if (json == null) {
                    callback.accept(null);
                    return;
                }
                boolean allowed = json.has("nameChangeAllowed") && json.get("nameChangeAllowed").getAsBoolean();
                Instant changedAt = null;
                if (json.has("changedAt") && !json.get("changedAt").isJsonNull()) {
                    try {
                        changedAt = Instant.parse(json.get("changedAt").getAsString());
                    } catch (Exception ignored) {
                    }
                }
                callback.accept(new NameChangeInfo(allowed, changedAt));
            } catch (Exception e) {
                callback.accept(null);
            }
        });
    }

    public static ProfileResult getProfileInfoWithRateLimit(String token) {
        if (token == null || token.trim().isEmpty()) {
            return ProfileResult.error("Token is empty");
        }

        HttpURLConnection connection = null;
        try {
            connection = open(PROFILE_URL, "GET", token, false);
            int status = connection.getResponseCode();
            if (status == 429) {
                return ProfileResult.rateLimited(readRetryAfter(connection));
            }
            if (status != 200) {
                return ProfileResult.error("HTTP " + status);
            }

            JsonObject json = new JsonParser().parse(readResponse(connection)).getAsJsonObject();
            return ProfileResult.success(getString(json, "name"), formatUuid(getString(json, "id")), parseCapes(json));
        } catch (Exception e) {
            return ProfileResult.error(safeMessage(e));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String normalizeSkinUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Please enter a skin URL.");
        }

        int markdownStart = normalized.indexOf("](");
        if (normalized.startsWith("[") && markdownStart > 0 && normalized.endsWith(")")) {
            normalized = normalized.substring(markdownStart + 2, normalized.length() - 1).trim();
        }
        if ((normalized.startsWith("<") && normalized.endsWith(">"))
                || (normalized.startsWith("\"") && normalized.endsWith("\""))
                || (normalized.startsWith("'") && normalized.endsWith("'"))) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        String encodedHttp = "http%3a%2f%2f";
        String encodedHttps = "https%3a%2f%2f";
        if (normalized.regionMatches(true, 0, encodedHttp, 0, encodedHttp.length())
                || normalized.regionMatches(true, 0, encodedHttps, 0, encodedHttps.length())) {
            try {
                normalized = URLDecoder.decode(normalized, "UTF-8");
            } catch (Exception ignored) {
            }
        }

        normalized = normalized.replace("&amp;", "&").replace(" ", "%20");
        if (normalized.startsWith("//")) {
            normalized = "https:" + normalized;
        } else if (!normalized.matches("(?i)^https?://.*")) {
            normalized = "https://" + normalized;
        }

        try {
            URI uri = new URI(normalized);
            if (uri.getHost() == null || uri.getHost().trim().isEmpty()) {
                throw new IllegalArgumentException("Skin URL is missing a valid host.");
            }
            if (uri.getUserInfo() != null) {
                throw new IllegalArgumentException("Skin URLs containing login credentials are not supported.");
            }
            if (uri.getRawFragment() != null) {
                normalized = normalized.substring(0, normalized.indexOf('#'));
            }
            return new URI(normalized).toASCIIString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Skin URL is malformed.");
        }
    }

    private static void async(String name, Runnable task) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean ok(int status) {
        return status == 200 || status == 204;
    }

    private static String commonError(int status) {
        switch (status) {
            case 401:
                return "Session token expired or is invalid.";
            case 404:
                return "No Minecraft profile for this session.";
            case 429:
                return "Rate limited. Try again later.";
            default:
                return "Error: HTTP " + status;
        }
    }

    private static Http send(String url, String method, String token, String body) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = open(url, method, token, body != null);
            if (body != null) {
                try (OutputStream out = connection.getOutputStream()) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }
            int status = connection.getResponseCode();
            String response = "";
            try {
                response = readResponse(connection);
            } catch (IOException ignored) {
            }
            return new Http(status, response);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection open(String url, String method, String token, boolean jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        MicrosoftAuth.applyTrust(connection);
        connection.setRequestMethod(method);
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(6000);
        connection.setUseCaches(false);
        if (token != null && !token.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + token.trim());
        }
        if (jsonBody) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }
        connection.setRequestProperty("Accept", "application/json");
        return connection;
    }

    private static String readResponse(HttpURLConnection connection) throws IOException {
        InputStream stream = connection.getResponseCode() >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        }
    }

    private static int readRetryAfter(HttpURLConnection connection) {
        try {
            return Integer.parseInt(connection.getHeaderField("Retry-After").trim());
        } catch (Exception ignored) {
            return 60;
        }
    }

    private static List<Cape> parseCapes(JsonObject json) {
        List<Cape> capes = new ArrayList<>();
        if (!json.has("capes") || !json.get("capes").isJsonArray()) {
            return capes;
        }
        JsonArray array = json.getAsJsonArray("capes");
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                JsonObject cape = element.getAsJsonObject();
                capes.add(new Cape(getString(cape, "id"), getString(cape, "alias"), getString(cape, "url"),
                        "ACTIVE".equalsIgnoreCase(getString(cape, "state"))));
            }
        }
        return capes;
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement parsed = new JsonParser().parse(body);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String getString(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static String formatUuid(String raw) {
        String compact = raw == null ? "" : raw.replace("-", "");
        if (compact.length() != 32) {
            return raw;
        }
        return compact.substring(0, 8) + '-' + compact.substring(8, 12) + '-' + compact.substring(12, 16)
                + '-' + compact.substring(16, 20) + '-' + compact.substring(20);
    }

    private static String serviceMessage(String body, String fallback) {
        JsonObject json = parseObject(body == null ? "" : body);
        if (json != null) {
            for (String key : new String[]{"errorMessage", "message", "error"}) {
                if (json.has(key) && json.get(key).isJsonPrimitive()) {
                    String message = json.get(key).getAsString().replaceAll("\\s+", " ").trim();
                    if (!message.isEmpty()) {
                        return message.length() <= 180 ? message : message.substring(0, 177) + "...";
                    }
                }
            }
        }
        return fallback;
    }

    private static String escapeJson(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        return message == null || message.trim().isEmpty() ? "Unknown error" : message.trim();
    }

    private static final class Http {
        final int status;
        final String body;

        Http(int status, String body) {
            this.status = status;
            this.body = body == null ? "" : body;
        }
    }

    public interface SimpleCallback {
        void onResult(boolean success, String message);
    }

    public interface CapeCallback {
        void onResult(CapeResult result);
    }

    public static final class NameChangeInfo {
        public final boolean allowed;
        public final Instant changedAt;

        public NameChangeInfo(boolean allowed, Instant changedAt) {
            this.allowed = allowed;
            this.changedAt = changedAt;
        }
    }

    public static final class CapeResult {
        public final boolean success;
        public final boolean rateLimited;
        public final int retryAfterSeconds;
        public final String errorMessage;
        public final List<Cape> capes;

        private CapeResult(boolean success, boolean rateLimited, int retryAfterSeconds, String errorMessage, List<Cape> capes) {
            this.success = success;
            this.rateLimited = rateLimited;
            this.retryAfterSeconds = retryAfterSeconds;
            this.errorMessage = errorMessage;
            this.capes = capes;
        }

        static CapeResult success(List<Cape> capes) {
            return new CapeResult(true, false, 0, null, capes);
        }

        static CapeResult rateLimited(int retryAfterSeconds) {
            return new CapeResult(false, true, retryAfterSeconds, "Rate limited", null);
        }

        static CapeResult error(String message) {
            return new CapeResult(false, false, 0, message, null);
        }
    }

    public static final class Cape {
        public final String id;
        public final String alias;
        public final String url;
        public final boolean active;

        Cape(String id, String alias, String url, boolean active) {
            this.id = id;
            this.alias = alias;
            this.url = url;
            this.active = active;
        }

        public String getDisplayName() {
            if (alias != null && !alias.trim().isEmpty()) {
                return alias;
            }
            return id != null && !id.trim().isEmpty() ? id : "Unknown Cape";
        }
    }

    public static final class ProfileResult {
        public final boolean success;
        public final String username;
        public final String uuid;
        public final boolean rateLimited;
        public final int retryAfterSeconds;
        public final String errorMessage;
        public final List<Cape> capes;

        private ProfileResult(boolean success, String username, String uuid, boolean rateLimited,
                              int retryAfterSeconds, String errorMessage, List<Cape> capes) {
            this.success = success;
            this.username = username;
            this.uuid = uuid;
            this.rateLimited = rateLimited;
            this.retryAfterSeconds = retryAfterSeconds;
            this.errorMessage = errorMessage;
            this.capes = capes;
        }

        static ProfileResult success(String username, String uuid, List<Cape> capes) {
            return new ProfileResult(true, username, uuid, false, 0, null, capes);
        }

        static ProfileResult rateLimited(int retryAfterSeconds) {
            return new ProfileResult(false, null, null, true, retryAfterSeconds, "Rate limited", null);
        }

        static ProfileResult error(String message) {
            return new ProfileResult(false, null, null, false, 0, message, null);
        }
    }
}
