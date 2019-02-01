package amirz.dngprocessor.gl;

import android.util.Rational;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import amirz.dngprocessor.MainActivity;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;
import static android.opengl.GLU.gluErrorString;
import static javax.microedition.khronos.opengles.GL10.GL_RGB;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLSquare {
    private static final int COORDS_PER_VERTEX = 3;
    private static final float COORDS[] = {
            -1, 1, 0,
            -1, -1, 0,
            1, 1, 0,
            1, -1, 0
    };
    private static final int STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private final FloatBuffer vertexBuffer;

    private final int mProgramSensorToIntermediate;
    private final int mProgramIntermediateToSRGB;

    private final int[] mIntermediateTex = new int[1];

    public GLSquare() {
        // (# of coordinate values * 4 bytes per float)
        ByteBuffer bb = ByteBuffer.allocateDirect(COORDS.length * 4);

        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(COORDS);
        vertexBuffer.position(0);

        int vertexShader1 = loadShader(
                GL_VERTEX_SHADER,
                MainActivity.VS1);

        int fragmentShader1 = loadShader(
                GL_FRAGMENT_SHADER,
                MainActivity.PS1);

        mProgramSensorToIntermediate = glCreateProgram();
        glAttachShader(mProgramSensorToIntermediate, vertexShader1);
        glAttachShader(mProgramSensorToIntermediate, fragmentShader1);
        glLinkProgram(mProgramSensorToIntermediate);
        glUseProgram(mProgramSensorToIntermediate);

        int vertexShader2 = loadShader(
                GL_VERTEX_SHADER,
                MainActivity.VS2);

        int fragmentShader2 = loadShader(
                GL_FRAGMENT_SHADER,
                MainActivity.PS2);

        mProgramIntermediateToSRGB = glCreateProgram();
        glAttachShader(mProgramIntermediateToSRGB, vertexShader2);
        glAttachShader(mProgramIntermediateToSRGB, fragmentShader2);
    }

    public static int loadShader(int type, String shaderCode) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);
        return shader;
    }

    private int inWidth, inHeight;

    public void setIn(byte[] in, int inWidth, int inHeight) {
        this.inWidth = inWidth;
        this.inHeight = inHeight;

        // Generate intermediate texture
        glGenTextures(1, mIntermediateTex, 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth, inHeight, 0, GL_RGB, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Now create the input texture and bind it to TEXTURE0
        ByteBuffer buffer = ByteBuffer.allocateDirect(in.length);
        buffer.put(in);
        buffer.flip();

        int[] rawTex = new int[1];
        glGenTextures(1, rawTex, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, rawTex[0]);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_R16UI, inWidth, inHeight, 0,
                GL_RED_INTEGER, GL_UNSIGNED_SHORT, buffer);

        glUniform1i(glGetUniformLocation(mProgramSensorToIntermediate, "rawBuffer"), 0);
        glUniform1i(glGetUniformLocation(mProgramSensorToIntermediate, "rawWidth"), inWidth);
        glUniform1i(glGetUniformLocation(mProgramSensorToIntermediate, "rawHeight"), inHeight);

        // Configure frame buffer
        int[] fb = new int[1];
        glGenFramebuffers(1, fb, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, fb[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mIntermediateTex[0], 0);

        glViewport(0, 0, inWidth, inHeight);
    }

    public void setCfaPattern(int cfaPattern) {
        glUniform1ui(glGetUniformLocation(mProgramSensorToIntermediate, "cfaPattern"), cfaPattern);
    }

    public void setBlackWhiteLevel(int[] blackLevel, float whiteLevel) {
        glUniform4f(glGetUniformLocation(mProgramSensorToIntermediate, "blackLevel"),
                blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);

        glUniform1f(glGetUniformLocation(mProgramSensorToIntermediate, "whiteLevel"), whiteLevel);
    }

    public void setNeutralPoint(Rational[] neutralPoint) {
        glUniform3f(glGetUniformLocation(mProgramSensorToIntermediate, "neutralPoint"),
                neutralPoint[0].floatValue(), neutralPoint[1].floatValue(), neutralPoint[2].floatValue());
    }

    public void setTransforms1(float[] sensorToIntermediate) {
        glUniformMatrix3fv(glGetUniformLocation(mProgramSensorToIntermediate, "sensorToIntermediate"),
                1, true, sensorToIntermediate, 0);
    }

    public void draw1() {
        int posHandle = glGetAttribLocation(mProgramSensorToIntermediate, "vPosition");
        glEnableVertexAttribArray(posHandle);
        glVertexAttribPointer(
                posHandle, COORDS_PER_VERTEX,
                GL_FLOAT, false,
                STRIDE, vertexBuffer);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(posHandle);

        // Now switch to the second program
        glLinkProgram(mProgramIntermediateToSRGB);
        glUseProgram(mProgramIntermediateToSRGB);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        int[] intermediateTex = new int[1];
        glGenTextures(1, intermediateTex, 0);

        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth, inHeight, 0, GL_RGB, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
    }

    public void setToneMapCoeffs(float[] toneMapCoeffs) {
        glUniform4f(glGetUniformLocation(mProgramIntermediateToSRGB, "toneMapCoeffs"),
                toneMapCoeffs[0], toneMapCoeffs[1], toneMapCoeffs[2], toneMapCoeffs[3]);
    }

    public void setTransforms2(float[] intermediateToProPhoto, float[] proPhotoToSRGB) {
        glUniformMatrix3fv(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateToProPhoto"),
                1, true, intermediateToProPhoto, 0);

        glUniformMatrix3fv(glGetUniformLocation(mProgramIntermediateToSRGB, "proPhotoToSRGB"),
                1, true, proPhotoToSRGB, 0);
    }

    public void setPostProcCurve(float[] postProcCurve) {
        glUniform3f(glGetUniformLocation(mProgramIntermediateToSRGB, "postProcCurve"),
                postProcCurve[0], postProcCurve[1], postProcCurve[2]);
    }

    public void setSaturationFactor(float saturationFactor) {
        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "saturationFactor"),
                saturationFactor);
    }

    public void setOffset(int offsetX, int offsetY) {
        glUniform2i(glGetUniformLocation(mProgramIntermediateToSRGB, "outOffset"), offsetX, offsetY);
    }

    public void setOut(int outWidth, int outHeight) {
        glViewport(0, 0, outWidth, outHeight);
    }

    public void draw2() {
        int posHandle = glGetAttribLocation(mProgramSensorToIntermediate, "vPosition");
        glEnableVertexAttribArray(posHandle);
        glVertexAttribPointer(
                posHandle, COORDS_PER_VERTEX,
                GL_FLOAT, false,
                STRIDE, vertexBuffer);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        glDisableVertexAttribArray(posHandle);

        // Clean everything up
        glDeleteProgram(mProgramSensorToIntermediate);
        glDeleteProgram(mProgramIntermediateToSRGB);
    }
}
