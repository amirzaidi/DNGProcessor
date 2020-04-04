package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.intermediate.Analysis;
import amirz.dngprocessor.pipeline.intermediate.NoiseMap;

import static android.opengl.GLES20.*;

public class NoiseReduce extends Stage {
    private static final String TAG = "NoiseReduce";

    private final ProcessParams mProcessParams;
    private NRParams mNRParams;
    private Texture mDenoised;

    public NoiseReduce(ProcessParams process) {
        mProcessParams = process;
    }

    public NRParams getNRParams() {
        return mNRParams;
    }

    public Texture getDenoised() {
        return mDenoised;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        Texture noisy = previousStages.getStage(MergeDetail.class).getIntermediate();
        mDenoised = noisy;
        mNRParams = new NoiseReduce.NRParams(mProcessParams,
                previousStages.getStage(Analysis.class).getSigma());

        if (mProcessParams.denoiseFactor == 0) {
            return;
        }

        GLPrograms converter = getConverter();

        noisy.bind(GL_TEXTURE0);
        int w = noisy.getWidth();
        int h = noisy.getHeight();

        converter.seti("intermediateBuffer", 0);
        converter.seti("intermediateWidth", w);
        converter.seti("intermediateHeight", h);

        converter.seti("radiusDenoise", mNRParams.denoiseFactor);
        converter.setf("sigma", mNRParams.sigma);
        converter.setf("sharpenFactor", mNRParams.sharpenFactor);

        Texture noiseMap = previousStages.getStage(NoiseMap.class).getNoiseTex();
        noiseMap.bind(GL_TEXTURE2);
        converter.seti("noiseTex", 2);

        try (Texture tmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            converter.useProgram(R.raw.stage2_3_bilateral);

            tmp.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);

            converter.setf("sigma", 0.01f, 0.92f);
            converter.seti("radius", 4, 1);

            mDenoised.setFrameBuffer();
            converter.drawBlocks(w, h);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_noise_reduce_fs;
    }

    /**
     * Noise Reduction Parameters.
     */
    static class NRParams {
        final float[] sigma;
        final int denoiseFactor;
        final float sharpenFactor;
        final float adaptiveSaturation, adaptiveSaturationPow;

        private NRParams(ProcessParams params, float[] s) {
            sigma = s;

            float hypot = (float) Math.hypot(s[0], s[1]);
            Log.d(TAG, "Chroma noise hypot " + hypot);

            denoiseFactor = (int)((float) params.denoiseFactor * Math.sqrt(s[0] + s[1]));
            Log.d(TAG, "Denoise radius " + denoiseFactor);

            sharpenFactor = Math.max(params.sharpenFactor - 6f * hypot, -0.25f);
            Log.d(TAG, "Sharpen factor " + sharpenFactor);

            adaptiveSaturation = Math.max(0f, params.adaptiveSaturation[0] - 30f * hypot);
            adaptiveSaturationPow = params.adaptiveSaturation[1];
            Log.d(TAG, "Adaptive saturation " + adaptiveSaturation);
        }
    }
}
