package amirz.dngprocessor.pipeline.convert;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.gl.Texture;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.Stage;
import amirz.dngprocessor.pipeline.StagePipeline;

import static android.opengl.GLES20.GL_CLAMP_TO_EDGE;
import static android.opengl.GLES20.GL_LINEAR;
import static android.opengl.GLES20.GL_NEAREST;
import static android.opengl.GLES20.GL_REPEAT;
import static android.opengl.GLES20.GL_TEXTURE0;
import static android.opengl.GLES20.GL_TEXTURE2;
import static android.opengl.GLES20.GL_TEXTURE4;

public class PreProcess extends Stage {
    private final SensorParams mSensor;
    private final byte[] mRaw;

    private Texture mSensorTex;

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

    @Override
    protected void execute(StagePipeline.StageMap previousStages) {
        super.execute(previousStages);
        GLPrograms converter = getConverter();

        // First texture is just for normalization
        mSensorTex = new Texture(getInWidth(), getInHeight(), 1, Texture.Format.Float16, null);

        // Now create the input texture and bind it to TEXTURE0
        ByteBuffer buffer = ByteBuffer.allocateDirect(mRaw.length);
        buffer.put(mRaw);
        buffer.flip();

        try (Texture sensorUITex = new Texture(getInWidth(), getInHeight(), 1, Texture.Format.UInt16, buffer)) {
            sensorUITex.bind(GL_TEXTURE0);

            converter.seti("rawBuffer", 0);
            converter.seti("rawWidth", getInWidth());
            converter.seti("rawHeight", getInHeight());

            mSensorTex.setFrameBuffer();

            float[] gainMap = mSensor.gainMap;
            int[] gainMapSize = mSensor.gainMapSize;
            converter.seti("gainMap", 2);
            if (gainMap == null) {
                gainMap = new float[]{1.f, 1.f, 1.f, 1.f};
                gainMapSize = new int[]{1, 1};
            }

            try (Texture gainMapTex = new Texture(gainMapSize[0], gainMapSize[1], 4, Texture.Format.Float16,
                    FloatBuffer.wrap(gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE)) {
                gainMapTex.bind(GL_TEXTURE2);

                int[] blackLevel = mSensor.blackLevelPattern;
                converter.setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
                converter.setf("whiteLevel", mSensor.whiteLevel);

                converter.seti("cfaPattern", getCfaPattern());

                converter.seti("hotPixels", 4);
                converter.seti("hotPixelsSize", mSensor.hotPixelsSize);

                int[] hotPixelsSize = mSensor.hotPixelsSize;
                try (Texture hotPx = new Texture(hotPixelsSize[0], hotPixelsSize[1], 1, Texture.Format.UInt16,
                        ShortBuffer.wrap(mSensor.hotPixels), GL_NEAREST, GL_REPEAT)) {
                    hotPx.bind(GL_TEXTURE4);
                    converter.drawBlocks(getInWidth(), getInHeight());
                }
            }

        }
    }

    @Override
    public int getShader() {
        return R.raw.stage1_1_fs;
    }
}
