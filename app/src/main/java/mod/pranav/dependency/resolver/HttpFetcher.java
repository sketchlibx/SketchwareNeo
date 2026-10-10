package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

public final class HttpFetcher {

    public static final int CONNECT_TIMEOUT_MS = 10_000;
    public static final int READ_TIMEOUT_MS = 20_000;
    public static final String USER_AGENT = "SketchwareNeo/DependencyResolver";
    private static final int MAX_REDIRECTS = 5;
    private static final int ERROR_BODY_LIMIT = 2048;
    private static final long[] RETRY_DELAYS_MS = {700, 1800};

    private HttpFetcher() {
    }

    public static final class Response {
        public final int code;
        public final String url;
        public final String body;
        @Nullable
        public final Throwable error;

        Response(int code, String url, String body, @Nullable Throwable error) {
            this.code = code;
            this.url = url;
            this.body = body;
            this.error = error;
        }

        public boolean isSuccess() {
            return code >= 200 && code < 300;
        }

        public boolean isNetworkFailure() {
            return code < 0;
        }

        public boolean isTransient() {
            return code < 0 || code == 408 || code == 429 || code >= 500;
        }

        @NonNull
        public String describe() {
            if (error != null) return FailureFormatter.describe(error);
            String detail = FailureFormatter.httpStatus(code);
            String snippet = body == null ? "" : body.trim();
            if (!isSuccess() && !snippet.isEmpty() && snippet.length() <= 160 && !snippet.startsWith("<")) {
                detail += " (" + snippet.replace('\n', ' ') + ")";
            }
            return detail;
        }
    }

    public static final class OpenResult {
        public final HttpURLConnection connection;
        public final int code;
        public final String finalUrl;

        OpenResult(HttpURLConnection connection, int code, String finalUrl) {
            this.connection = connection;
            this.code = code;
            this.finalUrl = finalUrl;
        }
    }

    @NonNull
    public static OpenResult open(@NonNull String url) throws IOException {
        String current = url;
        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", "*/*");
            int code;
            try {
                code = connection.getResponseCode();
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.trim().isEmpty()) {
                    throw new IOException("Redirect (" + code + ") without Location header from " + current);
                }
                current = new URL(new URL(current), location.trim()).toString();
                continue;
            }
            return new OpenResult(connection, code, current);
        }
        throw new IOException("Too many redirects (" + MAX_REDIRECTS + ") starting at " + url);
    }

    @NonNull
    public static Response get(@NonNull String url, int maxBodyBytes) {
        OpenResult opened;
        try {
            opened = open(url);
        } catch (Exception e) {
            return new Response(-1, url, "", e);
        }
        HttpURLConnection connection = opened.connection;
        try {
            boolean success = opened.code >= 200 && opened.code < 300;
            InputStream stream = success ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readLimited(stream, success ? maxBodyBytes : ERROR_BODY_LIMIT, success);
            return new Response(opened.code, opened.finalUrl, body, null);
        } catch (Exception e) {
            return new Response(-1, opened.finalUrl, "", e);
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    public static Response getWithRetry(@NonNull String url, int maxBodyBytes, int attempts, @Nullable BooleanSupplier cancelled) {
        Response last = null;
        for (int i = 0; i < Math.max(1, attempts); i++) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                return last != null ? last : new Response(-1, url, "", new IOException("Cancelled"));
            }
            last = get(url, maxBodyBytes);
            if (!last.isTransient() || i == attempts - 1) return last;
            long delay = RETRY_DELAYS_MS[Math.min(i, RETRY_DELAYS_MS.length - 1)];
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return last;
            }
        }
        return last;
    }

    @NonNull
    private static String readLimited(InputStream stream, int limit, boolean failIfExceeded) throws IOException {
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = in.read(buffer)) != -1) {
                total += read;
                if (total > limit) {
                    if (failIfExceeded) throw new IOException("Response larger than " + limit + " bytes");
                    out.write(buffer, 0, read - (total - limit));
                    break;
                }
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
