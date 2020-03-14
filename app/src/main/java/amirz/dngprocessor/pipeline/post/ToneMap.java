package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import java.nio.FloatBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;
import amirz.dngprocessor.pipeline.intermediate.SampleHistogram;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS;
import static android.opengl.GLES20.*;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

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
        super.execute(previousStages);
        GLPrograms converter = getConverter();

        glBindFramebuffer(GL_FRAMEBUFFER, mFbo[0]);

        float histFactor = mProcessParams.histFactor;
        float satLimit = mProcessParams.satLimit;

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        // Load intermediate buffers as textures
        Texture intermediate = previousStages.getStage(MergeDetail.class).getIntermediate();
        intermediate.bind(GL_TEXTURE0);

        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        converter.seti("intermediateBuffer", 0);
        converter.seti("intermediateWidth", preProcess.getInWidth());
        converter.seti("intermediateHeight", preProcess.getInHeight());

        SampleHistogram sampleHistogram = previousStages.getStage(SampleHistogram.class);
        float[] sigma = sampleHistogram.getSigma();

        converter.setf("sigma", sigma);

        Log.d(TAG, "Hist factor " + histFactor);
        converter.setf("histFactor", Math.max(histFactor, 0f));

        Log.d(TAG, "Saturation limit " + satLimit);
        converter.setf("satLimit", satLimit);

        converter.setf("noiseProfile", mSensorParams.noiseProfile[2], mSensorParams.noiseProfile[3]);
        converter.seti("lce", mProcessParams.lce ? 1 : 0);
        converter.seti("ahe", mProcessParams.ahe ? 1 : 0);
        converter.setf("toneMapCoeffs", CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        converter.setf("XYZtoProPhoto", mXYZtoProPhoto);
        converter.setf("proPhotoToSRGB", mProPhotoToSRGB);
        converter.seti("outOffset", mSensorParams.outputOffsetX, mSensorParams.outputOffsetY);

        int denoiseFactor = (int)((float) mProcessParams.denoiseFactor
                * Math.sqrt(sigma[0] + sigma[1]));

        float sharpenFactor = mProcessParams.sharpenFactor - 6f
                * (float) Math.hypot(sigma[0], sigma[1]);

        Log.d(TAG, "Denoise radius " + denoiseFactor);
        Log.d(TAG, "Sharpen " + sharpenFactor);

        converter.seti("radiusDenoise", denoiseFactor);
        converter.setf("sharpenFactor", Math.max(sharpenFactor, -0.25f));

        float[] saturation = mProcessParams.saturationMap;
        float[] sat = new float[saturation.length + 1];
        System.arraycopy(saturation, 0, sat, 0, saturation.length);
        sat[saturation.length] = saturation[0];

        Texture satTex = new Texture(sat.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(sat), GL_LINEAR, GL_CLAMP_TO_EDGE);
        satTex.bind(GL_TEXTURE6);

        converter.seti("saturation", 6);
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_fs;
    }
}
