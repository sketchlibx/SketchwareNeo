package neo.sketchware.ai;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

public final class ErrorFixTask {

    private static final Pattern JAVA_FILE_LINE_PATTERN =
            Pattern.compile("([\\w./\\\\-]+\\.java):(\\d+):");

    private static final String SYSTEM_PROMPT =
            "You are an expert Android/Java build-error fixer working inside Sketchware Neo, " +
            "a visual Android app builder that generates a real Gradle project behind the scenes. " +
            "You will be given a cleaned build error log, and optionally the full content of the " +
            "source file where the error occurred. " +
            "Reply with ONLY a single JSON object, no markdown fences, no extra text, in exactly this shape: " +
            "{\"summary\":\"one short plain-language sentence describing what broke\"," +
            "\"explanation\":\"1-3 short sentences on why it happened, no jargon dump\"," +
            "\"patches\":[{\"originalSnippet\":\"...\",\"fixedSnippet\":\"...\"}]," +
            "\"needsManualEvent\":false," +
            "\"manualEventHint\":\"\"," +
            "\"createActivityEventName\":\"\"," +
            "\"createViewEventTargetId\":\"\"," +
            "\"createViewEventName\":\"\"}" +
            " Rules for patches: each entry describes ONE change. " +
            "For a REPLACE, originalSnippet is the exact text to find (copied verbatim from the provided file " +
            "content) and fixedSnippet is its replacement. " +
            "For a pure REMOVAL, fixedSnippet is an empty string. " +
            "For a pure ADDITION with no specific anchor point (e.g. adding a new line/method at the end of the " +
            "file), leave originalSnippet as an empty string and put only the new code in fixedSnippet. " +
            "You can include multiple patches in the array if the fix needs changes in more than one place. " +
            "originalSnippet must always be copied character-for-character from the file content given to you " +
            "so it can be located with an exact text match, and should be as small as possible while still being " +
            "uniquely identifiable in the file. " +
            "If the real fix requires creating a NEW EVENT (like an onClick handler for a button) or a new " +
            "custom/more block that does not exist yet in this project - something that can't be done by editing " +
            "this file's text alone - set needsManualEvent to true, leave patches as an empty array (or include " +
            "only unrelated text patches if there genuinely are any), and put a short, clear instruction in " +
            "manualEventHint telling the user exactly which event to create (event type, target view/activity, " +
            "event name) and what code to put inside it once created. " +
            "Additionally, if - and only if - the missing event is a plain ACTIVITY lifecycle event (like " +
            "onCreate, onResume, onStart, onPause, onStop, onDestroy, onBackPressed, onActivityResult, " +
            "onRequestPermissionsResult - NOT a view click/event or a custom/more block), set " +
            "createActivityEventName to that exact event name (e.g. \"onCreate\") so it can be created " +
            "automatically. For any other kind of missing event (view events, more blocks), leave " +
            "createActivityEventName as an empty string even though needsManualEvent is true. " +
            "If the missing event is instead a VIEW event (like onClick) and the view list below contains an " +
            "id you're confident this event belongs to, set createViewEventTargetId to that EXACT id from the " +
            "list (never invent one) and createViewEventName to the event name (e.g. \"onClick\"). " +
            "If you can't confidently match it to a real id from the list, leave createViewEventTargetId and " +
            "createViewEventName empty and rely on manualEventHint instead.";

    private ErrorFixTask() {}

    public static void analyze(Context context, String scId, String rawError, ErrorFixCallback callback) {
        String cleanedError = ErrorCleaner.clean(rawError);

        String filePath = extractFilePath(rawError);
        String fileContent = null;
        if (filePath != null && FileUtil.isExistFile(filePath)) {
            fileContent = FileUtil.readFile(filePath);
        }

        String viewContext = buildViewContext(scId, javaNameFromFilePath(filePath));

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Build error log:\n").append(cleanedError);
        if (fileContent != null) {
            userPrompt.append("\n\nFull content of ").append(filePath).append(":\n").append(fileContent);
        }
        if (!viewContext.isEmpty()) {
            userPrompt.append("\n\nThe current layout has exactly these views (id: type) - use ONLY these ids for createViewEventTargetId:\n").append(viewContext);
        }

        String resolvedFilePath = filePath;

        AiManager.sendPrompt(context, SYSTEM_PROMPT, userPrompt.toString(), new AiResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                try {
                    String jsonText = extractJsonObject(responseText);
                    JSONObject json = new JSONObject(jsonText);

                    ErrorFixResult result = new ErrorFixResult();
                    result.summary = json.optString("summary", "").trim();
                    result.explanation = json.optString("explanation", "").trim();
                    result.filePath = resolvedFilePath;
                    result.needsManualEvent = json.optBoolean("needsManualEvent", false);
                    result.manualEventHint = json.optString("manualEventHint", "").trim();
                    result.createActivityEventName = json.optString("createActivityEventName", "").trim();
                    result.createViewEventTargetId = json.optString("createViewEventTargetId", "").trim();
                    result.createViewEventName = json.optString("createViewEventName", "").trim();

                    String liveContent = (resolvedFilePath != null && FileUtil.isExistFile(resolvedFilePath))
                            ? FileUtil.readFile(resolvedFilePath)
                            : null;

                    JSONArray patchesJson = json.optJSONArray("patches");
                    boolean allApplicable = liveContent != null && patchesJson != null && patchesJson.length() > 0;

                    if (patchesJson != null) {
                        for (int i = 0; i < patchesJson.length(); i++) {
                            JSONObject patchJson = patchesJson.getJSONObject(i);
                            ErrorFixResult.Patch patch = new ErrorFixResult.Patch();
                            patch.originalSnippet = patchJson.optString("originalSnippet", "");
                            patch.fixedSnippet = patchJson.optString("fixedSnippet", "");

                            if (liveContent == null) {
                                patch.applicable = false;
                            } else if (patch.originalSnippet.isEmpty()) {
                                patch.applicable = !patch.fixedSnippet.isEmpty();
                            } else {
                                patch.applicable = countOccurrences(liveContent, patch.originalSnippet) == 1;
                            }

                            if (!patch.applicable) allApplicable = false;
                            result.patches.add(patch);
                        }
                    }

                    result.patchable = allApplicable;

                    callback.onResult(result);
                } catch (Exception e) {
                    callback.onError("Could not parse AI response: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    public static boolean applyFix(ErrorFixResult result) {
        if (result == null || !result.patchable || result.filePath == null) return false;

        String content = FileUtil.readFile(result.filePath);
        if (content == null) return false;

        for (ErrorFixResult.Patch patch : result.patches) {
            if (patch.originalSnippet == null || patch.originalSnippet.isEmpty()) {
                if (!content.endsWith("\n")) content = content + "\n";
                content = content + patch.fixedSnippet;
            } else {
                if (countOccurrences(content, patch.originalSnippet) != 1) return false;
                content = content.replace(patch.originalSnippet, patch.fixedSnippet == null ? "" : patch.fixedSnippet);
            }
        }

        FileUtil.writeFile(result.filePath, content);
        return true;
    }

    public static String javaNameFromFilePath(String filePath) {
        if (filePath == null) return null;
        String fileName = filePath.substring(Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1);
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static String buildViewContext(String scId, String javaName) {
        if (scId == null || javaName == null) return "";
        try {
            com.besome.sketch.beans.ProjectFileBean projectFile = a.a.a.jC.b(scId).a(javaName);
            if (projectFile == null) return "";
            java.util.ArrayList<com.besome.sketch.beans.ViewBean> views = a.a.a.jC.a(scId).d(projectFile.getXmlName());
            if (views == null) return "";
            StringBuilder sb = new StringBuilder();
            for (com.besome.sketch.beans.ViewBean view : views) {
                if (view.id == null || view.id.isEmpty()) continue;
                sb.append(view.id).append(": ").append(com.besome.sketch.beans.ViewBean.getViewTypeName(view.type)).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractFilePath(String rawError) {
        if (rawError == null) return null;
        Matcher matcher = JAVA_FILE_LINE_PATTERN.matcher(rawError);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new IllegalArgumentException("No JSON object found in AI response");
        }
        return text.substring(start, end + 1);
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        int count = 0;
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
