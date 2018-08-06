package amirz.dngprocessor.renderscript;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicConvolve3x3;

public class BitmapTransformations {
    public static Bitmap sharpen(RenderScript rs, Bitmap src, float intensity) {
        return apply(rs, src, sharpenMatrix(intensity));
    }

    private static float[] sharpenMatrix(float intensity) {
        float mid = 1 + 4 * intensity;
        float side = -intensity;
        return new float[] {
                0, side, 0,
                side, mid, side,
                0, side, 0
        };
    }

    private static Bitmap apply(RenderScript rs, Bitmap src, float[] coefficients) {
        Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), src.getConfig());

        Allocation input = Allocation.createFromBitmap(rs, src);
        Allocation output = Allocation.createFromBitmap(rs, result);

        ScriptIntrinsicConvolve3x3 convolution = ScriptIntrinsicConvolve3x3.create(rs, Element.U8_4(rs));
        convolution.setInput(input);
        convolution.setCoefficients(coefficients);
        convolution.forEach(output);

        output.copyTo(result);
        return result;
    }
}
