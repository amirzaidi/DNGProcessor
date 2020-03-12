package amirz.dngprocessor.gl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import amirz.dngprocessor.math.BlockDivider;

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;

public abstract class GLProgramBase implements AutoCloseable {
    private final GLSquare mSquare = new GLSquare();
    private final List<Integer> mPrograms = new ArrayList<>();
    private int mProgramActive;

    public void useProgram(int program) {
        glLinkProgram(program);
        glUseProgram(program);
        mProgramActive = program;
    }

    public int createProgram(int vertex, String fragmentId) {
        int fragment;
        try {
            fragment = loadShader(GL_FRAGMENT_SHADER, fragmentId);
        } catch (RuntimeException e) {
            throw new RuntimeException("Error initializing fragment shader:\n" + fragmentId, e);
        }

        int program = glCreateProgram();
        glAttachShader(program, vertex);
        glAttachShader(program, fragment);
        mPrograms.add(program);
        return program;
    }

    public abstract void setYOffset(int y);

    public void draw() {
        mSquare.draw(vPosition());
        glFlush();
    }

    public void drawBlocks(int w, int h) {
        BlockDivider divider = new BlockDivider(h, BLOCK_HEIGHT);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            draw();
        }
    }

    @Override
    public void close() {
        // Clean everything up
        for (int program : mPrograms) {
            glDeleteProgram(program);
        }
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
