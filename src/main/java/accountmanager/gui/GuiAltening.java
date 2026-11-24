package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.AccountStorage;
import accountmanager.account.DataFiles;
import accountmanager.account.StoredAccount;
import accountmanager.auth.AlteningUtil;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class GuiAltening extends GuiScreen {
    private static final int BTN_GENERATE = 0;
    private static final int BTN_GENERATE_LOGIN = 1;
    private static final int BTN_BACK = 2;
    private static final int FIELD_TOP = 62;
    private static final String KEY_FILE = "altening-key.txt";

    private final GuiScreen parent;
    private GuiTextField keyField;
    private String status = "";
    private int statusColor = Theme.STATUS;
    private boolean working;

    public GuiAltening(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        String saved = keyField != null ? keyField.getText() : loadKey();
        keyField = new GuiTextField(0, fontRenderer, Theme.left(width) + 6, FIELD_TOP + 7, Theme.HALF_WIDTH * 2 - 12, 12);
        keyField.setMaxStringLength(256);
        keyField.setEnableBackgroundDrawing(false);
        keyField.setText(saved);
        keyField.setFocused(true);

        int y = height - 28;
        GuiButton generate = new GuiButton(BTN_GENERATE, Theme.buttonX(width, 0, 3), y, Theme.buttonWidth(3), 20,
                working ? "Working..." : "Generate");
        GuiButton generateLogin = new GuiButton(BTN_GENERATE_LOGIN, Theme.buttonX(width, 1, 3), y, Theme.buttonWidth(3), 20,
                "Generate + Login");
        generate.enabled = !working;
        generateLogin.enabled = !working;
        buttonList.add(generate);
        buttonList.add(generateLogin);
        buttonList.add(new GuiButton(BTN_BACK, Theme.buttonX(width, 2, 3), y, Theme.buttonWidth(3), 20, "Back"));
    }

    @Override
    public void updateScreen() {
        keyField.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        if (button.id == BTN_BACK) {
            mc.displayGuiScreen(parent);
        } else {
            generate(button.id == BTN_GENERATE_LOGIN);
        }
    }

    private void generate(boolean login) {
        if (working) {
            return;
        }
        String key = keyField.getText().trim();
        saveKey(key);
        working = true;
        setStatus("Generating alt...", Theme.PENDING);
        initGui();

        AlteningUtil.generateAsync(key, generated -> {
            if (!generated.success) {
                finish(generated.error, Theme.ERROR);
                return;
            }
            StoredAccount account = StoredAccount.altening(generated.username, generated.altToken);
            AccountStorage.add(account);
            if (!login) {
                finish("Added " + generated.username + " to the account manager.", Theme.SUCCESS);
                return;
            }
            try {
                AccountStorage.loginAltening(account);
                mc.addScheduledTask(() -> {
                    AccountSkinUtil.refreshAll();
                    AccountSkinUtil.refresh(account);
                });
                finish("Added and logged in as " + account.getUsername() + ".", Theme.SUCCESS);
            } catch (Exception exception) {
                finish("Added " + generated.username + ", but login failed: " + exception.getMessage(), Theme.ERROR);
            }
        });
    }

    private void finish(String message, int color) {
        mc.addScheduledTask(() -> {
            working = false;
            setStatus(message, color);
            initGui();
        });
    }

    private void setStatus(String message, int color) {
        status = message == null ? "Unknown error." : message;
        statusColor = color;
    }

    private static String loadKey() {
        try {
            File file = DataFiles.resolve(KEY_FILE);
            return file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static void saveKey(String key) {
        try {
            File file = DataFiles.resolve(KEY_FILE);
            if (DataFiles.ensureParent(file)) {
                Files.write(file.toPath(), key.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "TheAltening", mc.getSession() != null ? mc.getSession().getUsername() : null);

        int left = Theme.left(width);
        fontRenderer.drawStringWithShadow("API Key", left, FIELD_TOP - 12, Theme.accent());
        Theme.drawField(left, FIELD_TOP, Theme.right(width), keyField.isFocused());
        keyField.drawTextBox();

        Theme.drawStatus(fontRenderer, width, height - 50, status, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            generate(false);
        } else {
            keyField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        keyField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
