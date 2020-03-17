package amirz.dngprocessor.parser;

import android.util.Rational;

public class TIFFTag {
    private final int mType;
    private final Object[] mValues;

    public TIFFTag(int type, Object[] value) {
        mType = type;
        mValues = value;
    }

    private TIFFTag() {
        mType = 0;
        mValues = null;
    }

    protected Object[] getValues() {
        return mValues;
    }

    public int getInt() {
        return (int) getValues()[0];
    }

    public float getFloat() {
        return getRational().floatValue();
    }

    public Rational getRational() {
        return (Rational) getValues()[0];
    }

    public byte[] getByteArray() {
        Object[] values = getValues();
        byte[] ints = new byte[values.length];
        for (int i = 0; i < ints.length; i++) {
            if (mType == TIFF.TYPE_Byte || mType == TIFF.TYPE_Undef) {
                ints[i] = (byte) values[i];
            }
        }
        return ints;
    }

    public int[] getIntArray() {
        Object[] values = getValues();
        int[] ints = new int[values.length];
        for (int i = 0; i < ints.length; i++) {
            if (mType == TIFF.TYPE_Byte || mType == TIFF.TYPE_Undef) {
                ints[i] = (byte) values[i] & 0xFF;
            } else if (mType == TIFF.TYPE_UInt_16 || mType == TIFF.TYPE_UInt_32) {
                ints[i] = (int) values[i];
            } else if (mType == TIFF.TYPE_Frac || mType == TIFF.TYPE_UFrac) {
                ints[i] = (int)((Rational) values[i]).floatValue();
            }
        }
        return ints;
    }

    public float[] getFloatArray() {
        Object[] values = getValues();
        float[] floats = new float[values.length];
        for (int i = 0; i < floats.length; i++) {
            if (mType == TIFF.TYPE_Frac || mType == TIFF.TYPE_UFrac) {
                floats[i] = ((Rational) values[i]).floatValue();
            } else if (mType == TIFF.TYPE_Double) {
                floats[i] = ((Double) values[i]).floatValue();
            }
        }
        return floats;
    }

    public Rational[] getRationalArray() {
        Object[] values = getValues();
        Rational[] rationals = new Rational[values.length];
        for (int i = 0; i < rationals.length; i++) {
            if (mType == TIFF.TYPE_Frac || mType == TIFF.TYPE_UFrac) {
                rationals[i] = (Rational) values[i];
            }
        }
        return rationals;
    }

    @Override
    public String toString() {
        Object[] values = getValues();
        StringBuilder buffer = new StringBuilder();
        if (mType == TIFF.TYPE_String) {
            for (Object b : values) {
                buffer.append((char) b);
            }
        } else {
            for (int elementNum = 0; elementNum < values.length && elementNum < 20; elementNum++) {
                Object element = values[elementNum];
                if (element != null) {
                    buffer.append(element.toString()).append(" ");
                }
            }
        }
        return buffer.toString();
    }

    static TIFFTag exceptionWrapper(int id) {
        return new TIFFTag() {
            @Override
            protected Object[] getValues() {
                throw new TIFFTagException("TIFF tag " + id + " not found");
            }
        };
    }

    public static class TIFFTagException extends RuntimeException {
        private TIFFTagException(String s) {
            super(s);
        }
    }
}
