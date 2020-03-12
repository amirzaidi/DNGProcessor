package amirz.dngprocessor.pipeline.post;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLTexPool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

import static android.opengl.GLES20.GL_FRAMEBUFFER_BINDING;
import static android.opengl.GLES20.glGetIntegerv;

public class ToneMap extends Stage {
    private final int[] mFbo = new int[1];

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();
    }

    @Override
    public void init(GLProgramRawConverter converter, GLTexPool texPool,
                     ShaderLoader shaderLoader) {
        super.init(converter, texPool, shaderLoader);
        glGetIntegerv(GL_FRAMEBUFFER_BINDING, mFbo, 0);
    }

    @Override
    public int getShader() {
        return R.raw.stage3_1_fs;
    }
}
