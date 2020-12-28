package amirz.dngprocessor.gl;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class TexturePool extends GLResource {
    private static final String TAG = "TexturePool";

    public static TexturePool getInstance() {
        return GLCore.getInstance().getComponent(TexturePool.class, TexturePool::new);
    }

    public static Texture get(int width, int height, int channels, Texture.Format format) {
        return getInstance().getTex(width, height, channels, format);
    }

    public static Texture get(Texture texture) {
        return get(texture.getWidth(), texture.getHeight(), texture.getChannels(),
                texture.getFormat());
    }

    private final Set<Texture> mPool = new HashSet<>();
    private final Set<Texture> mGrants = new HashSet<>();

    private Texture getTex(int width, int height, int channels, Texture.Format format) {
        Texture texture = null;
        for (Texture tex : mPool) {
            if (tex.getWidth() == width && tex.getHeight() == height
                    && tex.getChannels() == channels && tex.getFormat() == format) {
                mPool.remove(tex);
                texture = tex;
                break;
            }
        }

        if (texture == null) {
            texture = new Texture(width, height, channels, format, null);
            Log.d(TAG, "Created " + texture + ": " + width + "x" + height + " (" + channels + " ch)");
        }

        Texture tex = texture;
        mGrants.add(tex);
        tex.setCloseOverride(() -> {
            mGrants.remove(tex);
            mPool.add(tex);
            tex.setCloseOverride(() -> {
                throw new RuntimeException("Attempting to close " + tex + " twice");
            });
        });

        return tex;
    }

    @Override
    public void release() {
        for (Texture texture : mPool) {
            texture.setCloseOverride(null);
            texture.close();
        }
        mPool.clear();
    }

    public static void logLeaks() {
        for (Texture tex : getInstance().mGrants) {
            Log.d(TAG, "Leaked texture: " + tex);
        }
    }
}
