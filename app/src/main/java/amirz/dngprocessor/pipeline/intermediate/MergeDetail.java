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

        Analysis sampleHistogram = previousStages.getStage(Analysis.class);
        float[] hist = sampleHistogram.getHist().clone();
        for (int i = 0; i < hist.length; i++) {
            hist[i] = (float) Math.pow(hist[i], mHistCurve);
        }

        Texture histTex = new Texture(hist.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        histTex.bind(GL_TEXTURE6);
        converter.seti("hist", 6);
        converter.setf("histOffset", 0.5f / hist.length, 1.f - 1.f / hist.length);

        // If there are many dark patches, the color noise goes up.
        // To ensure that we do not boost that too much, reduce with color noise.
        float[] sigma = sampleHistogram.getSigma();
        float baseBase = Math.max(1f - 6f * (float) Math.hypot(sigma[0], sigma[1]), 0f);
        Log.d(TAG, "Bilateral histogram equalization " + baseBase);
        converter.setf("histFactor", baseBase * mHistFactor);

        intermediateTex.bind(GL_TEXTURE0);
        bilateralTex.bind(GL_TEXTURE2);
        converter.seti("intermediate", 0);
        converter.seti("bilateral", 2);

        Texture noiseTex = previousStages.getStage(NoiseMap.class).getNoiseTex();
        noiseTex.bind(GL_TEXTURE4);
        converter.seti("noiseTex", 4);

        mIntermediate = new Texture(w, h, 3, Texture.Format.Float16, null);
        converter.drawBlocks(mIntermediate);

        intermediateTex.close();
        bilateralTex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_4_merge_detail;
    }
}
