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
                process.saturationMap = new float[] { 1f };
                process.histFactor = 0f;
                break;
            case Natural:
                TIFFTag modelTag = tags.get(TIFF.TAG_Model);
                DeviceMap.Device device = DeviceMap.get(modelTag == null ? "" : modelTag.toString());
                device.neutralPointCorrection(tags, sensor.neutralColorPoint);
                process.sharpenFactor = device.sharpenFactor(tags);
                float r = 1.2f; // Red, Skin
                float y = 1.25f; // Yellow
                float g = 1.55f; // Green, Grass
                float gb = 1.65f; // Green, Foliage
                float lb = 1.45f; // Blue, Water
                float db = 1.25f; // Blue, Sky
                float dp = 1.15f; // Purple
                float p = 0.85f; // Pink
                process.saturationMap = new float[] { r, y, g, gb, lb, db, dp, p, r };
                process.histFactor = 0.25f;
                break;
            case Boosted:
                process.sharpenFactor = 0.45f;
                process.saturationMap = new float[] { 1.75f };
                process.histFactor = 0.5f;
                break;
        }

    }
}
