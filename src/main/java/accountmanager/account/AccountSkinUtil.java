package accountmanager.account;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.util.ResourceLocation;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AccountSkinUtil {
    private static final Minecraft MC = Minecraft.getMinecraft();
    private static final Map<String, String> SKIN_URLS = new ConcurrentHashMap<>();
    private static final Map<String, String> CAPE_URLS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> REQUESTED = new ConcurrentHashMap<>();
    private static final Map<String, UUID> NAME_TO_UUID = new ConcurrentHashMap<>();

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "AccountManager-Skins");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private AccountSkinUtil() {
    }

    private static StoredAccount sessionAccount;
    private static net.minecraft.util.Session sessionOwner;

    public static StoredAccount sessionAccount() {
        net.minecraft.util.Session session = MC.getSession();
        if (session == null || session.getUsername() == null) {
            return null;
        }
        StoredAccount saved = AccountStorage.findByUuid(session.getPlayerID());
        if (saved != null) {
            return saved;
        }

        if (sessionAccount != null && session == sessionOwner) {
            return sessionAccount;
        }
        String token = session.getToken();
        boolean online = token != null && token.length() > 20;
        sessionAccount = online
                ? StoredAccount.microsoft(session.getUsername(), String.valueOf(session.getPlayerID()), "")
                : StoredAccount.offline(session.getUsername());
        sessionOwner = session;
        return sessionAccount;
    }

    public static void drawHead(StoredAccount account, float x, float y, float size) {
        request(account);
        TextureCache.Entry entry = skinEntry(account);
        if (entry != null && entry.isReady()) {
            drawHead(entry.location, entry.referenceHeight(), x, y, size);
        } else {
            drawHead(DefaultPlayerSkin.getDefaultSkin(uuidOf(account)), 64.0F, x, y, size);
        }
    }

    public static void drawHead(ResourceLocation skin, float referenceHeight, float x, float y, float size) {
        if (skin == null) {
            return;
        }

        begin();
        MC.getTextureManager().bindTexture(skin);
        quad(x, y, size, size, 8.0F, 8.0F, 16.0F, 16.0F, 64.0F, referenceHeight);
        quad(x, y, size, size, 40.0F, 8.0F, 48.0F, 16.0F, 64.0F, referenceHeight);
        end();
    }

    public static void drawCape(ResourceLocation cape, float x, float y, float width, float height) {
        if (cape == null) {
            return;
        }

        begin();
        MC.getTextureManager().bindTexture(cape);
        quad(x, y, width, height, 1.0F, 1.0F, 11.0F, 17.0F, 64.0F, 32.0F);
        end();
    }

    public static ResourceLocation getSkinLocation(StoredAccount account) {
        request(account);
        TextureCache.Entry entry = skinEntry(account);
        return entry != null && entry.isReady() ? entry.location : DefaultPlayerSkin.getDefaultSkin(uuidOf(account));
    }

    public static ResourceLocation getCapeLocation(StoredAccount account) {
        request(account);
        String url = account == null ? null : CAPE_URLS.get(createKey(account));
        if (url == null) {
            return null;
        }
        TextureCache.Entry entry = TextureCache.get(url);
        return entry.isReady() ? entry.location : null;
    }

    public static void refresh(StoredAccount account) {
        if (account == null) {
            return;
        }
        String key = createKey(account);
        SKIN_URLS.remove(key);
        CAPE_URLS.remove(key);
        REQUESTED.remove(key);
        request(account);
    }

    public static void refreshAll() {
        SKIN_URLS.clear();
        CAPE_URLS.clear();
        REQUESTED.clear();
    }

    private static TextureCache.Entry skinEntry(StoredAccount account) {
        String url = account == null ? null : SKIN_URLS.get(createKey(account));
        return url == null ? null : TextureCache.get(url);
    }

    private static void request(StoredAccount account) {
        if (account == null) {
            return;
        }
        String key = createKey(account);
        if (REQUESTED.putIfAbsent(key, Boolean.TRUE) == null) {
            WORKER.execute(() -> resolve(account, key));
        }
    }

    private static void resolve(StoredAccount account, String key) {
        try {
            UUID profileUuid;
            if (account.getType() == StoredAccount.Type.MICROSOFT) {
                profileUuid = parseUuid(account.getUuid(), account.getUsername());
            } else {
                profileUuid = resolveOnlineUuid(account.getUsername());
            }
            if (profileUuid == null) {
                return;
            }

            String compact = profileUuid.toString().replace("-", "");
            String body = fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + compact + "?unsigned=true");
            if (body == null) {
                return;
            }

            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            if (!root.has("properties") || !root.get("properties").isJsonArray()) {
                return;
            }

            for (JsonElement element : root.getAsJsonArray("properties")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject property = element.getAsJsonObject();
                if (!property.has("name") || !"textures".equals(property.get("name").getAsString())
                        || !property.has("value")) {
                    continue;
                }

                byte[] decoded = Base64.getDecoder().decode(property.get("value").getAsString());
                JsonObject payload = new JsonParser().parse(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
                if (!payload.has("textures")) {
                    return;
                }

                JsonObject textures = payload.getAsJsonObject("textures");
                String skinUrl = textureUrl(textures, "SKIN");
                String capeUrl = textureUrl(textures, "CAPE");
                if (skinUrl != null) {
                    SKIN_URLS.put(key, skinUrl);
                    TextureCache.get(skinUrl);
                }
                if (capeUrl != null) {
                    CAPE_URLS.put(key, capeUrl);
                    TextureCache.get(capeUrl);
                }
                return;
            }
        } catch (Throwable ignored) {
        }
    }

    private static String textureUrl(JsonObject textures, String type) {
        if (!textures.has(type) || !textures.get(type).isJsonObject()) {
            return null;
        }
        JsonObject texture = textures.getAsJsonObject(type);
        return texture.has("url") ? texture.get("url").getAsString() : null;
    }

    private static UUID resolveOnlineUuid(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }

        String name = username.trim();
        UUID cached = NAME_TO_UUID.get(name.toLowerCase());
        if (cached != null) {
            return cached;
        }

        try {
            String body = fetch("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(name, "UTF-8"));
            if (body == null) {
                return null;
            }
            JsonObject json = new JsonParser().parse(body).getAsJsonObject();
            if (!json.has("id")) {
                return null;
            }
            UUID uuid = parseCompactUuid(json.get("id").getAsString());
            NAME_TO_UUID.put(name.toLowerCase(), uuid);
            return uuid;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String fetch(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(7000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "AccountManager/1.0");
            if (connection.getResponseCode() != 200) {
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            return response.toString();
        } catch (Throwable t) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String createKey(StoredAccount account) {
        String type = account.getType().name();
        String uuid = account.getUuid() == null ? "" : account.getUuid();
        String username = account.getUsername() == null ? "" : account.getUsername();
        return type + ':' + uuid.toLowerCase() + ':' + username.toLowerCase();
    }

    private static UUID uuidOf(StoredAccount account) {
        return account == null ? offlineUuid("Player") : parseUuid(account.getUuid(), account.getUsername());
    }

    private static UUID parseUuid(String value, String username) {
        if (value != null && !value.trim().isEmpty()) {
            try {
                return UUID.fromString(value);
            } catch (Throwable ignored) {
                try {
                    return parseCompactUuid(value);
                } catch (Throwable ignored2) {
                }
            }
        }
        return offlineUuid(username == null ? "Player" : username);
    }

    private static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID parseCompactUuid(String value) {
        String compact = value.replace("-", "").trim();
        if (compact.length() != 32) {
            throw new IllegalArgumentException("Invalid UUID: " + value);
        }
        return UUID.fromString(compact.substring(0, 8) + '-' + compact.substring(8, 12) + '-'
                + compact.substring(12, 16) + '-' + compact.substring(16, 20) + '-' + compact.substring(20, 32));
    }

    private static void begin() {
        GlStateManager.enableTexture2D();
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void end() {
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void quad(float x, float y, float width, float height,
                             float u0, float v0, float u1, float v1,
                             float refWidth, float refHeight) {
        float su0 = u0 / refWidth;
        float sv0 = v0 / refHeight;
        float su1 = u1 / refWidth;
        float sv1 = v1 / refHeight;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + height, 0.0D).tex(su0, sv1).endVertex();
        buffer.pos(x + width, y + height, 0.0D).tex(su1, sv1).endVertex();
        buffer.pos(x + width, y, 0.0D).tex(su1, sv0).endVertex();
        buffer.pos(x, y, 0.0D).tex(su0, sv0).endVertex();
        tessellator.draw();
    }
}
