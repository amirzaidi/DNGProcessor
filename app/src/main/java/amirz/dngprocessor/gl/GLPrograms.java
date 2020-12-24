package amirz.dngprocessor.gl;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import amirz.dngprocessor.R;
import amirz.dngprocessor.math.BlockDivider;

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;

public class GLPrograms implements AutoCloseable {
    private static GLPrograms sInstance;

    static {
        GLCore.addOnCloseContextRunnable(() -> {
            if (sInstance != null) {
                sInstance.close();
            }
            sInstance = null;
        });
    }

    public static GLPrograms getInstance(ShaderLoader shaderLoader) {
        if (sInstance == null) {
            sInstance = new GLPrograms(shaderLoader);
        }
        return sInstance;
    }

    public final int vertexShader;

    private final ByteBuffer mFlushBuffer = ByteBuffer.allocateDirect(32);
    private final ShaderLoader mShaderLoader;
    private final SquareModel mSquare = new SquareModel();
    private final Map<Integer, Integer> mPrograms = new HashMap<>();
    private final Map<String, Integer> mTextureBinds = new HashMap<>();
    private int mNewTextureId;
    private int mProgramActive;

    private GLPrograms(ShaderLoader shaderLoader) {
        mShaderLoader = shaderLoader;
        vertexShader = loadShader(GL_VERTEX_SHADER, shaderLoader.readRaw(R.raw.passthrough_vs));
    }

    public void useProgram(int fragmentRes) {
        int program = mPrograms.computeIfAbsent(fragmentRes, x -> createProgram(
                vertexShader, mShaderLoader.readRaw(x)));

        glLinkProgram(program);
        glUseProgram(program);
        mProgramActive = program;

        mTextureBinds.clear();
        mNewTextureId = 0;
    }

    private int createProgram(int vertex, String fragmentId) {
        int fragment;
        try {
            fragment = loadShader(GL_FRAGMENT_SHADER, fragmentId);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error initializing fragment shader:\n" + fragmentId, e);
        }

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        return program;
    }

    public void draw() {
        mSquare.draw(vPosition());
        glFlush();
    }

    public void drawBlocks(Texture tex) {
        drawBlocks(tex, true);
    }

    public void drawBlocks(Texture tex, boolean forceFlush) {
        tex.setFrameBuffer();
        drawBlocks(tex.getWidth(), tex.getHeight(), forceFlush ? tex.getFormatInt() : -1, tex.getType());
    }

    private void drawBlocks(int w, int h, int format, int type) {
        // For some reason, Android cannot read all formats.
        if (format == GL_RED || format == GL_RGB) {
            format = GL_RGBA;
        }

        BlockDivider divider = new BlockDivider(h, BLOCK_HEIGHT);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            draw();

            if (format != -1) {
                mFlushBuffer.position(0);
                glReadPixels(0, row[0], 1, 1, format, type, mFlushBuffer);
                int glError = glGetError();
                if (glError != 0) {
                    Log.d("GLPrograms", "GLError: " + glError);
                }
            }
        }
    }

    @Override
    public void close() {
        // Clean everything up
        for (int program : mPrograms.values()) {
            glDeleteProgram(program);
        }
        mPrograms.clear();
    }

    protected static int loadShader(int type, String shaderCode) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);

        int[] status = new int[1];
        glGetShaderiv(shader, GL_COMPILE_STATUS, status, 0);
        if (status[0] == GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(shader));
        }

        return shader;
    }

    private int vPosition() {
        return glGetAttribLocation(mProgramActive, "vPosition");
    }

    @SuppressWarnings("ConstantConditions")
    public void setTexture(String var, Texture tex) {
        int textureId;
        if (mTextureBinds.containsKey(var)) {
            textureId = mTextureBinds.get(var);
        } else {
            textureId = mNewTextureId;
            mTextureBinds.put(var, textureId);
            mNewTextureId += 2;
        }

        seti(var, textureId);
        tex.bind(GL_TEXTURE0 + textureId);
    }

    public void seti(String var, int... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1i(loc, vals[0]); break;
            case 2: glUniform2i(loc, vals[0], vals[1]); break;
            case 3: glUniform3i(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4i(loc, vals[0], vals[1], vals[2], vals[3]); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    public void setui(String var, int... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1ui(loc, vals[0]); break;
            case 2: glUniform2ui(loc, vals[0], vals[1]); break;
            case 3: glUniform3ui(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4ui(loc, vals[0], vals[1], vals[2], vals[3]); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    public void setf(String var, float... vals) {
        int loc = loc(var);
        switch (vals.length) {
            case 1: glUniform1f(loc, vals[0]); break;
            case 2: glUniform2f(loc, vals[0], vals[1]); break;
            case 3: glUniform3f(loc, vals[0], vals[1], vals[2]); break;
            case 4: glUniform4f(loc, vals[0], vals[1], vals[2], vals[3]); break;
            case 9: glUniformMatrix3fv(loc, 1, true, vals, 0); break;
            default: throw new RuntimeException("Cannot set " + var + " to " + Arrays.toString(vals));
        }
    }

    private int loc(String var) {
        return glGetUniformLocation(mProgramActive, var);
    }
}
