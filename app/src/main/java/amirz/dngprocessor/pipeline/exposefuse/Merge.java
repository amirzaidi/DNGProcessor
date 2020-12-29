package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

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
        Laplace.Pyramid underExpo = laplace.getUnderPyramid();
        Laplace.Pyramid overExpo = laplace.getOverPyramid();

        converter.useProgram(getShader());
        converter.seti("useUpscaled", 0);
        converter.setTexture("gaussUnder", underExpo.gauss[underExpo.gauss.length - 1]);
        converter.setTexture("gaussOver", overExpo.gauss[overExpo.gauss.length - 1]);
        converter.setTexture("blendUnder", underExpo.gauss[underExpo.gauss.length - 1]);
        converter.setTexture("blendOver", overExpo.gauss[overExpo.gauss.length - 1]);
        converter.seti("level", overExpo.gauss.length - 1);

        Texture wip = TexturePool.get(underExpo.gauss[underExpo.gauss.length - 1]);
        converter.drawBlocks(wip, false);

        // Start with the lowest level gaussian.
        for (int i = underExpo.laplace.length - 1; i >= 0; i--) {
            try (Texture upscaleWip = upsample2x(converter, wip, underExpo.gauss[i])) {
                converter.useProgram(getShader());

                // We can discard the previous work in progress merge.
                wip.close();
                wip = TexturePool.get(underExpo.laplace[i]);

                converter.seti("useUpscaled", 1);
                converter.setTexture("upscaled", upscaleWip);
                converter.setTexture("gaussUnder", underExpo.gauss[i]);
                converter.setTexture("gaussOver", overExpo.gauss[i]);
                converter.setTexture("blendUnder", underExpo.laplace[i]);
                converter.setTexture("blendOver", overExpo.laplace[i]);
                converter.seti("level", i);

                converter.drawBlocks(wip, false);

                /*
                if (i < 4) {
                    // Reuse laplace for NR, and swap with non-NR texture, which will be closed
                    // by call to releasePyramid below.
                    Texture tmp = underExpo.laplace[i];
                    noiseReduce(wip, tmp, i);
                    underExpo.laplace[i] = wip;
                    wip = tmp;
                }
                 */
            }
        }

        laplace.releasePyramid();
        Texture chroma = previousStages.getStage(EdgeMirror.class).getIntermediate();
        mMerged = chroma; // Reuse.

        converter.useProgram(R.raw.stage4_9_combine_z);
        converter.setTexture("bufChroma", chroma);
        converter.setTexture("bufLuma", wip);
        converter.drawBlocks(mMerged);

        wip.close();
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
            converter.setf("sigma", 0.4f, 0.03f);
        }
        converter.drawBlocks(out, level == 0);
    }

    @Override
    public int getShader() {
        return R.raw.stage4_5_merge;
    }

    @Override
    protected boolean isEnabled() {
        return false;
    }
}
