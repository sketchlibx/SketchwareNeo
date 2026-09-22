package mod.sketchlibx.project.history;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the REAL on-disk format shared by "logic", "view", and "file":
 *
 * <pre>
 * @sectionKeyA
 * {json object}
 * {json object}
 *
 * @sectionKeyB
 * {json object}
 * </pre>
 *
 * i.e. lines starting with '@' are section headers, and every line
 * following one (until the next '@' line or end of file) is ONE JSON
 * object - not a JSON array, not one big document. This is why every
 * earlier attempt to Gson.fromJson() the whole content as a single Map
 * failed with "Expected BEGIN_OBJECT but was STRING" - the content was
 * never meant to be parsed as one JSON value at all.
 *
 * Confirmed directly against real exported "logic"/"view"/"file" content
 * (not inferred from bytecode alone):
 *  - "logic" sections: "{activity}.java_components" (ComponentBean lines),
 *    "{activity}.java_events" (EventBean lines), and
 *    "{activity}.java_{targetId}_{eventName}" (BlockBean lines - one chain).
 *  - "view" sections: "{layout}.xml" (ViewBean lines - the layout's widgets)
 *    and "{layout}.xml_fab" (a single ViewBean line - that layout's FAB).
 *  - "file" sections: fixed names "@activity" and "@customview"
 *    (ProjectFileBean lines - NOT Java source; this data was mislabeled
 *    "JAVA" before - see ProjectFileRenderer).
 */
public class SketchwareDataFile {

    /** sectionKey -> raw JSON lines in that section, in file order. */
    public final Map<String, List<String>> sections = new LinkedHashMap<>();

    public static SketchwareDataFile parse(String raw) {
        SketchwareDataFile result = new SketchwareDataFile();
        if (raw == null) return result;

        String currentSection = null;
        for (String line : raw.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty()) continue;

            if (trimmed.startsWith("@")) {
                currentSection = trimmed.substring(1);
                result.sections.computeIfAbsent(currentSection, k -> new ArrayList<>());
            } else if (currentSection != null) {
                result.sections.get(currentSection).add(trimmed);
            }
            // A line before any "@" header is unexpected for this format;
            // ignored rather than guessed at.
        }
        return result;
    }

    public boolean isEmpty() {
        return sections.isEmpty();
    }
}
