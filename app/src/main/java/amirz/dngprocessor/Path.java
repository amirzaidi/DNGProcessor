package amirz.dngprocessor;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.OpenableColumns;

import java.io.File;

public class Path {
    public static final String EXT_RAW = ".dng";

    public static final String ROOT = Environment.getExternalStorageDirectory().toString();

    public static final String DCIM = ROOT + File.separator + "DCIM";

    public static final String CAMERA = DCIM + File.separator + "Camera";

    public static final String PROCESSED = DCIM + File.separator + "Processed";

    public static String processedFile(String name) {
        return PROCESSED + File.separator + name;
    }

    public static String getFileFromUri(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }
}
