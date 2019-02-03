package amirz.dngprocessor.device;

import android.util.Rational;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import amirz.dngprocessor.parser.TIFFTag;

public class DeviceMap {
    public interface Device {
        boolean isModel(String model);

        void neutralPointCorrection(SparseArray<TIFFTag> tags, Rational[] neutral);
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
