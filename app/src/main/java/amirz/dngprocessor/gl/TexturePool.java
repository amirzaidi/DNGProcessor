package amirz.dngprocessor.gl;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class TexturePool {
    private static final String TAG = "TexturePool";

    private static final Set<Texture> sPool = new HashSet<>();
    private static final Set<Texture> sUnreturned = new HashSet<>();

    static {
        GLCore.getInstance().addOnCloseContextRunnable(() -> {
            for (Texture texture : sPool) {
                texture.setCloseOverride(null);
                texture.close();
            }
            sPool.clear();
        });
    }

    public static Texture get(int width, int height, int channels, Texture.Format format) {
        Texture texture = null;
        for (Texture tex : sPool) {
            if (tex.getWidth() == width && tex.getHeight() == height
                    && tex.getChannels() == channels && tex.getFormat() == format) {
                sPool.remove(tex);
                texture = tex;
                break;
            }
        }

        if (texture == null) {
            texture = new Texture(width, height, channels, format, null);
            Log.d(TAG, "Created " + texture + ": " + width + "x" + height + " (" + channels + " ch)");
        }

        Texture tex = texture;
        sUnreturned.add(tex);
        tex.setCloseOverride(() -> {
            sUnreturned.remove(tex);
            sPool.add(tex);
            tex.setCloseOverride(() -> {
                throw new RuntimeException("Attempting to close " + tex + " twice");
            });
        });

        return tex;
    }

    public static Texture get(Texture texture) {
        return get(texture.getWidth(), texture.getHeight(), texture.getChannels(),
                texture.getFormat());
    }

    public static void logLeaks() {
        for (Texture tex : sUnreturned) {
            Log.d(TAG, "Leaked texture: " + tex);
        }
    }
}
