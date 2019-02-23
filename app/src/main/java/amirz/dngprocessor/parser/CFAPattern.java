package amirz.dngprocessor.parser;

import android.hardware.camera2.CameraCharacteristics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CFAPattern {
    private static final Map<byte[], Integer> PATTERNS = new HashMap<>();

    static {
        PATTERNS.put(new byte[] { 0, 1, 1, 2 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB);

        PATTERNS.put(new byte[] { 1, 0, 2, 1 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG);

        PATTERNS.put(new byte[] { 1, 2, 0, 1 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG);

        PATTERNS.put(new byte[] { 2, 1, 1, 0 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR);
    }

    public static int get(byte[] cfaValues) {
        for (Map.Entry<byte[], Integer> kvp : PATTERNS.entrySet()) {
            if (Arrays.equals(kvp.getKey(), cfaValues)) {
                return kvp.getValue();
            }
        }
        return -1;
    }
}
