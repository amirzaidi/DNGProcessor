package amirz.dngprocessor.device;

import android.util.SparseArray;

import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.TIFFTag;

public class OnePlus7 implements DeviceMap.Device {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("GM19");
    }

    @Override
    public void sensorCorrection(SparseArray<TIFFTag> tags, SensorParams sensor) {
    }

    @Override
    public void processCorrection(SparseArray<TIFFTag> tags, ProcessParams process) {
        saturationCorrection(process.saturationMap);
        process.sharpenFactor -= 0.1f;
    }

    private void saturationCorrection(float[] saturationMap) {
        saturationMap[0] *= 1.1f;
        saturationMap[1] *= 1.3f;
        saturationMap[2] *= 1.2f;
        saturationMap[3] *= 1.3f;
        saturationMap[4] *= 1.2f;
        saturationMap[5] *= 1.0f;
        saturationMap[6] *= 1.1f;
        saturationMap[7] *= 1.1f;
    }
}
