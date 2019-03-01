package amirz.dngprocessor.math;

public class Convolve {
    public static float[] conv(float[] in, float[] conv) {
        float[] out = new float[in.length + conv.length - 1];
        float[] inCopy = new float[out.length + conv.length - 1];
        System.arraycopy(in, 0, inCopy, conv.length - 1, in.length);

        for (int i = 0; i < out.length; i++) {
            for (int j = 0; j < conv.length; j++) {
                out[i] += inCopy[conv.length - 1 + i - j] * conv[j];
            }
        }
        return out;
    }
}
