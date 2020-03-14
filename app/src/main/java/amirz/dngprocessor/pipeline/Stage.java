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

    protected void execute(StagePipeline.StageMap previousStages) {
        mConverter.useProgram(getShader());
    }

    public abstract int getShader();
}
