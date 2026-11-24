package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.StoredAccount;
import accountmanager.account.TextureCache;
import accountmanager.auth.ProfileApi;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class GuiSkinChanger extends GuiScreen {
    private static final int BTN_APPLY = 0;
    private static final int BTN_VARIANT = 1;
    private static final int BTN_BACK = 2;

    private static final int FIELD_TOP = 62;
    private static final int PREVIEW_TOP = 96;

    private final GuiScreen parent;
    private final PlayerModelPreview model = new PlayerModelPreview();

    private GuiTextField urlField;
    private String status = "Paste a direct PNG skin URL.";
    private int statusColor = Theme.STATUS;
    private boolean slim;
    private boolean applying;

    public GuiSkinChanger(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        String saved = urlField != null ? urlField.getText() : "";
        boolean focused = urlField == null || urlField.isFocused();
        urlField = new GuiTextField(0, fontRenderer, Theme.left(width) + 6, FIELD_TOP + 7, Theme.HALF_WIDTH * 2 - 12, 12);
        urlField.setMaxStringLength(2048);
        urlField.setEnableBackgroundDrawing(false);
        urlField.setText(saved);
        urlField.setFocused(focused);

        int y = height - 28;
        int w = Theme.buttonWidth(3);
        GuiButton apply = new GuiButton(BTN_APPLY, Theme.buttonX(width, 0, 3), y, w, 20, applying ? "Working..." : "Apply Skin");
        GuiButton variant = new GuiButton(BTN_VARIANT, Theme.buttonX(width, 1, 3), y, w, 20, variantLabel());
        apply.enabled = !applying;
        variant.enabled = !applying;
        buttonList.add(apply);
        buttonList.add(variant);
        buttonList.add(new GuiButton(BTN_BACK, Theme.buttonX(width, 2, 3), y, w, 20, "Back"));
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public void updateScreen() {
        urlField.updateCursorCounter();
    }

    private String variantLabel() {
        return slim ? "Model: Slim" : "Model: Classic";
    }

    private String previewUrl() {
        String text = urlField.getText().trim();
        return text.startsWith("http://") || text.startsWith("https://") ? text : "";
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case BTN_APPLY:
                applySkin();
                break;
            case BTN_VARIANT:
                slim = !slim;
                button.displayString = variantLabel();
                break;
            case BTN_BACK:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void applySkin() {
        if (applying) {
            return;
        }

        Session session = mc.getSession();
        if (session == null || session.getToken() == null || session.getToken().trim().length() < 20) {
            setStatus("You need a signed-in Microsoft account to change skins.", Theme.ERROR);
            return;
        }

        String url;
        try {
            url = ProfileApi.normalizeSkinUrl(urlField.getText());
        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage(), Theme.ERROR);
            return;
        }
        urlField.setText(url);

        applying = true;
        setStatus("Applying skin...", Theme.PENDING);
        initGui();

        ProfileApi.changeSkinAsync(url, session.getToken(), slim ? "slim" : "classic",
                (success, message) -> mc.addScheduledTask(() -> {
                    applying = false;
                    initGui();
                    setStatus(message, success ? Theme.SUCCESS : Theme.ERROR);
                    if (success) {
                        StoredAccount account = AccountSkinUtil.sessionAccount();
                        AccountSkinUtil.refresh(account);
                    }
                }));
    }

    private void setStatus(String message, int color) {
        status = message == null ? "" : message;
        statusColor = color;
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
            return;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            applySkin();
            return;
        }
        urlField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        urlField.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton == 0 && overPreview(mouseX, mouseY)) {
            model.startDrag(mouseX, mouseY);
        }
    }

    private boolean overPreview(int mouseX, int mouseY) {
        return mouseX >= Theme.left(width) && mouseX < Theme.right(width) && mouseY >= PREVIEW_TOP && mouseY < height - 54;
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        model.drag(mouseX, mouseY);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        model.endDrag();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 40, height - 28));
        Theme.drawHeader(fontRenderer, width, "Skin Changer", mc.getSession() != null ? mc.getSession().getUsername() : "Unknown");

        int left = Theme.left(width);
        fontRenderer.drawStringWithShadow("Skin URL", left, FIELD_TOP - 12, Theme.accent());
        Theme.drawField(left, FIELD_TOP, Theme.right(width), urlField.isFocused());
        urlField.drawTextBox();

        drawPreview();

        Theme.drawStatus(fontRenderer, width, height - 48, status, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview() {
        int left = Theme.left(width);
        int right = Theme.right(width);
        int top = PREVIEW_TOP;
        int bottom = height - 54;
        int middle = (top + bottom) / 2 - 4;
        Theme.drawPanel(left, top, right, bottom);

        String url = previewUrl();
        ResourceLocation skin;
        if (url.isEmpty()) {
            StoredAccount current = AccountSkinUtil.sessionAccount();
            skin = current == null ? null : AccountSkinUtil.getSkinLocation(current);
        } else {
            TextureCache.Entry entry = TextureCache.get(url);
            if (!entry.isReady()) {
                drawCenteredString(fontRenderer, entry.failed ? "Could not load that image" : "Loading...",
                        width / 2, middle, Theme.MUTED);
                return;
            }
            skin = entry.location;
        }
        if (skin == null) {
            return;
        }

        int scale = Math.max(16, Math.min((bottom - top - 16) / 2, (right - left) / 2 - 10));
        int feetY = (top + bottom) / 2 + scale;
        model.render(width / 2, feetY, scale, skin, null, slim);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
