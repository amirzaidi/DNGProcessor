package amirz.dngprocessor.gl;

import android.util.Rational;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;
import static javax.microedition.khronos.opengles.GL10.GL_RGB;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLProgram {
    private final GLSquare mSquare = new GLSquare();
    private final int mProgramSensorToIntermediate;
    private final int mProgramIntermediateToSRGB;

    private final int[] mIntermediateTex = new int[1];

    public GLProgram() {
        int vertexShader = loadShader(
                GL_VERTEX_SHADER,
                Shaders.VS);

        int fragmentShader1 = loadShader(
                GL_FRAGMENT_SHADER,
                Shaders.PS1);

        mProgramSensorToIntermediate = glCreateProgram();
        glAttachShader(mProgramSensorToIntermediate, vertexShader);
        glAttachShader(mProgramSensorToIntermediate, fragmentShader1);
        glLinkProgram(mProgramSensorToIntermediate);
        glUseProgram(mProgramSensorToIntermediate);

        int fragmentShader2 = loadShader(
                GL_FRAGMENT_SHADER,
                Shaders.PS2);

        mProgramIntermediateToSRGB = glCreateProgram();
        glAttachShader(mProgramIntermediateToSRGB, vertexShader);
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

    public void setBlackWhiteLevel(int[] blackLevel, int whiteLevel) {
        glUniform4f(glGetUniformLocation(mProgramSensorToIntermediate, "blackLevel"),
                blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);

        glUniform1f(glGetUniformLocation(mProgramSensorToIntermediate, "whiteLevel"),
                whiteLevel);
    }

    public void setNeutralPoint(Rational[] neutralPoint) {
        glUniform3f(glGetUniformLocation(mProgramSensorToIntermediate, "neutralPoint"),
                neutralPoint[0].floatValue(), neutralPoint[1].floatValue(), neutralPoint[2].floatValue());
    }

    public void setTransforms1(float[] sensorToIntermediate) {
        glUniformMatrix3fv(glGetUniformLocation(mProgramSensorToIntermediate, "sensorToIntermediate"),
                1, true, sensorToIntermediate, 0);
    }

    public void sensorToIntermediate(boolean histCurve) {
        mSquare.draw(glGetAttribLocation(mProgramSensorToIntermediate, "vPosition"));

        // Calculate a histogram on the result
        int histBins = 512;
        int sampling = 32;
        int[] hist = new int[histBins];

        float[] f = new float[inWidth * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        for (int y = 0; y < inHeight; y += sampling) {
            glReadPixels(0, y, inWidth, 1, GL_RGBA, GL_FLOAT, fb.reset());
            fb.get(f);

            // Loop over all z values
            for (int i = 2; i < f.length; i += 4 * sampling) {
                int bin = (int) (f[i] * histBins);
                if (bin >= histBins) bin = histBins - 1;
                hist[bin]++;
            }
        }

        float[] cumulativeHist = new float[histBins + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + hist[i - 1];
        }
        float max = cumulativeHist[histBins];
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
        }

        float a = 0f;
        float b = 1f;
        if (histCurve) {
            float mid = cumulativeHist[histBins / 12]; // [0,1]
            a = 1f - 2f * mid;
            b = 2f * mid;
            // ax² + bx
        }

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

        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateWidth"),
                inWidth);

        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateHeight"),
                inHeight);

        glUniform1fv(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateHist"),
                cumulativeHist.length, cumulativeHist, 0);

        glUniform2f(glGetUniformLocation(mProgramIntermediateToSRGB, "histCurve"),
                a, b);
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

    public void setDenoiseRadius(int maxRadiusDenoise) {
        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "maxRadiusDenoise"),
                maxRadiusDenoise);
    }

    public void setSharpenFactor(float sharpenFactor) {
        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "sharpenFactor"),
                sharpenFactor);
    }

    public void setSaturationCurve(float[] saturationFactor) {
        glUniform3f(glGetUniformLocation(mProgramIntermediateToSRGB, "saturationCurve"),
                saturationFactor[0], saturationFactor[1], saturationFactor[2]);
    }

    public void setHistoFactor(float histoFactor) {
        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "histFactor"),
                histoFactor);
    }

    public void setOutOffset(int offsetX, int offsetY) {
        glUniform2i(glGetUniformLocation(mProgramIntermediateToSRGB, "outOffset"),
                offsetX, offsetY);
    }

    public void intermediateToOutput(int outWidth, int y, int height) {
        glViewport(0, 0, outWidth, height);
        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "yOffset"), y);
        mSquare.draw(glGetAttribLocation(mProgramSensorToIntermediate, "vPosition"));
    }

    public void close() {
        // Clean everything up
        glDeleteProgram(mProgramSensorToIntermediate);
        glDeleteProgram(mProgramIntermediateToSRGB);
    }
}
