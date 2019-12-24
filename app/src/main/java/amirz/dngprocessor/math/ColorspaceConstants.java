package amirz.dngprocessor.math;

import android.hardware.camera2.CameraMetadata;
import android.util.SparseIntArray;

public class ColorspaceConstants {
    /**
     * Matrix to convert from CIE XYZ colorspace to sRGB, Bradford-adapted to D65.
     */
    public static final float[] sXYZtoSRGB = new float[]{
            3.1338561f, -1.6168667f, -0.4906146f,
            -0.9787684f, 1.9161415f, 0.0334540f,
            0.0719453f, -0.2289914f, 1.4052427f
    };

    /**
     * Matrix to convert from the ProPhoto RGB colorspace to CIE XYZ colorspace.
     */
    public static final float[] sProPhotoToXYZ = new float[]{
            0.797779f, 0.135213f, 0.031303f,
            0.288000f, 0.711900f, 0.000100f,
            0.000000f, 0.000000f, 0.825105f
    };

    /**
     * Matrix to convert from CIE XYZ colorspace to ProPhoto RGB colorspace.
     * Tone-mapping is done in that colorspace before converting back.
     */
    public static final float[] sXYZtoProPhoto = new float[]{
            1.345753f, -0.255603f, -0.051025f,
            -0.544426f, 1.508096f, 0.020472f,
            0.000000f, 0.000000f, 1.211968f
    };

    /*
     * Coefficients for a 3rd order polynomial, ordered from highest to lowest power.  This
     * polynomial approximates the default tonemapping curve used for ACR3.
     *
    public static final float[] DEFAULT_ACR3_TONEMAP_CURVE_COEFFS = new float[]{
            -0.7836f, 0.8469f, 0.943f, 0.0209f
    };
    */

    /**
     * Coefficients for a 3rd order polynomial, ordered from highest to lowest power.
     * Adapted to transform from [0,1] to [0,1]
     */
    public static final float[] CUSTOM_ACR3_TONEMAP_CURVE_COEFFS = new float[] {
            -1.087f, 1.643f, 0.443f, 0f
    };

    /**
     * The D50 whitepoint coordinates in CIE XYZ colorspace.
     */
    public static final float[] D50_XYZ = new float[]{0.9642f, 1, 0.8249f};

    /**
     * An array containing the color temperatures for standard reference illuminants.
     */
    public static final SparseIntArray sStandardIlluminants = new SparseIntArray();

    public static final int NO_ILLUMINANT = -1;

    static {
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT, 6504);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_TUNGSTEN, 2856);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D65, 6504);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D50, 5003);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D55, 5503);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_D75, 7504);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_A, 2856);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_B, 4874);
        sStandardIlluminants.append(CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_STANDARD_C, 6774);
        sStandardIlluminants.append(
                CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_DAYLIGHT_FLUORESCENT, 6430);
        sStandardIlluminants.append(
                CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_COOL_WHITE_FLUORESCENT, 4230);
        sStandardIlluminants.append(
                CameraMetadata.SENSOR_REFERENCE_ILLUMINANT1_WHITE_FLUORESCENT, 3450);
        // TODO: Add the rest of the illuminants included in the LightSource EXIF tag.

        /*
        public static final int SENSOR_REFERENCE_ILLUMINANT1_FLUORESCENT = 2;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_FLASH = 4;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_FINE_WEATHER = 9;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_CLOUDY_WEATHER = 10;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_SHADE = 11;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_DAY_WHITE_FLUORESCENT = 13;
        public static final int SENSOR_REFERENCE_ILLUMINANT1_ISO_STUDIO_TUNGSTEN = 24;
        */
    }
}
