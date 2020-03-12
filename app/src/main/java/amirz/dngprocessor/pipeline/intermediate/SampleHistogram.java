package amirz.dngprocessor.pipeline.intermediate;

import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class SampleHistogram extends Stage {
    private final int mOutWidth, mOutHeight, mOffsetX, mOffsetY;

    public SampleHistogram(int outWidth, int outHeight, int offsetX, int offsetY) {
        mOutWidth = outWidth;
        mOutHeight = outHeight;
        mOffsetX = offsetX;
        mOffsetY = offsetY;
    }

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.setOutOffset(mOffsetX, mOffsetY);
        converter.analyzeIntermediate(mOutWidth, mOutHeight, 32);

        //converter.setOutOffset(mSensorParams.outputOffsetX, mSensorParams.outputOffsetY);
        //converter.analyzeIntermediate(mOutWidth, mOutHeight, 32);
    }

    @Override
    public int getShader() {
        return R.raw.stage2_1_fs;
    }
}
