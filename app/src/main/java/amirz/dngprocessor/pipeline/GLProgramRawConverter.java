package amirz.dngprocessor.pipeline;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLProgramBase;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.gl.ShaderLoader;

import static android.opengl.GLES20.GL_VERTEX_SHADER;

public class GLProgramRawConverter extends GLProgramBase {
    public int inWidth, inHeight, cfaPattern;
    public GLTex mGainMap, mBlurred, mAHEMap, mDownscaled, mBilateralFiltered;
    public float[] sigma, hist;

    public int vertexShader;

    public GLProgramRawConverter(ShaderLoader loader) {
        vertexShader = loadShader(GL_VERTEX_SHADER, loader.readRaw(R.raw.passthrough_vs));
    }

    @Override
    public void setYOffset(int y) {
        seti("yOffset", y);
    }
}
