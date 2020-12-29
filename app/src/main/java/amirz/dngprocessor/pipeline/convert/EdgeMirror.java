package amirz.dngprocessor.pipeline.convert;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

public class EdgeMirror extends Stage {
    private Texture mIntermediate;

    public Texture getIntermediate() {
        return mIntermediate;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        ToIntermediate toIntermediate = previousStages.getStage(ToIntermediate.class);
        mIntermediate = toIntermediate.getIntermediate();
        int w = mIntermediate.getWidth();
        int h = mIntermediate.getHeight();

        converter.setTexture("intermediateBuffer", mIntermediate);

        int offsetX = getSensorParams().outputOffsetX;
        int offsetY = getSensorParams().outputOffsetY;
        converter.seti("minxy", offsetX, offsetY);
        converter.seti("maxxy", w - offsetX - 1, h - offsetY - 1);

        converter.drawBlocks(mIntermediate, false);
    }

    @Override
    public int getShader() {
        return R.raw.stage1_4_edge_mirror_fs;
    }
}
