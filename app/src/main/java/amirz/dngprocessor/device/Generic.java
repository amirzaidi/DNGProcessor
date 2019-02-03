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
}
