package amirz.dngprocessor.gl.generic;

import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class ShaderLoader {
    private final Resources mRes;

    public ShaderLoader(Resources res) {
        mRes = res;
    }

    public String readRaw(int resId) {
        try (InputStream inputStream = mRes.openRawResource(resId)) {
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
