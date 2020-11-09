package amirz.dngprocessor.pipeline;

import android.graphics.Bitmap;

import java.nio.IntBuffer;

import amirz.dngprocessor.gl.GLPrograms;
import amirz.dngprocessor.math.BlockDivider;

import static amirz.dngprocessor.util.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;

public class GLBlockProcessing {
    private final Bitmap mOut;
    private final int mOutWidth, mOutHeight;
    private final IntBuffer mBlockBuffer;
    private final IntBuffer mOutBuffer;

    public GLBlockProcessing(Bitmap out) {
        mOut = out;
        mOutWidth = out.getWidth();
        mOutHeight = out.getHeight();

        mBlockBuffer = IntBuffer.allocate(mOutWidth * BLOCK_HEIGHT);
        mOutBuffer = IntBuffer.allocate(mOutWidth * mOutHeight);
    }

    public void drawBlocksToOutput(GLPrograms gl) {
        BlockDivider divider = new BlockDivider(mOutHeight, BLOCK_HEIGHT);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];

            glViewport(0, 0, mOutWidth, height);
            gl.seti("yOffset", y);
            gl.draw();

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
}
