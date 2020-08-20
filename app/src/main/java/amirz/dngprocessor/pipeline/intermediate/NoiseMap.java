package amirz.dngprocessor.pipeline.intermediate;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.ProcessParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;
import amirz.dngprocessor.pipeline.convert.ToIntermediate;

public class NoiseMap extends Stage {
    private final ProcessParams mProcessParams;
    private Texture[] mNoiseTex;

    public NoiseMap(ProcessParams processParams) {
        mProcessParams = processParams;
    }

    public Texture[] getNoiseTex() {
        return mNoiseTex;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();
        Texture[] layers = previousStages.getStage(Decompose.class).getLayers();

        mNoiseTex = new Texture[layers.length];
        for (int i = 0; i < layers.length; i++) {
            converter.setTexture("intermediate", layers[i]);
            converter.seti("bufSize", layers[i].getWidth(), layers[i].getHeight());
            converter.seti("radius", 1 << (i * 2));
            mNoiseTex[i] = new Texture(layers[i].getWidth() / 4 + 1,
                    layers[i].getHeight() / 4 + 1, 3,
                    Texture.Format.Float16, null);
            converter.drawBlocks(mNoiseTex[i]);
        }

        converter.useProgram(R.raw.stage2_1_noise_level_blur_fs);

        for (int i = 0; i < layers.length; i++) {
            Texture layer = mNoiseTex[i];
            converter.seti("minxy", 0, 0);
            converter.seti("maxxy", layer.getWidth() - 1, layer.getHeight() - 1);
            try (Texture tmp = new Texture(layer.getWidth(), layer.getHeight(), 3,
                    Texture.Format.Float16, null)) {

                // First render to the tmp buffer.
                converter.setTexture("buf", mNoiseTex[i]);
                converter.setf("sigma", 1.5f * (1 << i));
                converter.seti("radius", 3 << i, 1);
                converter.seti("dir", 0, 1); // Vertical
                converter.drawBlocks(tmp, false);

                // Now render from tmp to the real buffer.
                converter.setTexture("buf", tmp);
                converter.seti("dir", 1, 0); // Horizontal
                converter.drawBlocks(mNoiseTex[i]);
            }
        }

        /*
        Texture intermediate = previousStages.getStage(ToIntermediate.class).getIntermediate();
        converter.setTexture("intermediate", intermediate);

        mNoiseTex = new Texture(intermediate.getWidth() / 2,
                intermediate.getHeight() / 2, 3,
                Texture.Format.Float16, null);
        converter.drawBlocks(mNoiseTex);

        converter.useProgram(R.raw.stage2_1_noise_level_blur_fs);
        converter.seti("minxy", 0, 0);
        converter.seti("maxxy", mNoiseTex.getWidth() - 1, mNoiseTex.getHeight() - 1);

        try (Texture tmp = new Texture(intermediate.getWidth(), intermediate.getHeight(), 3,
                Texture.Format.Float16, null)) {

            // First render to the tmp buffer.
            converter.setTexture("buf", mNoiseTex);
            converter.setf("sigma", 9f);
            converter.seti("radius", 18, 1);
            converter.seti("dir", 0, 1); // Vertical
            converter.drawBlocks(tmp, false);

            // Now render from tmp to the real buffer.
            converter.setTexture("buf", tmp);
            converter.seti("dir", 1, 0); // Horizontal
            converter.drawBlocks(mNoiseTex);

            //converter.drawBlocks(tmp);

            //converter.useProgram(R.raw.stage2_1_bilateral_ch);
            //converter.setTexture("buf", tmp);
            //converter.seti("bufSize", tmp.getWidth(), tmp.getHeight());

            //converter.setf("sigma", 0.25f, 2f);
            //converter.seti("radius", 4, 1);
        }
        */
    }

    @Override
    public int getShader() {
        return R.raw.stage2_1_noise_level_fs;
    }
}
