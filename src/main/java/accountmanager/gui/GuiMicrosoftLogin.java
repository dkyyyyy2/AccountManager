package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.AccountStorage;
import accountmanager.account.StoredAccount;
import accountmanager.auth.MicrosoftAuth;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.Session;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public final class GuiMicrosoftLogin extends GuiScreen {
    private static final int BTN_BROWSER = 0;
    private static final int BTN_CANCEL = 1;

    private final GuiScreen parent;
    private volatile String status = "Requesting Microsoft device code...";
    private volatile int statusColor = Theme.PENDING;
    private volatile String userCode = "";
    private volatile String verificationUri = "";
    private volatile String browserUri = "";
    private volatile boolean done;
    private volatile boolean cancelled;
    private boolean started;

    public GuiMicrosoftLogin(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int y = height - 28;
        GuiButton browser = new GuiButton(BTN_BROWSER, Theme.buttonX(width, 0, 2), y, Theme.buttonWidth(2), 20, "Open Browser");
        browser.enabled = !browserUri.isEmpty();
        buttonList.add(browser);
        buttonList.add(new GuiButton(BTN_CANCEL, Theme.buttonX(width, 1, 2), y, Theme.buttonWidth(2), 20, "Cancel"));

        if (!started) {
            started = true;
            startLogin();
        }
    }

    private void startLogin() {
        new Thread(() -> {
            try {
                MicrosoftAuth.DeviceCode code = MicrosoftAuth.requestDeviceCode();
                if (cancelled) {
                    return;
                }

                userCode = code.userCode;
                verificationUri = code.verificationUri;
                browserUri = code.verificationUriComplete;
                GuiScreen.setClipboardString(code.userCode);
                setStatus(code.verificationUriComplete.equals(code.verificationUri)
                        ? "Code copied - paste it into Microsoft:"
                        : "Opening Microsoft with the code filled in:", Theme.STATUS);
                mc.addScheduledTask(() -> buttonList.get(BTN_BROWSER).enabled = true);
                MicrosoftAuth.openBrowser(browserUri);
                MicrosoftAuth.LoginResult result = MicrosoftAuth.finishDeviceLogin(code, text -> setStatus(text, Theme.PENDING));
                if (cancelled) {
                    return;
                }

                StoredAccount account = StoredAccount.microsoft(result.username, result.uuid, result.refreshToken);
                account.setAccessToken(result.minecraftAccessToken);
                AccountStorage.add(account);
                AccountStorage.setSession(new Session(result.username, result.uuid, result.minecraftAccessToken, "mojang"));
                done = true;
                mc.addScheduledTask(() -> {
                    AccountSkinUtil.refreshAll();
                    AccountSkinUtil.refresh(account);
                    mc.displayGuiScreen(parent);
                });
            } catch (Throwable t) {
                setStatus("Login failed: " + safeMessage(t), Theme.ERROR);
            }
        }, "AccountManager-Microsoft-Login").start();
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
    }

    private static String safeMessage(Throwable t) {
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = t.getClass().getSimpleName();
        }
        return message.length() > 120 ? message.substring(0, 120) + "..." : message;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == BTN_BROWSER && !browserUri.isEmpty()) {
            if (!userCode.isEmpty()) {
                GuiScreen.setClipboardString(userCode);
            }
            MicrosoftAuth.openBrowser(browserUri);
        } else if (button.id == BTN_CANCEL) {
            cancelled = true;
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            cancelled = true;
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void onGuiClosed() {
        if (!done) {
            cancelled = true;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "Microsoft Login", mc.getSession() != null ? mc.getSession().getUsername() : null);

        int left = Theme.left(width);
        int right = Theme.right(width);
        int top = Theme.CONTENT_TOP + 20;
        int bottom = Math.min(top + 90, height - 58);
        Theme.drawPanel(left, top, right, bottom);

        int centerY = (top + bottom) / 2;
        if (userCode.isEmpty()) {
            drawCenteredString(fontRenderer, "Waiting for a code...", width / 2, centerY - 4, Theme.MUTED);
        } else {
            drawCenteredString(fontRenderer, "Your code", width / 2, top + 10, Theme.LABEL);
            GlStateManager.pushMatrix();
            GlStateManager.translate(width / 2.0F, centerY - 8, 0.0F);
            GlStateManager.scale(2.0F, 2.0F, 1.0F);
            fontRenderer.drawStringWithShadow(userCode, -fontRenderer.getStringWidth(userCode) / 2.0F, 0.0F, Theme.accent());
            GlStateManager.popMatrix();
            drawCenteredString(fontRenderer, verificationUri, width / 2, bottom - 24, Theme.LABEL);
            drawCenteredString(fontRenderer, "The code is also in your clipboard.", width / 2, bottom - 13, Theme.MUTED);
        }

        Theme.drawStatus(fontRenderer, width, height - 50, status, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
