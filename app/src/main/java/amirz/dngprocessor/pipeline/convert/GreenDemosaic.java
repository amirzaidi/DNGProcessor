package amirz.dngprocessor.pipeline.convert;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.GL_TEXTURE0;

public class GreenDemosaic extends Stage {
    private Texture mSensorG;

    public Texture getSensorGTex() {
        return mSensorG;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        // Load old texture
        Texture sensorTex = previousStages.getStage(PreProcess.class).getSensorTex();
        converter.setTexture("rawBuffer", sensorTex);
        converter.seti("rawWidth", preProcess.getInWidth());
        converter.seti("rawHeight", preProcess.getInHeight());

        mSensorG = new Texture(preProcess.getInWidth(), preProcess.getInHeight(), 1,
                Texture.Format.Float16, null);

        converter.seti("cfaPattern", preProcess.getCfaPattern());
        converter.drawBlocks(mSensorG);
    }

    @Override
    public int getShader() {
        return R.raw.stage1_2_fs;
    }
}
