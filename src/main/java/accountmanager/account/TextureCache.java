package accountmanager.account;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class TextureCache {
    private static final Map<String, Entry> ENTRIES = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TextureCache() {
    }

    public static Entry get(String url) {
        if (url == null || url.trim().isEmpty()) {
            return Entry.EMPTY;
        }

        String key = url.trim();
        Entry existing = ENTRIES.get(key);
        if (existing != null) {
            return existing;
        }

        Entry entry = new Entry();
        Entry raced = ENTRIES.putIfAbsent(key, entry);
        if (raced != null) {
            return raced;
        }

        Thread thread = new Thread(() -> download(key, entry), "AccountManager-Texture-" + COUNTER.incrementAndGet());
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
        return entry;
    }

    public static void forget(String url) {
        if (url != null) {
            ENTRIES.remove(url.trim());
        }
    }

    private static void download(String url, Entry entry) {
        try {
            BufferedImage image = fetchImage(url, 4);
            if (image == null) {
                entry.failed = true;
                return;
            }

            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                try {
                    entry.imageWidth = image.getWidth();
                    entry.imageHeight = image.getHeight();
                    entry.location = mc.getTextureManager().getDynamicTextureLocation(
                            "accountmanager_texture", new DynamicTexture(image));
                } catch (Throwable t) {
                    entry.failed = true;
                }
            });
        } catch (Throwable t) {
            entry.failed = true;
        }
    }

    private static BufferedImage fetchImage(String url, int redirectsLeft) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(8000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36");
        connection.setRequestProperty("Accept", "image/png,image/*;q=0.8,*/*;q=0.5");
        try {
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400 && redirectsLeft > 0) {
                String location = connection.getHeaderField("Location");
                if (location != null && !location.trim().isEmpty()) {
                    return fetchImage(new URL(new URL(url), location).toString(), redirectsLeft - 1);
                }
            }
            if (status != 200) {
                return null;
            }
            return ImageIO.read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    public static final class Entry {
        public static final Entry EMPTY = new Entry();

        public volatile ResourceLocation location;
        public volatile boolean failed;
        public volatile int imageWidth;
        public volatile int imageHeight;

        public boolean isReady() {
            return location != null;
        }

        public float referenceHeight() {
            return imageHeight > 0 && imageWidth == imageHeight * 2 ? 32.0F : 64.0F;
        }

        public boolean isLegacySkin() {
            return imageHeight > 0 && imageWidth == imageHeight * 2;
        }
    }
}
