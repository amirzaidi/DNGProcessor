package amirz.dngprocessor.pipeline.noisereduce;

import android.util.Log;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

public class NoiseReduce extends Stage {
    private static final String TAG = "NoiseReduce";

    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private Texture mDenoised;

    public NoiseReduce(SensorParams sensor, ProcessParams process) {
        mSensorParams = sensor;
        mProcessParams = process;
    }

    public Texture getDenoised() {
        return mDenoised;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Texture[] layers = previousStages.getStage(Decompose.class).getLayers();
        mDenoised = layers[0];

        if (mProcessParams.denoiseFactor == 0) {
            for (int i = 1; i < layers.length; i++) {
                layers[i].close();
            }
            return;
        }

        Texture[] denoised = new Texture[layers.length];
        Texture[] noise = previousStages.getStage(NoiseMap.class).getNoiseTex();

        // Gaussian pyramid denoising.
        for (int i = 0; i < layers.length; i++) {
            converter.useProgram(R.raw.stage3_1_noise_reduce_fs);

            converter.seti("bufSize", layers[i].getWidth(), layers[i].getHeight());
            converter.setTexture("noiseTex", noise[i]);
            converter.seti("radius", 3 << (i * 2), 1 << (i * 2)); // Radius, Sampling
            converter.setf("sigma", 2f * (1 << (i * 2)), 3f / (i + 1)); // Spatial, Color
            converter.setf("blendY", 3f / (2f + layers.length - i));

            denoised[i] = new Texture(layers[i]);

            converter.setTexture("inBuffer", layers[i]);
            converter.drawBlocks(denoised[i]);
        }

        // Merge all layers.
        converter.useProgram(R.raw.stage3_1_noise_reduce_remove_noise_fs);

        converter.setTexture("bufDenoisedHighRes", denoised[0]);
        converter.setTexture("bufDenoisedMediumRes", denoised[1]);
        converter.setTexture("bufDenoisedLowRes", denoised[2]);
        converter.setTexture("bufNoisyMediumRes", layers[1]);
        converter.setTexture("bufNoisyLowRes", layers[2]);
        converter.setTexture("noiseTexMediumRes", noise[1]);
        converter.setTexture("noiseTexLowRes", noise[2]);

        // Reuse original high res noisy texture.
        converter.drawBlocks(mDenoised);

        // Cleanup.
        denoised[0].close();
        for (int i = 1; i < layers.length; i++) {
            layers[i].close();
            denoised[i].close();
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
