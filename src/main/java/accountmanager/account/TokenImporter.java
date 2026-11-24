package accountmanager.account;

import accountmanager.auth.MicrosoftAuth;
import accountmanager.auth.ProfileApi;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class TokenImporter {

    private TokenImporter() {
    }

    public interface Listener {
        void onStatus(String text);

        void onDone(boolean success, String message);
    }

    public static void loginAsync(PastedCredentials creds, String clientId, String source, Listener listener) {
        Thread thread = new Thread(() -> run(creds, clientId, source, listener), "AccountManager-Token-Import");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(PastedCredentials creds, String clientId, String source, Listener listener) {
        String id = clientId == null ? "" : clientId.trim();

        if (creds.hasRefreshToken()) {
            listener.onStatus("Refresh token found - redeeming...");
            try {
                MicrosoftAuth.LoginResult result = MicrosoftAuth.loginWithRefreshToken(
                        creds.refreshToken, id, listener::onStatus, TokenImporter::saveRotatedToken);
                apply(result.username, result.uuid, result.minecraftAccessToken, result.refreshToken, id, source, listener);
                return;
            } catch (MicrosoftAuth.RotatedTokenException rotated) {
                if (!creds.hasAccessToken()) {
                    listener.onDone(false, "Refresh worked but sign-in failed (" + rotated.getMessage()
                            + "). Replacement token saved to accountmanager/rotated-tokens.txt.");
                    return;
                }
                listener.onStatus("Refresh rotated but sign-in failed - trying access token...");
            } catch (Throwable t) {
                if (!creds.hasAccessToken()) {
                    listener.onDone(false, "Refresh token failed: " + describe(t));
                    return;
                }
                listener.onStatus("Refresh token rejected - falling back to access token...");
            }
        }

        if (creds.hasMicrosoftAccessToken()) {
            listener.onStatus("Microsoft access token found - signing in...");
            try {
                MicrosoftAuth.LoginResult result =
                        MicrosoftAuth.loginWithMicrosoftAccessToken(creds.microsoftAccessToken, listener::onStatus);
                apply(result.username, result.uuid, result.minecraftAccessToken, "", id, source, listener);
                return;
            } catch (Throwable t) {
                listener.onDone(false, "Microsoft access token failed: " + describe(t));
                return;
            }
        }

        if (!creds.hasAccessToken()) {
            listener.onDone(false, "No usable token found.");
            return;
        }

        listener.onStatus("Validating access token...");
        ProfileApi.ProfileResult profile = ProfileApi.getProfileInfoWithRateLimit(creds.accessToken);
        if (profile.rateLimited) {
            listener.onDone(false, "Rate limited, retry in " + profile.retryAfterSeconds + "s.");
            return;
        }
        if (!profile.success) {
            listener.onDone(false, "Access token is invalid or expired"
                    + (profile.errorMessage == null ? "." : ": " + profile.errorMessage));
            return;
        }

        apply(profile.username, profile.uuid, creds.accessToken, creds.refreshToken, id, source, listener);
    }

    private static void apply(String username, String uuid, String accessToken, String refreshToken,
                              String clientId, String source, Listener listener) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            AccountStorage.add(StoredAccount.imported(username, uuid,
                    refreshToken == null ? "" : refreshToken, accessToken, clientId, source));
            AccountStorage.setSession(new Session(username, uuid, accessToken, "mojang"));
            AccountSkinUtil.refreshAll();
            listener.onDone(true, "Logged in as " + username + ".");
        });
    }

    public static void saveRotatedToken(String newRefreshToken) {
        try {
            File file = DataFiles.resolve("rotated-tokens.txt");
            File parent = file.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                return;
            }
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                writer.write(System.currentTimeMillis() + " " + newRefreshToken + System.lineSeparator());
            }
        } catch (Throwable ignored) {
        }
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null || message.trim().isEmpty() ? t.getClass().getSimpleName() : message;
    }
}
