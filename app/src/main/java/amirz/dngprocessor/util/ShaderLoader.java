package amirz.dngprocessor.util;

import android.content.Context;
import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import amirz.dngprocessor.R;

public class ShaderLoader {
    private static ShaderLoader sInstance;

    public static ShaderLoader getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new ShaderLoader(context.getApplicationContext().getResources());

            sInstance.mapImport("gaussian", R.raw.import_gaussian);
            sInstance.mapImport("load3x3", R.raw.import_load3x3);
            sInstance.mapImport("load3x3v2", R.raw.import_load3x3v2);
            sInstance.mapImport("load3x3v3", R.raw.import_load3x3v3);
            sInstance.mapImport("load5x5v3", R.raw.import_load5x5v3);
            sInstance.mapImport("sigmoid", R.raw.import_sigmoid);
            sInstance.mapImport("xyytoxyz", R.raw.import_xyy_to_xyz);
            sInstance.mapImport("xyztoxyy", R.raw.import_xyz_to_xyy);
        }
        return sInstance;
    }

    private final Resources mRes;
    private final Map<String, String> mImports = new HashMap<>();
    private final Map<Integer, String> mRaws = new HashMap<>();

    private ShaderLoader(Resources res) {
        mRes = res;
    }

    private void mapImport(String name, int resId) {
        mImports.put("#include " + name, readRawInternal(resId, false)
                .replace('\n', ' ')
                .replace('\r', ' '));
    }

    public String readRaw(int resId) {
        return mRaws.computeIfAbsent(resId, this::readRawInternal);
    }

    private String readRawInternal(int resId) {
        return readRawInternal(resId, true);
    }

    private String readRawInternal(int resId, boolean process) {
        try (InputStream inputStream = mRes.openRawResource(resId)) {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

            StringBuilder text = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                text.append(process ? mImports.getOrDefault(line, line) : line);
                text.append('\n');
            }

            return text.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
