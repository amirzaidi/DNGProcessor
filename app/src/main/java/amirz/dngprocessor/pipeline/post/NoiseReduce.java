package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.Decompose;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.intermediate.Analysis;
import amirz.dngprocessor.pipeline.intermediate.NoiseMap;

public class NoiseReduce extends Stage {
    private static final String TAG = "NoiseReduce";

    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private NRParams mNRParams;
    private Texture mDenoised;
    private Texture[] mDenoisedLayers;

    public NoiseReduce(SensorParams sensor, ProcessParams process) {
        mSensorParams = sensor;
        mProcessParams = process;
    }

    public NRParams getNRParams() {
        return mNRParams;
    }

    public Texture getDenoised() {
        return mDenoised;
    }

    public Texture[] getDenoisedLayers() {
        return mDenoisedLayers;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();
        Texture[] layers = previousStages.getStage(Decompose.class).getLayers();

        mDenoisedLayers = new Texture[] { layers[0], layers[1], layers[2] };
        if (mProcessParams.denoiseFactor == 0 || true) {
            return;
        }

        Texture[] noise = previousStages.getStage(NoiseMap.class).getNoiseTex();

        converter.seti("inBufferSize", layers[1].getWidth(), layers[1].getHeight());
        converter.setTexture("noiseTex", noise[1]);
        try (Texture tmp = new Texture(mDenoisedLayers[1])) {
            for (int i = 0; i < 0; i++) {
                converter.setTexture("inBuffer", mDenoisedLayers[1]);
                converter.drawBlocks(tmp);

                converter.setTexture("inBuffer", tmp);
                converter.drawBlocks(mDenoisedLayers[1]);
            }
        }

        /*
        Texture noisy = previousStages.getStage(MergeDetail.class).getIntermediate();
        mDenoised = noisy;
        mNRParams = new NoiseReduce.NRParams(mProcessParams,
                previousStages.getStage(Analysis.class).getSigma());

        if (mProcessParams.denoiseFactor == 0) {
            return;
        }

        GLPrograms converter = getConverter();

        int w = noisy.getWidth();
        int h = noisy.getHeight();

        converter.setTexture("inBuffer", noisy);

        int offsetX = mSensorParams.outputOffsetX;
        int offsetY = mSensorParams.outputOffsetY;

        //converter.seti("radiusDenoise", mNRParams.denoiseFactor);
        //converter.setf("sigma", mNRParams.sigma);
        //converter.setf("sharpenFactor", mNRParams.sharpenFactor);

        //Texture noiseTex = previousStages.getStage(NoiseMap.class).getNoiseTex();
        //converter.setTexture("noiseTex", noiseTex);

        try (Texture tmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            //converter.drawBlocks(tmp);

            //converter.useProgram(R.raw.stage2_3_bilateral);

            //converter.seti("bufSize", w, h);
            //converter.setTexture("noiseTex", noiseTex);

            //float s = mNRParams.sigma[0];
            //converter.setf("sigma", 0.015f + 0.1f * s, 0.35f + 9f * s);
            //converter.setf("sigma", 0.1f, 1.5f);
            //converter.seti("radius", 5, 1);

            // Aggressive chroma denoise.
            converter.seti("minxy", offsetX, offsetY);
            converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);
            for (int i = 0; i < 0; i++) {
                converter.setTexture("inBuffer", mDenoised);
                converter.drawBlocks(tmp);

                converter.setTexture("inBuffer", tmp);
                converter.drawBlocks(mDenoised);
            }

            // Mild luma and chroma denoise.
            converter.useProgram(R.raw.stage3_1_noise_reduce_fs);
            converter.seti("minxy", offsetX, offsetY);
            converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);
            for (int i = 0; i < 0; i++) {
                converter.setTexture("inBuffer", mDenoised);
                converter.drawBlocks(tmp);

                converter.setTexture("inBuffer", tmp);
                converter.drawBlocks(mDenoised);
            }
        }
        */
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_noise_reduce_fs;
        //return R.raw.stage3_1_noise_reduce_chroma_fs;
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
