package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.EdgeMirror;

public class Decompose extends Stage {
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
            //mHighResDiff, mMediumResDiff, mLowRes
            mHighRes, mMediumRes, mLowRes
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
        converter.seti("bufSize", w, h);

        try (Texture tmp = new Texture(w, h, 3,
                Texture.Format.Float16, null)) {
            converter.setf("sigma", 3f);
            converter.seti("radius", 6);

            // First render to the tmp buffer.
            converter.setTexture("buf", mHighRes);
            converter.seti("dir", 0, 1); // Vertical
            converter.drawBlocks(tmp, false);

            // Now render from tmp to the real buffer.
            converter.setTexture("buf", tmp);
            converter.seti("dir", 1, 0); // Horizontal

            mMediumRes = new Texture(w, h, 3,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mMediumRes);

            converter.setf("sigma", 9f);
            converter.seti("radius", 18);

            // First render to the tmp buffer.
            converter.setTexture("buf", mMediumRes);
            converter.seti("dir", 0, 1); // Vertical
            converter.drawBlocks(tmp, false);

            // Now render from tmp to the real buffer.
            converter.setTexture("buf", tmp);
            converter.seti("dir", 1, 0); // Horizontal

            mLowRes = new Texture(w, h, 3,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mLowRes);
        }

        /*
        converter.useProgram(R.raw.stage2_0_diff_3ch_fs);

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
         */
    }

    @Override
    public int getShader() {
        return R.raw.stage2_0_blur_3ch_fs;
    }
}
