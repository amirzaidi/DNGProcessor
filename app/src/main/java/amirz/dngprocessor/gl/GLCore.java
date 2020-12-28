package amirz.dngprocessor.gl;

import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static android.opengl.EGL14.*;

/**
 * Since OpenGL is entirely static, this is a singleton wrapper.
 */
public class GLCore {
    private static final String TAG = "GLCore";

    private static GLCore sInstance;

    public static GLCore getInstance() {
        if (sInstance == null) {
            sInstance = new GLCore();
        }
        return sInstance;
    }

    private final EGLDisplay mDisplay;
    private final EGLConfig mConfig;
    private final Map<Pair<Integer, Integer>, EGLSurface> mSurfaces = new HashMap<>();
    private final List<Runnable> mRunOnCloseContext = new ArrayList<>();
    private final Map<Class<?>, GLResource> mComponents = new HashMap<>();
    private EGLContext mContext;
    private EGLSurface mSurface;
    private Pair<Integer, Integer> mDimens;

    public GLCore() {
        mDisplay = eglGetDisplay(EGL_DEFAULT_DISPLAY);

        int[] major = new int[2];
        int[] minor = new int[2];
        eglInitialize(mDisplay, major, 0, minor, 0);

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
        if (!eglChooseConfig(mDisplay, attribList, 0,
                null, 0, 0, numConfig, 0)
                || numConfig[0] == 0) {
            throw new RuntimeException("OpenGL config count zero");
        }

        int configSize = numConfig[0];
        EGLConfig[] configs = new EGLConfig[configSize];
        if (!eglChooseConfig(mDisplay, attribList, 0,
                configs, 0, configSize, numConfig, 0)) {
            throw new RuntimeException("OpenGL config loading failed");
        }

        mConfig = configs[0];
        if (mConfig == null) {
            throw new RuntimeException("OpenGL config is null");
        }
    }

    public void setDimens(int width, int height) {
        if (mDimens != null && mDimens.first == width && mDimens.second == height) {
            Log.d(TAG, "Reusing full Context and Pbuffer Surface");
        } else {
            closeExistingContext();

            mDimens = new Pair<>(width, height);
            Log.d(TAG, "Reusing Pbuffer Surface: " + mSurfaces.containsKey(mDimens));
            mSurface = mSurfaces.computeIfAbsent(mDimens, x -> eglCreatePbufferSurface(
                    mDisplay, mConfig, new int[] {
                            EGL_WIDTH, x.first,
                            EGL_HEIGHT, x.second,
                            EGL_NONE
                    }, 0));

            mContext = eglCreateContext(mDisplay, mConfig, EGL_NO_CONTEXT, new int[] {
                    EGL_CONTEXT_CLIENT_VERSION, 3,
                    EGL_NONE
            }, 0);
        }

        eglMakeCurrent(mDisplay, mSurface, mSurface, mContext);
    }

    private void closeExistingContext() {
        if (mContext != null) {
            Log.d(TAG, "Closing current context");

            for (Runnable runnable : mRunOnCloseContext) {
                runnable.run();
            }
            for (GLResource resource : mComponents.values()) {
                resource.release();
            }

            eglMakeCurrent(mDisplay, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            eglDestroyContext(mDisplay, mContext);
            mContext = null;
        }
    }

    public void addOnCloseContextRunnable(Runnable onClose) {
        mRunOnCloseContext.add(onClose);
    }

    @SuppressWarnings("unchecked")
    public <T extends GLResource> T getComponent(Class<T> cls, Supplier<T> constructor) {
        return (T) mComponents.computeIfAbsent(cls, x -> constructor.get());
    }
}
