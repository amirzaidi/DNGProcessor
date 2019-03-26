package amirz.dngprocessor.device;

public class Redmi extends Xiaomi {
    @Override
    public boolean isModel(String model) {
        return model.startsWith("Redmi");
    }
}
