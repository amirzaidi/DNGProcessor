package amirz.dngprocessor.gl;

import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ShaderLoader {
    private final Resources mRes;
    private final Map<String, String> mImports = new HashMap<>();

    public ShaderLoader(Resources res) {
        mRes = res;
    }

    public void mapImport(String name, int resId) {
        mImports.put("#include " + name, Objects.requireNonNull(readRawInternal(resId, false)));
    }

    public String readRaw(int resId) {
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
