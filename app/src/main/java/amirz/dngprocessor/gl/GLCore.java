package amirz.dngprocessor.gl;

import android.graphics.Bitmap;

import java.nio.IntBuffer;

import javax.microedition.khronos.egl.*;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.EGL14.EGL_BIND_TO_TEXTURE_RGBA;
import static android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION;
import static android.opengl.EGL14.EGL_OPENGL_ES2_BIT;
import static javax.microedition.khronos.egl.EGL10.*;
import static javax.microedition.khronos.opengles.GL10.GL_RGBA;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;

public class GLCore {
    private final Bitmap mOut;
    private final int mOutWidth, mOutHeight;
    private final GL10 mGL;
    private final GLSquare mSquare;

    public GLCore(Bitmap out) {
        mOut = out;
        mOutWidth = out.getWidth();
        mOutHeight = out.getHeight();

        int[] version = new int[2];

        // No error checking performed, minimum required code to elucidate logic
        EGL10 egl = (EGL10) EGLContext.getEGL();
        EGLDisplay display = egl.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        egl.eglInitialize(display, version);

        int[] attribList2 = new int[] {
                EGL_DEPTH_SIZE, 0,
                EGL_STENCIL_SIZE, 0,
                EGL_RED_SIZE, 8,
                EGL_GREEN_SIZE, 8,
                EGL_BLUE_SIZE, 8,
                EGL_ALPHA_SIZE, 8,
                EGL_BIND_TO_TEXTURE_RGBA, 1,
                EGL_SURFACE_TYPE, EGL_PBUFFER_BIT,
                EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL_NONE
        };

        // No error checking performed, minimum required code to elucidate logic
        // Expand on this logic to be more selective in choosing a configuration
        int[] numConfig = new int[1];
        egl.eglChooseConfig(display, attribList2, null, 0, numConfig);

        int configSize = numConfig[0];
        EGLConfig[] mEGLConfigs = new EGLConfig[configSize];
        egl.eglChooseConfig(display, attribList2, mEGLConfigs, configSize, numConfig);

        EGLContext context = egl.eglCreateContext(display, mEGLConfigs[0], EGL_NO_CONTEXT, new int[] {
                EGL_CONTEXT_CLIENT_VERSION, 3, EGL10.EGL_NONE
        });

        EGLSurface surface = egl.eglCreatePbufferSurface(display, mEGLConfigs[0], new int[] {
                EGL_WIDTH, mOutWidth,
                EGL_HEIGHT, mOutHeight,
                EGL_NONE
        });

        egl.eglMakeCurrent(display, surface, surface, context);
        mGL = (GL10) context.getGL();

        mSquare = new GLSquare();
    }

    public GLSquare getSquare() {
        return mSquare;
    }

    public void save() {
        // Save to Bitmap through IntBuffer
        IntBuffer ib = IntBuffer.allocate(mOutWidth * mOutHeight);
        mGL.glReadPixels(0, 0, mOutWidth, mOutHeight, GL_RGBA, GL_UNSIGNED_BYTE, ib);
        mOut.copyPixelsFromBuffer(ib);
    }
}
