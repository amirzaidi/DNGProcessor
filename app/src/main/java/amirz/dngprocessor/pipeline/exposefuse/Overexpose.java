package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.noisereduce.NoiseReduce;

public class Overexpose extends Stage {
    private Texture mOverexposed;

    public Texture getOverexposed() {
        return mOverexposed;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Texture normalExposure = previousStages.getStage(NoiseReduce.class).getDenoised();

        mOverexposed = new Texture(normalExposure);

        converter.setTexture("buf", normalExposure);
        converter.setf("factor", 6f);

        converter.drawBlocks(mOverexposed);
    }

    @Override
    public int getShader() {
        return R.raw.stage4_1_overexpose;
    }
}
