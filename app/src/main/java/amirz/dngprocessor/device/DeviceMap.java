package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.parser.TIFFTag;

public class DeviceMap {
    public interface Device {
        boolean isModel(String model);

        void neutralPointCorrection(SparseArray<TIFFTag> tags, Rational[] neutral);

        // 0 is the default, higher means more histogram equalization
        float stretchPerc(SparseArray<TIFFTag> tags);

        // 0 is the default, higher means more value sharpening.
        float sharpenFactor(SparseArray<TIFFTag> tags);
    }

    private static final List<Device> sDevices = new ArrayList<>();
    static {
        sDevices.add(new OnePlus3());
        sDevices.add(new Generic());
    }

    public static Device get(String model) {
        for (Device device : sDevices) {
            if (device.isModel(model)) {
                return device;
            }
        }
        throw new RuntimeException("No device found");
    }
}
