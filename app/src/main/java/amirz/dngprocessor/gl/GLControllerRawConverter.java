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
package amirz.dngprocessor.gl;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.Arrays;

import amirz.dngprocessor.gl.generic.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.RawConverter;

import static amirz.dngprocessor.math.ColorspaceConstants.*;

/**
 * Utility class providing methods for rendering RAW16 images into other colorspaces.
 */
public class GLControllerRawConverter extends RawConverter implements AutoCloseable {
    private static final String TAG = "GLController";

    private int outWidth;
    private int outHeight;
    private SensorParams sensor;
    private ProcessParams process;
    private float[] XYZtoProPhoto;
    private float[] proPhotoToSRGB;
    private GLCoreBlockProcessing core;
    private GLProgramRawConverter square;

    /**
     * Convert a RAW16 buffer into an sRGB buffer, and write the result into a bitmap.
     */
    public GLControllerRawConverter(SensorParams sensor, ProcessParams process,
                                    byte[] rawImageInput, Bitmap argbOutput, ShaderLoader loader) {
        this.sensor = sensor;
        this.process = process;

        // Validate arguments
        if (argbOutput == null || rawImageInput == null) {
            throw new IllegalArgumentException("Null argument to convertToSRGB");
        }
        if (argbOutput.getConfig() != Bitmap.Config.ARGB_8888) {
            throw new IllegalArgumentException(
                    "Output bitmap passed to convertToSRGB is not ARGB_8888 format");
        }

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

        outWidth = argbOutput.getWidth();
        outHeight = argbOutput.getHeight();
        if (outWidth + sensor.outputOffsetX > sensor.inputWidth || outHeight + sensor.outputOffsetY > sensor.inputHeight) {
            throw new IllegalArgumentException("Raw image with dimensions (w=" + sensor.inputWidth +
                    ", h=" + sensor.inputHeight + "), cannot converted into sRGB image with dimensions (w="
                    + outWidth + ", h=" + outHeight + ").");
        }
        if (sensor.cfa < 0 || sensor.cfa > 3) {
            throw new IllegalArgumentException("Unsupported cfa pattern " + sensor.cfa + " used.");
        }

        if (DEBUG) {
            Log.d(TAG, "Metadata Used:");
            Log.d(TAG, "Input width,height: " + sensor.inputWidth + "," + sensor.inputHeight);
            Log.d(TAG, "Output offset x,y: " + sensor.outputOffsetX + "," + sensor.outputOffsetY);
            Log.d(TAG, "Output width,height: " + outWidth + "," + outHeight);
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

        float[] sensorToXYZ_D50 = new float[9];
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

        XYZtoProPhoto = new float[9];
        System.arraycopy(sXYZtoProPhoto, 0, XYZtoProPhoto, 0, sXYZtoProPhoto.length);
        if (DEBUG) Log.d(TAG, "XYZtoProPhoto xform used: " + Arrays.toString(XYZtoProPhoto));

        proPhotoToSRGB = new float[9];
        multiply(sXYZtoSRGB, sProPhotoToXYZ, /*out*/proPhotoToSRGB);
        if (DEBUG) Log.d(TAG, "proPhotoToSRGB xform used: " + Arrays.toString(proPhotoToSRGB));

        // Write the variables first
        core = new GLCoreBlockProcessing(argbOutput, loader);
        square = (GLProgramRawConverter) core.getProgram();

        square.setIn(rawImageInput, sensor.inputWidth, sensor.inputHeight, sensor.cfa);
        square.setGainMap(sensor.gainMap, sensor.gainMapSize);
        square.setBlackWhiteLevel(sensor.blackLevelPattern, sensor.whiteLevel);
        square.sensorPreProcess(sensor.hotPixels, sensor.hotPixelsSize);
        square.greenDemosaic();

        square.prepareToIntermediate();
        square.setNeutralPoint(sensor.neutralColorPoint, sensor.cfaVal);
        square.setTransforms1(sensorToXYZ_D50);
    }

    public void sensorToIntermediate() {
        square.sensorToIntermediate();
    }

    public void analyzeIntermediate() {
        square.setOutOffset(sensor.outputOffsetX, sensor.outputOffsetY);
        square.analyzeIntermediate(outWidth, outHeight, 32);
    }

    public void blurIntermediate() {
        square.blurIntermediate(process.lce, process.ahe);
    }

    public void intermediateToOutput() {
        square.prepareForOutput(process.histFactor, process.satLimit);
        square.setf("noiseProfile", sensor.noiseProfile[2], sensor.noiseProfile[3]);
        square.setLCE(process.lce);
        square.setAHE(process.ahe);
        square.setToneMapCoeffs(CUSTOM_ACR3_TONEMAP_CURVE_COEFFS);
        square.setTransforms2(XYZtoProPhoto, proPhotoToSRGB);
        square.setDenoiseFactor(process.denoiseFactor);
        square.setSharpenFactor(process.sharpenFactor);
        square.setSaturation(process.saturationMap);
        square.setOutOffset(sensor.outputOffsetX, sensor.outputOffsetY);

        core.intermediateToOutput();
    }

    @Override
    public void close() {
        core.close();
    }
}