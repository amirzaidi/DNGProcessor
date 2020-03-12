package amirz.dngprocessor.pipeline.convert;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class ToIntermediate extends Stage {
    private final SensorParams mSensor;
    private final float[] mSensorToXYZ_D50;

    public ToIntermediate(SensorParams sensor, float[] sensorToXYZ_D50) {
        mSensor = sensor;
        mSensorToXYZ_D50 = sensorToXYZ_D50;
    }

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.prepareToIntermediate();
        converter.setNeutralPoint(mSensor.neutralColorPoint, mSensor.cfaVal);
        converter.setTransforms1(mSensorToXYZ_D50);
        converter.sensorToIntermediate();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
