package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

import static android.opengl.GLES20.GL_TEXTURE0;

public class NoiseMap extends Stage {
    private final ProcessParams mProcessParams;
    private Texture mNoiseTex;

    public NoiseMap(ProcessParams processParams) {
        mProcessParams = processParams;
    }

    public Texture getNoiseTex() {
        return mNoiseTex;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        intermediate.bind(GL_TEXTURE0);
        converter.seti("intermediate", 0);

        try (Texture tmp = new Texture(intermediate.getWidth(), intermediate.getHeight(), 1,
                Texture.Format.Float16, null)) {
            converter.drawBlocks(tmp);

            converter.useProgram(R.raw.stage2_1_bilateral_ch);
            tmp.bind(GL_TEXTURE0);
            converter.seti("buf", 0);
            converter.seti("bufSize", tmp.getWidth(), tmp.getHeight());

            converter.setf("sigma", 0.25f, 2.5f);
            converter.seti("radius", 5, 1);

            mNoiseTex = new Texture(intermediate.getWidth(), intermediate.getHeight(), 1,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mNoiseTex);
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage2_1_noise_level_fs;
    }
}
