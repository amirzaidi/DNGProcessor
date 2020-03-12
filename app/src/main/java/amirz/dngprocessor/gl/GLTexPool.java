package amirz.dngprocessor.gl;

import java.util.HashMap;
import java.util.Map;

public class GLTexPool {
    private Map<GLTex.Config, GLTex> mTexPool = new HashMap<>();

    public AutoRepoolableTex findOrCreate(GLTex.Config config) {
        for (GLTex.Config pooledConfig : mTexPool.keySet()) {
            if (configEqual(config, pooledConfig)) {
                return new AutoRepoolableTex(pooledConfig, mTexPool.remove(config));
            }
        }
        return new AutoRepoolableTex(config, new GLTex(config));
    }

    private static boolean configEqual(GLTex.Config c1, GLTex.Config c2) {
        return c1.w == c2.w && c1.h == c2.h && c1.channels == c2.channels && c1.format == c2.format
                && c1.texFilter == c2.texFilter && c1.texWrap == c2.texWrap;
    }

    public class AutoRepoolableTex implements AutoCloseable {
        private final GLTex.Config mConfig;
        private final GLTex mTex;

        private AutoRepoolableTex(GLTex.Config config, GLTex tex) {
            mConfig = config;
            mTex = tex;
        }

        public GLTex getTex() {
            return mTex;
        }

        @Override
        public void close() {
            mTexPool.put(mConfig, mTex);
        }
    }
}
