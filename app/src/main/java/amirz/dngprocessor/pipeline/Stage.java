package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.gl.ShaderLoader;

public abstract class Stage {
    private GLPrograms mConverter;
    private TexturePool mTexPool;

    public void init(GLPrograms converter, TexturePool texPool,
                     ShaderLoader shaderLoader) {
        mConverter = converter;
        mTexPool = texPool;
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
        mConverter.useProgram(getShader());
    }

    public abstract int getShader();
}
