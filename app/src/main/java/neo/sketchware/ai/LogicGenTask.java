package neo.sketchware.ai;

import android.content.Context;

public final class LogicGenTask {

    private static final String SYSTEM_PROMPT_TEMPLATE =
            "You are generating Java logic for a specific event handler inside a Sketchware Neo " +
            "Android project (a visual block-based app builder that also supports raw Java). " +
            "The user is currently editing the \"%s\" event of activity \"%s\". " +
            "Reply with ONLY plain Java statements that belong inside that event's body - " +
            "no method signature, no class wrapper, no imports, no markdown code fences, no explanation, " +
            "no comments. " +
            "Reference views using the pattern binding.viewId (e.g. binding.myButton.setText(\"Hi\")), " +
            "which is how this project's generated activities access views. " +
            "Keep the code idiomatic Android/Java, use standard APIs, and prefer simple direct statements " +
            "over unnecessary helper methods so more of it can be represented as visual blocks.";

    private LogicGenTask() {}

    public static void generate(Context context, String activityName, String eventName, String userPrompt, AiResponseCallback callback) {
        String systemPrompt = String.format(SYSTEM_PROMPT_TEMPLATE, eventName, activityName);

        AiManager.sendPrompt(context, systemPrompt, userPrompt, new AiResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                callback.onSuccess(stripCodeFences(responseText));
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private static String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }
        return trimmed.trim();
    }
}
