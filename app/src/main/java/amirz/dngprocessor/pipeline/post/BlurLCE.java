package amirz.dngprocessor.pipeline.post;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class BlurLCE extends Stage {
    private final SensorParams mSensorParams;
    private final ProcessParams mProcessParams;
    private Texture mWeakBlur, mMediumBlur, mStrongBlur;

    public BlurLCE(SensorParams sensor, ProcessParams process) {
        mSensorParams = sensor;
        mProcessParams = process;
    }

    public Texture getWeakBlur() {
        return mWeakBlur;
    }

    public Texture getMediumBlur() {
        return mMediumBlur;
    }

    public Texture getStrongBlur() {
        return mStrongBlur;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        if (!mProcessParams.lce) {
            return;
        }

        Texture intermediate = previousStages.getStage(NoiseReduce.class).getDenoised();
        GLPrograms converter = getConverter();

        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        try (Texture tmp = new Texture(w, h, 1, Texture.Format.Float16, null)) {
            int offsetX = mSensorParams.outputOffsetX;
            int offsetY = mSensorParams.outputOffsetY;
            converter.seti("minxy", offsetX, offsetY);
            converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                converter.setf("sigma", 0.5f);
                converter.seti("radius", 2, 1);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mWeakBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
                converter.drawBlocks(mWeakBlur);
            }

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                converter.seti("buf", 0);
                converter.setf("sigma", 2f);
                converter.seti("radius", 6, 1);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mMediumBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
                converter.drawBlocks(mMediumBlur);
            }

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                converter.setf("sigma", 32f);
                converter.seti("radius", 96, 4);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mStrongBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
                converter.drawBlocks(mStrongBlur);
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_2_blur_fs;
    }
}
