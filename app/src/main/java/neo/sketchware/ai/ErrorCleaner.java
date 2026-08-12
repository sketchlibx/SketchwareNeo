package neo.sketchware.ai;

import java.util.regex.Pattern;

public final class ErrorCleaner {

    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*m");

    private static final String[] NOISE_LINE_PREFIXES = {
            "* What went wrong",
            "* Try:",
            "* Get more help",
            "* Exception is:",
            "A problem occurred configuring",
            "A problem occurred evaluating",
            "> Task :",
            "FAILURE: Build failed",
            "BUILD FAILED",
            "See the profiling report",
            "Deprecated Gradle features",
            "You can use '--warning-mode",
            "For more on this, please refer",
            "Run with --stacktrace",
            "Run with --info",
            "Run with --debug",
            "Run with --scan"
    };

    private ErrorCleaner() {}

    public static String clean(String rawError) {
        if (rawError == null) return "";

        String noAnsi = ANSI_ESCAPE.matcher(rawError).replaceAll("");
        String[] lines = noAnsi.split("\n", -1);

        StringBuilder result = new StringBuilder();
        int blankStreak = 0;
        int stackTraceAtCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            if (trimmed.isEmpty()) {
                blankStreak++;
                if (blankStreak > 1) continue;
                result.append('\n');
                continue;
            }
            blankStreak = 0;

            boolean isNoise = false;
            for (String prefix : NOISE_LINE_PREFIXES) {
                if (trimmed.startsWith(prefix)) {
                    isNoise = true;
                    break;
                }
            }
            if (isNoise) continue;

            if (trimmed.startsWith("at ")) {
                stackTraceAtCount++;
                if (stackTraceAtCount > 6) continue;
            } else {
                stackTraceAtCount = 0;
            }

            result.append(line).append('\n');
        }

        String cleaned = result.toString().trim();

        int maxLength = 6000;
        if (cleaned.length() > maxLength) {
            cleaned = cleaned.substring(0, maxLength) + "\n... (truncated)";
        }

        return cleaned;
    }
}
