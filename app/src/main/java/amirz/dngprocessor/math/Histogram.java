package amirz.dngprocessor.math;

public class Histogram {
    private static final int HIST_BINS = 256;
    private static final double EPSILON = 0.01;
    private static final float LINEARIZE_PERCEPTION = 2.4f;

    public final float[] sigma = new float[3];
    public final float[] hist;
    public final float gamma;
    public final float logAvgLuminance;

    public Histogram(float[] f, int whPixels) {
        int[] histv = new int[HIST_BINS];

        double logTotalLuminance = 0d;
        // Loop over all values
        for (int i = 0; i < f.length; i += 4) {
            for (int j = 0; j < 3; j++) {
                sigma[j] += f[i + j];
            }

            int bin = (int) (f[i + 3] * HIST_BINS);
            if (bin < 0) bin = 0;
            if (bin >= HIST_BINS) bin = HIST_BINS - 1;
            histv[bin]++;

            logTotalLuminance += Math.log(f[i + 3] + EPSILON);
        }

        logAvgLuminance = (float) Math.exp(logTotalLuminance * 4 / f.length);
        for (int j = 0; j < 3; j++) {
            sigma[j] /= whPixels;
        }

        //limitHighlightContrast(histv, f.length / 4);

        float[] cumulativeHist = new float[HIST_BINS + 1];
        for (int i = 1; i < cumulativeHist.length; i++) {
            cumulativeHist[i] = cumulativeHist[i - 1] + histv[i - 1];
        }

        float max = cumulativeHist[HIST_BINS];
        for (int i = 0; i < cumulativeHist.length; i++) {
            cumulativeHist[i] /= max;
        }

        float sumExponent = 0.f;
        int exponentCounted = 0;
        float maxi = cumulativeHist.length - 1;
        for (int i = 0; i <= maxi; i++) {
            float val = cumulativeHist[i];
            if (val > 0.001f) {
                // Which power of the input is the output.
                double exponent = Math.log(cumulativeHist[i]) / Math.log(i / maxi);
                if (exponent > 0f && exponent < 10f) {
                    sumExponent += exponent;
                    exponentCounted++;
                }
            }
        }

        // Blend shadows with linear curve.
        for (int i = 0; i < cumulativeHist.length; i++) {
            float og = (float) i / cumulativeHist.length;
            float heq = cumulativeHist[i];
            float a = Math.min(1f, 21f * og);
            if (a == 1f) {
                break;
            }
            cumulativeHist[i] = heq * a + og * (1f - a);
        }

        // In case we messed up the histogram, flatten it.
        for (int i = 1; i < cumulativeHist.length; i++) {
            if (cumulativeHist[i] < cumulativeHist[i - 1]) {
                cumulativeHist[i] = cumulativeHist[i - 1];
            }
        }

        // Limit contrast and banding.
        float[] tmp = new float[cumulativeHist.length];
        for (int i = cumulativeHist.length - 1; i > 0; i--) {
            System.arraycopy(cumulativeHist, 0, tmp, 0, i);
            for (int j = i; j < cumulativeHist.length - 1; j++) {
                tmp[j] = (cumulativeHist[j - 1] + cumulativeHist[j + 1]) * 0.5f;
            }
            tmp[tmp.length - 1] = cumulativeHist[cumulativeHist.length - 1];

            float[] swp = tmp;
            tmp = cumulativeHist;
            cumulativeHist = swp;
        }

        // Inverse of the average exponent.
        gamma = LINEARIZE_PERCEPTION * sumExponent / exponentCounted;

        for (int i = 1; i < cumulativeHist.length; i++) {
            // Compensate for the gamma being applied first.
            cumulativeHist[i] *= (i / maxi) / Math.pow(i / maxi, gamma);
        }

        // To prevent blowing up very dark scenes.
        hist = cumulativeHist;
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
