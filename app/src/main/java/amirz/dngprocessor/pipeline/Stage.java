package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLPrograms;

public abstract class Stage {
    private GLPrograms mConverter;

    public void init(GLPrograms converter) {
        mConverter = converter;
    }

    protected GLPrograms getConverter() {
        return mConverter;
    }

    protected boolean isEnabled() {
        return true;
    }

    protected abstract void execute(StagePipeline.StageMap previousStages);

    public abstract int getShader();
}
