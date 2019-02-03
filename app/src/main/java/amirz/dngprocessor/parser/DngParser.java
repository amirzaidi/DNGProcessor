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

import amirz.dngprocessor.NotifHandler;
import amirz.dngprocessor.Path;
import amirz.dngprocessor.device.DeviceMap;
import amirz.dngprocessor.gl.RawConverter;
import amirz.dngprocessor.gl.Shaders;

public class DngParser {
    private static final String TAG = "DngParser";
    private static final int JPEG_QUALITY = 95;

    private static int STEPS = 0;
    private static final int STEP_READ = STEPS++;
    private static final int STEP_PROCESS = STEPS++;
    private static final int STEP_SAVE = STEPS++;
    private static final int STEP_META = STEPS++;

    private final Context mContext;
    private final Uri mUri;
    private final String mFile;

    public DngParser(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
        mFile = Path.getFileFromUri(mContext, mUri);
    }

    private String getSavePath() {
        File folder = new File(Path.PROCESSED);
        if (!folder.exists() && !folder.mkdir()) {
            throw new RuntimeException("Cannot create " + Path.PROCESSED);
        }
        return Path.processedFile(mFile);
    }

    public void run() {
        NotifHandler.progress(mContext, STEPS, STEP_READ);

        ByteReader.ReaderWithExif reader = ByteReader.fromUri(mContext, mUri);
        Log.e(TAG, "Starting processing of " + mFile + " (" + mUri.toString() + ") size " +
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

        int rowsPerStrip = tags.get(TIFF.TAG_RowsPerStrip).getInt();
        if (rowsPerStrip != 1)
            throw new ParseException("Can only parse RowsPerStrip = 1");

        // Continue image parsing.
        int inputHeight = tags.get(TIFF.TAG_ImageLength).getInt();
        int inputWidth = tags.get(TIFF.TAG_ImageWidth).getInt();

        int[] stripOffsets = tags.get(TIFF.TAG_StripOffsets).getIntArray();
        int[] stripByteCounts = tags.get(TIFF.TAG_StripByteCounts).getIntArray();

        int startIndex = stripOffsets[0];
        int inputStride = stripByteCounts[0];

        byte[] rawImageInput = new byte[inputStride * inputHeight];
        ((ByteBuffer) wrap.position(startIndex)).get(rawImageInput);

        int cfa = CFAPattern.get(tags.get(TIFF.TAG_CFAPattern).getIntArray());
        String model = tags.get(TIFF.TAG_Model).toString();

        int[] blackLevelPattern = tags.get(TIFF.TAG_BlackLevel).getIntArray();
        int whiteLevel = tags.get(TIFF.TAG_WhiteLevel).getInt();
        int ref1 = tags.get(TIFF.TAG_CalibrationIlluminant1).getInt();
        int ref2 = tags.get(TIFF.TAG_CalibrationIlluminant2).getInt();
        float[] calib1 = tags.get(TIFF.TAG_CameraCalibration1).getFloatArray();
        float[] calib2 = tags.get(TIFF.TAG_CameraCalibration2).getFloatArray();
        float[] color1 = tags.get(TIFF.TAG_ColorMatrix1).getFloatArray();
        float[] color2 = tags.get(TIFF.TAG_ColorMatrix2).getFloatArray();
        float[] forward1 = tags.get(TIFF.TAG_ForwardMatrix1).getFloatArray();
        float[] forward2 = tags.get(TIFF.TAG_ForwardMatrix2).getFloatArray();
        Rational[] neutral = tags.get(TIFF.TAG_AsShotNeutral).getRationalArray();
        //LensShadingMap shadingMap = dynamicMetadata.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP);

        int[] defaultCropOrigin = tags.get(TIFF.TAG_DefaultCropOrigin).getIntArray();
        int[] defaultCropSize = tags.get(TIFF.TAG_DefaultCropSize).getIntArray();
        Bitmap argbOutput = Bitmap.createBitmap(defaultCropSize[0], defaultCropSize[1], Bitmap.Config.ARGB_8888);

        DeviceMap.Device device = DeviceMap.get(model);
        device.neutralPointCorrection(tags, neutral);

        float sharpenFactor = device.sharpenFactor(tags);
        float histoFactor = device.histFactor(tags);
        float[] postProcCurve = device.postProcCurve(tags);

        NotifHandler.progress(mContext, STEPS, STEP_PROCESS);

        Shaders.load(mContext);
        RawConverter.convertToSRGB(inputWidth, inputHeight, inputStride, cfa, blackLevelPattern, whiteLevel,
                rawImageInput, ref1, ref2, calib1, calib2, color1, color2,
                forward1, forward2, neutral, /* shadingMap */ null,
                defaultCropOrigin[0], defaultCropOrigin[1], postProcCurve,
                sharpenFactor, histoFactor, argbOutput);

        NotifHandler.progress(mContext, STEPS, STEP_SAVE);

        String savePath = getSavePath();
        try (FileOutputStream out = new FileOutputStream(savePath)) {
            argbOutput.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        } catch (Exception e) {
            e.printStackTrace();
        }

        NotifHandler.progress(mContext, 3, 2);
        argbOutput.recycle();

        NotifHandler.progress(mContext, STEPS, STEP_META);

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

            /*
            Broken on OP3(T)
            newExif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                     String.valueOf(tags.get(TIFF.TAG_ISOSpeedRatings).getInt()));
            */

            newExif.saveAttributes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        Intent mediaScannerIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScannerIntent.setData(Uri.fromFile(new File(savePath)));
        mContext.sendBroadcast(mediaScannerIntent);

        NotifHandler.progress(mContext, STEPS, STEPS);
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
            short tag = wrap.getShort();
            short type = wrap.getShort();
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
                if (type == TIFF.TYPE_Byte) {
                    values[elementNum] = valueWrap.get() & 0xFF;
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

            tags.append(tag & 0xFFFF, new TIFFTag(type, values));
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
