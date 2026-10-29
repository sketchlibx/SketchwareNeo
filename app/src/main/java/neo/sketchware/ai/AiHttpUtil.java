package neo.sketchware.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Shared, bounded HTTP and response handling for every remote/local HTTP provider. */
public final class AiHttpUtil {
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 45000;
    private static final int MAX_RESPONSE_CHARS = 128 * 1024;

    private AiHttpUtil() {}

    public static String post(String urlString, Map<String, String> headers, String jsonBody) throws IOException {
        if (urlString == null || urlString.trim().isEmpty()) throw new IOException("AI endpoint URL is empty.");
        HttpURLConnection connection = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    if (entry.getKey() != null && entry.getValue() != null) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }
            }

            byte[] payload = (jsonBody == null ? "{}" : jsonBody).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload);
                os.flush();
            }

            int statusCode = connection.getResponseCode();
            InputStream stream = statusCode >= 200 && statusCode < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = readStream(stream, MAX_RESPONSE_CHARS);
            if (statusCode < 200 || statusCode >= 300) {
                String retryAfter = connection.getHeaderField("Retry-After");
                throw new IOException(formatHttpError(statusCode, body, retryAfter));
            }
            if (body.trim().isEmpty()) throw new IOException("The AI provider returned an empty HTTP response. Check the selected model and endpoint.");
            return body;
        } catch (SocketTimeoutException e) {
            throw new IOException("The AI request timed out. Try a shorter prompt, reduce the output token limit, or check whether the local model server is still loading.", e);
        } catch (ConnectException e) {
            throw new IOException("Could not connect to the AI endpoint. Check your internet connection, endpoint URL, and whether a local server is running.", e);
        } finally {
            connection.disconnect();
        }
    }

    public static String humanizeError(Exception error, String providerName) {
        String message = error == null ? "" : error.getMessage();
        if (message == null || message.trim().isEmpty()) message = "Unknown error";
        if (message.startsWith("AI request timed out") || message.startsWith("Could not connect")
                || message.startsWith("The AI provider") || message.startsWith("AI provider")
                || message.startsWith("Request rejected") || message.startsWith("API key")
                || message.startsWith("The selected model") || message.startsWith("The AI endpoint")) return message;
        if (error instanceof SocketTimeoutException) return "The " + providerName + " request timed out. Try a shorter prompt or a lower output token limit.";
        if (error instanceof ConnectException) return "Could not connect to " + providerName + ". Check network access and the endpoint URL.";
        if (message.startsWith("HTTP ")) return message;
        return providerName + " request failed: " + truncate(message, 700);
    }

    public static String extractChatCompletionText(String responseBody, String providerName) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray choices = json.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                String detail = json.optString("detail", json.optString("message", ""));
                throw new IOException("The " + providerName + " response did not include any completion choices." + (detail.isEmpty() ? "" : " " + truncate(detail, 300)));
            }
            JSONObject choice = choices.optJSONObject(0);
            JSONObject message = choice == null ? null : choice.optJSONObject("message");
            Object content = message == null ? null : message.opt("content");
            StringBuilder text = new StringBuilder();
            if (content instanceof String) text.append((String) content);
            else if (content instanceof JSONArray) {
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part == null) continue;
                    String value = part.optString("text", "");
                    if (!value.isEmpty()) text.append(value);
                }
            }
            String result = text.toString().trim();
            if (result.isEmpty()) {
                String finish = choice == null ? "" : choice.optString("finish_reason", "");
                if ("length".equals(finish)) throw new IOException("The " + providerName + " output reached its token limit. Increase the output token limit or ask for a smaller result.");
                throw new IOException("The " + providerName + " response contained no readable text. Check model compatibility and request settings.");
            }
            return result;
        } catch (org.json.JSONException e) {
            throw new IOException("The " + providerName + " returned an unrecognized response format.", e);
        }
    }

    /** Parses the non-streaming OpenAI Responses API shape, including incomplete output errors. */
    public static String extractResponsesText(String responseBody, String providerName) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray output = json.optJSONArray("output");
            StringBuilder text = new StringBuilder();
            if (output != null) {
                for (int i = 0; i < output.length(); i++) {
                    JSONObject item = output.optJSONObject(i);
                    if (item == null || !"message".equals(item.optString("type", "message"))) continue;
                    JSONArray content = item.optJSONArray("content");
                    if (content == null) continue;
                    for (int j = 0; j < content.length(); j++) {
                        JSONObject part = content.optJSONObject(j);
                        if (part == null) continue;
                        String kind = part.optString("type", "");
                        if ("output_text".equals(kind) || "text".equals(kind)) {
                            text.append(part.optString("text", ""));
                        }
                    }
                }
            }
            String result = text.toString().trim();
            if (!result.isEmpty()) return result;

            JSONObject incomplete = json.optJSONObject("incomplete_details");
            String reason = incomplete == null ? "" : incomplete.optString("reason", "");
            if ("max_output_tokens".equals(reason)) {
                throw new IOException(providerName + " reached its output-token limit. Increase the output limit or request a smaller result.");
            }
            JSONObject error = json.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                throw new IOException(providerName + " failed: " + (message.isEmpty() ? "the Responses API returned an error." : truncate(message, 500)));
            }
            String status = json.optString("status", "");
            throw new IOException(providerName + " returned no readable text" + (status.isEmpty() ? "." : " (status: " + status + ")."));
        } catch (org.json.JSONException e) {
            throw new IOException(providerName + " returned an unrecognized Responses API format.", e);
        }
    }

    public static String extractGeminiText(String responseBody) throws IOException {
        try {
            JSONObject json = new JSONObject(responseBody);
            JSONArray candidates = json.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) {
                JSONObject feedback = json.optJSONObject("promptFeedback");
                String reason = feedback == null ? "" : feedback.optString("blockReason", "");
                if (!reason.isEmpty()) throw new IOException("Gemini did not return a candidate (" + reason + "). Review the prompt and try again.");
                throw new IOException("Gemini returned no candidate text. The model may be unavailable or the response may have been blocked.");
            }
            JSONObject candidate = candidates.optJSONObject(0);
            JSONObject content = candidate == null ? null : candidate.optJSONObject("content");
            JSONArray parts = content == null ? null : content.optJSONArray("parts");
            StringBuilder result = new StringBuilder();
            if (parts != null) {
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) result.append(part.optString("text", ""));
                }
            }
            String value = result.toString().trim();
            if (value.isEmpty()) {
                String finishReason = candidate == null ? "" : candidate.optString("finishReason", "");
                if ("MAX_TOKENS".equals(finishReason)) throw new IOException("Gemini reached the output token limit. Increase max output tokens or request a smaller result.");
                throw new IOException("Gemini returned no readable text. Check model access and safety feedback.");
            }
            return value;
        } catch (org.json.JSONException e) {
            throw new IOException("Gemini returned an unrecognized response format.", e);
        }
    }

    public static String encodeUrlComponent(String value) {
        try { return URLEncoder.encode(value == null ? "" : value, "UTF-8").replace("+", "%20"); }
        catch (Exception ignored) { return value == null ? "" : value; }
    }

    private static String formatHttpError(int code, String body, String retryAfter) {
        String detail = extractErrorDetail(body);
        String lower = (body == null ? "" : body).toLowerCase(java.util.Locale.ROOT);
        String guidance;
        switch (code) {
            case 400: guidance = "Request rejected. Check the exact model ID, supported parameters, and prompt format."; break;
            case 401: guidance = "API key was rejected. Check the key and make sure it belongs to this provider."; break;
            case 403: guidance = "Access denied. Check API permissions, billing/quota access, and whether this model is enabled for your key."; break;
            case 404: guidance = "Model or endpoint was not found. Verify the exact model ID and API endpoint URL."; break;
            case 408: case 504: guidance = "The provider timed out. Retry with a shorter request or check the provider status."; break;
            case 413: guidance = "The request is too large. Shorten the prompt or attach less code/log output."; break;
            case 429:
                if (lower.contains("quota") || lower.contains("resource_exhausted") || lower.contains("per_day") || lower.contains("daily")) {
                    guidance = "Provider quota/rate limit reached. Free-tier limits can apply per minute and per day; wait until the limit resets or use a smaller request/model that your account supports. No automatic retry was made.";
                } else {
                    guidance = "Provider is rate-limiting requests. Wait 30–60 seconds, then retry; avoid running several AI generations at once.";
                }
                if (retryAfter != null && retryAfter.matches("\\d{1,5}")) guidance += " Retry-After: about " + retryAfter + " second(s).";
                break;
            default:
                if (code >= 500) guidance = "Provider is temporarily unavailable. Wait briefly and retry.";
                else guidance = "Request failed with HTTP " + code + ". Check provider settings and endpoint compatibility.";
                break;
        }
        return guidance + (detail.isEmpty() ? "" : "\nProvider detail: " + truncate(detail, 550));
    }

    private static String extractErrorDetail(String body) {
        if (body == null || body.trim().isEmpty()) return "";
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (message.isEmpty()) message = error.optString("status", "");
                String code = error.optString("code", "");
                return message + (code.isEmpty() ? "" : " (code " + code + ")");
            }
            String message = root.optString("message", root.optString("detail", ""));
            if (!message.isEmpty()) return message;
        } catch (Exception ignored) { }
        return body.trim();
    }

    private static String readStream(InputStream stream, int maxChars) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && builder.length() < maxChars) {
                int remaining = maxChars - builder.length();
                builder.append(line.length() > remaining ? line.substring(0, remaining) : line).append('\n');
            }
        }
        if (builder.length() > maxChars) return builder.substring(0, maxChars);
        return builder.toString();
    }

    private static String truncate(String input, int max) {
        if (input == null) return "";
        String safe = input.replace('\r', ' ').replace('\n', ' ').trim();
        return safe.length() <= max ? safe : safe.substring(0, max) + "…";
    }
}
