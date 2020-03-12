package amirz.dngprocessor.pipeline.intermediate;

import java.util.List;

import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class SplitDetail extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return 0;
    }
}
