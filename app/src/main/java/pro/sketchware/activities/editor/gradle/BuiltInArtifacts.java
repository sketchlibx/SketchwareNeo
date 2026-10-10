package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.jbk.build.BuiltInLibraries;
import mod.jbk.editor.manage.library.ExcludeBuiltInLibrariesActivity;
import mod.pranav.dependency.resolver.MavenVersions;

public final class BuiltInArtifacts {

    public static final class Match {
        public final String libraryName;
        public final String version;

        Match(String libraryName, String version) {
            this.libraryName = libraryName;
            this.version = version;
        }
    }

    private static final Pattern NAME_VERSION = Pattern.compile("^.+?-(\\d[A-Za-z0-9.+_-]*)$");
    private static final Map<String, String> BY_COORDINATE = new HashMap<>();

    static {
        register(BuiltInLibraries.ANDROIDX_ACTIVITY, "androidx.activity", "activity");
        register(BuiltInLibraries.ANDROIDX_ANNOTATION_EXPERIMENTAL, "androidx.annotation", "annotation-experimental");
        register(BuiltInLibraries.ANDROIDX_ANNOTATION_JVM, "androidx.annotation", "annotation", "annotation-jvm");
        register(BuiltInLibraries.ANDROIDX_APPCOMPAT, "androidx.appcompat", "appcompat");
        register(BuiltInLibraries.ANDROIDX_APPCOMPAT_RESOURCES, "androidx.appcompat", "appcompat-resources");
        register(BuiltInLibraries.ANDROIDX_ASYNCLAYOUTINFLATER, "androidx.asynclayoutinflater", "asynclayoutinflater");
        register(BuiltInLibraries.ANDROIDX_BROWSER, "androidx.browser", "browser");
        register(BuiltInLibraries.ANDROIDX_CARDVIEW, "androidx.cardview", "cardview");
        register(BuiltInLibraries.ANDROIDX_COLLECTION_JVM, "androidx.collection", "collection", "collection-jvm");
        register(BuiltInLibraries.ANDROIDX_CONCURRENT_FUTURES, "androidx.concurrent", "concurrent-futures");
        register(BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT, "androidx.constraintlayout", "constraintlayout");
        register(BuiltInLibraries.ANDROIDX_CONSTRAINTLAYOUT_CORE, "androidx.constraintlayout", "constraintlayout-core");
        register(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT, "androidx.coordinatorlayout", "coordinatorlayout");
        register(BuiltInLibraries.ANDROIDX_CORE, "androidx.core", "core");
        register(BuiltInLibraries.ANDROIDX_CORE_COMMON, "androidx.arch.core", "core-common");
        register(BuiltInLibraries.ANDROIDX_CORE_KTX, "androidx.core", "core-ktx");
        register(BuiltInLibraries.ANDROIDX_CORE_RUNTIME, "androidx.arch.core", "core-runtime");
        register(BuiltInLibraries.ANDROIDX_CORE_VIEWTREE, "androidx.core", "core-viewtree");
        register(BuiltInLibraries.ANDROIDX_CURSORADAPTER, "androidx.cursoradapter", "cursoradapter");
        register(BuiltInLibraries.ANDROIDX_CUSTOMVIEW, "androidx.customview", "customview");
        register(BuiltInLibraries.ANDROIDX_CUSTOMVIEW_POOLINGCONTAINER, "androidx.customview", "customview-poolingcontainer");
        register(BuiltInLibraries.ANDROIDX_DOCUMENTFILE, "androidx.documentfile", "documentfile");
        register(BuiltInLibraries.ANDROIDX_DRAWERLAYOUT, "androidx.drawerlayout", "drawerlayout");
        register(BuiltInLibraries.ANDROIDX_DYNAMICANIMATION, "androidx.dynamicanimation", "dynamicanimation");
        register(BuiltInLibraries.ANDROIDX_EMOJI2, "androidx.emoji2", "emoji2");
        register(BuiltInLibraries.ANDROIDX_EMOJI2_VIEWS_HELPER, "androidx.emoji2", "emoji2-views-helper");
        register(BuiltInLibraries.ANDROIDX_EXIFINTERFACE, "androidx.exifinterface", "exifinterface");
        register(BuiltInLibraries.ANDROIDX_FRAGMENT, "androidx.fragment", "fragment");
        register(BuiltInLibraries.ANDROIDX_GRAPHICS_SHAPES_ANDROID, "androidx.graphics", "graphics-shapes", "graphics-shapes-android");
        register(BuiltInLibraries.ANDROIDX_INTERPOLATOR, "androidx.interpolator", "interpolator");
        register(BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_CORE_UI, "androidx.legacy", "legacy-support-core-ui");
        register(BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_CORE_UTILS, "androidx.legacy", "legacy-support-core-utils");
        register(BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_V13, "androidx.legacy", "legacy-support-v13");
        register(BuiltInLibraries.ANDROIDX_LEGACY_SUPPORT_V4, "androidx.legacy", "legacy-support-v4");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_COMMON, "androidx.lifecycle", "lifecycle-common");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_LIVEDATA, "androidx.lifecycle", "lifecycle-livedata");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_LIVEDATA_CORE, "androidx.lifecycle", "lifecycle-livedata-core");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_PROCESS, "androidx.lifecycle", "lifecycle-process");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_RUNTIME, "androidx.lifecycle", "lifecycle-runtime");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_SERVICE, "androidx.lifecycle", "lifecycle-service");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_VIEWMODEL, "androidx.lifecycle", "lifecycle-viewmodel");
        register(BuiltInLibraries.ANDROIDX_LIFECYCLE_VIEWMODEL_SAVEDSTATE, "androidx.lifecycle", "lifecycle-viewmodel-savedstate");
        register(BuiltInLibraries.ANDROIDX_LOADER, "androidx.loader", "loader");
        register(BuiltInLibraries.ANDROIDX_LOCALBROADCASTMANAGER, "androidx.localbroadcastmanager", "localbroadcastmanager");
        register(BuiltInLibraries.ANDROIDX_MEDIA, "androidx.media", "media");
        register(BuiltInLibraries.ANDROIDX_MULTIDEX, "androidx.multidex", "multidex");
        register(BuiltInLibraries.ANDROIDX_RECYCLERVIEW, "androidx.recyclerview", "recyclerview");
        register(BuiltInLibraries.ANDROIDX_ROOM_COMMON, "androidx.room", "room-common");
        register(BuiltInLibraries.ANDROIDX_ROOM_RUNTIME, "androidx.room", "room-runtime");
        register(BuiltInLibraries.ANDROIDX_SAVEDSTATE, "androidx.savedstate", "savedstate");
        register(BuiltInLibraries.ANDROIDX_SLIDINGPANELAYOUT, "androidx.slidingpanelayout", "slidingpanelayout");
        register(BuiltInLibraries.ANDROIDX_SQLITE, "androidx.sqlite", "sqlite");
        register(BuiltInLibraries.ANDROIDX_SQLITE_FRAMEWORK, "androidx.sqlite", "sqlite-framework");
        register(BuiltInLibraries.ANDROIDX_STARTUP_RUNTIME, "androidx.startup", "startup-runtime");
        register(BuiltInLibraries.ANDROIDX_SWIPEREFRESHLAYOUT, "androidx.swiperefreshlayout", "swiperefreshlayout");
        register(BuiltInLibraries.ANDROIDX_TRACING, "androidx.tracing", "tracing");
        register(BuiltInLibraries.ANDROIDX_TRANSITION, "androidx.transition", "transition");
        register(BuiltInLibraries.ANDROIDX_VECTORDRAWABLE, "androidx.vectordrawable", "vectordrawable");
        register(BuiltInLibraries.ANDROIDX_VECTORDRAWABLE_ANIMATED, "androidx.vectordrawable", "vectordrawable-animated");
        register(BuiltInLibraries.ANDROIDX_VERSIONEDPARCELABLE, "androidx.versionedparcelable", "versionedparcelable");
        register(BuiltInLibraries.ANDROIDX_VIEWPAGER, "androidx.viewpager", "viewpager");
        register(BuiltInLibraries.ANDROIDX_VIEWPAGER2, "androidx.viewpager2", "viewpager2");
        register(BuiltInLibraries.ANDROIDX_WORK_RUNTIME, "androidx.work", "work-runtime");
        register(BuiltInLibraries.AUTO_VALUE_ANNOTATIONS, "com.google.auto.value", "auto-value-annotations");
        register(BuiltInLibraries.CIRCLEIMAGEVIEW, "de.hdodenhof", "circleimageview");
        register(BuiltInLibraries.ERROR_PRONE_ANNOTATIONS, "com.google.errorprone", "error_prone_annotations");
        register(BuiltInLibraries.FIREBASE_AUTH, "com.google.firebase", "firebase-auth");
        register(BuiltInLibraries.FIREBASE_AUTH_INTEROP, "com.google.firebase", "firebase-auth-interop");
        register(BuiltInLibraries.FIREBASE_COMMON, "com.google.firebase", "firebase-common");
        register(BuiltInLibraries.FIREBASE_COMPONENTS, "com.google.firebase", "firebase-components");
        register(BuiltInLibraries.FIREBASE_DATABASE, "com.google.firebase", "firebase-database");
        register(BuiltInLibraries.FIREBASE_DATABASE_COLLECTION, "com.google.firebase", "firebase-database-collection");
        register(BuiltInLibraries.FIREBASE_IID, "com.google.firebase", "firebase-iid");
        register(BuiltInLibraries.FIREBASE_IID_INTEROP, "com.google.firebase", "firebase-iid-interop");
        register(BuiltInLibraries.FIREBASE_MEASUREMENT_CONNECTOR, "com.google.firebase", "firebase-measurement-connector");
        register(BuiltInLibraries.FIREBASE_MESSAGING, "com.google.firebase", "firebase-messaging");
        register(BuiltInLibraries.FIREBASE_STORAGE, "com.google.firebase", "firebase-storage");
        register(BuiltInLibraries.GLIDE, "com.github.bumptech.glide", "glide");
        register(BuiltInLibraries.GLIDE_ANNOTATIONS, "com.github.bumptech.glide", "annotations");
        register(BuiltInLibraries.GLIDE_DISKLRUCACHE, "com.github.bumptech.glide", "disklrucache");
        register(BuiltInLibraries.GLIDE_GIFDECODER, "com.github.bumptech.glide", "gifdecoder");
        register(BuiltInLibraries.GSON, "com.google.code.gson", "gson");
        register(BuiltInLibraries.JETBRAINS_ANNOTATIONS, "org.jetbrains", "annotations");
        register(BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_ANDROID, "org.jetbrains.kotlinx", "kotlinx-coroutines-android");
        register(BuiltInLibraries.JETBRAINS_KOTLINX_COROUTINES_CORE_JVM, "org.jetbrains.kotlinx", "kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm");
        register(BuiltInLibraries.JETBRAINS_KOTLIN_STDLIB, "org.jetbrains.kotlin", "kotlin-stdlib");
        register(BuiltInLibraries.JSPECIFY, "org.jspecify", "jspecify");
        register(BuiltInLibraries.LOTTIE, "com.airbnb.android", "lottie");
        register(BuiltInLibraries.MATERIAL, "com.google.android.material", "material");
        register(BuiltInLibraries.OKHTTP_ANDROID, "com.squareup.okhttp3", "okhttp", "okhttp-android");
        register(BuiltInLibraries.OKIO_JVM, "com.squareup.okio", "okio", "okio-jvm");
        register(BuiltInLibraries.PLAY_SERVICES_ADS, "com.google.android.gms", "play-services-ads");
        register(BuiltInLibraries.PLAY_SERVICES_ADS_BASE, "com.google.android.gms", "play-services-ads-base");
        register(BuiltInLibraries.PLAY_SERVICES_ADS_IDENTIFIER, "com.google.android.gms", "play-services-ads-identifier");
        register(BuiltInLibraries.PLAY_SERVICES_ADS_LITE, "com.google.android.gms", "play-services-ads-lite");
        register(BuiltInLibraries.PLAY_SERVICES_APPSET, "com.google.android.gms", "play-services-appset");
        register(BuiltInLibraries.PLAY_SERVICES_AUTH, "com.google.android.gms", "play-services-auth");
        register(BuiltInLibraries.PLAY_SERVICES_AUTH_API_PHONE, "com.google.android.gms", "play-services-auth-api-phone");
        register(BuiltInLibraries.PLAY_SERVICES_AUTH_BASE, "com.google.android.gms", "play-services-auth-base");
        register(BuiltInLibraries.PLAY_SERVICES_BASE, "com.google.android.gms", "play-services-base");
        register(BuiltInLibraries.PLAY_SERVICES_BASEMENT, "com.google.android.gms", "play-services-basement");
        register(BuiltInLibraries.PLAY_SERVICES_GASS, "com.google.android.gms", "play-services-gass");
        register(BuiltInLibraries.PLAY_SERVICES_GCM, "com.google.android.gms", "play-services-gcm");
        register(BuiltInLibraries.PLAY_SERVICES_IID, "com.google.android.gms", "play-services-iid");
        register(BuiltInLibraries.PLAY_SERVICES_LOCATION, "com.google.android.gms", "play-services-location");
        register(BuiltInLibraries.PLAY_SERVICES_MAPS, "com.google.android.gms", "play-services-maps");
        register(BuiltInLibraries.PLAY_SERVICES_MEASUREMENT_BASE, "com.google.android.gms", "play-services-measurement-base");
        register(BuiltInLibraries.PLAY_SERVICES_MEASUREMENT_SDK_API, "com.google.android.gms", "play-services-measurement-sdk-api");
        register(BuiltInLibraries.PLAY_SERVICES_PLACES_PLACEREPORT, "com.google.android.gms", "play-services-places-placereport");
        register(BuiltInLibraries.PLAY_SERVICES_STATS, "com.google.android.gms", "play-services-stats");
        register(BuiltInLibraries.PLAY_SERVICES_TASKS, "com.google.android.gms", "play-services-tasks");
    }

    private BuiltInArtifacts() {
    }

    private static void register(String libraryName, String group, String artifact, String... aliases) {
        BY_COORDINATE.put(group + ":" + artifact, libraryName);
        for (String alias : aliases) {
            BY_COORDINATE.put(group + ":" + alias, libraryName);
        }
    }

    @Nullable
    public static Match find(@NonNull String group, @NonNull String artifact) {
        String name = BY_COORDINATE.get(group + ":" + artifact);
        if (name == null) return null;
        if (!BuiltInLibraries.BuiltInLibrary.ofName(name).isPresent()) return null;
        Matcher m = NAME_VERSION.matcher(name);
        if (!m.matches()) return null;
        return new Match(name, m.group(1));
    }

    @Nullable
    public static Match findSatisfying(@NonNull String scId, @NonNull String group, @NonNull String artifact, @Nullable String requestedVersion) {
        Match match = findSatisfying(group, artifact, requestedVersion);
        if (match == null) return null;
        Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(match.libraryName);
        if (library.isPresent() && ExcludeBuiltInLibrariesActivity.getExcludedLibraries(scId).contains(library.get())) return null;
        return match;
    }

    @Nullable
    public static Match findSatisfying(@NonNull String group, @NonNull String artifact, @Nullable String requestedVersion) {
        Match match = find(group, artifact);
        if (match == null) return null;
        String requested = normalizeVersion(requestedVersion);
        if (requested == null) return null;
        return compareVersions(match.version, requested) >= 0 ? match : null;
    }

    @Nullable
    public static String normalizeVersion(@Nullable String version) {
        if (version == null) return null;
        String v = version.trim();
        if (v.startsWith("[") && v.endsWith("]") && !v.contains(",")) {
            v = v.substring(1, v.length() - 1).trim();
        }
        if (v.isEmpty() || v.equals("+") || v.contains("$") || v.contains(",") || v.contains("[") || v.contains("(")
                || v.contains(")") || v.contains("]") || v.endsWith("+")) {
            return null;
        }
        return v;
    }

    @NonNull
    public static Set<String> closure(@NonNull String libraryName) {
        Set<String> result = new LinkedHashSet<>();
        collect(libraryName, result);
        return result;
    }

    private static void collect(String name, Set<String> out) {
        if (!out.add(name)) return;
        Optional<BuiltInLibraries.BuiltInLibrary> library = BuiltInLibraries.BuiltInLibrary.ofName(name);
        if (!library.isPresent()) return;
        for (String dependency : library.get().getDependencyNames()) {
            collect(dependency, out);
        }
    }

    public static boolean isExtracted(@NonNull String libraryName) {
        File jar = BuiltInLibraries.getLibraryClassesJarPath(libraryName);
        File dex = BuiltInLibraries.getLibraryDexFile(libraryName);
        return jar.isFile() && jar.length() > 0 && dex.isFile() && dex.length() > 0;
    }

    public static int compareVersions(@NonNull String a, @NonNull String b) {
        return MavenVersions.compare(a, b);
    }
}
