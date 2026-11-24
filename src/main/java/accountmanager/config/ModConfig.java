package accountmanager.config;

import accountmanager.AccountManagerMod;
import accountmanager.account.DataFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "settings.json";

    public static ButtonLocation buttonLocation = ButtonLocation.BOTH;
    public static ButtonCorner buttonCorner = ButtonCorner.TOP_RIGHT;
    public static float mainMenuButtonX = -1F;
    public static float mainMenuButtonY = -1F;
    public static float multiplayerButtonX = -1F;
    public static float multiplayerButtonY = -1F;
    public static ButtonStyle buttonStyle = ButtonStyle.FANCY;
    public static String accentColor = "7AD7F0";
    public static boolean rainbowAccent = false;
    public static Background background = Background.DIRT;
    public static String shader = "Galactic Nebula";
    public static int shaderOpacity = 50;
    public static boolean showCapes = true;
    public static boolean showTypes = true;
    public static boolean animations = true;
    public static boolean uiSounds = true;

    public static boolean doubleClickLogin = true;
    public static boolean confirmDelete = true;
    public static boolean closeAfterLogin = false;
    public static AutoLogin autoLogin = AutoLogin.OFF;
    public static boolean loginToasts = true;
    public static boolean rememberSelection = true;
    public static boolean refreshSkinsOnOpen = false;
    public static boolean alteningLegacyHttp = false;

    public static int clearClipboardSeconds = 0;
    public static int backupCount = 3;

    public static String rememberedAccount = "";

    private static final Map<Field, Object> DEFAULTS = snapshot();

    private ModConfig() {
    }

    public static void load() {
        File file = DataFiles.resolve(FILE);
        if (!file.isFile()) {
            save();
            return;
        }
        try (Reader reader = DataFiles.openText(file)) {
            JsonElement root = new JsonParser().parse(reader);
            if (root == null || !root.isJsonObject()) {
                return;
            }
            JsonObject json = root.getAsJsonObject();
            for (Field field : fields()) {
                if (json.has(field.getName())) {
                    read(field, json.get(field.getName()));
                }
            }
        } catch (IOException | RuntimeException e) {
            AccountManagerMod.LOGGER.warn("Could not read {}, using defaults", FILE, e);
        }
        clearClipboardSeconds = Math.max(0, Math.min(300, clearClipboardSeconds));
        backupCount = Math.max(0, Math.min(20, backupCount));
        shaderOpacity = Math.max(0, Math.min(100, shaderOpacity));
    }

    public static void save() {
        JsonObject json = new JsonObject();
        for (Field field : fields()) {
            try {
                Object value = field.get(null);
                if (value instanceof Boolean) {
                    json.addProperty(field.getName(), (Boolean) value);
                } else if (value instanceof Number) {
                    json.addProperty(field.getName(), (Number) value);
                } else if (value instanceof Enum) {
                    json.addProperty(field.getName(), ((Enum<?>) value).name());
                } else if (value != null) {
                    json.addProperty(field.getName(), value.toString());
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        File file = DataFiles.resolve(FILE);
        if (!DataFiles.ensureParent(file)) {
            return;
        }
        File temp = new File(file.getParentFile(), FILE + ".tmp");
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            AccountManagerMod.LOGGER.warn("Could not write {}", FILE, e);
            return;
        }
        try {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            AccountManagerMod.LOGGER.warn("Could not write {}", FILE, e);
        }
    }

    public static void resetToDefaults() {
        String remembered = rememberedAccount;
        for (Map.Entry<Field, Object> entry : DEFAULTS.entrySet()) {
            try {
                entry.getKey().set(null, entry.getValue());
            } catch (IllegalAccessException ignored) {
            }
        }
        rememberedAccount = remembered;
        save();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void read(Field field, JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return;
        }
        try {
            Class<?> type = field.getType();
            if (type == boolean.class) {
                field.setBoolean(null, value.getAsBoolean());
            } else if (type == int.class) {
                field.setInt(null, value.getAsInt());
            } else if (type == float.class) {
                field.setFloat(null, value.getAsFloat());
            } else if (type == String.class) {
                field.set(null, value.getAsString());
            } else if (type.isEnum()) {
                field.set(null, Enum.valueOf((Class<? extends Enum>) type, value.getAsString()));
            }
        } catch (IllegalAccessException | RuntimeException ignored) {
        }
    }

    private static List<Field> fields() {
        List<Field> fields = new ArrayList<>();
        for (Field field : ModConfig.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && !Modifier.isFinal(modifiers)) {
                fields.add(field);
            }
        }
        return fields;
    }

    private static Map<Field, Object> snapshot() {
        Map<Field, Object> values = new LinkedHashMap<>();
        for (Field field : fields()) {
            try {
                values.put(field, field.get(null));
            } catch (IllegalAccessException ignored) {
            }
        }
        return values;
    }

    public enum ButtonLocation {
        MAIN_MENU,
        MULTIPLAYER,
        BOTH;

        public boolean mainMenu() {
            return this != MULTIPLAYER;
        }

        public boolean multiplayer() {
            return this != MAIN_MENU;
        }
    }

    public enum ButtonCorner {
        TOP_RIGHT,
        TOP_LEFT,
        BOTTOM_RIGHT,
        BOTTOM_LEFT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_RIGHT || this == BOTTOM_LEFT;
        }
    }

    public enum ButtonStyle {
        VANILLA,
        MINIMAL,
        MODERN,
        FANCY
    }

    public enum Background {
        DIRT,
        DARK,
        ACCENT,
        SHADER
    }

    public enum AutoLogin {
        OFF,
        LAST_USED
    }
}
