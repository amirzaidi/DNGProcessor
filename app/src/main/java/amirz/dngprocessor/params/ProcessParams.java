package amirz.dngprocessor.params;

import amirz.dngprocessor.Preferences;

public class ProcessParams {
    public static ProcessParams getPreset(Preferences.PostProcessMode mode) {
        ProcessParams process = new ProcessParams();
        switch (mode) {
            case Disabled:
                process.sharpenFactor = 0f;
                process.histFactor = 0f;
                process.histCurve = 0f;
                process.adaptiveSaturation = new float[] { 0f, 1f };
                break;
            case Natural:
                process.sharpenFactor = 0.25f;
                process.histFactor = 0.8f;
                process.histCurve = 1.2f;
                process.adaptiveSaturation = new float[] { 2.5f, 4f };
                break;
            case Boosted:
                process.sharpenFactor = 0.45f;
                process.histFactor = 1f;
                process.histCurve = 1.1f;
                process.adaptiveSaturation = new float[] { 4f, 2f };
                break;
        }
        return process;
    }

    public float sharpenFactor;
    public float histFactor;
    public float histCurve;

    public int denoiseFactor;
    public float[] saturationMap;
    public float satLimit;
    public float[] adaptiveSaturation;
    public boolean lce;
    public boolean ahe;

    private ProcessParams() {
    }
}
