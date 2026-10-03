package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradleDependencyEditor {

    private static final Pattern BLOCK_OPEN = Pattern.compile("(?m)^[ \\t]*dependencies[ \\t]*\\{");

    private GradleDependencyEditor() {
    }

    @NonNull
    public static String add(@NonNull String content, @NonNull String configuration, @NonNull String coordinate) {
        int[] range = findBlock(content);
        String line = "    " + configuration + " '" + coordinate + "'\n";
        if (range == null) {
            String prefix = content.endsWith("\n") || content.isEmpty() ? "" : "\n";
            return content + prefix + "\ndependencies {\n" + line + "}\n";
        }
        int close = range[1];
        int lineStart = content.lastIndexOf('\n', close - 1) + 1;
        boolean closeOnOwnLine = content.substring(lineStart, close).trim().isEmpty();
        if (closeOnOwnLine) {
            return content.substring(0, lineStart) + line + content.substring(lineStart);
        }
        return content.substring(0, close) + "\n" + line + content.substring(close);
    }

    @NonNull
    public static String remove(@NonNull String content, @NonNull String key) {
        int[] range = findBlock(content);
        if (range == null) return content;
        String block = content.substring(range[0], range[1]);
        Pattern line = linePattern(key);
        StringBuilder kept = new StringBuilder();
        for (String l : block.split("\n", -1)) {
            if (line.matcher(l).matches()) continue;
            kept.append(l).append('\n');
        }
        kept.setLength(Math.max(0, kept.length() - 1));
        return content.substring(0, range[0]) + kept + content.substring(range[1]);
    }

    @NonNull
    public static String update(@NonNull String content, @NonNull String key, @NonNull String configuration, @NonNull String coordinate) {
        int[] range = findBlock(content);
        if (range == null) return content;
        String block = content.substring(range[0], range[1]);
        Pattern line = linePattern(key);
        StringBuilder out = new StringBuilder();
        boolean replaced = false;
        for (String l : block.split("\n", -1)) {
            Matcher m = line.matcher(l);
            if (!replaced && m.matches()) {
                out.append(m.group(1)).append(configuration).append(" '").append(coordinate).append("'");
                replaced = true;
            } else {
                out.append(l);
            }
            out.append('\n');
        }
        out.setLength(Math.max(0, out.length() - 1));
        return content.substring(0, range[0]) + out + content.substring(range[1]);
    }

    @Nullable
    private static int[] findBlock(String content) {
        Matcher m = BLOCK_OPEN.matcher(content);
        if (!m.find()) return null;
        int open = content.indexOf('{', m.start());
        int depth = 0;
        for (int i = open; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return new int[]{open + 1, i};
            }
        }
        return null;
    }

    private static Pattern linePattern(String key) {
        return Pattern.compile("^([ \\t]*)[A-Za-z][A-Za-z0-9_]*[ \\t]*\\(?[ \\t]*([\"'])" + Pattern.quote(key) + "(?::[^\"']*)?\\2[ \\t]*\\)?[ \\t]*(?://.*)?$");
    }
}
