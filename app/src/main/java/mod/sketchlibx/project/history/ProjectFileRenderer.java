package mod.sketchlibx.project.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Map;

/**
 * Renders the "file" data type: confirmed (from real exported content) to
 * be the project's activity/custom-view file registry - sections "activity"
 * and "customview", each containing ProjectFileBean-shaped lines (fileName,
 * fileType, options, orientation, theme, keyboardSetting). This is NOT Java
 * source code - the app's real Java is generated on the fly from blocks/
 * components, and this file only lists WHICH activities/custom views exist
 * and their settings. It was being labeled "JAVA" and diffed as raw text
 * before, which is what "shows Sketchware's format instead of real code"
 * was actually about - it was never code to begin with.
 */
public class ProjectFileRenderer {

    public static String render(String raw) {
        if (raw == null || raw.isBlank()) return "(no project files)";

        SketchwareDataFile file = SketchwareDataFile.parse(raw);
        if (file.isEmpty()) return "(no project files)";

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<String>> section : file.sections.entrySet()) {
            String label = "activity".equals(section.getKey()) ? "Activities"
                    : "customview".equals(section.getKey()) ? "Custom Views"
                    : section.getKey();
            sb.append(label).append(":\n");

            for (String line : section.getValue()) {
                try {
                    JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
                    sb.append("  - ").append(getString(obj, "fileName", "?"));

                    List<String> details = new java.util.ArrayList<>();
                    Integer orientation = getIntOrNull(obj, "orientation");
                    if (orientation != null) {
                        details.add("orientation=" + orientationName(orientation));
                    }
                    Integer options = getIntOrNull(obj, "options");
                    if (options != null && options != 0) {
                        details.add("options=" + options);
                    }
                    Integer theme = getIntOrNull(obj, "theme");
                    if (theme != null && theme != -1) {
                        details.add("theme=" + theme);
                    }
                    if (!details.isEmpty()) {
                        sb.append("  (").append(String.join(", ", details)).append(")");
                    }
                    sb.append('\n');
                } catch (Exception e) {
                    sb.append("  - (could not parse: ").append(e.getMessage()).append(")\n");
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String orientationName(int value) {
        return switch (value) {
            case 0 -> "portrait";
            case 1 -> "landscape";
            case 2 -> "unspecified";
            default -> String.valueOf(value);
        };
    }

    private static String getString(JsonObject obj, String key, String def) {
        var e = obj.get(key);
        return (e != null && !e.isJsonNull()) ? e.getAsString() : def;
    }

    private static Integer getIntOrNull(JsonObject obj, String key) {
        var e = obj.get(key);
        try {
            return (e != null && !e.isJsonNull()) ? e.getAsInt() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
