package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.gl.GLProgramRawConverter;
import amirz.dngprocessor.gl.generic.GLTexPool;

public abstract class Stage {
    private GLProgramRawConverter mProgram;
    private GLTexPool mTexPool;

    public void init(GLProgramRawConverter program, GLTexPool texPool) {
        mProgram = program;
        mTexPool = texPool;
    }

    protected GLProgramRawConverter getProgram() {
        return mProgram;
    }

    protected GLTexPool getTexPool() {
        return mTexPool;
    }

    public abstract int getShader();
}
