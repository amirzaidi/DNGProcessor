package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.*;

public class BilateralFilter extends Stage {
    private final ProcessParams mProcess;
    private Texture mBilateral1, mBilateral2;

    public BilateralFilter(ProcessParams process) {
        mProcess = process;
    }

    public Texture getBilateral() {
        return mBilateral1;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        if (!mProcess.lce) {
            return;
        }

        GLPrograms converter = getConverter();

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        converter.useProgram(R.raw.helper_extract_channel_fs);
        mBilateral1 = new Texture(w, h, 1, Texture.Format.Float16, null);
        mBilateral2 = new Texture(w, h, 1, Texture.Format.Float16, null);
        intermediate.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        converter.setf("mult", 0, 0, 1, 0);
        mBilateral1.setFrameBuffer();
        converter.drawBlocks(w, h);

        // Pre-bilateral median filter.
        converter.useProgram(R.raw.stage2_2_median_fs);
        mBilateral1.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        mBilateral2.setFrameBuffer();
        converter.drawBlocks(w, h);

        // Bilateral filter setup.
        converter.useProgram(R.raw.stage3_0_fs);
        intermediate.bind(GL_TEXTURE2);
        converter.seti("buf", 0);
        converter.seti("bufSize", w, h);
        converter.seti("intermediate", 2);

        // 1) Small area, strong blur.
        mBilateral2.bind(GL_TEXTURE0);
        mBilateral1.setFrameBuffer();
        converter.setf("sigma", 0.3f, 3.f);
        converter.seti("radius", 6, 1);
        converter.drawBlocks(w, h);

        // 2) Medium area, medium blur.
        mBilateral1.bind(GL_TEXTURE0);
        mBilateral2.setFrameBuffer();
        converter.setf("sigma", 0.2f, 9.f);
        converter.seti("radius", 18, 3);
        converter.drawBlocks(w, h);

        // 3) Large area, weak blur.
        mBilateral2.bind(GL_TEXTURE0);
        mBilateral1.setFrameBuffer();
        converter.setf("sigma", 0.1f, 27.f);
        converter.seti("radius", 54, 9);
        converter.drawBlocks(w, h);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_2_fs;
    }
}
