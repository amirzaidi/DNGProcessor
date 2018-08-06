/*
 * Copyright 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package amirz.dngprocessor.renderscript;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicHistogram;
/**
 * Utility class providing methods for various pixel-wise ARGB bitmap operations.
 */
public class BitmapUtils {
    private static final String TAG = "BitmapUtils";
    private static final int COLOR_BIT_DEPTH = 256;
    public static int A = 3;
    public static int R = 0;
    public static int G = 1;
    public static int B = 2;
    public static int NUM_CHANNELS = 4;

    /**
     * Return the histograms for each color channel (interleaved).
     *
     * @param rs a {@link RenderScript} context to use.
     * @param bmap a {@link Bitmap} to generate the histograms for.
     * @return an array containing NUM_CHANNELS * COLOR_BIT_DEPTH histogram bucket values, with
     * the color channels interleaved.
     */
    public static int[] calcHistograms(RenderScript rs, Bitmap bmap) {
        ScriptIntrinsicHistogram hist = ScriptIntrinsicHistogram.create(rs, Element.U8_4(rs));
        Allocation sums = Allocation.createSized(rs, Element.I32_4(rs), COLOR_BIT_DEPTH);
        // Setup input allocation (ARGB 8888 bitmap).
        Allocation input = Allocation.createFromBitmap(rs, bmap);
        hist.setOutput(sums);
        hist.forEach(input);
        int[] output = new int[COLOR_BIT_DEPTH * NUM_CHANNELS];
        sums.copyTo(output);
        return output;
    }

    /**
     * Find the difference between two bitmaps using average of per-pixel differences.
     *
     * @param a first {@link Bitmap}.
     * @param b second {@link Bitmap}.
     * @return the difference.
     */
    public static double calcDifferenceMetric(Bitmap a, Bitmap b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            throw new IllegalArgumentException("Bitmap dimensions for arguments do not match a=" +
                    a.getWidth() + "x" + a.getHeight() + ", b=" + b.getWidth() + "x" +
                    b.getHeight());
        }
        // TODO: Optimize this in renderscript to avoid copy.
        int[] aPixels = new int[a.getHeight() * a.getWidth()];
        int[] bPixels = new int[aPixels.length];
        a.getPixels(aPixels, /*offset*/0, /*stride*/a.getWidth(), /*x*/0, /*y*/0, a.getWidth(),
                a.getHeight());
        b.getPixels(bPixels, /*offset*/0, /*stride*/b.getWidth(), /*x*/0, /*y*/0, b.getWidth(),
                b.getHeight());
        double diff = 0;
        for (int i = 0; i < aPixels.length; i++) {
            int aPix = aPixels[i];
            int bPix = bPixels[i];
            diff += Math.abs(Color.red(aPix) - Color.red(bPix)); // red
            diff += Math.abs(Color.green(aPix) - Color.green(bPix)); // green
            diff += Math.abs(Color.blue(aPix) - Color.blue(bPix)); // blue
        }
        diff /= (aPixels.length * 3);
        return diff;
    }
}