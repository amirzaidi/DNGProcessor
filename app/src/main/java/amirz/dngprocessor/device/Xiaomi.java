package amirz.dngprocessor.device;

public class Xiaomi extends Generic {
    @Override
    void saturationCorrection(float[] saturationMap) {
        float genericMult = 1.3f;
        saturationMap[0] *= genericMult;
        saturationMap[1] *= genericMult;
        saturationMap[2] *= genericMult;
        saturationMap[3] *= genericMult;
        saturationMap[4] *= genericMult;
        saturationMap[5] *= genericMult;
        saturationMap[6] *= genericMult;
        saturationMap[7] *= genericMult;
    }
}
