package amirz.dngprocessor.device;

import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.parser.TIFFTag;

public class DeviceMap {
    public interface Device {
        boolean isModel(String model);

        void sensorCorrection(SparseArray<TIFFTag> tags, SensorParams sensor);

        void processCorrection(SparseArray<TIFFTag> tags, ProcessParams process);
    }

    private static final List<Device> sDevices = new ArrayList<>();
    static {
        sDevices.add(new OnePlus7());
        sDevices.add(new OnePlus6());
        sDevices.add(new OnePlus5());
        sDevices.add(new OnePlus3());
        sDevices.add(new OnePlus());

        sDevices.add(new MotoG6());

        sDevices.add(new Mi9());
        sDevices.add(new Redmi());

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
