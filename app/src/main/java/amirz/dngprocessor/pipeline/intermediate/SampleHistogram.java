package amirz.dngprocessor.pipeline.intermediate;

import java.util.List;

import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class SampleHistogram extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return 0;
    }
}
