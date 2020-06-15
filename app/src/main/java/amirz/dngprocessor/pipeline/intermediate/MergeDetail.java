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

    private static final float MIN_GAMMA = 0.6f;

    private final float mHistFactor;
    private Texture mIntermediate;

    public MergeDetail(ProcessParams processParams) {
        mHistFactor = processParams.histFactor;
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
        float[] hist = sampleHistogram.getHist();

        Texture histTex = new Texture(hist.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        converter.setTexture("hist", histTex);
        converter.setf("histOffset", 0.5f / hist.length, 1.f - 1.f / hist.length);

        // If there are many dark patches, the color noise goes up.
        // To ensure that we do not boost that too much, reduce with color noise.
        float[] sigma = sampleHistogram.getSigma();
        float baseBase = Math.max(0f,
                1f - 8f * (float) Math.hypot(sigma[0], sigma[1])
                - sampleHistogram.getGamma() * 0.5f);

        Log.d(TAG, "Bilateral histogram equalization " + baseBase);
        converter.setf("histFactor", baseBase * mHistFactor);

        // Keep dark images dark.
        converter.setf("gamma", Math.max(MIN_GAMMA, sampleHistogram.getGamma()));

        converter.setTexture("intermediate", intermediateTex);
        converter.setTexture("bilateral", bilateralTex);

        Texture noiseTex = previousStages.getStage(NoiseMap.class).getNoiseTex();
        converter.setTexture("noiseTex", noiseTex);

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
