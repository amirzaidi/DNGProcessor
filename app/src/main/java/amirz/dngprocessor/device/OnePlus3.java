package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

import amirz.dngprocessor.parser.TIFF;
import amirz.dngprocessor.parser.TIFFTag;

public class OnePlus3 extends Generic {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("ONEPLUS A3");
    }

    @Override
    public void neutralPointCorrection(SparseArray<TIFFTag> tags, Rational[] neutral) {
        if (lowLight(tags)) {
            // Set a more red neutral point, to blue shift the final image
            neutral[0] = new Rational(neutral[0].getNumerator() * 16, neutral[0].getDenominator() * 15);
            neutral[2] = new Rational(neutral[2].getNumerator() * 14, neutral[2].getDenominator() * 15);
        } else {
            super.neutralPointCorrection(tags, neutral);
        }
    }

    @Override
    public float stretchPerc(SparseArray<TIFFTag> tags) {
        return noLight(tags)
                ? 0f
                : super.stretchPerc(tags);
    }

    private boolean lowLight(SparseArray<TIFFTag> tags) {
        return exposureAtLeast(tags, 0.04f);
    }

    private boolean noLight(SparseArray<TIFFTag> tags) {
        return exposureAtLeast(tags, 0.1f);
    }

    private boolean exposureAtLeast(SparseArray<TIFFTag> tags, float min) {
        TIFFTag exposure = tags.get(TIFF.TAG_ExposureTime);
        return exposure != null && exposure.getRational().floatValue() >= min;
    }
}
