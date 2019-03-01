package amirz.dngprocessor.math;

public class Convolve {
    public static float[] conv(float[] in, float[] conv, boolean cut) {
        float[] out = new float[in.length + conv.length - 1];
        float[] inCopy = new float[out.length + conv.length - 1];
        System.arraycopy(in, 0, inCopy, conv.length - 1, in.length);

        for (int i = 0; i < conv.length - 1; i++) {
            inCopy[i] = in[0];
        }
        for (int i = out.length; i < inCopy.length; i++) {
            inCopy[i] = in[in.length - 1];
        }

        for (int i = 0; i < out.length; i++) {
            for (int j = 0; j < conv.length; j++) {
                out[i] += inCopy[conv.length - 1 + i - j] * conv[j];
            }
        }
        if (cut) {
            float[] outCut = new float[in.length];
            System.arraycopy(out, conv.length - 1, outCut, 0, outCut.length);
            return outCut;
        }
        return out;
    }
}
