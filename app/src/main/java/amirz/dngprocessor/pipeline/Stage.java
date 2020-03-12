package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;

public abstract class Stage {
    private GLProgramRawConverter mConverter;
    private GLTexPool mTexPool;
    private int mProgram;

    public void init(GLProgramRawConverter converter, GLTexPool texPool,
                     ShaderLoader shaderLoader) {
        mConverter = converter;
        mTexPool = texPool;
        mProgram = converter.createProgram(converter.vertexShader, shaderLoader.readRaw(getShader()));
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

    protected void execute(StagePipeline.StageMap previousStages) {
        mConverter.useProgram(mProgram);
    }

    public abstract int getShader();
}
