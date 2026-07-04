package mod.hey.studios.project.proguard;

import com.google.gson.Gson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import a.a.a.ProjectBuilder;
import mod.hey.studios.util.Helper;
import mod.jbk.build.BuildProgressReceiver;
import pro.sketchware.utility.FileUtil;

public class ProguardHandler {
    public static String ANDROID_PROGUARD_RULES_PATH = createAndroidRules();
    public static String LINE_NUMBER_RULES_PATH = createLineNumberRules();
    public static String DEFAULT_PROGUARD_RULES_PATH = "";
    private final String config_path;
    private final String fm_config_path;

    public ProguardHandler(String sc_id) {
        DEFAULT_PROGUARD_RULES_PATH = createDefaultRules(sc_id);
        config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard";
        fm_config_path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard_fm";

        if (!FileUtil.isExistFile(config_path)) {
            FileUtil.writeFile(config_path, getDefaultConfig());
        }
    }

    private static String createLineNumberRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/line-number-rules.pro";

        if (!FileUtil.isExistFile(rulePath)) {
            FileUtil.writeFile(rulePath, """
                    -keepattributes SourceFile,LineNumberTable
                    -renamesourcefileattribute SourceFile
                    """);
        }

        return rulePath;
    }

    private static String createAndroidRules() {
        String rulePath = FileUtil.getExternalStorageDir() + "/.sketchware/libs/android-proguard-rules.pro";

        if (!FileUtil.isExistFile(rulePath)) {
            FileUtil.writeFile(rulePath, """
                    -dontusemixedcaseclassnames
                    -dontskipnonpubliclibraryclasses
                    -verbose
                    
                    -dontoptimize
                    -dontpreverify
                    
                    -keepattributes *Annotation*
                    -keep public class com.google.vending.licensing.ILicensingService
                    -keep public class com.android.vending.licensing.ILicensingService
                    
                    -keepclasseswithmembernames class * {
                        native <methods>;
                    }
                    
                    -keepclassmembers public class * extends android.view.View {
                       void set*(***);
                       *** get*();
                    }
                    
                    -keepclassmembers class * extends android.app.Activity {
                       public void *(android.view.View);
                    }
                    
                    -keepclassmembers enum * {
                        public static **[] values();
                        public static ** valueOf(java.lang.String);
                    }
                    
                    -keepclassmembers class * implements android.os.Parcelable {
                      public static final android.os.Parcelable$Creator CREATOR;
                    }
                    
                    -keepclassmembers class **.R$* {
                        public static <fields>;
                    }
                    
                    -dontwarn android.support.**
                    
                    -keep class android.support.annotation.Keep
                    
                    -keep @android.support.annotation.Keep class * {*;}
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <methods>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <fields>;
                    }
                    
                    -keepclasseswithmembers class * {
                        @android.support.annotation.Keep <init>(...);
                    }
                    
                    -keepclassmembers class * {
                        @android.webkit.JavascriptInterface <methods>;\
                    }
                    
                    -dontwarn android.arch.**
                    -dontwarn android.lifecycle.**
                    -keep class android.arch.** { *; }
                    -keep class android.lifecycle.** { *; }
                    
                    -dontwarn androidx.arch.**
                    -dontwarn androidx.lifecycle.**
                    -keep class androidx.arch.** { *; }
                    -keep class androidx.lifecycle.** { *; }
                    """);
        }

        return rulePath;
    }

    private static String createDefaultRules(String sc_id) {
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/proguard-rules.pro";

        if (!FileUtil.isExistFile(path)) {
            FileUtil.writeFile(path, """
                    -repackageclasses
                    -ignorewarnings
                    -dontwarn
                    -dontnote
                    """);
        }

        return path;
    }

    private String getDefaultConfig() {
        HashMap<String, String> defaultConfig = new HashMap<>();

        defaultConfig.put("enabled", "false");
        defaultConfig.put("debug", "false");

        return new Gson().toJson(defaultConfig);
    }

    public String getCustomProguardRules() {
        return DEFAULT_PROGUARD_RULES_PATH;
    }

    public boolean isKeepLineNumbersEnabled() {
        boolean enabled = false;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
                String value = config.get("keepLineNumbers");
                enabled = value != null && value.equals("true");
            } catch (Exception e) {
                enabled = false;
            }
        }
        return enabled;
    }

    public void setKeepLineNumbersEnabled(boolean enabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("keepLineNumbers", String.valueOf(enabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public static final Map<String, String> KEEP_RULE_TEMPLATES = createKeepRuleTemplates();

    private static Map<String, String> createKeepRuleTemplates() {
        Map<String, String> templates = new LinkedHashMap<>();

        templates.put("Gson", """
                -keepattributes Signature
                -keepattributes *Annotation*
                -keep class com.google.gson.** { *; }
                -keep class * implements com.google.gson.TypeAdapterFactory
                -keep class * implements com.google.gson.JsonSerializer
                -keep class * implements com.google.gson.JsonDeserializer
                -keepclassmembers,allowobfuscation class * {
                  @com.google.gson.annotations.SerializedName <fields>;
                }
                """);

        templates.put("Retrofit", """
                -keepattributes Signature, InnerClasses, EnclosingMethod
                -keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
                -keepattributes AnnotationDefault
                -keep class retrofit2.** { *; }
                -keepclasseswithmembers class * {
                    @retrofit2.http.* <methods>;
                }
                -dontwarn retrofit2.**
                """);

        templates.put("OkHttp", """
                -dontwarn okhttp3.**
                -dontwarn okio.**
                -keep class okhttp3.** { *; }
                -keep interface okhttp3.** { *; }
                """);

        templates.put("Kotlin", """
                -dontwarn kotlin.**
                -keep class kotlin.Metadata { *; }
                -keepclassmembers class **$WhenMappings {
                    <fields>;
                }
                -keepclassmembers class kotlin.Metadata {
                    public <methods>;
                }
                """);

        templates.put("Glide", """
                -keep public class * implements com.bumptech.glide.module.GlideModule
                -keep public class * extends com.bumptech.glide.module.AppGlideModule
                -keep public enum com.bumptech.glide.load.ImageHeaderParser$ImageType {
                    **[] $VALUES;
                    public *;
                }
                """);

        return templates;
    }

    public void appendKeepRuleTemplate(String templateName) {
        String template = KEEP_RULE_TEMPLATES.get(templateName);
        if (template == null) return;

        String existing = FileUtil.isExistFile(DEFAULT_PROGUARD_RULES_PATH)
                ? FileUtil.readFile(DEFAULT_PROGUARD_RULES_PATH) : "";

        String updated = existing + (existing.endsWith("\n") || existing.isEmpty() ? "" : "\n")
                + "\n# " + templateName + "\n" + template;

        FileUtil.writeFile(DEFAULT_PROGUARD_RULES_PATH, updated);
    }

    public boolean isDebugFilesEnabled() {
        boolean debugFiles = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                if (!config.containsKey("debug")) return false;

                String debug = config.get("debug");
                if (debug != null) {
                    debugFiles = debug.equals("true");
                }

            } catch (Exception e) {
                debugFiles = false;
            }
        }

        return debugFiles;
    }

    public boolean isShrinkingEnabled() {
        boolean proguardEnabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get("enabled");
                if (enabled == null) {
                    proguardEnabled = false;
                } else {
                    proguardEnabled = enabled.equals("true");
                }

            } catch (Exception e) {
                proguardEnabled = false;
            }
        }

        return proguardEnabled;
    }

    public void setProguardEnabled(boolean proguardEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("enabled", String.valueOf(proguardEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean isR8Enabled() {
        boolean r8Enabled = true;
        if (FileUtil.isExistFile(config_path)) {
            try {
                var config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);

                String enabled = config.get("r8");
                if (enabled == null) {
                    r8Enabled = false;
                } else {
                    r8Enabled = enabled.equals("true");
                }

            } catch (Exception e) {
                r8Enabled = false;
            }
        }

        return r8Enabled;
    }

    public void setR8Enabled(boolean r8Enabled) {
        var config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("r8", String.valueOf(r8Enabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public boolean libIsProguardFMEnabled(String library) {
        boolean enabled;
        if (isShrinkingEnabled() && FileUtil.isExistFile(fm_config_path)) {
            String configContent = FileUtil.readFile(fm_config_path);

            if (configContent.isEmpty()) {
                return false;
            }

            try {
                ArrayList<String> config = new Gson().fromJson(configContent, Helper.TYPE_STRING);
                enabled = config.contains(library);
                return enabled;
            } catch (Exception ignored) {
            }
        }

        return false;
    }

    public void setDebugEnabled(boolean debugEnabled) {
        HashMap<String, String> config = new Gson().fromJson(FileUtil.readFile(config_path), Helper.TYPE_STRING_MAP);
        config.put("debug", String.valueOf(debugEnabled));

        FileUtil.writeFile(config_path, new Gson().toJson(config));
    }

    public void setProguardFMLibs(ArrayList<String> fullModeLibs) {
        FileUtil.writeFile(fm_config_path, new Gson().toJson(fullModeLibs));
    }

    public void start(BuildProgressReceiver progressReceiver, ProjectBuilder builder) throws IOException {
        if (isShrinkingEnabled()) {
            if (isR8Enabled()) {
                progressReceiver.onProgress("Running R8 on classes...", 15);
                builder.runR8();
            } else {
                progressReceiver.onProgress("ProGuarding classes...", 16);
                builder.runProguard();
            }
        }
    }
}
