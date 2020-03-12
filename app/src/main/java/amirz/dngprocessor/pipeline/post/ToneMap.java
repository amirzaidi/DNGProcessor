package amirz.dngprocessor.pipeline.post;

import java.util.List;

import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class ToneMap extends Stage {
    @Override
    protected void execute(List<Stage> previousStages) {
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public int getShader() {
        return 0;
    }
}
