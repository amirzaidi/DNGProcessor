package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static amirz.dngprocessor.pipeline.exposefuse.FuseUtils.upsample2x;

public class Merge extends Stage {
    private Texture mMerged;

    public Texture getMerged() {
        return mMerged;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Laplace laplace = previousStages.getStage(Laplace.class);
        Laplace.Pyramid pyramid = laplace.getExpoPyramid();

        // Start with the lowest level gaussian.
        Texture wip = pyramid.gauss[pyramid.gauss.length - 1];
        for (int i = pyramid.laplace.length - 1; i >= 0; i--) {
            try (Texture upsampleWip = upsample2x(converter, wip, pyramid.gauss[i])) {
                converter.useProgram(getShader());

                // We can discard the previous work in progress merge.
                wip.close();
                wip = new Texture(pyramid.laplace[i]);

                converter.setTexture("upscaled", upsampleWip);
                converter.setTexture("downscaled", pyramid.gauss[i]);
                converter.setTexture("laplace", pyramid.laplace[i]);
                converter.seti("level", i);

                converter.drawBlocks(wip);

                if (i < 4) {
                    // Reuse laplace for NR, and swap with non-NR texture.
                    Texture tmp = pyramid.laplace[i];
                    noiseReduce(wip, tmp, i);
                    pyramid.laplace[i] = wip;
                    wip = tmp;
                }
            }
        }

        laplace.releasePyramid();
        mMerged = wip;
    }

    private void noiseReduce(Texture in, Texture out, int level) {
        GLPrograms converter = getConverter();
        converter.useProgram(level > 0
                ? R.raw.stage4_7_nr_intermediate
                : R.raw.stage4_8_nr_zero);
        converter.setTexture("buf", in);
        converter.seti("bufEdge", in.getWidth() - 1, in.getHeight() - 1);
        converter.setf("blendY", 0.9f);
        if (level > 0) {
            converter.setf("sigma", 0.4f, 0.06f);
        }
        converter.drawBlocks(out);
    }

    @Override
    public int getShader() {
        return R.raw.stage4_5_merge;
    }
}
