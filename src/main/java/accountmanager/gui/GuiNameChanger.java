package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.AccountStorage;
import accountmanager.account.StoredAccount;
import accountmanager.auth.ProfileApi;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class GuiNameChanger extends GuiScreen {
    private static final int BTN_CHANGE = 0;
    private static final int BTN_BACK = 1;

    private static final int FIELD_TOP = 62;
    private static final int INFO_TOP = 92;
    private static final int CHECK_DELAY_TICKS = 12;
    private static final Duration COOLDOWN = Duration.ofDays(30);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, yyyy").withZone(ZoneId.systemDefault());

    private final GuiScreen parent;
    private GuiTextField nameField;
    private String status = "";
    private int statusColor = Theme.STATUS;
    private boolean working;

    private String lastText = "";
    private int idleTicks;
    private String checkedName = "";
    private volatile String availability;
    private volatile boolean checking;

    private volatile boolean infoLoaded;
    private volatile ProfileApi.NameChangeInfo info;

    public GuiNameChanger(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        String saved = nameField != null ? nameField.getText() : "";
        nameField = new GuiTextField(0, fontRenderer, Theme.left(width) + 6, FIELD_TOP + 7, Theme.HALF_WIDTH * 2 - 90, 12);
        nameField.setMaxStringLength(16);
        nameField.setEnableBackgroundDrawing(false);
        nameField.setValidator(text -> text != null && text.matches("[A-Za-z0-9_]*"));
        nameField.setText(saved);
        nameField.setFocused(true);

        int y = height - 28;
        buttonList.add(new GuiButton(BTN_CHANGE, Theme.buttonX(width, 0, 3), y, Theme.buttonWidth(3) * 2 + Theme.BUTTON_GAP, 20,
                working ? "Working..." : "Change Name"));
        buttonList.add(new GuiButton(BTN_BACK, Theme.buttonX(width, 2, 3), y, Theme.buttonWidth(3), 20, "Back"));
        updateButtons();

        if (!infoLoaded && hasToken()) {
            ProfileApi.getNameChangeInfoAsync(token(), result -> {
                info = result;
                infoLoaded = true;
            });
        } else if (!hasToken()) {
            setStatus("You need a signed-in Microsoft account to change names.", Theme.ERROR);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private String token() {
        return mc.getSession() != null ? mc.getSession().getToken() : null;
    }

    private boolean hasToken() {
        String token = token();
        return token != null && token.trim().length() >= 20;
    }

    private static boolean isValidName(String name) {
        return name.matches("[A-Za-z0-9_]{3,16}");
    }

    private void updateButtons() {
        String name = nameField.getText();
        boolean cooldown = info != null && !info.allowed;
        buttonList.get(BTN_CHANGE).enabled = !working && hasToken() && !cooldown && isValidName(name)
                && !name.equalsIgnoreCase(currentName()) && !"DUPLICATE".equals(availabilityFor(name))
                && !"NOT_ALLOWED".equals(availabilityFor(name));
    }

    private String availabilityFor(String name) {
        return name.equalsIgnoreCase(checkedName) ? availability : null;
    }

    @Override
    public void updateScreen() {
        nameField.updateCursorCounter();

        String text = nameField.getText();
        if (!text.equals(lastText)) {
            lastText = text;
            idleTicks = 0;
        } else if (idleTicks <= CHECK_DELAY_TICKS) {
            idleTicks++;
        }
        if (idleTicks >= CHECK_DELAY_TICKS && !checking && hasToken() && isValidName(text)
                && !text.equalsIgnoreCase(checkedName) && !text.equalsIgnoreCase(currentName())) {
            checkAvailability(text);
        }
        updateButtons();
    }

    private void checkAvailability(String name) {
        checking = true;
        checkedName = name;
        availability = null;
        ProfileApi.checkNameAsync(name, token(), result -> {
            availability = result;
            checking = false;
        });
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(parent);
        } else if (button.id == BTN_CHANGE) {
            changeName();
        }
    }

    private void changeName() {
        String name = nameField.getText();
        if (working || !buttonList.get(BTN_CHANGE).enabled) {
            return;
        }
        Session session = mc.getSession();
        working = true;
        setStatus("Changing name to " + name + "...", Theme.PENDING);
        initGui();

        ProfileApi.changeNameAsync(name, session.getToken(), (success, message) -> mc.addScheduledTask(() -> {
            working = false;
            if (success) {
                applyRename(session, name);
                nameField.setText("");
                checkedName = "";
                availability = null;
                info = new ProfileApi.NameChangeInfo(false, Instant.now());
            }
            setStatus(message, success ? Theme.SUCCESS : Theme.ERROR);
            initGui();
        }));
    }

    private void applyRename(Session session, String name) {
        StoredAccount saved = AccountStorage.findByUuid(session.getPlayerID());
        if (saved != null) {
            saved.rename(name);
            AccountStorage.save();
        }
        AccountStorage.setSession(new Session(name, session.getPlayerID(), session.getToken(), "mojang"));
        AccountSkinUtil.refreshAll();
    }

    private void setStatus(String message, int color) {
        status = message == null ? "" : message;
        statusColor = color;
    }

    private String currentName() {
        Session session = mc.getSession();
        return session != null && session.getUsername() != null ? session.getUsername() : "Unknown";
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            changeName();
        } else if (GuiScreen.isKeyComboCtrlV(keyCode)) {
            String clipboard = GuiScreen.getClipboardString();
            nameField.writeText(clipboard == null ? "" : clipboard.trim().replaceAll("[^A-Za-z0-9_]", ""));
        } else {
            nameField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        nameField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "Name Changer", currentName());

        int left = Theme.left(width);
        int right = Theme.right(width);

        fontRenderer.drawStringWithShadow("New Name", left, FIELD_TOP - 12, Theme.accent());
        Theme.drawField(left, FIELD_TOP, right, nameField.isFocused());
        nameField.drawTextBox();
        String text = nameField.getText();
        drawAvailability(text, right - 6, FIELD_TOP + 7);

        drawInfoRow(0, "Current name", currentName(), Theme.TEXT);
        drawInfoRow(1, "Last changed", lastChangedText(), Theme.TEXT);
        drawInfoRow(2, "Next change", nextChangeText(), info != null && !info.allowed ? Theme.ERROR : Theme.SUCCESS);

        Theme.drawStatus(fontRenderer, width, height - 50, status, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawAvailability(String name, int rightX, int y) {
        String label;
        int color;
        if (name.isEmpty()) {
            return;
        } else if (!isValidName(name)) {
            label = "Too short";
            color = Theme.MUTED;
        } else if (name.equalsIgnoreCase(currentName())) {
            label = "Current";
            color = Theme.MUTED;
        } else if (!name.equalsIgnoreCase(checkedName) || checking) {
            label = "Checking...";
            color = Theme.MUTED;
        } else if ("AVAILABLE".equals(availability)) {
            label = "Available";
            color = Theme.SUCCESS;
        } else if ("DUPLICATE".equals(availability)) {
            label = "Taken";
            color = Theme.ERROR;
        } else if ("NOT_ALLOWED".equals(availability)) {
            label = "Not allowed";
            color = Theme.ERROR;
        } else {
            label = "Unknown";
            color = Theme.MUTED;
        }
        fontRenderer.drawString(label, rightX - fontRenderer.getStringWidth(label), y, color);
    }

    private void drawInfoRow(int index, String label, String value, int valueColor) {
        int left = Theme.left(width);
        int right = Theme.right(width);
        int y = INFO_TOP + index * Theme.ROW_SPACING;
        if (y + Theme.ROW_HEIGHT > height - 62) {
            return;
        }
        Theme.drawRow(left, y, right, false, false);
        fontRenderer.drawString(label, left + 8, y + 7, Theme.LABEL);
        fontRenderer.drawString(value, right - 8 - fontRenderer.getStringWidth(value), y + 7, valueColor);
    }

    private String lastChangedText() {
        if (!hasToken()) {
            return "-";
        }
        if (!infoLoaded) {
            return "Loading...";
        }
        if (info == null) {
            return "Unavailable";
        }
        return info.changedAt == null ? "Never" : DATE.format(info.changedAt);
    }

    private String nextChangeText() {
        if (!hasToken()) {
            return "-";
        }
        if (!infoLoaded) {
            return "Loading...";
        }
        if (info == null) {
            return "Unavailable";
        }
        if (info.allowed) {
            return "Available now";
        }
        return info.changedAt == null ? "Not allowed" : DATE.format(info.changedAt.plus(COOLDOWN));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
