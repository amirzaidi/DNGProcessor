package amirz.dngprocessor.util;

public class Constants {
    public static final int BLOCK_HEIGHT = 32;

    public static final short HORZ = 1;
    public static final short VERT = 2;
    public static final short PLUS = HORZ | VERT;
    public static final short CROSS = 4;

    public static final float[] ARCSOFT_CC1 = new float[] {
            1.109375f, -0.5234375f, -0.171875f,
            -0.96875f, 1.875f, 0.0390625f,
            0.046875f, -0.171875f, 0.8984375f
    };

    public static final float[] ARCSOFT_CC2 = new float[] {
            1.4375f, -0.6796875f, -0.21875f,
            -0.96875f, 1.875f, 0.0390625f,
            0.0390625f, -0.140625f, 0.734375f
    };

    public static final float[] DIAGONAL = new float[] {
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
    };
}
