package neo.sketchware.ai;

import android.content.Context;
import android.text.TextUtils;

/** Builds a compact event-generation request and rejects structurally broken Java before conversion. */
public final class LogicGenTask {
    private static final String SYSTEM_PROMPT =
            "You generate Java statements for an event body in Sketchware Neo, an Android visual-block editor. " +
            "Return ONLY a complete body of Java statements; no Markdown fences, imports, package/class/method wrapper, " +
            "setContentView(), or explanation. Use binding.<id> for views and only IDs listed in the request. " +
            "If existing code is provided, preserve all behavior not asked to change and return the COMPLETE updated body, " +
            "not a diff. If no existing code is provided, write the body from scratch. Prefer simple, idiomatic Android " +
            "statements that can be converted into visual blocks; avoid helper methods unless truly necessary. " +
            "Do not invent view IDs, dependencies, permissions, or API credentials.";

    private LogicGenTask() {}

    public static void generate(Context context, String activityName, String eventName, String viewContext,
                                String existingCode, String userPrompt, AiResponseCallback callback) {
        if (callback == null) return;
        if (TextUtils.isEmpty(userPrompt) || userPrompt.trim().isEmpty()) {
            callback.onFailure("Describe what this event should do first.");
            return;
        }

        StringBuilder request = new StringBuilder(1024);
        request.append("Activity: ").append(safeLabel(activityName)).append('\n')
                .append("Event: ").append(safeLabel(eventName)).append("\n\n");
        if (!TextUtils.isEmpty(viewContext)) {
            request.append("Views available in the current layout (id: type). Use ONLY these IDs as binding.<id>:\n")
                    .append(viewContext.trim()).append("\n\n");
        } else {
            request.append("No current layout view IDs were found. Do not reference binding IDs unless the request explicitly asks to create/use a view.\n\n");
        }
        if (!TextUtils.isEmpty(existingCode) && !existingCode.trim().isEmpty()) {
            request.append("Current event body (update it, do not discard unrelated behavior):\n")
                    .append(existingCode.trim()).append("\n\n");
        } else {
            request.append("The event currently has no logic. Write it from scratch.\n\n");
        }
        request.append("User's requested change (highest priority):\n").append(userPrompt.trim());

        AiManager.sendPrompt(context, SYSTEM_PROMPT, request.toString(), AiManager.Feature.LOGIC, new AiResponseCallback() {
            @Override public void onSuccess(String responseText) {
                String code = stripCodeFences(responseText);
                String issue = JavaSnippetValidator.validate(code);
                if (issue != null) {
                    callback.onFailure("Generated event code failed structural validation: " + issue + " Please retry with a more specific request.");
                    return;
                }
                callback.onSuccess(code);
            }

            @Override public void onFailure(String errorMessage) {
                callback.onFailure(errorMessage);
            }
        });
    }

    private static String safeLabel(String value) {
        return value == null || value.trim().isEmpty() ? "unknown" : value.trim().replace('\n', ' ').replace('\r', ' ');
    }

    private static String stripCodeFences(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1);
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
