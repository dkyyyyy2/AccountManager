package accountmanager.auth;

import accountmanager.account.LauncherAccountImporter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

public final class MicrosoftAuth {
    private static final String CLIENT_ID = "54fd49e4-2103-4044-9603-2b028c814ec3";
    private static final String MSAL_REDIRECT = "http://localhost:59125";
    private static final String MSAL_SCOPE = "XboxLive.signin XboxLive.offline_access";

    private static final String DEVICE_CLIENT_ID = "00000000402b5328";
    private static final String DEVICE_SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String LIVE_TOKEN_URL = "https://login.live.com/oauth20_token.srf";
    private static final String CONSUMERS_TOKEN_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String USER_AGENT = "AccountManager/1.0";

    private static final SSLContext FIXED_CONTEXT = buildFixedContext();

    private MicrosoftAuth() {
    }

    private static SSLContext buildFixedContext() {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = MicrosoftAuth.class.getResourceAsStream("/assets/accountmanager/sslfix.jks")) {
                if (in == null) {
                    return null;
                }
                keyStore.load(in, "accountmanager".toCharArray());
            }

            TrustManagerFactory customTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            customTmf.init(keyStore);
            TrustManagerFactory defaultTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            defaultTmf.init((KeyStore) null);

            List<X509TrustManager> managers = new ArrayList<>();
            addX509(managers, customTmf.getTrustManagers());
            addX509(managers, defaultTmf.getTrustManagers());

            TrustManager multiManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    check(chain, authType, false);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    check(chain, authType, true);
                }

                private void check(X509Certificate[] chain, String authType, boolean server) throws CertificateException {
                    CertificateException wrapper = new CertificateException("Untrusted by every trust manager.");
                    for (X509TrustManager manager : managers) {
                        try {
                            if (server) {
                                manager.checkServerTrusted(chain, authType);
                            } else {
                                manager.checkClientTrusted(chain, authType);
                            }
                            return;
                        } catch (Throwable t) {
                            wrapper.addSuppressed(t);
                        }
                    }
                    throw wrapper;
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    List<X509Certificate> certificates = new ArrayList<>();
                    for (X509TrustManager manager : managers) {
                        certificates.addAll(Arrays.asList(manager.getAcceptedIssuers()));
                    }
                    return certificates.toArray(new X509Certificate[0]);
                }
            };

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{multiManager}, new SecureRandom());
            return ctx;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void addX509(List<X509TrustManager> managers, TrustManager[] source) {
        for (TrustManager manager : source) {
            if (manager instanceof X509TrustManager) {
                managers.add((X509TrustManager) manager);
            }
        }
    }

    public static void applyTrust(URLConnection connection) {
        if (FIXED_CONTEXT != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        }
    }

    private static HttpURLConnection open(String endpoint) throws Exception {
        URLConnection connection = new URL(endpoint).openConnection();
        if (FIXED_CONTEXT != null && connection instanceof HttpsURLConnection) {
            ((HttpsURLConnection) connection).setSSLSocketFactory(FIXED_CONTEXT.getSocketFactory());
        }
        return (HttpURLConnection) connection;
    }

    public static DeviceCode requestDeviceCode() throws Exception {
        JsonObject json = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
                "client_id=" + enc(CLIENT_ID) + "&scope=" + enc(SCOPE));
        int expires = json.get("expires_in").getAsInt();
        int interval = json.has("interval") ? json.get("interval").getAsInt() : 5;
        String uri = json.has("verification_uri") ? json.get("verification_uri").getAsString() : "https://www.microsoft.com/link";
        String completeUri = json.has("verification_uri_complete") ? json.get("verification_uri_complete").getAsString() : uri;
        return new DeviceCode(json.get("device_code").getAsString(), json.get("user_code").getAsString(),
                uri, completeUri, interval, System.currentTimeMillis() + (long) expires * 1000L);
    }

    public static LoginResult loginWithMicrosoftAccessToken(String microsoftAccessToken, Progress progress) throws Exception {
        return exchange(new OAuthTokens(microsoftAccessToken, ""), true, progress);
    }

    public static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception ignored) {
        }
    }

    public static LoginResult finishDeviceLogin(DeviceCode code, Progress progress) throws Exception {
        progress.update("Waiting for Microsoft authorization...");

        while (System.currentTimeMillis() < code.expiresAt) {
            OAuthTokens tokens;
            try {
                JsonObject json = postForm("https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
                        "grant_type=" + enc("urn:ietf:params:oauth:grant-type:device_code")
                                + "&client_id=" + enc(CLIENT_ID)
                                + "&device_code=" + enc(code.deviceCode));
                tokens = new OAuthTokens(json.get("access_token").getAsString(), json.get("refresh_token").getAsString());
            } catch (OAuthPendingException ignored) {
                Thread.sleep((long) code.intervalSeconds * 1000L);
                continue;
            }
            return exchange(tokens, true, progress);
        }

        throw new IOException("Microsoft device code expired.");
    }

    public static LoginResult loginWithRefreshToken(String refreshToken, Progress progress) throws Exception {
        return loginWithRefreshToken(refreshToken, CLIENT_ID, progress);
    }

    public static LoginResult loginWithRefreshToken(String refreshToken, String clientId, Progress progress) throws Exception {
        return loginWithRefreshToken(refreshToken, clientId, progress, null);
    }

    public static LoginResult loginWithRefreshToken(String refreshToken, String clientId, Progress progress, TokenSink sink) throws Exception {
        List<Attempt> attempts = attemptsFor(clientId);
        List<String> failures = new ArrayList<>();

        for (int i = 0; i < attempts.size(); i++) {
            Attempt attempt = attempts.get(i);
            OAuthTokens tokens;
            try {
                progress.update(i == 0 ? "Refreshing Microsoft token..."
                        : "Retrying as " + attempt.label + " (" + (i + 1) + "/" + attempts.size() + ")...");
                tokens = attempt.refresh(refreshToken);
            } catch (Exception e) {
                failures.add(attempt.label + " -> " + shorten(e.getMessage()));
                continue;
            }

            if (sink != null && tokens.refresh != null && !tokens.refresh.isEmpty()) {
                try {
                    sink.onRotated(tokens.refresh);
                } catch (Throwable ignored) {
                }
            }

            try {
                return exchange(tokens, attempt.dPrefix, progress);
            } catch (Exception e) {
                throw new RotatedTokenException(tokens.refresh, e);
            }
        }

        throw new IOException(failures.isEmpty()
                ? "Refresh token could not be redeemed."
                : String.join(" | ", failures) + "");
    }

    private static String shorten(String message) {
        if (message == null) {
            return "no message";
        }
        String value = message.replaceAll("\\s+", " ").trim();
        int trace = value.indexOf("Trace ID:");
        if (trace > 0) {
            value = value.substring(0, trace).trim();
        }
        return value.length() > 110 ? value.substring(0, 110) + "..." : value;
    }

    private static List<Attempt> attemptsFor(String preferredClientId) {
        Attempt msal = new Attempt("IAS", LIVE_TOKEN_URL, CLIENT_ID, MSAL_SCOPE, MSAL_REDIRECT, true);
        Attempt device = new Attempt("Xbox", LIVE_TOKEN_URL, DEVICE_CLIENT_ID, DEVICE_SCOPE, null, false);
        Attempt consumers = new Attempt("v2", CONSUMERS_TOKEN_URL, CLIENT_ID, SCOPE, null, true);

        List<Attempt> attempts = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        seen.add(CLIENT_ID);
        seen.add(DEVICE_CLIENT_ID);

        String preferred = preferredClientId == null ? "" : preferredClientId.trim();
        if (!preferred.isEmpty() && seen.add(preferred)) {
            attempts.add(foreignAttempt(preferred, SCOPE));
            attempts.add(foreignAttempt(preferred, MSAL_SCOPE));
        }

        if (DEVICE_CLIENT_ID.equals(preferred)) {
            attempts.add(device);
            attempts.add(msal);
        } else {
            attempts.add(msal);
            attempts.add(device);
        }
        attempts.add(consumers);

        for (String id : LauncherAccountImporter.knownClientIds()) {
            if (id != null && seen.add(id.trim())) {
                attempts.add(foreignAttempt(id.trim(), SCOPE));
            }
        }
        return attempts;
    }

    private static Attempt foreignAttempt(String clientId, String scope) {
        String label = "client " + (clientId.length() > 8 ? clientId.substring(0, 8) : clientId);
        return new Attempt(label, LIVE_TOKEN_URL, clientId, scope, null, true);
    }

    private static final class Attempt {
        final String label;
        final String endpoint;
        final String clientId;
        final String scope;
        final String redirectUri;
        final boolean dPrefix;

        Attempt(String label, String endpoint, String clientId, String scope, String redirectUri, boolean dPrefix) {
            this.label = label;
            this.endpoint = endpoint;
            this.clientId = clientId;
            this.scope = scope;
            this.redirectUri = redirectUri;
            this.dPrefix = dPrefix;
        }

        OAuthTokens refresh(String refreshToken) throws Exception {
            String body = "client_id=" + enc(clientId)
                    + "&refresh_token=" + enc(refreshToken)
                    + "&grant_type=refresh_token"
                    + (redirectUri == null ? "" : "&redirect_uri=" + enc(redirectUri))
                    + "&scope=" + enc(scope);
            JsonObject json = LIVE_TOKEN_URL.equals(endpoint) ? postLiveForm(endpoint, body) : postForm(endpoint, body);
            String rotated = json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken;
            return new OAuthTokens(json.get("access_token").getAsString(), rotated);
        }
    }

    private static JsonObject postLiveForm(String endpoint, String body) throws Exception {
        HttpURLConnection connection = open(endpoint);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setDoOutput(true);
        try (OutputStream out = connection.getOutputStream()) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        JsonObject json = text.isEmpty() ? new JsonObject() : new JsonParser().parse(text).getAsJsonObject();
        if (status >= 200 && status < 300) {
            return json;
        }

        if (status == 429) {
            String retry = connection.getHeaderField("Retry-After");
            throw new IOException("Rate limited by Minecraft services - wait "
                    + (retry == null ? "a few minutes" : retry + "s")
                    + " and try again. The account is fine.");
        }

        String error = json.has("error") ? json.get("error").getAsString() : "HTTP " + status;
        String detail = json.has("error_description") ? json.get("error_description").getAsString() : text;
        throw new IOException(error + ": " + detail);
    }

    public interface TokenSink {
        void onRotated(String newRefreshToken);
    }

    public static final class RotatedTokenException extends Exception {
        public final String newRefreshToken;

        RotatedTokenException(String newRefreshToken, Throwable cause) {
            super(cause.getMessage(), cause);
            this.newRefreshToken = newRefreshToken;
        }
    }

    private static LoginResult exchange(OAuthTokens ms, boolean dPrefix, Progress progress) throws Exception {
        progress.update("Signing in to Xbox Live...");
        JsonObject xblReq = new JsonObject();
        JsonObject props = new JsonObject();
        props.addProperty("AuthMethod", "RPS");
        props.addProperty("SiteName", "user.auth.xboxlive.com");
        props.addProperty("RpsTicket", dPrefix ? "d=" + ms.access : ms.access);
        xblReq.add("Properties", props);
        xblReq.addProperty("RelyingParty", "http://auth.xboxlive.com");
        xblReq.addProperty("TokenType", "JWT");
        JsonObject xbl = postJson("https://user.auth.xboxlive.com/user/authenticate", xblReq);
        String xblToken = xbl.get("Token").getAsString();
        String userHash = xbl.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0).getAsJsonObject().get("uhs").getAsString();

        progress.update("Authorizing Xbox account...");
        JsonObject xstsReq = new JsonObject();
        JsonObject xstsProps = new JsonObject();
        xstsProps.addProperty("SandboxId", "RETAIL");
        JsonArray userTokens = new JsonArray();
        userTokens.add(xblToken);
        xstsProps.add("UserTokens", userTokens);
        xstsReq.add("Properties", xstsProps);
        xstsReq.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        xstsReq.addProperty("TokenType", "JWT");
        JsonObject xsts = postJson("https://xsts.auth.xboxlive.com/xsts/authorize", xstsReq);
        String xstsToken = xsts.get("Token").getAsString();

        progress.update("Signing in to Minecraft...");
        JsonObject mcReq = new JsonObject();
        mcReq.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        JsonObject mcLogin = postJson("https://api.minecraftservices.com/authentication/login_with_xbox", mcReq);
        String mcAccess = mcLogin.get("access_token").getAsString();

        progress.update("Reading Minecraft profile...");
        JsonObject profile = getJson("https://api.minecraftservices.com/minecraft/profile", mcAccess);
        String name = profile.get("name").getAsString();
        String id = profile.get("id").getAsString();
        String dashed = id.replaceFirst(
                "([0-9a-fA-F]{8})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{4})([0-9a-fA-F]{12})",
                "$1-$2-$3-$4-$5");
        progress.update("Logged in as " + name);
        return new LoginResult(name, dashed, mcAccess, ms.refresh);
    }

    private static JsonObject postForm(String endpoint, String body) throws Exception {
        return request(endpoint, "POST", "application/x-www-form-urlencoded", body, null);
    }

    private static JsonObject postJson(String endpoint, JsonObject body) throws Exception {
        return request(endpoint, "POST", "application/json", body.toString(), null);
    }

    private static JsonObject getJson(String endpoint, String bearer) throws Exception {
        return request(endpoint, "GET", "application/json", null, bearer);
    }

    private static JsonObject request(String endpoint, String method, String contentType, String body, String bearer) throws Exception {
        HttpURLConnection connection = open(endpoint);
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(15000);
        connection.setRequestMethod(method);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Content-Type", contentType);
        if (bearer != null) {
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
        }

        if (body != null) {
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
        String text = readAll(stream);
        JsonObject json = text.isEmpty() ? new JsonObject() : new JsonParser().parse(text).getAsJsonObject();
        if (status >= 200 && status < 300) {
            return json;
        }

        String error = json.has("error") ? json.get("error").getAsString() : "HTTP " + status;
        if (!"authorization_pending".equals(error) && !"slow_down".equals(error)) {
            String detail = json.has("error_description") ? json.get("error_description").getAsString() : text;
            throw new IOException(error + ": " + detail);
        }
        throw new OAuthPendingException();
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            return result.toString();
        }
    }

    private static String enc(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    public static final class DeviceCode {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final String verificationUriComplete;
        public final int intervalSeconds;
        public final long expiresAt;

        private DeviceCode(String deviceCode, String userCode, String verificationUri,
                            String verificationUriComplete, int intervalSeconds, long expiresAt) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.verificationUriComplete = verificationUriComplete;
            this.intervalSeconds = intervalSeconds;
            this.expiresAt = expiresAt;
        }
    }

    public static final class LoginResult {
        public final String username;
        public final String uuid;
        public final String minecraftAccessToken;
        public final String refreshToken;

        private LoginResult(String username, String uuid, String minecraftAccessToken, String refreshToken) {
            this.username = username;
            this.uuid = uuid;
            this.minecraftAccessToken = minecraftAccessToken;
            this.refreshToken = refreshToken;
        }
    }

    private static final class OAuthPendingException extends IOException {
        private OAuthPendingException() {
        }
    }

    private static final class OAuthTokens {
        final String access;
        final String refresh;

        OAuthTokens(String access, String refresh) {
            this.access = access;
            this.refresh = refresh;
        }
    }

    public interface Progress {
        void update(String text);
    }
}
