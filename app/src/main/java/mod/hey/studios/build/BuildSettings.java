package mod.hey.studios.build;

import java.io.Serializable;

import mod.hey.studios.project.ProjectSettings;
import pro.sketchware.utility.FileUtil;

public class BuildSettings extends ProjectSettings implements Serializable {

    public static final String SETTING_ANDROID_JAR_PATH = "android_jar";
    public static final String SETTING_CLASSPATH = "classpath";
    public static final String SETTING_DEXER = "dexer";
    public static final String SETTING_JAVA_VERSION = "java_ver";
    public static final String SETTING_NO_HTTP_LEGACY = "no_http_legacy";
    public static final String SETTING_NO_WARNINGS = "no_warn";
    public static final String SETTING_ENABLE_LOGCAT = "enable_logcat";
    public static final String SETTING_OFFLINE_CACHE = "offline_dependency_cache";
    public static final String SETTING_PARALLEL_THREADS = "parallel_threads";
    public static final String SETTING_SKIP_SUB_DEPENDENCIES = "skip_sub_dependencies";
    public static final int DEFAULT_PARALLEL_THREADS = 4;

    public static final String SETTING_DEXER_D8 = "D8";
    public static final String SETTING_DEXER_DX = "Dx"; 
    
    public static final String SETTING_JAVA_VERSION_1_7 = "1.7";
    public static final String SETTING_JAVA_VERSION_1_8 = "1.8";
    public static final String SETTING_JAVA_VERSION_1_9 = "1.9";
    public static final String SETTING_JAVA_VERSION_10 = "10";
    public static final String SETTING_JAVA_VERSION_11 = "11";
    public static final String SETTING_JAVA_VERSION_17 = "17";

    public BuildSettings(String sc_id) {
        super(sc_id);
    }

    public static int getMaxParallelThreads() {
        return Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors()));
    }

    public int getParallelThreads() {
        int max = getMaxParallelThreads();
        try {
            int requested = Integer.parseInt(getValue(SETTING_PARALLEL_THREADS, String.valueOf(DEFAULT_PARALLEL_THREADS)));
            return Math.max(1, Math.min(requested, max));
        } catch (NumberFormatException e) {
            return Math.min(DEFAULT_PARALLEL_THREADS, max);
        }
    }

    public boolean isSkipSubDependencies() {
        return getValue(SETTING_SKIP_SUB_DEPENDENCIES, SETTING_GENERIC_VALUE_TRUE).equals(SETTING_GENERIC_VALUE_TRUE);
    }

    public boolean isOfflineCacheEnabled() {
        return getValue(SETTING_OFFLINE_CACHE, SETTING_GENERIC_VALUE_TRUE).equals(SETTING_GENERIC_VALUE_TRUE);
    }

    @Override
    public String getPath() {
        return FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/build_config";
    }
}
