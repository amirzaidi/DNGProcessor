package amirz.dngprocessor.pipeline.convert;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class ToIntermediate extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
