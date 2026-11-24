package accountmanager.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class LauncherAccountImporter {

    private LauncherAccountImporter() {
    }

    public static List<Source> detect() {
        List<Source> sources = new ArrayList<>();
        String appData = System.getenv("APPDATA");
        String home = System.getProperty("user.home");

        add(sources, "Minecraft Launcher", Format.MOJANG, appData, ".minecraft", "launcher_accounts.json");
        add(sources, "Minecraft Launcher (MS Store)", Format.MOJANG, appData, ".minecraft", "launcher_accounts_microsoft_store.json");
        add(sources, "Prism Launcher", Format.PRISM, appData, "PrismLauncher", "accounts.json");
        add(sources, "Prism Launcher", Format.PRISM, home, ".local", "share", "PrismLauncher", "accounts.json");
        add(sources, "PolyMC", Format.PRISM, appData, "PolyMC", "accounts.json");
        add(sources, "PolyMC", Format.PRISM, home, ".local", "share", "PolyMC", "accounts.json");
        add(sources, "MultiMC", Format.PRISM, appData, "MultiMC", "accounts.json");
        add(sources, "MultiMC", Format.PRISM, home, "MultiMC", "accounts.json");

        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.gameDir != null) {
            addFile(sources, "Minecraft Launcher", Format.MOJANG, new File(mc.gameDir, "launcher_accounts.json"));
            addFile(sources, "Minecraft Launcher (MS Store)", Format.MOJANG,
                    new File(mc.gameDir, "launcher_accounts_microsoft_store.json"));
        }
        return sources;
    }

    public static Result sync() {
        List<Source> sources = detect();
        List<String> launchers = new ArrayList<>();
        int failed = 0;

        Map<String, Candidate> best = new LinkedHashMap<>();

        for (Source source : sources) {
            if (!launchers.contains(source.launcher)) {
                launchers.add(source.launcher);
            }

            List<Parsed> parsed;
            try {
                parsed = source.format == Format.MOJANG ? parseMojang(source.file) : parsePrism(source.file);
            } catch (Throwable t) {
                failed++;
                continue;
            }

            for (Parsed account : parsed) {
                String key = key(account.uuid, account.username);
                Candidate candidate = new Candidate(account, source.launcher);
                Candidate current = best.get(key);
                if (current == null || candidate.score() > current.score()) {
                    best.put(key, candidate);
                }
            }
        }

        Map<String, StoredAccount> stored = new LinkedHashMap<>();
        for (StoredAccount account : AccountStorage.getAccounts()) {
            stored.put(key(account.getUuid(), account.getUsername()), account);
        }

        List<String> added = new ArrayList<>();
        List<String> upgraded = new ArrayList<>();
        int duplicates = 0;
        int tokenless = 0;
        boolean dirty = false;

        for (Map.Entry<String, Candidate> entry : best.entrySet()) {
            Candidate candidate = entry.getValue();
            Parsed account = candidate.parsed;
            StoredAccount existing = stored.get(entry.getKey());

            if (existing != null) {
                if (existing.adoptTokens(account.refreshToken, account.accessToken,
                        account.clientId, candidate.launcher)) {
                    upgraded.add(account.username);
                    dirty = true;
                } else {
                    duplicates++;
                }
                continue;
            }

            if (candidate.score() == 0) {
                tokenless++;
                continue;
            }

            AccountStorage.add(StoredAccount.imported(account.username, account.uuid,
                    account.refreshToken, account.accessToken, account.clientId, candidate.launcher));
            added.add(account.username);
        }

        if (dirty) {
            AccountStorage.save();
        }

        return new Result(launchers, added, upgraded, duplicates, tokenless, failed);
    }

    private static final class Candidate {
        final Parsed parsed;
        final String launcher;

        Candidate(Parsed parsed, String launcher) {
            this.parsed = parsed;
            this.launcher = launcher;
        }

        int score() {
            int score = 0;
            if (!isBlank(parsed.refreshToken)) {
                score += 2;
            }
            if (!isBlank(parsed.accessToken)) {
                score += 1;
            }
            return score;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static Set<String> knownClientIds() {
        Set<String> ids = new LinkedHashSet<>();
        for (Source source : detect()) {
            if (source.format != Format.PRISM) {
                continue;
            }
            try {
                JsonObject root = read(source.file);
                if (root == null || !root.has("accounts") || !root.get("accounts").isJsonArray()) {
                    continue;
                }
                for (JsonElement element : root.getAsJsonArray("accounts")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    String id = string(element.getAsJsonObject(), "msa-client-id").trim();
                    if (!id.isEmpty()) {
                        ids.add(id);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return ids;
    }

    private static List<Parsed> parseMojang(File file) throws Exception {
        List<Parsed> results = new ArrayList<>();
        JsonObject root = read(file);
        if (root == null || !root.has("accounts") || !root.get("accounts").isJsonObject()) {
            return results;
        }

        for (java.util.Map.Entry<String, JsonElement> entry : root.getAsJsonObject("accounts").entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject account = entry.getValue().getAsJsonObject();
            if (!account.has("minecraftProfile") || !account.get("minecraftProfile").isJsonObject()) {
                continue;
            }
            JsonObject profile = account.getAsJsonObject("minecraftProfile");
            String name = string(profile, "name");
            String uuid = dashed(string(profile, "id"));
            if (name.isEmpty() || uuid.isEmpty()) {
                continue;
            }
            results.add(new Parsed(name, uuid, "", string(account, "accessToken"), ""));
        }
        return results;
    }

    private static List<Parsed> parsePrism(File file) throws Exception {
        List<Parsed> results = new ArrayList<>();
        JsonObject root = read(file);
        if (root == null || !root.has("accounts") || !root.get("accounts").isJsonArray()) {
            return results;
        }

        JsonArray accounts = root.getAsJsonArray("accounts");
        for (JsonElement element : accounts) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject account = element.getAsJsonObject();

            String name = "";
            String uuid = "";
            if (account.has("profile") && account.get("profile").isJsonObject()) {
                JsonObject profile = account.getAsJsonObject("profile");
                name = string(profile, "name");
                uuid = dashed(string(profile, "id"));
            } else if (account.has("profiles") && account.get("profiles").isJsonArray()) {
                JsonArray profiles = account.getAsJsonArray("profiles");
                if (profiles.size() > 0 && profiles.get(0).isJsonObject()) {
                    JsonObject profile = profiles.get(0).getAsJsonObject();
                    name = string(profile, "name");
                    uuid = dashed(string(profile, "id"));
                }
            }
            if (name.isEmpty() || uuid.isEmpty()) {
                continue;
            }

            String refresh = "";
            if (account.has("msa") && account.get("msa").isJsonObject()) {
                refresh = string(account.getAsJsonObject("msa"), "refresh_token");
            }
            String access = "";
            if (account.has("ygg") && account.get("ygg").isJsonObject()) {
                access = string(account.getAsJsonObject("ygg"), "token");
            } else {
                access = string(account, "accessToken");
            }

            results.add(new Parsed(name, uuid, refresh, access, string(account, "msa-client-id")));
        }
        return results;
    }

    private static JsonObject read(File file) throws Exception {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement root = new JsonParser().parse(reader);
            return root != null && root.isJsonObject() ? root.getAsJsonObject() : null;
        }
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : "";
    }

    private static String dashed(String value) {
        String compact = value == null ? "" : value.replace("-", "").trim();
        if (compact.length() != 32) {
            return "";
        }
        return compact.substring(0, 8) + '-' + compact.substring(8, 12) + '-' + compact.substring(12, 16)
                + '-' + compact.substring(16, 20) + '-' + compact.substring(20, 32);
    }

    private static String key(String uuid, String username) {
        String id = uuid == null ? "" : uuid.replace("-", "").toLowerCase(Locale.ROOT);
        return id.isEmpty() ? "name:" + (username == null ? "" : username.toLowerCase(Locale.ROOT)) : "id:" + id;
    }

    private static void add(List<Source> sources, String launcher, Format format, String base, String... parts) {
        if (base == null || base.isEmpty()) {
            return;
        }
        File file = new File(base);
        for (String part : parts) {
            file = new File(file, part);
        }
        addFile(sources, launcher, format, file);
    }

    private static void addFile(List<Source> sources, String launcher, Format format, File file) {
        if (!file.isFile()) {
            return;
        }
        for (Source existing : sources) {
            if (existing.file.equals(file)) {
                return;
            }
        }
        sources.add(new Source(launcher, format, file));
    }

    private enum Format {
        MOJANG,
        PRISM
    }

    public static final class Source {
        public final String launcher;
        final Format format;
        final File file;

        Source(String launcher, Format format, File file) {
            this.launcher = launcher;
            this.format = format;
            this.file = file;
        }
    }

    private static final class Parsed {
        final String username;
        final String uuid;
        final String refreshToken;
        final String accessToken;
        final String clientId;

        Parsed(String username, String uuid, String refreshToken, String accessToken, String clientId) {
            this.username = username;
            this.uuid = uuid;
            this.refreshToken = refreshToken;
            this.accessToken = accessToken;
            this.clientId = clientId;
        }
    }

    public static final class Result {
        public final List<String> launchers;
        public final List<String> added;
        public final List<String> upgraded;
        public final int duplicates;
        public final int tokenless;
        public final int failed;

        Result(List<String> launchers, List<String> added, List<String> upgraded,
               int duplicates, int tokenless, int failed) {
            this.launchers = launchers;
            this.added = added;
            this.upgraded = upgraded;
            this.duplicates = duplicates;
            this.tokenless = tokenless;
            this.failed = failed;
        }

        public String describe() {
            if (launchers.isEmpty()) {
                return "No launchers detected on this machine.";
            }
            StringBuilder builder = new StringBuilder();
            builder.append(added.size()).append(" added");
            if (!upgraded.isEmpty()) {
                builder.append(", ").append(upgraded.size()).append(" got tokens");
            }
            if (duplicates > 0) {
                builder.append(", ").append(duplicates).append(" already stored");
            }
            if (tokenless > 0) {
                builder.append(", ").append(tokenless).append(" skipped (no token)");
            }
            if (failed > 0) {
                builder.append(", ").append(failed).append(" unreadable");
            }
            builder.append(" from ").append(String.join(", ", launchers));
            return builder.toString();
        }
    }
}
