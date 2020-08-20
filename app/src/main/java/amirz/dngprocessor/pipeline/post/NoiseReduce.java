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
        return mDenoisedLayers[0];
    }

    private Texture[] getDenoisedLayers() {
        return mDenoisedLayers;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();
        Texture[] layers = previousStages.getStage(Decompose.class).getLayers();
        if (mProcessParams.denoiseFactor == 0) {
            mDenoisedLayers = layers;
            return;
        }

        mDenoisedLayers = new Texture[layers.length];
        Texture[] noise = previousStages.getStage(NoiseMap.class).getNoiseTex();

        for (int i = 0; i < layers.length; i++) {
            converter.useProgram(R.raw.stage3_1_noise_reduce_fs);

            converter.seti("bufSize", layers[i].getWidth(), layers[i].getHeight());
            converter.setTexture("noiseTex", noise[i]);
            converter.seti("radius", 3 << (i * 2), 1 << (i * 2)); // Radius, Sampling
            converter.setf("sigma", 2f * (1 << (i * 2)), 3f / (i + 1)); // Spatial, Color
            converter.setf("blendY", 3f / (2f + layers.length - i));

            mDenoisedLayers[i] = new Texture(layers[i]);

            converter.setTexture("inBuffer", layers[i]);
            converter.drawBlocks(mDenoisedLayers[i]);
        }

        converter.useProgram(R.raw.stage3_1_noise_reduce_remove_noise_fs);

        converter.setTexture("bufDenoisedHighRes", mDenoisedLayers[0]);
        converter.setTexture("bufDenoisedMediumRes", mDenoisedLayers[1]);
        converter.setTexture("bufDenoisedLowRes", mDenoisedLayers[2]);
        converter.setTexture("bufNoisyMediumRes", layers[1]);
        converter.setTexture("bufNoisyLowRes", layers[2]);
        converter.setTexture("noiseTexMediumRes", noise[1]);
        converter.setTexture("noiseTexLowRes", noise[2]);

        converter.drawBlocks(layers[0], true);
        layers[1].close();
        layers[2].close();

        mDenoisedLayers[0].close();
        mDenoisedLayers[1].close();
        mDenoisedLayers[2].close();

        mDenoisedLayers[0] = layers[0];
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
