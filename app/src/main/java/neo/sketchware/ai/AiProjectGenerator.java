package neo.sketchware.ai;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

/** Builds a bounded, structured app specification from a natural-language project brief. */
public final class AiProjectGenerator {
    private static final int MAX_SCREENS = 6;
    private static final int MAX_LAYOUT_CHARS = 45000;
    private static final int MAX_CODE_CHARS = 16000;

    private static final String SYSTEM_PROMPT =
            "You are the implementation agent for Sketchware Neo, a native block-based Android project editor. " +
            "Transform the user's app brief into a small, coherent, buildable Android application specification. " +
            "Return ONLY a JSON object, with no markdown or prose, in this shape: " +
            "{\"appName\":\"Short app title\",\"summary\":\"implementation summary\",\"screens\":[{" +
            "\"name\":\"home\",\"title\":\"screen title\",\"layoutXml\":\"complete Android layout XML\", " +
            "\"onCreateCode\":\"Java statements to run after setContentView\",\"helperMethods\":\"optional Java method declarations only\"}], " +
            "\"values\":{\"strings\":{\"resource_name\":\"text\"},\"colors\":{\"resource_name\":\"#6750A4\"},\"dimens\":{\"resource_name\":\"16dp\"}}}. " +
            "Include 1 to " + MAX_SCREENS + " screens. The first screen is the launch screen. Keep names simple (lowercase words/underscores); " +
            "the host will safely normalize them. Every layoutXml must be complete, well-formed XML for a real Android layout. " +
            "Prefer built-in Android views: LinearLayout, FrameLayout, ScrollView, HorizontalScrollView, TextView, Button, EditText, " +
            "ImageView, ProgressBar, CheckBox, RadioButton, Switch, Spinner and nested layouts. Use a LinearLayout or FrameLayout root " +
            "unless a different simple built-in root is necessary. Include xmlns:android on the root, set layout_width and layout_height, " +
            "and assign unique @+id/... ids to views referenced from code. Avoid custom views, custom libraries, data binding, view binding, " +
            "fragments, Compose, resource references not provided in values, unsupported attributes, and generated network/API credentials. " +
            "onCreateCode must be plain Java statements inserted after the host's existing setContentView(R.layout.<screen>) call; " +
            "do not include setContentView, imports, a method signature or class wrapper. Use findViewById(R.id.some_id) and only ids in that screen's XML. " +
            "Use safe, offline demo data and attach meaningful click listeners for interactive controls. " +
            "helperMethods must contain only optional complete Java method declarations, without an enclosing class. " +
            "Keep each field under reasonable size limits. Resource keys must be lowercase Android resource identifiers. Colors must be hex; dimensions dp/sp/px. " +
            "If the request is broad, choose a useful, polished MVP instead of asking follow-up questions. Do not claim a build has succeeded.";

    private AiProjectGenerator() { }

    public static void generate(Context context, String brief, AiResponseCallback callback) {
        generate(context, context == null ? null : AiManager.getActiveConfig(context), brief, callback);
    }

    public static void generate(Context context, AiModelConfig config, String brief, AiResponseCallback callback) {
        if (callback == null) return;
        if (context == null || TextUtils.isEmpty(brief) || brief.trim().isEmpty()) {
            callback.onFailure("Describe the app you want to create first.");
            return;
        }
        if (brief.length() > 24000) {
            callback.onFailure("Use 24,000 characters or fewer for the project brief.");
            return;
        }
        if (config == null) {
            callback.onFailure("Choose and test an AI agent before generating a project.");
            return;
        }

        AiManager.sendPrompt(context, config, SYSTEM_PROMPT,
                "Create a native Sketchware Neo project from this brief. Return the JSON implementation specification only.\n\n" + brief.trim(),
                new AiResponseCallback() {
                    @Override public void onSuccess(String responseText) {
                        try {
                            JSONObject root = new JSONObject(extractJsonObject(responseText));
                            JSONArray screens = root.optJSONArray("screens");
                            if (screens == null || screens.length() == 0 || screens.length() > MAX_SCREENS) {
                                throw new IllegalArgumentException("The generated specification must contain 1–" + MAX_SCREENS + " screens.");
                            }
                            if (TextUtils.isEmpty(root.optString("appName", "").trim())) root.put("appName", "AI Generated App");
                            for (int i = 0; i < screens.length(); i++) {
                                JSONObject screen = screens.optJSONObject(i);
                                if (screen == null) throw new IllegalArgumentException("Screen " + (i + 1) + " is not a valid object.");
                                String xml = screen.optString("layoutXml", "").trim();
                                if (xml.isEmpty() || xml.length() > MAX_LAYOUT_CHARS) {
                                    throw new IllegalArgumentException("Screen " + (i + 1) + " has empty or oversized layout XML.");
                                }
                                String code = screen.optString("onCreateCode", "");
                                String methods = screen.optString("helperMethods", "");
                                if (code.length() > MAX_CODE_CHARS || methods.length() > MAX_CODE_CHARS) {
                                    throw new IllegalArgumentException("Screen " + (i + 1) + " contains too much Java code.");
                                }
                                screen.put("layoutXml", xml);
                                screen.put("onCreateCode", code);
                                screen.put("helperMethods", methods);
                            }
                            callback.onSuccess(root.toString());
                        } catch (Exception exception) {
                            callback.onFailure("The AI response was not a usable project specification: " + safeMessage(exception));
                        }
                    }

                    @Override public void onFailure(String errorMessage) {
                        callback.onFailure(errorMessage);
                    }
                });
    }

    private static String extractJsonObject(String text) {
        if (text == null) throw new IllegalArgumentException("AI returned an empty response.");
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int newline = trimmed.indexOf('\n');
            if (newline >= 0) trimmed = trimmed.substring(newline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON object was found in the AI response.");
        return trimmed.substring(start, end + 1);
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return TextUtils.isEmpty(message) ? exception.getClass().getSimpleName() : message;
    }
}
