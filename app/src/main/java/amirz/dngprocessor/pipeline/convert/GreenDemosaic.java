package amirz.dngprocessor.pipeline.convert;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class GreenDemosaic extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
        converter.greenDemosaic();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_2_fs;
    }
}
