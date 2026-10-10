package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class FailureFormatter {

    private static final int MAX_CAUSE_DEPTH = 5;

    private FailureFormatter() {
    }

    @NonNull
    public static String describe(@Nullable Throwable error) {
        if (error == null) return "Unknown error";
        List<String> parts = new ArrayList<>();
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < MAX_CAUSE_DEPTH) {
            parts.add(describeSingle(current, depth == 0));
            Throwable cause = current.getCause();
            if (cause == current) break;
            current = cause;
            depth++;
        }
        StringBuilder out = new StringBuilder(parts.get(0));
        for (int i = 1; i < parts.size(); i++) {
            out.append("; caused by ").append(parts.get(i));
        }
        return out.toString();
    }

    @NonNull
    public static String rootCause(@Nullable Throwable error) {
        if (error == null) return "Unknown error";
        Throwable current = error;
        int depth = 0;
        while (current.getCause() != null && current.getCause() != current && depth < MAX_CAUSE_DEPTH) {
            current = current.getCause();
            depth++;
        }
        return describeSingle(current, true);
    }

    @NonNull
    private static String describeSingle(Throwable t, boolean withLocation) {
        String name = t.getClass().getSimpleName();
        if (name.isEmpty()) name = t.getClass().getName();
        String message = t.getMessage();
        StringBuilder out = new StringBuilder(name);
        if (message != null && !message.trim().isEmpty()) {
            out.append(": ").append(message.trim());
        } else {
            out.append(" (no message)");
            if (withLocation) {
                String location = topFrame(t);
                if (location != null) out.append(" at ").append(location);
            }
        }
        return out.toString();
    }

    @Nullable
    private static String topFrame(Throwable t) {
        StackTraceElement[] stack = t.getStackTrace();
        if (stack == null || stack.length == 0) return null;
        StackTraceElement frame = stack[0];
        String className = frame.getClassName();
        int dot = className.lastIndexOf('.');
        String simple = dot >= 0 ? className.substring(dot + 1) : className;
        String file = frame.getFileName();
        return simple + "." + frame.getMethodName() + (file != null ? "(" + file + ":" + frame.getLineNumber() + ")" : "");
    }

    @NonNull
    public static String httpStatus(int code) {
        String reason;
        switch (code) {
            case 400:
                reason = "Bad Request";
                break;
            case 401:
                reason = "Unauthorized";
                break;
            case 403:
                reason = "Forbidden";
                break;
            case 404:
                reason = "Not Found";
                break;
            case 408:
                reason = "Request Timeout";
                break;
            case 410:
                reason = "Gone";
                break;
            case 429:
                reason = "Too Many Requests";
                break;
            case 500:
                reason = "Internal Server Error";
                break;
            case 502:
                reason = "Bad Gateway";
                break;
            case 503:
                reason = "Service Unavailable";
                break;
            case 504:
                reason = "Gateway Timeout";
                break;
            default:
                reason = null;
        }
        return reason != null ? "HTTP " + code + " " + reason : "HTTP " + code;
    }
}
