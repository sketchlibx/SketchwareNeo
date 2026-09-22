package mod.sketchlibx.project.history;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts one layout's raw ViewBean JSON lines into real, readable Android
 * XML text - not a byte-perfect reproduction of Sketchware's own build-time
 * generator (Ox), but built directly from ViewBean's confirmed real fields
 * (id, convert, parent, index, layout{}, text{}, image{}, inject), which
 * were verified against actual exported "view" data, not guessed.
 *
 * "convert" already holds the real widget class name (e.g. "LinearLayout",
 * "EditText", "com.google.android.material...CircularProgressIndicator"),
 * so it's used directly as the XML tag - no type-code-to-tag-name mapping
 * needed.
 */
public class ViewXmlRenderer {

    private static class Node {
        String id;
        JsonObject bean;
        final List<Node> children = new ArrayList<>();
    }

    /** viewBeanLines: one ViewBean JSON object per line, as stored (see SketchwareDataFile). */
    public static String render(List<String> viewBeanLines) {
        if (viewBeanLines == null || viewBeanLines.isEmpty()) return "";

        java.util.Map<String, Node> byId = new java.util.LinkedHashMap<>();
        List<Node> roots = new ArrayList<>();

        for (String line : viewBeanLines) {
            try {
                JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                Node n = new Node();
                n.id = getString(obj, "id", "view");
                n.bean = obj;
                byId.put(n.id, n);
            } catch (Exception ignored) {
                // one malformed line shouldn't lose the rest of the layout
            }
        }

        for (Node n : byId.values()) {
            String parentId = getString(n.bean, "parent", null);
            Node parent = parentId != null ? byId.get(parentId) : null;
            if (parent != null) {
                parent.children.add(n);
            } else {
                roots.add(n);
            }
        }
        for (Node n : byId.values()) {
            n.children.sort((a, b) -> Integer.compare(getInt(a.bean, "index", 0), getInt(b.bean, "index", 0)));
        }

        StringBuilder sb = new StringBuilder();
        for (Node root : roots) {
            renderNode(root, 0, sb);
        }
        return sb.toString();
    }

    /** For a "{layout}.xml_fab" section: a single ViewBean line, no tree needed. */
    public static String renderSingle(String viewBeanLine) {
        if (viewBeanLine == null || viewBeanLine.isBlank()) return "";
        try {
            JsonObject obj = JsonParser.parseString(viewBeanLine).getAsJsonObject();
            Node n = new Node();
            n.bean = obj;
            StringBuilder sb = new StringBuilder();
            renderNode(n, 0, sb);
            return sb.toString();
        } catch (Exception e) {
            return "<!-- could not render: " + e.getMessage() + " -->";
        }
    }

    private static void renderNode(Node node, int depth, StringBuilder sb) {
        JsonObject bean = node.bean;
        String tag = getString(bean, "convert", "View");
        String id = getString(bean, "id", null);

        indent(sb, depth).append('<').append(tag).append('\n');
        if (id != null && !id.isEmpty()) {
            indent(sb, depth + 1).append("android:id=\"@+id/").append(id).append("\"\n");
        }

        JsonObject layout = getObject(bean, "layout");
        if (layout != null) {
            indent(sb, depth + 1).append("android:layout_width=\"").append(dimen(getInt(layout, "width", -2))).append("\"\n");
            indent(sb, depth + 1).append("android:layout_height=\"").append(dimen(getInt(layout, "height", -2))).append("\"\n");

            int orientation = getInt(layout, "orientation", -1);
            if (orientation == 0) indent(sb, depth + 1).append("android:orientation=\"horizontal\"\n");
            else if (orientation == 1) indent(sb, depth + 1).append("android:orientation=\"vertical\"\n");

            String gravity = gravity(getInt(layout, "gravity", 0));
            if (gravity != null) indent(sb, depth + 1).append("android:gravity=\"").append(gravity).append("\"\n");

            appendIfNonZero(sb, depth + 1, "android:layout_marginTop", getInt(layout, "marginTop", 0));
            appendIfNonZero(sb, depth + 1, "android:layout_marginBottom", getInt(layout, "marginBottom", 0));
            appendIfNonZero(sb, depth + 1, "android:layout_marginStart", getInt(layout, "marginLeft", 0));
            appendIfNonZero(sb, depth + 1, "android:layout_marginEnd", getInt(layout, "marginRight", 0));
            appendIfNonZero(sb, depth + 1, "android:paddingTop", getInt(layout, "paddingTop", 0));
            appendIfNonZero(sb, depth + 1, "android:paddingBottom", getInt(layout, "paddingBottom", 0));
            appendIfNonZero(sb, depth + 1, "android:paddingStart", getInt(layout, "paddingLeft", 0));
            appendIfNonZero(sb, depth + 1, "android:paddingEnd", getInt(layout, "paddingRight", 0));

            float weight = getFloat(layout, "weight", 0);
            if (weight != 0) indent(sb, depth + 1).append("android:layout_weight=\"").append(weight).append("\"\n");

            Integer bg = getIntOrNull(layout, "backgroundColor");
            if (bg != null) indent(sb, depth + 1).append("android:background=\"").append(colorHex(bg)).append("\"\n");
        }

        JsonObject text = getObject(bean, "text");
        if (text != null) {
            String textVal = getString(text, "text", "");
            if (!textVal.isEmpty()) indent(sb, depth + 1).append("android:text=\"").append(escape(textVal)).append("\"\n");
            String hint = getString(text, "hint", "");
            if (!hint.isEmpty()) indent(sb, depth + 1).append("android:hint=\"").append(escape(hint)).append("\"\n");
            Integer textColor = getIntOrNull(text, "textColor");
            if (textColor != null) indent(sb, depth + 1).append("android:textColor=\"").append(colorHex(textColor)).append("\"\n");
            int textSize = getInt(text, "textSize", 0);
            if (textSize > 0) indent(sb, depth + 1).append("android:textSize=\"").append(textSize).append("sp\"\n");
        }

        String inject = getString(bean, "inject", "");
        if (!inject.isEmpty()) {
            indent(sb, depth + 1).append(inject).append('\n');
        }

        if (node.children.isEmpty()) {
            // remove trailing newline before self-closing for tidier output
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
            sb.append(" />\n");
        } else {
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') sb.setLength(sb.length() - 1);
            sb.append(">\n");
            for (Node child : node.children) {
                renderNode(child, depth + 1, sb);
            }
            indent(sb, depth).append("</").append(tag).append(">\n");
        }
    }

    private static void appendIfNonZero(StringBuilder sb, int depth, String attr, int value) {
        if (value != 0) indent(sb, depth).append(attr).append("=\"").append(value).append("dp\"\n");
    }

    private static String dimen(int value) {
        if (value == -1) return "match_parent";
        if (value == -2) return "wrap_content";
        return value + "dp";
    }

    private static String gravity(int value) {
        return switch (value) {
            case 17 -> "center";
            case 1 -> "center_horizontal";
            case 16 -> "center_vertical";
            case 48 -> "top";
            case 80 -> "bottom";
            case 3 -> "left";
            case 5 -> "right";
            default -> null; // unrecognized/compound gravity value - omit rather than guess
        };
    }

    private static String colorHex(int argb) {
        return String.format("#%08X", argb);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static StringBuilder indent(StringBuilder sb, int depth) {
        return sb.append("    ".repeat(Math.max(0, depth)));
    }

    private static String getString(JsonObject obj, String key, String def) {
        JsonElement e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        JsonElement e = obj.get(key);
        try {
            return (e != null && !e.isJsonNull()) ? e.getAsInt() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    private static Integer getIntOrNull(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        try {
            return (e != null && !e.isJsonNull()) ? e.getAsInt() : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        JsonElement e = obj.get(key);
        try {
            return (e != null && !e.isJsonNull()) ? e.getAsFloat() : def;
        } catch (Exception ex) {
            return def;
        }
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        JsonElement e = obj.get(key);
        return (e != null && e.isJsonObject()) ? e.getAsJsonObject() : null;
    }
}
