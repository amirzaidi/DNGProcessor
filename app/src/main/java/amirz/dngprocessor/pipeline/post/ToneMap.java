package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import java.nio.FloatBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS;
import static android.opengl.GLES20.*;

public class ToneMap extends Stage {
    private static final String TAG = "ToneMap";

    private final int[] mFbo = new int[1];
    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private final float[] mXYZtoProPhoto, mProPhotoToSRGB;

    public ToneMap(SensorParams sensor, ProcessParams process,
                   float[] XYZtoProPhoto, float[] proPhotoToSRGB) {
        mSensorParams = sensor;
        mProcessParams = process;
        mXYZtoProPhoto = XYZtoProPhoto;
        mProPhotoToSRGB = proPhotoToSRGB;
    }

    @Override
    public void init(GLPrograms converter) {
        super.init(converter);
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, mFbo, 0);
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        glBindFramebuffer(GL_FRAMEBUFFER, mFbo[0]);

        float satLimit = mProcessParams.satLimit;

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        // Load intermediate buffers as textures
        Texture intermediate = previousStages.getStage(MergeDetail.class).getIntermediate();
        intermediate.bind(GL_TEXTURE0);
        converter.seti("intermediateBuffer", 0);
        converter.seti("intermediateWidth", preProcess.getInWidth());
        converter.seti("intermediateHeight", preProcess.getInHeight());

        if (mProcessParams.lce) {
            BlurLCE blur = previousStages.getStage(BlurLCE.class);
            blur.getWeakBlur().bind(GL_TEXTURE2);
            converter.seti("weakBlur", 2);
            blur.getMediumBlur().bind(GL_TEXTURE4);
            converter.seti("mediumBlur", 4);
            blur.getStrongBlur().bind(GL_TEXTURE6);
            converter.seti("strongBlur", 6);
        }

        SampleHistogram sampleHistogram = previousStages.getStage(SampleHistogram.class);
        float[] sigma = sampleHistogram.getSigma();

        converter.setf("sigma", sigma);

        Log.d(TAG, "Saturation limit " + satLimit);
        converter.setf("satLimit", satLimit);

        converter.seti("lce", mProcessParams.lce ? 1 : 0);
        converter.setf("toneMapCoeffs", CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        converter.setf("XYZtoProPhoto", mXYZtoProPhoto);
        converter.setf("proPhotoToSRGB", mProPhotoToSRGB);
        converter.seti("outOffset", mSensorParams.outputOffsetX, mSensorParams.outputOffsetY);

        int denoiseFactor = (int)((float) mProcessParams.denoiseFactor
                * Math.sqrt(sigma[0] + sigma[1]));

        float sharpenFactor = mProcessParams.sharpenFactor - 6f
                * (float) Math.hypot(sigma[0], sigma[1]);

        float adaptiveSaturation = Math.max(0, mProcessParams.adaptiveSaturation[0] - 25f
                * (float) Math.hypot(sigma[0], sigma[1]));
        float adaptiveSaturationPow = mProcessParams.adaptiveSaturation[1];

        Log.d(TAG, "Denoise radius " + denoiseFactor);
        Log.d(TAG, "Sharpen " + sharpenFactor);
        Log.d(TAG, "Adaptive saturation " + adaptiveSaturation);

        converter.seti("radiusDenoise", denoiseFactor);
        converter.setf("sharpenFactor", Math.max(sharpenFactor, -0.25f));
        converter.setf("adaptiveSaturation", adaptiveSaturation, adaptiveSaturationPow);

        float[] saturation = mProcessParams.saturationMap;
        float[] sat = new float[saturation.length + 1];
        System.arraycopy(saturation, 0, sat, 0, saturation.length);
        sat[saturation.length] = saturation[0];

        float saturationReduction = Math.max(1.f, 0.9f + (float) Math.hypot(sigma[0], sigma[1]));
        Log.d(TAG, "Saturation reduction " + saturationReduction);
        for (int i = 0; i < sat.length; i++) {
            sat[i] /= saturationReduction;
        }

        Texture satTex = new Texture(sat.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(sat), GL_LINEAR, GL_CLAMP_TO_EDGE);
        satTex.bind(GL_TEXTURE8);

        converter.seti("saturation", 8);
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_fs;
    }
}
