package mod.sketchlibx.project.history;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.Map;

/**
 * Parses the raw "logic" file content into {@code Map<eventName, List<BlockBean>>}.
 *
 * Defensively handles content that's double-JSON-encoded (a JSON string
 * containing the real object as escaped text, e.g. "{\"onCreate\":[...]}"
 * instead of a bare {"onCreate":[...]}) - this is what causes Gson to throw
 * "Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $", since the
 * top-level token is a quote character. If the first parse attempt fails with
 * exactly that shape of error, one level of String-unwrapping is tried before
 * giving up for real.
 */
public class BlocksJsonParser {

    private static final Gson GSON = new Gson();
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, List<BlockBean>>>() {}.getType();

    public static Map<String, List<BlockBean>> parse(String json) throws Exception {
        if (json == null || json.trim().isEmpty()) return null;

        try {
            return GSON.fromJson(json, MAP_TYPE);
        } catch (Exception firstAttemptError) {
            // Try treating it as a JSON string wrapping the real object, and
            // parse THAT. If this also fails, surface the ORIGINAL error, since
            // that's the more informative one if the content isn't actually
            // double-encoded at all.
            try {
                String unwrapped = GSON.fromJson(json, String.class);
                if (unwrapped != null && !unwrapped.equals(json)) {
                    return GSON.fromJson(unwrapped, MAP_TYPE);
                }
            } catch (Exception ignored) {
                // fall through to rethrow the original error below
            }
            throw firstAttemptError;
        }
    }
}
