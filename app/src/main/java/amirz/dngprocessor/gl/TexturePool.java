package amirz.dngprocessor.gl;

import java.util.HashSet;
import java.util.Set;

public class TexturePool {
    public enum Type {
        OneChF(1, Texture.Format.Float16),
        OneChI(1, Texture.Format.UInt16),
        TwoChF(2, Texture.Format.Float16),
        TwoChI(2, Texture.Format.UInt16),
        ThreeChF(3, Texture.Format.Float16),
        ThreeChI(3, Texture.Format.UInt16),
        FourChF(4, Texture.Format.Float16),
        FourChI(4, Texture.Format.UInt16);

        public final int channels;
        public final Texture.Format format;

        Type(int channels, Texture.Format format) {
            this.channels = channels;
            this.format = format;
        }
    }

    private static final Set<Texture> sPool = new HashSet<>();

    public static Texture get(int width, int height, Type type) {
        for (Texture tex : sPool) {
            if (tex.getWidth() == width && tex.getHeight() == height
                    && tex.getChannels() == type.channels && tex.getFormat() == type.format) {
                sPool.remove(tex);
                return tex;
            }
        }

        return new Texture(width, height, type.channels, type.format, null);
    }

    public static void put(Texture texture) {
        sPool.add(texture);
    }

    public static void invalidateAll() {
        for (Texture texture : sPool) {
            texture.close();
        }
        sPool.clear();
    }
}
