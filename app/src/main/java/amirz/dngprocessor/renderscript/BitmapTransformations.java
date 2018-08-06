package amirz.dngprocessor.renderscript;

import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicConvolve3x3;

public class BitmapTransformations {
    private static float[] MATRIX_SHARPEN = { 0, -1, 0,
                    -1, 5, -1,
                    0, -1, 0};

    private static float[] MATRIX_BLUR = { 0, 0.2f, 0,
            0.2f, 0.2f, 0.2f,
            0, 0.2f, 0};

    public static Bitmap sharpen(RenderScript rs, Bitmap src) {
        return apply(rs, src, MATRIX_SHARPEN);
    }

    public static Bitmap blur(RenderScript rs, Bitmap src) {
        return apply(rs, src, MATRIX_BLUR);
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
