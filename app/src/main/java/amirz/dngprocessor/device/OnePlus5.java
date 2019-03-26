package amirz.dngprocessor.device;

import android.util.SparseArray;

import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.TIFFTag;

import static amirz.dngprocessor.Constants.HORZ;
import static amirz.dngprocessor.Constants.PLUS;
import static amirz.dngprocessor.Constants.VERT;

public class OnePlus5 extends OnePlus {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("ONEPLUS A5");
    }

    @Override
    public void sensorCorrection(SparseArray<TIFFTag> tags, SensorParams sensor) {
        super.sensorCorrection(tags, sensor);
        super.matrixCorrection(tags, sensor);

        // Dot-fix
        int w = 8;
        int h = 16;

        sensor.hotPixelsSize = new int[] { w, h };
        sensor.hotPixels = new short[w * h];

        sensor.hotPixels[6] = HORZ;
        sensor.hotPixels[w + 5] = VERT;
        sensor.hotPixels[w + 6] = PLUS;
        sensor.hotPixels[w + 7] = VERT;
        sensor.hotPixels[2 * w + 6] = HORZ;

        sensor.hotPixels[8 * w + 2] = HORZ;
        sensor.hotPixels[9 * w + 1] = VERT;
        sensor.hotPixels[9 * w + 2] = PLUS;
        sensor.hotPixels[9 * w + 3] = VERT;
        sensor.hotPixels[10 * w + 2] = HORZ;
    }
}
