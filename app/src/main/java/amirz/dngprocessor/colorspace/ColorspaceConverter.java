package amirz.dngprocessor.colorspace;

import android.util.Log;
import android.util.Rational;

import java.util.Arrays;

import amirz.dngprocessor.params.SensorParams;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.D50_XYZ;
import static amirz.dngprocessor.colorspace.ColorspaceConstants.NO_ILLUMINANT;
import static amirz.dngprocessor.colorspace.ColorspaceConstants.sProPhotoToXYZ;
import static amirz.dngprocessor.colorspace.ColorspaceConstants.sStandardIlluminants;
import static amirz.dngprocessor.colorspace.ColorspaceConstants.sXYZtoProPhoto;
import static amirz.dngprocessor.colorspace.ColorspaceConstants.sXYZtoSRGB;

public class ColorspaceConverter {
    protected static final String TAG = "ColorspaceConverter";
    protected static final boolean DEBUG = true;

    public float[] sensorToXYZ_D50 = new float[9];
    public float[] XYZtoProPhoto = new float[9];
    public float[] proPhotoToSRGB = new float[9];

    public ColorspaceConverter(SensorParams sensor) {
        if (sensor.outputOffsetX < 0 || sensor.outputOffsetY < 0) {
            throw new IllegalArgumentException("Negative offset passed to convertToSRGB");
        }
        if ((sensor.inputStride / 2) < sensor.inputWidth) {
            throw new IllegalArgumentException("Stride too small.");
        }
        if ((sensor.inputStride % 2) != 0) {
            throw new IllegalArgumentException("Invalid stride for RAW16 format ("
                    + sensor.inputStride + ")");
        }

        if (sensor.cfa < 0 || sensor.cfa > 3) {
            throw new IllegalArgumentException("Unsupported cfa pattern " + sensor.cfa + " used.");
        }

        if (DEBUG) {
            Log.d(TAG, "Metadata Used:");
            Log.d(TAG, "Input width,height: " + sensor.inputWidth + "," + sensor.inputHeight);
            Log.d(TAG, "Output offset x,y: " + sensor.outputOffsetX + "," + sensor.outputOffsetY);
            Log.d(TAG, "CFA: " + sensor.cfa);
            Log.d(TAG, "BlackLevelPattern: " + Arrays.toString(sensor.blackLevelPattern));
            Log.d(TAG, "WhiteLevel: " + sensor.whiteLevel);
            Log.d(TAG, "ReferenceIlluminant1: " + sensor.referenceIlluminant1);
            Log.d(TAG, "ReferenceIlluminant2: " + sensor.referenceIlluminant2);
            Log.d(TAG, "CalibrationTransform1: " + Arrays.toString(sensor.calibrationTransform1));
            Log.d(TAG, "CalibrationTransform2: " + Arrays.toString(sensor.calibrationTransform2));
            Log.d(TAG, "ColorMatrix1: " + Arrays.toString(sensor.colorMatrix1));
            Log.d(TAG, "ColorMatrix2: " + Arrays.toString(sensor.colorMatrix2));
            Log.d(TAG, "ForwardTransform1: " + Arrays.toString(sensor.forwardTransform1));
            Log.d(TAG, "ForwardTransform2: " + Arrays.toString(sensor.forwardTransform2));
            Log.d(TAG, "NeutralColorPoint: " + Arrays.toString(sensor.neutralColorPoint));
        }

        float[] normalizedColorMatrix1 = Arrays.copyOf(sensor.colorMatrix1, sensor.colorMatrix1.length);
        normalizeCM(normalizedColorMatrix1);

        float[] normalizedColorMatrix2 = Arrays.copyOf(sensor.colorMatrix2, sensor.colorMatrix2.length);
        normalizeCM(normalizedColorMatrix2);

        if (DEBUG) {
            Log.d(TAG, "Normalized ColorMatrix1: " + Arrays.toString(normalizedColorMatrix1));
            Log.d(TAG, "Normalized ColorMatrix2: " + Arrays.toString(normalizedColorMatrix2));
        }

        // Calculate full sensor colorspace to sRGB colorspace transform.
        float[] sensorToXYZ = new float[9];
        double interpolationFactor = findDngInterpolationFactor(sensor.referenceIlluminant1,
                sensor.referenceIlluminant2, sensor.calibrationTransform1, sensor.calibrationTransform2,
                normalizedColorMatrix1, normalizedColorMatrix2, sensor.neutralColorPoint,
                sensorToXYZ);
        if (DEBUG) Log.d(TAG, "Interpolation factor used: " + interpolationFactor);

        if (sensor.forwardTransform1 != null && sensor.forwardTransform2 != null) {
            float[] normalizedForwardTransform1 = Arrays.copyOf(sensor.forwardTransform1,
                    sensor.forwardTransform1.length);
            normalizeFM(normalizedForwardTransform1);

            float[] normalizedForwardTransform2 = Arrays.copyOf(sensor.forwardTransform2,
                    sensor.forwardTransform2.length);
            normalizeFM(normalizedForwardTransform2);

            if (DEBUG) {
                Log.d(TAG, "Normalized ForwardTransform1: " + Arrays.toString(normalizedForwardTransform1));
                Log.d(TAG, "Normalized ForwardTransform2: " + Arrays.toString(normalizedForwardTransform2));
            }

            calculateCameraToXYZD50TransformFM(normalizedForwardTransform1, normalizedForwardTransform2,
                    sensor.calibrationTransform1, sensor.calibrationTransform2, sensor.neutralColorPoint,
                    interpolationFactor, sensorToXYZ_D50);
        } else {
            float[] neutralColorPoint = {
                    sensor.neutralColorPoint[0].floatValue(),
                    sensor.neutralColorPoint[1].floatValue(),
                    sensor.neutralColorPoint[2].floatValue()
            };

            float[] XYZ = new float[3];
            map(sensorToXYZ, neutralColorPoint, XYZ);

            XYZ[0] /= XYZ[1];
            XYZ[2] /= XYZ[1];
            XYZ[1] = 1f;

            float[] CA = mapWhiteMatrix(XYZ);
            multiply(CA, sensorToXYZ, sensorToXYZ_D50);
        }

        if (DEBUG) Log.d(TAG, "sensorToXYZ xform used: " + Arrays.toString(sensorToXYZ_D50));

        System.arraycopy(sXYZtoProPhoto, 0, XYZtoProPhoto, 0, sXYZtoProPhoto.length);
        if (DEBUG) Log.d(TAG, "XYZtoProPhoto xform used: " + Arrays.toString(XYZtoProPhoto));

        multiply(sXYZtoSRGB, sProPhotoToXYZ, /*out*/proPhotoToSRGB);
        if (DEBUG) Log.d(TAG, "proPhotoToSRGB xform used: " + Arrays.toString(proPhotoToSRGB));

    }

    private float[] mapWhiteMatrix(float[] sensorWhiteXYZ) {
        float[] Mb = {
                0.8951f,  0.2664f, -0.1614f,
                -0.7502f, 1.7135f,  0.0367f,
                0.0389f, -0.0685f, 1.0296f
        };

        float[] w1 = new float[3];
        map(Mb, sensorWhiteXYZ, w1);

        float[] w2 = new float[3];
        map(Mb, D50_XYZ, w2);

        float[] A = new float[9];
        A[0] = (float) Math.max(0.1, Math.min(w1[0] > 0 ? w2[0] / w1[0] : 10, 10));
        A[4] = (float) Math.max(0.1, Math.min(w1[1] > 0 ? w2[1] / w1[1] : 10, 10));
        A[8] = (float) Math.max(0.1, Math.min(w1[2] > 0 ? w2[2] / w1[2] : 10, 10));

        float[] MbInv = new float[9];
        if (!invert(Mb, MbInv)) {
            throw new IllegalArgumentException("Cannot invert mb");
        }

        float[] MbInvA = new float[9];
        multiply(MbInv, A, MbInvA);

        float[] MbInvAMb = new float[9];
        multiply(MbInvA, Mb, MbInvAMb);

        return MbInvAMb;
    }

    /**
     * Calculate the correlated color temperature (CCT) for a given x,y chromaticity in CIE 1931 x,y
     * chromaticity space using McCamy's cubic approximation algorithm given in:
     * <p>
     * McCamy, Calvin S. (April 1992).
     * "Correlated color temperature as an explicit function of chromaticity coordinates".
     * Color Research & Application 17 (2): 142–144
     *
     * @param x x chromaticity component.
     * @param y y chromaticity component.
     * @return the CCT associated with this chromaticity coordinate.
     */
    private static double calculateColorTemperature(double x, double y) {
        double n = (x - 0.332) / (y - 0.1858);
        return -449 * Math.pow(n, 3) + 3525 * Math.pow(n, 2) - 6823.3 * n + 5520.33;
    }

    /**
     * Calculate the x,y chromaticity coordinates in CIE 1931 x,y chromaticity space from the given
     * CIE XYZ coordinates.
     *
     * @param X the CIE XYZ X coordinate.
     * @param Y the CIE XYZ Y coordinate.
     * @param Z the CIE XYZ Z coordinate.
     * @return the [x, y] chromaticity coordinates as doubles.
     */
    private static double[] calculateCIExyCoordinates(double X, double Y, double Z) {
        double[] ret = new double[]{0, 0};
        ret[0] = X / (X + Y + Z);
        ret[1] = Y / (X + Y + Z);
        return ret;
    }

    /**
     * Linearly interpolate between a and b given fraction f.
     *
     * @param a first term to interpolate between, a will be returned when f == 0.
     * @param b second term to interpolate between, b will be returned when f == 1.
     * @param f the fraction to interpolate by.
     * @return interpolated result as double.
     */
    private static double lerp(double a, double b, double f) {
        return (a * (1.0f - f)) + (b * f);
    }

    /**
     * Linearly interpolate between 3x3 matrices a and b given fraction f.
     *
     * @param a      first 3x3 matrix to interpolate between, a will be returned when f == 0.
     * @param b      second 3x3 matrix to interpolate between, b will be returned when f == 1.
     * @param f      the fraction to interpolate by.
     * @param result will be set to contain the interpolated matrix.
     */
    private static void lerp(float[] a, float[] b, double f, /*out*/float[] result) {
        for (int i = 0; i < 9; i++) {
            result[i] = (float) lerp(a[i], b[i], f);
        }
    }

    /**
     * Find the interpolation factor to use with the RAW matrices given a neutral color point.
     *
     * @param referenceIlluminant1  first reference illuminant.
     * @param referenceIlluminant2  second reference illuminant.
     * @param calibrationTransform1 calibration matrix corresponding to the first reference
     *                              illuminant.
     * @param calibrationTransform2 calibration matrix corresponding to the second reference
     *                              illuminant.
     * @param colorMatrix1          color matrix corresponding to the first reference illuminant.
     * @param colorMatrix2          color matrix corresponding to the second reference illuminant.
     * @param neutralColorPoint     the neutral color point used to calculate the interpolation factor.
     * @return the interpolation factor corresponding to the given neutral color point.
     */
    private static double findDngInterpolationFactor(int referenceIlluminant1,
                                                     int referenceIlluminant2, float[] calibrationTransform1, float[] calibrationTransform2,
                                                     float[] colorMatrix1, float[] colorMatrix2, Rational[/*3*/] neutralColorPoint,
                                                     float[] interpXYZToCameraInverse) {
        int colorTemperature1 = sStandardIlluminants.get(referenceIlluminant1, NO_ILLUMINANT);
        if (colorTemperature1 == NO_ILLUMINANT) {
            throw new IllegalArgumentException("No such illuminant for reference illuminant 1: " +
                    referenceIlluminant1);
        }
        int colorTemperature2 = sStandardIlluminants.get(referenceIlluminant2, NO_ILLUMINANT);
        if (colorTemperature2 == NO_ILLUMINANT) {
            throw new IllegalArgumentException("No such illuminant for reference illuminant 2: " +
                    referenceIlluminant2);
        }
        if (DEBUG) Log.d(TAG, "ColorTemperature1: " + colorTemperature1);
        if (DEBUG) Log.d(TAG, "ColorTemperature2: " + colorTemperature2);
        double interpFactor = 0.5; // Initial guess for interpolation factor
        double oldInterpFactor = interpFactor;
        double lastDiff = Double.MAX_VALUE;
        double tolerance = 0.0001;
        float[] XYZToCamera1 = new float[9];
        float[] XYZToCamera2 = new float[9];
        multiply(calibrationTransform1, colorMatrix1, /*out*/XYZToCamera1);
        multiply(calibrationTransform2, colorMatrix2, /*out*/XYZToCamera2);
        float[] cameraNeutral = new float[]{neutralColorPoint[0].floatValue(),
                neutralColorPoint[1].floatValue(), neutralColorPoint[2].floatValue()};
        float[] neutralGuess = new float[3];
        float[] interpXYZToCamera = new float[9];
        double lower = Math.min(colorTemperature1, colorTemperature2);
        double upper = Math.max(colorTemperature1, colorTemperature2);
        if (DEBUG) {
            Log.d(TAG, "XYZtoCamera1: " + Arrays.toString(XYZToCamera1));
            Log.d(TAG, "XYZtoCamera2: " + Arrays.toString(XYZToCamera2));
            Log.d(TAG, "Finding interpolation factor, initial guess 0.5...");
        }
        // Iteratively guess xy value, find new CCT, and update interpolation factor.
        int loopLimit = 30;
        int count = 0;
        while (lastDiff > tolerance && loopLimit > 0) {
            if (DEBUG) Log.d(TAG, "Loop count " + count);
            lerp(XYZToCamera1, XYZToCamera2, interpFactor, interpXYZToCamera);
            if (!invert(interpXYZToCamera, /*out*/interpXYZToCameraInverse)) {
                throw new IllegalArgumentException(
                        "Cannot invert XYZ to Camera matrix, input matrices are invalid.");
            }
            map(interpXYZToCameraInverse, cameraNeutral, /*out*/neutralGuess);
            double[] xy = calculateCIExyCoordinates(neutralGuess[0], neutralGuess[1],
                    neutralGuess[2]);
            double colorTemperature = calculateColorTemperature(xy[0], xy[1]);
            if (colorTemperature <= lower) {
                interpFactor = 1;
            } else if (colorTemperature >= upper) {
                interpFactor = 0;
            } else {
                double invCT = 1.0 / colorTemperature;
                interpFactor = (invCT - 1.0 / upper) / (1.0 / lower - 1.0 / upper);
            }
            if (lower == colorTemperature1) {
                interpFactor = 1.0 - interpFactor;
            }
            interpFactor = (interpFactor + oldInterpFactor) / 2;
            lastDiff = Math.abs(oldInterpFactor - interpFactor);
            oldInterpFactor = interpFactor;
            loopLimit--;
            count++;
            if (DEBUG) {
                Log.d(TAG, "CameraToXYZ chosen: " + Arrays.toString(interpXYZToCameraInverse));
                Log.d(TAG, "XYZ neutral color guess: " + Arrays.toString(neutralGuess));
                Log.d(TAG, "xy coordinate: " + Arrays.toString(xy));
                Log.d(TAG, "xy color temperature: " + colorTemperature);
                Log.d(TAG, "New interpolation factor: " + interpFactor);
            }
        }
        if (loopLimit == 0) {
            Log.w(TAG, "Could not converge on interpolation factor, using factor " + interpFactor +
                    " with remaining error factor of " + lastDiff);
        }
        return interpFactor;
    }

    /**
     * Calculate the transform from the raw camera sensor colorspace to CIE XYZ colorspace with a
     * D50 whitepoint.
     *
     * @param forwardTransform1     forward transform matrix corresponding to the first reference
     *                              illuminant.
     * @param forwardTransform2     forward transform matrix corresponding to the second reference
     *                              illuminant.
     * @param calibrationTransform1 calibration transform matrix corresponding to the first
     *                              reference illuminant.
     * @param calibrationTransform2 calibration transform matrix corresponding to the second
     *                              reference illuminant.
     * @param neutralColorPoint     the neutral color point used to calculate the interpolation factor.
     * @param interpolationFactor   the interpolation factor to use for the forward and
     *                              calibration transforms.
     * @param outputTransform       set to the full sensor to XYZ colorspace transform.
     */
    private static void calculateCameraToXYZD50TransformFM(float[] forwardTransform1, float[] forwardTransform2,
                                                           float[] calibrationTransform1, float[] calibrationTransform2,
                                                           Rational[/*3*/] neutralColorPoint, double interpolationFactor,
                                                           float[] outputTransform) {
        float[] cameraNeutral = new float[]{neutralColorPoint[0].floatValue(),
                neutralColorPoint[1].floatValue(), neutralColorPoint[2].floatValue()};
        if (DEBUG) Log.d(TAG, "Camera neutral: " + Arrays.toString(cameraNeutral));

        float[] interpolatedCC = new float[9];
        lerp(calibrationTransform1, calibrationTransform2, interpolationFactor,
                interpolatedCC);
        float[] inverseInterpolatedCC = new float[9];
        if (!invert(interpolatedCC, /*out*/inverseInterpolatedCC)) {
            throw new IllegalArgumentException("Cannot invert interpolated calibration transform" +
                    ", input matrices are invalid.");
        }
        if (DEBUG) Log.d(TAG, "Inverted interpolated CalibrationTransform: " +
                Arrays.toString(inverseInterpolatedCC));

        float[] referenceNeutral = new float[3];
        map(inverseInterpolatedCC, cameraNeutral, /*out*/referenceNeutral);
        if (DEBUG) Log.d(TAG, "Reference neutral: " + Arrays.toString(referenceNeutral));

        float maxNeutral = Math.max(Math.max(referenceNeutral[0], referenceNeutral[1]),
                referenceNeutral[2]);
        float[] D = new float[]{maxNeutral / referenceNeutral[0], 0, 0,
                0, maxNeutral / referenceNeutral[1], 0,
                0, 0, maxNeutral / referenceNeutral[2]};
        if (DEBUG) Log.d(TAG, "Reference Neutral Diagonal: " + Arrays.toString(D));

        float[] FM = new float[9];
        float[] intermediate2 = new float[9];
        lerp(forwardTransform1, forwardTransform2, interpolationFactor, /*out*/FM);
        if (DEBUG) Log.d(TAG, "Interpolated ForwardTransform: " + Arrays.toString(FM));

        multiply(D, inverseInterpolatedCC, /*out*/intermediate2);
        multiply(FM, intermediate2, /*out*/outputTransform);
    }

    /**
     * Map a 3d column vector using the given matrix.
     *
     * @param matrix float array containing 3x3 matrix to map vector by.
     * @param input  3 dimensional vector to map.
     * @param output 3 dimensional vector result.
     */
    private static void map(float[] matrix, float[] input, /*out*/float[] output) {
        output[0] = input[0] * matrix[0] + input[1] * matrix[1] + input[2] * matrix[2];
        output[1] = input[0] * matrix[3] + input[1] * matrix[4] + input[2] * matrix[5];
        output[2] = input[0] * matrix[6] + input[1] * matrix[7] + input[2] * matrix[8];
    }

    /**
     * Multiply two 3x3 matrices together: A * B
     *
     * @param a left matrix.
     * @param b right matrix.
     */
    private static void multiply(float[] a, float[] b, /*out*/float[] output) {
        output[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
        output[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
        output[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
        output[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
        output[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
        output[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
        output[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];
        output[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];
        output[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];
    }

    /**
     * Invert a 3x3 matrix, or return false if the matrix is singular.
     *
     * @param m      matrix to invert.
     * @param output set the output to be the inverse of m.
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private static boolean invert(float[] m, /*out*/float[] output) {
        double a00 = m[0];
        double a01 = m[1];
        double a02 = m[2];
        double a10 = m[3];
        double a11 = m[4];
        double a12 = m[5];
        double a20 = m[6];
        double a21 = m[7];
        double a22 = m[8];
        double t00 = a11 * a22 - a21 * a12;
        double t01 = a21 * a02 - a01 * a22;
        double t02 = a01 * a12 - a11 * a02;
        double t10 = a20 * a12 - a10 * a22;
        double t11 = a00 * a22 - a20 * a02;
        double t12 = a10 * a02 - a00 * a12;
        double t20 = a10 * a21 - a20 * a11;
        double t21 = a20 * a01 - a00 * a21;
        double t22 = a00 * a11 - a10 * a01;
        double det = a00 * t00 + a01 * t10 + a02 * t20;
        if (Math.abs(det) < 1e-9) {
            return false; // Inverse too close to zero, not invertible.
        }
        output[0] = (float) (t00 / det);
        output[1] = (float) (t01 / det);
        output[2] = (float) (t02 / det);
        output[3] = (float) (t10 / det);
        output[4] = (float) (t11 / det);
        output[5] = (float) (t12 / det);
        output[6] = (float) (t20 / det);
        output[7] = (float) (t21 / det);
        output[8] = (float) (t22 / det);
        return true;
    }

    /**
     * Scale each element in a matrix by the given scaling factor.
     *
     * @param factor factor to scale by.
     * @param matrix the float array containing a 3x3 matrix to scale.
     */
    private static void scale(float factor, /*inout*/float[] matrix) {
        for (int i = 0; i < 9; i++) {
            matrix[i] *= factor;
        }
    }

    /**
     * Return the max float in the array.
     *
     * @param array array of floats to search.
     * @return max float in the array.
     */
    protected static float max(float[] array) {
        float val = array[0];
        for (float f : array) {
            val = (f > val) ? f : val;
        }
        return val;
    }

    /**
     * Normalize ColorMatrix to eliminate headroom for input space scaled to [0, 1] using
     * the D50 whitepoint.  This maps the D50 whitepoint into the colorspace used by the
     * ColorMatrix, then uses the resulting whitepoint to renormalize the ColorMatrix so
     * that the channel values in the resulting whitepoint for this operation are clamped
     * to the range [0, 1].
     *
     * @param colorMatrix a 3x3 matrix containing a DNG ColorMatrix to be normalized.
     */
    private static void normalizeCM(/*inout*/float[] colorMatrix) {
        float[] tmp = new float[3];
        map(colorMatrix, D50_XYZ, /*out*/tmp);
        float maxVal = max(tmp);
        if (maxVal > 0) {
            scale(1.0f / maxVal, colorMatrix);
        }
    }

    /**
     * Normalize ForwardMatrix to ensure that sensor whitepoint [1, 1, 1] maps to D50 in CIE XYZ
     * colorspace.
     *
     * @param forwardMatrix a 3x3 matrix containing a DNG ForwardTransform to be normalized.
     */
    private static void normalizeFM(/*inout*/float[] forwardMatrix) {
        float[] tmp = new float[]{1, 1, 1};
        float[] xyz = new float[3];
        map(forwardMatrix, tmp, /*out*/xyz);
        float[] intermediate = new float[9];
        float[] m = new float[]{1.0f / xyz[0], 0, 0, 0, 1.0f / xyz[1], 0, 0, 0, 1.0f / xyz[2]};
        multiply(m, forwardMatrix, /*out*/ intermediate);
        float[] m2 = new float[]{D50_XYZ[0], 0, 0, 0, D50_XYZ[1], 0, 0, 0, D50_XYZ[2]};
        multiply(m2, intermediate, /*out*/forwardMatrix);
    }
}
