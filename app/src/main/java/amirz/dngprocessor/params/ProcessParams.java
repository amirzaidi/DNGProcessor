package amirz.dngprocessor.params;

import amirz.dngprocessor.Settings;

public class ProcessParams {
    public static ProcessParams getPreset(Settings.PostProcessMode mode) {
        ProcessParams process = new ProcessParams();
        switch (mode) {
            case Disabled:
                process.sharpenFactor = 0f;
                process.saturationMap = new float[] { 1f };
                process.histFactor = 0f;
                break;
            case Natural:
                process.sharpenFactor = 0.3f;
                float r = 1.3f; // Red, Skin
                float y = 1.35f; // Yellow
                float g = 1.5f; // Green, Grass
                float gb = 1.6f; // Green, Foliage
                float lb = 1.55f; // Blue, Water
                float db = 1.5f; // Blue, Sky
                float dp = 1.45f; // Purple
                float p = 1.25f; // Pink
                process.saturationMap = new float[] { r, y, g, gb, lb, db, dp, p, r };
                process.histFactor = 0.25f;
                break;
            case Boosted:
                process.sharpenFactor = 0.45f;
                process.saturationMap = new float[] { 1.75f };
                process.histFactor = 0.5f;
                break;
        }
        return process;
    }

    public int denoiseFactor;
    public float sharpenFactor;
    public float[] saturationMap;
    public float histFactor;
    public boolean lce;

    private ProcessParams() {
    }
}
