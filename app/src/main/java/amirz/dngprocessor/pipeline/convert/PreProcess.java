package amirz.dngprocessor.pipeline.convert;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.List;

import amirz.dngprocessor.R;
import amirz.dngprocessor.gl.GLTex;
import amirz.dngprocessor.params.SensorParams;
import amirz.dngprocessor.pipeline.GLProgramRawConverter;
import amirz.dngprocessor.pipeline.Stage;

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

    public PreProcess(SensorParams sensor, byte[] raw) {
        mSensor = sensor;
        mRaw = raw;
    }

    @Override
    protected void execute(List<Stage> previousStages) {
        super.execute(previousStages);
        GLProgramRawConverter converter = getConverter();

        converter.inWidth = mSensor.inputWidth;
        converter.inHeight = mSensor.inputHeight;
        converter.cfaPattern = mSensor.cfa;

        // First texture is just for normalization
        converter.mSensor = new GLTex(converter.inWidth, converter.inHeight, 1, GLTex.Format.Float16, null);

        // Now create the input texture and bind it to TEXTURE0
        ByteBuffer buffer = ByteBuffer.allocateDirect(mRaw.length);
        buffer.put(mRaw);
        buffer.flip();

        converter.mSensorUI = new GLTex(converter.inWidth, converter.inHeight, 1, GLTex.Format.UInt16, buffer);
        converter.mSensorUI.bind(GL_TEXTURE0);

        converter.seti("rawBuffer", 0);
        converter.seti("rawWidth", converter.inWidth);
        converter.seti("rawHeight", converter.inHeight);

        converter.mSensor.setFrameBuffer();

        float[] gainMap = mSensor.gainMap;
        int[] gainMapSize = mSensor.gainMapSize;
        converter.seti("gainMap", 2);
        if (gainMap == null) {
            gainMap = new float[] { 1.f, 1.f, 1.f, 1.f };
            gainMapSize = new int[] { 1, 1 };
        }

        converter.mGainMap = new GLTex(gainMapSize[0], gainMapSize[1], 4, GLTex.Format.Float16,
                FloatBuffer.wrap(gainMap), GL_LINEAR, GL_CLAMP_TO_EDGE);
        converter.mGainMap.bind(GL_TEXTURE2);

        int[] blackLevel = mSensor.blackLevelPattern;
        converter.setf("blackLevel", blackLevel[0], blackLevel[1], blackLevel[2], blackLevel[3]);
        converter.setf("whiteLevel", mSensor.whiteLevel);

        converter.seti("cfaPattern", converter.cfaPattern);

        converter.seti("hotPixels", 4);
        converter.seti("hotPixelsSize", mSensor.hotPixelsSize);

        int[] hotPixelsSize = mSensor.hotPixelsSize;
        GLTex hotPx = new GLTex(hotPixelsSize[0], hotPixelsSize[1], 1, GLTex.Format.UInt16,
                ShortBuffer.wrap(mSensor.hotPixels), GL_NEAREST, GL_REPEAT);
        hotPx.bind(GL_TEXTURE4);

        converter.drawBlocks(converter.inWidth, converter.inHeight);

        converter.mSensorUI.delete();
        converter.mGainMap.delete();
        hotPx.delete();
    }

    @Override
    public int getShader() {
        return R.raw.stage1_1_fs;
    }
}
