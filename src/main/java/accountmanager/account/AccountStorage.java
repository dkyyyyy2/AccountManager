package accountmanager.account;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import accountmanager.auth.AlteningUtil;
import accountmanager.auth.MicrosoftAuth;
import accountmanager.auth.SessionUtil;
import accountmanager.config.ModConfig;
import net.minecraft.util.Session;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class AccountStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<StoredAccount> ACCOUNTS = new ArrayList<>();
    private static boolean loaded;
    private static boolean backedUp;

    private AccountStorage() {
    }

    public static List<StoredAccount> getAccounts() {
        load();
        return Collections.unmodifiableList(ACCOUNTS);
    }

    public static void add(StoredAccount account) {
        load();
        ACCOUNTS.add(account);
        save();
    }

    public static void remove(StoredAccount account) {
        load();
        ACCOUNTS.remove(account);
        save();
    }

    public static boolean move(int fromIndex, int toIndex) {
        load();
        if (fromIndex < 0 || fromIndex >= ACCOUNTS.size() || toIndex < 0 || toIndex >= ACCOUNTS.size() || fromIndex == toIndex) {
            return false;
        }
        StoredAccount account = ACCOUNTS.remove(fromIndex);
        ACCOUNTS.add(toIndex, account);
        save();
        return true;
    }

    public static StoredAccount findByUuid(String uuid) {
        load();
        String wanted = compact(uuid);
        if (wanted.isEmpty()) {
            return null;
        }
        for (StoredAccount account : ACCOUNTS) {
            if (wanted.equals(compact(account.getUuid()))) {
                return account;
            }
        }
        return null;
    }

    public static void sort(Comparator<StoredAccount> comparator) {
        load();
        ACCOUNTS.sort(comparator);
        save();
    }

    public static int removeUnusable() {
        load();
        int before = ACCOUNTS.size();
        ACCOUNTS.removeIf(account -> account.getType() != StoredAccount.Type.OFFLINE && account.hasNoTokens());
        int removed = before - ACCOUNTS.size();
        if (removed > 0) {
            save();
        }
        return removed;
    }

    public static void save() {
        load();
        File file = getFile();
        if (!DataFiles.ensureParent(file)) {
            return;
        }
        if (!backedUp) {
            backedUp = true;
            backup(file, ModConfig.backupCount);
        }

        JsonArray array = new JsonArray();
        for (StoredAccount account : ACCOUNTS) {
            array.add(account.toJson());
        }

        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            GSON.toJson(array, writer);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        try {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static File backupNow() {
        load();
        return backup(getFile(), Math.max(1, ModConfig.backupCount));
    }

    public static File export() {
        load();
        save();
        File file = getFile();
        File target = new File(file.getParentFile(), "accounts-export-" + stamp() + ".json");
        try {
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException e) {
            return null;
        }
    }

    public static File getFolder() {
        return getFile().getParentFile();
    }

    private static File backup(File file, int keep) {
        if (keep <= 0 || !file.isFile()) {
            return null;
        }
        File dir = new File(file.getParentFile(), "backups");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            return null;
        }
        File target = new File(dir, "accounts-" + stamp() + ".json");
        try {
            Files.copy(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return null;
        }

        File[] copies = dir.listFiles((d, name) -> name.startsWith("accounts-") && name.endsWith(".json"));
        if (copies != null && copies.length > keep) {
            Arrays.sort(copies, Comparator.comparing(File::getName));
            for (int i = 0; i < copies.length - keep; i++) {
                copies[i].delete();
            }
        }
        return target;
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.ROOT).format(new Date());
    }

    public static void loginOffline(StoredAccount account) {
        setSession(new Session(account.getUsername(), account.getUuid(), "0", "legacy"));
        account.markUsed();
        save();
    }

    public static void loginMicrosoft(StoredAccount account, MicrosoftAuth.Progress progress) throws Exception {
        String refresh = account.getRefreshToken();
        if (refresh == null || refresh.trim().isEmpty()) {
            String access = account.getAccessToken();
            if (access == null || access.trim().isEmpty()) {
                throw new IllegalStateException("No refresh or access token stored for this account.");
            }
            progress.update("Using stored access token...");
            setSession(new Session(account.getUsername(), account.getUuid(), access, "mojang"));
            account.markUsed();
            save();
            return;
        }

        MicrosoftAuth.LoginResult result = MicrosoftAuth.loginWithRefreshToken(refresh, account.getClientId(), progress);
        account.updateMicrosoft(result.username, result.uuid, result.refreshToken);
        account.setAccessToken(result.minecraftAccessToken);
        account.markUsed();
        save();
        setSession(new Session(result.username, result.uuid, result.minecraftAccessToken, "mojang"));
    }

    public static void loginAltening(StoredAccount account) throws Exception {
        AlteningUtil.Result result = AlteningUtil.login(account.getRefreshToken());
        if (!result.success) {
            throw new IllegalStateException(result.error);
        }
        account.updateMicrosoft(result.username, result.uuid, account.getRefreshToken());
        account.setAccessToken(result.accessToken);
        account.markUsed();
        save();
        setSession(new Session(result.username, result.uuid, result.accessToken, "mojang"));
        AlteningUtil.setSessionRedirect(true);
    }

    public static void setSession(Session session) {
        SessionUtil.setSession(session);
    }

    private static String compact(String uuid) {
        return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase();
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = getFile();
        if (!file.isFile()) {
            return;
        }

        try (Reader reader = DataFiles.openText(file)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root != null && root.isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray()) {
                    try {
                        ACCOUNTS.add(StoredAccount.fromJson(element.getAsJsonObject()));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            File broken = new File(file.getParentFile(), "accounts-broken-" + stamp() + ".json");
            file.renameTo(broken);
        }
    }

    private static File getFile() {
        return DataFiles.resolve("accounts.json");
    }
}
