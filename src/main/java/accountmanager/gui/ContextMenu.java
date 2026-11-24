package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.StoredAccount;
import accountmanager.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

public final class ContextMenu {
    private static final int ITEM_HEIGHT = 12;
    private static final int HEADER_HEIGHT = 16;
    private static final int SEPARATOR_HEIGHT = 5;
    private static final int PAD = 6;
    private static final int MIN_WIDTH = 120;
    private static final int HEADER_GAP = 14;
    private static final int EDGE = 4;
    private static final long REVEAL_MS = 110L;

    private static final int BACKGROUND = 0xEC121212;
    private static final int SHADOW = 0x50000000;
    private static final int OUTLINE = 0x40FFFFFF;
    private static final int SEPARATOR = 0x28FFFFFF;
    private static final int ITEM_TEXT = 0xE0E0E0;
    private static final int ITEM_DISABLED = 0x5E5E5E;

    private final FontRenderer font;
    private final StoredAccount account;
    private final String title;
    private final String subtitle;
    private final List<Item> items;
    private final long openedAt = Minecraft.getSystemTime();
    private final int x;
    private final int y;
    private final int width;
    private final int height;

    private int keyIndex = -1;
    private int lastMouseX = Integer.MIN_VALUE;
    private int lastMouseY = Integer.MIN_VALUE;

    public ContextMenu(FontRenderer font, StoredAccount account, String title, String subtitle, List<Item> items,
                       int x, int y, int screenWidth, int screenHeight) {
        this.font = font;
        this.account = account;
        this.title = title;
        this.subtitle = subtitle;
        this.items = new ArrayList<>(items);

        int headerWidth = PAD + 12 + font.getStringWidth(title) + HEADER_GAP + font.getStringWidth(subtitle) + PAD;
        int widest = headerWidth;
        int total = HEADER_HEIGHT + SEPARATOR_HEIGHT;
        for (Item item : this.items) {
            if (item.separator) {
                total += SEPARATOR_HEIGHT;
                continue;
            }
            widest = Math.max(widest, PAD + 4 + font.getStringWidth(item.label) + PAD);
            total += ITEM_HEIGHT;
        }
        this.width = Math.max(MIN_WIDTH, widest);
        this.height = total + 3;

        this.x = Math.max(EDGE, Math.min(x, screenWidth - EDGE - width));
        this.y = Math.max(EDGE, Math.min(y, screenHeight - EDGE - height));
    }

    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public void draw(int mouseX, int mouseY) {
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            if (contains(mouseX, mouseY)) {
                keyIndex = -1;
            }
        }
        int highlighted = highlighted(mouseX, mouseY);

        float t = !ModConfig.animations ? 1.0F : Math.min(1.0F, (Minecraft.getSystemTime() - openedAt) / (float) REVEAL_MS);
        float eased = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t);
        int shown = Math.max(1, Math.round((height + 2) * eased));
        int clipTop = y;
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x * scale, mc.displayHeight - (clipTop + shown) * scale, (width + 2) * scale, shown * scale);

        Theme.drawSoftRect(x + 2, y + 2, x + width + 2, y + height + 2, SHADOW);
        Theme.drawSoftRect(x, y, x + width, y + height, BACKGROUND);
        Theme.drawSoftOutline(x, y, x + width, y + height, OUTLINE);

        drawHeader();

        int rowY = y + HEADER_HEIGHT + SEPARATOR_HEIGHT;
        Gui.drawRect(x + 4, rowY - 3, x + width - 4, rowY - 2, SEPARATOR);
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (item.separator) {
                Gui.drawRect(x + 4, rowY + 2, x + width - 4, rowY + 3, SEPARATOR);
                rowY += SEPARATOR_HEIGHT;
                continue;
            }
            drawItem(item, rowY, i == highlighted);
            rowY += ITEM_HEIGHT;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawHeader() {
        int textY = y + (HEADER_HEIGHT - 8) / 2 + 1;
        int textX = x + PAD;
        if (account != null) {
            Gui.drawRect(x + PAD - 1, textY - 1, x + PAD + 9, textY + 9, 0x66000000);
            AccountSkinUtil.drawHead(account, x + PAD, textY, 8.0F);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            textX += 12;
        }
        int subtitleWidth = font.getStringWidth(subtitle);
        String name = font.trimStringToWidth(title, x + width - PAD - subtitleWidth - HEADER_GAP / 2 - textX);
        font.drawString(name, textX, textY, Theme.accent(), false);
        font.drawString(subtitle, x + width - PAD - subtitleWidth, textY, Theme.MUTED, false);
    }

    private void drawItem(Item item, int top, boolean highlighted) {
        boolean live = highlighted && item.enabled;
        if (live) {
            Gui.drawRect(x + 1, top, x + width - 1, top + ITEM_HEIGHT, Theme.accent(0x26));
            Gui.drawRect(x + 1, top + 1, x + 3, top + ITEM_HEIGHT - 1, Theme.accent(0xFF));
        }
        int color = !item.enabled ? ITEM_DISABLED : live ? Theme.TEXT : ITEM_TEXT;
        font.drawString(item.label, x + PAD + 4, top + (ITEM_HEIGHT - 8) / 2 + 1, color, false);
    }

    public boolean click(int mouseX, int mouseY) {
        int index = indexAt(mouseX, mouseY);
        return index >= 0 && activate(index);
    }

    public boolean keyTyped(int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            return true;
        }
        if (keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) {
            int start = keyIndex >= 0 ? keyIndex : indexAt(lastMouseX, lastMouseY);
            keyIndex = step(start, keyCode == Keyboard.KEY_DOWN ? 1 : -1);
            return false;
        }
        if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            int index = highlighted(lastMouseX, lastMouseY);
            return index >= 0 && activate(index);
        }
        return false;
    }

    private boolean activate(int index) {
        Item item = items.get(index);
        if (item.separator || !item.enabled) {
            return false;
        }
        Theme.playClick();
        item.action.run();
        return true;
    }

    private int highlighted(int mouseX, int mouseY) {
        return keyIndex >= 0 ? keyIndex : indexAt(mouseX, mouseY);
    }

    private int step(int from, int direction) {
        int count = items.size();
        int index = from;
        for (int tries = 0; tries < count; tries++) {
            index = index < 0 ? (direction > 0 ? 0 : count - 1) : Math.floorMod(index + direction, count);
            Item item = items.get(index);
            if (!item.separator && item.enabled) {
                return index;
            }
        }
        return -1;
    }

    private int indexAt(int mouseX, int mouseY) {
        if (mouseX < x + 1 || mouseX >= x + width - 1) {
            return -1;
        }
        int rowY = y + HEADER_HEIGHT + SEPARATOR_HEIGHT;
        for (int i = 0; i < items.size(); i++) {
            int rowHeight = items.get(i).separator ? SEPARATOR_HEIGHT : ITEM_HEIGHT;
            if (mouseY >= rowY && mouseY < rowY + rowHeight) {
                return items.get(i).separator ? -1 : i;
            }
            rowY += rowHeight;
        }
        return -1;
    }

    public static final class Item {
        private final String label;
        private final Runnable action;
        private final boolean separator;
        private boolean enabled = true;

        private Item(String label, Runnable action, boolean separator) {
            this.label = label;
            this.action = action;
            this.separator = separator;
        }

        public static Item of(String label, Runnable action) {
            return new Item(label, action, false);
        }

        public static Item separator() {
            return new Item("", null, true);
        }

        public Item enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
    }
}
