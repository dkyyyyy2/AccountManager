package accountmanager.account;

import net.minecraft.client.Minecraft;

public final class AccountLogin {

    private AccountLogin() {
    }

    public interface Listener {
        void onStatus(String text);

        void onDone(boolean success, String message);
    }

    public static void loginAsync(StoredAccount account, Listener listener) {
        Minecraft mc = Minecraft.getMinecraft();
        if (account.getType() == StoredAccount.Type.OFFLINE) {
            AccountStorage.loginOffline(account);
            AccountSkinUtil.refreshAll();
            AccountSkinUtil.refresh(account);
            listener.onDone(true, "Logged in as " + account.getUsername() + " (offline).");
            return;
        }

        boolean altening = account.getType() == StoredAccount.Type.ALTENING;
        listener.onStatus(altening ? "Logging in through TheAltening..." : "Starting Microsoft login...");
        Thread thread = new Thread(() -> {
            try {
                if (altening) {
                    AccountStorage.loginAltening(account);
                } else {
                    AccountStorage.loginMicrosoft(account, listener::onStatus);
                }
                mc.addScheduledTask(() -> {
                    AccountSkinUtil.refreshAll();
                    AccountSkinUtil.refresh(account);
                    listener.onDone(true, "Logged in as " + account.getUsername() + (altening ? " (Altening)." : "."));
                });
            } catch (Throwable t) {
                String message = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
                String text = (altening ? "Altening login failed: " : "Login failed: ") + message;
                mc.addScheduledTask(() -> listener.onDone(false, text));
            }
        }, "AccountManager-Login");
        thread.setDaemon(true);
        thread.start();
    }

    public static StoredAccount lastUsed() {
        StoredAccount best = null;
        for (StoredAccount account : AccountStorage.getAccounts()) {
            if (account.getLastUsed() > 0L && (best == null || account.getLastUsed() > best.getLastUsed())) {
                best = account;
            }
        }
        return best;
    }
}
