package amirz.dngprocessor.math;

public class Histogram {
    public final float[] sigma = new float[3];
    public final float[] hist;

    public Histogram(float[] f, int whPixels) {
        int histBins = 512;
        int[] histv = new int[histBins];

        // Loop over all values
        for (int i = 0; i < f.length; i += 4) {
            for (int j = 0; j < 3; j++) {
                sigma[j] += f[i + j];
            }

            int bin = (int) (f[i + 3] * 0.5f * histBins);
            if (bin >= histBins) bin = histBins - 1;
            histv[bin]++;
        }

        for (int j = 0; j < 3; j++) {
            sigma[j] /= whPixels;
        }

        float[] cumulativeHist = new float[histBins + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + histv[i - 1];
        }

        float max = cumulativeHist[histBins];
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
        }

        float[] gauss = { 0.06136f, 0.24477f, 0.38774f, 0.24477f, 0.06136f };
        hist = Convolve.conv(cumulativeHist, gauss, true);
    }
}
