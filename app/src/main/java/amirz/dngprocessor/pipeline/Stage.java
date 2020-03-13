package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.gl.ShaderLoader;

public abstract class Stage {
    private GLPrograms mConverter;
    private TexturePool mTexPool;
    private int mProgram;

    public void init(GLPrograms converter, TexturePool texPool,
                     ShaderLoader shaderLoader) {
        mConverter = converter;
        mTexPool = texPool;
        mProgram = converter.createProgram(converter.vertexShader, shaderLoader.readRaw(getShader()));
    }

    protected GLPrograms getConverter() {
        return mConverter;
    }

    protected TexturePool getTexPool() {
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
