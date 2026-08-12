package neo.sketchware.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class AiHttpUtil {

    private AiHttpUtil() {}

    public static String post(String urlString, Map<String, String> headers, String jsonBody) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(30000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                connection.setRequestProperty(entry.getKey(), entry.getValue());
            }

            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            String body = readStream(stream);

            if (statusCode < 200 || statusCode >= 300) {
                throw new IOException("HTTP " + statusCode + ": " + body);
            }

            return body;
        } finally {
            connection.disconnect();
        }
    }

    private static String readStream(InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
