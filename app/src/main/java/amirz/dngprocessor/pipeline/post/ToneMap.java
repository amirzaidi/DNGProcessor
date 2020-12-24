package amirz.dngprocessor.pipeline.post;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS;
import static android.opengl.GLES20.*;

public class ToneMap extends Stage {
    private static final String TAG = "ToneMap";

    private final int[] mFbo = new int[1];
    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private final float[] mXYZtoProPhoto, mProPhotoToSRGB;

    private final int ditherSize = 128;
    private final byte[] dither = new byte[ditherSize * ditherSize * 2];

    private Texture mDitherTex;

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

        // Load intermediate buffers as textures
        Texture highRes = previousStages.getStage(MergeDetail.class).getIntermediate();
        converter.setTexture("highRes", highRes);
        converter.seti("intermediateWidth", highRes.getWidth());
        converter.seti("intermediateHeight", highRes.getHeight());

        if (mProcessParams.lce) {
            BlurLCE blur = previousStages.getStage(BlurLCE.class);
            converter.setTexture("weakBlur", blur.getWeakBlur());
            converter.setTexture("mediumBlur", blur.getMediumBlur());
            converter.setTexture("strongBlur", blur.getStrongBlur());
        }

        float satLimit = mProcessParams.satLimit;
        Log.d(TAG, "Saturation limit " + satLimit);
        converter.setf("satLimit", satLimit);

        converter.setf("toneMapCoeffs", CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        converter.setf("XYZtoProPhoto", mXYZtoProPhoto);
        converter.setf("proPhotoToSRGB", mProPhotoToSRGB);
        converter.seti("outOffset", mSensorParams.outputOffsetX, mSensorParams.outputOffsetY);

        converter.seti("lce", mProcessParams.lce ? 1 : 0);
        //NoiseReduce.NRParams nrParams = previousStages.getStage(NoiseReduce.class).getNRParams();
        //converter.setf("sharpenFactor", nrParams.sharpenFactor);
        //converter.setf("adaptiveSaturation", nrParams.adaptiveSaturation, nrParams.adaptiveSaturationPow);
        converter.setf("sharpenFactor", mProcessParams.sharpenFactor);
        converter.setf("adaptiveSaturation", mProcessParams.adaptiveSaturation);

        float[] saturation = mProcessParams.saturationMap;
        float[] sat = new float[saturation.length + 1];
        System.arraycopy(saturation, 0, sat, 0, saturation.length);
        sat[saturation.length] = saturation[0];

        Texture satTex = new Texture(sat.length, 1, 1, Texture.Format.Float16,
                FloatBuffer.wrap(sat), GL_LINEAR, GL_CLAMP_TO_EDGE);
        converter.setTexture("saturation", satTex);

        // Fill with noise
        new Random(8682522807148012L).nextBytes(dither);
        mDitherTex = new Texture(ditherSize, ditherSize, 1, Texture.Format.UInt16,
                ByteBuffer.wrap(dither));
        converter.setTexture("ditherTex", mDitherTex);
        converter.seti("ditherSize", ditherSize);

        glBindFramebuffer(GL_FRAMEBUFFER, mFbo[0]);
    }

    @Override
    public int getShader() {
        return R.raw.stage3_3_tonemap_fs;
    }

    @Override
    public void close() {
        mDitherTex.close();
    }
}
