package amirz.dngprocessor.pipeline.intermediate;

import android.util.Log;

import java.nio.FloatBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.*;

public class MergeDetail extends Stage {
    private static final String TAG = "MergeDetail";

    private final float mHistFactor;
    private final float mHistCurve;
    private Texture mIntermediate;

    public MergeDetail(ProcessParams processParams) {
        mHistFactor = processParams.histFactor;
        mHistCurve = processParams.histCurve;
    }

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        BilateralFilter bilateral = previousStages.getStage(BilateralFilter.class);
        Texture bilateralTex = bilateral.getBilateral();

        ToIntermediate intermediate = previousStages.getStage(ToIntermediate.class);
        Texture intermediateTex = intermediate.getIntermediate();

        // If there is no bilateral filtered texture, skip this step.
        if (bilateralTex == null) {
            mIntermediate = intermediateTex;
            return;
        }

        PreProcess preProcess = previousStages.getStage(PreProcess.class);
        int w = preProcess.getInWidth();
        int h = preProcess.getInHeight();

        SampleHistogram sampleHistogram = previousStages.getStage(SampleHistogram.class);
        float[] hist = sampleHistogram.getHist().clone();
        for (int i = 0; i < hist.length; i++) {
            hist[i] = (float) Math.pow(hist[i], mHistCurve);
        }

        Texture histTex = new Texture(hist.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        histTex.bind(GL_TEXTURE6);
        converter.seti("hist", 6);

        // If there are many dark patches, the color noise goes up.
        // To ensure that we do not boost that too much, reduce with color noise.
        float[] sigma = sampleHistogram.getSigma();
        float reduce = Math.max(0f, 2.5f * (float) Math.hypot(sigma[0], sigma[1]) - 0.1f);
        float base = Math.max(1f - reduce, 0.25f);
        float boost = Math.max(0f, 1f - 8f * (float) Math.hypot(sigma[0], sigma[1]));
        Log.d(TAG, "Base " + base + " (Reduce " + reduce + ") - Boost " + boost);
        converter.setf("detail", 20f, base, boost * mHistFactor);

        intermediateTex.bind(GL_TEXTURE0);
        bilateralTex.bind(GL_TEXTURE2);
        converter.seti("intermediate", 0);
        converter.seti("bilateral", 2);
        mIntermediate = new Texture(w, h, 3, Texture.Format.Float16, null);
        mIntermediate.setFrameBuffer();
        converter.drawBlocks(w, h);

        //intermediateTex.close();
        bilateralTex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_detail;
    }
}
