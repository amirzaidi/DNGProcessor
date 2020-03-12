package amirz.dngprocessor.pipeline.convert;

import amirz.dngprocessor.R;
import amirz.dngprocessor.pipeline.Stage;

public class ToIntermediate extends Stage {
    @Override
    public int getShader() {
        return R.raw.stage1_3_fs;
    }
}
