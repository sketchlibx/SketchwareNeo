package neo.sketchware.ai;

import android.content.Context;

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
            "\"originalSnippet\":\"the exact original lines copied verbatim from the provided file content that need to change, or empty string if no file content was provided or you cannot pinpoint it\"," +
            "\"fixedSnippet\":\"the corrected replacement for originalSnippet only, same surrounding style, or empty string if originalSnippet is empty\"}" +
            " originalSnippet must be copied character-for-character from the file content given to you, so it can be located with an exact text match. " +
            "Keep originalSnippet as small as possible while still being uniquely identifiable in the file.";

    private ErrorFixTask() {}

    public static void analyze(Context context, String rawError, ErrorFixCallback callback) {
        String cleanedError = ErrorCleaner.clean(rawError);

        String filePath = extractFilePath(rawError);
        String fileContent = null;
        if (filePath != null && FileUtil.isExistFile(filePath)) {
            fileContent = FileUtil.readFile(filePath);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Build error log:\n").append(cleanedError);
        if (fileContent != null) {
            userPrompt.append("\n\nFull content of ").append(filePath).append(":\n").append(fileContent);
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
                    result.originalSnippet = json.optString("originalSnippet", "").trim();
                    result.fixedSnippet = json.optString("fixedSnippet", "").trim();
                    result.filePath = resolvedFilePath;

                    result.patchable = resolvedFilePath != null
                            && !result.originalSnippet.isEmpty()
                            && FileUtil.isExistFile(resolvedFilePath)
                            && countOccurrences(FileUtil.readFile(resolvedFilePath), result.originalSnippet) == 1;

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
        if (result == null || !result.patchable) return false;

        String content = FileUtil.readFile(result.filePath);
        if (countOccurrences(content, result.originalSnippet) != 1) return false;

        String patched = content.replace(result.originalSnippet, result.fixedSnippet);
        FileUtil.writeFile(result.filePath, patched);
        return true;
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
