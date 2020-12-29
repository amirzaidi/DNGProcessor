package amirz.dngprocessor.pipeline.convert;

import android.util.Rational;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class ToIntermediate extends Stage {
    private final float[] mSensorToXYZ_D50;

    private Texture mIntermediate;

    public ToIntermediate(float[] sensorToXYZ_D50) {
        mSensorToXYZ_D50 = sensorToXYZ_D50;
    }

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        PreProcess preProcess = previousStages.getStage(PreProcess.class);

        converter.seti("rawWidth", preProcess.getInWidth());
        converter.seti("rawHeight", preProcess.getInHeight());

        // Second texture for per-CFA pixel data
        mIntermediate = TexturePool.get(preProcess.getInWidth(), preProcess.getInHeight(), 3,
                Texture.Format.Float16);

        // Load mosaic and green raw texture
        try (Texture sensorGTex = previousStages.getStage(GreenDemosaic.class).getSensorGTex()) {
            try (Texture sensorTex = preProcess.getSensorTex()) {
                converter.setTexture("rawBuffer", sensorTex);
                converter.setTexture("greenBuffer", sensorGTex);

                Rational[] neutralPoint = getSensorParams().neutralColorPoint;
                byte[] cfaVal = getSensorParams().cfaVal;
                converter.setf("neutralLevel",
                        neutralPoint[cfaVal[0]].floatValue(),
                        neutralPoint[cfaVal[1]].floatValue(),
                        neutralPoint[cfaVal[2]].floatValue(),
                        neutralPoint[cfaVal[3]].floatValue());

                converter.setf("neutralPoint",
                        neutralPoint[0].floatValue(),
                        neutralPoint[1].floatValue(),
                        neutralPoint[2].floatValue());

                converter.setf("sensorToXYZ", mSensorToXYZ_D50);
                converter.seti("cfaPattern", preProcess.getCfaPattern());

                try (Texture gainMapTex = preProcess.getGainMapTex()) {
                    converter.setTexture("gainMap", gainMapTex);
                    converter.drawBlocks(mIntermediate);
                }
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
