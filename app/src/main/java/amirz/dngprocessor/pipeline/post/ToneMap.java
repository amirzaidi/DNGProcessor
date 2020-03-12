package amirz.dngprocessor.pipeline.post;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.CUSTOM_ACR3_TONEMAP_CURVE_COEFFS;
import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glGetIntegerv;

public class ToneMap extends Stage {
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
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.prepareForOutput(mProcessParams.histFactor, mProcessParams.satLimit);
        converter.setf("noiseProfile", mSensorParams.noiseProfile[2], mSensorParams.noiseProfile[3]);
        converter.setLCE(mProcessParams.lce);
        converter.setAHE(mProcessParams.ahe);
        converter.setToneMapCoeffs(CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        converter.setTransforms2(mXYZtoProPhoto, mProPhotoToSRGB);
        converter.setDenoiseFactor(mProcessParams.denoiseFactor);
        converter.setSharpenFactor(mProcessParams.sharpenFactor);
        converter.setSaturation(mProcessParams.saturationMap);
        converter.setOutOffset(mSensorParams.outputOffsetX, mSensorParams.outputOffsetY);
    }

    @Override
    public void init(GLProgramRawConverter converter, GLTexPool texPool,
                     ShaderLoader shaderLoader) {
        super.init(converter, texPool, shaderLoader);
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, mFbo, 0);
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_fs;
    }
}
