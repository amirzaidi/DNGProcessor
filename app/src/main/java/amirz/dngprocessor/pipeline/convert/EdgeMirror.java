package amirz.dngprocessor.pipeline.convert;

import android.util.Rational;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class EdgeMirror extends Stage {
    private final SensorParams mSensor;

    private Texture mIntermediate;

    public EdgeMirror(SensorParams sensor) {
        mSensor = sensor;
    }

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        ToIntermediate toIntermediate = previousStages.getStage(ToIntermediate.class);
        Texture intermediate = toIntermediate.getIntermediate();
        int w = intermediate.getWidth();
        int h = intermediate.getHeight();

        converter.setTexture("intermediateBuffer", intermediate);

        int offsetX = mSensor.outputOffsetX;
        int offsetY = mSensor.outputOffsetY;
        converter.seti("minxy", offsetX, offsetY);
        converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);

        // Second texture for per-CFA pixel data
        mIntermediate = new Texture(intermediate.getWidth(), intermediate.getHeight(), 3,
                Texture.Format.Float16, null);

        converter.drawBlocks(mIntermediate);
        intermediate.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_4_edge_mirror_fs;
    }
}
