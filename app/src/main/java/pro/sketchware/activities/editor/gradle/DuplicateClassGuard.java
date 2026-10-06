package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import dev.aldi.sayuti.editor.manage.LocalLibrariesUtil;
import mod.jbk.build.BuiltInLibraries;
import mod.sketchlibx.importer.GradleDependency;

public final class DuplicateClassGuard {

    private static final int MAX_PAIRS_REPORTED = 5;

    private DuplicateClassGuard() {
    }

    @Nullable
    public static String find(@NonNull String scId, @NonNull List<GradleDependency> declared) {
        Map<String, File> jars = new LinkedHashMap<>();
        for (HashMap<String, Object> library : LocalLibrariesUtil.getLocalLibraries(scId)) {
            Object name = library.get("name");
            Object jar = library.get("jarPath");
            if (name != null && jar instanceof String) jars.put("local:" + name, new File((String) jar));
        }
        Set<String> builtIns = new LinkedHashSet<>();
        for (GradleDependency dependency : declared) {
            if (!GradleSyncEngine.isConsumedConfiguration(dependency.configuration)) continue;
            BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(scId, dependency.group, dependency.artifact, dependency.version);
            if (match != null) builtIns.addAll(BuiltInArtifacts.closure(match.libraryName));
        }
        for (String name : builtIns) {
            File jar = BuiltInLibraries.getLibraryClassesJarPath(name);
            if (jar.isFile()) jars.put("built-in:" + name, jar);
        }

        Map<String, String> owner = new HashMap<>();
        Map<String, Integer> pairCounts = new LinkedHashMap<>();
        Map<String, String> pairSample = new HashMap<>();
        for (Map.Entry<String, File> entry : jars.entrySet()) {
            Set<String> classes = classNames(entry.getValue());
            for (String className : classes) {
                String first = owner.putIfAbsent(className, entry.getKey());
                if (first != null && !first.equals(entry.getKey())) {
                    String pair = first + " | " + entry.getKey();
                    pairCounts.merge(pair, 1, Integer::sum);
                    pairSample.putIfAbsent(pair, className);
                }
            }
        }
        if (pairCounts.isEmpty()) return null;

        StringBuilder message = new StringBuilder("Duplicate classes would break dexing:");
        int shown = 0;
        for (Map.Entry<String, Integer> pair : pairCounts.entrySet()) {
            if (shown++ == MAX_PAIRS_REPORTED) {
                message.append("\n  and ").append(pairCounts.size() - MAX_PAIRS_REPORTED).append(" more overlapping pair(s)");
                break;
            }
            String[] parts = pair.getKey().split(" \\| ");
            message.append("\n  ").append(parts[0]).append(" and ").append(parts[1]).append(" both contain ")
                    .append(pair.getValue()).append(" class(es), e.g. ").append(pairSample.get(pair.getKey()).replace('/', '.'));
        }
        message.append("\nUse a single version of each library, or remove one of the overlapping libraries.");
        return message.toString();
    }

    @NonNull
    private static Set<String> classNames(File jar) {
        if (!jar.isFile()) return Collections.emptySet();
        Set<String> names = new HashSet<>();
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (entry.isDirectory() || !name.endsWith(".class") || name.startsWith("META-INF/")
                        || name.equals("module-info.class") || name.endsWith("/module-info.class")) {
                    continue;
                }
                names.add(name);
            }
        } catch (Exception e) {
            return Collections.emptySet();
        }
        return names;
    }
}
