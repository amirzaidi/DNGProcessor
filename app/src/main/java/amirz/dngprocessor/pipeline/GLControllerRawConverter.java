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
package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;
import android.util.Log;

import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.colorspace.ColorspaceConverter;

import static amirz.dngprocessor.colorspace.ColorspaceConstants.*;

/**
 * Utility class providing methods for rendering RAW16 images into other colorspaces.
 */
public class GLControllerRawConverter extends ColorspaceConverter implements AutoCloseable {
    private static final String TAG = "GLController";

    private int mOutWidth;
    private int mOutHeight;
    private SensorParams mSensorParams;
    private ProcessParams mProcessParams;
    private GLCoreBlockProcessing mGlCore;
    private GLProgramRawConverter mGlSquare;

    /**
     * Convert a RAW16 buffer into an sRGB buffer, and write the result into a bitmap.
     */
    public GLControllerRawConverter(SensorParams sensor, ProcessParams process,
                                    byte[] rawImageInput, Bitmap argbOutput, ShaderLoader loader) {
        super(sensor);

        mSensorParams = sensor;
        mProcessParams = process;

        // Validate arguments
        if (argbOutput == null || rawImageInput == null) {
            throw new IllegalArgumentException("Null argument to convertToSRGB");
        }
        if (argbOutput.getConfig() != Bitmap.Config.ARGB_8888) {
            throw new IllegalArgumentException(
                    "Output bitmap passed to convertToSRGB is not ARGB_8888 format");
        }
        mOutWidth = argbOutput.getWidth();
        mOutHeight = argbOutput.getHeight();
        if (mOutWidth + sensor.outputOffsetX > sensor.inputWidth || mOutHeight + sensor.outputOffsetY > sensor.inputHeight) {
            throw new IllegalArgumentException("Raw image with dimensions (w=" + sensor.inputWidth +
                    ", h=" + sensor.inputHeight + "), cannot converted into sRGB image with dimensions (w="
                    + mOutWidth + ", h=" + mOutHeight + ").");
        }
        if (DEBUG) {
            Log.d(TAG, "Output width,height: " + mOutWidth + "," + mOutHeight);
        }

        // Write the variables first
        mGlCore = new GLCoreBlockProcessing(argbOutput, loader);
        mGlSquare = (GLProgramRawConverter) mGlCore.getProgram();
    }

    public int getOutWidth() {
        return mOutWidth;
    }

    public int getOutHeight() {
        return mOutHeight;
    }

    public GLProgramRawConverter getProgram() {
        return mGlSquare;
    }

    public GLCoreBlockProcessing getCore() {
        return mGlCore;
    }

    @Override
    public void close() {
        mGlCore.close();
    }
}