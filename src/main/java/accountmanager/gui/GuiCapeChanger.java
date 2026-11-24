package accountmanager.gui;

import accountmanager.account.AccountSkinUtil;
import accountmanager.account.StoredAccount;
import accountmanager.account.TextureCache;
import accountmanager.auth.ProfileApi;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class GuiCapeChanger extends GuiScreen {
    private static final int BTN_APPLY = 0;
    private static final int BTN_REMOVE = 1;
    private static final int BTN_REFRESH = 2;
    private static final int BTN_BACK = 3;

    private static final int LIST_WIDTH = 186;
    private static final int PREVIEW_GAP = 4;
    private static final int ROW_ICON = 16;

    private final GuiScreen parent;
    private final PlayerModelPreview model = new PlayerModelPreview().facingAway();

    private List<ProfileApi.Cape> capes = new ArrayList<>();
    private String status = "Loading capes...";
    private int statusColor = Theme.PENDING;
    private int selectedIndex = -1;
    private int scrollOffset;
    private boolean loading = true;
    private boolean changing;

    public GuiCapeChanger(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();

        int y = height - 28;
        int w = Theme.buttonWidth(4);
        buttonList.add(new GuiButton(BTN_APPLY, Theme.buttonX(width, 0, 4), y, w, 20, "Apply"));
        buttonList.add(new GuiButton(BTN_REMOVE, Theme.buttonX(width, 1, 4), y, w, 20, "Remove"));
        buttonList.add(new GuiButton(BTN_REFRESH, Theme.buttonX(width, 2, 4), y, w, 20, "Refresh"));
        buttonList.add(new GuiButton(BTN_BACK, Theme.buttonX(width, 3, 4), y, w, 20, "Back"));
        updateButtons();

        if (loading && capes.isEmpty()) {
            loadCapes();
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private void updateButtons() {
        boolean idle = !loading && !changing;
        boolean hasSelection = selectedIndex >= 0 && selectedIndex < capes.size();
        for (GuiButton button : buttonList) {
            switch (button.id) {
                case BTN_APPLY:
                    button.enabled = idle && hasSelection && !capes.get(selectedIndex).active;
                    break;
                case BTN_REMOVE:
                    button.enabled = idle && activeIndex() >= 0;
                    break;
                case BTN_REFRESH:
                    button.enabled = idle;
                    break;
                default:
                    button.enabled = true;
                    break;
            }
        }
    }

    private int activeIndex() {
        for (int i = 0; i < capes.size(); i++) {
            if (capes.get(i).active) {
                return i;
            }
        }
        return -1;
    }

    private String token() {
        return mc.getSession() != null ? mc.getSession().getToken() : null;
    }

    private boolean requireToken() {
        String token = token();
        if (token == null || token.trim().length() < 20) {
            setStatus("You need a signed-in Microsoft account for capes.", Theme.ERROR);
            loading = false;
            updateButtons();
            return false;
        }
        return true;
    }

    private void loadCapes() {
        if (!requireToken()) {
            return;
        }

        loading = true;
        setStatus("Loading capes...", Theme.PENDING);
        updateButtons();

        ProfileApi.getCapesAsync(token(), result -> mc.addScheduledTask(() -> {
            loading = false;
            if (result.rateLimited) {
                setStatus("Rate limited, retry in " + result.retryAfterSeconds + "s.", Theme.ERROR);
            } else if (result.success) {
                capes = result.capes == null ? new ArrayList<>() : new ArrayList<>(result.capes);
                selectedIndex = activeIndex();
                if (selectedIndex < 0 && !capes.isEmpty()) {
                    selectedIndex = 0;
                }
                ensureVisible();
                setStatus(capes.isEmpty() ? "No capes on this account." : capes.size() + " cape(s) available.",
                        capes.isEmpty() ? Theme.STATUS : Theme.SUCCESS);
            } else {
                setStatus(result.errorMessage == null ? "Failed to load capes." : result.errorMessage, Theme.ERROR);
            }
            updateButtons();
        }));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button == null || !button.enabled) {
            return;
        }
        switch (button.id) {
            case BTN_APPLY:
                applyCape();
                break;
            case BTN_REMOVE:
                removeCape();
                break;
            case BTN_REFRESH:
                scrollOffset = 0;
                loadCapes();
                break;
            case BTN_BACK:
                mc.displayGuiScreen(parent);
                break;
            default:
                break;
        }
    }

    private void applyCape() {
        if (changing || selectedIndex < 0 || selectedIndex >= capes.size() || !requireToken()) {
            return;
        }
        ProfileApi.Cape cape = capes.get(selectedIndex);
        startChange("Applying " + cape.getDisplayName() + "...");
        ProfileApi.setCapeAsync(token(), cape.id, this::onChanged);
    }

    private void removeCape() {
        if (changing || !requireToken()) {
            return;
        }
        startChange("Removing cape...");
        ProfileApi.removeCapeAsync(token(), this::onChanged);
    }

    private void startChange(String message) {
        changing = true;
        setStatus(message, Theme.PENDING);
        updateButtons();
    }

    private void onChanged(boolean success, String message) {
        mc.addScheduledTask(() -> {
            changing = false;
            setStatus(message, success ? Theme.SUCCESS : Theme.ERROR);
            if (success) {
                AccountSkinUtil.refresh(AccountSkinUtil.sessionAccount());
                loadCapes();
            } else {
                updateButtons();
            }
        });
    }

    private void setStatus(String message, int color) {
        status = message == null ? "" : message;
        statusColor = color;
    }

    private int listBottom() {
        return height - 58;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - Theme.CONTENT_TOP + 2) / Theme.ROW_SPACING);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }

        int left = Theme.left(width);
        if (mouseX >= left + LIST_WIDTH + PREVIEW_GAP && mouseX < Theme.right(width)
                && mouseY >= Theme.CONTENT_TOP && mouseY < listBottom()) {
            model.startDrag(mouseX, mouseY);
            return;
        }
        if (mouseX < left || mouseX > left + LIST_WIDTH || mouseY < Theme.CONTENT_TOP || mouseY >= listBottom()) {
            return;
        }

        int row = (mouseY - Theme.CONTENT_TOP) / Theme.ROW_SPACING;
        if (mouseY - Theme.CONTENT_TOP - row * Theme.ROW_SPACING >= Theme.ROW_HEIGHT || row >= visibleRows()) {
            return;
        }
        int index = row + scrollOffset;
        if (index >= 0 && index < capes.size()) {
            selectedIndex = index;
            updateButtons();
        }
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
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, capes.size() - visibleRows());
            scrollOffset = wheel < 0 ? Math.min(maxScroll, scrollOffset + 1) : Math.max(0, scrollOffset - 1);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(parent);
        } else if (keyCode == Keyboard.KEY_UP && selectedIndex > 0) {
            selectedIndex--;
            ensureVisible();
            updateButtons();
        } else if (keyCode == Keyboard.KEY_DOWN && selectedIndex < capes.size() - 1) {
            selectedIndex++;
            ensureVisible();
            updateButtons();
        } else if ((keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) && buttonList.get(BTN_APPLY).enabled) {
            applyCape();
        }
    }

    private void ensureVisible() {
        int rows = visibleRows();
        if (selectedIndex < scrollOffset) {
            scrollOffset = Math.max(0, selectedIndex);
        } else if (selectedIndex >= scrollOffset + rows) {
            scrollOffset = selectedIndex - rows + 1;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        Theme.drawBackground(this, Theme.barY(height - 38, height - 28));
        Theme.drawHeader(fontRenderer, width, "Cape Changer", mc.getSession() != null ? mc.getSession().getUsername() : "Unknown");

        int left = Theme.left(width);
        drawCapeList(mouseX, mouseY, left, left + LIST_WIDTH);
        drawPreview(left + LIST_WIDTH + PREVIEW_GAP, Theme.right(width));

        Theme.drawStatus(fontRenderer, width, height - 50, status, statusColor);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawCapeList(int mouseX, int mouseY, int left, int right) {
        if (capes.isEmpty()) {
            Theme.drawPanel(left, Theme.CONTENT_TOP, right, listBottom());
            drawCenteredString(fontRenderer, loading ? "Loading..." : "No capes found.",
                    (left + right) / 2, Theme.CONTENT_TOP + 22, Theme.MUTED);
            return;
        }

        int rows = visibleRows();
        for (int row = 0; row < rows && row + scrollOffset < capes.size(); row++) {
            int index = row + scrollOffset;
            ProfileApi.Cape cape = capes.get(index);
            int y = Theme.CONTENT_TOP + row * Theme.ROW_SPACING;
            boolean hovered = mouseX >= left && mouseX <= right && mouseY >= y && mouseY < y + Theme.ROW_HEIGHT;
            Theme.drawRow(left, y, right, index == selectedIndex, hovered);
            if (cape.active) {
                Theme.drawActiveMarker(left, y);
            }

            drawCapeIcon(cape, left + 8, y + (Theme.ROW_HEIGHT - ROW_ICON) / 2);

            int textX = left + 8 + ROW_ICON + 6;
            String tag = cape.active ? "Equipped" : "";
            int tagWidth = fontRenderer.getStringWidth(tag);
            if (!tag.isEmpty()) {
                fontRenderer.drawString(tag, right - 6 - tagWidth, y + 7, Theme.accent());
            }
            String name = fontRenderer.trimStringToWidth(cape.getDisplayName(), Math.max(20, right - 12 - tagWidth - textX));
            fontRenderer.drawString(name, textX, y + 7, Theme.TEXT);
        }

        if (capes.size() > rows) {
            String range = (scrollOffset + 1) + "-" + Math.min(capes.size(), scrollOffset + rows) + " / " + capes.size();
            fontRenderer.drawString(range, Theme.right(width) - fontRenderer.getStringWidth(range), 30, Theme.MUTED);
        }
    }

    private void drawCapeIcon(ProfileApi.Cape cape, int x, int y) {
        TextureCache.Entry entry = TextureCache.get(cape.url);
        if (!entry.isReady()) {
            drawRect(x + 3, y + 1, x + 13, y + 15, 0x33FFFFFF);
            return;
        }
        mc.getTextureManager().bindTexture(entry.location);
        Theme.drawTextureRegion(x + 3.0F, y, 10.0F, 16.0F, 1.0F, 1.0F, 11.0F, 17.0F, 64.0F, 32.0F);
    }

    private void drawPreview(int left, int right) {
        int top = Theme.CONTENT_TOP;
        int bottom = listBottom();
        Theme.drawPanel(left, top, right, bottom);
        int centerX = (left + right) / 2;

        ProfileApi.Cape cape = selectedIndex >= 0 && selectedIndex < capes.size() ? capes.get(selectedIndex) : null;
        TextureCache.Entry capeTexture = cape == null ? null : TextureCache.get(cape.url);
        if (cape != null && !capeTexture.isReady()) {
            drawCenteredString(fontRenderer, capeTexture.failed ? "No preview" : "Loading...", centerX, (top + bottom) / 2 - 4, Theme.MUTED);
            return;
        }

        StoredAccount account = AccountSkinUtil.sessionAccount();
        if (account != null) {
            int scale = Math.max(18, Math.min(48, (bottom - top - 34) / 2));
            model.render(centerX, bottom - 22, scale, AccountSkinUtil.getSkinLocation(account),
                    capeTexture == null ? null : capeTexture.location, false);
        }

        String label = cape == null ? "No cape" : fontRenderer.trimStringToWidth(cape.getDisplayName(), right - left - 8);
        drawCenteredString(fontRenderer, label, centerX, bottom - 13, Theme.LABEL);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
