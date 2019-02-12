package amirz.dngprocessor.gl;

import android.util.Log;
import android.util.Rational;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;
import static javax.microedition.khronos.opengles.GL10.GL_RGB;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLProgram {
    private static final String TAG = "GLProgram";

    private final GLSquare mSquare = new GLSquare();
    private final int mProgramSensorToIntermediate;
    private final int mProgramIntermediateAnalysis;
    private final int mProgramIntermediateToSRGB;

    private int inWidth, inHeight;
    private final int[] mIntermediateTex = new int[1];
    private float a, b;
    private float[] zRange;
    private float chromaSigma;
    private float lumaSigma;

    public GLProgram() {
        int vertexShader = loadShader(
                GL_VERTEX_SHADER,
                Shaders.VS);

        mProgramSensorToIntermediate = glCreateProgram();
        glAttachShader(mProgramSensorToIntermediate, vertexShader);
        glAttachShader(mProgramSensorToIntermediate, loadShader(GL_FRAGMENT_SHADER, Shaders.FS1));

        mProgramIntermediateAnalysis = glCreateProgram();
        glAttachShader(mProgramIntermediateAnalysis, vertexShader);
        glAttachShader(mProgramIntermediateAnalysis, loadShader(GL_FRAGMENT_SHADER, Shaders.FS2));

        mProgramIntermediateToSRGB = glCreateProgram();
        glAttachShader(mProgramIntermediateToSRGB, vertexShader);
        glAttachShader(mProgramIntermediateToSRGB, loadShader(GL_FRAGMENT_SHADER, Shaders.FS3));

        // Link first program
        glLinkProgram(mProgramSensorToIntermediate);
        glUseProgram(mProgramSensorToIntermediate);
    }


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
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
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

    public void setTransforms1(float[] sensorToXYZ) {
        glUniformMatrix3fv(glGetUniformLocation(mProgramSensorToIntermediate, "sensorToXYZ"),
                1, true, sensorToXYZ, 0);
    }

    public void sensorToIntermediate() {
        mSquare.draw(glGetAttribLocation(mProgramSensorToIntermediate, "vPosition"));
    }

    public void setOutOffset(int offsetX, int offsetY) {
        glUniform2i(glGetUniformLocation(mProgramIntermediateToSRGB, "outOffset"),
                offsetX, offsetY);
    }

    public void analyzeIntermediate(int w, int h, int samplingFactor,
                                    boolean histEqualization, float[] stretchPerc) {
        // Analyze
        glLinkProgram(mProgramIntermediateAnalysis);
        glUseProgram(mProgramIntermediateAnalysis);

        w /= samplingFactor;
        h /= samplingFactor;

        int[] analyzeTex = new int[1];
        glGenTextures(1, analyzeTex, 0);

        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, analyzeTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Load intermediate buffer as texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth, inHeight, 0, GL_RGB, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Configure frame buffer
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, analyzeTex[0], 0);

        glViewport(0, 0, w, h);
        glUniform1i(glGetUniformLocation(mProgramIntermediateAnalysis, "samplingFactor"),
                samplingFactor);
        mSquare.draw(glGetAttribLocation(mProgramIntermediateAnalysis, "vPosition"));

        int whPixels = w * h;
        float[] f = new float[whPixels * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(f);

        // Calculate a histogram on the result
        int histBins = 512;
        int[] hist = new int[histBins];

        float chromaSigmaTotal = 0f;
        float lumaSigmaTotal = 0f;

        // Loop over all values
        for (int i = 0; i < f.length; i += 4) {
            int bin = (int) (f[i] * histBins);
            if (bin >= histBins) bin = histBins - 1;
            hist[bin]++;

            chromaSigmaTotal += f[i + 1];
            lumaSigmaTotal += f[i + 2];
        }

        chromaSigma = chromaSigmaTotal / whPixels; // [0, 0.2]
        lumaSigma = lumaSigmaTotal / whPixels;

        float[] cumulativeHist = new float[histBins + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + hist[i - 1];
        }

        float max = cumulativeHist[histBins];
        int minZ = 0;
        int maxZ = histBins;
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
            if (cumulativeHist[i] < stretchPerc[0]) {
                minZ = i;
            } else if (cumulativeHist[i] > stretchPerc[1]) {
                maxZ = Math.min(maxZ, i);
            }
        }

        float brightenFactor = 0.5f;
        if (histEqualization) {
            // What fraction of pixels are in the first 25% of luminance
            brightenFactor = cumulativeHist[histBins / 4]; // [0,1]

            // Bring closer to 0.5 to reduce the effect strength
            brightenFactor = (float) (Math.sqrt(brightenFactor) - Math.sqrt(chromaSigma));
            if (brightenFactor < 0f) {
                brightenFactor = 0f;
            } else {
                brightenFactor *= 0.6f;
            }
        }

        // Set quadratic compensation curve based on brightenFactor
        // ax² + bx
        a = 1f - 2f * brightenFactor;
        b = 2f * brightenFactor;

        zRange = new float[] {
                0.5f * ((float) minZ) / histBins,
                0.5f * ((float) maxZ) / histBins + 0.5f
        };

        Log.d(TAG, "ChromaSigma " + chromaSigma + ", LumaSigma " + lumaSigma);
        Log.d(TAG, "Histogram EQ Curve: " + brightenFactor + ", " + a + ", " + b);
        Log.d(TAG, "Z Range: " + Arrays.toString(zRange));
    }

    public void prepareForOutput() {
        // Now switch to the second program
        glLinkProgram(mProgramIntermediateToSRGB);
        glUseProgram(mProgramIntermediateToSRGB);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Load intermediate buffer as texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth, inHeight, 0, GL_RGB, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateWidth"),
                inWidth);

        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "intermediateHeight"),
                inHeight);

        glUniform2f(glGetUniformLocation(mProgramIntermediateToSRGB, "zRange"),
                zRange[0], zRange[1]);

        glUniform2f(glGetUniformLocation(mProgramIntermediateToSRGB, "histCurve"),
                a, b);

        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "chromaSigma"),
                chromaSigma);

        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "lumaSigma"),
                lumaSigma);
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

    public void setDenoiseFactor(int denoiseFactor) {
        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "radiusDenoise"),
                (int)((float) denoiseFactor * chromaSigma));
    }

    public void setSharpenFactor(float sharpenFactor) {
        glUniform1f(glGetUniformLocation(mProgramIntermediateToSRGB, "sharpenFactor"),
                Math.max(sharpenFactor - 9f * chromaSigma, 0));
    }

    public void setSaturationCurve(float[] saturationFactor) {
        glUniform3f(glGetUniformLocation(mProgramIntermediateToSRGB, "saturationCurve"),
                saturationFactor[0], saturationFactor[1], saturationFactor[2]);
    }

    public void intermediateToOutput(int outWidth, int y, int height) {
        glViewport(0, 0, outWidth, height);
        glUniform1i(glGetUniformLocation(mProgramIntermediateToSRGB, "yOffset"), y);
        mSquare.draw(glGetAttribLocation(mProgramSensorToIntermediate, "vPosition"));
    }

    public void close() {
        // Clean everything up
        glDeleteProgram(mProgramSensorToIntermediate);
        glDeleteProgram(mProgramIntermediateAnalysis);
        glDeleteProgram(mProgramIntermediateToSRGB);
    }

    private static int loadShader(int type, String shaderCode) {
        int shader = glCreateShader(type);
        glShaderSource(shader, shaderCode);
        glCompileShader(shader);
        return shader;
    }
}
