package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.pipeline.Stage;

public class BilateralFilter extends Stage {
    @Override
    public int getShader() {
        return 0;
    }
}
