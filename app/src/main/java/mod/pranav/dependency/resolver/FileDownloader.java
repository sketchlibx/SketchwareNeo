package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.function.BooleanSupplier;

public final class FileDownloader {

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = {800, 2000};

    private FileDownloader() {
    }

    public interface Progress {
        void onProgress(long bytes, long total);
    }

    public static final class DownloadException extends IOException {
        public final int httpCode;
        public final String url;

        DownloadException(String message, int httpCode, String url, @Nullable Throwable cause) {
            super(message, cause);
            this.httpCode = httpCode;
            this.url = url;
        }

        boolean isTransient() {
            return httpCode < 0 || httpCode == 408 || httpCode == 429 || httpCode >= 500;
        }
    }

    public static long download(@NonNull String url, @NonNull File destination, @Nullable Progress progress,
                                @Nullable BooleanSupplier cancelled) throws IOException {
        DownloadException last = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("Cancelled");
            try {
                return downloadOnce(url, destination, progress, cancelled);
            } catch (DownloadException e) {
                last = e;
                if (!e.isTransient() || attempt == ATTEMPTS - 1) throw e;
                try {
                    Thread.sleep(RETRY_DELAYS_MS[Math.min(attempt, RETRY_DELAYS_MS.length - 1)]);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last != null ? last : new DownloadException("Download failed", -1, url, null);
    }

    private static long downloadOnce(String url, File destination, @Nullable Progress progress,
                                     @Nullable BooleanSupplier cancelled) throws IOException {
        File parent = destination.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("Cannot create directory " + parent.getAbsolutePath());
        }
        File partial = new File(destination.getPath() + ".part");
        HttpFetcher.OpenResult opened;
        try {
            opened = HttpFetcher.open(url);
        } catch (IOException e) {
            throw new DownloadException(FailureFormatter.describe(e), -1, url, e);
        }
        HttpURLConnection connection = opened.connection;
        try {
            if (opened.code < 200 || opened.code >= 300) {
                String body = "";
                InputStream error = connection.getErrorStream();
                if (error != null) {
                    try (InputStream in = error) {
                        byte[] bytes = new byte[512];
                        int read = in.read(bytes);
                        if (read > 0) body = new String(bytes, 0, read).trim();
                    } catch (IOException ignored) {
                    }
                }
                String detail = FailureFormatter.httpStatus(opened.code) + " for " + opened.finalUrl;
                if (!body.isEmpty() && !body.startsWith("<") && body.length() < 160) detail += " (" + body.replace('\n', ' ') + ")";
                throw new DownloadException(detail, opened.code, opened.finalUrl, null);
            }
            long expected = connection.getContentLengthLong();
            long written = 0;
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(partial)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    if (cancelled != null && cancelled.getAsBoolean()) throw new IOException("Cancelled");
                    out.write(buffer, 0, read);
                    written += read;
                    if (progress != null) progress.onProgress(written, expected);
                }
                out.flush();
            } catch (IOException e) {
                if (e instanceof DownloadException) throw e;
                if ("Cancelled".equals(e.getMessage())) throw e;
                throw new DownloadException(FailureFormatter.describe(e) + " after " + written + " bytes from " + opened.finalUrl, -1, opened.finalUrl, e);
            }
            if (written == 0) {
                throw new DownloadException("Server returned an empty file for " + opened.finalUrl, 0, opened.finalUrl, null);
            }
            if (expected > 0 && written != expected) {
                throw new DownloadException("Incomplete download: received " + written + " of " + expected + " bytes from " + opened.finalUrl, -1, opened.finalUrl, null);
            }
            if (destination.exists() && !destination.delete()) {
                throw new IOException("Cannot replace " + destination.getAbsolutePath());
            }
            if (!partial.renameTo(destination)) {
                throw new IOException("Cannot move downloaded file into place: " + destination.getAbsolutePath());
            }
            return written;
        } finally {
            connection.disconnect();
            if (partial.exists()) partial.delete();
        }
    }
}
