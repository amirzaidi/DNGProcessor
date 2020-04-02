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
    private Texture mDenoised;

    public NoiseReduce(ProcessParams process) {
        mProcessParams = process;
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

        SampleHistogram sampleHistogram = previousStages.getStage(SampleHistogram.class);
        float[] sigma = sampleHistogram.getSigma();
        int denoiseFactor = (int)((float) mProcessParams.denoiseFactor
                * Math.sqrt(sigma[0] + sigma[1]));
        Log.d(TAG, "Denoise radius " + denoiseFactor);
        converter.seti("radiusDenoise", denoiseFactor);
        converter.setf("sigma", sigma);

        float hypot = (float) Math.hypot(sigma[0], sigma[1]);
        float sharpenFactor = Math.max(mProcessParams.sharpenFactor - 6f * hypot, -0.25f);
        converter.setf("sharpenFactor", sharpenFactor);

        try (Texture tmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            converter.useProgram(R.raw.stage2_1_bilateral);

            tmp.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);

            converter.setf("sigma", 0.015f, 0.75f);
            converter.seti("radius", 4, 1);

            mDenoised.setFrameBuffer();
            converter.drawBlocks(w, h);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_noise_reduce_fs;
    }
}
