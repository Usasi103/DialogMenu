import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;

import javax.imageio.ImageIO;

/** Compiles the real composed pack shaders and renders their cursor with supplied view matrices. */
public class FullscreenShaderProbe {
    static ZipFile pack, client;
    static final int ATLAS = 2048;
    static int columns, rows, tileWidth, tileHeight, border;
    static BufferedImage guiSource;

    static void checkSeams(ByteBuffer pixels, int width, int height) {
        int samples = 0;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                boolean seam = x == 0 || x == width - 1 || y == 0 || y == height - 1;
                for (int c = 1; c < columns; c++)
                    seam |= Math.abs(x + .5 - c * width / (double) columns) < 1;
                for (int r = 1; r < rows; r++)
                    seam |= Math.abs(y + .5 - r * height / (double) rows) < 1;
                if (!seam) continue;
                if (Math.abs((x + .5) * 320 / width - 220) < 6
                        && Math.abs((y + .5) * 180 / height - 100) < 6) continue;
                double u = (x + .5) * guiSource.getWidth() / width - .5;
                double v = (height - y - .5) * guiSource.getHeight() / height - .5;
                int x0 = (int) Math.floor(u), y0 = (int) Math.floor(v);
                double fx = u - x0, fy = v - y0;
                for (int channel = 0; channel < 3; channel++) {
                    double expected = 0;
                    for (int dy = 0; dy < 2; dy++)
                        for (int dx = 0; dx < 2; dx++) {
                            int c =
                                    guiSource.getRGB(
                                            Math.clamp(x0 + dx, 0, guiSource.getWidth() - 1),
                                            Math.clamp(y0 + dy, 0, guiSource.getHeight() - 1));
                            expected +=
                                    ((c >> (16 - channel * 8)) & 255)
                                            * (dx == 0 ? 1 - fx : fx)
                                            * (dy == 0 ? 1 - fy : fy);
                        }
                    int actual = pixels.get((y * width + x) * 4 + channel) & 255;
                    if (Math.abs(actual - expected) > 2)
                        throw new AssertionError(
                                "tile seam/edge "
                                        + width
                                        + "x"
                                        + height
                                        + " at "
                                        + x
                                        + ","
                                        + y
                                        + " channel="
                                        + channel
                                        + " actual="
                                        + actual
                                        + " expected="
                                        + expected);
                }
                samples++;
            }
        System.out.println(
                "PASS: "
                        + width
                        + "x"
                        + height
                        + " tile seams and viewport edges match unsplit source ("
                        + samples
                        + " pixels)");
    }

    static int value(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(\\d+)").matcher(json);
        if (!m.find()) throw new AssertionError("Missing layout field " + key);
        return Integer.parseInt(m.group(1));
    }

    static float[] vertices(double yaw, double pitch, int shift) {
        double a = Math.toRadians(yaw), b = Math.toRadians(pitch);
        double[] right = {-Math.cos(a), -Math.sin(b) * Math.sin(a), Math.cos(b) * Math.sin(a)};
        double[] up = {0, Math.cos(b), Math.sin(b)};
        float[] data = new float[columns * rows * 36];
        float[][] corners = {{-1, 1}, {-1, -1}, {1, -1}, {1, 1}};
        int k = 0;
        for (int tile = 0; tile < columns * rows; tile++) {
            int ox = 23 + tile % columns * 250, oy = 49 + tile / columns * 230;
            for (int v = 0; v < 4; v++) {
                float[] corner = corners[(v + shift) % 4];
                for (int j = 0; j < 3; j++)
                    data[k++] =
                            (float)
                                    (right[j] * (corner[0] * .32 + tile * .7)
                                            + up[j] * corner[1] * .18
                                            + (j == 2 ? -1 : 0));
                data[k++] = 253f / 255;
                data[k++] = 23f / 255;
                data[k++] = 171f / 255;
                data[k++] = 1;
                data[k++] = (ox + (corner[0] < 0 ? .01f : tileWidth + border * 2 - .01f)) / ATLAS;
                data[k++] = (oy + (corner[1] > 0 ? .01f : tileHeight + border * 2 - .01f)) / ATLAS;
            }
        }
        return data;
    }

    static String read(String key) throws Exception {
        ZipFile source = pack.getEntry(key) != null ? pack : client;
        return new String(
                source.getInputStream(source.getEntry(key)).readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    static String expand(String source, Set<String> seen) throws Exception {
        Matcher matcher = Pattern.compile("#include <([^:>]+):([^>]+)>").matcher(source);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = "assets/" + matcher.group(1) + "/shaders/include/" + matcher.group(2);
            matcher.appendReplacement(
                    out,
                    Matcher.quoteReplacement(
                            seen.add(key)
                                    ? expand(read(key).replaceAll("(?m)^#version[^\\n]*", ""), seen)
                                    : ""));
        }
        return matcher.appendTail(out).toString();
    }

    static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0)
            throw new AssertionError(glGetShaderInfoLog(shader));
        return shader;
    }

    static int program(String defines) throws Exception {
        return program(defines, "text");
    }

    static int program(String defines, String core) throws Exception {
        String v =
                expand(read("assets/minecraft/shaders/core/" + core + ".vsh"), new HashSet<>())
                        .replace("#version 330", "#version 330\n" + defines);
        String f =
                expand(read("assets/minecraft/shaders/core/" + core + ".fsh"), new HashSet<>())
                        .replace("#version 330", "#version 330\n" + defines);
        int vs = compile(GL_VERTEX_SHADER, v), fs = compile(GL_FRAGMENT_SHADER, f);
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0)
            throw new AssertionError(glGetProgramInfoLog(program));
        glDeleteShader(vs);
        glDeleteShader(fs);
        return program;
    }

    static void uniform(int program, String name, int binding, float[] data) {
        int index = glGetUniformBlockIndex(program, name);
        if (index == GL_INVALID_INDEX) return;
        glUniformBlockBinding(program, index, binding);
        int buffer = glGenBuffers();
        glBindBuffer(GL_UNIFORM_BUFFER, buffer);
        glBufferData(GL_UNIFORM_BUFFER, data, GL_STATIC_DRAW);
        glBindBufferBase(GL_UNIFORM_BUFFER, binding, buffer);
    }

    static void attribute(int program, String name, int size, int offset) {
        int at = glGetAttribLocation(program, name);
        if (at < 0) return;
        glEnableVertexAttribArray(at);
        glVertexAttribPointer(at, size, GL_FLOAT, false, 36, offset * 4L);
    }

    static void antialiasCases(int program, int vbo, Path preview) throws Exception {
        int framebuffer = glGenFramebuffers(), target = glGenTextures();
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glActiveTexture(GL_TEXTURE3);
        glBindTexture(GL_TEXTURE_2D, target);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        glDisable(GL_DEPTH_TEST);
        glUseProgram(program);
        int count = 0;
        for (int[] size :
                new int[][] {
                    {640, 360},
                    {960, 540},
                    {1566, 882},
                    {1920, 1080},
                    {1024, 768},
                    {1280, 800},
                    {2560, 1080}
                }) {
            int width = size[0], height = size[1];
            glTexImage2D(
                    GL_TEXTURE_2D,
                    0,
                    GL_RGBA8,
                    width,
                    height,
                    0,
                    GL_RGBA,
                    GL_UNSIGNED_BYTE,
                    (ByteBuffer) null);
            if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new AssertionError("AA framebuffer incomplete");
            glViewport(0, 0, width, height);
            double minMass = Double.MAX_VALUE, maxMass = 0;
            for (int phase = 0; phase < 16; phase++) {
                // Blank patch of the actual menu, moving less than one physical pixel.
                double pointerX = 60 + phase / 16.0 * 320 / width;
                double pointerY = 10 + phase / 16.0 * 180 / height;
                float[] data = vertices(pointerX / 2, -pointerY / 2, phase % 4);
                glBindBuffer(GL_ARRAY_BUFFER, vbo);
                glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
                glDrawElements(GL_TRIANGLES, columns * rows * 6, GL_UNSIGNED_INT, 0);
                ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
                glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                if (phase == 0) checkSeams(pixels, width, height);
                double cx = (pointerX + 160) * width / 320, cy = (pointerY + 90) * height / 180;
                double mass = 0, sx = 0, sy = 0;
                int blended = 0;
                for (int y = (int) (cy - height / 30.0); y < cy + height / 30.0; y++) {
                    for (int x = (int) (cx - width / 53.0); x < cx + width / 53.0; x++) {
                        int i = (y * width + x) * 4;
                        int r = pixels.get(i) & 255,
                                g = pixels.get(i + 1) & 255,
                                blue = pixels.get(i + 2) & 255;
                        if (r > 16 && g > r * .7 && blue < r * .6) {
                            double coverage = (r - 8) / 247.0;
                            mass += coverage;
                            sx += (x + .5) * coverage;
                            sy += (y + .5) * coverage;
                            if (r > 24 && r < 240) blended++;
                        }
                    }
                }
                if (blended < 8 || Math.abs(sx / mass - cx) > .2 || Math.abs(sy / mass - cy) > .2)
                    throw new AssertionError(
                            "AA coverage/centroid "
                                    + width
                                    + "x"
                                    + height
                                    + " phase="
                                    + phase
                                    + " blended="
                                    + blended
                                    + " delta="
                                    + (sx / mass - cx)
                                    + ","
                                    + (sy / mass - cy));
                minMass = Math.min(minMass, mass);
                maxMass = Math.max(maxMass, mass);
                if (width == 1566 && phase == 5) {
                    BufferedImage capture =
                            new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < height; y++)
                        for (int x = 0; x < width; x++) {
                            int i = (y * width + x) * 4;
                            capture.setRGB(
                                    x,
                                    height - 1 - y,
                                    0xff000000
                                            | (pixels.get(i) & 255) << 16
                                            | (pixels.get(i + 1) & 255) << 8
                                            | (pixels.get(i + 2) & 255));
                        }
                    ImageIO.write(capture, "png", preview.toFile());
                }
                count++;
            }
            if (maxMass / minMass > 1.06)
                throw new AssertionError(
                        "Cursor flicker across subpixel phases: " + maxMass / minMass);
            System.out.printf(
                    Locale.ROOT,
                    "PASS: AA %dx%d subpixel brightness variation %.2f%%%n",
                    width,
                    height,
                    (maxMass / minMass - 1) * 100);
        }
        System.out.println(
                "PASS: "
                        + count
                        + " AA frames have blended edges, stable brightness and centroid within 0.2"
                        + " physical pixel");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(target);
        glActiveTexture(GL_TEXTURE0);
    }

    public static void main(String[] args) throws Exception {
        pack = new ZipFile(args[0]);
        client = new ZipFile(args[1]);
        if (!glfwInit()) throw new AssertionError("GLFW init failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(640, 360, "Fullscreen shader validation", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL context unavailable");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        System.out.println("GPU: " + glGetString(GL_RENDERER) + "; " + glGetString(GL_VERSION));
        String oitBase = "#define OIT\n#define OIT_COEFF_COUNT 8\n#define OIT_WAVELET_RANK 2\n#define OIT_COEFF_ATTACHMENT_COUNT 2\n";
        for (String oit : List.of("", oitBase + "#define OIT_ALPHA_ONLY\n#define OIT_DEPTH_BOUNDS\n",
                oitBase + "#define OIT_ALPHA_ONLY\n#define OIT_TRANSMITTANCE\n",
                oitBase + "#define OIT_ACCUMULATE\n")) {
            for (String mode : List.of("", "#define IS_GUI\n", "#define IS_SEE_THROUGH\n")) {
                for (String grayscale : List.of("", "#define IS_GRAYSCALE\n"))
                    glDeleteProgram(program(oit + mode + grayscale));
            }
            glDeleteProgram(program(oit, "position_color"));
        }
        System.out.println(
                "PASS: 24 text and 4 position_color variants compile/link, including OIT phases");
        int gui = program("", "gui");
        String vanillaGui = new String(client.getInputStream(client.getEntry("assets/minecraft/shaders/core/gui.fsh")).readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String shippedGui = read("assets/minecraft/shaders/core/gui.fsh");
        var block = Pattern.compile("uniform DynamicTransforms\\s*\\{([^}]+)\\}");
        var expected = block.matcher(vanillaGui);
        var actual = block.matcher(shippedGui);
        if (!expected.find() || !actual.find()
                || !expected.group(1).replaceAll("\\s+", "").equals(actual.group(1).replaceAll("\\s+", "")))
            throw new AssertionError("GUI DynamicTransforms differs from the target client");
        glDeleteProgram(gui);
        System.out.println("PASS: merged Dialog GUI compiles/links with the client uniform layout");
        int program = program("");
        glUseProgram(program);
        float[] dynamic = new float[40];
        dynamic[0] = dynamic[5] = dynamic[10] = dynamic[15] = 1;
        dynamic[16] = dynamic[21] = dynamic[26] = dynamic[31] = 1;
        dynamic[32] = dynamic[33] = dynamic[34] = dynamic[35] = 1;
        uniform(program, "DynamicTransforms", 0, dynamic);
        uniform(program, "Projection", 1, Arrays.copyOf(dynamic, 16));
        float[] globals = new float[16];
        globals[7] = -.5f;
        uniform(program, "Globals", 2, globals);
        uniform(program, "Fog", 3, new float[64]);
        String layout = read("assets/dialogmenu_fullscreen/layout.json");
        columns = value(layout, "columns");
        rows = value(layout, "rows");
        tileWidth = value(layout, "tile_width");
        tileHeight = value(layout, "tile_height");
        border = value(layout, "border");
        guiSource =
                new BufferedImage(
                        columns * tileWidth, rows * tileHeight, BufferedImage.TYPE_INT_ARGB);
        ByteBuffer rgba = BufferUtils.createByteBuffer(ATLAS * ATLAS * 4);
        for (int tile = 0; tile < columns * rows; tile++) {
            var source =
                    ImageIO.read(
                            pack.getInputStream(
                                    pack.getEntry(
                                            String.format(
                                                    Locale.ROOT,
                                                    "assets/dialogmenu_fullscreen/textures/gui/tile_%02d.png",
                                                    tile))));
            int ox = 23 + tile % columns * 250, oy = 49 + tile / columns * 230;
            for (int y = 0; y < tileHeight; y++)
                for (int x = 0; x < tileWidth; x++)
                    guiSource.setRGB(
                            tile % columns * tileWidth + x,
                            tile / columns * tileHeight + y,
                            source.getRGB(x + border, y + border));
            for (int y = 0; y < source.getHeight(); y++)
                for (int x = 0; x < source.getWidth(); x++) {
                    int c = source.getRGB(x, y), i = ((oy + y) * ATLAS + ox + x) * 4;
                    rgba.put(i, (byte) (c >> 16));
                    rgba.put(i + 1, (byte) (c >> 8));
                    rgba.put(i + 2, (byte) c);
                    rgba.put(i + 3, (byte) (c >> 24));
                }
        }
        glBindTexture(GL_TEXTURE_2D, glGenTextures());
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, ATLAS, ATLAS, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glUniform1i(glGetUniformLocation(program, "Sampler0"), 0);
        glUniform1i(glGetUniformLocation(program, "Sampler2"), 1);
        glBindVertexArray(glGenVertexArrays());
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        attribute(program, "Position", 3, 0);
        attribute(program, "Color", 4, 3);
        attribute(program, "UV0", 2, 7);
        int index = glGenBuffers();
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, index);
        int[] indices = new int[columns * rows * 6];
        int[] quad = {0, 1, 2, 2, 3, 0};
        for (int i = 0; i < indices.length; i++) indices[i] = i / 6 * 4 + quad[i % 6];
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        glViewport(0, 0, 640, 360);
        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_GEQUAL);
        glClearDepth(0);
        glDisable(GL_CULL_FACE);
        glDisable(GL_BLEND);
        int overlay = glCreateProgram();
        glAttachShader(
                overlay,
                compile(
                        GL_VERTEX_SHADER,
                        "#version 330\n"
                            + "void main(){vec2"
                            + " p[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));gl_Position=vec4(p[gl_VertexID],0.5,1.0);}"));
        glAttachShader(
                overlay,
                compile(
                        GL_FRAGMENT_SHADER,
                        "#version 330\nout vec4 fragColor;void main(){fragColor=vec4(1,0,1,1);}"));
        glLinkProgram(overlay);
        int count = 0;
        for (boolean zeroToOne : new boolean[] {false, true}) {
            org.lwjgl.opengl.ARBClipControl.glClipControl(
                    org.lwjgl.opengl.ARBClipControl.GL_LOWER_LEFT,
                    zeroToOne
                            ? org.lwjgl.opengl.ARBClipControl.GL_ZERO_TO_ONE
                            : org.lwjgl.opengl.ARBClipControl.GL_NEGATIVE_ONE_TO_ONE);
            for (int vertexShift = 0; vertexShift < 4; vertexShift++) {
                for (float yaw : new float[] {-179, -79, -59.5f, -24, 0, 24, 59.5f, 79, 179}) {
                    for (float pitch : new float[] {-89, -44, -33.5f, 0, 22.5f, 33.5f, 44, 89}) {
                        float[] data = vertices(yaw, pitch, vertexShift);
                        glUseProgram(program);
                        glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
                        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
                        glDrawElements(GL_TRIANGLES, columns * rows * 6, GL_UNSIGNED_INT, 0);
                        glUseProgram(overlay);
                        glDrawArrays(GL_TRIANGLES, 0, 3);
                        ByteBuffer pixels = BufferUtils.createByteBuffer(640 * 360 * 4);
                        glReadPixels(0, 0, 640, 360, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                        int hits = 0;
                        double sx = 0, sy = 0;
                        for (int y = 0; y < 360; y++)
                            for (int x = 0; x < 640; x++) {
                                int i = (y * 640 + x) * 4;
                                if ((pixels.get(i) & 255) > 245
                                        && (pixels.get(i + 1) & 255) > 210
                                        && (pixels.get(i + 1) & 255) < 235
                                        && (pixels.get(i + 2) & 255) < 65) {
                                    hits++;
                                    sx += x + .5;
                                    sy += y + .5;
                                }
                            }
                        double expectedX = Math.clamp(yaw * 2, -158, 158),
                                expectedY = Math.clamp(-pitch * 2, -88, 88);
                        if (hits == 0
                                || Math.abs(sx / hits / 2 - 160 - expectedX) > 1
                                || Math.abs(sy / hits / 2 - 90 - expectedY) > 1)
                            throw new AssertionError(
                                    "cursor zeroToOne="
                                            + zeroToOne
                                            + " yaw="
                                            + yaw
                                            + " pitch="
                                            + pitch
                                            + " hits="
                                            + hits
                                            + " x="
                                            + (sx / hits / 2 - 160)
                                            + " y="
                                            + (sy / hits / 2 - 90));
                        // A static button corner must stay upright for every vertex ordering.
                        int cornerPixel = (350 * 640 + 20) * 4;
                        int r = pixels.get(cornerPixel) & 255,
                                g = pixels.get(cornerPixel + 1) & 255;
                        if (!((r == 41 && g == 65) || ((r == 66 || r == 67) && g == 108)))
                            throw new AssertionError(
                                    "canvas orientation or late-world depth occlusion failed,"
                                            + " shift="
                                            + vertexShift
                                            + " yaw="
                                            + yaw
                                            + " pitch="
                                            + pitch
                                            + " rgb="
                                            + r
                                            + ","
                                            + g);
                        if (zeroToOne && vertexShift == 1 && yaw == 59.5f && pitch == -33.5f) {
                            BufferedImage capture =
                                    new BufferedImage(640, 360, BufferedImage.TYPE_INT_ARGB);
                            for (int y = 0; y < 360; y++)
                                for (int x = 0; x < 640; x++) {
                                    int i = (y * 640 + x) * 4;
                                    int c =
                                            0xff000000
                                                    | (pixels.get(i) & 255) << 16
                                                    | (pixels.get(i + 1) & 255) << 8
                                                    | (pixels.get(i + 2) & 255);
                                    capture.setRGB(x, 359 - y, c);
                                }
                            ImageIO.write(capture, "png", Path.of(args[2]).toFile());
                        }
                        count++;
                    }
                }
            }
        }
        System.out.println(
                "PASS: "
                        + count
                        + " rendered yaw/pitch cases match server cursor coordinates (within 1"
                        + " logical pixel)");
        System.out.println(
                "PASS: atlas offsets, four cyclic vertex orders, upright button pixels and later"
                        + " world occlusion");
        if (args.length > 3) antialiasCases(program, vbo, Path.of(args[3]));
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        glfwDestroyWindow(window);
        glfwTerminate();
        pack.close();
        client.close();
    }
}
