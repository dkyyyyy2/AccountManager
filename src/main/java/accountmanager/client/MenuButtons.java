package accountmanager.client;

import accountmanager.account.AccountLogin;
import accountmanager.account.StoredAccount;
import accountmanager.config.ModConfig;
import accountmanager.gui.GuiAccountManager;
import accountmanager.gui.LoginToast;
import accountmanager.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.Session;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.List;

public final class MenuButtons {
    private static final int SNAP = 6;
    private static final int EDGE = 8;

    public enum Target {
        MAIN_MENU,
        MULTIPLAYER
    }

    private static Target editing;
    private static GuiScreen editReturn;

    private boolean startupHandled;
    private AccountButton current;
    private boolean dragging;
    private int grabX;
    private int grabY;
    private boolean snappedX;
    private boolean snappedY;

    public static void edit(Target target, GuiScreen returnTo) {
        editing = target;
        editReturn = returnTo;
        Minecraft mc = Minecraft.getMinecraft();
        mc.displayGuiScreen(target == Target.MAIN_MENU ? new GuiMainMenu() : new GuiMultiplayer(returnTo));
    }

    public static void resetPositions() {
        ModConfig.mainMenuButtonX = -1F;
        ModConfig.mainMenuButtonY = -1F;
        ModConfig.multiplayerButtonX = -1F;
        ModConfig.multiplayerButtonY = -1F;
        ModConfig.save();
    }

    @SubscribeEvent
    public void onInitGui(GuiScreenEvent.InitGuiEvent.Post event) {
        GuiScreen screen = event.getGui();
        ModConfig.ButtonLocation location = ModConfig.buttonLocation;
        if (screen instanceof GuiMainMenu) {
            loginOnStartup();
        }
        current = null;
        dragging = false;
        AccountButton button = null;
        if (screen instanceof GuiMainMenu && (location.mainMenu() || editing == Target.MAIN_MENU)) {
            int brandingLines = Math.max(1, FMLCommonHandler.instance().getBrandings(true).size());
            int leftInset = 10 + (brandingLines - 1) * 9 + 6;
            int rightInset = 18 + 6;
            button = AccountButton.create(screen.width, screen.height, 8, leftInset, rightInset);
            place(button, ModConfig.mainMenuButtonX, ModConfig.mainMenuButtonY, screen, event.getButtonList());
        } else if (screen instanceof GuiMultiplayer && (location.multiplayer() || editing == Target.MULTIPLAYER)) {
            button = AccountButton.create(screen.width, screen.height, 4, 6, 6);
            place(button, ModConfig.multiplayerButtonX, ModConfig.multiplayerButtonY, screen, event.getButtonList());
        }
        if (button != null) {
            event.getButtonList().add(button);
            current = button;
        }
    }

    private static void place(AccountButton button, float x, float y, GuiScreen screen, List<GuiButton> others) {
        if (x >= 0.0F && y >= 0.0F) {
            button.placeAt(x, y, screen.width, screen.height);
        } else {
            button.clearOf(others);
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (editing != null && event.getGui() != null && !isTarget(event.getGui())) {
            editing = null;
            editReturn = null;
        }
    }

    private static boolean isTarget(GuiScreen screen) {
        return editing == Target.MAIN_MENU ? screen instanceof GuiMainMenu
                : editing == Target.MULTIPLAYER && screen instanceof GuiMultiplayer;
    }

    @SubscribeEvent
    public void onMouse(GuiScreenEvent.MouseInputEvent.Pre event) {
        GuiScreen screen = event.getGui();
        if (editing == null || current == null || !isTarget(screen)) {
            return;
        }
        event.setCanceled(true);
        Minecraft mc = Minecraft.getMinecraft();
        int mouseX = Mouse.getEventX() * screen.width / mc.displayWidth;
        int mouseY = screen.height - Mouse.getEventY() * screen.height / mc.displayHeight - 1;
        int button = Mouse.getEventButton();
        boolean pressed = Mouse.getEventButtonState();

        if (button == 0 && pressed) {
            if (mouseX >= current.x && mouseX < current.x + current.width
                    && mouseY >= current.y && mouseY < current.y + current.buttonHeight()) {
                dragging = true;
                grabX = mouseX - current.x;
                grabY = mouseY - current.y;
            }
        } else if (button == 0) {
            if (dragging) {
                dragging = false;
                store(screen);
            }
        } else if (button == -1 && dragging) {
            move(screen, mouseX - grabX, mouseY - grabY);
        }
    }

    private void move(GuiScreen screen, int x, int y) {
        int width = current.width;
        int height = current.buttonHeight();
        x = Math.max(2, Math.min(screen.width - width - 2, x));
        y = Math.max(2, Math.min(screen.height - height - 2, y));

        snappedX = false;
        snappedY = false;
        if (Math.abs(x + width / 2 - screen.width / 2) <= SNAP) {
            x = screen.width / 2 - width / 2;
            snappedX = true;
        } else if (Math.abs(x - EDGE) <= SNAP) {
            x = EDGE;
        } else if (Math.abs(x + width - (screen.width - EDGE)) <= SNAP) {
            x = screen.width - EDGE - width;
        }
        if (Math.abs(y + height / 2 - screen.height / 2) <= SNAP) {
            y = screen.height / 2 - height / 2;
            snappedY = true;
        } else if (Math.abs(y - EDGE) <= SNAP) {
            y = EDGE;
        } else if (Math.abs(y + height - (screen.height - EDGE)) <= SNAP) {
            y = screen.height - EDGE - height;
        }
        current.x = x;
        current.y = y;
    }

    private void store(GuiScreen screen) {
        float x = current.x / (float) Math.max(1, screen.width - current.width);
        float y = current.y / (float) Math.max(1, screen.height - current.buttonHeight());
        if (editing == Target.MAIN_MENU) {
            ModConfig.mainMenuButtonX = x;
            ModConfig.mainMenuButtonY = y;
        } else {
            ModConfig.multiplayerButtonX = x;
            ModConfig.multiplayerButtonY = y;
        }
        ModConfig.save();
    }

    @SubscribeEvent
    public void onKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        GuiScreen screen = event.getGui();
        if (editing == null || !isTarget(screen)) {
            return;
        }
        int key = Keyboard.getEventKey();
        boolean handled = key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN
                || key == Keyboard.KEY_NUMPADENTER || key == Keyboard.KEY_R;
        if (!handled) {
            return;
        }
        event.setCanceled(true);
        if (!Keyboard.getEventKeyState()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (key == Keyboard.KEY_ESCAPE || key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
            GuiScreen back = editReturn;
            editing = null;
            editReturn = null;
            dragging = false;
            mc.displayGuiScreen(back);
        } else if (key == Keyboard.KEY_R) {
            if (editing == Target.MAIN_MENU) {
                ModConfig.mainMenuButtonX = -1F;
                ModConfig.mainMenuButtonY = -1F;
            } else {
                ModConfig.multiplayerButtonX = -1F;
                ModConfig.multiplayerButtonY = -1F;
            }
            ModConfig.save();
            screen.setWorldAndResolution(mc, screen.width, screen.height);
        }
    }

    @SubscribeEvent
    public void onDraw(GuiScreenEvent.DrawScreenEvent.Post event) {
        GuiScreen screen = event.getGui();
        if (editing == null || current == null || !isTarget(screen)) {
            return;
        }
        int width = current.width;
        int height = current.buttonHeight();
        if (dragging && snappedX) {
            Gui.drawRect(screen.width / 2, 0, screen.width / 2 + 1, screen.height, Theme.accent(0x90));
        }
        if (dragging && snappedY) {
            Gui.drawRect(0, screen.height / 2, screen.width, screen.height / 2 + 1, Theme.accent(0x90));
        }
        Theme.drawOutline(current.x - 2, current.y - 2, current.x + width + 2, current.y + height + 2, Theme.accent(0xFF));

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String hint = "Drag the Accounts button   R: reset   Esc: done";
        int hintWidth = font.getStringWidth(hint) + 16;
        int hintX = (screen.width - hintWidth) / 2;
        int hintY = current.y < 40 ? screen.height - 26 : 6;
        Theme.drawSoftRect(hintX, hintY, hintX + hintWidth, hintY + 18, 0xE0121212);
        Theme.drawSoftOutline(hintX, hintY, hintX + hintWidth, hintY + 18, Theme.accent(0xFF));
        font.drawString(hint, hintX + 8, hintY + 5, Theme.TEXT, false);
    }

    @SubscribeEvent
    public void onAction(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (editing != null) {
            event.setCanceled(true);
            return;
        }
        if (event.getButton() instanceof AccountButton) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiAccountManager(event.getGui()));
            event.setCanceled(true);
        }
    }

    private void loginOnStartup() {
        if (startupHandled) {
            return;
        }
        startupHandled = true;
        if (ModConfig.autoLogin != ModConfig.AutoLogin.LAST_USED) {
            return;
        }
        StoredAccount account = AccountLogin.lastUsed();
        if (account == null || isSignedInAs(account)) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();
        AccountLogin.loginAsync(account, new AccountLogin.Listener() {
            @Override
            public void onStatus(String text) {
            }

            @Override
            public void onDone(boolean success, String message) {
                if (ModConfig.loginToasts || !success) {
                    mc.getToastGui().add(new LoginToast(account, success,
                            success ? "Logged in" : "Login failed",
                            success ? account.getUsername() : message));
                }
                if (mc.currentScreen instanceof GuiMainMenu) {
                    mc.currentScreen.setWorldAndResolution(mc, mc.currentScreen.width, mc.currentScreen.height);
                }
            }
        });
    }

    private static boolean isSignedInAs(StoredAccount account) {
        Session session = Minecraft.getMinecraft().getSession();
        if (session == null || session.getPlayerID() == null) {
            return false;
        }
        String sessionId = session.getPlayerID().replace("-", "");
        String accountId = String.valueOf(account.getUuid()).replace("-", "");
        return sessionId.equalsIgnoreCase(accountId);
    }
}
