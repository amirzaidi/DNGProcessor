package amirz.dngprocessor.math;

public class Histogram {
    private static final double EPSILON = 0.01;

    public final float[] sigma = new float[3];
    public final float[] hist;
    public final float logAvgLuminance;

    public Histogram(float[] f, int whPixels) {
        int histBins = 256;
        int[] histv = new int[histBins];

        double logTotalLuminance = 0d;
        // Loop over all values
        for (int i = 0; i < f.length; i += 4) {
            for (int j = 0; j < 3; j++) {
                sigma[j] += f[i + j];
            }

            int bin = (int) (f[i + 3] * histBins);
            if (bin < 0) bin = 0;
            if (bin >= histBins) bin = histBins - 1;
            histv[bin]++;

            logTotalLuminance += Math.log(f[i + 3] + EPSILON);
        }

        logAvgLuminance = (float) Math.exp(logTotalLuminance * 4 / f.length);
        for (int j = 0; j < 3; j++) {
            sigma[j] /= whPixels;
        }

        limitHighlightContrast(histv, f.length / 4);

        float[] cumulativeHist = new float[histBins + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + histv[i - 1];
        }

        float maxZ = 0.95f;
        float max = cumulativeHist[histBins];
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
            cumulativeHist[i] *= maxZ;
        }

        // Limit contrast and banding.
        float[] tmp = new float[cumulativeHist.length];
        for (int i = 0; i < 300; i++) {
            tmp[0] = cumulativeHist[0];
            for (int j = 1; j < cumulativeHist.length - 1; j++) {
                tmp[j] = (cumulativeHist[j - 1] + cumulativeHist[j + 1]) * 0.5f;
            }
            tmp[tmp.length - 1] = cumulativeHist[cumulativeHist.length - 1];

            float[] swp = tmp;
            tmp = cumulativeHist;
            cumulativeHist = swp;
        }

        improveHist(cumulativeHist);
        hist = cumulativeHist;

        //float[] gauss = { 0.06136f, 0.24477f, 0.38774f, 0.24477f, 0.06136f };
        //hist = Convolve.conv(cumulativeHist, gauss, true);
    }

    private static void improveHist(float[] hist) {
        /*
        for (int i = 0; i < hist.length; i++) {
            float og = (float) i / hist.length;
            float heq = hist[i];
            float a = Math.min(0.75f, 30f * og);
            hist[i] = heq * a + og * (1f - a);
        }

        // In case we messed up the histogram, flatten it.
        for (int i = 1; i < hist.length; i++) {
            if (hist[i] < hist[i - 1]) {
                hist[i] = hist[i - 1];
            }
        }*/

        // Crush shadows
        int maxShadow = hist.length / 50;
        for (int i = 0; i < maxShadow; i++) {
            hist[i] *= Math.sqrt((float) i / maxShadow);
        }
    }

    // Shift highlights down
    private static void limitHighlightContrast(int[] clippedHist, int valueCount) {
        for (int i = clippedHist.length - 1; i >= clippedHist.length / 4; i--) {
            int limit = 4 * valueCount / i;

            if (clippedHist[i] > limit) {
                int removed = clippedHist[i] - limit;
                clippedHist[i] = limit;

                for (int j = i - 1; j >= 0; j--) {
                    int space = limit - clippedHist[j];
                    if (space > 0) {
                        int allocate = Math.min(removed, space);
                        clippedHist[j] += allocate;
                        removed -= allocate;
                        if (removed == 0) {
                            break;
                        }
                    }
                }
            }
        }
    }
}
