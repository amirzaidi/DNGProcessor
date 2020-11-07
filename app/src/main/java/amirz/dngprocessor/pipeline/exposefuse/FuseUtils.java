package amirz.dngprocessor.pipeline.exposefuse;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;

public class FuseUtils {
    public static Texture downsample2x(GLPrograms converter, Texture in) {
        Texture downsampled = new Texture(in.getWidth() / 2 + 1,
                in.getHeight() / 2 + 1,
                in.getChannels(),
                in.getFormat(), null);

        try (Texture tmp2 = new Texture(in)) {
            try (Texture tmp = new Texture(in)) {
                blur2x(converter, in, tmp, tmp2);
            }

            converter.useProgram(R.raw.stage4_2_downsample);
            converter.setTexture("buf", tmp2);
            converter.seti("maxxy", tmp2.getWidth() - 1, tmp2.getHeight() - 1);
            converter.drawBlocks(downsampled);
        }

        return downsampled;
    }

    public static Texture upsample2x(GLPrograms converter, Texture in, Texture dimens) {
        Texture upsampled = new Texture(dimens);

        converter.useProgram(R.raw.stage4_3_upsample);
        converter.setTexture("buf", in);
        converter.drawBlocks(upsampled);

        try (Texture tmp = new Texture(upsampled)) {
            blur2x(converter, upsampled, tmp, upsampled);
        }

        return upsampled;
    }

    public static void blur2x(GLPrograms converter, Texture in, Texture tmp, Texture out) {
        converter.useProgram(R.raw.stage2_0_blur_3ch_fs);
        converter.seti("bufSize", in.getWidth(), in.getHeight());
        converter.setf("sigma", 1.36f);
        converter.seti("radius", 2);

        converter.setTexture("buf", in);
        converter.seti("dir", 1, 0); // Horizontal
        converter.drawBlocks(tmp);

        converter.setTexture("buf", tmp);
        converter.seti("dir", 0, 1); // Vertical
        converter.drawBlocks(out);
    }
}
