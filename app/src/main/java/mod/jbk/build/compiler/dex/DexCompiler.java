package mod.jbk.build.compiler.dex;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.OutputMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import a.a.a.ProjectBuilder;
import mod.hey.studios.project.ProjectSettings;
import mod.jbk.util.LogUtil;

public class DexCompiler {
    private static final String TAG = "DexCompiler";

    public static void compileDexFiles(ProjectBuilder builder) throws CompilationFailedException {
        int minApiLevel;
        try {
            minApiLevel = Integer.parseInt(builder.settings.getValue(
                    ProjectSettings.SETTING_MINIMUM_SDK_VERSION, "21"));
        } catch (NumberFormatException e) {
            throw new CompilationFailedException("Invalid minSdkVersion specified in Project Settings: " + e.getMessage());
        }
        if (minApiLevel < 1 || minApiLevel > 100) {
            throw new CompilationFailedException("Invalid minSdkVersion for D8: " + minApiLevel);
        }

        File binDirectory = new File(builder.yq.binDirectoryPath);
        File outputDirectory = new File(binDirectory, "dex");
        File stagingDirectory = new File(binDirectory, "dex-staging");
        ensureDirectory(binDirectory, "build output");
        ensureDirectory(outputDirectory, "DEX output");
        try {
            deleteRecursively(stagingDirectory);
        } catch (IOException e) {
            throw new CompilationFailedException("Could not clear stale DEX staging output: " + e.getMessage());
        }
        ensureDirectory(stagingDirectory, "DEX staging");

        try {
            List<java.nio.file.Path> programFiles = collectApplicationInputs(builder);
            Set<String> jarOnlyLibraries = collectJarOnlyLibraries(builder);
            for (String jarPath : jarOnlyLibraries) {
                File jar = new File(jarPath);
                if (isIncludedInProguardOutput(builder, jarPath)) {
                    LogUtil.d(TAG, "Local JAR is already included in the verified ProGuard input; not adding it twice: "
                            + jar.getName());
                    continue;
                }
                int classCount = countClassEntries(jar);
                if (classCount == 0) {
                    LogUtil.w(TAG, "Skipping a valid local JAR with no .class entries during D8 conversion: "
                            + jar.getAbsolutePath());
                    continue;
                }
                programFiles.add(jar.toPath());
                LogUtil.d(TAG, "Added local JAR as D8 program input: " + jar.getName() + " (" + classCount + " classes)");
            }

            Collection<java.nio.file.Path> libraryFiles = collectLibraryInputs(builder, jarOnlyLibraries);
            if (programFiles.isEmpty()) {
                throw new CompilationFailedException("D8 has no program inputs. No application bytecode can be packaged.");
            }
            if (libraryFiles.isEmpty()) {
                throw new CompilationFailedException("D8 has no library classpath inputs. Check the Android platform JAR and enabled library files.");
            }

            LogUtil.d(TAG, "D8 input summary: programInputs=" + programFiles.size()
                    + ", libraryInputs=" + libraryFiles.size()
                    + ", minApi=" + minApiLevel
                    + ", shrinking=" + builder.proguard.isShrinkingEnabled()
                    + ", multidex=" + builder.isMultiDexEnabled());

            runD8(programFiles, libraryFiles, stagingDirectory, minApiLevel);
            validateApplicationDex(stagingDirectory);
            replaceApplicationDexFiles(stagingDirectory, outputDirectory);
            validateApplicationDex(outputDirectory);
            LogUtil.d(TAG, "Application DEX output committed successfully to " + outputDirectory.getAbsolutePath());
        } catch (CompilationFailedException e) {
            clearDexOutputs(outputDirectory);
            throw e;
        } catch (Exception e) {
            clearDexOutputs(outputDirectory);
            throw new CompilationFailedException("Could not prepare or validate D8 inputs/outputs: " + safeMessage(e));
        } finally {
            try {
                deleteRecursively(stagingDirectory);
            } catch (IOException cleanupFailure) {
                LogUtil.w(TAG, "Could not remove temporary DEX staging directory: " + cleanupFailure.getMessage());
            }
        }
    }

    private static List<java.nio.file.Path> collectApplicationInputs(ProjectBuilder builder)
            throws CompilationFailedException {
        List<java.nio.file.Path> inputs = new ArrayList<>();
        if (builder.proguard.isShrinkingEnabled()) {
            File shrunkJar = new File(builder.yq.proguardClassesPath);
            int classCount = countClassEntries(shrunkJar);
            if (classCount == 0) {
                throw new CompilationFailedException("ProGuard completed without a usable application JAR. Expected compiled class entries in: "
                        + shrunkJar.getAbsolutePath() + ". Check the ProGuard output and keep rules; D8 was not started.");
            }
            inputs.add(shrunkJar.toPath());
            LogUtil.d(TAG, "Using verified ProGuard application JAR: " + classCount + " class entries");
            return inputs;
        }

        File classesDirectory = new File(builder.yq.compiledClassesPath);
        List<File> classFiles;
        try {
            classFiles = findClassFiles(classesDirectory);
        } catch (IOException e) {
            throw new CompilationFailedException("Could not inspect Java compiler output: " + e.getMessage());
        }
        if (classFiles.isEmpty()) {
            throw new CompilationFailedException("Java compilation produced no valid .class files under "
                    + classesDirectory.getAbsolutePath() + ". D8 cannot create application DEX without compiled classes.");
        }
        for (File classFile : classFiles) inputs.add(classFile.toPath());
        LogUtil.d(TAG, "Using " + classFiles.size() + " verified Java compiler .class files directly as D8 inputs.");
        return inputs;
    }

    private static Set<String> collectJarOnlyLibraries(ProjectBuilder builder) throws CompilationFailedException {
        Set<String> result = new LinkedHashSet<>();
        for (String value : builder.mll.getJarOnlyLibraryJars()) {
            if (value == null || value.trim().isEmpty()) continue;
            File jar = new File(value.trim());
            if (!jar.isFile() || jar.length() == 0L || !jar.canRead()) {
                throw new CompilationFailedException("A required local library JAR is missing, unreadable or empty: " + jar.getAbsolutePath());
            }
            try {
                result.add(jar.getCanonicalPath());
            } catch (IOException e) {
                throw new CompilationFailedException("Could not resolve local library JAR path: " + jar.getAbsolutePath());
            }
        }
        return result;
    }

    private static boolean isIncludedInProguardOutput(ProjectBuilder builder, String jarPath)
            throws CompilationFailedException {
        if (!builder.proguard.isShrinkingEnabled() || builder.mll.list == null) return false;
        File target = new File(jarPath);
        String canonicalTarget;
        try {
            canonicalTarget = target.getCanonicalPath();
        } catch (IOException e) {
            throw new CompilationFailedException("Could not resolve local library path before D8: " + target.getAbsolutePath());
        }
        for (HashMap<String, Object> library : builder.mll.list) {
            if (library == null) continue;
            Object pathValue = library.get("jarPath");
            Object nameValue = library.get("name");
            if (!(pathValue instanceof String) || !(nameValue instanceof String)
                    || library.get("dexPath") instanceof String) continue;
            try {
                if (canonicalTarget.equals(new File((String) pathValue).getCanonicalPath())) {
                    return builder.proguard.libIsProguardFMEnabled((String) nameValue);
                }
            } catch (IOException e) {
                throw new CompilationFailedException("Could not resolve configured local library path: " + pathValue);
            }
        }
        return false;
    }

    private static Collection<java.nio.file.Path> collectLibraryInputs(ProjectBuilder builder,
                                                                        Set<String> programJarPaths)
            throws CompilationFailedException {
        Collection<java.nio.file.Path> libraries = new LinkedHashSet<>();
        String classpath = builder.getClasspath();
        if (classpath == null || classpath.trim().isEmpty()) return libraries;

        for (String item : classpath.split(":", -1)) {
            String value = item == null ? "" : item.trim();
            if (value.isEmpty()) continue;
            File library = new File(value);
            String canonicalPath;
            try {
                canonicalPath = library.getCanonicalPath();
            } catch (IOException e) {
                throw new CompilationFailedException("Could not resolve D8 library classpath input: " + library.getAbsolutePath());
            }
            if (programJarPaths.contains(canonicalPath)) continue;
            if (!library.exists() || (library.isFile() && library.length() == 0L)) {
                throw new CompilationFailedException("A D8 library classpath input is missing or empty: " + library.getAbsolutePath());
            }
            if (library.isFile() && (library.getName().endsWith(".jar") || library.getName().endsWith(".zip"))) {
                try (JarFile ignored = new JarFile(library)) {
                } catch (IOException e) {
                    throw new CompilationFailedException("A D8 library classpath archive is invalid: " + library.getAbsolutePath()
                            + " (" + e.getMessage() + ")");
                }
            }
            libraries.add(library.toPath());
        }
        return libraries;
    }

    private static void runD8(Collection<java.nio.file.Path> programFiles,
                              Collection<java.nio.file.Path> libraryFiles,
                              File outputDirectory, int minApiLevel) throws CompilationFailedException {
        if (programFiles == null || programFiles.isEmpty()) {
            throw new CompilationFailedException("D8 received an empty program input list.");
        }
        if (libraryFiles == null || libraryFiles.isEmpty()) {
            throw new CompilationFailedException("D8 received an empty library classpath.");
        }
        D8Command command = D8Command.builder()
                .setMode(CompilationMode.RELEASE)
                .setIntermediate(false)
                .setMinApiLevel(minApiLevel)
                .setDisableDesugaring(false)
                .addLibraryFiles(libraryFiles)
                .setOutput(outputDirectory.toPath(), OutputMode.DexIndexed)
                .addProgramFiles(programFiles)
                .build();
        D8.run(command);
    }

    private static List<File> findClassFiles(File root) throws IOException {
        if (!root.exists() || !root.isDirectory()) {
            throw new IOException("Classes directory does not exist: " + root.getAbsolutePath());
        }
        File canonicalRoot = root.getCanonicalFile();
        List<File> result = new ArrayList<>();
        collectClassFiles(canonicalRoot, canonicalRoot, result, new HashSet<>());
        Collections.sort(result, (left, right) -> left.getAbsolutePath().compareTo(right.getAbsolutePath()));
        return result;
    }

    private static void collectClassFiles(File root, File directory, List<File> result, Set<String> visited)
            throws IOException {
        if (java.nio.file.Files.isSymbolicLink(directory.toPath())) {
            throw new IOException("Compiler output contains an unexpected symbolic link: " + directory.getAbsolutePath());
        }
        File canonicalDirectory = directory.getCanonicalFile();
        String rootPath = root.getPath() + File.separator;
        if (!canonicalDirectory.equals(root) && !canonicalDirectory.getPath().startsWith(rootPath)) {
            throw new IOException("Compiler output path escapes the classes directory: " + directory.getAbsolutePath());
        }
        if (!visited.add(canonicalDirectory.getPath())) {
            throw new IOException("Compiler output contains a recursive directory link: " + directory.getAbsolutePath());
        }
        File[] entries = directory.listFiles();
        if (entries == null) throw new IOException("Could not list compiler output directory: " + directory.getAbsolutePath());
        Arrays.sort(entries, (left, right) -> left.getName().compareTo(right.getName()));
        for (File entry : entries) {
            if (java.nio.file.Files.isSymbolicLink(entry.toPath())) {
                throw new IOException("Compiler output contains an unexpected symbolic link: " + entry.getAbsolutePath());
            }
            if (entry.isDirectory()) {
                collectClassFiles(root, entry, result, visited);
            } else if (entry.isFile() && entry.getName().endsWith(".class")) {
                if (!hasClassFileMagic(entry)) {
                    throw new IOException("Compiler output contains an invalid or truncated class file: " + entry.getAbsolutePath());
                }
                File canonicalFile = entry.getCanonicalFile();
                if (!canonicalFile.getPath().startsWith(rootPath)) {
                    throw new IOException("Compiler output file escapes the classes directory: " + entry.getAbsolutePath());
                }
                result.add(entry);
            }
        }
    }

    private static boolean hasClassFileMagic(File file) throws IOException {
        if (file.length() < 8L) return false;
        try (FileInputStream input = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = input.read(magic);
            return read == 4 && (magic[0] & 0xff) == 0xca && (magic[1] & 0xff) == 0xfe
                    && (magic[2] & 0xff) == 0xba && (magic[3] & 0xff) == 0xbe;
        }
    }

    private static int countClassEntries(File jarFile) throws CompilationFailedException {
        if (jarFile == null || !jarFile.isFile() || jarFile.length() == 0L) return 0;
        int count = 0;
        try (JarFile jar = new JarFile(jarFile)) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;
                try (java.io.InputStream input = jar.getInputStream(entry)) {
                    byte[] header = new byte[8];
                    int offset = 0;
                    while (offset < header.length) {
                        int read = input.read(header, offset, header.length - offset);
                        if (read < 0) break;
                        offset += read;
                    }
                    if (offset < header.length || (header[0] & 0xff) != 0xca || (header[1] & 0xff) != 0xfe
                            || (header[2] & 0xff) != 0xba || (header[3] & 0xff) != 0xbe) {
                        throw new CompilationFailedException("Invalid class-file entry in " + jarFile.getAbsolutePath() + ": " + entry.getName());
                    }
                } catch (IOException e) {
                    throw new CompilationFailedException("Could not read class-file entry " + entry.getName() + " from "
                            + jarFile.getAbsolutePath() + ": " + e.getMessage());
                }
                count++;
            }
        } catch (IOException e) {
            throw new CompilationFailedException("Invalid or unreadable classes JAR " + jarFile.getAbsolutePath() + ": " + e.getMessage());
        }
        return count;
    }

    private static void ensureDirectory(File directory, String description) throws CompilationFailedException {
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new CompilationFailedException("Could not create " + description + " directory: " + directory.getAbsolutePath());
        }
    }

    private static void clearDexOutputs(File outputDirectory) throws CompilationFailedException {
        ensureDirectory(outputDirectory, "DEX output");
        File[] entries = outputDirectory.listFiles();
        if (entries == null) throw new CompilationFailedException("Could not inspect DEX output directory: " + outputDirectory.getAbsolutePath());
        for (File entry : entries) {
            if (entry.isFile() && entry.getName().endsWith(".dex")) {
                if (!entry.delete() && entry.exists()) {
                    throw new CompilationFailedException("Could not clear previous DEX output: " + entry.getAbsolutePath());
                }
            }
        }
    }

    private static void validateApplicationDex(File outputDirectory) throws CompilationFailedException {
        File[] dexFiles = outputDirectory.listFiles((directory, name) -> name.matches("classes([0-9]+)?\\.dex"));
        if (dexFiles == null || dexFiles.length == 0) {
            throw new CompilationFailedException("D8 returned without producing classes*.dex under "
                    + outputDirectory.getAbsolutePath() + ". Check the validated application class inputs and D8 diagnostics.");
        }
        Arrays.sort(dexFiles, (left, right) -> Integer.compare(dexSequence(left.getName()), dexSequence(right.getName())));
        for (File dex : dexFiles) validateDexFile(dex);
    }

    private static void validateDexFile(File dex) throws CompilationFailedException {
        if (dex == null || !dex.isFile() || dex.length() < 112L) {
            throw new CompilationFailedException("D8 produced a missing, empty or truncated DEX file: "
                    + (dex == null ? "<null>" : dex.getAbsolutePath()));
        }
        try (RandomAccessFile input = new RandomAccessFile(dex, "r")) {
            byte[] header = new byte[40];
            input.readFully(header);
            if (header[0] != 'd' || header[1] != 'e' || header[2] != 'x' || header[3] != '\n'
                    || header[4] < '0' || header[4] > '9' || header[5] < '0' || header[5] > '9'
                    || header[6] < '0' || header[6] > '9' || header[7] != 0) {
                throw new CompilationFailedException("D8 output has an invalid DEX header: " + dex.getAbsolutePath());
            }
            int declaredFileSize = littleEndianInt(header, 32);
            int declaredHeaderSize = littleEndianInt(header, 36);
            if (declaredFileSize != dex.length() || declaredHeaderSize != 112) {
                throw new CompilationFailedException("D8 output has an inconsistent DEX header or file size: " + dex.getAbsolutePath());
            }
        } catch (IOException e) {
            throw new CompilationFailedException("Could not validate D8 output " + dex.getAbsolutePath() + ": " + e.getMessage());
        }
    }

    private static int littleEndianInt(byte[] data, int offset) {
        return (data[offset] & 0xff) | ((data[offset + 1] & 0xff) << 8)
                | ((data[offset + 2] & 0xff) << 16) | ((data[offset + 3] & 0xff) << 24);
    }

    private static int dexSequence(String name) {
        if ("classes.dex".equals(name)) return 1;
        try {
            return Integer.parseInt(name.substring("classes".length(), name.length() - ".dex".length()));
        } catch (Exception ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private static void replaceApplicationDexFiles(File stagingDirectory, File outputDirectory)
            throws CompilationFailedException {
        clearDexOutputs(outputDirectory);
        File[] stagedFiles = stagingDirectory.listFiles((directory, name) -> name.matches("classes([0-9]+)?\\.dex"));
        if (stagedFiles == null || stagedFiles.length == 0) {
            throw new CompilationFailedException("Validated D8 staging directory unexpectedly became empty.");
        }
        Arrays.sort(stagedFiles, (left, right) -> Integer.compare(dexSequence(left.getName()), dexSequence(right.getName())));
        for (File staged : stagedFiles) {
            File target = new File(outputDirectory, staged.getName());
            File temporaryTarget = new File(outputDirectory, staged.getName() + ".tmp");
            if (temporaryTarget.exists() && !temporaryTarget.delete()) {
                throw new CompilationFailedException("Could not clear temporary DEX output: " + temporaryTarget.getAbsolutePath());
            }
            copyFile(staged, temporaryTarget);
            validateDexFile(temporaryTarget);
            if (!temporaryTarget.renameTo(target)) {
                temporaryTarget.delete();
                throw new CompilationFailedException("Could not commit validated DEX output: " + target.getAbsolutePath());
            }
        }
    }

    private static void copyFile(File source, File target) throws CompilationFailedException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            output.flush();
            output.getFD().sync();
        } catch (IOException e) {
            target.delete();
            throw new CompilationFailedException("Could not copy validated DEX file to build output: " + e.getMessage());
        }
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null) return;
        if (java.nio.file.Files.isSymbolicLink(file.toPath())) {
            java.nio.file.Files.delete(file.toPath());
            return;
        }
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) {
                throw new IOException("Could not list temporary DEX path: " + file.getAbsolutePath());
            }
            for (File child : children) deleteRecursively(child);
        }
        if (!file.delete() && file.exists()) {
            throw new IOException("Could not remove temporary DEX path: " + file.getAbsolutePath());
        }
    }

    private static String safeMessage(Throwable failure) {
        if (failure == null) return "Unknown D8 failure";
        String message = failure.getMessage();
        return message == null || message.trim().isEmpty() ? failure.getClass().getSimpleName() : message;
    }
}
