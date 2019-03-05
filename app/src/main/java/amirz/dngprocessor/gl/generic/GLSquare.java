package amirz.dngprocessor.gl.generic;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import static android.opengl.GLES20.*;

public class GLSquare {
    private static final int COORDS_PER_VERTEX = 3;
    private static final float COORDS[] = {
            -1, 1, 0,
            -1, -1, 0,
            1, 1, 0,
            1, -1, 0
    };
    private static final int STRIDE = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private final FloatBuffer mVertexBuffer;

    public GLSquare() {
        // (# of coordinate values * 4 bytes per float)
        ByteBuffer bb = ByteBuffer.allocateDirect(COORDS.length * 4);

        bb.order(ByteOrder.nativeOrder());
        mVertexBuffer = bb.asFloatBuffer();
        mVertexBuffer.put(COORDS);
        mVertexBuffer.position(0);
    }

    public void draw(int posHandle) {
        glEnableVertexAttribArray(posHandle);
        glVertexAttribPointer(
                posHandle, COORDS_PER_VERTEX,
                GL_FLOAT, false,
                STRIDE, mVertexBuffer);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, COORDS.length / 3);
        glDisableVertexAttribArray(posHandle);
    }
}
