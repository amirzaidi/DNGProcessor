package amirz.dngprocessor.device;

import android.util.SparseArray;

import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.TIFFTag;

public class OnePlus7 extends Generic {
    private static final float[] CC1 = new float[] {
            1f, 0f, 0.2f, // Low=Remove Pink, High=Invert Pink | Low=Remove Red, High=Cyberpunk
            0.2f, 1f, 0.3f, // Low=Purple/Yellow, High=Magenta/Green | Low=Cyan, High=Violet/Green
            0.1f, 0f, 1f, // | Low=Blue->Bright, High=Blue->Green
    };

    private static final float[] CC2 = CC1;

    @Override
    public boolean isModel(String model) {
        return model.startsWith("GM19");
    }

    @Override
    public void sensorCorrection(SparseArray<TIFFTag> tags, SensorParams sensor) {
        sensor.calibrationTransform1 = CC1;
        sensor.calibrationTransform2 = CC2;

        /*
        sensor.colorMatrix1 = new float[] {
                86f / 128f, -10f / 128f, -13f / 128f,
                -52f / 128f, 167f / 128f, 9f / 128f,
                -9f / 128f, 30f / 128f, 48f / 128f,
        };
        sensor.colorMatrix2 = new float[] {
                137f / 128f, -37f / 128f, -14f / 128f,
                -66f / 128f, 190f / 128f, 31f / 128f,
                -4f / 128f, 14f / 128f, 81f / 128f,
        };
        sensor.forwardTransform1 = new float[] {
                94f / 128f, 8f / 128f, 21f / 128f,
                30f / 128f, 102f / 128f, -5f / 128f,
                -1f / 128f, -45f / 128f, 152f / 128f,
        };
        sensor.forwardTransform2 = new float[] {
                94f / 128f, 8f / 128f, 21f / 128f,
                30f / 128f, 102f / 128f, -5f / 128f,
                -1f / 128f, -45f / 128f, 152f / 128f,
        };
        */
    }

    @Override
    void saturationCorrection(float[] saturationMap) {
        saturationMap[0] *= 1.25f;
        saturationMap[1] *= 1.2f;
        saturationMap[2] *= 1.15f;
        saturationMap[3] *= 1.1f;
        saturationMap[4] *= 0.8f;
        saturationMap[5] *= 1.1f;
        saturationMap[6] *= 1.15f;
        saturationMap[7] *= 1.2f;
    }
}
