import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.ZipFile;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import online.toraka.dialogmenu.AnimationPreset;

/** Checks the actual packaged shaders, not a copy of their animation math. */
public final class AnimationShaderProbe {
    static ZipFile pack, client;
    static String read(String key) throws Exception {
        ZipFile z=pack.getEntry(key)!=null?pack:client;
        return new String(z.getInputStream(z.getEntry(key)).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
    }
    static String expand(String text, Set<String> seen) throws Exception {
        Matcher m=Pattern.compile("#(?:moj_import|include) <([^:>]+):([^>]+)>").matcher(text);
        StringBuilder out=new StringBuilder();
        while(m.find()) {
            String key="assets/"+m.group(1)+"/shaders/include/"+m.group(2);
            m.appendReplacement(out,Matcher.quoteReplacement(seen.add(key)?expand(read(key).replaceAll("(?m)^#version[^\\n]*",""),seen):""));
        }
        return m.appendTail(out).toString();
    }
    static int shader(int type,String source) {
        int s=glCreateShader(type);glShaderSource(s,source);glCompileShader(s);
        if(glGetShaderi(s,GL_COMPILE_STATUS)==0) throw new AssertionError(glGetShaderInfoLog(s));
        return s;
    }
    static int program(boolean fragment) throws Exception {
        int p=glCreateProgram();
        String v=expand(read("assets/minecraft/shaders/core/text.vsh"),new HashSet<>()).replace("#version 330","#version 330\n#define IS_GUI\n");
        glAttachShader(p,shader(GL_VERTEX_SHADER,v));
        if(fragment) {
            String f=expand(read("assets/minecraft/shaders/core/text.fsh"),new HashSet<>()).replace("#version 330","#version 330\n#define IS_GUI\n");
            glAttachShader(p,shader(GL_FRAGMENT_SHADER,f));
        } else glTransformFeedbackVaryings(p,new String[]{"gl_Position","dmAnimationAlpha"},GL_INTERLEAVED_ATTRIBS);
        glLinkProgram(p);
        if(glGetProgrami(p,GL_LINK_STATUS)==0) throw new AssertionError(glGetProgramInfoLog(p));
        return p;
    }
    static void uniform(int p,String name,int binding,float[] data) {
        int index=glGetUniformBlockIndex(p,name);if(index==GL_INVALID_INDEX)return;
        glUniformBlockBinding(p,index,binding);glBindBuffer(GL_UNIFORM_BUFFER,glGenBuffers());
        glBufferData(GL_UNIFORM_BUFFER,data,GL_STATIC_DRAW);glBindBufferBase(GL_UNIFORM_BUFFER,binding,glGetInteger(GL_UNIFORM_BUFFER_BINDING));
    }
    static void setup(int p,boolean modern,boolean raster) {
        glUseProgram(p);
        float[] d=new float[40]; d[0]=d[5]=d[10]=d[15]=1;
        int tex=modern?16:24;d[tex]=d[tex+5]=d[tex+10]=d[tex+15]=1;
        int color=modern?32:16;Arrays.fill(d,color,color+4,1);
        uniform(p,"DynamicTransforms",0,d);
        float[] projection=Arrays.copyOf(d,16);
        if(raster) {projection[0]=projection[5]=2f/64;projection[12]=projection[13]=-1;}
        uniform(p,"Projection",1,projection);uniform(p,"Globals",2,new float[16]);
        glUniform1i(glGetUniformLocation(p,"Sampler0"),0);
        for(Object[] a:new Object[][]{{"Position",3,0},{"Color",4,3},{"UV0",2,7}}) {
            int at=glGetAttribLocation(p,(String)a[0]);if(at<0)continue;
            glEnableVertexAttribArray(at);glVertexAttribPointer(at,(int)a[1],GL_FLOAT,false,36,(int)a[2]*4L);
        }
    }
    static float[] vertices(int mode,int phase,int shift,boolean tagged) {
        float[] v=new float[36];int[][] c={{0,0},{0,1},{1,1},{1,0}};
        for(int i=0;i<4;i++) {
            int[] q=c[(i+shift)%4];int j=i*9;
            v[j]=14+q[0]*36-(tagged?3145728:0);v[j+1]=14+q[1]*36;
            v[j+3]=mode/255f;v[j+4]=phase/255f;v[j+5]=215/255f;v[j+6]=1;
            v[j+7]=(23+q[0]*16+(q[0]==0?.01f:-.01f))/256;
            v[j+8]=(49+q[1]*16+(q[1]==0?.01f:-.01f))/256;
        }
        return v;
    }
    public static void main(String[] args) throws Exception {
        pack=new ZipFile(args[0]);client=new ZipFile(args[1]);boolean modern=args[2].equals("26.3");
        if(!glfwInit())throw new AssertionError("GLFW");
        glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3);glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(64,64,"Animation verification",0,0);glfwMakeContextCurrent(window);GL.createCapabilities();
        glBindTexture(GL_TEXTURE_2D,glGenTextures());var white=BufferUtils.createByteBuffer(256*256*4);
        for(int i=0;i<256*256*4;i++)white.put((byte)255);white.flip();
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,256,256,0,GL_RGBA,GL_UNSIGNED_BYTE,white);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glBindVertexArray(glGenVertexArrays());int input=glGenBuffers();glBindBuffer(GL_ARRAY_BUFFER,input);
        int p=program(false);setup(p,modern,false);
        int feedback=glGenBuffers();glBindBuffer(GL_TRANSFORM_FEEDBACK_BUFFER,feedback);glBufferData(GL_TRANSFORM_FEEDBACK_BUFFER,80,GL_STREAM_READ);glBindBufferBase(GL_TRANSFORM_FEEDBACK_BUFFER,0,feedback);
        glEnable(GL_RASTERIZER_DISCARD);int cases=0;
        for(AnimationPreset preset:AnimationPreset.values())for(int phase=0;phase<256;phase++)for(int shift=0;shift<4;shift++) {
            float[] v=vertices(preset.ordinal(),phase,shift,true);glBufferData(GL_ARRAY_BUFFER,v,GL_STREAM_DRAW);
            glBeginTransformFeedback(GL_POINTS);glDrawArrays(GL_POINTS,0,4);glEndTransformFeedback();
            float[] actual=new float[20];glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER,0,actual);
            var f=preset.frame(phase/255.0);
            for(int i=0;i<4;i++) {
                double x=v[i*9]+3145728-32,y=v[i*9+1]-32;
                double ex=32+f.x()+f.scale()*(Math.cos(f.angle())*x-Math.sin(f.angle())*y);
                double ey=32+f.y()+f.scale()*(Math.sin(f.angle())*x+Math.cos(f.angle())*y);
                if(Math.abs(actual[i*5]-ex)>.02 || Math.abs(actual[i*5+1]-ey)>.02 || Math.abs(actual[i*5+4]-f.opacity())>.00001)
                    throw new AssertionError(preset+" phase="+phase+" vertex="+i+" actual="+Arrays.toString(actual)+" expected="+f);
            }
            cases++;
        }
        float[] normal=vertices(0,128,0,false);glBufferData(GL_ARRAY_BUFFER,normal,GL_STREAM_DRAW);
        glBeginTransformFeedback(GL_POINTS);glDrawArrays(GL_POINTS,0,4);glEndTransformFeedback();
        float[] actual=new float[20];glGetBufferSubData(GL_TRANSFORM_FEEDBACK_BUFFER,0,actual);
        for(int i=0;i<4;i++)if(actual[i*5]!=normal[i*9] || actual[i*5+1]!=normal[i*9+1] || actual[i*5+4]!=-1)throw new AssertionError("Ordinary GUI text changed");
        glDisable(GL_RASTERIZER_DISCARD);
        p=program(true);setup(p,modern,true);glViewport(0,0,64,64);glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        var pixel=BufferUtils.createByteBuffer(4);int fades=0;
        for(AnimationPreset preset:List.of(AnimationPreset.FADE_IN,AnimationPreset.FADE_OUT))for(int phase=0;phase<256;phase++) {
            glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);glBufferData(GL_ARRAY_BUFFER,vertices(preset.ordinal(),phase,0,true),GL_STREAM_DRAW);
            glDrawArrays(GL_TRIANGLE_FAN,0,4);glReadPixels(32,32,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
            int value=pixel.get(0)&255,expected=(int)Math.round(preset.frame(phase/255.0).opacity()*255);
            if(Math.abs(value-expected)>1)throw new AssertionError("Fade discontinuity "+preset+" phase="+phase+" rgb="+value+" expected="+expected);
            fades++;
        }
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error");
        System.out.println("PASS: "+cases+" GPU preset/phase/vertex-order cases; "+fades+" alpha blend samples, ordinary GUI isolated");
        glfwDestroyWindow(window);glfwTerminate();pack.close();client.close();
    }
}
