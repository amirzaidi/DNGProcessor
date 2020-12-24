package amirz.dngprocessor.gl;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class TexturePool {
    private static final String TAG = "TexturePool";

    private static final Set<Texture> sPool = new HashSet<>();

    static {
        GLCore.addOnCloseContextRunnable(() -> {
            for (Texture texture : sPool) {
                texture.setCloseOverride(null);
                texture.close();
            }
            sPool.clear();
        });
    }

    public static Texture get(int width, int height, int channels, Texture.Format format) {
        for (Texture tex : sPool) {
            if (tex.getWidth() == width && tex.getHeight() == height
                    && tex.getChannels() == channels && tex.getFormat() == format) {
                Log.d(TAG, "Reusing existing texture from pool");
                sPool.remove(tex);
                return tex;
            }
        }

        Texture texture = new Texture(width, height, channels, format, null);
        texture.setCloseOverride(() -> sPool.add(texture));
        return texture;
    }

    public static Texture get(Texture texture) {
        return get(texture.getWidth(), texture.getHeight(), texture.getChannels(),
                texture.getFormat());
    }
}
