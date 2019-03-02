package amirz.dngprocessor.gl;

import android.content.Context;
import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import amirz.dngprocessor.R;

public class Shaders {
    public static String VS;

    // Mosaiced
    public static String FS_PREPROCESS;
    public static String FS_GREENDEMOSAIC;

    // Demosaicing
    public static String FS_INTERMEDIATE;

    // Demosaiced
    public static String FS_ANALYSIS;
    public static String FS_OUTPUT;

    public static void load(Context context) {
        Resources res = context.getResources();

        VS = readRaw(res, R.raw.passthrough_vs);

        FS_PREPROCESS = readRaw(res, R.raw.stage1_1_fs);
        FS_GREENDEMOSAIC = readRaw(res, R.raw.stage1_2_fs);
        FS_INTERMEDIATE = readRaw(res, R.raw.stage1_3_fs);
        FS_ANALYSIS = readRaw(res, R.raw.stage2_1_fs);
        FS_OUTPUT = readRaw(res, R.raw.stage3_1_fs);
    }

    private static String readRaw(Resources res, int resId) {
        try (InputStream inputStream = res.openRawResource(resId)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(line);
                text.append('\n');
            }

            return text.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
