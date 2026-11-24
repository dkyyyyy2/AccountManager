package accountmanager.gui;

import accountmanager.AccountManagerMod;
import accountmanager.account.DataFiles;
import accountmanager.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLContext;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ShaderBackground {
    public static final String BUILT_IN = "Galactic Nebula";
    private static final String BUILT_IN_RESOURCE = "/assets/accountmanager/shaders/galactic_nebula.frag";
    private static final String[] EXTENSIONS = {".frag", ".fsh", ".glsl"};
    private static final long START = System.nanoTime();

    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+(\\d+)[^\\n]*\\n?");
    private static final Pattern OUTPUT = Pattern.compile("(?m)^\\s*(?:layout\\s*\\([^)]*\\)\\s*)?out\\s+vec4\\s+(\\w+)\\s*;[^\\n]*\\n?");
    private static final Pattern MAIN = Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)");
    private static final Pattern PRECISION = Pattern.compile("(?m)^\\s*precision\\s+\\w+\\s+\\w+\\s*;[^\\n]*\\n?");

    private static String loadedName;
    private static long loadedStamp;
    private static int program;
    private static String error = "";
    private static final Map<String, Integer> TYPES = new HashMap<>();
    private static final Map<String, Integer> LOCATIONS = new HashMap<>();
    private static String scannedFor;
    private static File scannedFile;
    private static long scannedAt;

    private ShaderBackground() {
    }

    public static File getFolder() {
        File dir = DataFiles.resolve("shaders");
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<String> available() {
        List<String> names = new ArrayList<>();
        names.add(BUILT_IN);
        File[] files = getFolder().listFiles((dir, name) -> shaderName(name) != null);
        if (files != null) {
            List<String> custom = new ArrayList<>();
            for (File file : files) {
                custom.add(shaderName(file.getName()));
            }
            custom.sort(String.CASE_INSENSITIVE_ORDER);
            names.addAll(custom);
        }
        return names;
    }

    public static String error() {
        return error;
    }

    public static boolean draw(int left, int top, int right, int bottom, float alpha) {
        if (!GLContext.getCapabilities().OpenGL20) {
            error = "Your graphics driver does not support shaders.";
            return false;
        }
        String wanted = ModConfig.shader == null || ModConfig.shader.isEmpty() ? BUILT_IN : ModConfig.shader;
        File file = fileFor(wanted);
        long stamp = file == null ? 0L : file.lastModified();
        if (!wanted.equals(loadedName) || stamp != loadedStamp) {
            load(wanted, file, stamp);
        }
        if (program == 0 || bottom <= top) {
            return program != 0;
        }

        Minecraft mc = Minecraft.getMinecraft();
        GL20.glUseProgram(program);
        float time = (System.nanoTime() - START) / 1.0E9F;
        float width = mc.displayWidth;
        float height = mc.displayHeight;
        set("u_Time", time);
        set("iTime", time);
        set("time", time);
        set("u_Resolution", width, height, 1.0F);
        set("iResolution", width, height, 1.0F);
        set("resolution", width, height, 1.0F);
        set("u_Mouse", 0.0F, 0.0F, 0.0F, 0.0F);
        set("iMouse", 0.0F, 0.0F, 0.0F, 0.0F);
        set("mouse", 0.0F, 0.0F, 0.0F, 0.0F);
        set("am_Alpha", Math.max(0.0F, Math.min(1.0F, alpha)));

        GlStateManager.disableTexture2D();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION);
        buffer.pos(left, bottom, 0.0D).endVertex();
        buffer.pos(right, bottom, 0.0D).endVertex();
        buffer.pos(right, top, 0.0D).endVertex();
        buffer.pos(left, top, 0.0D).endVertex();
        tessellator.draw();
        GL20.glUseProgram(0);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
        return true;
    }

    private static void load(String name, File file, long stamp) {
        if (program != 0) {
            GL20.glDeleteProgram(program);
            program = 0;
        }
        TYPES.clear();
        LOCATIONS.clear();
        loadedName = name;
        loadedStamp = stamp;
        error = "";

        String source = read(name, file);
        if (source == null) {
            error = "Could not find the shader " + name + ".";
            return;
        }
        program = build(source, false);
        if (program == 0) {
            String modernError = error;
            program = build(source, true);
            if (program == 0) {
                error = modernError;
                AccountManagerMod.LOGGER.warn("Shader {} failed to compile: {}", name, modernError);
            } else {
                error = "";
            }
        }
    }

    private static int build(String source, boolean legacy) {
        String fragment;
        String vertex;
        try {
            int version = versionOf(source);
            String header = legacy ? "#version 120\n" : "#version " + Math.max(150, version) + " compatibility\n";
            fragment = prepare(source, header, legacy);
            vertex = header + "void main() { gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex; }\n";
        } catch (RuntimeException e) {
            error = "Could not read the shader.";
            return 0;
        }

        int vert = compile(GL20.GL_VERTEX_SHADER, vertex);
        if (vert == 0) {
            return 0;
        }
        int frag = compile(GL20.GL_FRAGMENT_SHADER, fragment);
        if (frag == 0) {
            GL20.glDeleteShader(vert);
            return 0;
        }
        int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vert);
        GL20.glAttachShader(linked, frag);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            error = firstLine(GL20.glGetProgramInfoLog(linked, 2048));
            GL20.glDeleteProgram(linked);
            return 0;
        }

        int count = GL20.glGetProgrami(linked, GL20.GL_ACTIVE_UNIFORMS);
        for (int i = 0; i < count; i++) {
            String uniform = GL20.glGetActiveUniform(linked, i, 256);
            int bracket = uniform.indexOf('[');
            if (bracket >= 0) {
                uniform = uniform.substring(0, bracket);
            }
            TYPES.put(uniform, GL20.glGetActiveUniformType(linked, i));
            LOCATIONS.put(uniform, GL20.glGetUniformLocation(linked, uniform));
        }
        return linked;
    }

    private static String prepare(String source, String header, boolean legacy) {
        String body = VERSION.matcher(source).replaceFirst("");
        boolean shadertoy = body.contains("mainImage") && !MAIN.matcher(body).find();

        String output = null;
        Matcher out = OUTPUT.matcher(body);
        if (out.find()) {
            output = out.group(1);
            if (legacy) {
                body = out.replaceFirst("");
            }
        }
        if (legacy) {
            body = PRECISION.matcher(body).replaceAll("");
            if (output != null) {
                body = body.replaceAll("\\b" + Pattern.quote(output) + "\\b", "gl_FragColor");
            }
            body = body.replaceAll("\\btexture\\s*\\(", "texture2D(");
            output = "gl_FragColor";
        }

        StringBuilder builder = new StringBuilder(header);
        if (shadertoy) {
            if (!body.contains("iResolution")) {
                builder.append("uniform vec3 iResolution;\n");
            }
            if (!body.contains("uniform float iTime")) {
                builder.append("uniform float iTime;\n");
            }
            if (!body.contains("uniform vec4 iMouse")) {
                builder.append("uniform vec4 iMouse;\n");
            }
        }
        if (output == null) {
            if (legacy) {
                output = "gl_FragColor";
            } else {
                output = "am_FragColor";
                builder.append("out vec4 am_FragColor;\n");
            }
        }
        builder.append("uniform float am_Alpha;\n");

        if (shadertoy) {
            builder.append(body).append('\n');
            builder.append("void main() {\n    vec4 am_color = vec4(0.0, 0.0, 0.0, 1.0);\n    mainImage(am_color, gl_FragCoord.xy);\n    ")
                    .append(output).append(" = vec4(am_color.rgb, am_Alpha);\n}\n");
        } else {
            builder.append(MAIN.matcher(body).replaceFirst("void am_user_main()")).append('\n');
            builder.append("void main() {\n    am_user_main();\n    ")
                    .append(output).append(" = vec4(").append(output).append(".rgb, clamp(")
                    .append(output).append(".a, 0.0, 1.0) * am_Alpha);\n}\n");
        }
        return builder.toString();
    }

    private static int compile(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            error = firstLine(GL20.glGetShaderInfoLog(shader, 2048));
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void set(String name, float... values) {
        Integer location = LOCATIONS.get(name);
        Integer type = TYPES.get(name);
        if (location == null || location < 0 || type == null) {
            return;
        }
        switch (type) {
            case GL11.GL_FLOAT:
                GL20.glUniform1f(location, values[0]);
                break;
            case GL20.GL_FLOAT_VEC2:
                GL20.glUniform2f(location, values[0], values.length > 1 ? values[1] : 0.0F);
                break;
            case GL20.GL_FLOAT_VEC3:
                GL20.glUniform3f(location, values[0], values.length > 1 ? values[1] : 0.0F, values.length > 2 ? values[2] : 0.0F);
                break;
            case GL20.GL_FLOAT_VEC4:
                GL20.glUniform4f(location, values[0], values.length > 1 ? values[1] : 0.0F,
                        values.length > 2 ? values[2] : 0.0F, values.length > 3 ? values[3] : 0.0F);
                break;
            default:
                break;
        }
    }

    private static int versionOf(String source) {
        Matcher matcher = VERSION.matcher(source);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private static String read(String name, File file) {
        try {
            if (file != null) {
                return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            }
            if (!BUILT_IN.equals(name)) {
                return null;
            }
            try (InputStream in = ShaderBackground.class.getResourceAsStream(BUILT_IN_RESOURCE)) {
                if (in == null) {
                    return null;
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    bytes.write(buffer, 0, read);
                }
                return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static File fileFor(String name) {
        if (BUILT_IN.equals(name)) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (!name.equals(scannedFor) || now - scannedAt > 1000L) {
            File[] files = getFolder().listFiles((dir, file) -> name.equals(shaderName(file)));
            scannedFor = name;
            scannedFile = files != null && files.length > 0 ? files[0] : null;
            scannedAt = now;
        }
        return scannedFile;
    }

    private static String shaderName(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        for (String extension : EXTENSIONS) {
            if (lower.endsWith(extension)) {
                String base = fileName.substring(0, fileName.length() - extension.length());
                int dot = base.indexOf('.');
                return dot > 0 ? base.substring(0, dot) : base;
            }
        }
        return null;
    }

    private static String firstLine(String log) {
        String text = log == null ? "" : log.trim();
        int newline = text.indexOf('\n');
        String line = newline >= 0 ? text.substring(0, newline) : text;
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }
}
