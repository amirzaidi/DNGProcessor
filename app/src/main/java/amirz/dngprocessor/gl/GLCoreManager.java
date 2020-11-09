package amirz.dngprocessor.gl;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Pair;

import java.util.HashMap;
import java.util.Map;

import static android.opengl.EGL14.*;

public class GLCoreManager {
    private static final EGLDisplay sDisplay;
    private static final EGLConfig sConfig;
    private static final Map<Pair<Integer, Integer>, EGLSurface> sSurfaces = new HashMap<>();
    private static EGLContext sContext;

    static {
        sDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);

        int[] major = new int[2];
        int[] minor = new int[2];
        eglInitialize(sDisplay, major, 0, minor, 0);

        int[] attribList = {
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
        if (!eglChooseConfig(sDisplay, attribList, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }

        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(sDisplay, attribList, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }

        sConfig = configs[0];
        if (sConfig == null) {
            throw new RuntimeException("OpenGL config is null");
        }
    }

    public static void setDimens(int width, int height) {
        Pair<Integer, Integer> dimens = new Pair<>(width, height);
        EGLSurface surface = sSurfaces.computeIfAbsent(dimens, x -> eglCreatePbufferSurface(
                sDisplay, sConfig, new int[] {
                        EGL_WIDTH, x.first,
                        EGL_HEIGHT, x.second,
                        EGL_NONE
                }, 0));

        sContext = eglCreateContext(sDisplay, sConfig, EGL_NO_CONTEXT, new int[] {
                EGL_CONTEXT_CLIENT_VERSION, 3,
                EGL_NONE
        }, 0);

        eglMakeCurrent(sDisplay, surface, surface, sContext);
    }

    public static void closeContext() {
        if (sContext != null) {
            eglDestroyContext(sDisplay, sContext);
            sContext = null;
        }
    }
}
