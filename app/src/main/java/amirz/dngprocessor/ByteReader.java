package amirz.dngprocessor;

import android.content.Context;
import android.net.Uri;
import android.support.media.ExifInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteReader {
    public static class ReaderWithExif {
        public final ExifInterface exif;
        public final ByteBuffer wrap;
        public final int length;

        private ReaderWithExif(ExifInterface exif, byte[] bytes) {
            this.exif = exif;
            length = bytes.length;
            wrap = wrap(bytes);
        }
    }

    public static ReaderWithExif fromUri(Context context, Uri uri) {
        byte[] bytes = null;
        try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
            if (stream != null) {
                bytes = fromStream(stream);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (bytes != null) {
            ExifInterface exif = null;
            try (ByteArrayInputStream stream = new ByteArrayInputStream(bytes)) {
                exif = new ExifInterface(stream);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (exif != null) {
                return new ReaderWithExif(exif, bytes);
            }
        }
        return null;
    }

    private static byte[] fromStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteBuffer = new ByteArrayOutputStream();
        int bufferSize = 1024;
        byte[] buffer = new byte[bufferSize];

        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteBuffer.write(buffer, 0, len);
        }
        return byteBuffer.toByteArray();
    }

    public static ByteBuffer wrap(byte[] b) {
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
    }
}
