package amirz.dngprocessor.pipeline.intermediate;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.math.Histogram;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.*;

public class Analysis extends Stage {
    private static final String TAG = "SampleHistogram";

    private final int mOutWidth, mOutHeight, mOffsetX, mOffsetY;
    private float[] mSigma, mHist;
    private Texture mAnalyzeTex;

    public Analysis(int outWidth, int outHeight, int offsetX, int offsetY) {
        mOutWidth = outWidth;
        mOutHeight = outHeight;
        mOffsetX = offsetX;
        mOffsetY = offsetY;
    }

    public float[] getSigma() {
        return mSigma;
    }

    public float[] getHist() {
        return mHist;
    }

    public Texture getAnalyzeTex() {
        return mAnalyzeTex;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        converter.useProgram(R.raw.stage2_2_analysis_fs);

        converter.setTexture("intermediate", intermediate);
        converter.seti("outOffset", mOffsetX, mOffsetY);

        int w = mOutWidth;
        int h = mOutHeight;
        int samplingFactor = 16;

        // Analyze
        w /= samplingFactor;
        h /= samplingFactor;

        converter.seti("samplingFactor", samplingFactor);

        mAnalyzeTex = new Texture(w, h, 4, Texture.Format.Float16, null);
        converter.drawBlocks(mAnalyzeTex);

        int whPixels = w * h;
        float[] f = new float[whPixels * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(f);

        // Calculate a histogram on the result
        Histogram histParser = new Histogram(f, whPixels);
        mSigma = histParser.sigma;
        mHist = histParser.hist;

        Log.d(TAG, "Sigma " + Arrays.toString(mSigma));
        Log.d(TAG, "LogAvg " + histParser.logAvgLuminance);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_1_noise_level_fs;
    }
}
