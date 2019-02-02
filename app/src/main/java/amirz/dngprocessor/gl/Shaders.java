package amirz.dngprocessor.gl;

import android.content.Context;
import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import amirz.dngprocessor.R;

public class Shaders {
    public static String VS1;
    public static String PS1;

    public static String VS2;
    public static String PS2;

    public static void load(Context context) {
        Resources res = context.getResources();

        VS1 = readRaw(res, R.raw.stage1_vs);
        PS1 = readRaw(res, R.raw.stage1_fs);

        VS2 = readRaw(res, R.raw.stage2_vs);
        PS2 = readRaw(res, R.raw.stage2_fs);
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
