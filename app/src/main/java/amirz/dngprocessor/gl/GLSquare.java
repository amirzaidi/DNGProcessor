package amirz.dngprocessor.gl;

import android.util.Rational;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import amirz.dngprocessor.MainActivity;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;

public class GLSquare {
    private final FloatBuffer vertexBuffer;
    private final int mProgram;

    private static final int COORDS_PER_VERTEX = 3;
    private static final float COORDS[] = {
            -1, 1, 0,
            -1, -1, 0,
            1, 1, 0,
            1, -1, 0
    };

    private static final int STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    public GLSquare() {
        // (# of coordinate values * 4 bytes per float)
        ByteBuffer bb = ByteBuffer.allocateDirect(COORDS.length * 4);

        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(COORDS);
        vertexBuffer.position(0);

        int vertexShader = loadShader(
                GL_VERTEX_SHADER,
                MainActivity.VS);

        int fragmentShader = loadShader(
                GL_FRAGMENT_SHADER,
                MainActivity.PS);

        mProgram = glCreateProgram();
        glAttachShader(mProgram, vertexShader);
        glAttachShader(mProgram, fragmentShader);
        glLinkProgram(mProgram);
        glUseProgram(mProgram);
    }

    public static int loadShader(int type, String shaderCode) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);
        return shader;
    }

    public void setCfaPattern(int cfaPattern) {
        glUniform1ui(glGetUniformLocation(mProgram, "cfaPattern"), cfaPattern);
    }

    public void setBlackWhiteLevel(int[] blackLevel, float whiteLevel) {
        glUniform4f(glGetUniformLocation(mProgram, "blackLevel"),
                blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);

        glUniform1f(glGetUniformLocation(mProgram, "whiteLevel"), whiteLevel);
    }

    public void setNeutralPoint(Rational[] neutralPoint) {
        glUniform3f(glGetUniformLocation(mProgram, "neutralPoint"),
                neutralPoint[0].floatValue(), neutralPoint[1].floatValue(), neutralPoint[2].floatValue());
    }

    public void setToneMapCoeffs(float[] toneMapCoeffs) {
        glUniform4f(glGetUniformLocation(mProgram, "toneMapCoeffs"),
                toneMapCoeffs[0], toneMapCoeffs[1], toneMapCoeffs[2], toneMapCoeffs[3]);
    }

    public void setTransforms(float[] sensorToIntermediate, float[] intermediateToProPhoto, float[] proPhotoToSRGB) {
        glUniformMatrix3fv(glGetUniformLocation(mProgram, "sensorToIntermediate"),
                1, true, sensorToIntermediate, 0);

        glUniformMatrix3fv(glGetUniformLocation(mProgram, "intermediateToProPhoto"),
                1, true, intermediateToProPhoto, 0);

        glUniformMatrix3fv(glGetUniformLocation(mProgram, "proPhotoToSRGB"),
                1, true, proPhotoToSRGB, 0);
    }

    public void setIn(byte[] in, int inWidth, int inHeight) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(in.length);
        buffer.put(in);
        buffer.flip();

        int[] textureIds = new int[1];
        glGenTextures(1, textureIds, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, textureIds[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16UI, inWidth, inHeight, 0,
                GL_RED_INTEGER, GL_UNSIGNED_SHORT, buffer);

        glUniform1i(glGetUniformLocation(mProgram, "raw"), 0);
        glUniform1i(glGetUniformLocation(mProgram, "rawWidth"), inWidth);
        glUniform1i(glGetUniformLocation(mProgram, "rawHeight"), inHeight);
    }

    public void setOut(int outWidth, int outHeight) {
        glViewport(0, 0, outWidth, outHeight);
        glUniform1i(glGetUniformLocation(mProgram, "outWidth"), outWidth);
        glUniform1i(glGetUniformLocation(mProgram, "outHeight"), outHeight);
    }

    public void setOffset(int offsetX, int offsetY) {
        glUniform2i(glGetUniformLocation(mProgram, "outOffset"), offsetX, offsetY);
    }

    public void draw() {
        int posHandle = glGetAttribLocation(mProgram, "vPosition");
        glEnableVertexAttribArray(posHandle);
        glVertexAttribPointer(
                posHandle, COORDS_PER_VERTEX,
                GL_FLOAT, false,
                STRIDE, vertexBuffer);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(posHandle);
    }
}
