package mod.sketchlibx.project.history;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the real "logic" file format (see SketchwareDataFile's javadoc for
 * the confirmed on-disk shape). Only extracts the BlockBean sections -
 * "{activity}.java_components" (ComponentBean) and
 * "{activity}.java_events" (EventBean) sections are skipped here since this
 * class is specifically for rendering blocks; a full project-data reader
 * would want those too.
 */
public class BlocksJsonParser {

    private static final Gson GSON = new Gson();

    /** activityJavaFileName -> eventKey -> blocks (chain order not yet resolved - by nextBlock/subStack). */
    public static Map<String, Map<String, List<BlockBean>>> parse(String raw) {
        Map<String, Map<String, List<BlockBean>>> result = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return result;

        SketchwareDataFile file = SketchwareDataFile.parse(raw);

        for (Map.Entry<String, List<String>> section : file.sections.entrySet()) {
            String sectionKey = section.getKey();
            int javaIdx = sectionKey.indexOf(".java_");
            if (javaIdx < 0) continue; // not a per-activity section we recognize

            String activityName = sectionKey.substring(0, javaIdx + ".java".length());
            String rest = sectionKey.substring(javaIdx + ".java_".length());

            if ("components".equals(rest) || "events".equals(rest)) {
                continue; // ComponentBean/EventBean sections - not blocks
            }

            String eventKey = rest;
            List<BlockBean> blocks = new ArrayList<>();
            for (String line : section.getValue()) {
                try {
                    BlockBean b = GSON.fromJson(line, BlockBean.class);
                    if (b != null) blocks.add(b);
                } catch (Exception ignored) {
                    // One malformed line shouldn't lose the rest of the event's blocks.
                }
            }

            result.computeIfAbsent(activityName, k -> new LinkedHashMap<>()).put(eventKey, blocks);
        }

        return result;
    }
}
