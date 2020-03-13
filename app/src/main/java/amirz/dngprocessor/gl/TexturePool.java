package amirz.dngprocessor.gl;

import java.util.HashMap;
import java.util.Map;

public class TexturePool implements AutoCloseable {
    private Map<Texture.Config, Texture> mTexPool = new HashMap<>();

    public AutoRepoolableTex findOrCreate(Texture.Config config) {
        for (Texture.Config pooledConfig : mTexPool.keySet()) {
            if (configEqual(config, pooledConfig)) {
                return new AutoRepoolableTex(pooledConfig, mTexPool.remove(config));
            }
        }
        return new AutoRepoolableTex(config, new Texture(config));
    }

    private static boolean configEqual(Texture.Config c1, Texture.Config c2) {
        return c1.w == c2.w && c1.h == c2.h && c1.channels == c2.channels && c1.format == c2.format
                && c1.texFilter == c2.texFilter && c1.texWrap == c2.texWrap;
    }

    @Override
    public void close() {
        for (Texture tex : mTexPool.values()) {
            tex.close();
        }
        mTexPool.clear();
    }

    public class AutoRepoolableTex implements AutoCloseable {
        private final Texture.Config mConfig;
        private final Texture mTex;

        private AutoRepoolableTex(Texture.Config config, Texture tex) {
            mConfig = config;
            mTex = tex;
        }

        public Texture getTex() {
            return mTex;
        }

        @Override
        public void close() {
            mTexPool.put(mConfig, mTex);
        }
    }
}
