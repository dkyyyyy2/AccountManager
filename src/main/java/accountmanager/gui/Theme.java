package accountmanager.gui;

import accountmanager.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.SoundEvents;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;

import java.util.List;

public final class Theme {
    public static final int HALF_WIDTH = 150;
    public static final int BUTTON_SPAN = 308;
    public static final int BUTTON_GAP = 4;
    public static final int BUTTON_HEIGHT = 20;

    public static final int CONTENT_TOP = 42;
    public static final int ROW_HEIGHT = 22;
    public static final int ROW_SPACING = 24;

    public static final int TITLE = 0xFFFFFF;
    public static final int TEXT = 0xFFFFFF;
    public static final int LABEL = 0xAAAAAA;
    public static final int MUTED = 0x888888;
    public static final int STATUS = 0xDDDDDD;
    public static final int SUCCESS = 0x7FE08A;
    public static final int ERROR = 0xFF7070;
    public static final int PENDING = 0xF2D07C;

    public static final int ROW_IDLE = 0x44111111;
    public static final int ROW_HOVER = 0x66333333;
    public static final int ROW_SELECTED = 0x88555555;
    public static final int DIVIDER = 0x55FFFFFF;
    public static final int FRAME = 0x99FFFFFF;
    private static final int FOOTER_FADE = 20;

    private static String accentSource;
    private static int accent = 0x7AD7F0;

    private Theme() {
    }

    public static int accent() {
        if (ModConfig.rainbowAccent) {
            float hue = (Minecraft.getSystemTime() % 8000L) / 8000.0F;
            return java.awt.Color.HSBtoRGB(hue, 0.45F, 0.95F) & 0xFFFFFF;
        }
        String source = ModConfig.accentColor;
        if (source != null && !source.equals(accentSource)) {
            accentSource = source;
            try {
                accent = Integer.parseInt(source.trim().replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException e) {
                accent = 0x7AD7F0;
            }
        }
        return accent;
    }

    public static int accent(int alpha) {
        return (alpha & 0xFF) << 24 | accent();
    }

    public static int barY(int dividerY, int buttonTop) {
        return dividerY + (buttonTop - dividerY) / 2;
    }

    public static void drawBackground(GuiScreen screen, int barY) {
        int width = screen.width;
        int height = screen.height;
        switch (ModConfig.background) {
            case SHADER:
                Gui.drawRect(0, 0, width, barY, 0xFF000000);
                if (!ShaderBackground.draw(0, 0, width, barY, ModConfig.shaderOpacity / 100.0F)) {
                    drawGradient(0, 0, width, height, 0xFF141418, 0xFF0B0B0E);
                }
                break;
            case DARK:
                drawGradient(0, 0, width, height, 0xFF141418, 0xFF0B0B0E);
                break;
            case ACCENT:
                drawGradient(0, 0, width, height, 0xFF141418, 0xFF0B0B0E);
                drawGradient(0, 0, width, height / 2, accent(0x2A), accent(0x00));
                break;
            default:
                screen.drawDefaultBackground();
                break;
        }
        drawFooter(width, height, barY);
    }

    private static void drawFooter(int width, int height, int barY) {
        for (int i = 0; i < FOOTER_FADE; i++) {
            float t = (i + 1) / (float) FOOTER_FADE;
            float eased = t * t * (3.0F - 2.0F * t);
            int y = barY - FOOTER_FADE + i;
            Gui.drawRect(0, y, width, y + 1, Math.round(255 * eased) << 24);
        }
        Gui.drawRect(0, barY, width, height, 0xFF000000);
        Gui.drawRect(0, barY, width, barY + 1, DIVIDER);
    }

    public static void drawGradient(int left, int top, int right, int bottom, int topColor, int bottomColor) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.shadeModel(GL_SMOOTH);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        color(buffer.pos(right, top, 0.0D), topColor).endVertex();
        color(buffer.pos(left, top, 0.0D), topColor).endVertex();
        color(buffer.pos(left, bottom, 0.0D), bottomColor).endVertex();
        color(buffer.pos(right, bottom, 0.0D), bottomColor).endVertex();
        tessellator.draw();
        GlStateManager.shadeModel(GL_FLAT);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static final int GL_SMOOTH = 0x1D01;
    private static final int GL_FLAT = 0x1D00;

    private static BufferBuilder color(BufferBuilder buffer, int argb) {
        return buffer.color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24);
    }

    public static float approach(float current, float target, float seconds) {
        if (!ModConfig.animations) {
            return target;
        }
        float step = Math.min(0.1F, seconds) * 7.0F;
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    public static void playClick() {
        if (ModConfig.uiSounds) {
            Minecraft.getMinecraft().getSoundHandler()
                    .playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    public static int controlOutline(boolean hovered) {
        return hovered ? 0xAAFFFFFF : 0x55FFFFFF;
    }

    public static void drawControlBox(int left, int top, int right, int bottom, int outline) {
        Gui.drawRect(left, top, right, bottom, 0x66000000);
        drawOutline(left, top, right, bottom, outline);
    }

    public static void drawCheckbox(int right, int top, float on, boolean hovered) {
        int left = right - 10;
        drawControlBox(left, top, right, top + 10, blend(controlOutline(hovered), accent(0xFF), on));
        if (on > 0.05F) {
            int inset = 2 + Math.round(3.0F * (1.0F - on));
            Gui.drawRect(left + inset, top + inset, right - inset, top + 10 - inset, accent(0xFF));
        }
    }

    private static final String[] COG = {
            "....####....",
            ".##.####.##.",
            ".##########.",
            "..########..",
            "####....####",
            "####....####",
            "####....####",
            "####....####",
            "..########..",
            ".##########.",
            ".##.####.##.",
            "....####....",
    };

    private static final String[] DOTS = {
            "............",
            "............",
            "............",
            "............",
            "............",
            ".##..##..##.",
            ".##..##..##.",
            "............",
            "............",
            "............",
            "............",
            "............",
    };

    public static void drawCog(int x, int y, int color) {
        drawGlyph(COG, x, y, color);
    }

    public static void drawDots(int x, int y, int color) {
        drawGlyph(DOTS, x, y, color);
    }

    private static void drawGlyph(String[] rows, int x, int y, int color) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int start = -1;
            for (int col = 0; col <= line.length(); col++) {
                boolean filled = col < line.length() && line.charAt(col) == '#';
                if (filled && start < 0) {
                    start = col;
                } else if (!filled && start >= 0) {
                    Gui.drawRect(x + start, y + row, x + col, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }

    public static int left(int screenWidth) {
        return screenWidth / 2 - HALF_WIDTH;
    }

    public static int right(int screenWidth) {
        return screenWidth / 2 + HALF_WIDTH;
    }

    public static int buttonX(int screenWidth, int index, int count) {
        return screenWidth / 2 - BUTTON_SPAN / 2 + index * (buttonWidth(count) + BUTTON_GAP);
    }

    public static int buttonWidth(int count) {
        return (BUTTON_SPAN - BUTTON_GAP * (count - 1)) / count;
    }

    public static void drawHeader(FontRenderer font, int screenWidth, String title, String subtitle) {
        font.drawStringWithShadow(title, screenWidth / 2 - font.getStringWidth(title) / 2, 15, TITLE);
        if (subtitle != null) {
            font.drawStringWithShadow(subtitle, screenWidth / 2 - font.getStringWidth(subtitle) / 2, 28, accent());
        }
    }

    public static void drawRow(int left, int top, int right, boolean selected, boolean hovered) {
        Gui.drawRect(left, top, right, top + ROW_HEIGHT, selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW_IDLE);
    }

    public static void drawActiveMarker(int left, int top) {
        Gui.drawRect(left, top, left + 2, top + ROW_HEIGHT, accent(0xFF));
    }

    public static void drawPanel(int left, int top, int right, int bottom) {
        Gui.drawRect(left, top, right, bottom, ROW_IDLE);
    }

    public static void drawField(int left, int top, int right, boolean focused) {
        Gui.drawRect(left, top, right, top + ROW_HEIGHT, focused ? ROW_HOVER : ROW_IDLE);
        if (focused) {
            Gui.drawRect(left, top + ROW_HEIGHT - 1, right, top + ROW_HEIGHT, accent(0xFF));
        }
    }

    public static void drawStatus(FontRenderer font, int screenWidth, int lastLineY, String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }
        List<String> lines = font.listFormattedStringToWidth(text, HALF_WIDTH * 2);
        int y = lastLineY - (lines.size() - 1) * 10;
        for (String line : lines) {
            font.drawStringWithShadow(line, screenWidth / 2 - font.getStringWidth(line) / 2, y, color);
            y += 10;
        }
    }

    public static void drawOutline(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left, top, right, top + 1, color);
        Gui.drawRect(left, bottom - 1, right, bottom, color);
        Gui.drawRect(left, top, left + 1, bottom, color);
        Gui.drawRect(right - 1, top, right, bottom, color);
    }

    public static void drawIconFrame(int x, int y, int size, boolean hovered, boolean enabled) {
        Gui.drawRect(x, y, x + size, y + size, hovered ? ROW_HOVER : ROW_IDLE);
        int border = !enabled ? 0x44FFFFFF : hovered ? accent(0xFF) : FRAME;
        drawOutline(x, y, x + size, y + size, border);
    }

    public static void drawSoftRect(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left + 1, top, right - 1, bottom, color);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, color);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, color);
    }

    public static void drawSoftOutline(int left, int top, int right, int bottom, int color) {
        Gui.drawRect(left + 1, top, right - 1, top + 1, color);
        Gui.drawRect(left + 1, bottom - 1, right - 1, bottom, color);
        Gui.drawRect(left, top + 1, left + 1, bottom - 1, color);
        Gui.drawRect(right - 1, top + 1, right, bottom - 1, color);
    }

    public static int blend(int from, int to, float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = from >>> shift & 0xFF;
            int b = to >>> shift & 0xFF;
            result |= Math.round(a + (b - a) * clamped) << shift;
        }
        return result;
    }

    public static void drawTextureRegion(float x, float y, float w, float h,
                                         float u0, float v0, float u1, float v1,
                                         float refWidth, float refHeight) {
        GlStateManager.enableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX);
        buffer.pos(x, y + h, 0.0D).tex(u0 / refWidth, v1 / refHeight).endVertex();
        buffer.pos(x + w, y + h, 0.0D).tex(u1 / refWidth, v1 / refHeight).endVertex();
        buffer.pos(x + w, y, 0.0D).tex(u1 / refWidth, v0 / refHeight).endVertex();
        buffer.pos(x, y, 0.0D).tex(u0 / refWidth, v0 / refHeight).endVertex();
        tessellator.draw();

        GlStateManager.disableBlend();
    }
}
