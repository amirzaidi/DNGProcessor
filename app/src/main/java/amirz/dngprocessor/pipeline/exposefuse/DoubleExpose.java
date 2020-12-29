package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
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

        mUnderexposed = TexturePool.get(normalExposure.getWidth(), normalExposure.getHeight(), 1,
                Texture.Format.Float16);
        converter.setTexture("buf", normalExposure);
        converter.setf("factor", 0.8f);
        converter.drawBlocks(mUnderexposed);

        mOverexposed = TexturePool.get(mUnderexposed);
        converter.setTexture("buf", normalExposure);
        converter.setf("factor", 1.8f);
        converter.drawBlocks(mOverexposed);
    }

    @Override
    public int getShader() {
        return R.raw.stage4_1_doubleexpose;
    }

    @Override
    protected boolean isEnabled() {
        return getProcessParams().exposeFuse;
    }
}
