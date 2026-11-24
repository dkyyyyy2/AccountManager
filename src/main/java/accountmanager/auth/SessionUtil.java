package accountmanager.auth;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Session;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

public final class SessionUtil {
    private static volatile Session originalSession;

    private SessionUtil() {
    }

    public static synchronized void captureOriginalSessionIfNeeded() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (originalSession == null && minecraft != null && minecraft.getSession() != null) {
            originalSession = copySession(minecraft.getSession());
        }
    }

    public static Session getOriginalSessionCopy() {
        captureOriginalSessionIfNeeded();
        return originalSession != null ? copySession(originalSession) : null;
    }

    public static boolean restoreOriginalSession() {
        Session original = getOriginalSessionCopy();
        if (original == null) {
            return false;
        }
        return setSession(original);
    }

    public static boolean isOriginalSessionActive() {
        captureOriginalSessionIfNeeded();
        Session current = Minecraft.getMinecraft().getSession();
        if (current == null || originalSession == null) {
            return false;
        }

        return Objects.equals(current.getUsername(), originalSession.getUsername())
                && Objects.equals(current.getPlayerID(), originalSession.getPlayerID())
                && Objects.equals(current.getToken(), originalSession.getToken());
    }

    public static boolean setSession(Session session) {
        if (session == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        captureOriginalSessionIfNeeded();
        AlteningUtil.setSessionRedirect(false);

        String[] fieldNames = new String[]{"session", "field_71449_j"};
        for (String fieldName : fieldNames) {
            try {
                Field field = Minecraft.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(minecraft, session);
                return true;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public static Session copySession(Session session) {
        if (session == null) {
            return null;
        }

        String type = resolveSessionType(session);
        return new Session(session.getUsername(), session.getPlayerID(), session.getToken(), type);
    }

    private static String resolveSessionType(Session session) {
        try {
            Method method = Session.class.getMethod("getSessionType");
            Object typeObject = method.invoke(session);
            if (typeObject != null) {
                return typeObject.toString().toLowerCase();
            }
        } catch (Exception ignored) {
        }

        return session.getToken() != null && !session.getToken().isEmpty() ? "mojang" : "legacy";
    }
}
