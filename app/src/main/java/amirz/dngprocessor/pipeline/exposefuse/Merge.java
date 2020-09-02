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

        Laplace.Pyramid normalExpo = laplace.getNormalExpoPyramid();
        Laplace.Pyramid highExpo = laplace.getHighExpoPyramid();

        converter.seti("useUpsampled", 0);

        Texture wip = new Texture(normalExpo.gauss[normalExpo.gauss.length - 1]);

        // Weighting and blending is the same on the lowest layer.
        converter.setTexture("normalExpo", normalExpo.gauss[normalExpo.gauss.length - 1]);
        converter.setTexture("highExpo", highExpo.gauss[highExpo.gauss.length - 1]);

        converter.setTexture("normalExpoDiff", normalExpo.gauss[normalExpo.gauss.length - 1]);
        converter.setTexture("highExpoDiff", highExpo.gauss[highExpo.gauss.length - 1]);

        converter.drawBlocks(wip);

        for (int i = normalExpo.laplace.length - 1; i >= 0; i--) {
            try (Texture upsampleWip = upsample2x(converter, wip)) {
                converter.useProgram(getShader());

                converter.setTexture("upsampled", upsampleWip);
                converter.seti("useUpsampled", 1);

                // We can discard the previous work in progress merge.
                wip.close();
                wip = new Texture(normalExpo.laplace[i]);

                // Weigh full image.
                converter.setTexture("normalExpo", normalExpo.gauss[i]);
                converter.setTexture("highExpo", highExpo.gauss[i]);

                // Blend feature level.
                converter.setTexture("normalExpoDiff", normalExpo.laplace[i]);
                converter.setTexture("highExpoDiff", highExpo.laplace[i]);

                converter.drawBlocks(wip);
            }
        }

        laplace.releasePyramids();

        converter.useProgram(R.raw.stage4_6_xyz_to_xyy);
        converter.setTexture("buf", wip);
        converter.drawBlocks(wip);
        mMerged = wip;
    }

    @Override
    public int getShader() {
        return R.raw.stage4_5_merge;
    }
}
