package amirz.dngprocessor;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraCharacteristics;
import android.support.media.ExifInterface;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.renderscript.RenderScript;
import android.util.Log;
import android.util.Rational;
import android.util.SparseArray;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import amirz.dngprocessor.renderscript.BitmapTransformations;
import amirz.dngprocessor.renderscript.RawConverter;

public class Parser implements Runnable {
    private static final String TAG = "Parser";
    private static final String FOLDER = File.separator + "DCIM" + File.separator + "Processed";

    private final Context mContext;
    private final Uri mUri;

    public Parser(Context context, Uri uri) {
        mContext = context;
        mUri = uri;
    }

    private String getFileName() {
        String result = null;
        if (mUri.getScheme().equals("content")) {
            try (Cursor cursor = mContext.getContentResolver().query(mUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }

        if (result == null) {
            result = mUri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }

        return result;
    }

    private String getSavePath(String newExt) {
        File folder = new File(Environment.getExternalStorageDirectory() + FOLDER);
        if (!folder.exists() && !folder.mkdir()) {
            throw new RuntimeException("Cannot create " + FOLDER);
        }
        return folder.getPath() + File.separator + getFileName().replace(".dng", "." + newExt);
    }

    @Override
    public void run() {
        ByteReader.ReaderWithExif reader = ByteReader.fromUri(mContext, mUri);
        Log.e(TAG, mUri.toString() + " size " + reader.length);

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
        for (int i = 0; i < tags.size(); i++) {
            Log.w(TAG, tags.keyAt(i) + " = " + tags.valueAt(i).toString());
        }

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

        // ToDo: Support other CFA formats
        int cfa = -1;
        int[] cfaValues = tags.get(TIFF.TAG_CFAPattern).getIntArray();
        int[] cfaValuesRGGB = { 0, 1, 1, 2 };
        if (Arrays.equals(cfaValues, cfaValuesRGGB)) {
            cfa = CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB;
        }

        //int[] blackLevelPattern = tags.get(TIFF.TAG_BlackLevel).getIntArray();
        int[] blackLevelPattern = { 0, 0, 0, 0 };
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

        RenderScript rs = RenderScript.create(mContext);

        int[] defaultCropOrigin = tags.get(TIFF.TAG_DefaultCropOrigin).getIntArray();
        int[] defaultCropSize = tags.get(TIFF.TAG_DefaultCropSize).getIntArray();
        Bitmap argbOutput = Bitmap.createBitmap(defaultCropSize[0], defaultCropSize[1], Bitmap.Config.ARGB_8888);

        Log.e(TAG, "Converting..");

        float tonemapStrength = 0.625f;
        float[] tonemap = new float[] {
                -2f + 2f * tonemapStrength,
                3f - 3f * tonemapStrength,
                tonemapStrength,
                0f
        };
        float saturationFactor = 2.05f;
        float sharpenFactor = 0.33f;

        RawConverter.convertToSRGB(rs, inputWidth, inputHeight, inputStride, cfa, blackLevelPattern, whiteLevel,
                rawImageInput, ref1, ref2, calib1, calib2, color1, color2,
                forward1, forward2, neutral, /* shadingMap */ null,
                defaultCropOrigin[0], defaultCropOrigin[1], tonemap, saturationFactor, sharpenFactor, argbOutput);

        rs.destroy();

        Log.e(TAG, "Saving..");

        String savePath = getSavePath("jpg");
        try (FileOutputStream out = new FileOutputStream(savePath)) {
            argbOutput.compress(Bitmap.CompressFormat.JPEG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        }

        argbOutput.recycle();

        try {
            ExifInterface newExif = new ExifInterface(savePath);
            copyAttributes(reader.exif, newExif);

            newExif.setAttribute(ExifInterface.TAG_FOCAL_LENGTH,
                    tags.get(TIFF.TAG_FocalLength).getRational().toString());

            newExif.setAttribute(ExifInterface.TAG_APERTURE_VALUE,
                    tags.get(TIFF.TAG_FNumber).getRational().toString());

            newExif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME,
                    String.valueOf(tags.get(TIFF.TAG_ExposureTime).getFloat()));

            newExif.setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
                    String.valueOf(tags.get(TIFF.TAG_ISOSpeedRatings).getInt()));

            newExif.saveAttributes();
        } catch (Exception e) {
            e.printStackTrace();
        }

        MediaScannerConnection.scanFile(mContext,
                new String[] { savePath }, null, null);

        Log.e(TAG, "Done");
        Toast.makeText(mContext, "Saved processed image", Toast.LENGTH_SHORT).show();
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
