package amirz.dngprocessor.gl;

import android.graphics.Bitmap;

import java.nio.IntBuffer;

import amirz.dngprocessor.gl.generic.GLCoreBase;
import amirz.dngprocessor.gl.generic.GLProgramBase;
import amirz.dngprocessor.math.BlockDivider;

import static amirz.dngprocessor.Constants.BLOCK_HEIGHT;
import static android.opengl.GLES20.*;

public class GLCore extends GLCoreBase {
    private final Bitmap mOut;
    private final int mOutWidth, mOutHeight;
    private final IntBuffer mBlockBuffer;
    private final IntBuffer mOutBuffer;

    public GLCore(Bitmap out) {
        super(out.getWidth(), BLOCK_HEIGHT);

        mOut = out;
        mOutWidth = out.getWidth();
        mOutHeight = out.getHeight();

        mBlockBuffer = IntBuffer.allocate(mOutWidth * BLOCK_HEIGHT);
        mOutBuffer = IntBuffer.allocate(mOutWidth * mOutHeight);
    }

    public void intermediateToOutput() {
        GLProgram program = (GLProgram) getProgram();

        BlockDivider divider = new BlockDivider(mOutHeight, BLOCK_HEIGHT);
        int[] row = new int[2];
        while (divider.nextBlock(row)) {
            int y = row[0];
            int height = row[1];
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
