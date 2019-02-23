package amirz.dngprocessor.parser;

import android.util.Log;

import java.nio.ByteBuffer;

public class OpParser {
    private static final String TAG = "OpParser";

    public static class GainMap {
        public int top;
        public int left;
        public int bottom;
        public int right;
        public int plane;
        public int planes;
        public int rowPitch;
        public int colPitch;
        public int mapPointsV;
        public int mapPointsH;
        public double mapSpacingV;
        public double mapSpacingH;
        public double mapOriginV;
        public double mapOriginH;
        public int mapPlanes;
        public float[] px;
    }

    private static GainMap parseGainMap(ByteBuffer reader) {
        GainMap map = new GainMap();
        map.top = reader.getInt();
        map.left = reader.getInt();
        map.bottom = reader.getInt();
        map.right = reader.getInt();
        map.plane = reader.getInt();
        map.planes = reader.getInt();
        map.rowPitch = reader.getInt();
        map.colPitch = reader.getInt();
        map.mapPointsV = reader.getInt();
        map.mapPointsH = reader.getInt();
        map.mapSpacingV = reader.getDouble();
        map.mapSpacingH = reader.getDouble();
        map.mapOriginV = reader.getDouble();
        map.mapOriginH = reader.getDouble();
        map.mapPlanes = reader.getInt();
        map.px = new float[map.mapPointsH * map.mapPointsV];

        if (map.mapPlanes != 1) {
            throw new IllegalArgumentException("GainMap.mapPlanes can only be 1");
        }

        for (int x = 0; x < map.mapPointsH; x++) {
            for (int y = 0; y < map.mapPointsV; y++) {
                map.px[x * map.mapPointsV + y] = reader.getFloat();
            }
        }

        return map;
    }

    public static Object[] parseAll(byte[] input) {
        ByteBuffer reader = ByteReader.wrapBigEndian(input);

        Object[] ops = new Object[reader.getInt()];
        for (int i = 0; i < ops.length; i++) {
            int id = reader.getInt();
            byte[] ver = { reader.get(), reader.get(), reader.get(), reader.get() };
            int flags = reader.getInt();
            int size = reader.getInt();
            Log.d(TAG, "OpCode " + id + " with size " + size);

            if (id == 9) {
                ops[i] = parseGainMap(reader);
            } else {
                reader.position(reader.position() + size);
            }
        }
        return ops;
    }
}
