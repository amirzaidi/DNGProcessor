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

public class GLProgram extends GLProgramBase {
    private static final String TAG = "GLProgram";

    private final GLSquare mSquare = new GLSquare();
    private final int mProgramSensorToIntermediate;
    private final int mProgramIntermediateAnalysis;
    private final int mProgramIntermediateDownscale;
    private final int mProgramIntermediateToSRGB;

    private int inWidth, inHeight;
    private final int[] mIntermediateTex = new int[2];
    private float[] zRange;
    private float[] sigma;

    public GLProgram() {
        int vertexShader = loadShader(GL_VERTEX_SHADER, Shaders.VS);

        mProgramSensorToIntermediate = glCreateProgram();
        glAttachShader(mProgramSensorToIntermediate, vertexShader);
        glAttachShader(mProgramSensorToIntermediate, loadShader(GL_FRAGMENT_SHADER, Shaders.FS1));

        mProgramIntermediateAnalysis = glCreateProgram();
        glAttachShader(mProgramIntermediateAnalysis, vertexShader);
        glAttachShader(mProgramIntermediateAnalysis, loadShader(GL_FRAGMENT_SHADER, Shaders.FS2));

        mProgramIntermediateDownscale = glCreateProgram();
        glAttachShader(mProgramIntermediateDownscale, vertexShader);
        glAttachShader(mProgramIntermediateDownscale, loadShader(GL_FRAGMENT_SHADER, Shaders.FS3));

        mProgramIntermediateToSRGB = glCreateProgram();
        glAttachShader(mProgramIntermediateToSRGB, vertexShader);
        glAttachShader(mProgramIntermediateToSRGB, loadShader(GL_FRAGMENT_SHADER, Shaders.FS4));

        // Link first program
        useProgram(mProgramSensorToIntermediate);
    }

    public void setIn(byte[] in, int inWidth, int inHeight) {
        this.inWidth = inWidth;
        this.inHeight = inHeight;

        // Generate intermediate textures
        glGenTextures(2, mIntermediateTex, 0);

        // Texture 1 for per-CFA pixel data
        glActiveTexture(0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth, inHeight, 0, GL_RGB, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Texture 2 for downscaled data
        // In height and width are always even because of the CFA layout
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[1]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB16F, inWidth / 2, inHeight / 2, 0, GL_RGB, GL_FLOAT, null);
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

        seti("rawBuffer", 0);
        seti("rawWidth", inWidth);
        seti("rawHeight", inHeight);

        // Configure frame buffer
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mIntermediateTex[0], 0);

        glViewport(0, 0, inWidth, inHeight);
    }

    public void setCfaPattern(int cfaPattern) {
        setui("cfaPattern", cfaPattern);
    }

    public void setBlackWhiteLevel(int[] blackLevel, int whiteLevel) {
        setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
        setf("whiteLevel", whiteLevel);
    }

    public void setNeutralPoint(Rational[] neutralPoint) {
        setf("neutralPoint",
                neutralPoint[0].floatValue(),
                neutralPoint[1].floatValue(),
                neutralPoint[2].floatValue());
    }

    public void setTransforms1(float[] sensorToXYZ) {
        setf("sensorToXYZ", sensorToXYZ);
    }


    public void sensorToIntermediate() {
        mSquare.draw(vPosition());
        glFlush();
    }

    public void setOutOffset(int offsetX, int offsetY) {
        seti("outOffset", offsetX, offsetY);
    }

    public void analyzeIntermediate(int w, int h, int samplingFactor,
                                    float[] stretchPerc) {
        // Analyze
        useProgram(mProgramIntermediateAnalysis);

        w /= samplingFactor;
        h /= samplingFactor;

        int[] analyzeTex = new int[1];
        glGenTextures(1, analyzeTex, 0);

        glActiveTexture(0);
        glBindTexture(GL_TEXTURE_2D, analyzeTex[0]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, w, h, 0, GL_RGBA, GL_FLOAT, null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);

        // Load intermediate buffer as texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);

        // Configure frame buffer
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, analyzeTex[0], 0);

        glViewport(0, 0, w, h);
        seti("samplingFactor", samplingFactor);
        mSquare.draw(vPosition());
        glFlush();

        int whPixels = w * h;
        float[] f = new float[whPixels * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(f);

        // Calculate a histogram on the result
        int histBins = 512;
        int[] hist = new int[histBins];

        // Loop over all values
        sigma = new float[3];
        for (int i = 0; i < f.length; i += 4) {
            for (int j = 0; j < 3; j++) {
                sigma[j] += f[i + j];
            }

            int bin = (int) (f[i + 3] * histBins);
            if (bin >= histBins) bin = histBins - 1;
            hist[bin]++;
        }

        for (int j = 0; j < 3; j++) {
            sigma[j] /= whPixels;
        }

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

        zRange = new float[] {
                0.5f * ((float) minZ) / histBins,
                0.5f * ((float) maxZ) / histBins + 0.5f
        };

        Log.d(TAG, "Sigma " + Arrays.toString(sigma));
        Log.d(TAG, "Z Range: " + Arrays.toString(zRange));
    }

    public void downscaleIntermediate() {
        useProgram(mProgramIntermediateDownscale);

        // Configure frame buffer
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mIntermediateTex[1], 0);

        glViewport(0, 0, inWidth / 2, inHeight / 2);
        mSquare.draw(vPosition());
        glFlush();
    }

    public void prepareForOutput() {
        // Now switch to the last program
        useProgram(mProgramIntermediateToSRGB);

        glBindFramebuffer(GL_FRAMEBUFFER, 0);

        // Load intermediate buffers as textures
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[0]);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, mIntermediateTex[1]);

        seti("intermediateWidth", inWidth);
        seti("intermediateHeight", inHeight);
        setf("zRange", zRange);
        setf("sigma", sigma);
    }

    public void setToneMapCoeffs(float[] toneMapCoeffs) {
        setf("toneMapCoeffs", toneMapCoeffs);
    }

    public void setTransforms2(float[] intermediateToProPhoto, float[] proPhotoToSRGB) {
        setf("intermediateToProPhoto", intermediateToProPhoto);
        setf("proPhotoToSRGB", proPhotoToSRGB);
    }

    public void setDenoiseFactor(int denoiseFactor) {
        seti("radiusDenoise", (int)((float) denoiseFactor * (sigma[0] + sigma[1])));
    }

    public void setSharpenFactor(float sharpenFactor) {
        setf("sharpenFactor", Math.max(sharpenFactor - 9f * (sigma[0] + sigma[1]), 0));
    }

    public void setSaturationCurve(float[] saturationFactor) {
        setf("saturationCurve", saturationFactor);
    }

    public void intermediateToOutput(int outWidth, int y, int height) {
        glViewport(0, 0, outWidth, height);
        seti("yOffset", y);
        mSquare.draw(vPosition());
        glFlush();
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
