package neo.sketchware.ai;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.wq;

/** AI error analysis with validated, multi-file patches and rollback-safe writes. */
public final class ErrorFixTask {

    private static final Pattern FILE_LINE_PATTERN = Pattern.compile(
            "([\\w./\\\\:-]+\\.(?:java|kt|xml|gradle|kts|pro|json|txt)):(\\d+)(?::|\\s)",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_CONTEXT_FILES = 18;
    private static final int MAX_FILE_CHARS = 14000;
    private static final int MAX_CONTEXT_CHARS = 85000;
    private static final long MAX_PATCH_CHARS = 180000L;
    private static final String[] ALLOWED_DIRS = {
            "files/java", "files/resource", "files/assets", "custom_java", "custom_gradle",
            "Injection/androidmanifest", "java", "service", "broadcast"
    };
    private static final String[] ALLOWED_EXTENSIONS = {
            ".java", ".kt", ".xml", ".gradle", ".kts", ".pro", ".json", ".txt"
    };

    private static final String SYSTEM_PROMPT =
            "You are an expert Android/Java build-error fixer working inside Sketchware Neo. " +
            "The host supplies an error log and a bounded set of real project files identified by exact project-relative paths. " +
            "Return ONLY one JSON object (no Markdown) in this shape: " +
            "{\"summary\":\"one sentence\",\"explanation\":\"brief reason\",\"patches\":[{" +
            "\"filePath\":\"exact supplied relative path\",\"originalSnippet\":\"exact existing text or empty for addition\",\"fixedSnippet\":\"replacement or new text\"}]," +
            "\"needsManualEvent\":false,\"manualEventHint\":\"\",\"createActivityEventName\":\"\",\"createViewEventTargetId\":\"\",\"createViewEventName\":\"\"}. " +
            "Rules: choose filePath ONLY from the supplied Project files list, or use a safe new file under files/resource/values, " +
            "files/resource/layout, files/java, custom_java or custom_gradle when creating a necessary file. Never use absolute paths or '..'. " +
            "For replacements, originalSnippet must be copied character-for-character from that exact file and be unique. " +
            "A pure addition uses an empty originalSnippet; fixedSnippet must contain the complete new content/lines. " +
            "Prefer minimal changes and include every file needed for the fix. Do not invent file contents that were not supplied. " +
            "If a real fix requires a view/custom event which cannot be created by changing source text alone, set needsManualEvent=true and give exact instructions. " +
            "Only request createActivityEventName for a standard Activity lifecycle event. " +
            "Only request createViewEventTargetId when that exact id is present in the supplied view list. " +
            "Do not claim that the project was built or verified.";

    private ErrorFixTask() { }

    public static void analyze(Context context, String scId, String rawError, ErrorFixCallback callback) {
        if (callback == null) return;
        if (context == null || TextUtils.isEmpty(scId) || TextUtils.isEmpty(rawError)) {
            callback.onError("A project and build error log are required.");
            return;
        }

        File projectRoot;
        try {
            projectRoot = new File(wq.b(scId)).getCanonicalFile();
        } catch (Exception e) {
            callback.onError("Could not resolve the project's data folder.");
            return;
        }

        List<File> projectFiles = collectProjectFiles(projectRoot);
        String primaryPath = resolvePrimaryPath(projectRoot, projectFiles, rawError);
        LinkedHashMap<String, File> contextFiles = chooseContextFiles(projectRoot, projectFiles, rawError, primaryPath);
        String viewContext = buildViewContext(scId, javaNameFromFilePath(primaryPath));
        StringBuilder userPrompt = new StringBuilder(16000);
        userPrompt.append("Build error log:\n").append(ErrorCleaner.clean(rawError));
        if (!viewContext.isEmpty()) {
            userPrompt.append("\n\nCurrent layout view ids (id: type). Only use these for createViewEventTargetId:\n")
                    .append(viewContext);
        }
        userPrompt.append("\n\nProject files (paths are relative to the project data folder; only these existing files may be edited):\n");
        for (Map.Entry<String, File> entry : contextFiles.entrySet()) {
            userPrompt.append("- ").append(entry.getKey()).append('\n');
        }
        int remaining = MAX_CONTEXT_CHARS;
        for (Map.Entry<String, File> entry : contextFiles.entrySet()) {
            if (remaining <= 0) break;
            String content = readUtf8(entry.getValue());
            if (content == null) continue;
            if (content.length() > MAX_FILE_CHARS) content = content.substring(0, MAX_FILE_CHARS) + "\n[File content truncated for analysis]";
            if (content.length() > remaining) content = content.substring(0, remaining) + "\n[Context limit reached]";
            userPrompt.append("\n--- FILE: ").append(entry.getKey()).append(" ---\n")
                    .append(content).append('\n');
            remaining -= content.length();
        }
        if (contextFiles.isEmpty()) {
            userPrompt.append("\nNo readable source files were found. You may recommend a safe minimal new file only if the error log clearly identifies what is missing.");
        }

        final File fixedProjectRoot = projectRoot;
        final String resolvedPrimaryPath = primaryPath;
        final LinkedHashMap<String, File> knownFiles = contextFiles;
        AiManager.sendPrompt(context, SYSTEM_PROMPT, userPrompt.toString(), AiManager.Feature.ERROR_FIX, new AiResponseCallback() {
            @Override public void onSuccess(String responseText) {
                try {
                    JSONObject json = new JSONObject(extractJsonObject(responseText));
                    ErrorFixResult result = new ErrorFixResult();
                    result.summary = json.optString("summary", "").trim();
                    result.explanation = json.optString("explanation", "").trim();
                    result.filePath = resolvedPrimaryPath;
                    result.projectRootPath = fixedProjectRoot.getCanonicalPath();
                    result.projectId = scId;
                    result.needsManualEvent = json.optBoolean("needsManualEvent", false);
                    result.manualEventHint = json.optString("manualEventHint", "").trim();
                    result.createActivityEventName = json.optString("createActivityEventName", "").trim();
                    result.createViewEventTargetId = json.optString("createViewEventTargetId", "").trim();
                    result.createViewEventName = json.optString("createViewEventName", "").trim();

                    JSONArray patches = json.optJSONArray("patches");
                    boolean allApplicable = patches != null && patches.length() > 0;
                    long totalPatchChars = 0;
                    for (int i = 0; patches != null && i < patches.length(); i++) {
                        JSONObject rawPatch = patches.optJSONObject(i);
                        if (rawPatch == null) { allApplicable = false; continue; }
                        ErrorFixResult.Patch patch = new ErrorFixResult.Patch();
                        String aiPath = rawPatch.optString("filePath", "").trim();
                        if (aiPath.isEmpty()) aiPath = resolvedPrimaryPath == null ? "" : relativePath(fixedProjectRoot, new File(resolvedPrimaryPath));
                        File target = resolvePatchTarget(fixedProjectRoot, knownFiles, aiPath);
                        patch.filePath = target == null ? null : target.getAbsolutePath();
                        patch.originalSnippet = rawPatch.optString("originalSnippet", "");
                        patch.fixedSnippet = rawPatch.optString("fixedSnippet", "");
                        totalPatchChars += (patch.originalSnippet == null ? 0 : patch.originalSnippet.length());
                        totalPatchChars += (patch.fixedSnippet == null ? 0 : patch.fixedSnippet.length());

                        String current = target == null ? null : (target.isFile() ? readUtf8(target) : null);
                        boolean isNewFile = target != null && !target.exists();
                        if (patch.filePath == null || patch.fixedSnippet == null || patch.fixedSnippet.isEmpty()) {
                            patch.applicable = false;
                        } else if (isNewFile) {
                            patch.applicable = patch.originalSnippet == null || patch.originalSnippet.isEmpty();
                        } else if (current == null) {
                            patch.applicable = false;
                        } else if (patch.originalSnippet == null || patch.originalSnippet.isEmpty()) {
                            patch.applicable = true;
                        } else {
                            patch.applicable = countOccurrences(current, patch.originalSnippet) == 1;
                        }
                        if (!patch.applicable) allApplicable = false;
                        result.patches.add(patch);
                    }
                    if (totalPatchChars > MAX_PATCH_CHARS || result.patches.isEmpty()) allApplicable = false;
                    result.patchable = allApplicable;
                    callback.onResult(result);
                } catch (Exception e) {
                    callback.onError("Could not parse the AI's proposed changes: " + safeMessage(e));
                }
            }

            @Override public void onFailure(String errorMessage) { callback.onError(errorMessage); }
        });
    }

    /** Applies every patch or restores all files if any commit fails. */
    public static boolean applyFix(ErrorFixResult result) {
        if (result == null || !result.patchable || result.patches == null || result.patches.isEmpty()) return false;
        LinkedHashMap<File, List<ErrorFixResult.Patch>> byFile = new LinkedHashMap<>();
        final File projectRoot;
        try {
            if (TextUtils.isEmpty(result.projectRootPath)) return false;
            projectRoot = new File(result.projectRootPath).getCanonicalFile();
            if (!projectRoot.isDirectory()) return false;
        } catch (Exception e) { return false; }
        for (ErrorFixResult.Patch patch : result.patches) {
            if (patch == null || !patch.applicable || TextUtils.isEmpty(patch.filePath)) return false;
            try {
                File target = new File(patch.filePath).getCanonicalFile();
                if (!isSupportedTargetFile(projectRoot, target) || !isInside(target, projectRoot)
                        || !isAllowedWriteTarget(projectRoot, target)) return false;
                if (Files.isSymbolicLink(target.toPath())) return false;
                if (!target.exists() && (patch.originalSnippet != null && !patch.originalSnippet.isEmpty())) return false;
                List<ErrorFixResult.Patch> list = byFile.get(target);
                if (list == null) { list = new ArrayList<>(); byFile.put(target, list); }
                list.add(patch);
            } catch (Exception e) { return false; }
        }

        LinkedHashMap<File, String> before = new LinkedHashMap<>();
        LinkedHashMap<File, String> after = new LinkedHashMap<>();
        long total = 0;
        try {
            for (Map.Entry<File, List<ErrorFixResult.Patch>> entry : byFile.entrySet()) {
                File target = entry.getKey();
                if (target.exists() && (!target.isFile() || Files.isSymbolicLink(target.toPath()))) return false;
                File parent = target.getParentFile();
                if (parent == null || !isInside(parent.getCanonicalFile(), projectRoot)
                        || !isAllowedWriteTarget(projectRoot, target)) return false;
                String original = target.exists() ? readUtf8(target) : "";
                if (original == null) return false;
                before.put(target, original);
                String updated = original;
                for (ErrorFixResult.Patch patch : entry.getValue()) {
                    String old = patch.originalSnippet == null ? "" : patch.originalSnippet;
                    String replacement = patch.fixedSnippet == null ? "" : patch.fixedSnippet;
                    if (old.isEmpty()) {
                        if (!updated.isEmpty() && !updated.endsWith("\n") && !replacement.startsWith("\n")) updated += "\n";
                        if (target.exists() || !updated.isEmpty()) updated += replacement;
                        else updated = replacement;
                    } else {
                        if (countOccurrences(updated, old) != 1) return false;
                        updated = updated.replace(old, replacement);
                    }
                }
                total += updated.length();
                if (total > MAX_PATCH_CHARS) return false;
                after.put(target, updated);
            }
        } catch (Exception e) { return false; }

        LinkedHashMap<File, File> temps = new LinkedHashMap<>();
        LinkedHashMap<File, File> backups = new LinkedHashMap<>();
        List<File> committed = new ArrayList<>();
        try {
            for (Map.Entry<File, String> entry : after.entrySet()) {
                File target = entry.getKey();
                File parent = target.getParentFile();
                if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory()) throw new IllegalStateException("Cannot create target folder");
                File temp = new File(parent, target.getName() + ".autofix.tmp");
                File backup = new File(parent, target.getName() + ".autofix.bak");
                if (temp.exists() && !temp.delete()) throw new IllegalStateException("Cannot clear temporary file");
                if (backup.exists() && !backup.delete()) throw new IllegalStateException("Cannot clear previous backup");
                try (FileOutputStream stream = new FileOutputStream(temp)) {
                    stream.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                    stream.getFD().sync();
                }
                temps.put(target, temp);
                if (target.exists()) backups.put(target, backup);
            }
            for (File target : after.keySet()) {
                File backup = backups.get(target);
                if (backup != null) move(target, backup, false);
                try {
                    move(temps.get(target), target, false);
                    committed.add(target);
                } catch (Exception installError) {
                    if (backup != null && backup.exists()) move(backup, target, false);
                    throw installError;
                }
            }
            for (File backup : backups.values()) if (backup.exists()) backup.delete();
            return true;
        } catch (Exception e) {
            Collections.reverse(committed);
            for (File target : committed) {
                try { if (target.exists() && !target.delete()) continue; } catch (Exception ignored) { }
                File backup = backups.get(target);
                try { if (backup != null && backup.exists()) move(backup, target, false); } catch (Exception ignored) { }
            }
            // Also restore a target moved to backup immediately before an install failure.
            for (Map.Entry<File, File> entry : backups.entrySet()) {
                if (!entry.getKey().exists() && entry.getValue().exists()) {
                    try { move(entry.getValue(), entry.getKey(), false); } catch (Exception ignored) { }
                }
            }
            for (File temp : temps.values()) if (temp.exists()) temp.delete();
            return false;
        } finally {
            for (File temp : temps.values()) if (temp.exists()) temp.delete();
            // Successful commits remove backups; after a rollback, any leftover backup is kept
            // rather than deleting the only surviving copy of a source file.
        }
    }

    public static String javaNameFromFilePath(String filePath) {
        if (filePath == null) return null;
        String fileName = filePath.substring(Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1);
        return fileName.endsWith(".java") ? fileName.substring(0, fileName.length() - 5) : fileName;
    }

    private static String buildViewContext(String scId, String javaName) {
        if (TextUtils.isEmpty(scId) || TextUtils.isEmpty(javaName) || !javaName.endsWith("Activity")) return "";
        try {
            com.besome.sketch.beans.ProjectFileBean projectFile = a.a.a.jC.b(scId).a(javaName);
            if (projectFile == null) return "";
            ArrayList<com.besome.sketch.beans.ViewBean> views = a.a.a.jC.a(scId).d(projectFile.getXmlName());
            if (views == null) return "";
            StringBuilder sb = new StringBuilder();
            for (com.besome.sketch.beans.ViewBean view : views) {
                if (view.id == null || view.id.isEmpty()) continue;
                sb.append(view.id).append(": ").append(com.besome.sketch.beans.ViewBean.getViewTypeName(view.type)).append('\n');
            }
            return sb.toString();
        } catch (Exception ignored) { return ""; }
    }

    private static List<File> collectProjectFiles(File root) {
        ArrayList<File> files = new ArrayList<>();
        if (root == null || !root.isDirectory()) return files;
        for (String relative : ALLOWED_DIRS) collectRecursively(new File(root, relative), root, files, 500);
        String[] topLevel = {"custom_manifest.xml", "proguard-rules.pro", "build_config", "project_config"};
        for (String relative : topLevel) {
            File file = new File(root, relative);
            if (file.isFile() && isSupportedTargetFile(root, file)) files.add(file);
        }
        files.sort(Comparator.comparing(File::getAbsolutePath));
        return files;
    }

    private static void collectRecursively(File dir, File root, List<File> out, int maxFiles) {
        if (dir == null || !dir.isDirectory() || Files.isSymbolicLink(dir.toPath()) || out.size() >= maxFiles) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        java.util.Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            if (out.size() >= maxFiles) return;
            try {
                if (!isInside(child.getCanonicalFile(), root.getCanonicalFile()) || Files.isSymbolicLink(child.toPath())) continue;
                if (child.isDirectory()) collectRecursively(child, root, out, maxFiles);
                else if (child.isFile() && hasAllowedExtension(child.getName()) && child.length() <= 512 * 1024) out.add(child);
            } catch (Exception ignored) { }
        }
    }

    private static LinkedHashMap<String, File> chooseContextFiles(File root, List<File> files, String log, String primary) {
        LinkedHashMap<String, File> result = new LinkedHashMap<>();
        ArrayList<File> ordered = new ArrayList<>();
        if (primary != null) {
            File file = new File(primary);
            for (File candidate : files) if (sameFile(candidate, file)) { ordered.add(candidate); break; }
        }
        String lowerLog = log == null ? "" : log.toLowerCase(Locale.ROOT);
        for (File file : files) {
            if (ordered.contains(file)) continue;
            String relative = relativePath(root, file);
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (lowerLog.contains(name) || lowerLog.contains(relative.toLowerCase(Locale.ROOT))) ordered.add(file);
        }
        for (File file : files) {
            if (ordered.contains(file)) continue;
            String relative = relativePath(root, file).toLowerCase(Locale.ROOT);
            if (relative.contains("/java/") || relative.contains("custom_java/") || relative.contains("/layout/") || relative.contains("/values/") || relative.contains("custom_manifest")) ordered.add(file);
        }
        for (File file : files) {
            if (ordered.size() >= MAX_CONTEXT_FILES) break;
            if (!ordered.contains(file)) ordered.add(file);
        }
        int count = 0;
        for (File file : ordered) {
            if (count >= MAX_CONTEXT_FILES) break;
            String rel = relativePath(root, file);
            if (!rel.isEmpty()) { result.put(rel, file); count++; }
        }
        return result;
    }

    private static String resolvePrimaryPath(File root, List<File> files, String log) {
        if (log == null) return null;
        Matcher matcher = FILE_LINE_PATTERN.matcher(log);
        while (matcher.find()) {
            String token = matcher.group(1);
            try {
                File candidate = new File(token);
                if (!candidate.isAbsolute()) candidate = new File(root, token);
                if (candidate.isFile() && isInside(candidate.getCanonicalFile(), root.getCanonicalFile())) return candidate.getCanonicalPath();
                String basename = new File(token.replace('\\', '/')).getName();
                ArrayList<File> matches = new ArrayList<>();
                for (File file : files) if (file.getName().equalsIgnoreCase(basename)) matches.add(file);
                if (matches.size() == 1) return matches.get(0).getCanonicalPath();
                String normalized = token.replace('\\', '/').toLowerCase(Locale.ROOT);
                for (File file : matches) {
                    String rel = relativePath(root, file).toLowerCase(Locale.ROOT);
                    if (normalized.endsWith(rel)) return file.getCanonicalPath();
                }
            } catch (Exception ignored) { }
        }
        return null;
    }

    private static File resolvePatchTarget(File root, Map<String, File> knownFiles, String aiPath) {
        if (TextUtils.isEmpty(aiPath) || aiPath.startsWith("/") || aiPath.contains("\\") || aiPath.contains(":") || aiPath.contains("..")) return null;
        String normalized = aiPath.replaceFirst("^\\./", "");
        File known = knownFiles.get(normalized);
        if (known != null) return known;
        try {
            File target = new File(root, normalized).getCanonicalFile();
            if (!isInside(target, root.getCanonicalFile()) || !hasAllowedExtension(target.getName()) || !isAllowedWriteDirectory(root, target.getParentFile())) return null;
            // New targets are permitted only when the model names a file in a reviewed project resource/source directory.
            if (!target.exists()) {
                File parent = target.getParentFile();
                if (parent == null || !parent.isDirectory()) return null;
                return target;
            }
            for (File file : knownFiles.values()) if (sameFile(file, target)) return file;
            return null;
        } catch (Exception ignored) { return null; }
    }

    private static boolean isAllowedWriteTarget(File root, File target) {
        if (root == null || target == null) return false;
        if (isAllowedWriteDirectory(root, target.getParentFile())) return true;
        try {
            String relative = relativePath(root, target).replace('\\', '/');
            return relative.indexOf('/') < 0 && ("custom_manifest.xml".equals(relative)
                    || "proguard-rules.pro".equals(relative) || "build_config".equals(relative)
                    || "project_config".equals(relative));
        } catch (Exception ignored) { return false; }
    }

    private static boolean isAllowedWriteDirectory(File root, File parent) {
        if (root == null || parent == null) return false;
        try {
            String rel = relativePath(root, parent).replace('\\', '/');
            for (String allowed : ALLOWED_DIRS) if (rel.equals(allowed) || rel.startsWith(allowed + "/")) return true;
            return false;
        } catch (Exception ignored) { return false; }
    }


    private static boolean isSupportedTargetFile(File root, File target) {
        if (target == null) return false;
        if (hasAllowedExtension(target.getName())) return true;
        try {
            String relative = relativePath(root, target).replace('\\', '/');
            return relative.indexOf('/') < 0 && ("build_config".equals(relative) || "project_config".equals(relative));
        } catch (Exception ignored) { return false; }
    }

    private static boolean hasAllowedExtension(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : ALLOWED_EXTENSIONS) if (lower.endsWith(extension)) return true;
        return false;
    }

    private static String relativePath(File root, File file) {
        try {
            String rel = root.getCanonicalFile().toPath().relativize(file.getCanonicalFile().toPath()).toString();
            return rel.replace(File.separatorChar, '/');
        } catch (Exception ignored) { return ""; }
    }

    private static boolean isInside(File child, File parent) {
        try {
            String root = parent.getCanonicalPath();
            String path = child.getCanonicalPath();
            return path.equals(root) || path.startsWith(root + File.separator);
        } catch (Exception ignored) { return false; }
    }

    private static boolean sameFile(File left, File right) {
        try { return left.getCanonicalFile().equals(right.getCanonicalFile()); } catch (Exception ignored) { return false; }
    }

    private static String readUtf8(File file) {
        if (file == null || !file.isFile() || file.length() > 1024 * 1024) return null;
        try { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
        catch (Exception ignored) { return null; }
    }

    private static void move(File source, File target, boolean replace) throws Exception {
        if (replace) Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        else {
            try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(source.toPath(), target.toPath()); }
        }
    }

    private static int countOccurrences(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return 0;
        int count = 0, index = 0;
        while ((index = haystack.indexOf(needle, index)) != -1) { count++; index += needle.length(); }
        return count;
    }

    private static String extractJsonObject(String text) {
        if (text == null) throw new IllegalArgumentException("AI returned an empty response");
        String trimmed = text.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end < start) throw new IllegalArgumentException("No JSON object found in the AI response");
        return trimmed.substring(start, end + 1);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }
}
