package amirz.dngprocessor.pipeline.convert;

import java.nio.ByteBuffer;
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
    private final SensorParams mSensor;
    private final byte[] mRaw;

    private Texture mSensorTex, mGainMapTex;

    public PreProcess(SensorParams sensor, byte[] raw) {
        mSensor = sensor;
        mRaw = raw;
    }

    public Texture getSensorTex() {
        return mSensorTex;
    }

    public int getInWidth() {
        return mSensor.inputWidth;
    }

    public int getInHeight() {
        return mSensor.inputHeight;
    }

    public int getCfaPattern() {
        return mSensor.cfa;
    }

    public Texture getGainMapTex() {
        return mGainMapTex;
    }

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        GLPrograms converter = getConverter();

        // First texture is just for normalization
        mSensorTex = TexturePool.get(getInWidth(), getInHeight(),
                TexturePool.Type.OneChF);

        // Now create the input texture and bind it to TEXTURE0
        ByteBuffer buffer = ByteBuffer.allocateDirect(mRaw.length);
        buffer.put(mRaw);
        buffer.flip();

        Texture sensorUITex = TexturePool.get(getInWidth(), getInHeight(),
                TexturePool.Type.OneChI);
        sensorUITex.setPixels(buffer);

        converter.setTexture("rawBuffer", sensorUITex);
        converter.seti("rawWidth", getInWidth());
        converter.seti("rawHeight", getInHeight());
        converter.seti("cfaPattern", mSensor.cfa);

        float[] gainMap = mSensor.gainMap;
        int[] gainMapSize = mSensor.gainMapSize;
        if (gainMap == null) {
            gainMap = new float[] { 1f, 1f, 1f, 1f };
            gainMapSize = new int[] {1, 1};
        }

        mGainMapTex = new Texture(gainMapSize[0], gainMapSize[1], 4, Texture.Format.Float16,
                FloatBuffer.wrap(gainMap), GL_LINEAR);
        converter.setTexture("gainMap", mGainMapTex);

        int[] blackLevel = mSensor.blackLevelPattern;
        converter.setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
        converter.setf("whiteLevel", mSensor.whiteLevel);
        converter.seti("cfaPattern", getCfaPattern());
        converter.seti("hotPixelsSize", mSensor.hotPixelsSize);

        int[] hotPixelsSize = mSensor.hotPixelsSize;
        try (Texture hotPx = new Texture(hotPixelsSize[0], hotPixelsSize[1], 1, Texture.Format.UInt16,
                ShortBuffer.wrap(mSensor.hotPixels), GL_NEAREST, GL_REPEAT)) {
            converter.setTexture("hotPixels", hotPx);
            converter.drawBlocks(mSensorTex);
        }

        sensorUITex.close();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_1_fs;
    }
}
