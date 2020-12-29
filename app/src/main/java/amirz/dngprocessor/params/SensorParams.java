package amirz.dngprocessor.params;

import android.util.Rational;

public class SensorParams {
    public int inputWidth;
    public int inputHeight;
    public int inputStride;
    public int cfa;
    public byte[] cfaVal;
    public int[] blackLevelPattern;
    public int whiteLevel;
    public int referenceIlluminant1;
    public int referenceIlluminant2;
    public float[] calibrationTransform1;
    public float[] calibrationTransform2;
    public float[] colorMatrix1;
    public float[] colorMatrix2;
    public float[] forwardTransform1;
    public float[] forwardTransform2;
    public float[] neutralColorPoint;
    public float[] noiseProfile;
    public int outputOffsetX;
    public int outputOffsetY;
    public float[] gainMap;
    public int[] gainMapSize;
    public short[] hotPixels = new short[1];
    public int[] hotPixelsSize = new int[] { 1, 1 };
}
