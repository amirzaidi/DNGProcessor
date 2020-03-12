package amirz.dngprocessor.gl;

import java.nio.Buffer;

import static android.opengl.GLES20.*;
import static android.opengl.GLES30.*;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_2D;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MAG_FILTER;
import static javax.microedition.khronos.opengles.GL10.GL_TEXTURE_MIN_FILTER;

public class GLTex implements AutoCloseable {
    public enum Format {
        Float16,
        UInt16
    }

    public static class Config {
        public int w, h;
        public Buffer pixels;

        public int channels = 1;
        public Format format = Format.Float16;
        public int texFilter = GL_NEAREST;
        public int texWrap = GL_CLAMP_TO_EDGE;
    }

    private final int mWidth, mHeight;
    private final int mChannels;
    private final Format mFormat;
    private final int mTexId;

    public GLTex(Config config) {
        this(config.w, config.h, config.channels, config.format, config.pixels, config.texFilter,
                config.texWrap);
    }

    public GLTex(int w, int h, int channels, Format format, Buffer pixels) {
        this(w, h, channels, format, pixels, GL_NEAREST);
    }

    public GLTex(int w, int h, int channels, Format format, Buffer pixels, int texFilter) {
        this(w, h, channels, format, pixels, texFilter, GL_CLAMP_TO_EDGE);
    }

    public GLTex(int w, int h, int channels, Format format, Buffer pixels, int texFilter, int texWrap) {
        mWidth = w;
        mHeight = h;
        mChannels = channels;
        mFormat = format;

        int[] texId = new int[1];
        glGenTextures(texId.length, texId, 0);
        mTexId = texId[0];

        // Use a high ID to load
        glActiveTexture(GL_TEXTURE16);
        glBindTexture(GL_TEXTURE_2D, mTexId);
        glTexImage2D(GL_TEXTURE_2D, 0, internalFormat(), w, h, 0, format(), type(), pixels);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, texFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, texFilter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, texWrap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, texWrap);
    }

    public void bind(int slot) {
        glActiveTexture(slot);
        glBindTexture(GL_TEXTURE_2D, mTexId);
    }

    /*
    public void enableMipmaps() {
        glGenerateMipmap(GL_TEXTURE_2D);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST_MIPMAP_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
    }
    */

    public void setFrameBuffer() {
        // Configure frame buffer
        int[] frameBuffer = new int[1];
        glGenFramebuffers(1, frameBuffer, 0);
        glBindFramebuffer(GL_FRAMEBUFFER, frameBuffer[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, mTexId, 0);

        glViewport(0, 0, mWidth, mHeight);
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    @Override
    public void close() {
        glDeleteTextures(1, new int[] { mTexId }, 0);
    }

    private int internalFormat() {
        switch (mFormat) {
            case Float16:
                switch (mChannels) {
                    case 1: return GL_R16F;
                    case 2: return GL_RG16F;
                    case 3: return GL_RGB16F;
                    case 4: return GL_RGBA16F;
                }
            case UInt16:
                switch (mChannels) {
                    case 1: return GL_R16UI;
                    case 2: return GL_RG16UI;
                    case 3: return GL_RGB16UI;
                    case 4: return GL_RGBA16UI;
                }
        }
        return 0;
    }

    private int format() {
        switch (mFormat) {
            case Float16:
                switch (mChannels) {
                    case 1: return GL_RED;
                    case 2: return GL_RG;
                    case 3: return GL_RGB;
                    case 4: return GL_RGBA;
                }
            case UInt16:
                switch (mChannels) {
                    case 1: return GL_RED_INTEGER;
                    case 2: return GL_RG_INTEGER;
                    case 3: return GL_RGB_INTEGER;
                    case 4: return GL_RGBA_INTEGER;
                }
        }
        return 0;
    }

    private int type() {
        switch (mFormat) {
            case Float16: return GL_FLOAT;
            case UInt16: return GL_UNSIGNED_SHORT;
        }
        return 0;
    }
}
