package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

import amirz.dngprocessor.parser.TIFF;
import amirz.dngprocessor.parser.TIFFTag;

public class OnePlus3 implements DeviceMap.Device {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("ONEPLUS A3");
    }

    @Override
    public void neutralPointCorrection(SparseArray<TIFFTag> tags, Rational[] neutral) {
        TIFFTag iso = tags.get(TIFF.TAG_ISOSpeedRatings);
        if (iso != null && iso.getInt() > 400) {
            // Set a more red neutral point, to blue shift the final image
            neutral[0] = new Rational(neutral[0].getNumerator() * 12, neutral[0].getDenominator() * 11);
            neutral[2] = new Rational(neutral[2].getNumerator() * 10, neutral[2].getDenominator() * 11);
        }
    }
}
