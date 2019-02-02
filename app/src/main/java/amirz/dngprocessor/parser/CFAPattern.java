package amirz.dngprocessor.parser;

import android.hardware.camera2.CameraCharacteristics;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CFAPattern {
    private static final Map<int[], Integer> PATTERNS = new HashMap<>();

    static {
        PATTERNS.put(new int[] { 0, 1, 1, 2 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB);

        PATTERNS.put(new int[] { 1, 0, 2, 1 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GRBG);

        PATTERNS.put(new int[] { 1, 2, 0, 1 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_GBRG);

        PATTERNS.put(new int[] { 2, 1, 1, 0 },
                CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR);
    }

    public static int get(int[] cfaValues) {
        for (Map.Entry<int[], Integer> kvp : PATTERNS.entrySet()) {
            if (Arrays.equals(kvp.getKey(), cfaValues)) {
                return kvp.getValue();
            }
        }
        return -1;
    }
}
