package a.a.a;

import static android.system.OsConstants.S_IRUSR;
import static android.system.OsConstants.S_IWUSR;
import static android.system.OsConstants.S_IXUSR;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.StrictMode;
import android.os.SystemClock;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Xml;
import android.widget.Toast;

import com.android.sdklib.build.ApkBuilder;
import com.android.sdklib.build.ApkCreationException;
import com.android.sdklib.build.DuplicateFileException;
import com.android.sdklib.build.SealedApkException;
import com.github.megatronking.stringfog.plugin.StringFogClassInjector;
import com.github.megatronking.stringfog.plugin.StringFogMappingPrinter;
import com.iyxan23.zipalignjava.InvalidZipException;
import com.iyxan23.zipalignjava.ZipAlign;

import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.lang.reflect.Method;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import mod.agus.jcoderz.dex.Dex;
import mod.agus.jcoderz.dex.FieldId;
import mod.agus.jcoderz.dex.MethodId;
import mod.agus.jcoderz.dex.ProtoId;
import mod.agus.jcoderz.dx.command.dexer.DxContext;
import mod.agus.jcoderz.dx.command.dexer.Main;
import mod.agus.jcoderz.dx.merge.CollisionPolicy;
import mod.agus.jcoderz.dx.merge.DexMerger;
import mod.agus.jcoderz.editor.library.ExtLibSelected;
import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.compiler.kotlin.KotlinCompilerBridge;
import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.project.proguard.ProguardHandler;
import mod.hey.studios.util.SystemLogPrinter;
import mod.jbk.build.BuildProgressReceiver;
import mod.jbk.build.BuiltInLibraries;
import mod.jbk.build.compiler.dex.DexCompiler;
import mod.jbk.build.compiler.resource.ResourceCompiler;
import mod.jbk.util.LogUtil;
import mod.jbk.util.TestkeySignBridge;
import mod.pranav.build.JarBuilder;
import mod.pranav.build.R8Service;
import mod.pranav.viewbinding.ViewBindingBuilder;
import pro.sketchware.SketchApplication;
import pro.sketchware.util.library.BuiltInLibraryManager;
import pro.sketchware.utility.FilePathUtil;
import mod.hey.studios.activity.managers.cpp.CppExporter;
import mod.hey.studios.activity.managers.cpp.NativeCompiler;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import proguard.Configuration;
import proguard.ConfigurationParser;
import proguard.ParseException;
import proguard.ProGuard;

public class ProjectBuilder {
    public static final String TAG = "AppBuilder";
    public static final String ACTION_BUILD_DIAGNOSTICS = "com.sketchware.action.BUILD_DIAGNOSTICS";

    private final File aapt2Binary;
    private final Context context;
    public BuildSettings build_settings;
    public yq yq;
    public FilePathUtil fpu;
    public ManageLocalLibrary mll;
    public BuiltInLibraryManager builtInLibraryManager;
    public String androidJarPath;
    public ProguardHandler proguard;
    public ProjectSettings settings;
    private BuildProgressReceiver progressReceiver;
    private boolean buildAppBundle = false;
    private ArrayList<File> dexesToAddButNotMerge = new ArrayList<>();
    private boolean isMultiDexEnabled = false; 
    // Per-build fallback only; never overwrite the user's persisted R8 setting.
    private boolean useProguardFallbackForCurrentBuild = false;
    private ArrayList<String> cachedApplicationKeepClassesForCurrentBuild;

    private long timestampResourceCompilationStarted;

    public ProjectBuilder(Context context, yq yqVar) {
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .build()
        );

        SystemLogPrinter.start();

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            LogUtil.d(TAG, "Running Sketchware Neo " + info.versionName + " (" + info.versionCode + ")");
            ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
            long fileSizeInBytes = new File(applicationInfo.sourceDir).length();
            LogUtil.d(TAG, "base.apk's size is " + Formatter.formatFileSize(context, fileSizeInBytes) + " (" + fileSizeInBytes + " B)");
        } catch (PackageManager.NameNotFoundException e) {
            LogUtil.e(TAG, "Somehow failed to get package info about us!", e);
        }

        aapt2Binary = new File(context.getCacheDir(), "aapt2");
        build_settings = new BuildSettings(yqVar.sc_id);
        this.context = context;
        yq = yqVar;
        fpu = new FilePathUtil();
        mll = new ManageLocalLibrary(yqVar.sc_id);
        builtInLibraryManager = new BuiltInLibraryManager(yqVar.sc_id);
        proguard = new ProguardHandler(yqVar.sc_id);
        settings = new ProjectSettings(yqVar.sc_id);

        int compileSdk = settings.getCompileSdkVersion();
        File defaultAndroidJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android.jar");
        File compileSdkJar = new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "android-" + compileSdk + ".jar");

        if (compileSdkJar.exists()) {
            androidJarPath = build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, compileSdkJar.getAbsolutePath());
            LogUtil.d(TAG, "Using custom Compile SDK android.jar: " + androidJarPath);
        } else {
            androidJarPath = build_settings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, defaultAndroidJar.getAbsolutePath());
            LogUtil.d(TAG, "Custom Compile SDK JAR not found. Falling back to default: " + androidJarPath);
        }
    }

    public ProjectBuilder(BuildProgressReceiver buildAsyncTask, Context context, yq yqVar) {
        this(context, yqVar);
        progressReceiver = buildAsyncTask;
    }

    private void broadcastBuildOutput(String output) {
        Intent intent = new Intent(ACTION_BUILD_DIAGNOSTICS);
        intent.putExtra("build_output", output);
        intent.setPackage(context.getPackageName()); 
        context.sendBroadcast(intent);
    }

    public static boolean hasFileChanged(String fileInAssets, String targetFile) {
        long length;
        File compareToFile = new File(targetFile);
        oB fileUtil = new oB();
        long lengthOfFileInAssets = fileUtil.a(SketchApplication.getContext(), fileInAssets);
        if (compareToFile.exists()) {
            length = compareToFile.length();
        } else {
            length = 0;
        }
        if (lengthOfFileInAssets == length) {
            return false;
        }
        fileUtil.a(compareToFile);
        fileUtil.a(SketchApplication.getContext(), fileInAssets, targetFile);
        return true;
    }

    public void compileResources() throws Exception {
        neo.sketchware.plugin.PluginManager.notifyBuildStarted(yq.sc_id);

        timestampResourceCompilationStarted = System.currentTimeMillis();
        ResourceCompiler compiler = new ResourceCompiler(
                this,
                aapt2Binary,
                buildAppBundle,
                progressReceiver);
        compiler.compile();
        LogUtil.d(TAG, "Compiling resources took " + (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    public void generateViewBinding() throws IOException, SAXException {
        if (settings.getValue(ProjectSettings.SETTING_ENABLE_VIEWBINDING, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_FALSE)) {
            return;
        }
        File outputDirectory = new File(yq.javaFilesPath + File.separator + yq.packageName.replace(".", File.separator) + File.separator + "databinding");
        outputDirectory.mkdirs();

        List<File> layouts = FileUtil.listFiles(yq.layoutFilesPath, "xml").stream()
                .map(File::new)
                .collect(Collectors.toList());

        ViewBindingBuilder builder = new ViewBindingBuilder(layouts, outputDirectory, yq.packageName);
        builder.generateBindings();
    }

    public boolean isD8Enabled() {
        String javaVer = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
        if (javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_11) || javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_17)) {
            return true;
        }
        return build_settings.getValue(
                BuildSettings.SETTING_DEXER,
                BuildSettings.SETTING_DEXER_DX
        ).equals(BuildSettings.SETTING_DEXER_D8);
    }
    
    public void setMultiDexEnabled(boolean isEnabled) {
        this.isMultiDexEnabled = isEnabled;
    }
    
    public boolean isMultiDexEnabled() {
        return isMultiDexEnabled;
    }

    public String getDxRunningText() {
        return (isD8Enabled() ? "D8" : "Dx") + " is running...";
    }

    public void compileNativeCode() throws zy {
        String cppSourcePath = fpu.getPathCpp(yq.sc_id);
        if (!CppExporter.hasCppFiles(cppSourcePath)) {
            LogUtil.d(TAG, "No C/C++ native source structure detected. Skipping native build stage.");
            return;
        }
        
        if (progressReceiver != null) {
            progressReceiver.onProgress("Compiling C/C++ Native Code...", 14);
        }

        String tempBuildPath = yq.projectMyscPath + File.separator + "cpp_build";

        try {
            NativeCompiler.compileOnDevice(
                    context, 
                    yq.sc_id, 
                    cppSourcePath, 
                    yq.compiledNativeLibsPath,
                    tempBuildPath 
            );
        } catch (Exception e) {
            String errorMsg = "C/C++ Compilation Error:\n\n" + e.getMessage();
            broadcastBuildOutput(errorMsg);
            throw new zy(errorMsg);
        }
    }

    public void createDexFilesFromClasses() throws Exception {
        File dexOutputDir = new File(yq.binDirectoryPath, "dex");

        if (isR8ActiveForCurrentBuild()) {
            File[] r8DexFiles = dexOutputDir.listFiles(
                    (dir, name) -> name.matches("classes([0-9]+)?\\.dex"));
            if (r8DexFiles == null || r8DexFiles.length == 0) {
                throw new IOException("R8 was reported successful, but no classes*.dex files exist in "
                        + dexOutputDir.getAbsolutePath() + ". The build is stopped instead of packaging an APK without application bytecode.");
            }
            for (File dexFile : r8DexFiles) {
                validateDexOutputFile(dexFile, "R8");
            }
            LogUtil.d(TAG, "Preserving R8 outputs: " + Arrays.toString(r8DexFiles));
            return;
        }

        if (dexOutputDir.exists()) {
            File[] staleDexFiles = dexOutputDir.listFiles((dir, name) -> name.matches("classes([0-9]+)?\\.dex"));
            if (staleDexFiles != null) {
                for (File staleDexFile : staleDexFiles) {
                    if (!staleDexFile.delete() && staleDexFile.exists()) {
                        throw new IOException("Could not remove stale DEX output before D8/Dx compilation: "
                                + staleDexFile.getAbsolutePath());
                    }
                }
            }
        } else if (!dexOutputDir.mkdirs() && !dexOutputDir.isDirectory()) {
            throw new IOException("Could not create DEX output directory: " + dexOutputDir.getAbsolutePath());
        }

        if (isD8Enabled()) {
            long savedTimeMillis = System.currentTimeMillis();
            try {
                DexCompiler.compileDexFiles(this);
                LogUtil.d(TAG, "D8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "D8 failed to process .class files", e);
                throw e;
            }
        } else {
            long savedTimeMillis = System.currentTimeMillis();
            List<String> args = new ArrayList<>(Arrays.asList(
                    "--debug",
                    "--verbose",
                    "--multi-dex",
                    "--output=" + yq.binDirectoryPath + File.separator + "dex",
                    proguard.isShrinkingEnabled() ? yq.proguardClassesPath : yq.compiledClassesPath
            ));
            args.addAll(mll.getJarOnlyLibraryJars());

            try {
                LogUtil.d(TAG, "Running Dx with these arguments: " + args);

                Main.clearInternTables();
                Main.Arguments arguments = new Main.Arguments();
                Method parseMethod = Main.Arguments.class.getDeclaredMethod("parse", String[].class);
                parseMethod.setAccessible(true);
                parseMethod.invoke(arguments, (Object) args.toArray(new String[0]));

                Main.run(arguments);
                LogUtil.d(TAG, "Dx took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } catch (Exception e) {
                LogUtil.e(TAG, "Dx failed to process .class files", e);
                throw e;
            }
        }
    }

    /** classes.dex sorts before classes2.dex, classes3.dex, and so on. */
    private int getDexOutputSequence(String fileName) {
        if ("classes.dex".equals(fileName)) return 1;
        if (fileName != null && fileName.matches("classes[0-9]+\\.dex")) {
            try {
                int sequence = Integer.parseInt(fileName.substring("classes".length(), fileName.length() - ".dex".length()));
                return sequence >= 2 ? sequence : Integer.MAX_VALUE;
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Validates the DEX header before its path is allowed into APK packaging. */
    private void validateDexOutputFile(File dexFile, String compilerName) throws IOException {
        if (dexFile == null || !dexFile.isFile() || dexFile.length() < 112L) {
            throw new IOException(compilerName + " produced a missing, empty, or truncated DEX file: "
                    + (dexFile == null ? "<null>" : dexFile.getAbsolutePath()));
        }
        try (RandomAccessFile input = new RandomAccessFile(dexFile, "r")) {
            byte[] magic = new byte[8];
            input.readFully(magic);
            if (magic[0] != 'd' || magic[1] != 'e' || magic[2] != 'x' || magic[3] != '\n' || magic[7] != 0) {
                throw new IOException(compilerName + " output is not a valid DEX file: " + dexFile.getAbsolutePath());
            }
        }
    }

    public String getClasspath() {
        StringBuilder classpath = new StringBuilder();

        KotlinCompilerBridge.maybeAddKotlinFilesToClasspath(classpath, yq);
        classpath.append(androidJarPath);

        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY,
                BuildSettings.SETTING_GENERIC_VALUE_FALSE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        if (settings.getMinSdkVersion() < 21) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.ANDROIDX_MULTIDEX));
        }

        String javaVer = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
        if (!javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_1_7)) {
            classpath.append(":").append(new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar").getAbsolutePath());
        }

        for (Jp library : builtInLibraryManager.getLibraries()) {
            classpath.append(":").append(BuiltInLibraries.getLibraryClassesJarPathString(library.getName()));
        }

        classpath.append(mll.getJarLocalLibrary());

        if (!build_settings.getValue(BuildSettings.SETTING_CLASSPATH, "").isEmpty()) {
            classpath.append(":").append(build_settings.getValue(BuildSettings.SETTING_CLASSPATH, ""));
        }

        String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + yq.sc_id + "/files/classpath/";
        ArrayList<String> jars = FileUtil.listFiles(path, "jar");
        classpath.append(":").append(TextUtils.join(":", jars));

        return classpath.toString();
    }

    public String getProguardClasspath() {
        Collection<String> localLibraryJarsWithFullModeOn = new LinkedList<>();

        for (HashMap<String, Object> localLibrary : mll.list) {
            Object nameObject = localLibrary.get("name");
            Object jarPathObject = localLibrary.get("jarPath");

            if (nameObject instanceof String name && jarPathObject instanceof String jarPath) {

                if (localLibrary.containsKey("jarPath") && proguard.libIsProguardFMEnabled(name)) {
                    localLibraryJarsWithFullModeOn.add(jarPath);
                }
            }
        }

        String normalClasspath = getClasspath();
        StringBuilder classpath = new StringBuilder();
        normalClasspathLoop:
        for (String classpathPart : normalClasspath.split(":")) {
            for (String jarPathToExclude : localLibraryJarsWithFullModeOn) {
                if (classpathPart.equals(jarPathToExclude)) {
                    localLibraryJarsWithFullModeOn.remove(jarPathToExclude);
                    continue normalClasspathLoop;
                }
            }

            if (!classpathPart.equals(yq.compiledClassesPath)) {
                classpath.append(classpathPart).append(':');
            }
        }

        if (classpath.length() > 0) classpath.deleteCharAt(classpath.length() - 1);
        return classpath.toString();
    }

    private Collection<File> dexLibraries(File outputDirectory, List<File> dexes) throws Exception {
        int lastDexNumber = 1;
        String nextMergedDexFilename;
        Collection<File> resultDexFiles = new LinkedList<>();
        LinkedList<Dex> dexObjects = new LinkedList<>();
        Iterator<File> toMergeIterator = dexes.iterator();

        List<FieldId> mergedDexFields;
        List<MethodId> mergedDexMethods;
        List<ProtoId> mergedDexProtos;
        List<Integer> mergedDexTypes;

        if (!toMergeIterator.hasNext()) return resultDexFiles;

        {
            Dex firstDex = new Dex(new FileInputStream(toMergeIterator.next()));
            dexObjects.add(firstDex);
            mergedDexFields = new LinkedList<>(firstDex.fieldIds());
            mergedDexMethods = new LinkedList<>(firstDex.methodIds());
            mergedDexProtos = new LinkedList<>(firstDex.protoIds());
            mergedDexTypes = new LinkedList<>(firstDex.typeIds());
        }

        while (toMergeIterator.hasNext()) {
            File dexFile = toMergeIterator.next();
            nextMergedDexFilename = lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex";

            Dex dex = new Dex(new FileInputStream(dexFile));

            boolean canMerge = true;
            List<FieldId> newDexFieldIds = new LinkedList<>();
            List<MethodId> newDexMethodIds = new LinkedList<>();
            List<ProtoId> newDexProtoIds = new LinkedList<>();
            List<Integer> newDexTypeIds = new LinkedList<>();

            bruh:
            {
                for (FieldId fieldId : dex.fieldIds()) {
                    if (!mergedDexFields.contains(fieldId)) {
                        if (mergedDexFields.size() + newDexFieldIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new field IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexFields.size() + " field IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexFieldIds.add(fieldId);
                        }
                    }
                }

                for (MethodId methodId : dex.methodIds()) {
                    if (!newDexMethodIds.contains(methodId)) {
                        if (mergedDexMethods.size() + newDexMethodIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new method IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexMethods.size() + " method IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexMethodIds.add(methodId);
                        }
                    }
                }

                for (ProtoId protoId : dex.protoIds()) {
                    if (!newDexProtoIds.contains(protoId)) {
                        if (mergedDexProtos.size() + newDexProtoIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new proto IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexProtos.size() + " proto IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexProtoIds.add(protoId);
                        }
                    }
                }

                for (Integer typeId : dex.typeIds()) {
                    if (!newDexTypeIds.contains(typeId)) {
                        if (mergedDexTypes.size() + newDexTypeIds.size() + 1 > 0xffff) {
                            LogUtil.d(TAG, "Can't merge DEX file to " + nextMergedDexFilename +
                                    " because it has too many new type IDs. "
                                    + nextMergedDexFilename + " will have " + mergedDexTypes.size() + " type IDs");
                            canMerge = false;
                            break bruh;
                        } else {
                            newDexTypeIds.add(typeId);
                        }
                    }
                }
            }

            if (canMerge) {
                LogUtil.d(TAG, "Merging DEX #" + dexes.indexOf(dexFile) + " as well to " + nextMergedDexFilename);
                dexObjects.add(dex);
                mergedDexFields.addAll(newDexFieldIds);
                mergedDexMethods.addAll(newDexMethodIds);
                mergedDexProtos.addAll(newDexProtoIds);
                mergedDexTypes.addAll(newDexTypeIds);
            } else {
                File target = new File(outputDirectory, nextMergedDexFilename);
                mergeDexes(target, dexObjects);
                resultDexFiles.add(target);
                dexObjects.clear();
                dexObjects.add(dex);

                mergedDexFields = new ArrayList<>(dex.fieldIds());
                mergedDexMethods = new ArrayList<>(dex.methodIds());
                mergedDexProtos = new ArrayList<>(dex.protoIds());
                mergedDexTypes = new ArrayList<>(dex.typeIds());
                lastDexNumber++;
            }
        }
        if (!dexObjects.isEmpty()) {
            File file = new File(outputDirectory, lastDexNumber == 1 ? "classes.dex" : "classes" + lastDexNumber + ".dex");
            mergeDexes(file, dexObjects);
            resultDexFiles.add(file);
        }

        return resultDexFiles;
    }

    public String getLibraryPackageNames() {
        StringBuilder extraPackages = new StringBuilder();
        for (Jp library : builtInLibraryManager.getLibraries()) {
            if (library.hasResources()) {
                extraPackages.append(library.getPackageName()).append(":");
            }
        }
        return extraPackages + mll.getPackageNameLocalLibrary();
    }

    public void MichelJavaCode() throws zy, IOException {} 

    public void compileJavaCode() throws zy, IOException {
        long savedTimeMillis = System.currentTimeMillis();

        class EclipseOutOutputStream extends OutputStream {
            private final StringBuffer mBuffer = new StringBuffer();
            @Override public void write(int b) { mBuffer.append((char) b); }
            public String getOut() { return mBuffer.toString(); }
        }

        class EclipseErrOutputStream extends OutputStream {
            private final StringBuffer mBuffer = new StringBuffer();
            @Override public void write(int b) { mBuffer.append((char) b); }
            public String getOut() { return mBuffer.toString(); }
        }

        try (EclipseOutOutputStream outOutputStream = new EclipseOutOutputStream();
             PrintWriter outWriter = new PrintWriter(outOutputStream);
             EclipseErrOutputStream errOutputStream = new EclipseErrOutputStream();
             PrintWriter errWriter = new PrintWriter(errOutputStream)) {

            ArrayList<String> args = new ArrayList<>();
            String javaVer = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
            
            if (javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_17)) {
                args.add("-source"); args.add("17");
                args.add("-target"); args.add("17");
            } else if (javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_11)) {
                args.add("-source"); args.add("11");
                args.add("-target"); args.add("11");
            } else {
                args.add("-source"); args.add(javaVer);
                args.add("-target"); args.add(javaVer);
            }
            
            args.add("-nowarn");
            if (!build_settings.getValue(BuildSettings.SETTING_NO_WARNINGS, BuildSettings.SETTING_GENERIC_VALUE_TRUE).equals(BuildSettings.SETTING_GENERIC_VALUE_TRUE)) {
                args.add("-deprecation");
            }
            args.add("-d"); args.add(yq.compiledClassesPath);
            args.add("-cp"); args.add(getClasspath());
            args.add("-proc:none"); args.add(yq.javaFilesPath); args.add(yq.rJavaDirectoryPath);
            
            String pathJava = fpu.getPathJava(yq.sc_id);
            if (FileUtil.isExistFile(pathJava)) args.add(pathJava);
            String pathBroadcast = fpu.getPathBroadcast(yq.sc_id);
            if (FileUtil.isExistFile(pathBroadcast)) args.add(pathBroadcast);
            String pathService = fpu.getPathService(yq.sc_id);
            if (FileUtil.isExistFile(pathService)) args.add(pathService);

            File rJavaFileWithoutPackage = new File(yq.rJavaDirectoryPath, "R.java");
            if (rJavaFileWithoutPackage.exists() && !rJavaFileWithoutPackage.delete()) {
                LogUtil.w(TAG, "Failed to delete file " + rJavaFileWithoutPackage.getAbsolutePath());
            }

            org.eclipse.jdt.internal.compiler.batch.Main main = new org.eclipse.jdt.internal.compiler.batch.Main(outWriter, errWriter, false, null, null);
            LogUtil.d(TAG, "Running Eclipse compiler with these arguments: " + args);

            boolean compileSucceeded;
            try {
                compileSucceeded = main.compile(args.toArray(new String[0]));
            } catch (LinkageError hostRuntimeMismatch) {
                String diagnostic = "Java compiler crashed with a LinkageError (" + hostRuntimeMismatch.getMessage()
                        + ") instead of compiling. This means the bundled 'ecj' library version requires "
                        + "a real desktop JDK to run and is incompatible with Android's runtime — pin "
                        + "org.eclipse.jdt:ecj to an older version (3.33.0 or earlier) in libs_versions.toml.";
                LogUtil.e(TAG, diagnostic, hostRuntimeMismatch);
                broadcastBuildOutput(diagnostic);
                throw new zy(diagnostic);
            }

            LogUtil.d(TAG, "System.out of Eclipse compiler: " + outOutputStream.getOut());

            if (compileSucceeded && main.globalErrorsCount <= 0) {
                if (!directoryContainsAnyFile(new File(yq.compiledClassesPath), ".class")) {
                    String diagnostic = "Java compiler reported success (0 errors) but produced NO .class files.\n"
                            + "This usually means ECJ rejected the -source/-target arguments for Java version '"
                            + javaVer + "' as unsupported by the bundled compiler version, and failed at the "
                            + "argument-parsing stage before compiling anything (a failure mode ECJ does not "
                            + "count in globalErrorsCount).\n\n"
                            + "Eclipse compiler stdout:\n" + outOutputStream.getOut()
                            + "\n\nEclipse compiler stderr:\n" + errOutputStream.getOut();
                    LogUtil.e(TAG, "compileJavaCode produced zero .class files despite reporting success");
                    broadcastBuildOutput(diagnostic);
                    throw new zy(diagnostic);
                }
                LogUtil.d(TAG, "System.err of Eclipse compiler: " + errOutputStream.getOut());
                LogUtil.d(TAG, "Compiling Java files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
            } else {
                String errorOutput = errOutputStream.getOut();
                if (!compileSucceeded) {
                    errorOutput = "Eclipse compiler reported a configuration failure (invalid arguments, "
                            + "unsupported -source/-target '" + javaVer + "', or similar) before any file was "
                            + "compiled.\n\n" + errorOutput;
                }
                LogUtil.e(TAG, "Failed to compile Java files");
                broadcastBuildOutput(errorOutput);
                throw new zy(errorOutput);
            }
        }
    }

    private static boolean directoryContainsAnyFile(File dir, String suffix) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isDirectory()) {
                if (directoryContainsAnyFile(child, suffix)) return true;
            } else if (child.getName().endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    public void buildApk() throws By {
        String firstDexPath = dexesToAddButNotMerge.isEmpty() ? yq.classesDexPath : dexesToAddButNotMerge.remove(0).getAbsolutePath();
        try {
            ApkBuilder apkBuilder = new ApkBuilder(new File(yq.unsignedUnalignedApkPath), new File(yq.resourcesApkPath), new File(firstDexPath), null, null, System.out);

            for (Jp library : builtInLibraryManager.getLibraries()) {
                apkBuilder.addResourcesFromJar(BuiltInLibraries.getLibraryClassesJarPath(library.getName()));
            }

            for (String jarPath : mll.getJarLocalLibrary().split(":")) {
                if (!jarPath.trim().isEmpty()) {
                    apkBuilder.addResourcesFromJar(new File(jarPath));
                }
            }

            File nativeLibrariesDirectory = new File(fpu.getPathNativelibs(yq.sc_id));
            if (nativeLibrariesDirectory.exists()) {
                apkBuilder.addNativeLibraries(nativeLibrariesDirectory);
            }

            for (String nativeLibraryDirectory : mll.getNativeLibs()) {
                apkBuilder.addNativeLibraries(new File(nativeLibraryDirectory));
            }

            for (Jp library : builtInLibraryManager.getLibraries()) {
                File builtInNativeLibraries = BuiltInLibraries.getLibraryNativeLibraries(library.getName());
                if (builtInNativeLibraries.isDirectory()) {
                    apkBuilder.addNativeLibraries(builtInNativeLibraries);
                }
            }
            
            if (CppExporter.hasCompiledNativeLibs(yq)) {
                apkBuilder.addNativeLibraries(new File(yq.compiledNativeLibsPath));
            }

            if (dexesToAddButNotMerge.isEmpty()) {
                List<String> dexFiles = FileUtil.listFiles(yq.binDirectoryPath, "dex");
                for (String dexFile : dexFiles) {
                    if (!Uri.fromFile(new File(dexFile)).getLastPathSegment().equals("classes.dex")) {
                        apkBuilder.addFile(new File(dexFile), Uri.parse(dexFile).getLastPathSegment());
                    }
                }
            } else {
                int dexNumber = 2;

                for (File dexFile : dexesToAddButNotMerge) {
                    apkBuilder.addFile(dexFile, "classes" + dexNumber + ".dex");
                    dexNumber++;
                }
            }

            apkBuilder.setDebugMode(false);
            apkBuilder.sealApk();
        } catch (ApkCreationException | SealedApkException e) {
            throw new By(e.getMessage());
        } catch (DuplicateFileException e) {
            String message = "Duplicate files from two libraries detected \r\n";
            message += "File1: " + e.getFile1() + " \r\n";
            message += "File2: " + e.getFile2() + " \r\n";
            message += "Archive path: " + e.getArchivePath();
            throw new By(message);
        }
        LogUtil.d(TAG, "Time passed since starting to compile resources until building the unsigned APK: " +
                (System.currentTimeMillis() - timestampResourceCompilationStarted) + " ms");
    }

    public void getDexFilesReady() throws Exception {
        long savedTimeMillis = System.currentTimeMillis();
        ArrayList<File> dexes = new ArrayList<>();
        
        boolean isR8Running = isR8ActiveForCurrentBuild();

        if (!isR8Running) {
            if (settings.getMinSdkVersion() < 21) {
                dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.ANDROIDX_MULTIDEX));
            }

            if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                    .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE)) {
                dexes.add(BuiltInLibraries.getLibraryDexFile(BuiltInLibraries.HTTP_LEGACY_ANDROID));
            }

            for (Jp builtInLibrary : builtInLibraryManager.getLibraries()) {
                dexes.add(BuiltInLibraries.getLibraryDexFile(builtInLibrary.getName()));
            }
        } else {
            for (Jp builtInLibrary : builtInLibraryManager.getLibraries()) {
                if (!builtInLibraryManager.isDirectlyRequired(builtInLibrary.getName())) {
                    dexes.add(BuiltInLibraries.getLibraryDexFile(builtInLibrary.getName()));
                }
            }
        }

        ArrayList<HashMap<String, Object>> list = mll.list;
        for (int i1 = 0, listSize = list.size(); i1 < listSize; i1++) {
            HashMap<String, Object> localLibrary = list.get(i1);
            Object localLibraryName = localLibrary.get("name");

            if (localLibraryName instanceof String) {
                Object localLibraryDexPath = localLibrary.get("dexPath");

                if (localLibraryDexPath instanceof String) {
                    if (!proguard.libIsProguardFMEnabled((String) localLibraryName)) {
                        dexes.add(new File((String) localLibraryDexPath));
                        File localLibraryDirectory = new File((String) localLibraryDexPath).getParentFile();

                        if (localLibraryDirectory != null) {
                            File[] localLibraryFiles = localLibraryDirectory.listFiles();

                            if (localLibraryFiles != null) {
                                for (File localLibraryFile : localLibraryFiles) {
                                    String filename = localLibraryFile.getName();

                                    if (!filename.equals("classes.dex")
                                            && filename.startsWith("classes") && filename.endsWith(".dex")) {
                                        dexes.add(localLibraryFile);
                                    }
                                }
                            }
                        }
                    }
                } else if (!(localLibrary.get("jarPath") instanceof String)) {
                    SketchwareUtil.toastError("Invalid DEX file path of enabled Local library #" + i1, Toast.LENGTH_LONG);
                }
            } else {
                SketchwareUtil.toastError("Invalid name of enabled Local library #" + i1, Toast.LENGTH_LONG);
            }
        }

        File applicationDexDirectory = new File(yq.binDirectoryPath, "dex");
        ArrayList<File> applicationDexFiles = new ArrayList<>();
        File[] applicationDexEntries = applicationDexDirectory.listFiles(
                (dir, name) -> name.matches("classes([0-9]+)?\\.dex"));
        if (applicationDexEntries != null) {
            for (File dexFile : applicationDexEntries) {
                if (dexFile.isFile() && dexFile.length() > 0L) {
                    applicationDexFiles.add(dexFile);
                }
            }
        }
        applicationDexFiles.sort((left, right) -> Integer.compare(
                getDexOutputSequence(left.getName()), getDexOutputSequence(right.getName())));

        if (isR8Running && applicationDexFiles.isEmpty()) {
            throw new IOException("R8 DEX output disappeared before APK assembly. Expected classes.dex under "
                    + applicationDexDirectory.getAbsolutePath() + "; refusing to package an APK without app classes.");
        }
        if (!isR8Running && applicationDexFiles.isEmpty()) {
            throw new IOException("D8/Dx produced no classes*.dex files under "
                    + applicationDexDirectory.getAbsolutePath() + ". Refusing to package an APK without application bytecode.");
        }
        for (File applicationDexFile : applicationDexFiles) {
            validateDexOutputFile(applicationDexFile, isR8Running ? "R8" : (isD8Enabled() ? "D8" : "Dx"));
        }
        dexes.addAll(0, applicationDexFiles);

        LogUtil.d(TAG, "DEX assembly order (application output first): " + dexes);

        if (settings.getMinSdkVersion() < 21 || !yq.N.isDebugBuild) {
            dexLibraries(new File(yq.binDirectoryPath), dexes);
            LogUtil.d(TAG, "Merging DEX files took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
        } else {
            dexesToAddButNotMerge = dexes;
            LogUtil.d(TAG, "Skipped merging DEX files due to debug build with minSdkVersion >= 21");
        }
    }

    public void maybeExtractAapt2() throws By {
        var abi = Build.SUPPORTED_ABIS[0];
        try {
            if (hasFileChanged("aapt/aapt2-" + abi, aapt2Binary.getAbsolutePath())) {
                Os.chmod(aapt2Binary.getAbsolutePath(), S_IRUSR | S_IWUSR | S_IXUSR);
            }
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to extract AAPT2 binaries", e);
            throw new By(
                    e instanceof FileNotFoundException fileNotFoundException ?
                            "Looks like the device's architecture (" + abi + ") isn't supported.\n"
                                    + Log.getStackTraceString(fileNotFoundException)
                            : "Couldn't extract AAPT2 binaries! Message: " + e.getMessage()
            );
        }
    }

    public void buildBuiltInLibraryInformation() {
        if (yq.N.g) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_APPCOMPAT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.ANDROIDX_COORDINATORLAYOUT);
            builtInLibraryManager.addLibrary(BuiltInLibraries.MATERIAL);
        }
        if (yq.N.isFirebaseEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_COMMON);
        }
        if (yq.N.isFirebaseAuthUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_AUTH);
        }
        if (yq.N.isFirebaseDatabaseUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_DATABASE);
        }
        if (yq.N.isFirebaseStorageUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.FIREBASE_STORAGE);
        }
        if (yq.N.isMapUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_MAPS);
        }
        if (yq.N.isAdMobEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.PLAY_SERVICES_ADS);
        }
        if (yq.N.isUnityAdsEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.UNITY_ADS);
        }
        if (yq.N.isGoogleBillingEnabled) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.BILLING);
        }
        if (yq.N.isGsonUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GSON);
        }
        if (yq.N.isGlideUsed) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.GLIDE);
        }
        if (yq.N.isHttp3Used) {
            builtInLibraryManager.addLibrary(BuiltInLibraries.OKHTTP_ANDROID);
        }

        KotlinCompilerBridge.maybeAddKotlinBuiltInLibraryDependenciesIfPossible(this, builtInLibraryManager);

        ExtLibSelected.addUsedDependencies(yq.N.x, builtInLibraryManager);
    }

    public BuiltInLibraryManager getBuiltInLibraryManager() {
        return builtInLibraryManager;
    }

    public void signDebugApk() throws GeneralSecurityException, IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
        TestkeySignBridge.signWithTestkey(yq.unsignedUnalignedApkPath, yq.finalToInstallApkPath);
    }

    private void mergeDexes(File target, List<Dex> dexes) throws IOException {
        DexMerger merger = new DexMerger(dexes.toArray(new Dex[0]), CollisionPolicy.KEEP_FIRST, new DxContext());
        merger.merge().writeTo(target);
    }

    private void proguardAddLibConfigs(List<String> args) {
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File config = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (config.exists()) {
                args.add("-include");
                args.add(config.getAbsolutePath());
            }
        }
    }

    private void proguardAddRjavaRules(List<String> args) throws IOException {
        File generatedRules = new File(yq.proguardAutoGeneratedExclusions);
        File parent = generatedRules.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
            throw new IOException("Could not create generated ProGuard rules directory: " + parent.getAbsolutePath());
        }
        FileUtil.writeFile(yq.proguardAutoGeneratedExclusions, getRJavaRules());
        if (!generatedRules.isFile() || generatedRules.length() == 0L) {
            throw new IOException("Generated ProGuard rules file is missing or empty: " + generatedRules.getAbsolutePath());
        }
        args.add("-include");
        args.add(generatedRules.getAbsolutePath());
    }

    /**
     * Returns app entry-point and XML-inflated custom classes which must be roots for the
     * standalone R8 invocation. Unlike the Android Gradle Plugin, this custom build path does
     * not receive AAPT2's manifest-derived keep rules automatically. Without these exact roots,
     * R8 can successfully shrink a project down to only META-INF and silently remove its Activity.
     */
    private ArrayList<String> getApplicationKeepClassesForCurrentBuild() throws IOException {
        if (cachedApplicationKeepClassesForCurrentBuild != null) {
            return new ArrayList<>(cachedApplicationKeepClassesForCurrentBuild);
        }

        File compiledClassesDir = new File(yq.compiledClassesPath);
        File manifestFile = new File(yq.androidManifestPath);
        if (!manifestFile.isFile() || manifestFile.length() == 0L) {
            throw new IOException("Cannot build R8 keep rules: project AndroidManifest.xml is missing or empty: "
                    + manifestFile.getAbsolutePath());
        }

        LinkedHashSet<String> roots = new LinkedHashSet<>();
        String[] manifestPackage = new String[] { yq.packageName };
        collectManifestEntryClasses(manifestFile, compiledClassesDir, manifestPackage, roots);

        File resourceDirectory = new File(yq.resDirectoryPath);
        File[] resourceEntries = resourceDirectory.listFiles();
        if (resourceEntries != null) {
            for (File resourceEntry : resourceEntries) {
                String resourceName = resourceEntry.getName();
                if (resourceEntry.isDirectory()
                        && ("layout".equals(resourceName) || resourceName.startsWith("layout-"))) {
                    collectLayoutInflatedClasses(resourceEntry, compiledClassesDir, manifestPackage[0], roots);
                }
            }
        } else if (resourceDirectory.exists()) {
            throw new IOException("Cannot list project resource directory while generating R8 keep rules: "
                    + resourceDirectory.getAbsolutePath());
        }

        cachedApplicationKeepClassesForCurrentBuild = new ArrayList<>(roots);
        LogUtil.d(TAG, "R8 app roots discovered from manifest/layout XML: " + roots.size()
                + (roots.isEmpty() ? " (no compiled local component roots detected)" : ": " + roots));
        return new ArrayList<>(cachedApplicationKeepClassesForCurrentBuild);
    }

    private void collectManifestEntryClasses(File manifestFile, File compiledClassesDir,
                                             String[] manifestPackage, Set<String> roots) throws IOException {
        final String androidNamespace = "http://schemas.android.com/apk/res/android";
        try (FileInputStream input = new FileInputStream(manifestFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(input, StandardCharsets.UTF_8.name());
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String tag = parser.getName();
                if ("manifest".equals(tag)) {
                    String declaredPackage = parser.getAttributeValue(null, "package");
                    if (declaredPackage != null && !declaredPackage.trim().isEmpty()
                            && declaredPackage.indexOf('$') < 0) {
                        manifestPackage[0] = declaredPackage.trim();
                    }
                    continue;
                }

                boolean applicationComponent = "application".equals(tag)
                        || "activity".equals(tag)
                        || "service".equals(tag)
                        || "receiver".equals(tag)
                        || "provider".equals(tag)
                        || "instrumentation".equals(tag);
                if (applicationComponent) {
                    addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "name"),
                            manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                    if ("activity".equals(tag)) {
                        addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "parentActivityName"),
                                manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                    }
                    if ("application".equals(tag)) {
                        addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "appComponentFactory"),
                                manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                        addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "backupAgent"),
                                manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                        addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "manageSpaceActivity"),
                                manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                    }
                } else if ("activity-alias".equals(tag)) {
                    addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "targetActivity"),
                            manifestPackage[0], compiledClassesDir, roots, manifestFile.getAbsolutePath());
                }
            }
        } catch (XmlPullParserException | RuntimeException parseFailure) {
            throw new IOException("Could not parse project AndroidManifest.xml for R8 component keep rules: "
                    + manifestFile.getAbsolutePath() + ": " + parseFailure.getMessage(), parseFailure);
        }
    }

    private void collectLayoutInflatedClasses(File directory, File compiledClassesDir,
                                              String manifestPackage, Set<String> roots) throws IOException {
        File[] entries = directory.listFiles();
        if (entries == null) {
            throw new IOException("Cannot read layout directory while generating R8 keep rules: "
                    + directory.getAbsolutePath());
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collectLayoutInflatedClasses(entry, compiledClassesDir, manifestPackage, roots);
            } else if (entry.isFile() && entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
                collectOneLayoutInflatedClasses(entry, compiledClassesDir, manifestPackage, roots);
            }
        }
    }

    private void collectOneLayoutInflatedClasses(File layoutFile, File compiledClassesDir,
                                                 String manifestPackage, Set<String> roots) throws IOException {
        final String androidNamespace = "http://schemas.android.com/apk/res/android";
        try (FileInputStream input = new FileInputStream(layoutFile)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
            parser.setInput(input, StandardCharsets.UTF_8.name());
            int event;
            while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
                if (event != XmlPullParser.START_TAG) continue;
                String tag = parser.getName();
                if (tag != null && tag.indexOf('.') > 0) {
                    addCompiledApplicationRoot(tag, manifestPackage, compiledClassesDir, roots,
                            layoutFile.getAbsolutePath());
                }

                if ("view".equals(tag) || "fragment".equals(tag)
                        || "androidx.fragment.app.FragmentContainerView".equals(tag)) {
                    addCompiledApplicationRoot(parser.getAttributeValue(null, "class"), manifestPackage,
                            compiledClassesDir, roots, layoutFile.getAbsolutePath());
                    addCompiledApplicationRoot(parser.getAttributeValue(androidNamespace, "name"),
                            manifestPackage, compiledClassesDir, roots, layoutFile.getAbsolutePath());
                }
            }
        } catch (XmlPullParserException | RuntimeException parseFailure) {
            throw new IOException("Could not parse layout XML while generating R8 custom-view keep rules: "
                    + layoutFile.getAbsolutePath() + ": " + parseFailure.getMessage(), parseFailure);
        }
    }

    private void addCompiledApplicationRoot(String rawName, String manifestPackage,
                                            File compiledClassesDir, Set<String> roots,
                                            String sourcePath) throws IOException {
        if (rawName == null) return;
        String candidate = rawName.trim();
        if (candidate.isEmpty() || candidate.startsWith("${") || candidate.startsWith("@")) return;

        String className;
        if (candidate.startsWith(".")) {
            className = manifestPackage + candidate;
        } else if (candidate.indexOf('.') < 0) {
            className = manifestPackage + "." + candidate;
        } else {
            className = candidate;
        }

        if (!className.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")) return;
        File classFile = new File(compiledClassesDir,
                className.replace('.', File.separatorChar) + ".class");
        if (classFile.isFile()) {
            roots.add(className);
            return;
        }

        String appPackage = yq.packageName == null ? "" : yq.packageName.trim();
        if (!appPackage.isEmpty()
                && (className.equals(appPackage) || className.startsWith(appPackage + "."))) {
            throw new IOException("R8 cannot preserve component '" + className + "' because its .class file is "
                    + "missing from the Java compiler output. Reference source: " + sourcePath
                    + ". Check whether the class compiled successfully and whether its package matches.");
        }
    }

    private String getRJavaRules() throws IOException {
        StringBuilder sb = new StringBuilder("# R.java rules\n");
        for (Jp jp : builtInLibraryManager.getLibraries()) {
            if (jp.hasResources() && !jp.getPackageName().isEmpty()) {
                sb.append("-keep class ").append(jp.getPackageName()).append(".R { *; }\n");
                sb.append("-keep class ").append(jp.getPackageName()).append(".R$* { *; }\n");
            }
        }
        for (HashMap<String, Object> hashMap : mll.list) {
            if (hashMap.containsKey("packageName")) {
                String pkg = hashMap.get("packageName").toString();
                sb.append("-keep class ").append(pkg).append(".R { *; }\n");
                sb.append("-keep class ").append(pkg).append(".R$* { *; }\n");
            }
        }
        sb.append("-keep class ").append(yq.packageName).append(".R { *; }\n");
        sb.append("-keep class ").append(yq.packageName).append(".R$* { *; }\n");

        List<String> applicationRoots = getApplicationKeepClassesForCurrentBuild();
        if (!applicationRoots.isEmpty()) {
            sb.append("\n# Exact app component and XML-inflated class roots. Generated because this is a standalone R8 invocation.\n");
            for (String className : applicationRoots) {
                sb.append("-keep class ").append(className).append(" { *; }\n");
            }
        }
        return sb.toString();
    }

    private ArrayList<String> normalizeR8InputFiles(Collection<String> inputs, String role) throws IOException {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String rawPath : inputs) {
            if (rawPath == null || rawPath.trim().isEmpty()) {
                throw new IOException("R8 " + role + " input contains an empty file path.");
            }
            File input = new File(rawPath);
            if (!input.isFile() || !input.canRead() || input.length() <= 0L) {
                throw new IOException("R8 " + role + " input is missing, unreadable, or empty: "
                        + input.getAbsolutePath());
            }
            unique.add(input.getCanonicalPath());
        }
        return new ArrayList<>(unique);
    }

    private boolean isR8ActiveForCurrentBuild() {
        return !useProguardFallbackForCurrentBuild
                && proguard.isShrinkingEnabled()
                && proguard.isR8Enabled();
    }

    /**
     * Use the established ProGuard + D8/Dx path only after the isolated R8 service explicitly
     * reports OutOfMemoryError. This fallback applies to this ProjectBuilder instance/build only;
     * the user's saved R8 preference is not changed.
     */
    public void runProguardFallbackAfterR8OutOfMemory() throws IOException {
        useProguardFallbackForCurrentBuild = true;
        File dexOutputDir = new File(yq.binDirectoryPath, "dex");
        if (dexOutputDir.exists()) {
            File[] dexFiles = dexOutputDir.listFiles((dir, name) -> name.matches("classes(?:[0-9]+)?\\.dex"));
            if (dexFiles != null) {
                for (File dexFile : dexFiles) {
                    if (!dexFile.delete() && dexFile.exists()) {
                        throw new IOException("Could not remove partial R8 output before fallback: " + dexFile.getName());
                    }
                }
            }
        } else if (!dexOutputDir.mkdirs() && !dexOutputDir.isDirectory()) {
            throw new IOException("Could not create DEX output directory for ProGuard fallback.");
        }
        File staleProguardOutput = new File(yq.proguardClassesPath);
        if (staleProguardOutput.exists() && !staleProguardOutput.delete() && staleProguardOutput.exists()) {
            throw new IOException("Could not remove stale ProGuard output before fallback.");
        }
        runProguard();
    }

    private boolean isR8ServiceRunning(Context context) {
        android.app.ActivityManager manager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return false;
        try {
            for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (service.service != null && R8Service.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            LogUtil.w(TAG, "Could not query R8 service state: " + e.getMessage());
        }
        return false;
    }

    private JSONArray toJsonArray(Collection<String> values) {
        return new JSONArray(values);
    }

    public void runR8() throws IOException {
        useProguardFallbackForCurrentBuild = false;
        long savedTimeMillis = System.currentTimeMillis();

        ArrayList<String> config = new ArrayList<>();
        config.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);
        config.add(yq.proguardAaptRules);
        config.add(proguard.getCustomProguardRules());
        if (proguard.isKeepLineNumbersEnabled()) {
            config.add(ProguardHandler.LINE_NUMBER_RULES_PATH);
        }
        ArrayList<String> rules = new ArrayList<>(Arrays.asList(getRJavaRules().split("\n")));
        for (Jp library : builtInLibraryManager.getLibraries()) {
            File f = BuiltInLibraries.getLibraryProguardConfiguration(library.getName());
            if (f.exists()) config.add(f.getAbsolutePath());
        }
        config.addAll(mll.getPgRules());

        ArrayList<String> programJars = new ArrayList<>();
        ArrayList<String> libraryJars = new ArrayList<>();

        File compiledClassesDir = new File(yq.compiledClassesPath);
        File[] compiledClassEntries = compiledClassesDir.listFiles();
        if (!compiledClassesDir.isDirectory() || compiledClassEntries == null || compiledClassEntries.length == 0) {
            throw new IOException("Compiled classes directory is empty or does not exist. Compilation might have failed silently.");
        }

        try {
            JarBuilder.INSTANCE.generateJar(compiledClassesDir);
            File classesJar = new File(yq.compiledClassesPath + ".jar");
            if (!classesJar.isFile() || classesJar.length() == 0L) {
                throw new IOException("Generated classes JAR is missing or empty.");
            }
            programJars.add(classesJar.getAbsolutePath());
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to build temp JAR for R8", e);
            throw new IOException("Failed to package the project's own compiled classes for R8. R8 cannot run without this input.", e);
        }

        for (Jp library : builtInLibraryManager.getLibraries()) {
            String classesJarPath = BuiltInLibraries.getLibraryClassesJarPathString(library.getName());
            File classesJar = new File(classesJarPath);
            if (!classesJar.isFile()) {
                throw new IOException("Required built-in library JAR is missing: " + library.getName() + " (" + classesJarPath + ")");
            }
            if (builtInLibraryManager.isDirectlyRequired(library.getName())) programJars.add(classesJarPath);
            else libraryJars.add(classesJarPath);
        }

        if (settings.getMinSdkVersion() < 21) {
            programJars.add(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.ANDROIDX_MULTIDEX));
        }

        if (!build_settings.getValue(BuildSettings.SETTING_NO_HTTP_LEGACY, ProjectSettings.SETTING_GENERIC_VALUE_FALSE)
                .equals(ProjectSettings.SETTING_GENERIC_VALUE_TRUE)) {
            programJars.add(BuiltInLibraries.getLibraryClassesJarPathString(BuiltInLibraries.HTTP_LEGACY_ANDROID));
        }

        for (HashMap<String, Object> hashMap : mll.list) {
            if (hashMap.containsKey("jarPath")) {
                String name = String.valueOf(hashMap.get("name"));
                String jarPath = String.valueOf(hashMap.get("jarPath"));
                if (proguard.libIsProguardFMEnabled(name) || !hashMap.containsKey("dexPath")) programJars.add(jarPath);
                else libraryJars.add(jarPath);
            }
        }

        libraryJars.add(androidJarPath);
        String javaVer = build_settings.getValue(BuildSettings.SETTING_JAVA_VERSION, BuildSettings.SETTING_JAVA_VERSION_1_8);
        if (!javaVer.equals(BuildSettings.SETTING_JAVA_VERSION_1_7)) {
            libraryJars.add(new File(BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH, "core-lambda-stubs.jar").getAbsolutePath());
        }

        String extraCpPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + yq.sc_id + "/files/classpath/";
        for (String jar : FileUtil.listFiles(extraCpPath, "jar")) libraryJars.add(jar);
        String customCp = build_settings.getValue(BuildSettings.SETTING_CLASSPATH, "");
        if (!customCp.isEmpty()) libraryJars.addAll(Arrays.asList(customCp.split(":")));

        programJars = normalizeR8InputFiles(programJars, "program");
        libraryJars = normalizeR8InputFiles(libraryJars, "library");
        Set<String> programPathSet = new java.util.HashSet<>(programJars);
        int libraryOverlapCount = 0;
        for (java.util.Iterator<String> iterator = libraryJars.iterator(); iterator.hasNext();) {
            if (programPathSet.contains(iterator.next())) {
                iterator.remove();
                libraryOverlapCount++;
            }
        }
        if (programJars.isEmpty()) {
            throw new IOException("R8 has no program inputs after de-duplication; refusing to create an empty APK.");
        }
        if (libraryJars.isEmpty()) {
            throw new IOException("R8 has no library classpath inputs after de-duplication.");
        }
        LogUtil.d(TAG, "R8 input plan: programJars=" + programJars.size()
                + ", libraryJars=" + libraryJars.size()
                + ", duplicate program/library paths removed=" + libraryOverlapCount
                + ", expected app roots=" + getApplicationKeepClassesForCurrentBuild().size());

        File argsFile = new File(yq.binDirectoryPath, "r8_args.json");
        File argsTempFile = new File(yq.binDirectoryPath, "r8_args.json.tmp");
        JSONObject args = new JSONObject();
        try {
            args.put("rules", toJsonArray(rules));
            args.put("config_files", toJsonArray(config));
            args.put("library_inputs", toJsonArray(libraryJars));
            args.put("program_inputs", toJsonArray(programJars));
            args.put("expected_classes", toJsonArray(getApplicationKeepClassesForCurrentBuild()));
            args.put("min_api", settings.getMinSdkVersion());
            args.put("multi_dex", isMultiDexEnabled);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(argsTempFile), StandardCharsets.UTF_8)) {
                writer.write(args.toString());
                writer.flush();
            }
            try {
                Files.move(argsTempFile.toPath(), argsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(argsTempFile.toPath(), argsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (JSONException | IOException e) {
            argsTempFile.delete();
            throw new IOException("Failed to write R8 arguments: " + e.getMessage(), e);
        }

        final String requestId = UUID.randomUUID().toString();
        final String[] error = new String[1];
        final boolean[] success = new boolean[1];
        final CountDownLatch latch = new CountDownLatch(1);
        final long startedAt = SystemClock.elapsedRealtime();
        final boolean[] receiverRegistered = new boolean[1];

        android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (intent == null || !R8Service.ACTION_COMPLETE.equals(intent.getAction())) return;
                String receivedId = intent.getStringExtra(R8Service.EXTRA_REQUEST_ID);
                if (!requestId.equals(receivedId)) return;
                success[0] = intent.getBooleanExtra(R8Service.EXTRA_SUCCESS, false);
                error[0] = intent.getStringExtra(R8Service.EXTRA_ERROR);
                if (!success[0] && (error[0] == null || error[0].trim().isEmpty())) {
                    error[0] = "R8 service returned an unsuccessful result without an error message.";
                }
                latch.countDown();
            }
        };

        try {
            android.content.IntentFilter filter = new android.content.IntentFilter(R8Service.ACTION_COMPLETE);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            receiverRegistered[0] = true;

            Intent intent = new Intent(context, R8Service.class);
            intent.putExtra(R8Service.EXTRA_SC_ID, yq.sc_id);
            intent.putExtra(R8Service.EXTRA_ARGS_PATH, argsFile.getAbsolutePath());
            intent.putExtra(R8Service.EXTRA_REQUEST_ID, requestId);
            try {
                context.startForegroundService(intent);
            } catch (RuntimeException startError) {
                throw new IOException("Android could not start the R8 compiler service. Check that R8Service.java is included in the installed APK.", startError);
            }

            // Do not wait forever if the service process crashes or Android kills it.
            final long timeoutMillis = TimeUnit.MINUTES.toMillis(20);
            final long startupGraceMillis = TimeUnit.SECONDS.toMillis(15);
            boolean finished = false;
            while (!finished) {
                long elapsed = SystemClock.elapsedRealtime() - startedAt;
                long remaining = timeoutMillis - elapsed;
                if (remaining <= 0L) {
                    error[0] = "R8 compilation timed out after 20 minutes. Check device memory and the build log; no successful R8 result was reported.";
                    break;
                }
                finished = latch.await(Math.min(TimeUnit.SECONDS.toMillis(2), remaining), TimeUnit.MILLISECONDS);
                if (!finished && elapsed >= startupGraceMillis && !isR8ServiceRunning(context)) {
                    error[0] = "R8 compiler service exited without reporting a result. Verify the installed APK contains " + R8Service.class.getName() + " and inspect the R8 service log.";
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("R8 compilation wait was interrupted.", e);
        } finally {
            if (receiverRegistered[0]) {
                try { context.unregisterReceiver(receiver); } catch (IllegalArgumentException ignored) { }
            }
            argsTempFile.delete();
        }

        if (error[0] != null || !success[0]) {
            String message = error[0] == null ? "R8 did not report a successful result." : error[0];
            String normalizedMessage = message.toLowerCase(java.util.Locale.ROOT);
            boolean explicitOutOfMemory = normalizedMessage.contains("r8 ran out of memory")
                    || normalizedMessage.contains("outofmemoryerror")
                    || normalizedMessage.contains("out of memory in the compiler process");
            if (explicitOutOfMemory) {
                broadcastBuildOutput("R8 reported an out-of-memory failure. Retrying this build with the existing ProGuard + D8/Dx path; your R8 setting will remain enabled for future builds.");
                if (progressReceiver != null) {
                    progressReceiver.onProgress("R8 memory limit reached; retrying with ProGuard...", 16);
                }
                try {
                    runProguardFallbackAfterR8OutOfMemory();
                    LogUtil.w(TAG, "R8 explicitly reported OOM; ProGuard fallback completed for this build.");
                    return;
                } catch (IOException fallbackFailure) {
                    fallbackFailure.addSuppressed(new IOException("Original R8 failure: " + message));
                    broadcastBuildOutput("R8 OOM fallback also failed: " + fallbackFailure.getMessage());
                    throw fallbackFailure;
                }
            }
            broadcastBuildOutput("R8 Compilation Failed:\n" + message);
            throw new IOException("R8 Compilation Failed:\n" + message);
        }

        LogUtil.d(TAG, "R8 took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void runProguard() throws IOException {
        long savedTimeMillis = System.currentTimeMillis();

        File compiledClassesDir = new File(yq.compiledClassesPath);
        if (!compiledClassesDir.exists() || compiledClassesDir.list() == null || compiledClassesDir.list().length == 0) {
            throw new IOException("Compiled classes directory is empty or does not exist. Compilation might have failed silently.");
        }

        try {
            JarBuilder.INSTANCE.generateJar(compiledClassesDir);
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to build temp JAR for Proguard", e);
        }

        ArrayList<String> args = new ArrayList<>();

        args.add("-include");
        args.add(ProguardHandler.ANDROID_PROGUARD_RULES_PATH);

        args.add("-include");
        args.add(yq.proguardAaptRules);

        args.add("-include");
        args.add(proguard.getCustomProguardRules());

        if (proguard.isKeepLineNumbersEnabled()) {
            args.add("-include");
            args.add(ProguardHandler.LINE_NUMBER_RULES_PATH);
        }

        proguardAddLibConfigs(args);
        proguardAddRjavaRules(args);

        for (String rule : mll.getPgRules()) {
            args.add("-include");
            args.add(rule);
        }

        args.add("-injars");
        File tempJar = new File(yq.compiledClassesPath + ".jar");
        if (tempJar.exists()) {
             args.add(tempJar.getAbsolutePath());
        } else {
             args.add(yq.compiledClassesPath);
        }

        for (HashMap<String, Object> hashMap : mll.list) {
            String obj = hashMap.get("name").toString();
            if (hashMap.containsKey("jarPath") && proguard.libIsProguardFMEnabled(obj)) {
                args.add("-injars");
                args.add(hashMap.get("jarPath").toString());
            }
        }
        args.add("-libraryjars");
        args.add(getProguardClasspath());
        args.add("-outjars");
        args.add(yq.proguardClassesPath);
        if (proguard.isDebugFilesEnabled()) {
            args.add("-printseeds");
            args.add(yq.proguardSeedsPath);
            args.add("-printusage");
            args.add(yq.proguardUsagePath);
            args.add("-printmapping");
            args.add(yq.proguardMappingPath);
        }
        LogUtil.d(TAG, "About to run ProGuard with these arguments: " + args);

        Configuration configuration = new Configuration();

        try {
            ConfigurationParser parser = new ConfigurationParser(args.toArray(new String[0]), System.getProperties());
            try {
                parser.parse(configuration);
            } finally {
                parser.close();
            }
        } catch (ParseException e) {
            throw new IOException(e);
        }

        try {
            new ProGuard(configuration).execute();
        } catch (Exception e) {
            throw new IOException(e);
        }

        LogUtil.d(TAG, "ProGuard took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void runStringfog() {
        try {
            StringFogMappingPrinter stringFogMappingPrinter = new StringFogMappingPrinter(new File(yq.binDirectoryPath,
                    "stringFogMapping.txt"));
            StringFogClassInjector stringFogClassInjector = new StringFogClassInjector(new String[0],
                    "UTF-8",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    "com.github.megatronking.stringfog.xor.StringFogImpl",
                    stringFogMappingPrinter);
            stringFogMappingPrinter.startMappingOutput();
            stringFogMappingPrinter.ouputInfo("UTF-8", "com.github.megatronking.stringfog.xor.StringFogImpl");
            stringFogClassInjector.doFog2ClassInDir(new File(yq.compiledClassesPath));
            KB.a(context, "stringfog/stringfog.zip", yq.compiledClassesPath);
        } catch (Exception e) {
            LogUtil.e("StringFog", "Failed to run StringFog", e);
        }
    }

    public void runZipalign(String inPath, String outPath) throws By {
        LogUtil.d(TAG, "About to zipalign " + inPath + " to " + outPath);
        long savedTimeMillis = System.currentTimeMillis();

        try (RandomAccessFile in = new RandomAccessFile(inPath, "r");
             FileOutputStream out = new FileOutputStream(outPath)) {
            ZipAlign.alignZip(in, out);
        } catch (IOException e) {
            throw new By("Couldn't run zipalign on " + inPath + " with output path " + outPath + ": " + Log.getStackTraceString(e));
        } catch (InvalidZipException e) {
            throw new By("Failed to zipalign due to the given zip being invalid: " + Log.getStackTraceString(e));
        }

        LogUtil.d(TAG, "zipalign took " + (System.currentTimeMillis() - savedTimeMillis) + " ms");
    }

    public void setBuildAppBundle(boolean buildAppBundle) {
        this.buildAppBundle = buildAppBundle;
    }
}
