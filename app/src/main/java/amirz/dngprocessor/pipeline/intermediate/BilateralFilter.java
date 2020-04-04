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
    private Texture mBilateral;

    public BilateralFilter(ProcessParams process) {
        mProcess = process;
    }

    public Texture getBilateral() {
        return mBilateral;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        if (mProcess.histFactor == 0f) {
            return;
        }

        GLPrograms converter = getConverter();

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        mBilateral = new Texture(w, h, 3, Texture.Format.Float16, null);
        try (Texture bilateralTmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            // Pre-bilateral median filter.
            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            bilateralTmp.setFrameBuffer();
            converter.drawBlocks(w, h);

            // 5-step bilateral filter setup.
            converter.useProgram(R.raw.stage2_3_bilateral);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);

            // 1) Very fine blur.
            bilateralTmp.bind(GL_TEXTURE0);
            mBilateral.setFrameBuffer();
            converter.setf("sigma", 0.05f, 0.1f);
            converter.seti("radius", 1, 1);
            converter.drawBlocks(w, h);

            // 2) Fine blur.
            mBilateral.bind(GL_TEXTURE0);
            bilateralTmp.setFrameBuffer();
            converter.setf("sigma", 0.04f, 0.3f);
            converter.seti("radius", 3, 1);
            converter.drawBlocks(w, h);

            // 3) Small area, strong blur.
            bilateralTmp.bind(GL_TEXTURE0);
            mBilateral.setFrameBuffer();
            converter.setf("sigma", 0.03f, 0.5f);
            converter.seti("radius", 5, 1);
            converter.drawBlocks(w, h);

            // 4) Medium area, medium blur.
            mBilateral.bind(GL_TEXTURE0);
            bilateralTmp.setFrameBuffer();
            converter.setf("sigma", 0.02f, 3f);
            converter.seti("radius", 10, 2);
            converter.drawBlocks(w, h);

            // 5) Large area, weak blur.
            bilateralTmp.bind(GL_TEXTURE0);
            mBilateral.setFrameBuffer();
            converter.setf("sigma", 0.01f, 9f);
            converter.seti("radius", 15, 3);
            converter.drawBlocks(w, h);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_median;
    }
}
