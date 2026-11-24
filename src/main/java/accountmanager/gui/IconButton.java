package accountmanager.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

final class IconButton extends GuiButton {
    private final String tooltip;
    private float hover;
    private long lastFrame;

    IconButton(int id, int x, int y, String tooltip) {
        super(id, x, y, 20, 20, "");
        this.tooltip = tooltip;
    }

    String getTooltip() {
        return tooltip;
    }

    boolean isMouseOver(int mouseX, int mouseY) {
        return visible && mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
        if (!visible) {
            return;
        }
        hovered = enabled && isMouseOver(mouseX, mouseY);
        long now = Minecraft.getSystemTime();
        float elapsed = lastFrame == 0L ? 0.0F : (now - lastFrame) / 1000.0F;
        lastFrame = now;
        hover = Theme.approach(hover, hovered ? 1.0F : 0.0F, elapsed);

        Theme.drawSoftRect(x, y, x + width, y + height, Theme.blend(0x90000000, 0xC0101010, hover));
        Theme.drawSoftOutline(x, y, x + width, y + height, Theme.blend(0x30FFFFFF, Theme.accent(0xFF), hover));

        int color = !enabled ? 0xFF555555 : Theme.blend(0xFFBBBBBB, Theme.accent(0xFF), hover);
        int gx = x + (width - 12) / 2;
        int gy = y + (height - 12) / 2;
        GlStateManager.pushMatrix();
        GlStateManager.translate(gx + 6.0F, gy + 6.0F, 0.0F);
        GlStateManager.rotate(90.0F * hover, 0.0F, 0.0F, 1.0F);
        GlStateManager.translate(-6.0F, -6.0F, 0.0F);
        Theme.drawCog(0, 0, color);
        GlStateManager.popMatrix();
    }
}
