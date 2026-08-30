package mod.sketchlibx.project.history;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple, self-contained keyword/string/comment highlighting for the diff
 * preview - not a real tokenizer, just enough to make Java/XML readable at
 * a glance in a plain TextView. Colors are chosen to read on both light and
 * dark diff backgrounds (the added/removed row tints), not tied to the
 * app's theme, since this always renders on the fixed diff colors.
 */
public class SimpleSyntaxHighlighter {

    private static final String[] JAVA_KEYWORDS = {
            "public", "private", "protected", "class", "void", "int", "long", "float", "double",
            "boolean", "String", "new", "if", "else", "for", "while", "return", "static", "final",
            "this", "super", "import", "package", "try", "catch", "finally", "throw", "throws",
            "extends", "implements", "interface", "null", "true", "false", "break", "continue",
            "switch", "case", "default", "abstract", "synchronized", "instanceof"
    };

    private static final Pattern JAVA_KEYWORD_PATTERN = Pattern.compile(
            "\\b(" + String.join("|", JAVA_KEYWORDS) + ")\\b");
    private static final Pattern JAVA_STRING = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern JAVA_COMMENT = Pattern.compile("//.*$");

    private static final Pattern XML_TAG = Pattern.compile("</?[a-zA-Z0-9._]+");
    private static final Pattern XML_ATTR_NAME = Pattern.compile("[a-zA-Z0-9_:.]+(?=\\s*=)");
    private static final Pattern XML_STRING = Pattern.compile("\"[^\"]*\"");

    private static final String COLOR_KEYWORD = "#CC7832";
    private static final String COLOR_STRING = "#6A8759";
    private static final String COLOR_COMMENT = "#808080";
    private static final String COLOR_XML_TAG = "#E8BF6A";
    private static final String COLOR_XML_ATTR = "#9876AA";

    public static SpannableString highlight(String line, String language) {
        if (line == null) line = "";
        if ("JAVA".equals(language)) return highlightJava(line);
        if ("XML".equals(language)) return highlightXml(line);
        return new SpannableString(line);
    }

    private static SpannableString highlightJava(String line) {
        SpannableString s = new SpannableString(line);
        applyBoldColor(s, JAVA_KEYWORD_PATTERN, COLOR_KEYWORD);
        applyColor(s, JAVA_STRING, COLOR_STRING);
        applyColor(s, JAVA_COMMENT, COLOR_COMMENT);
        return s;
    }

    private static SpannableString highlightXml(String line) {
        SpannableString s = new SpannableString(line);
        applyColor(s, XML_TAG, COLOR_XML_TAG);
        applyColor(s, XML_ATTR_NAME, COLOR_XML_ATTR);
        applyColor(s, XML_STRING, COLOR_STRING);
        return s;
    }

    private static void applyColor(SpannableString s, Pattern pattern, String colorHex) {
        Matcher m = pattern.matcher(s);
        while (m.find()) {
            s.setSpan(new ForegroundColorSpan(Color.parseColor(colorHex)), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private static void applyBoldColor(SpannableString s, Pattern pattern, String colorHex) {
        Matcher m = pattern.matcher(s);
        while (m.find()) {
            s.setSpan(new ForegroundColorSpan(Color.parseColor(colorHex)), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new StyleSpan(Typeface.BOLD), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
