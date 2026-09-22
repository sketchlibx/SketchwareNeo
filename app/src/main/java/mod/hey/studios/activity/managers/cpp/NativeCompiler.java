package mod.hey.studios.activity.managers.cpp;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pro.sketchware.utility.FileUtil;

public class NativeCompiler {

    private static final String TAG = "NativeCompiler";

    public static void compileOnDevice(Context context, String scId, String cppSourcePath, String nativeLibsOutputPath, String tempBuildPath) throws Exception {

        if (!InbuiltNdkManager.isNdkInstalled(context)) {
            throw new Exception("Inbuilt NDK and CMake are not installed. Please install them first from the C++ Manager settings.");
        }

        FileUtil.makeDir(tempBuildPath);
        FileUtil.makeDir(nativeLibsOutputPath + "/arm64-v8a");

        File cmakeBin = new File(context.getFilesDir(), "cmake/3.22.1/bin/cmake");
        File ninjaBin = new File(context.getFilesDir(), "cmake/3.22.1/bin/ninja");
        File installedNdkDir = InbuiltNdkManager.getInstalledNdkDir(context);
        if (installedNdkDir == null) {
            throw new Exception("No NDK installation found under files/ndk/. Run Setup NDK from the C/C++ Manager first.");
        }

        File prebuiltRoot = new File(installedNdkDir, "toolchains/llvm/prebuilt");
        File hostDir = findHostToolchainDir(prebuiltRoot);
        if (hostDir == null) {
            throw new Exception("Could not find a usable clang under " + prebuiltRoot
                    + ". Try a different NDK URL from the C/C++ Manager.");
        }

        File binDir = new File(hostDir, "bin");
        File clang = findCompilerBinary(binDir, "clang");
        File clangxx;
        try {
            clangxx = findCompilerBinary(binDir, "clang++");
        } catch (Exception clangxxNotFound) {
            Log.w(TAG, "No valid clang++ found, reusing clang for C++ as well: " + clangxxNotFound.getMessage());
            clangxx = clang;
        }
        File sysroot = new File(hostDir, "sysroot");
        if (!sysroot.isDirectory()) {
            throw new Exception("Found clang at " + clang + " but no sysroot at " + sysroot
                    + ". This NDK build appears incomplete — try Setup NDK again, or a different NDK URL.");
        }
        clang.setExecutable(true);
        clangxx.setExecutable(true);

        if (!cmakeBin.canExecute()) {
            cmakeBin.setExecutable(true);
            if (!cmakeBin.canExecute()) {
                throw new Exception("cmake binary at " + cmakeBin + " is not executable. Try Setup NDK again from the C++ Manager.");
            }
        }
        if (!ninjaBin.exists()) {
            throw new Exception("ninja binary not found at " + ninjaBin + ". Try Setup NDK again from the C++ Manager.");
        }
        ninjaBin.setExecutable(true);

        deleteRecursive(new File(tempBuildPath, "CMakeCache.txt"));
        deleteRecursive(new File(tempBuildPath, "CMakeFiles"));

        File generatedToolchain = new File(tempBuildPath, "sketchware_toolchain.cmake");
        writeToolchainFile(generatedToolchain, clang, clangxx, sysroot);

        StringBuilder logOutput = new StringBuilder();
        logOutput.append("clang:   ").append(clang.getAbsolutePath()).append("\n");
        logOutput.append("clang++: ").append(clangxx.getAbsolutePath()).append("\n");
        logOutput.append("sysroot: ").append(sysroot.getAbsolutePath()).append("\n");

        try {
            String[] cmakeArgs = {
                    cmakeBin.getAbsolutePath(),
                    cppSourcePath,
                    "-GNinja",
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DCMAKE_TOOLCHAIN_FILE=" + generatedToolchain.getAbsolutePath(),
                    "-DCMAKE_MAKE_PROGRAM=" + ninjaBin.getAbsolutePath()
            };
            ProcessBuilder cmakePb = new ProcessBuilder(cmakeArgs);
            cmakePb.directory(new File(tempBuildPath));
            cmakePb.redirectErrorStream(true);

            Map<String, String> cmakeEnv = cmakePb.environment();
            cmakeEnv.put("PATH", cmakeBin.getParent() + ":" + hostDir.getAbsolutePath() + "/bin:" + cmakeEnv.get("PATH"));

            logOutput.append("\n$ ").append(String.join(" ", cmakeArgs)).append("\n");

            Process cmakeProcess = cmakePb.start();
            logOutput.append("--- CMake Configuration ---\n");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(cmakeProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) logOutput.append(line).append("\n");
            }
            if (cmakeProcess.waitFor() != 0) {
                throw new Exception("CMake configuration failed:\n" + logOutput);
            }

            String[] ninjaArgs = {ninjaBin.getAbsolutePath(), "-j4", "-v"};
            ProcessBuilder ninjaPb = new ProcessBuilder(ninjaArgs);
            ninjaPb.directory(new File(tempBuildPath));
            ninjaPb.redirectErrorStream(true);

            Map<String, String> ninjaEnv = ninjaPb.environment();
            ninjaEnv.put("PATH", ninjaBin.getParent() + ":" + hostDir.getAbsolutePath() + "/bin:" + ninjaEnv.get("PATH"));

            logOutput.append("\n$ ").append(String.join(" ", ninjaArgs)).append("\n");

            Process ninjaProcess = ninjaPb.start();
            logOutput.append("--- Ninja Build ---\n");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(ninjaProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) logOutput.append(line).append("\n");
            }
            if (ninjaProcess.waitFor() != 0) {
                throw new Exception("Ninja build failed:\n" + logOutput);
            }

            copySoFiles(new File(tempBuildPath), new File(nativeLibsOutputPath, "arm64-v8a"));
            copyStlSharedLib(sysroot, new File(nativeLibsOutputPath, "arm64-v8a"), logOutput);

        } catch (Exception e) {
            Log.e(TAG, "Native compilation failed", e);
            throw new Exception("Native C/C++ Compilation Failed:\n" + e.getMessage() + "\n\nLogs:\n" + logOutput);
        }
    }

    private static void writeToolchainFile(File file, File clang, File clangxx, File sysroot) throws Exception {
        String content = "set(CMAKE_SYSTEM_NAME Linux)\n"
                + "set(CMAKE_SYSTEM_PROCESSOR aarch64)\n"
                + "set(ANDROID 1)\n"
                + "set(CMAKE_C_COMPILER \"" + clang.getAbsolutePath() + "\")\n"
                + "set(CMAKE_CXX_COMPILER \"" + clangxx.getAbsolutePath() + "\")\n"
                + "set(CMAKE_C_COMPILER_TARGET aarch64-linux-android21)\n"
                + "set(CMAKE_CXX_COMPILER_TARGET aarch64-linux-android21)\n"
                + "set(CMAKE_SYSROOT \"" + sysroot.getAbsolutePath() + "\")\n"
                + "set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)\n"
                + "set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)\n"
                + "set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)\n"
                + "set(CMAKE_POSITION_INDEPENDENT_CODE ON)\n";
        try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
            writer.write(content);
        }
    }

    private static void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    private static File findHostToolchainDir(File prebuiltRoot) {
        if (!prebuiltRoot.isDirectory()) return null;
        if (new File(prebuiltRoot, "bin/clang").exists()) return prebuiltRoot;

        File[] candidates = prebuiltRoot.listFiles(File::isDirectory);
        if (candidates == null) return null;
        for (File candidate : candidates) {
            if (new File(candidate, "bin/clang").exists()) return candidate;
        }
        return null;
    }

    private static File findCompilerBinary(File binDir, String baseName) throws Exception {
        File plain = new File(binDir, baseName);
        if (isValidElfExecutable(plain)) return plain;

        File[] siblings = binDir.listFiles();
        File best = null;
        int bestVersion = -1;
        if (siblings != null) {
            Pattern versionedPattern = Pattern.compile("^" + Pattern.quote(baseName) + "-(\\d+)$");
            for (File candidate : siblings) {
                Matcher m = versionedPattern.matcher(candidate.getName());
                if (m.matches() && isValidElfExecutable(candidate)) {
                    int version = Integer.parseInt(m.group(1));
                    if (version > bestVersion) {
                        bestVersion = version;
                        best = candidate;
                    }
                }
            }
        }
        if (best != null) return best;

        String plainDescription = plain.exists()
                ? plain.length() + " bytes, not a valid ELF binary"
                : "does not exist";
        throw new Exception("No working '" + baseName + "' executable found in " + binDir + ". "
                + "'" + baseName + "': " + plainDescription + ". "
                + "This is usually a symlink from the NDK archive that extraction failed to recreate — "
                + "try Setup NDK again, or a different NDK URL.");
    }

    private static boolean isValidElfExecutable(File file) {
        if (!file.isFile() || file.length() < 4) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] magic = new byte[4];
            int read = fis.read(magic);
            return read == 4 && magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private static void copyStlSharedLib(File sysroot, File targetDir, StringBuilder logOutput) throws Exception {
        File libcxxShared = findFileRecursive(sysroot, "libc++_shared.so");
        if (libcxxShared == null) {
            logOutput.append("libc++_shared.so not found under sysroot — skipping (fine if this build is pure C / doesn't use C++ STL features).\n");
            return;
        }
        FileUtil.copyFile(libcxxShared.getAbsolutePath(), new File(targetDir, "libc++_shared.so").getAbsolutePath());
        logOutput.append("Packaged libc++_shared.so from ").append(libcxxShared.getAbsolutePath()).append("\n");
    }

    private static File findFileRecursive(File dir, String name) {
        if (!dir.isDirectory()) return null;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findFileRecursive(child, name);
                if (found != null) return found;
            } else if (child.getName().equals(name)) {
                return child;
            }
        }
        return null;
    }

    private static void copySoFiles(File sourceDir, File targetDir) throws Exception {
        if (!sourceDir.exists()) return;
        File[] files = sourceDir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                copySoFiles(file, targetDir);
            } else if (file.getName().endsWith(".so")) {
                FileUtil.copyFile(file.getAbsolutePath(), new File(targetDir, file.getName()).getAbsolutePath());
            }
        }
    }
}
