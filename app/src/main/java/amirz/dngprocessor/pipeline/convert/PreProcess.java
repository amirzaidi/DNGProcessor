package amirz.dngprocessor.pipeline.convert;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.gl.TexturePool;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.*;

public class PreProcess extends Stage {
    private final byte[] mRaw;

    private Texture mSensorTex, mGainMapTex;

    public PreProcess(byte[] raw) {
        mRaw = raw;
    }

    public Texture getSensorTex() {
        return mSensorTex;
    }

    public int getInWidth() {
        return getSensorParams().inputWidth;
    }

    public int getInHeight() {
        return getSensorParams().inputHeight;
    }

    public int getCfaPattern() {
        return getSensorParams().cfa;
    }

    public Texture getGainMapTex() {
        return mGainMapTex;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();
        SensorParams sensor = getSensorParams();

        // First texture is just for normalization
        mSensorTex = TexturePool.get(getInWidth(), getInHeight(), 1,
                Texture.Format.Float16);

        try (Texture sensorUITex = TexturePool.get(getInWidth(), getInHeight(), 1,
                Texture.Format.UInt16)) {
            sensorUITex.setPixels(mRaw);

            converter.setTexture("rawBuffer", sensorUITex);
            converter.seti("rawWidth", getInWidth());
            converter.seti("rawHeight", getInHeight());
            converter.seti("cfaPattern", sensor.cfa);

            float[] gainMap = sensor.gainMap;
            int[] gainMapSize = sensor.gainMapSize;
            if (gainMap == null) {
                gainMap = new float[] { 1f, 1f, 1f, 1f };
                gainMapSize = new int[] { 1, 1 };
            }

            mGainMapTex = new Texture(gainMapSize[0], gainMapSize[1], 4, Texture.Format.Float16,
                    FloatBuffer.wrap(gainMap), GL_LINEAR);
            converter.setTexture("gainMap", mGainMapTex);

            int[] blackLevel = sensor.blackLevelPattern;
            converter.setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
            converter.setf("whiteLevel", sensor.whiteLevel);
            converter.seti("cfaPattern", getCfaPattern());
            converter.seti("hotPixelsSize", sensor.hotPixelsSize);

            int[] hotPixelsSize = sensor.hotPixelsSize;
            try (Texture hotPx = new Texture(hotPixelsSize[0], hotPixelsSize[1], 1, Texture.Format.UInt16,
                    ShortBuffer.wrap(sensor.hotPixels), GL_NEAREST, GL_REPEAT)) {
                converter.setTexture("hotPixels", hotPx);
                converter.drawBlocks(mSensorTex);
            }
        }
    }

    @Override
    public int getShader() {
        return R.raw.stage1_1_fs;
    }
}
