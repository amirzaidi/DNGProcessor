package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

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
        return 0.4f;
    }
}
