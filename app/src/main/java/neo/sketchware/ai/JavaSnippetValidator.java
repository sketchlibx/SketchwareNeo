package neo.sketchware.ai;

/** Lightweight structural validation for Java statements before they are passed into the block converter. */
public final class JavaSnippetValidator {
    private JavaSnippetValidator() {}

    public static String validate(String code) {
        if (code == null || code.trim().isEmpty()) return "The generated event body is empty.";
        String trimmed = code.trim();
        if (trimmed.startsWith("```") || trimmed.startsWith("package ") || trimmed.startsWith("import ")
                || trimmed.matches("(?s)^(public|private|protected)\\s+(?:static\\s+)?(?:[\\w<>?,.\\[\\] ]+)\\s+\\w+\\s*\\(.*")) {
            return "Return only statements for inside the event body—no fences, imports, class, or method wrapper.";
        }

        int round = 0, square = 0, curly = 0;
        boolean inString = false, inChar = false, lineComment = false, blockComment = false, escaped = false;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = i + 1 < code.length() ? code.charAt(i + 1) : '\0';
            if (lineComment) { if (c == '\n') lineComment = false; continue; }
            if (blockComment) { if (c == '*' && next == '/') { blockComment = false; i++; } continue; }
            if (inString || inChar) {
                if (escaped) { escaped = false; continue; }
                if (c == '\\') { escaped = true; continue; }
                if (inString && c == '"') inString = false;
                else if (inChar && c == '\'') inChar = false;
                continue;
            }
            if (c == '/' && next == '/') { lineComment = true; i++; continue; }
            if (c == '/' && next == '*') { blockComment = true; i++; continue; }
            if (c == '"') { inString = true; continue; }
            if (c == '\'') { inChar = true; continue; }
            if (c == '(') round++;
            else if (c == ')') { if (--round < 0) return "Unmatched ')' in generated Java."; }
            else if (c == '[') square++;
            else if (c == ']') { if (--square < 0) return "Unmatched ']' in generated Java."; }
            else if (c == '{') curly++;
            else if (c == '}') { if (--curly < 0) return "Unmatched '}' in generated Java."; }
        }
        if (inString || inChar || blockComment) return "Generated Java contains an unfinished string, character, or block comment.";
        if (round != 0 || square != 0 || curly != 0) return "Generated Java has unbalanced parentheses, brackets, or braces.";
        return null;
    }
}
