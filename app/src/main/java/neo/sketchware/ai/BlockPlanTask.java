package neo.sketchware.ai;

import android.content.Context;

/**
 * Stage 1 of block generation: asks the AI for a compact structural PLAN only (names, roles,
 * relationships, one-line purpose) - no code, no spec, no color. This is deliberately small so
 * it never hits token limits even for a big SDK, and it lets the generation stage below
 * (MultiBlockGenTask) know which blocks belong together (e.g. which callbacks nest inside which
 * listener-wrapper block) before any full block JSON is produced.
 *
 * Plan JSON contract: {"objectKind": "<ShortTypeName>"|null, "blocks": [
 *   {"name":"...", "role":"init|action|listener_wrapper|callback|other",
 *    "purpose":"one line", "category":"...", "parentListener":"<name>"|null,
 *    "exposesValue":{"name":"...","typeName":"String|boolean|...”}|null}
 * ]}
 */
public final class BlockPlanTask {

    private static final String SYSTEM_PROMPT =
            "You are planning a set of CUSTOM PALETTE BLOCKS for Sketchware Neo (a visual Android app " +
            "builder) that together let a user work with a library/SDK/API from the visual block editor. " +
            "Do NOT write any code or block spec yet - only plan the STRUCTURE. " +
            "Think like an Android developer integrating this SDK: what object(s) need to be created/held, " +
            "what setup/init call is needed, what actions (load/show/get/parse/...) are needed, and - " +
            "critically - what callbacks/events the SDK reports (success, failure, loaded, closed, error, " +
            "etc.), since those need dedicated blocks too. Don't invent steps the SDK doesn't have, and " +
            "don't omit a callback the SDK clearly has. " +
            "Reply with ONLY one JSON object, no markdown fences, no extra text: " +
            "{\"objectKind\": \"<ShortTypeName>\" or null, \"blocks\": [ {\"name\":\"...\", \"role\":\"...\", " +
            "\"purpose\":\"...\", \"category\":\"...\", \"parentListener\":\"...\" or null, " +
            "\"exposesValue\": {\"name\":\"...\",\"typeName\":\"...\"} or null}, ... ]} " +
            "Field meanings: " +
            "\"objectKind\" = if this SDK works through a per-instance object the user must hold onto " +
            "(e.g. an InterstitialAd, a Dialog, a Parser handle), give it one short PascalCase type name " +
            "reused by every block that needs that handle (e.g. \"UnityAdsInterstitial\"). If the SDK is " +
            "purely static calls (no handle to hold), use null. " +
            "\"name\" = internal unique block name, short camelCase, no spaces. " +
            "\"role\" = exactly one of: " +
            "\"init\" (one-time setup/creation call), " +
            "\"action\" (a direct call like load/show/get/parse - no callback involved), " +
            "\"listener_wrapper\" (a block whose job is ONLY to attach a set of callbacks to an object - " +
            "plan exactly one of these per distinct callback-group the SDK has, e.g. one for interstitial " +
            "callbacks and a separate one for rewarded-ad callbacks if both exist), " +
            "\"callback\" (one specific event the SDK reports - always has a non-null \"parentListener\" " +
            "naming the listener_wrapper block it belongs inside), " +
            "\"other\" (rare - anything that doesn't fit the above, e.g. a plain getter unrelated to a " +
            "callback). " +
            "\"purpose\" = one short sentence a user would read in the palette. " +
            "\"category\" = a short grouping label for the preview (e.g. \"Setup\", \"Interstitial Ads\", " +
            "\"Rewarded Ads\", \"Parsing\"). Blocks that work together (an action + its listener_wrapper + " +
            "its callbacks) should share one category. " +
            "\"parentListener\" = for role \"callback\" only: the exact \"name\" of the listener_wrapper " +
            "block it nests inside. null for every other role. " +
            "\"exposesValue\" = for a \"callback\" that reports a result value back to the user (e.g. an " +
            "error message, a loaded object, a parsed string) - name the value and its Java type. Most " +
            "callbacks (e.g. a plain \"ad closed\" event) have no value to expose - use null. " +
            "Keep the plan to exactly the blocks actually needed - do not pad, do not omit a real " +
            "callback the SDK has. A typical ads SDK integration needs roughly: 1 init + " +
            "(1 action + 1 listener_wrapper + 2-5 callbacks) per ad type it supports. A parsing/data " +
            "library typically needs no listener_wrapper/callback at all - just \"action\" blocks.";

    private BlockPlanTask() {}

    public static void plan(Context context, String libraryOrTopic, String userPrompt, AiResponseCallback callback) {
        StringBuilder prompt = new StringBuilder();
        if (libraryOrTopic != null && !libraryOrTopic.trim().isEmpty()) {
            prompt.append("Library/SDK/API or topic: ").append(libraryOrTopic.trim()).append("\n\n");
        }
        prompt.append(userPrompt);
        AiManager.sendPrompt(context, SYSTEM_PROMPT, prompt.toString(), callback);
    }
}
