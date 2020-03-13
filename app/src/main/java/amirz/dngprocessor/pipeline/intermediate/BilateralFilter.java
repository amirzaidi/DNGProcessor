package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.PreProcess;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_TEXTURE0;

public class BilateralFilter extends Stage {
    private final ProcessParams mProcess;
    private int mProgramHelperDownscale,
            mProgramIntermediateBlur,
            mProgramIntermediateHistGen,
            mProgramIntermediateHistBlur,
            mProgramIntermediateBilateral;

    private Texture mBlurred, mAHEMap, mDownscaled, mBilateralFiltered;

    public BilateralFilter(ProcessParams process) {
        mProcess = process;
    }

    public Texture getBlurred() {
        return mBlurred;
    }

    public Texture getAHEMap() {
        return mAHEMap;
    }

    public Texture getDownscaled() {
        return mDownscaled;
    }

    public Texture getBilateralFiltered() {
        return mBilateralFiltered;
    }

    @Override
    public void init(GLPrograms converter, TexturePool texPool,
                     ShaderLoader shaderLoader) {
        super.init(converter, texPool, shaderLoader);
        mProgramHelperDownscale = converter.createProgram(converter.vertexShader,
                shaderLoader.readRaw(R.raw.helper_downscale_fs));
        mProgramIntermediateBlur = converter.createProgram(converter.vertexShader,
                shaderLoader.readRaw(R.raw.stage2_2_fs));
        mProgramIntermediateHistGen = converter.createProgram(converter.vertexShader,
                shaderLoader.readRaw(R.raw.stage2_3_fs));
        mProgramIntermediateHistBlur = converter.createProgram(converter.vertexShader,
                shaderLoader.readRaw(R.raw.stage2_4_fs));
        mProgramIntermediateBilateral = converter.createProgram(converter.vertexShader,
                shaderLoader.readRaw(R.raw.stage3_0_fs));
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLPrograms converter = getConverter();

        boolean lce = mProcess.lce;
        boolean ahe = mProcess.ahe;

        lce = false;
        ahe = false;

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();

        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        converter.useProgram(mProgramHelperDownscale);

        int scale = 8;
        intermediate.bind(GL_TEXTURE0);
        converter.seti("buf", 0);
        converter.seti("scale", scale);

        mDownscaled = new Texture(w / scale, h / scale, 1, Texture.Format.Float16, null, GL_LINEAR);
        mDownscaled.setFrameBuffer();
        converter.draw();

        // LCE
        if (lce) {
            converter.useProgram(mProgramIntermediateBlur);

            intermediate.bind(GL_TEXTURE0);
            converter.seti("sampleBuf", 0);
            converter.seti("blurBuf", 0);
            converter.seti("bufSize", w, h);

            converter.seti("dir", 1, 0); // Right
            converter.setf("ch", 0, 1); // xy[Y]

            Texture temp = new Texture(w, h, 1, Texture.Format.Float16, null);
            temp.setFrameBuffer();
            converter.drawBlocks(w, h);

            temp.bind(GL_TEXTURE0);
            converter.seti("dir", 0, 1); // Down
            converter.setf("ch", 1, 0); // [Y]00

            mBlurred = new Texture(w, h, 1, Texture.Format.Float16, null);
            mBlurred.setFrameBuffer();
            converter.drawBlocks(w, h);

            temp.close();
        }

        // AHE
        if (ahe) {
            converter.useProgram(mProgramIntermediateHistGen);

            intermediate.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w, h);
            converter.seti("mode", 0);
            converter.seti("dir", 1, 0); // Right

            Texture temp = new Texture(w / 2, h / 2, 4, Texture.Format.Float16, null);
            temp.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            converter.seti("bufSize", w / 2, h / 2);
            converter.seti("mode", 1);
            converter.seti("dir", 0, 1); // Down

            mAHEMap = new Texture(w / 2, h / 2, 4, Texture.Format.Float16, null, GL_LINEAR);
            mAHEMap.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            converter.useProgram(mProgramIntermediateHistBlur);

            mAHEMap.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", w / 2, h / 2);
            converter.seti("mode", 0);
            converter.seti("dir", 1, 0); // Right

            temp.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.bind(GL_TEXTURE0);
            converter.seti("mode", 1);
            converter.seti("dir", 0, 1); // Down

            mAHEMap.setFrameBuffer();
            converter.drawBlocks(w / 2, h / 2);

            temp.close();
        }

        // Double bilateral filter.
        converter.useProgram(mProgramIntermediateBilateral);

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("buf", 0);
        converter.seti("bufSize", w, h);
        mBilateralFiltered = new Texture(preProcess.getInWidth(), preProcess.getInHeight(),
                3, Texture.Format.Float16, null);

        // Two iterations means four total filters.
        for (int i = 0; i < 2; i++) {
            intermediate.bind(GL_TEXTURE0);
            mBilateralFiltered.setFrameBuffer();

            converter.drawBlocks(w, h);

            mBilateralFiltered.bind(GL_TEXTURE0);
            intermediate.setFrameBuffer();

            converter.drawBlocks(w, h);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage2_2_fs;
    }
}
