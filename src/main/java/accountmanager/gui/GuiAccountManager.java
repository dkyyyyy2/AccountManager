package accountmanager.gui;

import accountmanager.account.AccountLogin;
import accountmanager.account.AccountSkinUtil;
import accountmanager.account.AccountStorage;
import accountmanager.account.LauncherAccountImporter;
import accountmanager.account.PastedCredentials;
import accountmanager.account.StoredAccount;
import accountmanager.account.TokenImporter;
import accountmanager.auth.MicrosoftAuth;
import accountmanager.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class GuiAccountManager extends GuiScreen {
    private static final int BTN_LOGIN = 0;
    private static final int BTN_ADD_OFFLINE = 1;
    private static final int BTN_ADD_MICROSOFT = 2;
    private static final int BTN_DELETE = 3;
    private static final int BTN_BACK = 4;
    private static final int BTN_COPY_PERM = 5;
    private static final int BTN_PASTE_TOKEN = 6;
    private static final int BTN_COPY_TEMP = 9;
    private static final int BTN_SYNC = 10;
    private static final int BTN_ALTENING = 11;
    private static final int BTN_SETTINGS = 12;

    private static final int ICON_SKIN = 0;
    private static final int ICON_CAPE = 1;
    private static final int ICON_NAME = 2;
    private static final int ICON_MORE = 3;
    private static final int ICON_COUNT = 4;
    private static final String[] ICON_TIPS = {"Change skin", "Change cape", "Change name", "Profiles & more"};

    private static final String[][] PROFILE_SITES = {
            {"NameMC", "https://namemc.com/profile/"},
            {"LabyMod", "https://laby.net/@"},
            {"Crafty", "https://crafty.gg/@"},
    };

    private static final long CONFIRM_MS = 3000L;

    private static final int ICON_SIZE = 16;
    private static final int ICON_GAP = 4;

    private static final long DOUBLE_CLICK_DELAY_MS = 300L;
    private static final int BUTTON_WIDTH = 100;

    private final GuiScreen parent;
    private int selected = -1;
    private float scrollTarget;
    private float scrollCurrent;
    private long lastFrameTime;
    private volatile String status = "";
    private volatile int statusColor = Theme.STATUS;
    private volatile boolean loggingIn;
    private int lastClickedAccount = -1;
    private long lastAccountClickTime;
    private ContextMenu menu;
    private long deleteArmedUntil;

    private boolean revealSelected;

    public GuiAccountManager(GuiScreen parent) {
        this.parent = parent;
        if (ModConfig.rememberSelection && !ModConfig.rememberedAccount.isEmpty()) {
            List<StoredAccount> accounts = AccountStorage.getAccounts();
            for (int i = 0; i < accounts.size(); i++) {
                if (ModConfig.rememberedAccount.equals(rememberKey(accounts.get(i)))) {
                    selected = i;
                    revealSelected = true;
                    break;
                }
            }
        }
        if (ModConfig.refreshSkinsOnOpen) {
            AccountSkinUtil.refreshAll();
        }
    }

    @Override
    public void onGuiClosed() {
        List<StoredAccount> accounts = AccountStorage.getAccounts();
        String key = selected >= 0 && selected < accounts.size() ? rememberKey(accounts.get(selected)) : "";
        if (!key.equals(ModConfig.rememberedAccount)) {
            ModConfig.rememberedAccount = key;
            ModConfig.save();
        }
    }

    private static String rememberKey(StoredAccount account) {
        String uuid = account.getUuid() == null ? "" : account.getUuid().replace("-", "").trim().toLowerCase();
        return uuid.isEmpty() ? "name:" + account.getUsername().toLowerCase() : uuid;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        menu = null;
        int row3 = height - 28;
        int row2 = row3 - Theme.ROW_SPACING;
        int row1 = row2 - Theme.ROW_SPACING;
        int col1 = Theme.buttonX(width, 0, 3);
        int col2 = Theme.buttonX(width, 1, 3);
        int col3 = Theme.buttonX(width, 2, 3);

        buttonList.add(new GuiButton(BTN_ALTENING, 8, 8, 60, 20, "Altening"));
        buttonList.add(new GuiButton(BTN_LOGIN, col1, row1, BUTTON_WIDTH, 20, "Login"));
        buttonList.add(new GuiButton(BTN_ADD_OFFLINE, col2, row1, BUTTON_WIDTH, 20, "Add Offline"));
        buttonList.add(new GuiButton(BTN_ADD_MICROSOFT, col3, row1, BUTTON_WIDTH, 20, "Add Microsoft"));

        buttonList.add(new GuiButton(BTN_COPY_PERM, col1, row2, BUTTON_WIDTH, 20, "Copy Perm"));
        buttonList.add(new GuiButton(BTN_COPY_TEMP, col2, row2, BUTTON_WIDTH, 20, "Copy Temp"));
        buttonList.add(new GuiButton(BTN_PASTE_TOKEN, col3, row2, BUTTON_WIDTH, 20, "Paste Token"));

        buttonList.add(new GuiButton(BTN_SYNC, col1, row3, BUTTON_WIDTH, 20, "Sync Launchers"));
        buttonList.add(new GuiButton(BTN_DELETE, col2, row3, BUTTON_WIDTH, 20, "Delete"));
        buttonList.add(new GuiButton(BTN_BACK, col3, row3, BUTTON_WIDTH, 20, "Back"));

        boolean room = col1 >= 8 + 20 + 6;
        buttonList.add(new IconButton(BTN_SETTINGS, room ? 8 : 72, room ? row3 : 8, "Settings"));

        if (selected >= AccountStorage.getAccounts().size()) {
            selected = -1;
        }
        clampScroll();
        if (revealSelected) {
            revealSelected = false;
            ensureVisible();
            scrollCurrent = scrollTarget;
        }
        updateButtons();
    }

    private void updateButtons() {
        List<StoredAccount> accounts = AccountStorage.getAccounts();
        boolean valid = selected >= 0 && selected < accounts.size() && !loggingIn;
        StoredAccount account = valid ? accounts.get(selected) : null;

        for (GuiButton button : buttonList) {
            switch (button.id) {
                case BTN_LOGIN:
                case BTN_DELETE:
                    button.enabled = valid;
                    break;
                case BTN_COPY_PERM:
                    button.enabled = valid && permToken(account) != null;
                    break;
                case BTN_COPY_TEMP:
                    button.enabled = valid && tempToken(account) != null;
                    break;
                case BTN_SETTINGS:
                    button.enabled = true;
                    break;
                default:
                    button.enabled = !loggingIn;
                    break;
            }
            if (button.id == BTN_DELETE) {
                boolean armed = deleteArmedUntil != 0L;
                button.displayString = armed ? "Confirm?" : "Delete";
                button.packedFGColour = armed ? Theme.ERROR : 0;
            }
        }
    }

    private void disarmDelete() {
        if (deleteArmedUntil != 0L) {
            deleteArmedUntil = 0L;
            updateButtons();
        }
    }

    @Override
    public void updateScreen() {
        if (deleteArmedUntil != 0L && Minecraft.getSystemTime() >= deleteArmedUntil) {
            disarmDelete();
        }
    }

    private void setStatus(String text, int color) {
        status = text == null ? "" : text;
        statusColor = color;
    }

    private static String permToken(StoredAccount account) {
        if (account == null) {
            return null;
        }
        String refresh = account.getRefreshToken();
        return refresh == null || refresh.trim().isEmpty() ? null : refresh.trim();
    }

    private String tempToken(StoredAccount account) {
        if (account == null) {
            return null;
        }

        Session session = mc.getSession();
        if (session != null && session.getUsername() != null
                && session.getUsername().equalsIgnoreCase(account.getUsername())) {
            String token = session.getToken();
            boolean altening = account.getType() == StoredAccount.Type.ALTENING;
            if (token != null && (altening ? !token.trim().isEmpty() : PastedCredentials.isAccessToken(token.trim()))) {
                return token;
            }
        }

        String stored = account.getAccessToken();
        return stored != null && !stored.trim().isEmpty() ? stored.trim() : null;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        List<StoredAccount> accounts = AccountStorage.getAccounts();
        StoredAccount chosen = selected >= 0 && selected < accounts.size() ? accounts.get(selected) : null;
        switch (button.id) {
            case BTN_BACK:
                mc.displayGuiScreen(parent);
                break;
            case BTN_ADD_OFFLINE:
                mc.displayGuiScreen(new GuiAddOfflineAccount(this));
                break;
            case BTN_ALTENING:
                mc.displayGuiScreen(new GuiAltening(this));
                break;
            case BTN_SETTINGS:
                mc.displayGuiScreen(new GuiSettings(this));
                break;
            case BTN_ADD_MICROSOFT:
                mc.displayGuiScreen(new GuiMicrosoftLogin(this));
                break;
            case BTN_COPY_PERM:
            case BTN_COPY_TEMP:
                if (chosen != null) {
                    copyToken(chosen, button.id == BTN_COPY_PERM);
                }
                break;
            case BTN_PASTE_TOKEN:
                pasteToken();
                break;
            case BTN_SYNC:
                syncLaunchers();
                break;
            case BTN_DELETE:
                if (chosen == null) {
                    break;
                }
                if (ModConfig.confirmDelete && deleteArmedUntil == 0L) {
                    deleteArmedUntil = Minecraft.getSystemTime() + CONFIRM_MS;
                    setStatus("Click Delete again to remove " + chosen.getUsername() + ".", Theme.PENDING);
                    updateButtons();
                    break;
                }
                deleteArmedUntil = 0L;
                removeAccount(chosen);
                break;
            case BTN_LOGIN:
                if (chosen != null) {
                    login(chosen);
                }
                break;
            default:
                break;
        }
    }

    private void removeAccount(StoredAccount account) {
        int index = AccountStorage.getAccounts().indexOf(account);
        if (index < 0) {
            return;
        }
        AccountStorage.remove(account);
        if (selected > index || selected >= AccountStorage.getAccounts().size()) {
            selected--;
        }
        clampScroll();
        setStatus("Account removed.", Theme.STATUS);
        updateButtons();
    }

    private void copyToken(StoredAccount account, boolean perm) {
        String token = perm ? permToken(account) : tempToken(account);
        if (token == null) {
            setStatus((perm ? "No perm (refresh) token stored for " : "No live temp (access) token for ")
                    + account.getUsername() + ".", Theme.ERROR);
            return;
        }

        GuiScreen.setClipboardString(token);
        int seconds = ModConfig.clearClipboardSeconds;
        setStatus("Copied " + (perm ? "perm (refresh)" : "temp (access)") + " token for " + account.getUsername()
                + (seconds > 0 ? "; clearing it in " + seconds + "s." : "."), Theme.STATUS);
        if (seconds > 0) {
            clearClipboardLater(token, seconds);
        }
    }

    private static void clearClipboardLater(String token, int seconds) {
        Minecraft mc = Minecraft.getMinecraft();
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(seconds * 1000L);
            } catch (InterruptedException e) {
                return;
            }
            mc.addScheduledTask(() -> {
                if (token.equals(GuiScreen.getClipboardString())) {
                    GuiScreen.setClipboardString("");
                }
            });
        }, "AccountManager-Clipboard");
        thread.setDaemon(true);
        thread.start();
    }

    private void pasteToken() {
        PastedCredentials creds = PastedCredentials.parse(GuiScreen.getClipboardString());
        if (!creds.hasAnything()) {
            setStatus("Clipboard does not contain a usable token.", Theme.ERROR);
            return;
        }

        if (creds.hasAlteningToken()) {
            loginAlteningToken(creds.alteningToken);
            return;
        }

        loggingIn = true;
        updateButtons();

        TokenImporter.loginAsync(creds, "", "pasted", new TokenImporter.Listener() {
            @Override
            public void onStatus(String text) {
                setStatus(text, Theme.PENDING);
            }

            @Override
            public void onDone(boolean success, String message) {
                loggingIn = false;
                setStatus(message, success ? Theme.SUCCESS : Theme.ERROR);
                mc.addScheduledTask(GuiAccountManager.this::updateButtons);
            }
        });
    }

    private void loginAlteningToken(String token) {
        StoredAccount existing = null;
        for (StoredAccount account : AccountStorage.getAccounts()) {
            if (account.getType() == StoredAccount.Type.ALTENING && token.equalsIgnoreCase(account.getRefreshToken())) {
                existing = account;
                break;
            }
        }
        boolean added = existing == null;
        StoredAccount account = added ? StoredAccount.altening("Altening", token) : existing;
        if (added) {
            AccountStorage.add(account);
        }
        selected = AccountStorage.getAccounts().indexOf(account);
        ensureVisible();
        loggingIn = true;
        updateButtons();
        AccountLogin.loginAsync(account, new AccountLogin.Listener() {
            @Override
            public void onStatus(String text) {
                setStatus(text, Theme.PENDING);
            }

            @Override
            public void onDone(boolean success, String message) {
                loggingIn = false;
                if (!success && added) {
                    AccountStorage.remove(account);
                    selected = -1;
                    clampScroll();
                }
                setStatus(success && added ? "Added and " + Character.toLowerCase(message.charAt(0)) + message.substring(1) : message,
                        success ? Theme.SUCCESS : Theme.ERROR);
                updateButtons();
            }
        });
    }

    private void syncLaunchers() {
        loggingIn = true;
        setStatus("Scanning installed launchers...", Theme.PENDING);
        updateButtons();

        new Thread(() -> {
            LauncherAccountImporter.Result result;
            try {
                result = LauncherAccountImporter.sync();
            } catch (Throwable t) {
                setStatus("Sync failed: " + describe(t), Theme.ERROR);
                loggingIn = false;
                mc.addScheduledTask(this::updateButtons);
                return;
            }

            mc.addScheduledTask(() -> {
                loggingIn = false;
                setStatus(result.describe(), Theme.STATUS);
                AccountSkinUtil.refreshAll();
                clampScroll();
                updateButtons();
            });
        }, "AccountManager-Launcher-Sync").start();
    }

    private void login(StoredAccount account) {
        loggingIn = account.getType() != StoredAccount.Type.OFFLINE;
        updateButtons();
        AccountLogin.loginAsync(account, new AccountLogin.Listener() {
            @Override
            public void onStatus(String text) {
                setStatus(text, Theme.PENDING);
            }

            @Override
            public void onDone(boolean success, String message) {
                loggingIn = false;
                setStatus(message, success ? Theme.SUCCESS : Theme.ERROR);
                updateButtons();
                boolean here = mc.currentScreen == GuiAccountManager.this;
                if (success && here && ModConfig.closeAfterLogin) {
                    mc.displayGuiScreen(parent);
                    here = false;
                }
                if (!here && ModConfig.loginToasts) {
                    mc.getToastGui().add(new LoginToast(account, success,
                            success ? "Logged in" : "Login failed",
                            success ? account.getUsername() : message));
                }
            }
        });
    }

    private static String describe(Throwable t) {
        String message = t.getMessage();
        return message == null ? t.getClass().getSimpleName() : message;
    }

    private boolean isActive(StoredAccount account) {
        Session session = mc.getSession();
        if (session == null || session.getUsername() == null) {
            return false;
        }
        String sessionId = String.valueOf(session.getPlayerID()).replace("-", "");
        String accountId = String.valueOf(account.getUuid()).replace("-", "");
        return sessionId.equalsIgnoreCase(accountId) && session.getUsername().equalsIgnoreCase(account.getUsername());
    }

    private int getListTop() {
        return Theme.CONTENT_TOP;
    }

    private int getListBottom() {
        return height - 104;
    }

    private int listHeight() {
        return getListBottom() - getListTop();
    }

    private int contentHeight() {
        return AccountStorage.getAccounts().size() * Theme.ROW_SPACING;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - listHeight());
    }

    private void clampScroll() {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scrollCurrent = Math.max(0, Math.min(maxScroll(), scrollCurrent));
    }

    private int scrollPixels() {
        return Math.round(scrollCurrent);
    }

    private int rowY(int index) {
        return getListTop() + index * Theme.ROW_SPACING - scrollPixels();
    }

    private boolean isVisible(int index) {
        int y = rowY(index);
        return y + Theme.ROW_HEIGHT > getListTop() && y < getListBottom();
    }

    private void animateScroll() {
        long now = Minecraft.getSystemTime();
        float dt = lastFrameTime == 0L ? 0.0F : Math.min(0.05F, (now - lastFrameTime) / 1000.0F);
        lastFrameTime = now;
        clampScroll();
        float diff = scrollTarget - scrollCurrent;
        if (!ModConfig.animations || Math.abs(diff) < 0.5F) {
            scrollCurrent = scrollTarget;
        } else {
            scrollCurrent += diff * Math.min(1.0F, dt * 16.0F);
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            menu = null;
            scrollTarget -= (wheel / 120.0F) * Theme.ROW_SPACING * 1.8F;
            clampScroll();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (menu != null) {
            if (menu.contains(mouseX, mouseY) && (mouseButton != 0 || !menu.click(mouseX, mouseY))) {
                return;
            }
            menu = null;
            return;
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);

        if (mouseButton == 0) {
            for (int icon = 0; icon < ICON_COUNT; icon++) {
                if (overRowIcon(icon, mouseX, mouseY)) {
                    if (icon == ICON_MORE) {
                        openProfiles(AccountStorage.getAccounts().get(selected));
                    } else {
                        openEditor(icon);
                    }
                    return;
                }
            }
        }

        int left = Theme.left(width);
        int right = Theme.right(width);
        if (mouseX < left || mouseX > right || mouseY < getListTop() || mouseY >= getListBottom()) {
            return;
        }

        int index = (mouseY - getListTop() + scrollPixels()) / Theme.ROW_SPACING;
        List<StoredAccount> accounts = AccountStorage.getAccounts();
        if (index < 0 || index >= accounts.size()) {
            return;
        }

        int rowY = rowY(index);
        if (mouseY < rowY || mouseY >= rowY + Theme.ROW_HEIGHT) {
            return;
        }

        if (index != selected) {
            disarmDelete();
        }
        if (mouseButton == 0 && !loggingIn) {
            int arrowX = left + 6;
            int stackTop = rowY + 3;
            int downY = stackTop + 6 + 3;
            boolean up = mouseX >= arrowX && mouseX < arrowX + 10 && mouseY >= stackTop && mouseY < stackTop + 6;
            boolean down = mouseX >= arrowX && mouseX < arrowX + 10 && mouseY >= downY && mouseY < downY + 6;
            if (up && index > 0 && AccountStorage.move(index, index - 1)) {
                selected = index - 1;
                ensureVisible();
                resetDoubleClick();
                updateButtons();
                return;
            }
            if (down && index < accounts.size() - 1 && AccountStorage.move(index, index + 1)) {
                selected = index + 1;
                ensureVisible();
                resetDoubleClick();
                updateButtons();
                return;
            }
        }

        long now = System.currentTimeMillis();
        boolean doubleClick = mouseButton == 0 && index == lastClickedAccount && now - lastAccountClickTime <= DOUBLE_CLICK_DELAY_MS;
        selected = index;
        updateButtons();
        if (mouseButton == 0) {
            lastClickedAccount = index;
            lastAccountClickTime = now;
        }

        if (doubleClick && !loggingIn && ModConfig.doubleClickLogin) {
            resetDoubleClick();
            login(accounts.get(index));
        }
    }

    private void openProfiles(StoredAccount account) {
        String name = account.getUsername();
        List<ContextMenu.Item> items = new ArrayList<>();
        for (String[] site : PROFILE_SITES) {
            items.add(ContextMenu.Item.of(site[0], () -> {
                MicrosoftAuth.openBrowser(site[1] + encode(name));
                setStatus("Opened " + site[0] + " for " + name + ".", Theme.STATUS);
            }));
        }
        int x = Theme.right(width) + (maxScroll() > 0 ? 9 : 4);
        menu = new ContextMenu(fontRenderer, account, name, "Profiles", items, x, rowY(selected), width, height);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private void openEditor(int icon) {
        StoredAccount account = AccountStorage.getAccounts().get(selected);
        if (!isActive(account)) {
            setStatus("Log in as " + account.getUsername() + " first to " + ICON_TIPS[icon].toLowerCase() + ".", Theme.ERROR);
            return;
        }
        if (icon == ICON_SKIN) {
            mc.displayGuiScreen(new GuiSkinChanger(this));
        } else if (icon == ICON_CAPE) {
            mc.displayGuiScreen(new GuiCapeChanger(this));
        } else {
            mc.displayGuiScreen(new GuiNameChanger(this));
        }
    }

    private void ensureVisible() {
        if (selected < 0) {
            return;
        }
        int rowTop = selected * Theme.ROW_SPACING;
        int rowBottom = rowTop + Theme.ROW_HEIGHT;
        if (rowTop < scrollTarget) {
            scrollTarget = rowTop;
        } else if (rowBottom > scrollTarget + listHeight()) {
            scrollTarget = rowBottom - listHeight();
        }
        clampScroll();
    }

    private void resetDoubleClick() {
        lastClickedAccount = -1;
        lastAccountClickTime = 0L;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (menu != null) {
            if (menu.keyTyped(keyCode)) {
                menu = null;
            }
            return;
        }
        if (keyCode == 1 && !loggingIn) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (GuiScreen.isKeyComboCtrlV(keyCode) && !loggingIn) {
            pasteToken();
            return;
        }
        if (GuiScreen.isKeyComboCtrlC(keyCode)) {
            copySelectedAccessToken();
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    private void copySelectedAccessToken() {
        List<StoredAccount> accounts = AccountStorage.getAccounts();
        if (selected < 0 || selected >= accounts.size()) {
            setStatus("Select an account first, then press Ctrl+C.", Theme.ERROR);
            return;
        }
        copyToken(accounts.get(selected), false);
    }

    @Override
    public void drawScreen(int realMouseX, int realMouseY, float partialTicks) {
        int mouseX = menu == null ? realMouseX : -1;
        int mouseY = menu == null ? realMouseY : -1;
        Theme.drawBackground(this, Theme.barY(height - 86, height - 76));
        Theme.drawHeader(fontRenderer, width, "Account Manager", currentName());
        animateScroll();

        List<StoredAccount> accounts = AccountStorage.getAccounts();
        int left = Theme.left(width);
        int right = Theme.right(width);
        int hoveredIcon = -1;

        ScaledResolution sr = new ScaledResolution(mc);
        int sf = sr.getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(0, (height - getListBottom()) * sf, width * sf, listHeight() * sf);

        for (int i = 0; i < accounts.size(); i++) {
            if (!isVisible(i)) {
                continue;
            }
            StoredAccount account = accounts.get(i);
            int y = rowY(i);
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + Theme.ROW_HEIGHT;
            boolean isSelected = i == selected;
            boolean active = isActive(account);
            Theme.drawRow(left, y, right, isSelected, hovered);
            if (active) {
                Theme.drawActiveMarker(left, y);
            }
            if (hovered) {
                drawReorderArrows(left, y, mouseX, mouseY, i, accounts.size());
            }

            AccountSkinUtil.drawHead(account, left + 24, y + 3, 16.0F);

            ResourceLocation equippedCape = ModConfig.showCapes ? AccountSkinUtil.getCapeLocation(account) : null;
            int nameX = left + 46;
            if (equippedCape != null) {
                AccountSkinUtil.drawCape(equippedCape, left + 43, y + 3, 10.0F, 16.0F);
                nameX = left + 58;
            }

            int iconY = y + (Theme.ROW_HEIGHT - ICON_SIZE) / 2;
            int typeRight = right - 6;
            if (isSelected) {
                for (int icon = 0; icon < ICON_COUNT; icon++) {
                    boolean over = overRowIcon(icon, mouseX, mouseY);
                    if (over) {
                        hoveredIcon = icon;
                    }
                    drawIcon(icon, account, rowIconX(icon), iconY, over, active || icon == ICON_MORE);
                }
                typeRight = rowIconX(0) - 6;
            }

            int typeX = typeRight;
            if (ModConfig.showTypes) {
                String type = account.getType() == StoredAccount.Type.MICROSOFT
                        ? TextFormatting.GREEN + "Microsoft"
                        : account.getType() == StoredAccount.Type.ALTENING
                        ? TextFormatting.LIGHT_PURPLE + "Altening"
                        : TextFormatting.GRAY + "Offline";
                typeX = typeRight - fontRenderer.getStringWidth(TextFormatting.getTextWithoutFormattingCodes(type));
                drawString(fontRenderer, type, typeX, y + 7, Theme.TEXT);
            }

            String name = fontRenderer.trimStringToWidth(account.getUsername(), Math.max(20, typeX - 6 - nameX));
            fontRenderer.drawString(name, nameX, y + 7, Theme.TEXT, false);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        if (accounts.isEmpty()) {
            drawCenteredString(fontRenderer, "No saved accounts.", width / 2, 64, Theme.MUTED);
        } else {
            drawScrollbar(right);
        }

        Theme.drawStatus(fontRenderer, width, height - 98, status, statusColor);

        super.drawScreen(mouseX, mouseY, partialTicks);

        if (menu != null) {
            menu.draw(realMouseX, realMouseY);
        } else if (hoveredIcon >= 0) {
            drawHoveringText(Collections.singletonList(ICON_TIPS[hoveredIcon]), mouseX, mouseY);
        } else {
            for (GuiButton button : buttonList) {
                if (button instanceof IconButton && ((IconButton) button).isMouseOver(mouseX, mouseY)) {
                    drawHoveringText(Collections.singletonList(((IconButton) button).getTooltip()), mouseX, mouseY);
                }
            }
        }
    }

    private String currentName() {
        Session session = mc.getSession();
        return session != null && session.getUsername() != null ? session.getUsername() : "Unknown";
    }

    private void drawScrollbar(int contentRight) {
        int max = maxScroll();
        if (max <= 0) {
            return;
        }
        int top = getListTop();
        int height = listHeight();
        int x = contentRight + 3;
        drawRect(x, top, x + 3, top + height, 0x33FFFFFF);

        int thumbHeight = Math.max(20, (int) ((long) height * height / contentHeight()));
        int thumbY = top + Math.round((height - thumbHeight) * (scrollCurrent / max));
        drawRect(x, thumbY, x + 3, thumbY + thumbHeight, Theme.accent(0xCC));
    }

    private int rowIconX(int index) {
        return Theme.right(width) - 6 - (ICON_COUNT - index) * ICON_SIZE - (ICON_COUNT - 1 - index) * ICON_GAP;
    }

    private boolean overRowIcon(int index, int mouseX, int mouseY) {
        if (selected < 0 || selected >= AccountStorage.getAccounts().size() || !isVisible(selected)) {
            return false;
        }
        int y = rowY(selected) + (Theme.ROW_HEIGHT - ICON_SIZE) / 2;
        int x = rowIconX(index);
        return mouseX >= x && mouseX < x + ICON_SIZE && mouseY >= y && mouseY < y + ICON_SIZE;
    }

    private void drawIcon(int icon, StoredAccount account, int x, int y, boolean hovered, boolean enabled) {
        Theme.drawIconFrame(x, y, ICON_SIZE, hovered, enabled);
        if (icon == ICON_SKIN) {
            AccountSkinUtil.drawHead(account, x + 2, y + 2, ICON_SIZE - 4);
        } else if (icon == ICON_CAPE) {
            drawCapeGlyph(account, x, y);
        } else if (icon == ICON_NAME) {
            drawNameTagGlyph(x, y);
        } else {
            Theme.drawDots(x + 2, y + 2, hovered ? Theme.accent(0xFF) : 0xFFDDDDDD);
        }
        if (!enabled) {
            drawRect(x + 1, y + 1, x + ICON_SIZE - 1, y + ICON_SIZE - 1, 0x88000000);
        }
    }

    private void drawCapeGlyph(StoredAccount account, int x, int y) {
        ResourceLocation cape = AccountSkinUtil.getCapeLocation(account);
        if (cape != null) {
            AccountSkinUtil.drawCape(cape, x + 3.5F, y + 2.0F, 9.0F, 12.0F);
            return;
        }
        int cloth = Theme.accent(0xFF);
        drawRect(x + 5, y + 3, x + 11, y + 4, 0xFFE8F4F8);
        drawRect(x + 4, y + 4, x + 12, y + 11, cloth);
        drawRect(x + 5, y + 11, x + 11, y + 13, cloth);
        drawRect(x + 6, y + 13, x + 10, y + 14, 0xFF000000 | Theme.blend(Theme.accent(0xFF), 0xFF000000, 0.55F));
    }

    private void drawNameTagGlyph(int x, int y) {
        int tag = Theme.accent(0xFF);
        drawRect(x + 5, y + 5, x + 13, y + 11, tag);
        drawRect(x + 4, y + 6, x + 5, y + 10, tag);
        drawRect(x + 3, y + 7, x + 4, y + 9, tag);
        drawRect(x + 5, y + 7, x + 6, y + 9, 0xFF202020);
        drawRect(x + 7, y + 7, x + 12, y + 8, 0xFFF0F0F0);
        drawRect(x + 7, y + 9, x + 10, y + 10, 0xFFF0F0F0);
        drawRect(x + 2, y + 4, x + 3, y + 7, 0xFFBBBBBB);
    }

    private void drawReorderArrows(int rowLeft, int rowY, int mouseX, int mouseY, int index, int accountCount) {
        int arrowX = rowLeft + 6;
        int stackTop = rowY + 3;
        int downY = stackTop + 6 + 3;
        if (index > 0) {
            boolean hoveredUp = mouseX >= arrowX && mouseX < arrowX + 10 && mouseY >= stackTop && mouseY < stackTop + 6;
            drawTriangle(arrowX, stackTop, 10.0F, 6.0F, true, hoveredUp ? -1 : -5592406);
        }
        if (index < accountCount - 1) {
            boolean hoveredDown = mouseX >= arrowX && mouseX < arrowX + 10 && mouseY >= downY && mouseY < downY + 6;
            drawTriangle(arrowX, downY, 10.0F, 6.0F, false, hoveredDown ? -1 : -5592406);
        }
    }

    private void drawTriangle(float x, float y, float width, float height, boolean up, int argb) {
        float a = (float) (argb >> 24 & 255) / 255.0F;
        float r = (float) (argb >> 16 & 255) / 255.0F;
        float g = (float) (argb >> 8 & 255) / 255.0F;
        float b = (float) (argb & 255) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(r, g, b, a);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(4, DefaultVertexFormats.POSITION);
        if (up) {
            buffer.pos(x + width / 2.0D, y, 0.0D).endVertex();
            buffer.pos(x, y + height, 0.0D).endVertex();
            buffer.pos(x + width, y + height, 0.0D).endVertex();
        } else {
            buffer.pos(x, y, 0.0D).endVertex();
            buffer.pos(x + width, y, 0.0D).endVertex();
            buffer.pos(x + width / 2.0D, y + height, 0.0D).endVertex();
        }
        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
