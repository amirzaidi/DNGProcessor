package amirz.dngprocessor.pipeline.post;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;
import amirz.dngprocessor.pipeline.intermediate.MergeDetail;

import static android.opengl.GLES20.GL_TEXTURE0;

public class BlurLCE extends Stage {
    private final ProcessParams mProcessParams;
    private Texture mWeakBlur, mMediumBlur, mStrongBlur;

    public BlurLCE(ProcessParams process) {
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

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        GLPrograms converter = getConverter();

        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        try (Texture tmp = new Texture(w, h, 1, Texture.Format.Float16, null)) {
            // First render to the tmp buffer.
            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.setf("sigma", 1f);
            converter.seti("radius", 3);
            converter.seti("dir", 0, 1); // Vertical
            converter.setf("ch", 0, 1); // xy[Y]

            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Now render from tmp to the real buffer.
            tmp.bind(GL_TEXTURE0);

            converter.seti("dir", 1, 0); // Horizontal
            converter.setf("ch", 1, 0); // [Y]00

            mWeakBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
            mWeakBlur.setFrameBuffer();
            converter.drawBlocks(w, h);

            // First render to the tmp buffer.
            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.setf("sigma", 2f);
            converter.seti("radius", 6);
            converter.seti("dir", 0, 1); // Vertical
            converter.setf("ch", 0, 1); // xy[Y]

            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Now render from tmp to the real buffer.
            tmp.bind(GL_TEXTURE0);

            converter.seti("dir", 1, 0); // Horizontal
            converter.setf("ch", 1, 0); // [Y]00

            mMediumBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
            mMediumBlur.setFrameBuffer();
            converter.drawBlocks(w, h);

            // First render to the tmp buffer.
            intermediate.bind(GL_TEXTURE0);
            converter.setf("sigma", 2.5f);
            converter.seti("radius", 8);
            converter.seti("dir", 0, 1); // Vertical
            converter.setf("ch", 0, 1); // xy[Y]

            tmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // Now render from tmp to the real buffer.
            tmp.bind(GL_TEXTURE0);
            converter.seti("dir", 1, 0); // Horizontal
            converter.setf("ch", 1, 0); // [Y]00

            mStrongBlur = new Texture(w, h, 1, Texture.Format.Float16, null);
            mStrongBlur.setFrameBuffer();
            converter.drawBlocks(w, h);
        }

        if (previousStages.getStage(MergeDetail.class).getIntermediate() != intermediate) {
            intermediate.close();
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage3_0_blur_fs;
    }
}
