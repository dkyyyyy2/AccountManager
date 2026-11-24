package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.StoredAccount;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.toasts.GuiToast;
import net.minecraft.client.gui.toasts.IToast;
import net.minecraft.client.renderer.GlStateManager;

public final class LoginToast implements IToast {
    private static final long DURATION_MS = 4000L;

    private final StoredAccount account;
    private final boolean success;
    private final String title;
    private final String subtitle;

    public LoginToast(StoredAccount account, boolean success, String title, String subtitle) {
        this.account = account;
        this.success = success;
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public Visibility draw(GuiToast toastGui, long delta) {
        FontRenderer font = toastGui.getMinecraft().fontRenderer;
        int strip = success ? Theme.accent(0xFF) : 0xFF000000 | Theme.ERROR;

        Theme.drawSoftRect(0, 0, 160, 32, 0xEC121212);
        Theme.drawSoftOutline(0, 0, 160, 32, 0x40FFFFFF);
        Gui.drawRect(1, 4, 3, 28, strip);

        if (account != null) {
            Gui.drawRect(8, 7, 26, 25, 0x66000000);
            AccountSkinUtil.drawHead(account, 9, 8, 16.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        font.drawString(font.trimStringToWidth(title, 124), 32, 7, success ? Theme.accent() : Theme.ERROR, false);
        font.drawString(font.trimStringToWidth(subtitle, 124), 32, 18, Theme.TEXT, false);
        return delta < DURATION_MS ? Visibility.SHOW : Visibility.HIDE;
    }
}
