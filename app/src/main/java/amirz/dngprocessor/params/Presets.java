package amirz.dngprocessor.params;

import android.util.SparseArray;

import amirz.dngprocessor.Settings;
import amirz.dngprocessor.device.DeviceMap;
import amirz.dngprocessor.parser.TIFF;
import amirz.dngprocessor.parser.TIFFTag;

public class Presets {
    public static void apply(Settings.PostProcessMode mode, SparseArray<TIFFTag> tags, SensorParams sensor, ProcessParams process) {
        switch (mode) {
            case Disabled:
                process.sharpenFactor = 0f;
                process.saturationCurve = new float[] { 1f, 0f, 0f };
                process.stretchPerc = new float[] { 0f, 1f };
                break;
            case Natural:
                TIFFTag modelTag = tags.get(TIFF.TAG_Model);
                DeviceMap.Device device = DeviceMap.get(modelTag == null ? "" : modelTag.toString());
                device.neutralPointCorrection(tags, sensor.neutralColorPoint);
                process.sharpenFactor = device.sharpenFactor(tags);
                process.saturationCurve = new float[] { 1.4f, 0.5f, 1.25f };
                process.stretchPerc = device.stretchPerc(tags);
                break;
            case Boosted:
                process.sharpenFactor = 0.65f;
                process.saturationCurve = new float[] { 2.25f, 0.5f, 1f };
                process.stretchPerc = new float[] { 0.1f, 0.95f };
                break;
        }
    }
}
