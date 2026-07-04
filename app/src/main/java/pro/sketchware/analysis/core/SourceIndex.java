package pro.sketchware.analysis.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceIndex {

    private final Map<String, String> perFileText; // absolute path -> content
    private final String combinedText;

    private SourceIndex(Map<String, String> perFileText, String combinedText) {
        this.perFileText = Collections.unmodifiableMap(perFileText);
        this.combinedText = combinedText;
    }
    public Map<String, String> getPerFileText() { return perFileText; }
    
    public String getCombinedText() { return combinedText; }

    public static SourceIndex build(ProjectContext ctx, SourceIndexCache cache, List<File> roots) {
        Map<String, String> perFile = new LinkedHashMap<>();
        StringBuilder combined = new StringBuilder();

        for (File root : roots) {
            if (root == null || !root.exists()) continue;
            walk(root, perFile, combined, cache);
        }

        return new SourceIndex(perFile, combined.toString());
    }

    private static final java.util.Set<String> INDEXED_EXTENSIONS = java.util.Set.of(
            ".java", ".kt", ".xml", ".gradle", ".pro"
    );

    private static void walk(File file, Map<String, String> perFile, StringBuilder combined, SourceIndexCache cache) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) return;
            for (File child : children) {
                walk(child, perFile, combined, cache);
            }
            return;
        }

        String name = file.getName();
        boolean indexed = false;
        for (String ext : INDEXED_EXTENSIONS) {
            if (name.endsWith(ext)) { indexed = true; break; }
        }
        if (!indexed) return;

        String path = file.getAbsolutePath();
        long fingerprint = file.lastModified();

        String content = cache.getIfUnchanged(path, fingerprint);
        if (content == null) {
            try {
                content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            } catch (IOException e) {
                content = "";
            }
            cache.put(path, fingerprint, content);
        }

        perFile.put(path, content);
        combined.append(content).append('\n');
    }
}
