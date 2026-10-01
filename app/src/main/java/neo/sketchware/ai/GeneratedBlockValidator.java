package neo.sketchware.ai;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates one AI-generated block JSON object against the real block.json schema, plus the
 * structural rules for its planned role (init/action/listener_wrapper/callback). Used by
 * BlockGenerationActivity after every generation chunk, and again on the merged final set.
 */
public final class GeneratedBlockValidator {

    private static final Set<String> VALID_BLOCK_TYPES = new HashSet<>(java.util.Arrays.asList(
            "regular", "c", "e", "s", "b", "d", "v", "a", "f", "l", "p", "h"));
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");
    private static final Pattern BARE_PERCENT_M = Pattern.compile("%m(?!\\.[A-Za-z]+)");
    private static final Pattern ANY_PERCENT_PARAM = Pattern.compile("%(?:s(?:\\.inputOnly)?|b|d|m\\.[A-Za-z]+)");
    private static final Pattern NUMBERED_CODE_PLACEHOLDER = Pattern.compile("%(\\d+)\\$s");
    // kq.a() in a.a.a returns this exact color for any opcode it does not recognize as a
    // reserved built-in name; anything else means the name collides with a real built-in block.
    private static final int KQ_UNRECOGNIZED_COLOR = 0xff8a55d7;

    private GeneratedBlockValidator() {}

    public static class Result {
        public final boolean ok;
        public final String error; // null if ok
        public Result(boolean ok, String error) { this.ok = ok; this.error = error; }
        static Result ok() { return new Result(true, null); }
        static Result fail(String msg) { return new Result(false, msg); }
    }

    public static Result validate(JSONObject b, BlockPlanEntry planEntry, Set<String> existingNames, Set<String> namesInThisRun) {
        String name = b.optString("name", "");
        if (name.isEmpty()) return Result.fail("missing \"name\"");
        if (!IDENTIFIER_PATTERN.matcher(name).matches()) return Result.fail("\"name\" must be a plain identifier");
        if (namesInThisRun.contains(name)) return Result.fail("duplicate \"name\" within this generation");
        if (existingNames.contains(name)) return Result.fail("a block named \"" + name + "\" already exists");
        try {
            if (a.a.a.kq.a(name, " ") != KQ_UNRECOGNIZED_COLOR) {
                return Result.fail("\"" + name + "\" collides with a reserved built-in block name");
            }
        } catch (Exception ignored) {
            // don't block import over a failure in the reserved-name check itself
        }

        String type = b.optString("type", "regular");
        if (!VALID_BLOCK_TYPES.contains(type)) return Result.fail("invalid \"type\": \"" + type + "\"");

        String spec = b.optString("spec", "");
        if (spec.isEmpty()) return Result.fail("missing \"spec\"");
        if (BARE_PERCENT_M.matcher(spec).find()) return Result.fail("\"spec\" has a bare %m not followed by .kind");

        if ("e".equals(type)) {
            String spec2 = b.optString("spec2", "");
            if (spec2.isEmpty()) return Result.fail("type is 'e' (if-else) but \"spec2\" is missing");
            if (BARE_PERCENT_M.matcher(spec2).find()) return Result.fail("\"spec2\" has a bare %m not followed by .kind");
        }

        String color = b.optString("color", "");
        if (!color.isEmpty() && !HEX_COLOR_PATTERN.matcher(color).matches()) {
            return Result.fail("\"color\" isn't a valid hex color (e.g. #4A90D9)");
        }

        String code = b.optString("code", "");
        if (code.trim().isEmpty()) return Result.fail("missing \"code\"");

        // Count real (non-substack) parameters declared in spec.
        int specParamCount = 0;
        java.util.regex.Matcher pm = ANY_PERCENT_PARAM.matcher(spec);
        while (pm.find()) specParamCount++;

        int maxCodePlaceholder = 0;
        java.util.regex.Matcher cm = NUMBERED_CODE_PLACEHOLDER.matcher(code);
        while (cm.find()) maxCodePlaceholder = Math.max(maxCodePlaceholder, Integer.parseInt(cm.group(1)));

        if (planEntry == null) return Result.ok(); // no role context (e.g. ad-hoc single block) - schema-only checks above are enough

        switch (planEntry.role) {
            case "listener_wrapper":
                if (!"c".equals(type)) return Result.fail("planned as listener_wrapper but type is \"" + type + "\" (must be \"c\")");
                if (maxCodePlaceholder != specParamCount + 1) {
                    return Result.fail("listener_wrapper's code must have exactly one placeholder beyond its spec params (for the nested callbacks), found " + maxCodePlaceholder + " vs spec params " + specParamCount);
                }
                break;
            case "callback":
                if (!"c".equals(type)) return Result.fail("planned as callback but type is \"" + type + "\" (must be \"c\")");
                if (specParamCount != 0) return Result.fail("a callback's spec must be a bare name with no %-placeholders");
                if (maxCodePlaceholder != 1) return Result.fail("a callback's code must contain exactly one %1$s for its own substack");
                if (planEntry.exposesValueName != null && !code.contains(planEntry.exposesValueName)) {
                    return Result.fail("planned to expose value \"" + planEntry.exposesValueName + "\" but code never declares it");
                }
                break;
            case "init":
            case "action":
            case "other":
                if ("c".equals(type) || "e".equals(type)) {
                    return Result.fail("planned as \"" + planEntry.role + "\" but type \"" + type + "\" implies an inner stack - use \"regular\" or a value type instead");
                }
                break;
            default:
                break;
        }

        return Result.ok();
    }

    /** Builds the matching "v"-type getter block for a callback's exposesValue, exactly mirroring
     *  the real pattern (StringErrorLoadFBAd): code is just the bare name itself. */
    public static JSONObject buildValueGetterBlock(BlockPlanEntry callbackEntry, String color) throws org.json.JSONException {
        JSONObject v = new JSONObject();
        v.put("name", callbackEntry.exposesValueName);
        v.put("type", "v");
        v.put("typeName", callbackEntry.exposesValueTypeName != null ? callbackEntry.exposesValueTypeName : "String");
        v.put("spec", callbackEntry.exposesValueName);
        v.put("code", callbackEntry.exposesValueName);
        v.put("color", color);
        return v;
    }
}
