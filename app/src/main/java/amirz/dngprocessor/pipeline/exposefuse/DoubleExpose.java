package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

public class DoubleExpose extends Stage {
    private Texture mUnderexposed;
    private Texture mOverexposed;

    public Texture getUnderexposed() {
        return mUnderexposed;
    }

    public Texture getOverexposed() {
        return mOverexposed;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Texture normalExposure = previousStages.getStage(EdgeMirror.class).getIntermediate();

        mUnderexposed = new Texture(normalExposure.getWidth(), normalExposure.getHeight(),
                1, Texture.Format.Float16, null);
        converter.setTexture("buf", normalExposure);
        converter.setf("factor", 0.67f);
        converter.drawBlocks(mUnderexposed);

        mOverexposed = new Texture(mUnderexposed);
        converter.setTexture("buf", normalExposure);
        converter.setf("factor", 1.5f);
        converter.drawBlocks(mOverexposed);
    }

    @Override
    public int getShader() {
        return R.raw.stage4_1_doubleexpose;
    }
}
