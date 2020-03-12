package amirz.dngprocessor.pipeline;

import java.util.List;

import amirz.dngprocessor.gl.GLTexPool;

public abstract class Stage {
    private GLProgramRawConverter mConverter;
    private GLTexPool mTexPool;

    public void init(GLProgramRawConverter converter, GLTexPool texPool) {
        mConverter = converter;
        mTexPool = texPool;
    }

    protected GLProgramRawConverter getConverter() {
        return mConverter;
    }

    protected GLTexPool getTexPool() {
        return mTexPool;
    }

    protected boolean isEnabled() {
        return true;
    }

    protected abstract void execute(List<Stage> previousStages);

    public abstract int getShader();
}
