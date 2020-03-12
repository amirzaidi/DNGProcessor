package amirz.dngprocessor.pipeline.intermediate;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.math.Histogram;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.GL_FLOAT;
import static android.opengl.GLES20.GL_RGBA;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.glReadPixels;
import static android.opengl.GLES20.glViewport;

public class SampleHistogram extends Stage {
    private static final String TAG = "SampleHistogram";

    private final int mOutWidth, mOutHeight, mOffsetX, mOffsetY;

    public SampleHistogram(int outWidth, int outHeight, int offsetX, int offsetY) {
        mOutWidth = outWidth;
        mOutHeight = outHeight;
        mOffsetX = offsetX;
        mOffsetY = offsetY;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.seti("outOffset", mOffsetX, mOffsetY);

        int w = mOutWidth;
        int h = mOutHeight;
        int samplingFactor = 32;

        // Analyze
        w /= samplingFactor;
        h /= samplingFactor;

        GLTex analyzeTex = new GLTex(w, h, 4, GLTex.Format.Float16, null);

        // Load intermediate buffer as texture
        converter.mIntermediate.bind(GL_TEXTURE0);

        // Configure frame buffer
        analyzeTex.setFrameBuffer();

        glViewport(0, 0, w, h);
        converter.seti("samplingFactor", samplingFactor);
        converter.draw();

        int whPixels = w * h;
        float[] f = new float[whPixels * 4];
        FloatBuffer fb = ByteBuffer.allocateDirect(f.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.mark();

        glReadPixels(0, 0, w, h, GL_RGBA, GL_FLOAT, fb.reset());
        fb.get(f);

        analyzeTex.close();

        // Calculate a histogram on the result
        Histogram histParser = new Histogram(f, whPixels);
        converter.sigma = histParser.sigma;
        converter.hist = histParser.hist;

        Log.d(TAG, "Sigma " + Arrays.toString(converter.sigma));
        Log.d(TAG, "LogAvg " + histParser.logAvgLuminance);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_1_fs;
    }
}
