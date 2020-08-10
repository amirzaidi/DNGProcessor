package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

import static android.opengl.GLES20.GL_LINEAR;

public class Decompose extends Stage {
    private static final int SAMPLING_FACTOR = 2;

    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private Texture mHighRes, mMediumRes, mLowRes;
    private Texture mHighResDiff, mMediumResDiff;

    public Decompose(SensorParams sensor, ProcessParams process) {
        mSensorParams = sensor;
        mProcessParams = process;
    }

    public Texture[] getLayers() {
        return new Texture[] {
            mHighResDiff, mMediumResDiff, mLowRes
        };
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        mHighRes = previousStages.getStage(EdgeMirror.class).getIntermediate();
        int w = mHighRes.getWidth();
        int h = mHighRes.getHeight();

        //converter.setf("sigma", 1.36f);
        //converter.seti("radius", 2);

        converter.setf("sigma", 9f);
        converter.seti("radius", 18);

        try (Texture tmp = new Texture(w, h / SAMPLING_FACTOR + 1, 3,
                Texture.Format.Float16, null)) {
            // First render to the tmp buffer.
            converter.setTexture("buf", mHighRes);
            converter.seti("bufSize", w, h);
            converter.seti("dir", 0, 1); // Vertical
            converter.seti("samplingFactor", 1, SAMPLING_FACTOR);
            converter.drawBlocks(tmp, false);

            // Now render from tmp to the real buffer.
            converter.setTexture("buf", tmp);
            converter.seti("bufSize", tmp.getWidth(), tmp.getHeight());
            converter.seti("dir", 1, 0); // Horizontal
            converter.seti("samplingFactor", SAMPLING_FACTOR, 1);

            mMediumRes = new Texture(tmp.getWidth() / SAMPLING_FACTOR + 1, tmp.getHeight(), 3,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mMediumRes);
        }

        w = mMediumRes.getWidth();
        h = mMediumRes.getHeight();

        try (Texture tmp = new Texture(w, h / SAMPLING_FACTOR + 1, 3,
                Texture.Format.Float16, null)) {
            // First render to the tmp buffer.
            converter.setTexture("buf", mMediumRes);
            converter.seti("bufSize", w, h);
            converter.seti("dir", 0, 1); // Vertical
            converter.seti("samplingFactor", 1, SAMPLING_FACTOR);
            converter.drawBlocks(tmp, false);

            // Now render from tmp to the real buffer.
            converter.setTexture("buf", tmp);
            converter.seti("bufSize", tmp.getWidth(), tmp.getHeight());
            converter.seti("dir", 1, 0); // Horizontal
            converter.seti("samplingFactor", SAMPLING_FACTOR, 1);

            mLowRes = new Texture(tmp.getWidth() / SAMPLING_FACTOR + 1, tmp.getHeight(), 3,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mLowRes);
        }

        converter.useProgram(R.raw.stage2_0_diff_3ch_fs);
        converter.seti("samplingFactor", SAMPLING_FACTOR);

        converter.setTexture("highResBuf", mHighRes);
        converter.setTexture("lowResBuf", mMediumRes);
        mHighResDiff = new Texture(mHighRes.getWidth(), mHighRes.getHeight(), 3,
                Texture.Format.Float16, null);
        converter.drawBlocks(mHighResDiff);

        converter.setTexture("highResBuf", mMediumRes);
        converter.setTexture("lowResBuf", mLowRes);
        mMediumResDiff = new Texture(mMediumRes.getWidth(), mMediumRes.getHeight(), 3,
                Texture.Format.Float16, null);
        converter.drawBlocks(mMediumResDiff);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_0_blur_3ch_fs;
    }
}
