package amirz.dngprocessor.pipeline.intermediate;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class BilateralFilter extends Stage {
    private final ProcessParams mProcess;

    public BilateralFilter(ProcessParams process) {
        mProcess = process;
    }

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
        converter.blurIntermediate(mProcess.lce, mProcess.ahe);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_2_fs;
    }
}
