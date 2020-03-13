package amirz.dngprocessor.pipeline.convert;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLProgramBase;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.GL_TEXTURE0;

public class GreenDemosaic extends Stage {
    private GLTex mSensorG;

    public GLTex getSensorGTex() {
        return mSensorG;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLProgramBase converter = getConverter();

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("rawBuffer", 0);
        converter.seti("rawWidth", preProcess.getInWidth());
        converter.seti("rawHeight", preProcess.getInHeight());

        mSensorG = new GLTex(preProcess.getInWidth(), preProcess.getInHeight(), 1,
                GLTex.Format.Float16, null);

        // Load old texture
        GLTex sensorTex = previousStages.getStage(PreProcess.class).getSensorTex();
        sensorTex.bind(GL_TEXTURE0);

        // Configure frame buffer
        mSensorG.setFrameBuffer();

        converter.seti("cfaPattern", preProcess.getCfaPattern());
        converter.drawBlocks(preProcess.getInWidth(), preProcess.getInHeight());
    }

    @Override
    public int getShader() {
        return R.raw.stage1_2_fs;
    }
}
