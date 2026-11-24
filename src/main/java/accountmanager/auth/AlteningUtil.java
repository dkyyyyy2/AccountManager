package accountmanager.auth;

import accountmanager.config.ModConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

public final class AlteningUtil {
    private static final String[] API_HOSTS = {"https://api.thealtening.com", "http://api.thealtening.com"};
    private static final String[] AUTH_HOSTS = {"https://authserver.thealtening.com", "http://authserver.thealtening.com"};
    private static final String[] LEGACY_API_HOSTS = {"http://api.thealtening.com"};
    private static final String[] LEGACY_AUTH_HOSTS = {"http://authserver.thealtening.com"};
    private static final java.util.regex.Pattern TOKEN = java.util.regex.Pattern.compile("(?i)^[a-z0-9]+(?:-[a-z0-9]+)+@alt\\.com$");
    private static final String MOJANG_SESSION = "https://sessionserver.mojang.com";
    private static final String ALTENING_SESSION = "http://sessionserver.thealtening.com";

    private static boolean redirected;

    private AlteningUtil() {
    }

    public static void generateAsync(String apiKey, Consumer<Result> callback) {
        new Thread(() -> callback.accept(generate(apiKey)), "AccountManager-Altening-generate").start();
    }

    public static void loginAsync(String altToken, Consumer<Result> callback) {
        new Thread(() -> callback.accept(login(altToken)), "AccountManager-Altening-login").start();
    }

    public static Result generate(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return Result.fail("Enter your TheAltening API key first.");
        }
        String lastError = "Could not reach TheAltening.";
        for (String host : ModConfig.alteningLegacyHttp ? LEGACY_API_HOSTS : API_HOSTS) {
            try {
                Http response = request(host + "/v2/generate?key=" + URLEncoder.encode(apiKey.trim(), "UTF-8"), null);
                JsonObject json = parse(response.body);
                if (response.status == 200 && json != null && json.has("token")) {
                    Result result = new Result();
                    result.success = true;
                    result.altToken = json.get("token").getAsString();
                    result.username = json.has("username") ? json.get("username").getAsString() : "Altening";
                    return result;
                }
                lastError = errorMessage(json, response.status);
                if (response.status > 0) {
                    break;
                }
            } catch (Exception exception) {
                lastError = "Connection failed: " + exception.getClass().getSimpleName();
            }
        }
        return Result.fail(lastError);
    }

    public static Result login(String altToken) {
        JsonObject body = new JsonObject();
        JsonObject agent = new JsonObject();
        agent.addProperty("name", "Minecraft");
        agent.addProperty("version", 1);
        body.add("agent", agent);
        body.addProperty("username", altToken);
        body.addProperty("password", "accountmanager");
        body.addProperty("requestUser", true);

        String lastError = "Could not reach TheAltening auth server.";
        for (String host : ModConfig.alteningLegacyHttp ? LEGACY_AUTH_HOSTS : AUTH_HOSTS) {
            try {
                Http response = request(host + "/authenticate", body.toString());
                JsonObject json = parse(response.body);
                if (response.status == 200 && json != null && json.has("accessToken") && json.has("selectedProfile")) {
                    JsonObject profile = json.getAsJsonObject("selectedProfile");
                    Result result = new Result();
                    result.success = true;
                    result.altToken = altToken;
                    result.accessToken = json.get("accessToken").getAsString();
                    result.uuid = profile.get("id").getAsString();
                    result.username = profile.get("name").getAsString();
                    return result;
                }
                lastError = errorMessage(json, response.status);
                if (response.status > 0) {
                    break;
                }
            } catch (Exception exception) {
                lastError = "Connection failed: " + exception.getClass().getSimpleName();
            }
        }
        return Result.fail(lastError);
    }

    public static boolean isAltToken(String value) {
        return value != null && TOKEN.matcher(value.trim()).matches();
    }

    public static synchronized void setSessionRedirect(boolean altening) {
        if (redirected == altening) {
            return;
        }
        String base = altening ? ALTENING_SESSION : MOJANG_SESSION;
        try {
            setStaticUrl("JOIN_URL", base + "/session/minecraft/join");
            setStaticUrl("CHECK_URL", base + "/session/minecraft/hasJoined");
            redirected = altening;
        } catch (Exception exception) {
            accountmanager.AccountManagerMod.LOGGER.warn("Failed to redirect session server to {}", base, exception);
        }
    }

    public static boolean isRedirected() {
        return redirected;
    }

    private static void setStaticUrl(String name, String url) throws Exception {
        Field field = YggdrasilMinecraftSessionService.class.getDeclaredField(name);
        field.setAccessible(true);
        Field modifiers = Field.class.getDeclaredField("modifiers");
        modifiers.setAccessible(true);
        modifiers.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, new URL(url));
    }

    private static Http request(String url, String jsonBody) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        MicrosoftAuth.applyTrust(connection);
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0");
        if (jsonBody != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }
        try {
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            StringBuilder builder = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        builder.append(line);
                    }
                }
            }
            return new Http(status, builder.toString());
        } finally {
            connection.disconnect();
        }
    }

    private static JsonObject parse(String body) {
        try {
            return new JsonParser().parse(body).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String errorMessage(JsonObject json, int status) {
        if (json != null) {
            for (String key : new String[]{"errorMessage", "message", "error"}) {
                if (json.has(key) && json.get(key).isJsonPrimitive()) {
                    return json.get(key).getAsString();
                }
            }
        }
        if (status == 401 || status == 403) {
            return "Rejected (HTTP " + status + ") - check your API key / plan.";
        }
        if (status == 429) {
            return "Rate limited by TheAltening, try again shortly.";
        }
        return "TheAltening returned HTTP " + status + ".";
    }

    private static final class Http {
        private final int status;
        private final String body;

        private Http(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    public static final class Result {
        public boolean success;
        public String error;
        public String altToken;
        public String accessToken;
        public String username;
        public String uuid;

        private static Result fail(String error) {
            Result result = new Result();
            result.error = error;
            return result;
        }
    }
}
