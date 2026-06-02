package mod.jbk.build.compiler.dex;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.LinkedList;

import a.a.a.ProjectBuilder;
import mod.hey.studios.project.ProjectSettings;
import mod.pranav.build.JarBuilder;
import mod.jbk.util.LogUtil;
import neo.sketchware.utility.FileUtil;

public class DexCompiler {
    public static void compileDexFiles(ProjectBuilder builder) throws CompilationFailedException {
        int minApiLevel;

        try {
            minApiLevel = Integer.parseInt(builder.settings.getValue(
                    ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            throw new CompilationFailedException("Invalid minSdkVersion specified in Project Settings: " + e.getMessage());
        }

        Collection<Path> programFiles = new LinkedList<>();
        
        if (builder.proguard.isShrinkingEnabled()) {
            programFiles.add(Paths.get(builder.yq.proguardClassesPath));
        } else {
            File compiledClassesDir = new File(builder.yq.compiledClassesPath);
            if (compiledClassesDir.exists() && compiledClassesDir.list() != null && compiledClassesDir.list().length > 0) {
                try {
                    File tempJar = new File(builder.yq.compiledClassesPath + ".jar");
                    // Generating JAR natively before sending to D8 is much safer
                    JarBuilder.INSTANCE.generateJar(compiledClassesDir);
                    if (tempJar.exists()) {
                        programFiles.add(tempJar.toPath());
                        LogUtil.d("DexCompiler", "D8 Program File added as JAR to prevent class drop (Java 11/17 safe)");
                    } else {
                        // Fallback
                        for (File file : FileUtil.listFilesRecursively(compiledClassesDir, ".class")) {
                            programFiles.add(file.toPath());
                        }
                    }
                } catch (Exception e) {
                    LogUtil.e("DexCompiler", "Failed to build temp JAR for D8", e);
                    // Fallback
                    for (File file : FileUtil.listFilesRecursively(compiledClassesDir, ".class")) {
                        programFiles.add(file.toPath());
                    }
                }
            }
        }

        Collection<Path> libraryFiles = new LinkedList<>();
        for (String jarPath : builder.getClasspath().split(":")) {
            if (!jarPath.trim().isEmpty()) {
                libraryFiles.add(Paths.get(jarPath));
            }
        }

        // MultiDex generation support
        // D8 requires OutputMode.DexIndexed without a main dex list to automatically generate classesX.dex
        D8Command.Builder commandBuilder = D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setIntermediate(false)
                .setMinApiLevel(minApiLevel)
                .setDisableDesugaring(false) // Always keep desugaring ENABLED (setDisable=false) for backwards compatibility
                .addLibraryFiles(libraryFiles)
                .setOutput(new File(builder.yq.binDirectoryPath, "dex").toPath(), OutputMode.DexIndexed)
                .addProgramFiles(programFiles);

        if (builder.isMultiDexEnabled()) {
            LogUtil.d("DexCompiler", "MultiDex is enabled for D8 compilation");
        }

        D8.run(commandBuilder.build());
    }
}
