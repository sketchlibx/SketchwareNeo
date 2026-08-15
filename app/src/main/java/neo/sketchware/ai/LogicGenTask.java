package neo.sketchware.ai;

import android.content.Context;
import android.text.TextUtils;

public final class LogicGenTask {

    private LogicGenTask() {}

    public static void generate(Context context, String activityName, String eventName, String viewContext, String existingCode, String userPrompt, AiResponseCallback callback) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("You are generating Java logic for the \"").append(eventName)
                .append("\" event of activity \"").append(activityName)
                .append("\" inside a Sketchware Neo Android project (a visual block-based app builder that also supports raw Java). ");
        systemPrompt.append("Reply with ONLY plain Java statements that belong inside that event's body - ")
                .append("no method signature, no class wrapper, no imports, no markdown code fences, no explanation, no comments. ");
        systemPrompt.append("Reference views using the pattern binding.viewId (e.g. binding.myButton.setText(\"Hi\")), ")
                .append("which is how this project's generated activities access views.");

        if (!TextUtils.isEmpty(viewContext)) {
            systemPrompt.append("\n\nThe current layout has exactly these views (id: type). Use ONLY these ids via binding.<id> - never invent an id that isn't listed here:\n")
                    .append(viewContext);
        } else {
            systemPrompt.append("\n\nNo views were found in the current layout, so avoid referencing any binding.<id> unless the user's request clearly implies a view that should exist.");
        }

        if (!TextUtils.isEmpty(existingCode)) {
            systemPrompt.append("\n\nThis event ALREADY contains the following logic:\n")
                    .append(existingCode)
                    .append("\n\nThe user's request below is asking you to modify or upgrade this existing logic, not replace it blindly. ")
                    .append("Keep everything that still makes sense, change only what the request asks for, and return the COMPLETE updated body (not just the new/changed lines, not a diff).");
        } else {
            systemPrompt.append("\n\nThis event currently has no logic yet - write it from scratch based on the request below.");
        }

        systemPrompt.append(" Keep the code idiomatic Android/Java, use standard APIs, and prefer simple direct statements ")
                .append("over unnecessary helper methods so more of it can be represented as visual blocks.");

        AiManager.sendPrompt(context, systemPrompt.toString(), userPrompt, new AiResponseCallback() {
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
