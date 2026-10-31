package mod.sketchlibx.importer;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Read-only preflight inspection for ZIP Android Studio projects. */
public final class AndroidStudioProjectInspector {
    private static final String TAG = "ASProjectInspector";
    private static final int MAX_ENTRIES = 50000;
    private static final long MAX_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L;

    private AndroidStudioProjectInspector() {}

    public static final class Report {
        public boolean valid;
        public String error = "";
        public String projectName = "Android Studio Project";
        public String repositoryUrl = "";
        public String appModule = "";
        public String packageName = "com.imported.project";
        public String versionName = "1.0";
        public int versionCode = 1;
        public int compileSdk = 34;
        public int minSdk = 21;
        public int targetSdk = 34;
        public String javaVersion = "1.8";
        public boolean viewBinding;
        public int activities;
        public int layouts;
        public int drawables;
        public int classes;
        public int drawers;
        public int fragments;
        public int services;
        public int receivers;
        public final List<String> dependencies = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
    }

    public static Report inspect(Context context, File zipFile, String displayName) {
        Report report = new Report();
        report.projectName = safeDisplayName(displayName, zipFile == null ? "Android Studio Project" : zipFile.getName());
        if (zipFile == null || !zipFile.isFile() || zipFile.length() <= 0) {
            report.error = "Selected ZIP file is missing or empty.";
            return report;
        }

        File temp = new File(context.getCacheDir(), "as_inspect_" + System.nanoTime());
        if (!temp.mkdirs() && !temp.isDirectory()) {
            report.error = "Could not create a temporary inspection folder.";
            return report;
        }

        try {
            extractSecurely(zipFile, temp);
            ModuleDetector.DetectionResult detected = new ModuleDetector().detect(temp);
            if (detected == null || detected.appModuleDir == null) {
                report.error = "No Android application module was detected. Check settings.gradle and the app module's plugins.";
                return report;
            }
            File app = detected.appModuleDir;
            File srcMain = new File(app, "src/main");
            report.appModule = relativeTo(detected.projectRoot, app);

            ImportValidator validator = new ImportValidator();
            if (!validator.validate(detected.projectRoot, app)) {
                report.error = validator.getFailReason();
                return report;
            }

            ParsedManifest manifest = new ManifestParser(srcMain).parse();
            ParsedGradle gradle = new GradleParser().parse(app);
            if (manifest.appName == null || manifest.appName.trim().isEmpty()
                    || manifest.appName.startsWith("@") || "Imported App".equals(manifest.appName.trim())) {
                if (gradle.appName != null && !gradle.appName.trim().isEmpty() && !gradle.appName.startsWith("@")) {
                    report.projectName = gradle.appName.trim();
                } else {
                    report.projectName = humanizeProjectName(displayName == null ? zipFile.getName() : displayName);
                }
            } else {
                report.projectName = manifest.appName.trim();
            }
            String gradleApplicationId = gradle.applicationId;
            if (gradleApplicationId == null || gradleApplicationId.trim().isEmpty()
                    || "com.imported.project".equals(gradleApplicationId.trim())) {
                gradleApplicationId = firstNonEmpty(gradle.namespace, manifest.packageName);
            }
            report.packageName = firstNonEmpty(gradleApplicationId, gradle.namespace, manifest.packageName);
            report.versionCode = gradle.versionCode;
            report.versionName = gradle.versionName;
            report.compileSdk = gradle.compileSdk;
            report.minSdk = gradle.minSdk;
            report.targetSdk = gradle.targetSdk;
            report.javaVersion = gradle.javaVersion == null ? "Not detected" : gradle.javaVersion;
            report.viewBinding = gradle.viewBindingEnabled;
            report.activities = manifest.activities.size();
            report.fragments = manifest.activities.isEmpty() ? 0 : countSourceTypes(srcMain, "Fragment");
            report.services = manifest.services.size();
            report.receivers = manifest.receivers.size();

            File res = new File(srcMain, "res");
            report.layouts = countFilesWithExtension(new File(res, "layout"), ".xml", true);
            report.drawables = countFilesWithExtension(res, ".png", false, "drawable", "mipmap")
                    + countFilesWithExtension(res, ".webp", false, "drawable", "mipmap")
                    + countFilesWithExtension(res, ".jpg", false, "drawable", "mipmap")
                    + countFilesWithExtension(res, ".jpeg", false, "drawable", "mipmap")
                    + countFilesWithExtension(res, ".xml", false, "drawable", "mipmap");
            report.classes = countSourceFiles(new File(srcMain, "java")) + countSourceFiles(new File(srcMain, "kotlin"));
            report.drawers = countDrawerLayouts(new File(res, "layout"));

            File gradleFile = new File(app, "build.gradle");
            if (!gradleFile.isFile()) gradleFile = new File(app, "build.gradle.kts");
            if (gradleFile.isFile()) {
                String content = new String(Files.readAllBytes(gradleFile.toPath()), StandardCharsets.UTF_8);
                for (GradleDependency dependency : new GradleParser().parseDependencyList(content)) {
                    report.dependencies.add(dependency.coordinate());
                }
                // Keep catalog aliases visible rather than pretending we resolved them to concrete coordinates.
                java.util.regex.Matcher aliases = java.util.regex.Pattern.compile("(?m)^\\s*(?:implementation|api|runtimeOnly|compileOnly|kapt|ksp)\\s*\\(?\\s*(libs\\.[A-Za-z0-9_.-]+)").matcher(content);
                while (aliases.find()) report.dependencies.add("Version Catalog: " + aliases.group(1));
            }
            java.util.Collections.sort(report.dependencies, String.CASE_INSENSITIVE_ORDER);
            report.valid = true;
            if (gradle.hasKotlin) report.warnings.add("Kotlin source/build plugins detected; Kotlin-to-native-block conversion is not supported. Original source will be preserved.");
            if (report.fragments > 0) report.warnings.add("Fragments are counted and copied, but Sketchware screens do not map one-to-one to Android fragments.");
            if (report.services + report.receivers > 0) report.warnings.add("Manifest services/receivers may require manual registration in Sketchware Neo.");
            if (report.dependencies.isEmpty()) report.warnings.add("No direct Maven dependencies were parsed. Version-catalog, convention-plugin and variable-based declarations may need manual review.");
            return report;
        } catch (Exception e) {
            Log.e(TAG, "Inspection failed", e);
            report.error = "Project inspection failed: " + safeMessage(e);
            return report;
        } finally {
            deleteRecursively(temp);
        }
    }

    private static void extractSecurely(File zip, File dest) throws IOException {
        byte[] buffer = new byte[16384];
        int entries = 0;
        long total = 0;
        java.util.Set<String> seenPaths = new java.util.HashSet<>();
        String root = dest.getCanonicalPath() + File.separator;
        try (ZipInputStream in = new ZipInputStream(new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                checkInspectionCancelled();
                if (++entries > MAX_ENTRIES) throw new IOException("ZIP contains too many entries (limit " + MAX_ENTRIES + ").");
                String name = entry.getName();
                if (name == null || name.trim().isEmpty() || name.startsWith("/") || name.startsWith("\\") || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("ZIP contains an unsafe entry path.");
                }
                for (String segment : name.replace('\\', '/').split("/")) {
                    if ("..".equals(segment) || ".".equals(segment)) throw new IOException("ZIP contains a traversal path segment.");
                }
                File out = new File(dest, name);
                String canonical = out.getCanonicalPath();
                if (!canonical.startsWith(root)) throw new IOException("ZIP path escapes the extraction directory.");
                if (!seenPaths.add(canonical)) throw new IOException("ZIP contains duplicate entry paths.");
                if (entry.isDirectory()) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new IOException("Cannot create extraction folder.");
                } else {
                    File parent = out.getParentFile();
                    if (parent != null && !parent.mkdirs() && !parent.isDirectory()) throw new IOException("Cannot create extraction folder.");
                    try (FileOutputStream fos = new FileOutputStream(out)) {
                        int len;
                        while ((len = in.read(buffer)) != -1) {
                            checkInspectionCancelled();
                            total += len;
                            if (total > MAX_UNCOMPRESSED_BYTES) throw new IOException("ZIP expands beyond the 1 GB inspection limit.");
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                in.closeEntry();
            }
        }
    }

    private static String relativeTo(File root, File child) {
        try { return root.toPath().relativize(child.toPath()).toString(); }
        catch (Exception ignored) { return child.getName(); }
    }
    private static String safeDisplayName(String a, String b) {
        String s = a == null || a.trim().isEmpty() ? b : a;
        if (s == null || s.trim().isEmpty()) return "Android Studio Project";
        return s.replaceAll("(?i)\\.zip$", "").trim();
    }
    private static String humanizeProjectName(String value) {
        String clean = value == null ? "" : value.trim().replaceAll("(?i)\\.zip$", "").replaceAll("(?i)\\.git$", "");
        clean = clean.replaceAll("[_-]+", " ").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) return "Imported App";
        StringBuilder result = new StringBuilder(clean.length());
        boolean capitalize = true;
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            result.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = Character.isWhitespace(c);
        }
        return result.toString();
    }
    private static String firstNonEmpty(String... values) {
        if (values != null) for (String s : values) if (s != null && !s.trim().isEmpty()) return s.trim();
        return "com.imported.project";
    }
    private static String safeMessage(Exception e) {
        String s = e.getMessage(); return s == null || s.trim().isEmpty() ? e.getClass().getSimpleName() : s;
    }
    private static int countSourceTypes(File srcMain, String typeWord) {
        return countMatching(new File(srcMain, "java"), "(?s).*extends\\s+[A-Za-z0-9_$.]*" + typeWord + "\\b.*")
                + countMatching(new File(srcMain, "kotlin"), "(?s).*:\\s*[A-Za-z0-9_$.]*" + typeWord + "\\b.*");
    }
    private static int countMatching(File dir, String regex) {
        if (dir == null || !dir.isDirectory()) return 0;
        int n = 0; File[] list = dir.listFiles(); if (list == null) return 0;
        for (File f : list) {
            checkInspectionCancelled();
            if (f.isDirectory()) n += countMatching(f, regex);
            else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) {
                try { if (new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).matches(regex)) n++; } catch (Exception ignored) {}
            }
        } return n;
    }
    private static int countSourceFiles(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        int n = 0; File[] list = dir.listFiles(); if (list == null) return 0;
        for (File f : list) { checkInspectionCancelled(); if (f.isDirectory()) n += countSourceFiles(f); else if (f.getName().endsWith(".java") || f.getName().endsWith(".kt")) n++; }
        return n;
    }
    private static int countFilesWithExtension(File dir, String ext, boolean directOnly) { return countFilesWithExtension(dir, ext, directOnly, (String[]) null); }
    private static int countFilesWithExtension(File dir, String ext, boolean directOnly, String... directoryPrefixes) {
        if (dir == null || !dir.isDirectory()) return 0;
        int n = 0; File[] list = dir.listFiles(); if (list == null) return 0;
        for (File f : list) {
            checkInspectionCancelled();
            if (f.isDirectory()) {
                boolean include = directoryPrefixes == null;
                if (directoryPrefixes != null) for (String prefix : directoryPrefixes) if (f.getName().startsWith(prefix)) include = true;
                if (!directOnly && include) n += countFilesWithExtension(f, ext, false);
                else if (directOnly && directoryPrefixes == null) n += countFilesWithExtension(f, ext, true);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(ext)) n++;
        } return n;
    }
    private static int countDrawerLayouts(File layoutDir) {
        if (layoutDir == null || !layoutDir.isDirectory()) return 0;
        int n = 0; File[] files = layoutDir.listFiles(); if (files == null) return 0;
        for (File f : files) { checkInspectionCancelled(); if (f.isFile() && f.getName().endsWith(".xml")) {
            try { String xml = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
                if (xml.contains("DrawerLayout") || xml.contains("androidx.drawerlayout")) n++;
            } catch (Exception ignored) {}
        } } return n;
    }
    private static void checkInspectionCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.io.UncheckedIOException(new IOException("Project inspection was cancelled."));
    }
    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) { File[] children = file.listFiles(); if (children != null) for (File child : children) deleteRecursively(child); }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
