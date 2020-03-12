package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class SplitDetail extends Stage {
    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_fs;
    }
}
