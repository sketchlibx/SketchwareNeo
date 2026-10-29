package neo.sketchware.ai;

import java.util.regex.Pattern;

/** Small, conservative prompt normalization shared by every provider. */
public final class AiPromptOptimizer {
    private static final int MAX_SYSTEM_CHARS = 12000;
    private static final int MAX_USER_CHARS = 48000;
    private static final Pattern TOO_MANY_BLANK_LINES = Pattern.compile("\\n[ \\t]*\\n(?:[ \\t]*\\n)+");

    private AiPromptOptimizer() {}

    public static String compact(String input) {
        if (input == null) return "";
        String text = input.replace("\r\n", "\n").replace('\r', '\n').trim();
        text = TOO_MANY_BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text;
    }

    public static String optimizeSystem(String input) {
        return limit(compact(input), MAX_SYSTEM_CHARS, "[System instructions shortened to fit the request.]", true);
    }

    public static String optimizeUser(String input) {
        return limit(compact(input), MAX_USER_CHARS,
                "\n\n[Middle context omitted to reduce request size; beginning and ending context retained.]\n\n", true);
    }

    private static String limit(String text, int max, String marker, boolean preserveTail) {
        if (text.length() <= max) return text;
        int head = preserveTail ? (max - marker.length()) * 2 / 3 : max - marker.length();
        int tail = preserveTail ? max - marker.length() - head : 0;
        if (head < 0) head = 0;
        if (tail <= 0) return text.substring(0, max - marker.length()) + marker;
        return text.substring(0, head) + marker + text.substring(text.length() - tail);
    }
}
