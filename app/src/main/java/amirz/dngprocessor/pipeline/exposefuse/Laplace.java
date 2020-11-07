package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

import static amirz.dngprocessor.pipeline.exposefuse.FuseUtils.*;

public class Laplace extends Stage {
    private static final int LEVELS = 10;

    public static class Pyramid {
        public Texture[] gauss;
        public Texture[] laplace;
    }

    private Pyramid mExpoPyramid;

    public Pyramid getExpoPyramid() {
        return mExpoPyramid;
    }

    public void releasePyramid() {
        for (Texture tex : mExpoPyramid.gauss) {
            tex.close();
        }
        mExpoPyramid = null;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        Texture normalExposure = previousStages.getStage(EdgeMirror.class).getIntermediate();
        mExpoPyramid = createPyramid(normalExposure);
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

            // Reuse the upsampled texture.
            diff[i] = upsampled[i];
            converter.drawBlocks(diff[i]);
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
}
