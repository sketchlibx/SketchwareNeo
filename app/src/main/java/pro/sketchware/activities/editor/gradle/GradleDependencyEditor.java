package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GradleDependencyEditor {

    private static final Pattern BLOCK_OPEN = Pattern.compile("(?m)^[ \\t]*dependencies[ \\t]*\\{");
    private static final Pattern ENTRY = Pattern.compile("^([ \\t]*)([A-Za-z][A-Za-z0-9_]*)([ \\t]*\\(?[ \\t]*)([\"'])([^\"'\\r\\n]*)\\4");

    private GradleDependencyEditor() {
    }

    private static final class Entry {
        String key;
        String configuration;
        String indent;
        char quote;
        int lineStart;
        int removeEnd;
        int configStart;
        int configEnd;
        int literalStart;
        int literalEnd;
    }

    private static final class Block {
        int open;
        int close;
        boolean[] code;
    }

    @NonNull
    public static String add(@NonNull String content, @NonNull String configuration, @NonNull String coordinate) {
        String eol = content.contains("\r\n") ? "\r\n" : "\n";
        Block block = findBlock(content);
        if (block == null) {
            String prefix = content.isEmpty() || content.endsWith("\n") ? "" : eol;
            return content + prefix + eol + "dependencies {" + eol + "    " + configuration + " '" + coordinate + "'" + eol + "}" + eol;
        }
        List<Entry> entries = entries(content, block);
        String indent = "    ";
        char quote = '\'';
        if (!entries.isEmpty()) {
            Entry last = entries.get(entries.size() - 1);
            indent = last.indent;
            quote = last.quote;
        }
        String line = indent + configuration + " " + quote + coordinate + quote;
        int lineStart = lastIndexOfLineBreak(content, block.close - 1) + 1;
        boolean closeOnOwnLine = lineStart > block.open && content.substring(lineStart, block.close).trim().isEmpty();
        if (closeOnOwnLine) {
            return content.substring(0, lineStart) + line + eol + content.substring(lineStart);
        }
        return content.substring(0, block.close) + eol + line + eol + content.substring(block.close);
    }

    public static boolean has(@NonNull String content, @NonNull String key) {
        return find(content, key) != null;
    }

    @NonNull
    public static String remove(@NonNull String content, @NonNull String key) {
        Entry entry = find(content, key);
        if (entry == null) return content;
        return content.substring(0, entry.lineStart) + content.substring(entry.removeEnd);
    }

    @NonNull
    public static String update(@NonNull String content, @NonNull String key, @NonNull String configuration, @NonNull String coordinate) {
        Entry entry = find(content, key);
        if (entry == null) return content;
        StringBuilder out = new StringBuilder(content);
        out.replace(entry.literalStart, entry.literalEnd, coordinate);
        out.replace(entry.configStart, entry.configEnd, configuration);
        return out.toString();
    }

    private static Entry find(String content, String key) {
        Block block = findBlock(content);
        if (block == null) return null;
        for (Entry entry : entries(content, block)) {
            if (entry.key.equals(key)) return entry;
        }
        return null;
    }

    private static List<Entry> entries(String content, Block block) {
        List<Entry> result = new ArrayList<>();
        int depth = 0;
        int pos = block.open + 1;
        while (pos < block.close) {
            int lineEnd = lineEnd(content, pos, block.close);
            if (depth == 0) {
                Matcher m = ENTRY.matcher(content.substring(pos, lineEnd));
                if (m.lookingAt() && block.code[pos + m.start(2)]) {
                    String[] parts = m.group(5).split(":", -1);
                    boolean singleLiteral = !hasCommaInCode(content, pos + m.end(), lineEnd, block.code);
                    if (parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty() && singleLiteral) {
                        Entry entry = new Entry();
                        entry.key = parts[0] + ":" + parts[1];
                        entry.configuration = m.group(2);
                        entry.indent = m.group(1);
                        entry.quote = m.group(4).charAt(0);
                        entry.lineStart = pos;
                        entry.configStart = pos + m.start(2);
                        entry.configEnd = pos + m.end(2);
                        entry.literalStart = pos + m.end(4);
                        entry.literalEnd = pos + m.end() - 1;
                        entry.removeEnd = statementEnd(content, pos, block);
                        result.add(entry);
                    }
                }
            }
            depth = Math.max(0, depth + braceDelta(content, block, pos, lineEnd));
            pos = nextLineStart(content, lineEnd, block.close);
        }
        return result;
    }

    private static boolean hasCommaInCode(String content, int from, int to, boolean[] code) {
        for (int i = from; i < to; i++) {
            if (code[i] && content.charAt(i) == ',') return true;
        }
        return false;
    }

    private static int statementEnd(String content, int lineStart, Block block) {
        int depth = 0;
        int pos = lineStart;
        while (pos < block.close) {
            int lineEnd = lineEnd(content, pos, block.close);
            depth += braceDelta(content, block, pos, lineEnd);
            int next = nextLineStart(content, lineEnd, block.close);
            if (depth <= 0) return next;
            pos = next;
        }
        return block.close;
    }

    private static int lineEnd(String content, int from, int limit) {
        int i = from;
        while (i < limit && content.charAt(i) != '\n' && content.charAt(i) != '\r') i++;
        return i;
    }

    private static int nextLineStart(String content, int lineEnd, int limit) {
        int i = lineEnd;
        if (i < limit && content.charAt(i) == '\r') i++;
        if (i < limit && content.charAt(i) == '\n') i++;
        return i;
    }

    private static int braceDelta(String content, Block block, int from, int to) {
        int delta = 0;
        for (int i = from; i < to; i++) {
            if (!block.code[i]) continue;
            char c = content.charAt(i);
            if (c == '{') delta++;
            else if (c == '}') delta--;
        }
        return delta;
    }

    private static int lastIndexOfLineBreak(String content, int from) {
        for (int i = Math.min(from, content.length() - 1); i >= 0; i--) {
            if (content.charAt(i) == '\n') return i;
        }
        return -1;
    }

    private static Block findBlock(String content) {
        boolean[] code = codeMask(content);
        Matcher m = BLOCK_OPEN.matcher(content);
        while (m.find()) {
            int open = content.indexOf('{', m.start());
            if (open < 0 || !code[open]) continue;
            int depth = 0;
            for (int i = open; i < content.length(); i++) {
                if (!code[i]) continue;
                char c = content.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        Block block = new Block();
                        block.open = open;
                        block.close = i;
                        block.code = code;
                        return block;
                    }
                }
            }
            return null;
        }
        return null;
    }

    private static boolean[] codeMask(String s) {
        int n = s.length();
        boolean[] code = new boolean[n];
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) i++;
                i = Math.min(n, i + 2);
                continue;
            }
            if (c == '"' || c == '\'') {
                String triple = c == '"' ? "\"\"\"" : "'''";
                if (s.startsWith(triple, i)) {
                    i += 3;
                    while (i < n && !s.startsWith(triple, i)) i++;
                    i = Math.min(n, i + 3);
                    continue;
                }
                i++;
                while (i < n) {
                    char d = s.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    i++;
                    if (d == c || d == '\n') break;
                }
                continue;
            }
            code[i] = true;
            i++;
        }
        return code;
    }
}
