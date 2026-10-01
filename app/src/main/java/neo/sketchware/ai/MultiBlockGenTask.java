package neo.sketchware.ai;

import android.content.Context;

/**
 * Stage 2 of block generation. Generates full block JSON for ONE CHUNK of an already-produced
 * BlockPlanTask plan - never the whole SDK in one call - so large requests stay within provider
 * token limits. The chunk's plan entries are included so names/roles/relationships are already
 * decided; this stage only has to fill in spec/code/type/color/imports for them.
 *
 * The system prompt teaches the real Sketchware event/callback pattern via one actual verified
 * example pulled from this app's own block.json (the Facebook Ads interstitial listener), not
 * just a description - this is the fix for Phase B's wrong "'h' type for events" guess.
 */
public final class MultiBlockGenTask {

    private static final String REAL_EXAMPLE =
            "Real verified example from this app's own block library (Facebook Ads interstitial), " +
            "showing exactly how a listener_wrapper + its callbacks + a value-exposing callback work:\n" +
            "{\"name\":\"InterstitialAdsListener\",\"type\":\"c\",\"typeName\":\"\",\"spec\":\"%m.FBAdsInterstitial setListener\",\"code\":\"%1$s.setAdListener(new InterstitialAdListener() {\\n%2$s\\n});\"}\n" +
            "{\"name\":\"IntersOnAdsLoaded\",\"type\":\"c\",\"typeName\":\"\",\"spec\":\"IntersOnAdsLoaded\",\"code\":\"@Override\\npublic void onAdLoaded(Ad ad) {\\n%1$s\\n}\"}\n" +
            "{\"name\":\"IntersOnAdsError\",\"type\":\"c\",\"typeName\":\"\",\"spec\":\"IntersOnAdsError\",\"code\":\"@Override\\npublic void onError(Ad ad, AdError adError) {\\nfinal String StringErrorLoadFBAd = adError.getErrorMessage();\\n%1$s\\n}\"}\n" +
            "{\"name\":\"StringErrorLoadFBAd\",\"type\":\"v\",\"typeName\":\"String\",\"spec\":\"StringErrorLoadFBAd\",\"code\":\"StringErrorLoadFBAd\"}\n" +
            "Notice: the listener_wrapper's code ends with its OWN spec-param substitutions (here just " +
            "%1$s for the object) followed by ONE MORE placeholder (%2$s) with nothing after it in spec - " +
            "that extra trailing placeholder is where callback blocks nest, and it is NOT written in " +
            "\"spec\" at all, only in \"code\". Each callback's spec is JUST its own bare name (no %% " +
            "placeholders), because it has no visible parameters - its \"code\" is a Java @Override method " +
            "with exactly one placeholder (%1$s) for its own nested substack. A callback that exposes a " +
            "result value declares a real Java local variable in its code using the EXACT SAME name as a " +
            "companion \"v\"-type block, whose own code is just that same name as a bare expression - that " +
            "v-type block is how the user reads the value elsewhere in that callback's substack.";

    private static final String SYSTEM_PROMPT =
            "You are generating the full block definitions for ONE GROUP of an already-planned set of " +
            "Sketchware Neo custom palette blocks. You will be given: the overall plan (for context/" +
            "consistency only) and the specific blocks to fully generate right now. Only output the " +
            "requested blocks, not the whole plan.\n\n" + REAL_EXAMPLE + "\n\n" +
            "General rules for every block you generate: " +
            "\"type\" must match the planned \"role\" exactly: init/action/other -> usually \"regular\" " +
            "(or \"s\"/\"b\"/\"d\"/\"l\"/\"a\" if it returns a value) - never \"c\"/\"e\" unless it truly has " +
            "an inner stack; listener_wrapper -> \"c\"; callback -> \"c\". " +
            "If a callback's plan entry has \"exposesValue\" set, its \"code\" MUST declare a real Java " +
            "local variable using EXACTLY that exposesValue name (e.g. \"final String <name> = ...;\") " +
            "before its substack placeholder - do NOT generate a separate block for that value yourself, " +
            "the app builds its getter block automatically from your variable declaration. " +
            "\"spec\" placeholder syntax: %s/%b/%d for string/boolean/number input sockets, %s.inputOnly " +
            "for a plain text field, %m.<kind> for a typed object socket (reuse the SAME <kind> string " +
            "everywhere this SDK's object handle is referenced, matching the plan's objectKind if one was " +
            "given - never invent a different kind name for the same handle). A listener_wrapper's spec has " +
            "ONLY its own real parameters (typically just \"%m.<kind> setListener\"-style) - the substack " +
            "placeholder is implicit and must NOT appear in spec. A callback's spec is its bare name only. " +
            "\"code\" placeholders are %s/%b/%d in the SAME left-to-right order as they appear in spec " +
            "(use %1$s, %2$s, ... numbered form), PLUS exactly one extra trailing numbered placeholder for " +
            "a listener_wrapper's or callback's own substack (the next number after its real params). " +
            "\"color\": reuse one consistent hex color across this whole SDK's blocks unless there's a " +
            "good reason to vary it slightly for a sub-group. " +
            "\"imports\": newline-separated fully-qualified Java imports this block's code needs, empty " +
            "string if none. " +
            "Every block name must be unique, including against the \"existingNames\" list you're given - " +
            "never reuse one. " +
            "Reply with ONLY {\"blocks\":[{...}, ...]} - no markdown fences, no commentary, one object per " +
            "requested block, nothing else.";

    private MultiBlockGenTask() {}

    public static void generateChunk(Context context, String planJson, String chunkBlocksJson, String existingNames, AiResponseCallback callback) {
        String prompt = "Overall plan (context only, do not re-generate blocks from this list unless they also " +
                "appear below):\n" + planJson +
                "\n\nGenerate the FULL block JSON now for exactly these planned blocks:\n" + chunkBlocksJson +
                (existingNames != null && !existingNames.isEmpty()
                        ? "\n\nThese names already exist elsewhere and must not be reused: " + existingNames
                        : "");
        AiManager.sendPrompt(context, SYSTEM_PROMPT, prompt, callback);
    }
}
