package amirz.dngprocessor.pipeline.convert;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class PreProcess extends Stage {
    private final SensorParams mSensor;
    private final byte[] mRaw;

    public PreProcess(SensorParams sensor, byte[] raw) {
        mSensor = sensor;
        mRaw = raw;
    }

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.setIn(mRaw, mSensor.inputWidth, mSensor.inputHeight, mSensor.cfa);
        converter.setGainMap(mSensor.gainMap, mSensor.gainMapSize);
        converter.setBlackWhiteLevel(mSensor.blackLevelPattern, mSensor.whiteLevel);
        converter.sensorPreProcess(mSensor.hotPixels, mSensor.hotPixelsSize);
    }

    @Override
    public int getShader() {
        return R.raw.stage1_1_fs;
    }
}
