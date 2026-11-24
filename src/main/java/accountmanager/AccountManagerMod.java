package accountmanager;

import accountmanager.client.MenuButtons;
import accountmanager.auth.SessionUtil;
import accountmanager.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = AccountManagerMod.MODID, name = AccountManagerMod.NAME, version = AccountManagerMod.VERSION,
        clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]",
        guiFactory = "accountmanager.config.ConfigGuiFactory")
public final class AccountManagerMod {
    public static final String MODID = "accountmanager";
    public static final String NAME = "Account Manager";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ModConfig.load();
        SessionUtil.captureOriginalSessionIfNeeded();
        MinecraftForge.EVENT_BUS.register(new MenuButtons());
    }
}
