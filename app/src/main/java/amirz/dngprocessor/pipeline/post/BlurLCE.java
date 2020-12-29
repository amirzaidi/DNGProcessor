package amirz.dngprocessor.pipeline.post;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;

public class BlurLCE extends Stage {
    private Texture mWeakBlur, mMediumBlur, mStrongBlur;

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
        if (!getProcessParams().lce) {
            return;
        }

        Texture intermediate = previousStages.getStage(MergeDetail.class).getIntermediate();
        GLPrograms converter = getConverter();

        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        try (Texture tmp = TexturePool.get(w, h, 1, Texture.Format.Float16)) {
            int offsetX = getSensorParams().outputOffsetX;
            int offsetY = getSensorParams().outputOffsetY;
            converter.seti("minxy", offsetX, offsetY);
            converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                converter.setf("sigma", 2.5f);
                converter.seti("radius", 9, 1);
                //converter.setf("sigma", 32f);
                //converter.seti("radius", 84, 4);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp, false);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mStrongBlur = TexturePool.get(w, h, 1, Texture.Format.Float16);
                converter.drawBlocks(mStrongBlur);
            }

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                converter.seti("buf", 0);
                //converter.setf("sigma", 2f);
                //converter.seti("radius", 5, 1);
                converter.setf("sigma", 1.5f);
                converter.seti("radius", 6, 1);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp, false);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mMediumBlur = TexturePool.get(w, h, 1, Texture.Format.Float16);
                converter.drawBlocks(mMediumBlur);
            }

            {
                // First render to the tmp buffer.
                converter.setTexture("buf", intermediate);
                //converter.setf("sigma", 0.33f);
                //converter.seti("radius", 2, 1);
                converter.setf("sigma", 0.67f);
                converter.seti("radius", 3, 1);
                converter.seti("dir", 0, 1); // Vertical
                converter.setf("ch", 0, 1); // xy[Y]
                converter.drawBlocks(tmp, false);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.setf("ch", 1, 0); // [Y]00

                mWeakBlur = TexturePool.get(w, h, 1, Texture.Format.Float16);
                converter.drawBlocks(mWeakBlur);
            }
        }
    }

    @Override
    public void close() {
        if (getProcessParams().lce) {
            mWeakBlur.close();
            mMediumBlur.close();
            mStrongBlur.close();
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_2_blur_fs;
    }
}
