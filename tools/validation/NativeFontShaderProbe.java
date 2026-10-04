import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.zip.ZipFile;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;

/** Capture actual GPU vertices to check typography and that ordinary/world text passes through. */
public final class NativeFontShaderProbe extends FullscreenShaderProbe {
    public static void main(String[] args) throws Exception {
        pack = new ZipFile(args[0]);
        client = new ZipFile(args[1]);
        if (!glfwInit()) throw new AssertionError("GLFW initialization failed");
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(64, 64, "Native font validation", 0, 0);
        if (window == 0) throw new AssertionError("OpenGL context unavailable");
        glfwMakeContextCurrent(window);
        GL.createCapabilities();
        String source = expand(read("assets/minecraft/shaders/core/text.vsh"), new HashSet<>()).replace("#version 330", "#version 330\n#define IS_GUI\n#define IS_GRAYSCALE\n");
        int program = glCreateProgram();
        glAttachShader(program, compile(GL_VERTEX_SHADER, source));
        glTransformFeedbackVaryings(program, new String[] {"gl_Position"}, GL_INTERLEAVED_ATTRIBS);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == 0) throw new AssertionError(glGetProgramInfoLog(program));
        glUseProgram(program);
        float[] dynamic = new float[40];
        dynamic[0] = dynamic[5] = dynamic[10] = dynamic[15] = 1;
        dynamic[16] = dynamic[21] = dynamic[26] = dynamic[31] = 1;
        dynamic[32] = dynamic[33] = dynamic[34] = dynamic[35] = 1;
        uniform(program, "DynamicTransforms", 0, dynamic);
        float[] projection = java.util.Arrays.copyOf(dynamic, 16);
        uniform(program, "Projection", 1, projection);
        glBindVertexArray(glGenVertexArrays());
        int vbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int at = glGetAttribLocation(program, "Position");
        glEnableVertexAttribArray(at);
        glVertexAttribPointer(at, 3, GL_FLOAT, false, 12, 0);
        int feedback = glGenBuffers();
        glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, feedback);
        glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER, 4 * 4 * 4, GL_STREAM_READ);
        glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER, 0, feedback);
        glEnable(GL_RASTERIZER_DISCARD);
        int cases = 0;
        for (boolean perspective : new boolean[] {false, true}) {
            projection[15] = perspective ? 0 : 1;
            uniform(program, "Projection", 1, projection);
            for (int index = -1; index < 76; index++) {
                int base = Math.max(0, index) % 38;
                int size = base / 2 + 6;
                float paddedHeight = (size * 3 + 1) / 2;
                float ascent = size == 8 ? 11 : base % 2 == 1 && size <= 12 ? 7 - (18 - size) / 2 : Math.min(7, size);
                for (float fractional : new float[] {0, .5f}) {
                    float shift = index < 0 ? 0 : (200 + index) * 16384;
                    float[] vertices = {40 + fractional - shift, 20, 0, 40 + fractional - shift, 28, 0, 48 + fractional - shift, 28, 0, 48 + fractional - shift, 20, 0};
                    glBindBuffer(GL_ARRAY_BUFFER, vbo);
                    glBufferData(GL_ARRAY_BUFFER, vertices, GL_STREAM_DRAW);
                    glBeginTransformFeedback(GL_POINTS);
                    glDrawArrays(GL_POINTS, 0, 4);
                    glEndTransformFeedback();
                    var result = BufferUtils.createFloatBuffer(16);
                    glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER, feedback);
                    glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER, 0, result);
                    for (int corner = 0; corner < 4; corner++) {
                        boolean bottom = corner == 1 || corner == 2;
                        float expectedX = vertices[corner * 3], expectedY = vertices[corner * 3 + 1];
                        if (!perspective && index >= 0) {
                            expectedX = 40 + fractional + (corner >= 2 ? paddedHeight * 2 / 3 : 0);
                            expectedY = 20 + 7 - ascent + (bottom ? paddedHeight * 2 / 3 : 0);
                            if (index >= 38) expectedX += 1 - .25f * (7 - ascent + (bottom ? paddedHeight : 0));
                        }
                        if (Math.abs(result.get(corner * 4) - expectedX) > .3 || Math.abs(result.get(corner * 4 + 1) - expectedY) > .001) throw new AssertionError("layout=" + index + " corner=" + corner + " x=" + result.get(corner * 4) + "/" + expectedX + " y=" + result.get(corner * 4 + 1) + "/" + expectedY);
                    }
                    cases++;
                }
            }
        }
        if (glGetError() != GL_NO_ERROR) throw new AssertionError("OpenGL error");
        System.out.println("PASS: " + cases + " real GPU cases: 19 sizes, two baselines, italic, half-pixel bold offset, ordinary text and perspective isolation; " + glGetString(GL_RENDERER));
        glfwDestroyWindow(window);
        glfwTerminate();
        pack.close();
        client.close();
    }
}
