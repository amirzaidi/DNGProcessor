package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

import amirz.dngprocessor.parser.TIFF;
import amirz.dngprocessor.parser.TIFFTag;

public class Generic implements DeviceMap.Device {
    @Override
    public boolean isModel(String model) {
        return true;
    }

    @Override
    public void neutralPointCorrection(SparseArray<TIFFTag> tags, Rational[] neutral) {
    }

    @Override
    public float sharpenFactor(SparseArray<TIFFTag> tags) {
        return lowLight(tags)
                ? 0.05f
                : 0.25f;
    }

    @Override
    public float[] stretchPerc(SparseArray<TIFFTag> tags) {
        return new float[] { 0.01f, 0.995f };
    }

    private boolean lowLight(SparseArray<TIFFTag> tags) {
        // Note: OnePlus only reports ISOs up to 799
        TIFFTag iso = tags.get(TIFF.TAG_ISOSpeedRatings);
        return iso != null && iso.getInt() > 400;
    }
}
