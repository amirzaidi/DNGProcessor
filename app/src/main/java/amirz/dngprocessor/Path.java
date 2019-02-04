package amirz.dngprocessor;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

public class Path {
    public static final String EXT_RAW = ".dng";
    public static final String EXT_JPG = ".jpg";

    public static final String MIME_RAW = "image/x-adobe-dng";
    public static final String MIME_JPG = "image/jpeg";

    public static final String ROOT = Environment.getExternalStorageDirectory().toString();

    public static final String DCIM = ROOT + File.separator + "DCIM";

    public static final String CAMERA = DCIM + File.separator + "Camera";

    public static final String PROCESSED = DCIM + File.separator + "Processed";

    public static boolean isRaw(ContentResolver contentResolver, Uri uri, String file) {
        String mime = contentResolver.getType(uri);
        return MIME_RAW.equals(mime) || (MIME_JPG.equals(mime) && file.endsWith(Path.EXT_RAW));
    }

    public static String processedFile(String name) {
        return PROCESSED + File.separator + name.replace(EXT_RAW, EXT_JPG);
    }

    public static String getFileFromUri(Context context, Uri uri) {
        String result = getPathFromUri(context, uri);
        int cut = result.lastIndexOf('/');
        if (cut != -1) {
            result = result.substring(cut + 1);
        }
        return result;
    }

    public static String getPathFromUri(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        String[] column = { MediaStore.Images.Media.DATA };
        String filePath = null;

        // /document/image:NUM
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getDocumentId(uri).split(":")[1];
            try (Cursor cursor = cr.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    column,
                    MediaStore.Images.Media._ID + "=?",
                    new String[] { id }, null)) {
                int columnIndex = cursor.getColumnIndex(column[0]);
                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(columnIndex);
                }
            }
        }

        // content://media/external/images/media/NUM
        if (filePath == null) {
            try (Cursor cursor = cr.query(
                    uri, column, null, null, null)) {
                int columnIndex = cursor.getColumnIndex(column[0]);
                if (cursor.moveToFirst()) {
                    filePath = cursor.getString(columnIndex);
                }
            }
        }

        // /document/raw:PATH
        if (filePath == null) {
            String[] split = uri.getPath().split(":");
            filePath = split[split.length - 1];
        }

        return filePath;
    }
}
