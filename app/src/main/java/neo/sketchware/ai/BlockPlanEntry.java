package neo.sketchware.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class BlockPlanEntry {
    public String name;
    public String role; // init | action | listener_wrapper | callback | other
    public String purpose;
    public String category;
    public String parentListener; // non-null only for role == callback
    public String exposesValueName;    // non-null if this callback exposes a value
    public String exposesValueTypeName;

    public static class Plan {
        public String objectKind; // nullable
        public List<BlockPlanEntry> entries = new ArrayList<>();
    }

    public static Plan parse(String planJson) throws JSONException {
        String trimmed = planJson.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new JSONException("No JSON object found in plan response");
        }
        JSONObject root = new JSONObject(trimmed.substring(start, end + 1));
        Plan plan = new Plan();
        plan.objectKind = root.isNull("objectKind") ? null : root.optString("objectKind", null);
        JSONArray arr = root.getJSONArray("blocks");
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            BlockPlanEntry e = new BlockPlanEntry();
            e.name = o.optString("name", "");
            e.role = o.optString("role", "other");
            e.purpose = o.optString("purpose", "");
            e.category = o.optString("category", "General");
            e.parentListener = o.isNull("parentListener") ? null : o.optString("parentListener", null);
            JSONObject exposes = o.optJSONObject("exposesValue");
            if (exposes != null) {
                e.exposesValueName = exposes.optString("name", null);
                e.exposesValueTypeName = exposes.optString("typeName", "String");
            }
            plan.entries.add(e);
        }
        return plan;
    }
}
