package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;

import static android.opengl.GLES20.GL_TEXTURE0;

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

        mNRParams = new NoiseReduce.NRParams(mProcessParams,
                previousStages.getStage(SampleHistogram.class).getSigma());
        converter.seti("radiusDenoise", mNRParams.denoiseFactor);
        converter.setf("sigma", mNRParams.sigma);
        converter.setf("sharpenFactor", mNRParams.sharpenFactor);

        try (Texture tmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            converter.useProgram(R.raw.stage2_1_bilateral);

            tmp.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);

            converter.setf("sigma", 0.007f, 0.9f);
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
        final float desaturateThres;

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

            desaturateThres = Math.max(0f, Math.min(0.04f, hypot - 0.05f));
            Log.d(TAG, "Desaturate threshold " + desaturateThres);
        }
    }
}
