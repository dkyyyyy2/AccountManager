package accountmanager.account;

import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

public final class DataFiles {
    private static final String DIR = "accountmanager";

    private DataFiles() {
    }

    public static File resolve(String name) {
        return new File(new File(Minecraft.getMinecraft().gameDir, DIR), name);
    }

    public static boolean ensureParent(File file) {
        File parent = file.getParentFile();
        return parent.isDirectory() || parent.mkdirs();
    }

    public static Reader openText(File file) throws IOException {
        PushbackReader reader = new PushbackReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8), 1);
        int first = reader.read();
        if (first != -1 && first != '﻿') {
            reader.unread(first);
        }
        return reader;
    }
}
