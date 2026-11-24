package accountmanager.account;

import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class StoredAccount {
    private final Type type;
    private String username;
    private String uuid;
    private String refreshToken;
    private String accessToken;
    private String clientId;
    private String source;
    private long lastUsed;

    public StoredAccount(Type type, String username, String uuid, String refreshToken) {
        this(type, username, uuid, refreshToken, "", "", "");
    }

    public StoredAccount(Type type, String username, String uuid, String refreshToken,
                         String accessToken, String clientId, String source) {
        this.type = type;
        this.username = username;
        this.uuid = uuid;
        this.refreshToken = refreshToken == null ? "" : refreshToken;
        this.accessToken = accessToken == null ? "" : accessToken;
        this.clientId = clientId == null ? "" : clientId;
        this.source = source == null ? "" : source;
    }

    public static StoredAccount offline(String username) {
        UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        return new StoredAccount(Type.OFFLINE, username, id.toString(), "");
    }

    public static StoredAccount microsoft(String username, String uuid, String refreshToken) {
        return new StoredAccount(Type.MICROSOFT, username, uuid, refreshToken);
    }

    public static StoredAccount altening(String username, String altToken) {
        return new StoredAccount(Type.ALTENING, username, "", altToken);
    }

    public static StoredAccount imported(String username, String uuid, String refreshToken,
                                         String accessToken, String clientId, String source) {
        return new StoredAccount(Type.MICROSOFT, username, uuid, refreshToken, accessToken, clientId, source);
    }

    public Type getType() {
        return type;
    }

    public String getUsername() {
        return username;
    }

    public String getUuid() {
        return uuid;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSource() {
        return source;
    }

    public void updateMicrosoft(String username, String uuid, String refreshToken) {
        this.username = username;
        this.uuid = uuid;
        this.refreshToken = refreshToken;
    }

    public void rename(String username) {
        this.username = username;
    }

    public long getLastUsed() {
        return lastUsed;
    }

    public void markUsed() {
        lastUsed = System.currentTimeMillis();
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken;
    }

    public boolean adoptTokens(String refresh, String access, String client, String from) {
        boolean changed = false;
        if (isBlank(refreshToken) && !isBlank(refresh)) {
            refreshToken = refresh;
            changed = true;
        }
        if (isBlank(accessToken) && !isBlank(access)) {
            accessToken = access;
            changed = true;
        }
        if (isBlank(clientId) && !isBlank(client)) {
            clientId = client;
            changed = true;
        }
        if (changed && isBlank(source) && !isBlank(from)) {
            source = from;
        }
        return changed;
    }

    public boolean hasNoTokens() {
        return isBlank(refreshToken) && isBlank(accessToken);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.name());
        json.addProperty("username", username);
        json.addProperty("uuid", uuid);
        if (lastUsed > 0L) {
            json.addProperty("lastUsed", lastUsed);
        }
        if (type != Type.OFFLINE) {
            json.addProperty("refreshToken", refreshToken);
            if (!accessToken.isEmpty()) {
                json.addProperty("accessToken", accessToken);
            }
            if (!clientId.isEmpty()) {
                json.addProperty("clientId", clientId);
            }
            if (!source.isEmpty()) {
                json.addProperty("source", source);
            }
        }
        return json;
    }

    public static StoredAccount fromJson(JsonObject json) {
        Type type = Type.valueOf(json.get("type").getAsString());
        String username = json.get("username").getAsString();
        String uuid = json.get("uuid").getAsString();
        StoredAccount account = new StoredAccount(type, username, uuid,
                optional(json, "refreshToken"),
                optional(json, "accessToken"),
                optional(json, "clientId"),
                optional(json, "source"));
        if (json.has("lastUsed") && json.get("lastUsed").isJsonPrimitive()) {
            try {
                account.lastUsed = json.get("lastUsed").getAsLong();
            } catch (NumberFormatException ignored) {
            }
        }
        return account;
    }

    private static String optional(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    public enum Type {
        OFFLINE,
        MICROSOFT,
        ALTENING
    }
}
