package amirz.dngprocessor.pipeline.convert;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

public class GreenDemosaic extends Stage {
    @Override
    public int getShader() {
        return R.raw.stage1_2_fs;
    }
}
