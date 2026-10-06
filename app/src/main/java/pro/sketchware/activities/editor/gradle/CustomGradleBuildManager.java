package pro.sketchware.activities.editor.gradle;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import a.a.a.Jp;
import a.a.a.zy;
import mod.hey.studios.project.ProjectSettings;
import mod.sketchlibx.importer.GradleDependency;
import mod.sketchlibx.importer.GradleParser;
import mod.sketchlibx.importer.ParsedGradle;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class CustomGradleBuildManager {

    public static final String FILE_APP_BUILD = "app_build.gradle";
    public static final String FILE_BUILD = "build.gradle";
    public static final String FILE_SETTINGS = "settings.gradle";
    public static final String FILE_PROPERTIES = "gradle.properties";

    private CustomGradleBuildManager() {
    }

    public static boolean isEnabled(String scId) {
        return new ProjectSettings(scId)
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_GRADLE, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE);
    }

    public static File getDirectory(String scId) {
        return new File(new FilePathUtil().getPathCustomGradle(scId));
    }

    public static void prepareBuild(String scId) throws zy {
        if (!isEnabled(scId)) return;

        File dir = getDirectory(scId);
        File app = new File(dir, FILE_APP_BUILD);
        if (!app.isFile()) {
            throw new zy(FILE_APP_BUILD + ":0: error: Custom Gradle is enabled but " + app.getAbsolutePath()
                    + " does not exist. Open Gradle Manager to restore it, or disable Custom Gradle.");
        }

        List<GradleFileValidator.Issue> issues = GradleFileValidator.validate(dir);
        StringBuilder errors = new StringBuilder();
        for (GradleFileValidator.Issue issue : issues) {
            if (issue.severity == GradleFileValidator.Severity.ERROR) {
                errors.append(issue).append('\n');
            }
        }
        if (errors.length() > 0) {
            throw new zy("Custom Gradle configuration is invalid:\n" + errors);
        }

        GradleParser parser = new GradleParser();
        ParsedGradle parsed = parser.parseFile(app, null);
        parser.parsePropertiesInto(new File(dir, FILE_PROPERTIES), parsed);
        parser.applyToProjectBuildSettings(parsed, scId);

        List<GradleDependency> dependencies = parser.parseDependencyList(FileUtil.readFile(app.getAbsolutePath()));
        StringBuilder unresolved = new StringBuilder();
        for (GradleSyncEngine.Row row : GradleSyncEngine.inspect(scId, dependencies)) {
            if (row.isConsumed() && row.state != GradleSyncEngine.DepState.READY) {
                unresolved.append(FILE_APP_BUILD).append(":0: error: Dependency ").append(row.dep.coordinate())
                        .append(" is ").append(row.state.name().toLowerCase());
                if (row.error != null) unresolved.append(" (").append(row.error).append(')');
                else if (row.note != null) unresolved.append(" (").append(row.note).append(')');
                unresolved.append('\n');
            }
        }
        if (unresolved.length() > 0) {
            throw new zy("Custom Gradle dependencies are not synced. Open Gradle Manager and run Gradle Sync.\n" + unresolved);
        }

        String overlap = DuplicateClassGuard.find(scId, dependencies);
        if (overlap != null) {
            throw new zy(FILE_APP_BUILD + ":0: error: " + overlap);
        }
    }

    public static void addDeclaredBuiltInLibraries(String scId, BuiltInLibraryManager manager) {
        if (!isEnabled(scId)) return;
        File app = new File(getDirectory(scId), FILE_APP_BUILD);
        if (!app.isFile()) return;

        List<GradleDependency> dependencies = new GradleParser().parseDependencyList(FileUtil.readFile(app.getAbsolutePath()));
        GradleSyncEngine.Store store = GradleSyncEngine.loadStore(scId);
        Set<String> names = new LinkedHashSet<>();
        for (GradleDependency dependency : dependencies) {
            if (!GradleSyncEngine.isConsumedConfiguration(dependency.configuration)) continue;
            BuiltInArtifacts.Match match = BuiltInArtifacts.findSatisfying(dependency.group, dependency.artifact, dependency.version);
            if (match != null) names.addAll(BuiltInArtifacts.closure(match.libraryName));
            List<String> transitive = store.builtIns.get(dependency.coordinate());
            if (transitive != null) {
                for (String name : transitive) names.addAll(BuiltInArtifacts.closure(name));
            }
        }
        for (String name : names) {
            if (!currentNames(manager).contains(name)) manager.addLibrary(name);
        }
    }

    private static Set<String> currentNames(BuiltInLibraryManager manager) {
        Set<String> result = new LinkedHashSet<>();
        ArrayList<Jp> libraries = manager.getLibraries();
        if (libraries != null) {
            for (Jp library : libraries) {
                if (library != null && library.getName() != null) result.add(library.getName());
            }
        }
        return result;
    }
}
