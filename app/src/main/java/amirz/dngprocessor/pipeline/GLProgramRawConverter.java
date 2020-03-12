package amirz.dngprocessor.pipeline;

import android.util.Log;
import android.util.Rational;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLProgramBase;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.math.BlockDivider;
import amirz.dngprocessor.math.Histogram;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLProgramRawConverter extends GLProgramBase {
    private static final String TAG = "GLProgram";

    private final GLTexPool mTexPool = new GLTexPool();

    private final int mProgramHelperDownscale;
    private final int mProgramIntermediateAnalysis;
    private final int mProgramIntermediateBlur;
    private final int mProgramIntermediateHistGen;
    private final int mProgramIntermediateHistBlur;
    private final int mProgramIntermediateBilateral;
    private final int mProgramIntermediateToSRGB;

    private final int[] fbo = new int[1];
    private int inWidth, inHeight, cfaPattern;
    private GLTex mSensorUI, mGainMap, mSensor, mSensorG, mIntermediate, mBlurred, mAHEMap, mDownscaled;
    private GLTex mBilateralFiltered;
    private float[] sigma;
    private float[] hist;

    public int vertexShader;

    public GLProgramRawConverter(ShaderLoader loader) {
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbo, 0);

        vertexShader = loadShader(GL_VERTEX_SHADER, loader.readRaw(R.raw.passthrough_vs));

        mProgramHelperDownscale = createProgram(vertexShader, loader.readRaw(R.raw.helper_downscale_fs));
        mProgramIntermediateAnalysis = createProgram(vertexShader, loader.readRaw(R.raw.stage2_1_fs));
        mProgramIntermediateBlur = createProgram(vertexShader, loader.readRaw(R.raw.stage2_2_fs));
        mProgramIntermediateHistGen = createProgram(vertexShader, loader.readRaw(R.raw.stage2_3_fs));
        mProgramIntermediateHistBlur = createProgram(vertexShader, loader.readRaw(R.raw.stage2_4_fs));
        mProgramIntermediateBilateral = createProgram(vertexShader, loader.readRaw(R.raw.stage3_0_fs));
        mProgramIntermediateToSRGB = createProgram(vertexShader, loader.readRaw(R.raw.stage3_1_fs));
    }

    public GLTexPool getTexPool() {
        return mTexPool;
    }

    public void setIn(byte[] in, int inWidth, int inHeight, int cfaPattern) {
        this.inWidth = inWidth;
        this.inHeight = inHeight;
        this.cfaPattern = cfaPattern;

        // First texture is just for normalization
        mSensor = new GLTex(inWidth, inHeight, 1, GLTex.Format.Float16, null);

        // Now create the input texture and bind it to TEXTURE0
        ByteBuffer buffer = ByteBuffer.allocateDirect(in.length);
        buffer.put(in);
        buffer.flip();

        mSensorUI = new GLTex(inWidth, inHeight, 1, GLTex.Format.UInt16, buffer);
        mSensorUI.bind(GL_TEXTURE0);

        seti("rawBuffer", 0);
        seti("rawWidth", inWidth);
        seti("rawHeight", inHeight);

        mSensor.setFrameBuffer();
    }

    public void setGainMap(float[] gainMap, int[] gainMapSize) {
        seti("gainMap", 2);
        if (gainMap == null) {
            gainMap = new float[] { 1.f, 1.f, 1.f, 1.f };
            gainMapSize = new int[] { 1, 1 };
        }

        mGainMap = new GLTex(gainMapSize[0], gainMapSize[1], 4, GLTex.Format.Float16,
                FloatBuffer.wrap(gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        mGainMap.bind(GL_TEXTURE2);
    }

    public void setBlackWhiteLevel(int[] blackLevel, int whiteLevel) {
        setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
        setf("whiteLevel", whiteLevel);
    }

    public void sensorPreProcess(short[] hotPixels, int[] hotPixelsSize) {
        seti("cfaPattern", cfaPattern);
        seti("hotPixels", 4);
        seti("hotPixelsSize", hotPixelsSize);

        GLTex hotPx = new GLTex(hotPixelsSize[0], hotPixelsSize[1], 1, GLTex.Format.UInt16,
                ShortBuffer.wrap(hotPixels), GL_NEAREST, GL_REPEAT);
        hotPx.bind(GL_TEXTURE4);

        drawBlocks(inWidth, inHeight);

        mSensorUI.delete();
        mGainMap.delete();
        hotPx.delete();
    }

    public void greenDemosaic() {
        seti("rawBuffer", 0);
        seti("rawWidth", inWidth);
        seti("rawHeight", inHeight);

        mSensorG = new GLTex(inWidth, inHeight, 1, GLTex.Format.Float16, null);

        // Load old texture
        mSensor.bind(GL_TEXTURE0);

        // Configure frame buffer
        mSensorG.setFrameBuffer();

        seti("cfaPattern", cfaPattern);
        drawBlocks(inWidth, inHeight);
    }

    public void prepareToIntermediate() {
        seti("rawBuffer", 0);
        seti("greenBuffer", 2);
        seti("rawWidth", inWidth);
        seti("rawHeight", inHeight);

        // Second texture for per-CFA pixel data
        mIntermediate = new GLTex(inWidth, inHeight, 3, GLTex.Format.Float16, null);

        // Load mosaic and green raw texture
        mSensor.bind(GL_TEXTURE0);
        mSensorG.bind(GL_TEXTURE2);

        // Configure frame buffer
        mIntermediate.setFrameBuffer();
    }

    public void setNeutralPoint(Rational[] neutralPoint, byte[] cfaVal) {
        setf("neutralLevel",
                neutralPoint[cfaVal[0]].floatValue(),
                neutralPoint[cfaVal[1]].floatValue(),
                neutralPoint[cfaVal[2]].floatValue(),
                neutralPoint[cfaVal[3]].floatValue());

        setf("neutralPoint",
                neutralPoint[0].floatValue(),
                neutralPoint[1].floatValue(),
                neutralPoint[2].floatValue());
    }

    public void setTransforms1(float[] sensorToXYZ) {
        setf("sensorToXYZ", sensorToXYZ);
    }

    public void sensorToIntermediate() {
        seti("cfaPattern", cfaPattern);
        drawBlocks(inWidth, inHeight);

        mSensor.delete();
        mSensorG.delete();
    }

    public void setOutOffset(int offsetX, int offsetY) {
        seti("outOffset", offsetX, offsetY);
    }

    public void analyzeIntermediate(int w, int h, int samplingFactor) {
        // Analyze
        useProgram(mProgramIntermediateAnalysis);

        w /= samplingFactor;
        h /= samplingFactor;

        GLTex analyzeTex = new GLTex(w, h, 4, GLTex.Format.Float16, null);

        // Load intermediate buffer as texture
        mIntermediate.bind(GL_TEXTURE0);

        // Configure frame buffer
        analyzeTex.setFrameBuffer();

        glViewport(0, 0, w, h);
        seti("samplingFactor", samplingFactor);
        draw();

        int whPixels = w * h;
        float[] f = new float[whPixels * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(f);

        analyzeTex.delete();

        // Calculate a histogram on the result
        Histogram histParser = new Histogram(f, whPixels);
        sigma = histParser.sigma;
        hist = histParser.hist;

        Log.d(TAG, "Sigma " + Arrays.toString(sigma));
        Log.d(TAG, "LogAvg " + histParser.logAvgLuminance);
    }

    public void blurIntermediate(boolean lce, boolean ahe) {
        lce = false;
        ahe = false;

        int w = mIntermediate.getWidth();
        int h = mIntermediate.getHeight();

        useProgram(mProgramHelperDownscale);

        int scale = 8;
        mIntermediate.bind(GL_TEXTURE0);
        seti("buf", 0);
        seti("scale", scale);

        mDownscaled = new GLTex(w / scale, h / scale, 1, GLTex.Format.Float16, null, GL_LINEAR);
        mDownscaled.setFrameBuffer();
        draw();

        // LCE
        if (lce) {
            useProgram(mProgramIntermediateBlur);

            mIntermediate.bind(GL_TEXTURE0);
            seti("sampleBuf", 0);
            seti("blurBuf", 0);
            seti("bufSize", w, h);

            seti("dir", 1, 0); // Right
            setf("ch", 0, 1); // xy[Y]

            GLTex temp = new GLTex(w, h, 1, GLTex.Format.Float16, null);
            temp.setFrameBuffer();
            drawBlocks(w, h);

            temp.bind(GL_TEXTURE0);
            seti("dir", 0, 1); // Down
            setf("ch", 1, 0); // [Y]00

            mBlurred = new GLTex(w, h, 1, GLTex.Format.Float16, null);
            mBlurred.setFrameBuffer();
            drawBlocks(w, h);

            temp.delete();
        }

        // AHE
        if (ahe) {
            useProgram(mProgramIntermediateHistGen);

            mIntermediate.bind(GL_TEXTURE0);
            seti("buf", 0);
            seti("bufSize", w, h);
            seti("mode", 0);
            seti("dir", 1, 0); // Right

            GLTex temp = new GLTex(w / 2, h / 2, 4, GLTex.Format.Float16, null);
            temp.setFrameBuffer();
            drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            seti("bufSize", w / 2, h / 2);
            seti("mode", 1);
            seti("dir", 0, 1); // Down

            mAHEMap = new GLTex(w / 2, h / 2, 4, GLTex.Format.Float16, null, GL_LINEAR);
            mAHEMap.setFrameBuffer();
            drawBlocks(w / 2, h / 2);

            useProgram(mProgramIntermediateHistBlur);

            mAHEMap.bind(GL_TEXTURE0);
            seti("buf", 0);
            seti("bufSize", w / 2, h / 2);
            seti("mode", 0);
            seti("dir", 1, 0); // Right

            temp.setFrameBuffer();
            drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            seti("mode", 1);
            seti("dir", 0, 1); // Down

            mAHEMap.setFrameBuffer();
            drawBlocks(w / 2, h / 2);

            temp.delete();
        }

        // Double bilateral filter.
        useProgram(mProgramIntermediateBilateral);

        seti("buf", 0);
        seti("bufSize", w, h);
        mBilateralFiltered = new GLTex(inWidth, inHeight, 3, GLTex.Format.Float16, null);

        // Two iterations means four total filters.
        for (int i = 0; i < 2; i++) {
            mIntermediate.bind(GL_TEXTURE0);
            mBilateralFiltered.setFrameBuffer();

            drawBlocks(w, h);

            mBilateralFiltered.bind(GL_TEXTURE0);
            mIntermediate.setFrameBuffer();

            drawBlocks(w, h);
        }
    }

    public void prepareForOutput(float histFactor, float satLimit) {
        // Now switch to the last program
        useProgram(mProgramIntermediateToSRGB);

        glBindFramebuffer(GL_FRAMEBUFFER, fbo[0]);

        // Load intermediate buffers as textures
        mIntermediate.bind(GL_TEXTURE0);
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        seti("intermediateBuffer", 0);

        seti("intermediateWidth", inWidth);
        seti("intermediateHeight", inHeight);
        setf("sigma", sigma);

        if (mBlurred != null) {
            seti("blurred", 2);
            mBlurred.bind(GL_TEXTURE2);
        }

        if (mAHEMap != null) {
            seti("ahemap", 4);
            mAHEMap.bind(GL_TEXTURE4);
        }

        GLTex histTex = new GLTex(hist.length, 1, 1, GLTex.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        histTex.bind(GL_TEXTURE6);
        seti("hist", 6);

        mDownscaled.bind(GL_TEXTURE8);
        seti("downscaledBuffer", 8);

        Log.d(TAG, "Hist factor " + histFactor);
        setf("histFactor", Math.max(histFactor, 0f));

        Log.d(TAG, "Saturation limit " + satLimit);
        setf("satLimit", satLimit);
    }

    public void setLCE(boolean lce) {
        seti("lce", lce ? 1 : 0);
    }

    public void setAHE(boolean ahe) {
        seti("ahe", ahe ? 1 : 0);
    }

    public void setToneMapCoeffs(float[] toneMapCoeffs) {
        setf("toneMapCoeffs", toneMapCoeffs);
    }

    public void setTransforms2(float[] XYZtoProPhoto, float[] proPhotoToSRGB) {
        setf("XYZtoProPhoto", XYZtoProPhoto);
        setf("proPhotoToSRGB", proPhotoToSRGB);
    }

    public void setDenoiseFactor(int denoiseFactor) {
        denoiseFactor = (int)((float) denoiseFactor * Math.sqrt(sigma[0] + sigma[1]));
        Log.d(TAG, "Denoise radius " + denoiseFactor);
        seti("radiusDenoise", denoiseFactor);
    }

    public void setSharpenFactor(float sharpenFactor) {
        sharpenFactor -= 7f * Math.hypot(sigma[0], sigma[1]);
        Log.d(TAG, "Sharpen " + sharpenFactor);
        setf("sharpenFactor", Math.max(sharpenFactor, -0.25f));
    }

    public void setSaturation(float[] saturation) {
        float[] sat = new float[saturation.length + 1];
        System.arraycopy(saturation, 0, sat, 0, saturation.length);
        sat[saturation.length] = saturation[0];

        GLTex satTex = new GLTex(sat.length, 1, 1, GLTex.Format.Float16,
                FloatBuffer.wrap(sat), GL_LINEAR, GL_CLAMP_TO_EDGE);
        satTex.bind(GL_TEXTURE6);
        seti("saturation", 6);
    }

    public void intermediateToOutput(int outWidth, int y, int height) {
        glViewport(0, 0, outWidth, height);
        seti("yOffset", y);
        draw();
    }

    private void drawBlocks(int w, int h) {
        BlockDivider divider = new BlockDivider(h, BLOCK_HEIGHT);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            glViewport(0, row[0], w, row[1]);
            draw();
        }
    }
}
