package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.AccountStorage;
import accountmanager.account.StoredAccount;
import accountmanager.auth.SessionUtil;
import accountmanager.client.MenuButtons;
import accountmanager.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class GuiSettings extends GuiScreen {
    private static final String[] TABS = {"Appearance", "Behaviour", "Privacy", "Tools"};
    private static final int[] SWATCHES = {0x7AD7F0, 0x8B9BFF, 0xB48CFF, 0xF07AB4, 0xFF8A7A, 0xF2C14E, 0x7FE08A, 0xE6E6E6};
    private static final int SWATCH = 10;
    private static final int CONTROL_INSET = 8;
    private static final int CHOICE_WIDTH = 84;
    private static final int BACK_ZONE = 16;
    private static final int SWATCH_GAP = 3;

    private static final int TAB_TOP = 42;
    private static final int LIST_TOP = 62;
    private static final int BTN_DONE = 0;
    private static final long MESSAGE_MS = 5000L;

    private static int lastTab;

    private final GuiScreen parent;
    private final List<List<Row>> tabs = new ArrayList<>();
    private int tab = lastTab;
    private float underlineX = -1.0F;
    private float underlineWidth;
    private float scrollTarget;
    private float scrollCurrent;
    private long lastFrame;
    private GuiTextField hexField;
    private boolean hexVisible;
    private SliderRow draggingSlider;
    private String message = "";
    private int messageColor = Theme.STATUS;
    private long messageUntil;

    public GuiSettings(GuiScreen parent) {
        this.parent = parent;
        buildRows();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        buttonList.add(new GuiButton(BTN_DONE, Theme.buttonX(width, 0, 1), height - 28, Theme.buttonWidth(1), 20, "Done"));

        String text = hexField != null ? hexField.getText() : ModConfig.accentColor.toUpperCase(Locale.ROOT);
        hexField = new GuiTextField(1, fontRenderer, 0, 0, 44, 10);
        hexField.setMaxStringLength(6);
        hexField.setEnableBackgroundDrawing(false);
        hexField.setText(text);
        clampScroll();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        ModConfig.save();
    }

    private void buildRows() {
        List<Row> appearance = new ArrayList<>();
        appearance.add(choice("Button style", "How the Accounts button on the menus looks.",
                new String[]{"Vanilla", "Minimal", "Modern", "Fancy"},
                () -> ModConfig.buttonStyle.ordinal(),
                i -> ModConfig.buttonStyle = ModConfig.ButtonStyle.values()[i]));
        appearance.add(choice("Button location", "Which screens get the Accounts button.",
                new String[]{"Main menu", "Multiplayer", "Both"},
                () -> ModConfig.buttonLocation.ordinal(),
                i -> ModConfig.buttonLocation = ModConfig.ButtonLocation.values()[i]));
        appearance.add(choice("Button corner", "The corner the Accounts button starts in, until you drag it somewhere else.",
                new String[]{"Top right", "Top left", "Bottom right", "Bottom left"},
                () -> ModConfig.buttonCorner.ordinal(),
                i -> ModConfig.buttonCorner = ModConfig.ButtonCorner.values()[i]));
        appearance.add(action("Move button on main menu", "Opens the main menu so you can drag the Accounts button anywhere.", false, () -> {
            MenuButtons.edit(MenuButtons.Target.MAIN_MENU, this);
            return null;
        }));
        appearance.add(action("Move button on server list", "Opens the server list so you can drag the Accounts button anywhere.", false, () -> {
            MenuButtons.edit(MenuButtons.Target.MULTIPLAYER, this);
            return null;
        }));
        appearance.add(action("Reset button position", "Puts the Accounts button back in its corner on both screens.", false, () -> {
            MenuButtons.resetPositions();
            return "The Accounts button is back in its corner.";
        }));
        appearance.add(new SwatchRow());
        appearance.add(new HexRow());
        appearance.add(toggle("Rainbow accent", "Slowly cycles the accent through soft pastels.",
                () -> ModConfig.rainbowAccent, v -> ModConfig.rainbowAccent = v));
        appearance.add(choice("Background", "What the account screens draw behind everything.",
                new String[]{"Dirt", "Dark", "Accent glow", "Shader"},
                () -> ModConfig.background.ordinal(),
                i -> ModConfig.background = ModConfig.Background.values()[i]));
        appearance.add(new ChoiceRow("Shader", "The shader used by the Shader background. Drop .frag files in the shaders folder to add your own.",
                () -> ModConfig.shader, direction -> {
            List<String> names = ShaderBackground.available();
            int index = Math.max(0, names.indexOf(ModConfig.shader));
            ModConfig.shader = names.get(Math.floorMod(index + direction, names.size()));
        }));
        appearance.add(new SliderRow("Shader opacity", "How strongly the shader shows. Lower is easier on the eyes.",
                0, 100, () -> ModConfig.shaderOpacity, v -> ModConfig.shaderOpacity = v));
        appearance.add(toggle("Show capes in list", "Draws each account's equipped cape beside its head.",
                () -> ModConfig.showCapes, v -> ModConfig.showCapes = v));
        appearance.add(toggle("Show account types", "Shows Microsoft / Offline / Altening on each row.",
                () -> ModConfig.showTypes, v -> ModConfig.showTypes = v));
        appearance.add(toggle("Animations", "Smooth scrolling, menu reveals and hover fades.",
                () -> ModConfig.animations, v -> ModConfig.animations = v));
        appearance.add(toggle("Interface sounds", "Clicks when using menus, tabs and switches.",
                () -> ModConfig.uiSounds, v -> ModConfig.uiSounds = v));

        List<Row> behaviour = new ArrayList<>();
        behaviour.add(toggle("Double-click to login", "Double-clicking an account row logs into it.",
                () -> ModConfig.doubleClickLogin, v -> ModConfig.doubleClickLogin = v));
        behaviour.add(toggle("Confirm delete", "The Delete button needs a second click before removing an account.",
                () -> ModConfig.confirmDelete, v -> ModConfig.confirmDelete = v));
        behaviour.add(toggle("Close after login", "Goes back to the previous screen as soon as a login works.",
                () -> ModConfig.closeAfterLogin, v -> ModConfig.closeAfterLogin = v));
        behaviour.add(choice("Login on startup", "Logs into your most recently used account when the game reaches the main menu.",
                new String[]{"Off", "Last used"},
                () -> ModConfig.autoLogin.ordinal(),
                i -> ModConfig.autoLogin = ModConfig.AutoLogin.values()[i]));
        behaviour.add(toggle("Login notifications", "A toast in the corner when a login finishes outside the account list.",
                () -> ModConfig.loginToasts, v -> ModConfig.loginToasts = v));
        behaviour.add(toggle("Remember selection", "Reopens the list on the account and scroll position you left it at.",
                () -> ModConfig.rememberSelection, v -> ModConfig.rememberSelection = v));
        behaviour.add(toggle("Altening legacy HTTP", "Talk to TheAltening over plain HTTP, like older launchers do. Turn on if pasted @alt.com tokens fail to log in.",
                () -> ModConfig.alteningLegacyHttp, v -> ModConfig.alteningLegacyHttp = v));
        behaviour.add(toggle("Refresh skins on open", "Re-downloads every head and cape each time the list opens.",
                () -> ModConfig.refreshSkinsOnOpen, v -> ModConfig.refreshSkinsOnOpen = v));

        List<Row> privacy = new ArrayList<>();
        privacy.add(numberChoice("Clear copied tokens", "Wipes a copied token from the clipboard after this long.",
                new int[]{0, 10, 30, 60}, new String[]{"Never", "10s", "30s", "60s"}, "s",
                () -> ModConfig.clearClipboardSeconds, v -> ModConfig.clearClipboardSeconds = v));
        privacy.add(numberChoice("Account backups", "Older copies of accounts.json to keep in backups/, one per launch.",
                new int[]{0, 1, 3, 5, 10}, new String[]{"Off", "1", "3", "5", "10"}, "",
                () -> ModConfig.backupCount, v -> ModConfig.backupCount = v));

        List<Row> tools = new ArrayList<>();
        tools.add(action("Open shaders folder", "Drop .frag shader files in here and pick them under Appearance -> Shader.", false, () -> {
            OpenGlHelper.openFile(ShaderBackground.getFolder());
            return "Opened the shaders folder.";
        }));
        tools.add(action("Open accounts folder", "Opens .minecraft/accountmanager in your file browser.", false, () -> {
            OpenGlHelper.openFile(AccountStorage.getFolder());
            return "Opened the accounts folder.";
        }));
        tools.add(action("Back up accounts now", "Copies accounts.json into the backups folder right now.", false, () -> {
            File file = AccountStorage.backupNow();
            return file == null ? "!Nothing to back up yet." : "Saved backups/" + file.getName() + ".";
        }));
        tools.add(action("Export accounts", "Writes a copy of the list you can move to another PC. It contains your tokens, so keep it private.", false, () -> {
            File file = AccountStorage.export();
            return file == null ? "!Export failed." : "Exported to " + file.getName() + ". Keep it private.";
        }));
        tools.add(action("Sort by name", "Puts the account list in A-Z order.", false, () -> {
            AccountStorage.sort(Comparator.comparing(a -> a.getUsername().toLowerCase(Locale.ROOT)));
            return "Sorted " + AccountStorage.getAccounts().size() + " accounts by name.";
        }));
        tools.add(action("Sort by last used", "Most recently used accounts first.", false, () -> {
            AccountStorage.sort(Comparator.comparingLong(StoredAccount::getLastUsed).reversed());
            return "Sorted by last used.";
        }));
        tools.add(action("Sort by type", "Microsoft, then Altening, then offline accounts, each A-Z.", false, () -> {
            AccountStorage.sort(Comparator.comparing(StoredAccount::getType)
                    .thenComparing(a -> a.getUsername().toLowerCase(Locale.ROOT)));
            return "Sorted by type.";
        }));
        tools.add(action("Remove unusable accounts", "Deletes Microsoft and Altening entries that have no token left to log in with.", true, () -> {
            int removed = AccountStorage.removeUnusable();
            return removed == 0 ? "Every account has a token; nothing removed." : "Removed " + removed + " unusable account" + (removed == 1 ? "." : "s.");
        }));
        tools.add(action("Refresh all skins", "Re-downloads every head and cape.", false, () -> {
            AccountSkinUtil.refreshAll();
            return "Refreshing skins...";
        }));
        tools.add(action("Back to launcher account", "Switches back to the account the launcher started the game with.", false, () -> {
            if (SessionUtil.isOriginalSessionActive()) {
                return "Already on the launcher's account.";
            }
            if (!SessionUtil.restoreOriginalSession()) {
                return "!The launcher's account was not captured.";
            }
            AccountSkinUtil.refreshAll();
            return "Back on " + mc.getSession().getUsername() + ".";
        }));
        tools.add(action("Reset all settings", "Puts every option on every tab back to its default.", true, () -> {
            ModConfig.resetToDefaults();
            hexField.setText(ModConfig.accentColor);
            return "Settings reset.";
        }));

        tabs.add(appearance);
        tabs.add(behaviour);
        tabs.add(privacy);
        tabs.add(tools);
    }

    private Row toggle(String label, String description, BooleanSupplier get, Consumer<Boolean> set) {
        return new ToggleRow(label, description, get, set);
    }

    private Row choice(String label, String description, String[] names, IntSupplier get, IntConsumer set) {
        return new ChoiceRow(label, description, () -> names[get.getAsInt()], direction -> {
            int next = Math.floorMod(get.getAsInt() + direction, names.length);
            set.accept(next);
        });
    }

    private Row numberChoice(String label, String description, int[] values, String[] names, String suffix,
                             IntSupplier get, IntConsumer set) {
        return new ChoiceRow(label, description, () -> {
            int value = get.getAsInt();
            for (int i = 0; i < values.length; i++) {
                if (values[i] == value) {
                    return names[i];
                }
            }
            return value + suffix;
        }, direction -> {
            int value = get.getAsInt();
            int index = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == value) {
                    index = i;
                }
            }
            if (index < 0) {
                index = direction > 0 ? values.length - 1 : 0;
                for (int i = 0; i < values.length; i++) {
                    if (direction > 0 && values[i] > value) {
                        index = i;
                        break;
                    }
                    if (direction < 0 && values[i] < value) {
                        index = i;
                    }
                }
                set.accept(values[index]);
                return;
            }
            set.accept(values[Math.floorMod(index + direction, values.length)]);
        });
    }

    private Row action(String label, String description, boolean confirm, Supplier<String> run) {
        return new ActionRow(label, description, confirm, run);
    }

    private List<Row> rows() {
        return tabs.get(tab);
    }

    private void setMessage(String text, int color) {
        message = text;
        messageColor = color;
        messageUntil = Minecraft.getSystemTime() + MESSAGE_MS;
    }

    private void changed() {
        ModConfig.save();
        Theme.playClick();
    }

    private int listBottom() {
        return height - 66;
    }

    private int listHeight() {
        return listBottom() - LIST_TOP;
    }

    private int maxScroll() {
        return Math.max(0, rows().size() * Theme.ROW_SPACING - 2 - listHeight());
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scrollCurrent = Math.max(0, Math.min(maxScroll(), scrollCurrent));
    }

    private int rowY(int index) {
        return LIST_TOP + index * Theme.ROW_SPACING - Math.round(scrollCurrent);
    }

    private int tabLeft(int index) {
        return Theme.left(width) + index * (Theme.HALF_WIDTH * 2 / TABS.length);
    }

    private int tabWidth() {
        return Theme.HALF_WIDTH * 2 / TABS.length;
    }

    private int rowAt(int mouseX, int mouseY) {
        if (mouseX < Theme.left(width) || mouseX >= Theme.right(width) || mouseY < LIST_TOP || mouseY >= listBottom()) {
            return -1;
        }
        int index = (mouseY - LIST_TOP + Math.round(scrollCurrent)) / Theme.ROW_SPACING;
        if (index < 0 || index >= rows().size()) {
            return -1;
        }
        int y = rowY(index);
        return mouseY < y + Theme.ROW_HEIGHT ? index : -1;
    }

    private void switchTab(int index) {
        if (index == tab) {
            return;
        }
        tab = index;
        lastTab = index;
        scrollTarget = 0;
        scrollCurrent = 0;
        hexField.setFocused(false);
        for (List<Row> list : tabs) {
            for (Row row : list) {
                row.disarm();
            }
        }
        Theme.playClick();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_DONE) {
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseY >= TAB_TOP && mouseY < TAB_TOP + 14 && mouseX >= Theme.left(width) && mouseX < Theme.right(width)) {
            switchTab(Math.min(TABS.length - 1, (mouseX - Theme.left(width)) / tabWidth()));
            return;
        }

        if (tab == 0 && hexVisible) {
            hexField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        int index = rowAt(mouseX, mouseY);
        if (index >= 0) {
            Row row = rows().get(index);
            row.click(mouseX, mouseY, mouseButton, Theme.left(width), rowY(index), Theme.right(width));
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (draggingSlider != null) {
            draggingSlider.drag(mouseX, Theme.right(width));
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (draggingSlider != null) {
            draggingSlider = null;
            ModConfig.save();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            scrollTarget -= (wheel / 120.0F) * Theme.ROW_SPACING * 1.8F;
            clampScroll();
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (tab == 0 && hexField.isFocused()) {
            if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                hexField.setFocused(false);
                return;
            }
            if (Character.digit(typedChar, 16) >= 0 || typedChar < ' ' || keyCode == Keyboard.KEY_BACK
                    || keyCode == Keyboard.KEY_DELETE || GuiScreen.isKeyComboCtrlV(keyCode) || GuiScreen.isKeyComboCtrlA(keyCode)) {
                hexField.textboxKeyTyped(typedChar, keyCode);
                String text = hexField.getText().replace("#", "").trim();
                if (text.matches("[0-9a-fA-F]{6}")) {
                    ModConfig.accentColor = text.toUpperCase(Locale.ROOT);
                    ModConfig.rainbowAccent = false;
                    ModConfig.save();
                }
            }
            return;
        }
        if (keyCode == Keyboard.KEY_LEFT || keyCode == Keyboard.KEY_RIGHT || keyCode == Keyboard.KEY_TAB) {
            int direction = keyCode == Keyboard.KEY_LEFT || (keyCode == Keyboard.KEY_TAB && GuiScreen.isShiftKeyDown()) ? -1 : 1;
            switchTab(Math.floorMod(tab + direction, TABS.length));
        }
    }

    @Override
    public void updateScreen() {
        hexField.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        long now = Minecraft.getSystemTime();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.05F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "Settings", mc.getSession() != null ? mc.getSession().getUsername() : null);
        drawTabs(mouseX, mouseY, dt);

        clampScroll();
        float diff = scrollTarget - scrollCurrent;
        scrollCurrent = !ModConfig.animations || Math.abs(diff) < 0.5F ? scrollTarget : scrollCurrent + diff * Math.min(1.0F, dt * 16.0F);

        int left = Theme.left(width);
        int right = Theme.right(width);
        int hoveredRow = rowAt(mouseX, mouseY);

        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, mc.displayHeight - listBottom() * scale, width * scale, listHeight() * scale);
        List<Row> rows = rows();
        hexVisible = false;
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i);
            if (y + Theme.ROW_HEIGHT <= LIST_TOP || y >= listBottom()) {
                continue;
            }
            Row row = rows.get(i);
            boolean hovered = i == hoveredRow;
            Theme.drawRow(left, y, right, false, hovered);
            fontRenderer.drawString(row.label, left + 8, y + 7, Theme.TEXT, false);
            row.draw(left, y, right, mouseX, mouseY, hovered, dt);
        }
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        drawScrollbar(right);

        String text;
        int color;
        boolean shaderBroken = ModConfig.background == ModConfig.Background.SHADER && !ShaderBackground.error().isEmpty();
        if (now < messageUntil) {
            text = message;
            color = messageColor;
        } else if (shaderBroken && (hoveredRow < 0 || "Shader".equals(rows.get(hoveredRow).label))) {
            text = "Shader error: " + ShaderBackground.error();
            color = Theme.ERROR;
        } else if (hoveredRow >= 0) {
            text = rows.get(hoveredRow).description;
            color = Theme.MUTED;
        } else {
            text = "";
            color = Theme.MUTED;
        }
        Theme.drawStatus(fontRenderer, width, height - 50, text, color);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawTabs(int mouseX, int mouseY, float dt) {
        int left = Theme.left(width);
        int right = Theme.right(width);
        int segment = tabWidth();
        for (int i = 0; i < TABS.length; i++) {
            int x = tabLeft(i);
            boolean hovered = mouseY >= TAB_TOP && mouseY < TAB_TOP + 14 && mouseX >= x && mouseX < x + segment;
            int color = i == tab ? Theme.TEXT : hovered ? 0xCCCCCC : Theme.MUTED;
            fontRenderer.drawString(TABS[i], x + (segment - fontRenderer.getStringWidth(TABS[i])) / 2, TAB_TOP + 2, color, false);
        }
        Gui.drawRect(left, TAB_TOP + 13, right, TAB_TOP + 14, 0x22FFFFFF);

        float targetWidth = fontRenderer.getStringWidth(TABS[tab]) + 8;
        float targetX = tabLeft(tab) + (segment - targetWidth) / 2.0F;
        if (underlineX < 0 || !ModConfig.animations) {
            underlineX = targetX;
            underlineWidth = targetWidth;
        } else {
            float t = Math.min(1.0F, dt * 14.0F);
            underlineX += (targetX - underlineX) * t;
            underlineWidth += (targetWidth - underlineWidth) * t;
        }
        Gui.drawRect(Math.round(underlineX), TAB_TOP + 12, Math.round(underlineX + underlineWidth), TAB_TOP + 14, Theme.accent(0xFF));
    }

    private void drawScrollbar(int contentRight) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int height = listHeight();
        int x = contentRight + 3;
        drawRect(x, LIST_TOP, x + 3, LIST_TOP + height, 0x33FFFFFF);
        int content = rows().size() * Theme.ROW_SPACING;
        int thumb = Math.max(20, (int) ((long) height * height / content));
        int thumbY = LIST_TOP + Math.round((height - thumb) * (scrollCurrent / max));
        drawRect(x, thumbY, x + 3, thumbY + thumb, Theme.accent(0xCC));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    private abstract static class Row {
        final String label;
        final String description;

        Row(String label, String description) {
            this.label = label;
            this.description = description;
        }

        abstract void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt);

        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
        }

        void disarm() {
        }
    }

    private final class ToggleRow extends Row {
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;
        private float on = -1.0F;

        ToggleRow(String label, String description, BooleanSupplier get, Consumer<Boolean> set) {
            super(label, description);
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            float target = get.getAsBoolean() ? 1.0F : 0.0F;
            on = on < 0 ? target : Theme.approach(on, target, dt);
            Theme.drawCheckbox(right - CONTROL_INSET, y + 6, on, hovered);
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            if (button == 0) {
                set.accept(!get.getAsBoolean());
                changed();
            }
        }
    }

    private final class ChoiceRow extends Row {
        private final Supplier<String> value;
        private final IntConsumer step;

        ChoiceRow(String label, String description, Supplier<String> value, IntConsumer step) {
            super(label, description);
            this.value = value;
            this.step = step;
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            int boxRight = right - CONTROL_INSET;
            int boxLeft = boxRight - CHOICE_WIDTH;
            int top = y + 4;
            boolean back = hovered && mouseX >= boxLeft && mouseX < boxLeft + BACK_ZONE;
            Theme.drawControlBox(boxLeft, top, boxRight, top + 14, Theme.controlOutline(hovered));
            fontRenderer.drawString("<", boxLeft + 5, top + 3, back ? Theme.accent() : Theme.MUTED, false);
            fontRenderer.drawString(">", boxRight - 5 - fontRenderer.getStringWidth(">"), top + 3,
                    hovered && !back ? Theme.accent() : Theme.MUTED, false);
            String text = fontRenderer.trimStringToWidth(value.get(), CHOICE_WIDTH - 2 * BACK_ZONE);
            fontRenderer.drawString(text, (boxLeft + boxRight - fontRenderer.getStringWidth(text)) / 2, top + 3, Theme.TEXT, false);
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            if (button == 0 || button == 1) {
                int boxLeft = right - CONTROL_INSET - CHOICE_WIDTH;
                boolean back = button == 1 || (mouseX >= boxLeft && mouseX < boxLeft + BACK_ZONE);
                step.accept(back ? -1 : 1);
                changed();
            }
        }
    }

    private final class SwatchRow extends Row {
        SwatchRow() {
            super("Accent colour", "Picks the highlight colour used across every account screen.");
        }

        private int swatchX(int right, int index) {
            return right - CONTROL_INSET - (SWATCHES.length - index) * SWATCH - (SWATCHES.length - 1 - index) * SWATCH_GAP;
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            int top = y + (Theme.ROW_HEIGHT - SWATCH) / 2;
            int current = parseAccent();
            for (int i = 0; i < SWATCHES.length; i++) {
                int x = swatchX(right, i);
                boolean over = mouseX >= x && mouseX < x + SWATCH && mouseY >= top && mouseY < top + SWATCH;
                boolean chosen = !ModConfig.rainbowAccent && current == SWATCHES[i];
                if (chosen || over) {
                    Theme.drawOutline(x - 2, top - 2, x + SWATCH + 2, top + SWATCH + 2, chosen ? 0xFFFFFFFF : 0x88FFFFFF);
                }
                Gui.drawRect(x, top, x + SWATCH, top + SWATCH, 0xFF000000 | SWATCHES[i]);
            }
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            int top = y + (Theme.ROW_HEIGHT - SWATCH) / 2;
            for (int i = 0; i < SWATCHES.length; i++) {
                int x = swatchX(right, i);
                if (mouseX >= x - 1 && mouseX < x + SWATCH + 1 && mouseY >= top - 1 && mouseY < top + SWATCH + 1) {
                    ModConfig.accentColor = String.format("%06X", SWATCHES[i]);
                    ModConfig.rainbowAccent = false;
                    hexField.setText(ModConfig.accentColor);
                    changed();
                    return;
                }
            }
        }

        private int parseAccent() {
            try {
                return Integer.parseInt(ModConfig.accentColor.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    private final class HexRow extends Row {
        HexRow() {
            super("Custom colour", "Type any RRGGBB hex colour; it applies as soon as all six digits are in.");
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            int boxRight = right - CONTROL_INSET;
            int boxLeft = boxRight - 62;
            int top = y + 4;
            boolean valid = hexField.getText().matches("[0-9a-fA-F]{6}");
            int outline = !hexField.isFocused() ? Theme.controlOutline(hovered)
                    : valid ? Theme.accent(0xFF) : 0xFF000000 | Theme.ERROR;
            Theme.drawControlBox(boxLeft, top, boxRight, top + 14, outline);
            Gui.drawRect(boxLeft + 3, top + 3, boxLeft + 11, top + 11, 0xFF000000 | Theme.accent());
            fontRenderer.drawString("#", boxLeft + 14, top + 3, Theme.MUTED, false);
            hexField.x = boxLeft + 21;
            hexField.y = top + 3;
            hexVisible = true;
            hexField.setTextColor(valid || !hexField.isFocused() ? 0xE0E0E0 : Theme.ERROR);
            hexField.drawTextBox();
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            if (!hexField.isFocused()) {
                hexField.setFocused(true);
            }
        }
    }

    private final class SliderRow extends Row {
        private final int min;
        private final int max;
        private final IntSupplier get;
        private final IntConsumer set;

        SliderRow(String label, String description, int min, int max, IntSupplier get, IntConsumer set) {
            super(label, description);
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
        }

        private int trackRight(int right) {
            return right - CONTROL_INSET;
        }

        private int trackLeft(int right) {
            return trackRight(right) - CHOICE_WIDTH;
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            int trackLeft = trackLeft(right);
            int trackRight = trackRight(right);
            int top = y + 8;
            float t = (get.getAsInt() - min) / (float) (max - min);
            Theme.drawControlBox(trackLeft, top, trackRight, top + 6, Theme.controlOutline(hovered || draggingSlider == this));
            Gui.drawRect(trackLeft + 1, top + 1, trackLeft + 1 + Math.round((trackRight - trackLeft - 2) * t), top + 5, Theme.accent(0xFF));
            int knob = trackLeft + Math.round((trackRight - trackLeft) * t);
            Theme.drawSoftRect(knob - 2, y + 5, knob + 2, y + 17, 0xFFE0E0E0);
            String value = get.getAsInt() + "%";
            fontRenderer.drawString(value, trackLeft - 6 - fontRenderer.getStringWidth(value), y + 7, Theme.TEXT, false);
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            if (button == 0 && mouseX >= trackLeft(right) - 4 && mouseX <= trackRight(right) + 4) {
                draggingSlider = this;
                drag(mouseX, right);
                Theme.playClick();
            }
        }

        void drag(int mouseX, int right) {
            int trackLeft = trackLeft(right);
            int trackRight = trackRight(right);
            float t = Math.max(0.0F, Math.min(1.0F, (mouseX - trackLeft) / (float) (trackRight - trackLeft)));
            set.accept(Math.round(min + t * (max - min)));
        }
    }

    private final class ActionRow extends Row {
        private final boolean confirm;
        private final Supplier<String> run;
        private boolean armed;

        ActionRow(String label, String description, boolean confirm, Supplier<String> run) {
            super(label, description);
            this.confirm = confirm;
            this.run = run;
        }

        @Override
        void draw(int left, int y, int right, int mouseX, int mouseY, boolean hovered, float dt) {
            if (armed && !hovered) {
                armed = false;
            }
            String hint = armed ? "Click again" : ">";
            int color = armed ? Theme.ERROR : hovered ? Theme.accent() : Theme.MUTED;
            fontRenderer.drawString(hint, right - CONTROL_INSET - fontRenderer.getStringWidth(hint), y + 7, color, false);
            if (hovered) {
                Gui.drawRect(left, y, left + 2, y + Theme.ROW_HEIGHT, confirm ? 0xFF000000 | Theme.ERROR : Theme.accent(0xFF));
            }
        }

        @Override
        void click(int mouseX, int mouseY, int button, int left, int y, int right) {
            if (button != 0) {
                return;
            }
            Theme.playClick();
            if (confirm && !armed) {
                armed = true;
                return;
            }
            armed = false;
            String result = run.get();
            if (result != null) {
                boolean error = result.startsWith("!");
                setMessage(error ? result.substring(1) : result, error ? Theme.ERROR : Theme.SUCCESS);
            }
        }

        @Override
        void disarm() {
            armed = false;
        }
    }
}
