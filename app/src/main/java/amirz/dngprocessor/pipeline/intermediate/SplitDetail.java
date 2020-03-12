package amirz.dngprocessor.pipeline.intermediate;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class SplitDetail extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return R.raw.stage2_3_fs;
    }
}
