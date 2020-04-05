package amirz.dngprocessor.pipeline.convert;

import android.util.Rational;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.*;

public class ToIntermediate extends Stage {
    private final SensorParams mSensor;
    private final float[] mSensorToXYZ_D50;

    private Texture mIntermediate;

    public ToIntermediate(SensorParams sensor, float[] sensorToXYZ_D50) {
        mSensor = sensor;
        mSensorToXYZ_D50 = sensorToXYZ_D50;
    }

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("rawBuffer", 0);
        converter.seti("greenBuffer", 2);
        converter.seti("rawWidth", preProcess.getInWidth());
        converter.seti("rawHeight", preProcess.getInHeight());

        // Second texture for per-CFA pixel data
        mIntermediate = new Texture(preProcess.getInWidth(), preProcess.getInHeight(), 3,
                Texture.Format.Float16, null);

        // Load mosaic and green raw texture
        try (Texture sensorGTex = previousStages.getStage(GreenDemosaic.class).getSensorGTex()) {
            try (Texture sensorTex = preProcess.getSensorTex()) {
                sensorTex.bind(GL_TEXTURE0);
                sensorGTex.bind(GL_TEXTURE2);

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

                try (Texture gainMapTex = preProcess.getGainMapTex()) {
                    converter.seti("gainMap", 4);
                    gainMapTex.bind(GL_TEXTURE4);

                    converter.drawBlocks(mIntermediate);
                }
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
