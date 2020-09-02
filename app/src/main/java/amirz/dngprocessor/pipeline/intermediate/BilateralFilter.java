package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;
import amirz.dngprocessor.pipeline.exposefuse.Merge;
import amirz.dngprocessor.pipeline.noisereduce.NoiseReduce;

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

        Texture intermediate = previousStages.getStage(Merge.class).getMerged();
        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        mBilateral = new Texture(w, h, 3, Texture.Format.Float16, null);
        try (Texture bilateralTmp = new Texture(w, h, 3, Texture.Format.Float16, null)) {
            // Pre-bilateral median filter.
            converter.setTexture("buf", intermediate);
            converter.drawBlocks(bilateralTmp, false);

            // 3-step bilateral filter setup.
            converter.useProgram(R.raw.stage2_3_bilateral);
            converter.seti("bufSize", w, h);

            // 1) Small area, strong blur.
            converter.setTexture("buf", bilateralTmp);
            converter.setf("sigma", 0.03f, 0.5f);
            converter.seti("radius", 4, 1);
            converter.drawBlocks(mBilateral);

            // 2) Medium area, medium blur.
            converter.setTexture("buf", mBilateral);
            converter.setf("sigma", 0.02f, 3f);
            converter.seti("radius", 8, 2);
            converter.drawBlocks(bilateralTmp, false);

            // 3) Large area, weak blur.
            converter.setTexture("buf", bilateralTmp);
            converter.setf("sigma", 0.01f, 9f);
            converter.seti("radius", 12, 3);
            converter.drawBlocks(mBilateral);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_median;
    }
}
