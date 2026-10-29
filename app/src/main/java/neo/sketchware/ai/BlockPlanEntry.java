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
        if (arr.length() > 32) throw new JSONException("The plan contains " + arr.length() + " blocks; split this request into a smaller integration (maximum 32 per run to avoid rate limits).");
        java.util.Set<String> seenNames = new java.util.HashSet<>();
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
            if (e.name == null || !e.name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new JSONException("Block " + (i + 1) + " has an invalid internal name.");
            }
            if (!seenNames.add(e.name)) throw new JSONException("Duplicate planned block name: " + e.name);
            if (!("init".equals(e.role) || "action".equals(e.role) || "listener_wrapper".equals(e.role) || "callback".equals(e.role) || "other".equals(e.role))) {
                throw new JSONException("Block " + e.name + " has an unsupported role: " + e.role);
            }
            if (e.name.length() > 80) throw new JSONException("Block name is too long: " + e.name);
            plan.entries.add(e);
        }
        for (BlockPlanEntry e : plan.entries) {
            if ("callback".equals(e.role)) {
                if (e.parentListener == null || e.parentListener.trim().isEmpty()) throw new JSONException("Callback " + e.name + " is missing parentListener.");
                boolean foundParent = false;
                for (BlockPlanEntry parent : plan.entries) {
                    if (e.parentListener.equals(parent.name) && "listener_wrapper".equals(parent.role)) { foundParent = true; break; }
                }
                if (!foundParent) throw new JSONException("Callback " + e.name + " refers to a missing listener wrapper: " + e.parentListener);
            }
            if (e.exposesValueName != null && !e.exposesValueName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                throw new JSONException("Callback " + e.name + " has an invalid exposed value name.");
            }
        }
        return plan;
    }
}
