package accountmanager.gui;

import accountmanager.account.AccountStorage;
import accountmanager.account.StoredAccount;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class GuiAddOfflineAccount extends GuiScreen {
    private static final int BTN_ADD = 0;
    private static final int BTN_CANCEL = 1;
    private static final int FIELD_TOP = 62;

    private final GuiScreen parent;
    private GuiTextField username;
    private String error = "";

    public GuiAddOfflineAccount(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        String saved = username != null ? username.getText() : "";
        username = new GuiTextField(1, fontRenderer, Theme.left(width) + 6, FIELD_TOP + 7, Theme.HALF_WIDTH * 2 - 12, 12);
        username.setMaxStringLength(16);
        username.setEnableBackgroundDrawing(false);
        username.setText(saved);
        username.setFocused(true);

        int y = height - 28;
        buttonList.add(new GuiButton(BTN_ADD, Theme.buttonX(width, 0, 2), y, Theme.buttonWidth(2), 20, "Add"));
        buttonList.add(new GuiButton(BTN_CANCEL, Theme.buttonX(width, 1, 2), y, Theme.buttonWidth(2), 20, "Cancel"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_CANCEL) {
            mc.displayGuiScreen(parent);
        } else if (button.id == BTN_ADD) {
            String name = username.getText().trim();
            if (!name.matches("[A-Za-z0-9_]{1,16}")) {
                error = "Username must be 1-16 letters, numbers, or underscores.";
                return;
            }
            AccountStorage.add(StoredAccount.offline(name));
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            actionPerformed(buttonList.get(BTN_ADD));
        } else {
            username.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        username.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void updateScreen() {
        username.updateCursorCounter();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "Add Offline Account", mc.getSession() != null ? mc.getSession().getUsername() : null);

        int left = Theme.left(width);
        fontRenderer.drawStringWithShadow("Username", left, FIELD_TOP - 12, Theme.accent());
        Theme.drawField(left, FIELD_TOP, Theme.right(width), username.isFocused());
        username.drawTextBox();

        Theme.drawStatus(fontRenderer, width, height - 50, error, Theme.ERROR);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
