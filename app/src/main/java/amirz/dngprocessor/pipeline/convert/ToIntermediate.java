package amirz.dngprocessor.pipeline.convert;

import android.util.Rational;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLProgramBase;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE2;

public class ToIntermediate extends Stage {
    private final SensorParams mSensor;
    private final float[] mSensorToXYZ_D50;

    private GLTex mIntermediate;

    public ToIntermediate(SensorParams sensor, float[] sensorToXYZ_D50) {
        mSensor = sensor;
        mSensorToXYZ_D50 = sensorToXYZ_D50;
    }

    public GLTex getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLProgramBase converter = getConverter();

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("rawBuffer", 0);
        converter.seti("greenBuffer", 2);
        converter.seti("rawWidth", preProcess.getInWidth());
        converter.seti("rawHeight", preProcess.getInHeight());

        // Second texture for per-CFA pixel data
        mIntermediate = new GLTex(preProcess.getInWidth(), preProcess.getInHeight(), 3,
                GLTex.Format.Float16, null);

        // Load mosaic and green raw texture
        GLTex sensorTex = previousStages.getStage(PreProcess.class).getSensorTex();
        GLTex sensorGTex = previousStages.getStage(GreenDemosaic.class).getSensorGTex();

        sensorTex.bind(GL_TEXTURE0);
        sensorGTex.bind(GL_TEXTURE2);

        // Configure frame buffer
        mIntermediate.setFrameBuffer();

        Rational[] neutralPoint = mSensor.neutralColorPoint;
        byte[] cfaVal = mSensor.cfaVal;
        converter.setf("neutralLevel",
                neutralPoint[cfaVal[0]].floatValue(),
                neutralPoint[cfaVal[1]].floatValue(),
                neutralPoint[cfaVal[2]].floatValue(),
                neutralPoint[cfaVal[3]].floatValue());

        converter.setf("neutralPoint",
                neutralPoint[0].floatValue(),
                neutralPoint[1].floatValue(),
                neutralPoint[2].floatValue());

        converter.setf("sensorToXYZ", mSensorToXYZ_D50);

        converter.seti("cfaPattern", preProcess.getCfaPattern());
        converter.drawBlocks(preProcess.getInWidth(), preProcess.getInHeight());

        sensorTex.close();
        sensorGTex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
