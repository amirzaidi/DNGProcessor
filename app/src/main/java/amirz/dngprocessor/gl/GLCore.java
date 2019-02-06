package amirz.dngprocessor.gl;

import android.graphics.Bitmap;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;

import java.nio.IntBuffer;

import static android.opengl.EGL14.*;
import static android.opengl.GLES20.*;

public class GLCore implements AutoCloseable {
    private static final int BLOCK_HEIGHT = 64;

    private final Bitmap mOut;
    private final int mOutWidth, mOutHeight;
    private final EGLDisplay mDisplay;
    private final EGLContext mContext;
    private final EGLSurface mSurface;
    private final GLProgram mProgram;
    private final IntBuffer mBlockBuffer;
    private final IntBuffer mOutBuffer;

    public GLCore(Bitmap out) {
        mOut = out;
        mOutWidth = out.getWidth();
        mOutHeight = out.getHeight();

        int[] major = new int[2];
        int[] minor = new int[2];

        // No error checking performed, minimum required code to elucidate logic
        mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);
        eglInitialize(mDisplay, major, 0, minor, 0);

        int[] attribList2 = new int[] {
                EGL_DEPTH_SIZE, 0,
                EGL_STENCIL_SIZE, 0,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_BIND_TO_TEXTURE_RGBA, EGL_TRUE,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        int[] numConfig = new int[1];
        if (!eglChooseConfig(mDisplay, attribList2, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }

        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(mDisplay, attribList2, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }

        if (configs[0] == null) {
            throw new RuntimeException("OpenGL config is null");
        }

        mContext = eglCreateContext(mDisplay, configs[0], EGL_NO_CONTEXT, new int[] {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        }, 0);

        mSurface = eglCreatePbufferSurface(mDisplay, configs[0], new int[] {
                EGL_WIDTH, mOutWidth,
                EGL_HEIGHT, BLOCK_HEIGHT,
                EGL_NONE
        }, 0);

        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
        mProgram = new GLProgram();

        mBlockBuffer = IntBuffer.allocate(mOutWidth * BLOCK_HEIGHT);
        mOutBuffer = IntBuffer.allocate(mOutWidth * mOutHeight);
    }

    public GLProgram getSquare() {
        return mProgram;
    }

    public void intermediateToOutput() {
        for (int remainingRows = mOutHeight; remainingRows > 0; remainingRows -= BLOCK_HEIGHT) {
            int y = mOutHeight - remainingRows;
            int height = Math.min(remainingRows, BLOCK_HEIGHT);

            mProgram.intermediateToOutput(mOutWidth, y, height);
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
    public void close() {
        mProgram.close();

        eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        eglDestroyContext(mDisplay, mContext);
        eglDestroySurface(mDisplay, mSurface);
        eglTerminate(mDisplay);
    }
}
