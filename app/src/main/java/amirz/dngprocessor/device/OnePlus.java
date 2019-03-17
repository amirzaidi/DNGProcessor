package amirz.dngprocessor.device;

import android.util.SparseArray;

import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.TIFFTag;

class OnePlus extends Generic {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("ONEPLUS");
    }

    @Override
    public void sensorCorrection(SparseArray<TIFFTag> tags, SensorParams sensor) {
        super.sensorCorrection(tags, sensor);
    }
}
