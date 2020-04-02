package amirz.dngprocessor.pipeline.post;

import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class ChromaNoiseReduce extends Stage {
    @Override
    protected void execute(StagePipeline.StageMap previousStages) {

    }

    @Override
    public int getShader() {
        return 0;
    }
}
