package amirz.dngprocessor.parser;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.support.media.ExifInterface;
import android.net.Uri;
import android.util.Log;
import android.util.Rational;
import android.util.SparseArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.util.NotifHandler;
import amirz.dngprocessor.util.Path;
import amirz.dngprocessor.Preferences;
import amirz.dngprocessor.device.DeviceMap;
import amirz.dngprocessor.gl.ShaderLoader;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;

public class DngParser {
    private static final String TAG = "DngParser";
    private static final int JPEG_QUALITY = 95;

    private static int ADD_STEPS = 0;
    private static int STEP_SAVE = ADD_STEPS++;
    private static int STEP_META = ADD_STEPS++;

    private final Context mContext;
    private final Uri mUri;
    private final String mFile;

    public DngParser(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
        mFile = Path.getFileFromUri(mContext, mUri);
    }

    public void run() {
        NotifHandler.progress(mContext, 1, 0);

        Preferences pref = Preferences.global();

        ByteReader.ReaderWithExif reader = ByteReader.fromUri(mContext, mUri);
        Log.e(TAG, "Starting processing of " + mFile + " (" + mUri.getPath() + ") size " +
                reader.length);

        ByteBuffer wrap = reader.wrap;

        byte[] format = { wrap.get(), wrap.get() };
        if (!new String(format).equals("II"))
            throw new ParseException("Can only parse Intel byte order");

        short version = wrap.getShort();
        if (version != 42)
            throw new ParseException("Can only parse v42");

        int start = wrap.getInt();
        wrap.position(start);

        SparseArray<TIFFTag> tags = parseTags(wrap);

        TIFFTag subIFD = tags.get(TIFF.TAG_SubIFDs);
        TIFFTag type = tags.get(TIFF.TAG_NewSubfileType);
        if (subIFD != null && type != null && type.getInt() == 1) {
            wrap.position(subIFD.getInt());
            SparseArray<TIFFTag> subTags = parseTags(wrap);
            for (int i = 0; i < subTags.size(); i++) {
                tags.put(subTags.keyAt(i), subTags.valueAt(i));
            }
        }

        //int rowsPerStrip = tags.get(TIFF.TAG_RowsPerStrip).getInt();
        //if (rowsPerStrip != 1)
        //    throw new ParseException("Can only parse RowsPerStrip = 1");

        SensorParams sensor = new SensorParams();

        // Continue image parsing.
        sensor.inputHeight = tags.get(TIFF.TAG_ImageLength).getInt();
        sensor.inputWidth = tags.get(TIFF.TAG_ImageWidth).getInt();

        int[] stripOffsets = tags.get(TIFF.TAG_StripOffsets).getIntArray();
        int[] stripByteCounts = tags.get(TIFF.TAG_StripByteCounts).getIntArray();

        if (stripOffsets.length != stripByteCounts.length) {
            throw new RuntimeException("StripOffsets was not equal to StripByteCounts");
        }

        sensor.inputStride = stripByteCounts[0];

        byte[] rawImageInput = new byte[sensor.inputWidth * sensor.inputHeight * 2];
        int rawImageOffset = 0;
        for (int i = 0; i < stripOffsets.length; i++) {
            ((ByteBuffer) wrap.position(stripOffsets[i]))
                    .get(rawImageInput, rawImageOffset, stripByteCounts[i]);
            rawImageOffset += stripByteCounts[i];
        }

        sensor.cfaVal = tags.get(TIFF.TAG_CFAPattern).getByteArray();
        sensor.cfa = CFAPattern.get(sensor.cfaVal);
        sensor.blackLevelPattern = tags.get(TIFF.TAG_BlackLevel).getIntArray();
        sensor.whiteLevel = tags.get(TIFF.TAG_WhiteLevel).getInt();
        sensor.referenceIlluminant1 = tags.get(TIFF.TAG_CalibrationIlluminant1).getInt();
        sensor.referenceIlluminant2 = tags.get(TIFF.TAG_CalibrationIlluminant2).getInt();
        sensor.calibrationTransform1 = tags.get(TIFF.TAG_CameraCalibration1).getFloatArray();
        sensor.calibrationTransform2 = tags.get(TIFF.TAG_CameraCalibration2).getFloatArray();
        sensor.colorMatrix1 = tags.get(TIFF.TAG_ColorMatrix1).getFloatArray();
        sensor.colorMatrix2 = tags.get(TIFF.TAG_ColorMatrix2).getFloatArray();
        TIFFTag fm1 = tags.get(TIFF.TAG_ForwardMatrix1);
        TIFFTag fm2 = tags.get(TIFF.TAG_ForwardMatrix2);
        if (fm1 != null && fm2 != null) {
            sensor.forwardTransform1 = fm1.getFloatArray();
            sensor.forwardTransform2 = fm2.getFloatArray();
        }
        sensor.neutralColorPoint = tags.get(TIFF.TAG_AsShotNeutral).getRationalArray();

        TIFFTag noiseProfile = tags.get(TIFF.TAG_NoiseProfile);
        if (noiseProfile == null) {
            sensor.noiseProfile = new float[6];
        } else {
            sensor.noiseProfile = noiseProfile.getFloatArray();
        }

        int[] defaultCropOrigin = tags.get(TIFF.TAG_DefaultCropOrigin).getIntArray();
        sensor.outputOffsetX = defaultCropOrigin[0];
        sensor.outputOffsetY = defaultCropOrigin[1];

        int[] defaultCropSize = tags.get(TIFF.TAG_DefaultCropSize).getIntArray();
        Bitmap argbOutput = Bitmap.createBitmap(defaultCropSize[0], defaultCropSize[1], Bitmap.Config.ARGB_8888);

        TIFFTag Op2 = tags.get(TIFF.TAG_OpcodeList2);
        if (Op2 != null) {
            Object[] opParsed = OpParser.parseAll(Op2.getByteArray());
            OpParser.GainMap[] mapPlanes = new OpParser.GainMap[4];

            for (Object o : opParsed) {
                Log.e(TAG, "Parsed opcode: " + o.toString());
                if (o instanceof OpParser.GainMap) {
                    OpParser.GainMap mapPlane = (OpParser.GainMap) o;
                    mapPlanes[(mapPlane.top << 1) | mapPlane.left] = mapPlane;
                }
            }

            if (mapPlanes[0] != null && mapPlanes[1] != null
                    && mapPlanes[2] != null && mapPlanes[3] != null) {
                sensor.gainMapSize = new int[] { mapPlanes[0].mapPointsH, mapPlanes[0].mapPointsV };
                sensor.gainMap = new float[sensor.gainMapSize[0] * sensor.gainMapSize[1] * 4];
                for (int i = 0; i < sensor.gainMap.length; i++) {
                    sensor.gainMap[i] = mapPlanes[i % 4].px[i / 4];
                }
            }
        }

        ProcessParams process = ProcessParams.getPreset(Preferences.postProcess());
        process.saturationMap = new float[] {
                pref.saturationRed.get(),
                pref.saturationYellow.get(),
                pref.saturationGreen.get(),
                pref.saturationCyan.get(),
                pref.saturationBlue.get(),
                pref.saturationIndigo.get(),
                pref.saturationViolet.get(),
                pref.saturationMagenta.get()
        };
        process.satLimit = pref.saturationLimit.get();
        process.denoiseFactor = pref.noiseReduce.get() ? 100 : 0;
        process.lce = pref.lce.get();
        process.ahe = pref.ahe.get();

        // Override sensor and process settings with model specific ones
        TIFFTag modelTag = tags.get(TIFF.TAG_Model);
        DeviceMap.Device device = DeviceMap.get(modelTag == null ? "" : modelTag.toString());
        device.sensorCorrection(tags, sensor);
        device.processCorrection(tags, process);

        if (!pref.forwardMatrix.get()) {
            sensor.forwardTransform1 = null;
            sensor.forwardTransform2 = null;
        }

        if (!pref.gainMap.get()) {
            sensor.gainMap = null;
            sensor.gainMapSize = null;
        }

        ShaderLoader loader = new ShaderLoader(mContext.getResources());
        final int[] steps = { 0 };
        try (StagePipeline pipeline = new StagePipeline(
                sensor, process, rawImageInput, argbOutput, loader)) {
            pipeline.execute((completed, total) -> {
                steps[0] = total;
                NotifHandler.progress(mContext, total + ADD_STEPS, completed);
            });
        }

        NotifHandler.progress(mContext, steps[0] + ADD_STEPS, steps[0] + STEP_SAVE);
        String savePath = Path.processedPath(pref.savePath.get(), mFile);
        try (FileOutputStream out = new FileOutputStream(savePath)) {
            argbOutput.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        } catch (Exception e) {
            e.printStackTrace();
        }
        argbOutput.recycle();

        NotifHandler.progress(mContext, steps[0] + ADD_STEPS, steps[0] + STEP_META);
        try {
            ExifInterface newExif = new ExifInterface(savePath);
            copyAttributes(reader.exif, newExif);

            if (tags.get(TIFF.TAG_FocalLength) != null) {
                newExif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH,
                        tags.get(TIFF.TAG_FocalLength).getRational().toString());
            }

            if (tags.get(TIFF.TAG_FNumber) != null) {
                newExif.setAttribute(ExifInterface.TAG_APERTURE_VALUE,
                        tags.get(TIFF.TAG_FNumber).getRational().toString());
            }

            if (tags.get(TIFF.TAG_ExposureTime) != null) {
                newExif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,
                        String.valueOf(tags.get(TIFF.TAG_ExposureTime).getFloat()));
            }

            if (tags.get(TIFF.TAG_ISOSpeedRatings) != null) {
                newExif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                        String.valueOf(tags.get(TIFF.TAG_ISOSpeedRatings).getInt()));
            }

            newExif.saveAttributes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        mContext.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                Uri.fromFile(new File(savePath))));

        NotifHandler.progress(mContext, steps[0] + ADD_STEPS, steps[0] + ADD_STEPS);
    }

    @SuppressWarnings("deprecation")
    private static void copyAttributes(ExifInterface oldExif, ExifInterface newExif) {
        String[] tags = {
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE,
                ExifInterface.TAG_X_RESOLUTION,
                ExifInterface.TAG_Y_RESOLUTION,
        };

        for (String tag : tags) {
            String value = oldExif.getAttribute(tag);
            if (value != null) {
                newExif.setAttribute(tag, value);
            }
        }
    }

    private static SparseArray<TIFFTag> parseTags(ByteBuffer wrap) {
        short tagCount = wrap.getShort();
        SparseArray<TIFFTag> tags = new SparseArray<>(tagCount);

        for (int tagNum = 0; tagNum < tagCount; tagNum++) {
            int tag = wrap.getShort() & 0xFFFF;
            int type = wrap.getShort() & 0xFFFF;
            int elementCount = wrap.getInt();
            int elementSize = TIFF.TYPE_SIZES.get(type);

            byte[] buffer = new byte[Math.max(4, elementCount * elementSize)];
            if (buffer.length == 4) {
                wrap.get(buffer);
            } else {
                int dataPos = wrap.getInt();
                independentMove(wrap, dataPos).get(buffer);
            }

            ByteBuffer valueWrap = ByteReader.wrap(buffer);
            Object[] values = new Object[elementCount];
            for (int elementNum = 0; elementNum < elementCount; elementNum++) {
                if (type == TIFF.TYPE_Byte || type == TIFF.TYPE_Undef) {
                    values[elementNum] = valueWrap.get();
                } else if (type == TIFF.TYPE_String) {
                    values[elementNum] = (char) valueWrap.get();
                } else if (type == TIFF.TYPE_UInt_16) {
                    values[elementNum] = valueWrap.getShort() & 0xFFFF;
                } else if (type == TIFF.TYPE_UInt_32) {
                    values[elementNum] = valueWrap.getInt();
                } else if (type == TIFF.TYPE_UFrac) {
                    values[elementNum] = new Rational(valueWrap.getInt(), valueWrap.getInt());
                } else if (type == TIFF.TYPE_Frac) {
                    values[elementNum] = new Rational(valueWrap.getInt(), valueWrap.getInt());
                } else if (type == TIFF.TYPE_Double) {
                    values[elementNum] = valueWrap.getDouble();
                }
            }

            tags.append(tag, new TIFFTag(type, values));
        }

        return tags;
    }

    private static ByteBuffer independentMove(ByteBuffer wrap, int position) {
        return (ByteBuffer) wrap.duplicate().order(ByteOrder.LITTLE_ENDIAN).position(position);
    }

    private class ParseException extends RuntimeException {
        private ParseException(String s) {
            super(s);
        }
    }
}
