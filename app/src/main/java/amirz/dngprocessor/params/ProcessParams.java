package amirz.dngprocessor.params;

public class ProcessParams {
    public int denoiseRadius;
    public float sharpenFactor;
    public float[] saturationCurve; // x - y * s^z
    public float[] stretchPerc;
    public boolean histEqualization;
}
