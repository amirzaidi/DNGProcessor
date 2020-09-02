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
import amirz.dngprocessor.pipeline.exposefuse.Merge;
import amirz.dngprocessor.pipeline.exposefuse.Overexpose;
import amirz.dngprocessor.pipeline.noisereduce.NoiseReduce;

import static android.opengl.GLES20.*;

public class MergeDetail extends Stage {
    private static final String TAG = "MergeDetail";

    private static final float MIN_GAMMA = 0.55f;

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

        mIntermediate = previousStages.getStage(Merge.class).getMerged();

        // If there is no bilateral filtered texture, skip this step.
        if (bilateralTex == null) {
            return;
        }

        Analysis sampleHistogram = previousStages.getStage(Analysis.class);
        float[] hist = sampleHistogram.getHist();

        Texture histTex = new Texture(hist.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(hist), GL_LINEAR, GL_CLAMP_TO_EDGE);
        converter.setTexture("hist", histTex);
        converter.setf("histOffset", 0.5f / hist.length, 1.f - 1.f / hist.length);

        // If there are many dark patches, the color noise goes up.
        // To ensure that we do not boost that too much, reduce with color noise.
        float[] sigma = sampleHistogram.getSigma();
        float minGamma = Math.min(1f, MIN_GAMMA + 3f * (float) Math.hypot(sigma[0], sigma[1]));
        float gamma = Math.max(minGamma, 0.35f + 0.65f * sampleHistogram.getGamma());
        Log.d(TAG, "Setting gamma of " + gamma + " (original " + sampleHistogram.getGamma() + ")");
        converter.setf("gamma", gamma);

        // Reduce the histogram equalization in scenes with good light distribution.
        float bilatHistEq = Math.max(0.4f, 1f - sampleHistogram.getGamma() * 0.6f
                - 4f * (float) Math.hypot(sigma[0], sigma[1]));
        Log.d(TAG, "Bilateral histogram equalization " + bilatHistEq);
        converter.setf("histFactor", bilatHistEq * mHistFactor);

        converter.setTexture("intermediate", mIntermediate);
        converter.setTexture("bilateral", bilateralTex);

        converter.drawBlocks(mIntermediate);

        bilateralTex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_4_merge_detail;
    }
}
