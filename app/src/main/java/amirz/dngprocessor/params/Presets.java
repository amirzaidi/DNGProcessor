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
                process.saturationMap = new float[] { 1.f };
                process.histFactor = 0f;
                break;
            case Natural:
                TIFFTag modelTag = tags.get(TIFF.TAG_Model);
                DeviceMap.Device device = DeviceMap.get(modelTag == null ? "" : modelTag.toString());
                device.neutralPointCorrection(tags, sensor.neutralColorPoint);
                process.sharpenFactor = device.sharpenFactor(tags);
                float r = 1.1f; // Skin
                float y = 1.275f;
                float g = 1.6f; // Grass
                float gb = 1.7f; // Grass
                float lb = 1.5f; // Water
                float db = 1.3f; // Sky
                float dp = 1.1f;
                float p = 0.9f;
                process.saturationMap = new float[] { r, y, g, gb, lb, db, dp, p, r };
                process.histFactor = 0.1f;
                break;
            case Boosted:
                process.sharpenFactor = 0.65f;
                process.saturationMap = new float[] { 1.75f };
                process.histFactor = 0.2f;
                break;
        }

    }
}
