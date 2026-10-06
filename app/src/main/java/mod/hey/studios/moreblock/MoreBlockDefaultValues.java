package mod.hey.studios.moreblock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validation and normalization of MoreBlock parameter default values.
 * <p>
 * Everything this class returns is a compile-valid Java expression for the parameter's declared type
 * (it is pasted verbatim into a generated overload), so only a small, closed grammar is accepted:
 * numeric literals, string literals and, for booleans, literals combined with comparison / logical
 * operators (for example {@code 5 == 6}). Method calls, identifiers and anything else are rejected.
 */
public final class MoreBlockDefaultValues {
    private static final int MAX_LENGTH = 200;
    private static final int MAX_STRING_LENGTH = 500;
    private static final Pattern NUMBER = Pattern.compile("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?");

    private MoreBlockDefaultValues() {
    }

    /** Java parameter types that can have a default value. */
    public enum Kind {
        BOOLEAN("boolean"),
        INT("int"),
        FLOAT("float"),
        DOUBLE("double"),
        STRING("String");

        public final String javaType;

        Kind(String javaType) {
            this.javaType = javaType;
        }
    }

    /** Kind for a declared Java parameter type as written by {@code Lx.getMoreBlockCode}. */
    @Nullable
    public static Kind kindForJavaType(@NonNull String javaType) {
        for (Kind kind : Kind.values()) {
            if (kind.javaType.equals(javaType)) return kind;
        }
        return null;
    }

    /**
     * Kind for a spec type code, mirroring what {@code Lx.getMoreBlockCode} declares:
     * {@code b} boolean, {@code d} double, {@code s} String and the {@code m.<type>} variable types
     * that {@code mq.b}/{@code mq.e} turn into a primitive ({@code varInt} double, {@code varFloat}
     * float, {@code varIntNum} int). Everything else (maps, lists, views, components) has no default.
     */
    @Nullable
    public static Kind kindForCode(@Nullable String code) {
        if (code == null) return null;
        return switch (code) {
            case "b" -> Kind.BOOLEAN;
            case "d", "m.varInt" -> Kind.DOUBLE;
            case "s" -> Kind.STRING;
            case "m.varFloat" -> Kind.FLOAT;
            case "m.varIntNum" -> Kind.INT;
            default -> null;
        };
    }

    /**
     * @param raw what the user typed (non-empty)
     * @return a compile-valid Java expression of the given kind, or null if the input is not valid
     */
    @Nullable
    public static String normalize(@NonNull Kind kind, @NonNull String raw) {
        String text = raw.trim();
        switch (kind) {
            case STRING:
                return normalizeString(raw);
            case INT:
                return normalizeInt(text);
            case FLOAT:
                return normalizeFloatingPoint(text, true);
            case DOUBLE:
                return normalizeFloatingPoint(text, false);
            case BOOLEAN:
                return normalizeBoolean(text);
            default:
                return null;
        }
    }

    /** True only for expressions that are exactly what {@link #normalize} would produce (used before emitting code). */
    public static boolean isValidExpression(@NonNull Kind kind, @Nullable String expression) {
        if (expression == null || expression.isEmpty()) return false;
        if (kind == Kind.STRING) return isSafeStringLiteral(expression);
        return expression.equals(normalize(kind, expression));
    }

    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static String normalizeInt(String text) {
        if (!text.matches("[+-]?\\d{1,10}")) return null;
        try {
            long value = Long.parseLong(text.startsWith("+") ? text.substring(1) : text);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return null;
            return Long.toString(value); // also drops leading zeros, which Java would read as octal
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String normalizeFloatingPoint(String text, boolean isFloat) {
        String core = text;
        if (!core.isEmpty()) {
            char last = core.charAt(core.length() - 1);
            if (isFloat ? (last == 'f' || last == 'F') : (last == 'd' || last == 'D')) {
                core = core.substring(0, core.length() - 1);
            }
        }
        if (core.length() > 40 || !NUMBER.matcher(core).matches()) return null;
        try {
            if (isFloat) {
                float value = Float.parseFloat(core);
                if (Float.isNaN(value) || Float.isInfinite(value)) return null;
                return Float.toString(value) + "f";
            }
            double value = Double.parseDouble(core);
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            return Double.toString(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The text is taken literally; one pair of surrounding double quotes is accepted and removed. */
    @Nullable
    private static String normalizeString(String raw) {
        String content = raw;
        if (content.length() >= 2 && content.startsWith("\"") && content.endsWith("\"")) {
            content = content.substring(1, content.length() - 1);
        }
        if (content.length() > MAX_STRING_LENGTH) return null;

        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20 || c == 0x7f) return null;
                    if (c > 0x7e) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        return out.append('"').toString();
    }

    /** Scanner (not a regex) so escapes like {@code \u000a} / {@code \u0022}, which javac resolves before lexing, are refused. */
    private static boolean isSafeStringLiteral(String s) {
        int n = s.length();
        if (n < 2 || n > MAX_STRING_LENGTH * 6 || s.charAt(0) != '"' || s.charAt(n - 1) != '"') return false;
        int i = 1;
        while (i < n - 1) {
            char c = s.charAt(i);
            if (c == '"' || c < 0x20 || c > 0x7e) return false;
            if (c == '\\') {
                if (i + 1 >= n - 1) return false;
                char e = s.charAt(i + 1);
                if (e == '"' || e == '\\' || e == 'n' || e == 'r' || e == 't') {
                    i += 2;
                } else if (e == 'u') {
                    if (i + 6 > n - 1) return false;
                    String hex = s.substring(i + 2, i + 6);
                    if (!hex.matches("[0-9a-fA-F]{4}")) return false;
                    if (Integer.parseInt(hex, 16) < 0x80) return false;
                    i += 6;
                } else {
                    return false;
                }
            } else {
                i++;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Boolean expressions
    // ---------------------------------------------------------------------------------------------

    @Nullable
    private static String normalizeBoolean(String text) {
        if (text.isEmpty() || text.length() > MAX_LENGTH) return null;
        List<String> tokens = tokenize(text);
        if (tokens == null || tokens.isEmpty()) return null;
        BooleanParser parser = new BooleanParser(tokens);
        Type type = parser.parseOr();
        if (type != Type.BOOLEAN || !parser.atEnd()) return null;
        // Tokens are joined with single spaces so "5 - -3" can never collapse into the "--" operator;
        // only the padding inside parentheses is removed.
        return String.join(" ", tokens).replace("( ", "(").replace(" )", ")");
    }

    private enum Type {NUMBER, BOOLEAN}

    @Nullable
    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int start = i;
                boolean simpleInteger = true;
                while (i < n && Character.isDigit(text.charAt(i))) i++;
                if (i < n && text.charAt(i) == '.') {
                    simpleInteger = false;
                    i++;
                    while (i < n && Character.isDigit(text.charAt(i))) i++;
                }
                if (i < n && (text.charAt(i) == 'e' || text.charAt(i) == 'E')) {
                    simpleInteger = false;
                    int save = i;
                    i++;
                    if (i < n && (text.charAt(i) == '+' || text.charAt(i) == '-')) i++;
                    if (i >= n || !Character.isDigit(text.charAt(i))) return null;
                    while (i < n && Character.isDigit(text.charAt(i))) i++;
                    if (save == i) return null;
                }
                if (i < n && "fFdD".indexOf(text.charAt(i)) >= 0) {
                    simpleInteger = false;
                    i++;
                }
                String token = text.substring(start, i);
                if (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '.')) {
                    return null; // 1abc, 1.2.3, hex/binary/long suffixes...
                }
                if (simpleInteger) {
                    // 08 would be an invalid octal literal, 99999999999 an out-of-range int
                    if (token.length() > 1 && token.charAt(0) == '0') return null;
                    if (token.length() > 10 || Long.parseLong(token) > Integer.MAX_VALUE) return null;
                } else {
                    String core = token.replaceAll("[fFdD]$", "");
                    if (!NUMBER.matcher(core).matches()) return null;
                    if (core.length() > 1 && core.charAt(0) == '0' && Character.isDigit(core.charAt(1))) return null;
                }
                tokens.add(token);
            } else if (Character.isLetter(c)) {
                int start = i;
                while (i < n && Character.isLetter(text.charAt(i))) i++;
                String word = text.substring(start, i);
                if (!word.equals("true") && !word.equals("false")) return null;
                if (i < n && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_')) return null;
                tokens.add(word);
            } else if (c == '=' || c == '!' || c == '<' || c == '>') {
                if (i + 1 < n && text.charAt(i + 1) == '=') {
                    tokens.add(text.substring(i, i + 2));
                    i += 2;
                } else if (c == '=') {
                    return null; // assignment is not allowed
                } else {
                    tokens.add(String.valueOf(c));
                    i++;
                }
            } else if (c == '&' || c == '|') {
                if (i + 1 < n && text.charAt(i + 1) == c) {
                    tokens.add(text.substring(i, i + 2));
                    i += 2;
                } else {
                    return null; // bitwise operators are not allowed
                }
            } else if ("+-*/%()".indexOf(c) >= 0) {
                tokens.add(String.valueOf(c));
                i++;
            } else {
                return null;
            }
        }
        return tokens;
    }

    /** Typed recursive descent parser; it only decides whether the expression is a valid boolean expression. */
    private static final class BooleanParser {
        private final List<String> tokens;
        private int position;

        BooleanParser(List<String> tokens) {
            this.tokens = tokens;
        }

        boolean atEnd() {
            return position == tokens.size();
        }

        private boolean accept(String token) {
            if (position < tokens.size() && tokens.get(position).equals(token)) {
                position++;
                return true;
            }
            return false;
        }

        @Nullable
        Type parseOr() {
            Type left = parseAnd();
            while (left != null && accept("||")) {
                Type right = parseAnd();
                if (left != Type.BOOLEAN || right != Type.BOOLEAN) return null;
            }
            return left;
        }

        @Nullable
        private Type parseAnd() {
            Type left = parseEquality();
            while (left != null && accept("&&")) {
                Type right = parseEquality();
                if (left != Type.BOOLEAN || right != Type.BOOLEAN) return null;
            }
            return left;
        }

        @Nullable
        private Type parseEquality() {
            Type left = parseRelational();
            if (left == null) return null;
            while (position < tokens.size() && (tokens.get(position).equals("==") || tokens.get(position).equals("!="))) {
                position++;
                Type right = parseRelational();
                if (right == null || right != left) return null;
                left = Type.BOOLEAN;
            }
            return left;
        }

        @Nullable
        private Type parseRelational() {
            Type left = parseAdditive();
            if (left == null) return null;
            if (position < tokens.size() && (tokens.get(position).equals("<") || tokens.get(position).equals(">")
                    || tokens.get(position).equals("<=") || tokens.get(position).equals(">="))) {
                position++;
                Type right = parseAdditive();
                if (left != Type.NUMBER || right != Type.NUMBER) return null;
                return Type.BOOLEAN;
            }
            return left;
        }

        @Nullable
        private Type parseAdditive() {
            Type left = parseMultiplicative();
            while (left != null && position < tokens.size() && (tokens.get(position).equals("+") || tokens.get(position).equals("-"))) {
                position++;
                Type right = parseMultiplicative();
                if (left != Type.NUMBER || right != Type.NUMBER) return null;
            }
            return left;
        }

        @Nullable
        private Type parseMultiplicative() {
            Type left = parseUnary();
            while (left != null && position < tokens.size() && (tokens.get(position).equals("*") || tokens.get(position).equals("/") || tokens.get(position).equals("%"))) {
                position++;
                Type right = parseUnary();
                if (left != Type.NUMBER || right != Type.NUMBER) return null;
            }
            return left;
        }

        @Nullable
        private Type parseUnary() {
            if (accept("!")) {
                return parseUnary() == Type.BOOLEAN ? Type.BOOLEAN : null;
            }
            if (accept("-") || accept("+")) {
                return parseUnary() == Type.NUMBER ? Type.NUMBER : null;
            }
            return parsePrimary();
        }

        @Nullable
        private Type parsePrimary() {
            if (position >= tokens.size()) return null;
            String token = tokens.get(position);
            if (token.equals("(")) {
                position++;
                Type inner = parseOr();
                if (inner == null || !accept(")")) return null;
                return inner;
            }
            if (token.equals("true") || token.equals("false")) {
                position++;
                return Type.BOOLEAN;
            }
            if (Character.isDigit(token.charAt(0)) || token.charAt(0) == '.') {
                position++;
                return Type.NUMBER;
            }
            return null;
        }
    }
}
