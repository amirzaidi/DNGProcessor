package amirz.dngprocessor.pipeline.convert;

import android.util.Rational;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE2;

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

        converter.seti("rawBuffer", 0);
        converter.seti("greenBuffer", 2);
        converter.seti("rawWidth", converter.inWidth);
        converter.seti("rawHeight", converter.inHeight);

        // Second texture for per-CFA pixel data
        converter.mIntermediate = new GLTex(converter.inWidth, converter.inHeight, 3,
                GLTex.Format.Float16, null);

        // Load mosaic and green raw texture
        converter.mSensor.bind(GL_TEXTURE0);
        converter.mSensorG.bind(GL_TEXTURE2);

        // Configure frame buffer
        converter.mIntermediate.setFrameBuffer();

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

        converter.seti("cfaPattern", converter.cfaPattern);
        converter.drawBlocks(converter.inWidth, converter.inHeight);

        converter.mSensor.close();
        converter.mSensorG.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
