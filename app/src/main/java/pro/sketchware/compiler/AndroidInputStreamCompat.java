package pro.sketchware.compiler;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AndroidInputStreamCompat {
    private static final int BUFFER_SIZE = 8192;

    private AndroidInputStreamCompat() {
    }

    public static byte[] readAllBytes(InputStream input) throws IOException {
        checkOpenIfEmpty(input);
        if (input == null) throw new NullPointerException("input");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer, 0, buffer.length)) != -1) {
            if (count == 0) {
                int value = input.read();
                if (value == -1) break;
                output.write(value);
            } else {
                output.write(buffer, 0, count);
            }
        }
        return output.toByteArray();
    }

    public static byte[] readNBytes(InputStream input, int length) throws IOException {
        checkOpenIfEmpty(input);
        if (input == null) throw new NullPointerException("input");
        if (length < 0) throw new IllegalArgumentException("len < 0");
        if (length == 0) return new byte[0];

        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(length, BUFFER_SIZE));
        byte[] buffer = new byte[Math.min(length, BUFFER_SIZE)];
        int remaining = length;
        while (remaining > 0) {
            int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
            if (count == -1) break;
            if (count == 0) {
                int value = input.read();
                if (value == -1) break;
                output.write(value);
                remaining--;
            } else {
                output.write(buffer, 0, count);
                remaining -= count;
            }
        }
        return output.toByteArray();
    }

    public static int readNBytes(InputStream input, byte[] destination, int offset, int length) throws IOException {
        checkOpenIfEmpty(input);
        if (input == null) throw new NullPointerException("input");
        if (destination == null) throw new NullPointerException("destination");
        if (offset < 0 || length < 0 || length > destination.length - offset) {
            throw new IndexOutOfBoundsException();
        }

        int total = 0;
        while (total < length) {
            int count = input.read(destination, offset + total, length - total);
            if (count == -1) break;
            if (count == 0) {
                int value = input.read();
                if (value == -1) break;
                destination[offset + total] = (byte) value;
                total++;
            } else {
                total += count;
            }
        }
        return total;
    }

    public static long transferTo(InputStream input, OutputStream output) throws IOException {
        checkOpenIfEmpty(input);
        if (input == null) throw new NullPointerException("input");
        if (output == null) throw new NullPointerException("output");

        long transferred = 0L;
        byte[] buffer = new byte[BUFFER_SIZE];
        int count;
        while ((count = input.read(buffer, 0, buffer.length)) != -1) {
            if (count == 0) {
                int value = input.read();
                if (value == -1) break;
                output.write(value);
                transferred++;
            } else {
                output.write(buffer, 0, count);
                transferred += count;
            }
        }
        return transferred;
    }

    public static void skipNBytes(InputStream input, long count) throws IOException {
        checkOpenIfEmpty(input);
        if (input == null) throw new NullPointerException("input");
        long remaining = count;
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped < 0L || skipped > remaining) {
                throw new IOException("Unable to skip exactly the requested number of bytes");
            }
            if (skipped > 0L) {
                remaining -= skipped;
            } else {
                if (input.read() == -1) throw new EOFException();
                remaining--;
            }
        }
    }

    public static InputStream nullInputStream() {
        return new EmptyInputStream();
    }

    private static void checkOpenIfEmpty(InputStream input) throws IOException {
        if (input instanceof EmptyInputStream) {
            ((EmptyInputStream) input).checkOpen();
        }
    }

    private static final class EmptyInputStream extends InputStream {
        private boolean closed;

        void checkOpen() throws IOException {
            if (closed) throw new IOException("Stream closed");
        }

        @Override
        public int read() throws IOException {
            checkOpen();
            return -1;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            checkOpen();
            if (buffer == null) throw new NullPointerException("buffer");
            if (offset < 0 || length < 0 || length > buffer.length - offset) {
                throw new IndexOutOfBoundsException();
            }
            return length == 0 ? 0 : -1;
        }

        @Override
        public long skip(long count) throws IOException {
            checkOpen();
            return 0L;
        }

        @Override
        public int available() throws IOException {
            checkOpen();
            return 0;
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void mark(int readLimit) {
        }

        @Override
        public void reset() throws IOException {
            throw new IOException(closed ? "Stream closed" : "Mark not set");
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
