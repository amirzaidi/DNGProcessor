package amirz.dngprocessor.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.File;

public class Path {
    private static final String TAG = "Path";

    public static final String EXT_RAW = ".dng";
    public static final String EXT_JPG = ".jpg";

    public static final String MIME_RAW = "image/x-adobe-dng";
    public static final String MIME_JPG = "image/jpeg";

    public static final String ROOT = Environment.getExternalStorageDirectory().toString();

    public static boolean isRaw(ContentResolver contentResolver, Uri uri, String file) {
        String mime = contentResolver.getType(uri);
        return MIME_RAW.equals(mime) || (MIME_JPG.equals(mime) && file.endsWith(Path.EXT_RAW));
    }

    public static String processedPath(String dir, String name) {
        dir = ROOT + File.separator + dir;
        File folder = new File(dir);
        if (!folder.exists() && !folder.mkdir()) {
            throw new RuntimeException("Cannot create " + dir);
        }
        return dir + File.separator + name.replace(EXT_RAW, EXT_JPG);
    }

    public static String getFileFromUri(Context context, Uri uri) {
        String fileName = getColumn(context, uri, OpenableColumns.DISPLAY_NAME);

        /* document/raw:PATH */
        if (fileName == null) {
            String result = getPathFromUri(context, uri);
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                fileName = result.substring(cut + 1);
            }
        }

        Log.d(TAG, "Resolved " + uri.toString() + " to name " + fileName);
        return fileName;
    }

    public static String getPathFromUri(Context context, Uri uri) {
        String filePath = getColumn(context, uri, MediaStore.Images.Media.DATA);

        /* document/raw:PATH */
        if (filePath == null) {
            filePath = uri.getPath();
            if (filePath.contains(":")) {
                String[] split = filePath.split(":");
                filePath = split[split.length - 1];
            }
        }

        Log.d(TAG, "Resolved " + uri.toString() + " to path " + filePath);
        return filePath;
    }

    private static String getColumn(Context context, Uri uri, String column) {
        ContentResolver cr = context.getContentResolver();
        String result = null;
        if (DocumentsContract.isDocumentUri(context, uri)) {
            String id = DocumentsContract.getDocumentId(uri);
            if (id.contains(":")) {
                id = id.split(":")[1];
            }

            /* document/image:NUM */
            result = query(cr,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    column,
                    MediaStore.Images.Media._ID + "=?",
                    new String[] { id });

            /* document/NUM */
            if (result == null) {
                try {
                    long l = Long.valueOf(id);
                    result = query(cr, ContentUris.withAppendedId(
                            Uri.parse("content://downloads/public_downloads"), l),
                            column);
                } catch (Exception ignored) {
                }
            }
        }

        /* media/external/images/media/NUM */
        if (result == null) {
            result = query(cr, uri, column);
        }

        return result;
    }

    private static String query(ContentResolver cr, Uri uri, String column) {
        return query(cr, uri, column, null, null);
    }

    private static String query(ContentResolver cr, Uri uri, String column, String selection, String[] selectionArgs) {
        try (Cursor cursor = cr.query(
                uri, new String[] { column }, selection, selectionArgs, null)) {
            if (cursor != null) {
                int columnIndex = cursor.getColumnIndex(column);
                if (cursor.moveToFirst()) {
                    return cursor.getString(columnIndex);
                }
            }
        }
        return null;
    }
}
