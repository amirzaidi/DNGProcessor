package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static amirz.dngprocessor.pipeline.exposefuse.FuseUtils.*;

public class Laplace extends Stage {
    private static final int LEVELS = 10;

    public static class Pyramid {
        public Texture[] gauss;
        public Texture[] laplace;
    }

    private Pyramid mUnderPyramid;
    private Pyramid mOverPyramid;

    public Pyramid getUnderPyramid() {
        return mUnderPyramid;
    }

    public Pyramid getOverPyramid() {
        return mOverPyramid;
    }

    public void releasePyramid() {
        for (Pyramid pyr : new Pyramid[] { mUnderPyramid, mOverPyramid }) {
            for (Texture tex : pyr.gauss) {
                tex.close();
            }
            for (Texture tex : pyr.laplace) {
                tex.close();
            }
        }
        mUnderPyramid = null;
        mOverPyramid = null;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        DoubleExpose de = previousStages.getStage(DoubleExpose.class);
        mUnderPyramid = createPyramid(de.getUnderexposed());
        mOverPyramid = createPyramid(de.getOverexposed());
    }

    private Pyramid createPyramid(Texture remainder) {
        GLPrograms converter = getConverter();

        // Downsample loop.
        Texture[] downsampled = new Texture[LEVELS];
        downsampled[0] = remainder;
        for (int i = 1; i < downsampled.length; i++) {
            downsampled[i] = downsample2x(converter, downsampled[i - 1]);
        }

        // Upsample loop.
        Texture[] upsampled = new Texture[downsampled.length - 1];
        for (int i = 0; i < upsampled.length; i++) {
            upsampled[i] = upsample2x(converter, downsampled[i + 1], downsampled[i]);
        }

        // Diff loop. Indices for resolution are the same between downsampled and upsampled,
        // but the upsampled ones lack high frequency information.
        Texture[] diff = new Texture[upsampled.length];
        converter.useProgram(R.raw.stage4_4_difference);
        for (int i = 0; i < diff.length; i++) {
            converter.setTexture("base", upsampled[i]);
            converter.setTexture("target", downsampled[i]);

            diff[i] = TexturePool.get(upsampled[i]);
            converter.drawBlocks(diff[i]);
            upsampled[i].close();
        }

        Pyramid pyramid = new Pyramid();
        pyramid.gauss = downsampled;
        pyramid.laplace = diff;
        return pyramid;
    }

    @Override
    public int getShader() {
        return R.raw.stage4_2_downsample;
    }

    @Override
    protected boolean isEnabled() {
        return false;
    }
}
