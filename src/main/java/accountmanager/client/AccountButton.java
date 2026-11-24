package accountmanager.client;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.StoredAccount;
import accountmanager.config.ModConfig;
import accountmanager.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

public final class AccountButton extends GuiButton {
    public static final int ID = 0x41434D;
    private static final String LABEL = "Accounts";
    private static final int MARGIN = 8;
    private static final int FANCY_HEIGHT = 26;
    private static final int FANCY_MAX_NAME = 96;
    private static final int VANILLA_HEIGHT = 20;

    private final ModConfig.ButtonStyle style;
    private final int fallbackTop;
    private float hover;
    private long lastFrame;

    private AccountButton(int x, int y, int width, int height, ModConfig.ButtonStyle style, int fallbackTop) {
        super(ID, x, y, width, height, LABEL);
        this.style = style;
        this.fallbackTop = fallbackTop;
    }

    public static AccountButton create(int screenWidth, int screenHeight, int top, int bottomLeftInset, int bottomRightInset) {
        ModConfig.ButtonStyle style = ModConfig.buttonStyle;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width;
        int height;
        switch (style) {
            case MINIMAL:
                width = font.getStringWidth(LABEL) + 12;
                height = 16;
                break;
            case FANCY:
                int nameWidth = Math.min(FANCY_MAX_NAME, font.getStringWidth(currentName()));
                width = Math.max(110, 26 + Math.max(nameWidth, font.getStringWidth(LABEL)) + 10);
                height = FANCY_HEIGHT;
                break;
            default:
                width = 100;
                height = 20;
                break;
        }
        ModConfig.ButtonCorner corner = ModConfig.buttonCorner;
        int x = corner.right() ? screenWidth - width - MARGIN : MARGIN;
        int inset = corner.right() ? bottomRightInset : bottomLeftInset;
        int y = corner.bottom() ? screenHeight - height - inset : top;
        return new AccountButton(x, y, width, height, style, top);
    }

    public AccountButton placeAt(float fractionX, float fractionY, int screenWidth, int screenHeight) {
        x = Math.round(fractionX * Math.max(0, screenWidth - width));
        y = Math.round(fractionY * Math.max(0, screenHeight - height));
        return this;
    }

    public int buttonHeight() {
        return height;
    }

    public AccountButton clearOf(List<GuiButton> others) {
        if (overlap(others) != null && ModConfig.buttonCorner.bottom()) {
            y = fallbackTop;
        }
        for (int pass = 0; pass < others.size(); pass++) {
            GuiButton hit = overlap(others);
            if (hit == null) {
                break;
            }
            y = hit.y + VANILLA_HEIGHT + 4;
        }
        return this;
    }

    private GuiButton overlap(List<GuiButton> others) {
        for (GuiButton other : others) {
            if (other != this && other.visible && x < other.x + other.width && other.x < x + width
                    && y < other.y + VANILLA_HEIGHT && other.y < y + height) {
                return other;
            }
        }
        return null;
    }

    private static String currentName() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc.getSession() != null && mc.getSession().getUsername() != null ? mc.getSession().getUsername() : "Unknown";
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
        animate();

        switch (style) {
            case VANILLA:
                super.drawButton(mc, mouseX, mouseY, partialTicks);
                break;
            case MINIMAL:
                drawMinimal(mc.fontRenderer);
                break;
            case MODERN:
                drawModern(mc.fontRenderer);
                break;
            default:
                drawFancy(mc.fontRenderer);
                break;
        }
    }

    private void animate() {
        long now = Minecraft.getSystemTime();
        float elapsed = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        hover = Math.max(0.0F, Math.min(1.0F, hover + (hovered ? elapsed : -elapsed) * 7.0F));
    }

    private void drawMinimal(FontRenderer font) {
        int textWidth = font.getStringWidth(LABEL);
        int textX = x + (width - textWidth) / 2;
        int textY = y + (height - 8) / 2;
        font.drawStringWithShadow(LABEL, textX, textY, Theme.blend(0xFFBBBBBB, 0xFFFFFFFF, hover));

        int lineHalf = Math.round(textWidth / 2.0F * hover);
        if (lineHalf > 0) {
            int center = textX + textWidth / 2;
            drawRect(center - lineHalf, textY + 10, center + lineHalf, textY + 11, Theme.accent(0xFF));
        }
    }

    private void drawModern(FontRenderer font) {
        Theme.drawSoftRect(x, y, x + width, y + height, Theme.blend(0x90000000, 0xC0101010, hover));
        Theme.drawSoftOutline(x, y, x + width, y + height, Theme.blend(0x30FFFFFF, 0x50FFFFFF, hover));

        int lineHalf = Math.round((width / 2.0F - 6) * hover);
        if (lineHalf > 0) {
            int center = x + width / 2;
            drawRect(center - lineHalf, y + height - 2, center + lineHalf, y + height - 1, Theme.accent(0xFF));
        }

        int color = Theme.blend(0xFFE0E0E0, Theme.accent(0xFF), hover * 0.6F);
        font.drawStringWithShadow(LABEL, x + (width - font.getStringWidth(LABEL)) / 2, y + (height - 8) / 2, color);
    }

    private void drawFancy(FontRenderer font) {
        if (hover > 0.0F) {
            int glow = Theme.accent(Math.round(40 * hover));
            Theme.drawSoftRect(x - 2, y - 2, x + width + 2, y + height + 2, glow);
            Theme.drawSoftRect(x - 1, y - 1, x + width + 1, y + height + 1, glow);
        }
        Theme.drawSoftRect(x, y, x + width, y + height, Theme.blend(0xB0101010, 0xD0181818, hover));
        Theme.drawSoftOutline(x, y, x + width, y + height,
                Theme.blend(0x40FFFFFF, Theme.accent(0xFF), hover));
        drawRect(x + 1, y + 3, x + 3, y + height - 3, Theme.accent(Math.round(120 + 135 * hover)));

        StoredAccount account = AccountSkinUtil.sessionAccount();
        if (account != null) {
            drawRect(x + 6, y + 4, x + 24, y + 22, 0x66000000);
            AccountSkinUtil.drawHead(account, x + 7, y + 5, 16.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        }

        String name = font.trimStringToWidth(currentName(), FANCY_MAX_NAME);
        font.drawStringWithShadow(name, x + 29, y + 4, 0xFFFFFFFF);
        font.drawString(LABEL, x + 29, y + 14, Theme.blend(0xFF8A8A8A, Theme.accent(0xFF), hover));
    }
}
