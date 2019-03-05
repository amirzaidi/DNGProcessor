package amirz.dngprocessor.gl;

import android.graphics.Bitmap;

import java.nio.IntBuffer;

import amirz.dngprocessor.gl.generic.GLCoreBase;
import amirz.dngprocessor.gl.generic.GLProgramBase;

import static android.opengl.GLES20.*;

public class GLCore extends GLCoreBase {
    private static final int BLOCK_HEIGHT = 64;

    private final IntBuffer mBlockBuffer;
    private final IntBuffer mOutBuffer;

    public GLCore(Bitmap out) {
        super(out, BLOCK_HEIGHT);

        mBlockBuffer = IntBuffer.allocate(mOutWidth * BLOCK_HEIGHT);
        mOutBuffer = IntBuffer.allocate(mOutWidth * mOutHeight);
    }

    public void intermediateToOutput() {
        GLProgram program = (GLProgram) getProgram();

        for (int remainingRows = mOutHeight; remainingRows > 0; remainingRows -= BLOCK_HEIGHT) {
            int y = mOutHeight - remainingRows;
            int height = Math.min(remainingRows, BLOCK_HEIGHT);

            program.intermediateToOutput(mOutWidth, y, height);
            mBlockBuffer.position(0);
            glReadPixels(0, 0, mOutWidth, height, GL_RGBA, GL_UNSIGNED_BYTE, mBlockBuffer);
            if (height < BLOCK_HEIGHT) {
                // This can only happen once
                int[] data = new int[mOutWidth * height];
                mBlockBuffer.get(data);
                mOutBuffer.put(data);
            } else {
                mOutBuffer.put(mBlockBuffer);
            }
        }

        mOutBuffer.position(0);
        mOut.copyPixelsFromBuffer(mOutBuffer);
    }

    @Override
    protected GLProgramBase createProgram() {
        return new GLProgram();
    }
}
