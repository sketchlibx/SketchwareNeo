package dev.aldi.sayuti.editor.manage;

import static pro.sketchware.utility.FileUtil.deleteFile;
import static pro.sketchware.utility.FileUtil.getExternalStorageDir;
import static pro.sketchware.utility.FileUtil.isExistFile;
import static pro.sketchware.utility.FileUtil.listDirAsFile;
import static pro.sketchware.utility.FileUtil.readFile;
import static pro.sketchware.utility.FileUtil.writeFile;

import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import mod.hey.studios.util.Helper;

public class LocalLibrariesUtil {
    private static final String localLibsPath = getExternalStorageDir().concat("/.sketchware/libs/local_libs/");

    public static final String ARTIFACT_METADATA_FILE_NAME = "artifact.txt";

    private static List<LocalLibrary> cachedLibraries = null;

    public static void clearCache() {
        cachedLibraries = null;
    }

    public static List<LocalLibrary> getAllLocalLibraries() {
        if (cachedLibraries != null) {
            return new ArrayList<>(cachedLibraries);
        }

        ArrayList<File> localLibraryFiles = new ArrayList<>();
        listDirAsFile(localLibsPath, localLibraryFiles);
        localLibraryFiles.sort(new LocalLibrariesComparator());

        List<LocalLibrary> localLibraries = localLibraryFiles.parallelStream()
                .filter(File::isDirectory)
                .map(file -> {
                    LocalLibrary library = LocalLibrary.fromFile(file);
                    String artifactMetaPath = new File(file, ARTIFACT_METADATA_FILE_NAME).getAbsolutePath();
                    if (isExistFile(artifactMetaPath)) {
                        String artifact = readFile(artifactMetaPath).trim();
                        if (!artifact.isEmpty()) {
                            library.setMavenDependency(artifact);
                        }
                    }
                    return library;
                })
                .collect(Collectors.toList());

        cachedLibraries = new ArrayList<>(localLibraries);

        return localLibraries;
    }

    public static void writeArtifactMetadata(String libraryFolderName, String mavenDependency) {
        if (libraryFolderName == null || mavenDependency == null || mavenDependency.isEmpty()) {
            return;
        }
        String path = localLibsPath + libraryFolderName + File.separator + ARTIFACT_METADATA_FILE_NAME;
        writeFile(path, mavenDependency);
    }

    public static LocalLibrary findInstalledLibraryByGroupArtifact(String group, String artifact) {
        if (group == null || artifact == null) {
            return null;
        }
        String prefix = group + ":" + artifact + ":";
        for (LocalLibrary library : getAllLocalLibraries()) {
            String dependency = library.getMavenDependency();
            if (dependency != null && dependency.startsWith(prefix)) {
                return library;
            }
        }
        return null;
    }

    public static ArrayList<HashMap<String, Object>> getLocalLibraries(String scId) {
        File localLibFile = getLocalLibFile(scId);
        String fileContent;
        if (!localLibFile.exists() || (fileContent = readFile(localLibFile.getAbsolutePath())).isEmpty()) {
            writeFile(localLibFile.getAbsolutePath(), "[]");
            return new ArrayList<>();
        }
        return new Gson().fromJson(fileContent, Helper.TYPE_MAP_LIST);
    }

    public static void deleteSelectedLocalLibraries(String scId, List<LocalLibrary> localLibraries, ArrayList<HashMap<String, Object>> projectUsedLibs) {
        clearCache();
        
        localLibraries.removeIf(library -> {
            if (library.isSelected()) {
                deleteFile(localLibsPath.concat(library.getName()));
                if (projectUsedLibs != null) {
                    int indexToRemove = -1;
                    for (int i = 0; i < projectUsedLibs.size(); i++) {
                        Map<String, Object> libraryMap = projectUsedLibs.get(i);
                        if (library.getName().equals(libraryMap.get("name").toString())) {
                            indexToRemove = i;
                            break;
                        }
                    }
                    if (indexToRemove != -1) {
                        projectUsedLibs.remove(indexToRemove);
                    }
                }
                return true;
            }
            return false;
        });
        if (projectUsedLibs != null)
            rewriteLocalLibFile(scId, new Gson().toJson(projectUsedLibs));
    }

    public static File getLocalLibFile(String scId) {
        return new File(getExternalStorageDir().concat("/.sketchware/data/").concat(scId.concat("/local_library")));
    }

    public static void rewriteLocalLibFile(String scId, String newContent) {
        writeFile(getLocalLibFile(scId).getAbsolutePath(), newContent);
    }

    public static HashMap<String, Object> createLibraryMap(String name, String dependency) {
        String configPath = localLibsPath + name + "/config";
        String resPath = localLibsPath + name + "/res";
        String jarPath = localLibsPath + name + "/classes.jar";
        String dexPath = localLibsPath + name + "/classes.dex";
        String manifestPath = localLibsPath + name + "/AndroidManifest.xml";
        String pgRulesPath = localLibsPath + name + "/proguard.txt";
        String assetsPath = localLibsPath + name + "/assets";

        HashMap<String, Object> localLibrary = new HashMap<>();
        localLibrary.put("name", name);
        if (dependency != null) {
            localLibrary.put("dependency", dependency);
        }
        if (isExistFile(configPath)) {
            localLibrary.put("packageName", readFile(configPath));
        }
        if (isExistFile(resPath)) {
            localLibrary.put("resPath", resPath);
        }
        if (isExistFile(jarPath)) {
            localLibrary.put("jarPath", jarPath);
        }
        if (isExistFile(dexPath)) {
            localLibrary.put("dexPath", dexPath);
        }
        if (isExistFile(manifestPath)) {
            localLibrary.put("manifestPath", manifestPath);
        }
        if (isExistFile(pgRulesPath)) {
            localLibrary.put("pgRulesPath", pgRulesPath);
        }
        if (isExistFile(assetsPath)) {
            localLibrary.put("assetsPath", assetsPath);
        }
        return localLibrary;
    }
}
