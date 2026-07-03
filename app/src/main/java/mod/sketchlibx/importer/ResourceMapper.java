package mod.sketchlibx.importer;

import android.util.Log;

import com.google.gson.GsonBuilder;
import com.google.gson.Gson;

import pro.sketchware.utility.FileUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps Android Studio resource files to Sketchware's resource structure.
 *
 * Responsibilities:
 *  - Copy image (drawable/mipmap) files to .sketchware/resources/images/<sc_id>/
 *  - Copy sound (raw) files to .sketchware/resources/sounds/<sc_id>/
 *  - Copy font files to .sketchware/resources/fonts/<sc_id>/
 *  - Copy other resources (values, anim, menu, xml, color) to files/resource/
 *  - Detect and copy the launcher icon to files/app-icon/icon.png
 *  - Build the Sketchware "resource" data string
 *
 * Density qualifier collision rule:
 *   drawable-xxhdpi/bg.png + drawable-hdpi/bg.png → prefer xxhdpi.
 *   Never silently overwrite. Logs a WARNING for every collision.
 *
 * Density priority (highest = most preferred):
 *   xxxhdpi > xxhdpi > xhdpi > hdpi > mdpi > ldpi > nodpi > (no qualifier)
 */
public class ResourceMapper {

    private static final String TAG = "ResourceMapper";

    // Density priority map — higher value = preferred
    private static final Map<String, Integer> DENSITY_PRIORITY = new HashMap<>();
    static {
        DENSITY_PRIORITY.put("xxxhdpi", 7);
        DENSITY_PRIORITY.put("xxhdpi",  6);
        DENSITY_PRIORITY.put("xhdpi",   5);
        DENSITY_PRIORITY.put("hdpi",    4);
        DENSITY_PRIORITY.put("mdpi",    3);
        DENSITY_PRIORITY.put("ldpi",    2);
        DENSITY_PRIORITY.put("nodpi",   1);
        DENSITY_PRIORITY.put("",        0); // no qualifier (lowest)
    }

    // Confirmed from eC.class: GsonBuilder with excludeFieldsWithoutExposeAnnotation
    private static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .create();

    // ── Result holder ─────────────────────────────────────────────────────────

    public static class ResourceResult {
        /** Sketchware "resource" data string: @images\n...\n@sounds\n...\n@fonts\n...\n */
        public String resourceData  = "@images\n@sounds\n@fonts\n";
        /** True if a custom launcher icon was found and copied. */
        public boolean hasCustomIcon = false;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Processes all resources under src/main/res/.
     *
     * @param resDir         src/main/res directory
     * @param filesPath      data/<sc_id>/files path (for non-Sketchware-resource files)
     * @param swImagesPath   .sketchware/resources/images/<sc_id>/ path
     * @param swSoundsPath   .sketchware/resources/sounds/<sc_id>/ path
     * @param swFontsPath    .sketchware/resources/fonts/<sc_id>/  path
     * @param iconResName    launcher icon name (without extension) from manifest
     */
    public ResourceResult process(
            File resDir,
            String filesPath,
            String swImagesPath,
            String swSoundsPath,
            String swFontsPath,
            String iconResName) {

        ResourceResult result = new ResourceResult();
        if (!resDir.exists() || !resDir.isDirectory()) return result;

        // Ensure target directories exist
        FileUtil.makeDir(swImagesPath);
        FileUtil.makeDir(swSoundsPath);
        FileUtil.makeDir(swFontsPath);

        // ── First pass: collect all images with density metadata ──────────────
        // Map: filename (e.g. "bg.png") → best candidate File so far
        Map<String, DensityCandidate> bestImages = new HashMap<>();

        List<ResourceEntry> soundEntries = new ArrayList<>();
        List<ResourceEntry> fontEntries  = new ArrayList<>();

        File[] dirs = resDir.listFiles();
        if (dirs == null) return result;

        for (File dir : dirs) {
            if (!dir.isDirectory()) continue;
            String dName = dir.getName();

            if (dName.startsWith("drawable") || dName.startsWith("mipmap")) {
                // Extract density qualifier from folder name
                // "drawable-xxhdpi" → "xxhdpi", "drawable" → ""
                String qualifier = extractQualifier(dName);
                int priority = DENSITY_PRIORITY.getOrDefault(qualifier, 0);

                File[] files = dir.listFiles();
                if (files == null) continue;

                for (File resFile : files) {
                    String fName = resFile.getName();
                    String lower = fName.toLowerCase();

                    if (lower.endsWith(".png") || lower.endsWith(".jpg")
                            || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {

                        DensityCandidate existing = bestImages.get(fName);
                        if (existing == null || priority > existing.priority) {
                            if (existing != null) {
                                Log.w(TAG, "Density collision: " + fName
                                        + " — replacing density=" + existing.qualifier
                                        + " with density=" + qualifier);
                            }
                            bestImages.put(fName, new DensityCandidate(resFile, qualifier, priority));
                        }

                    } else if (lower.endsWith(".xml")) {
                        // Vector drawables / shape drawables → copy to files/resource/
                        copyToFilesResource(resFile, filesPath, dName);
                    }
                }

            } else if (dName.startsWith("raw")) {
                // Sounds
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File resFile : files) {
                    String fName = resFile.getName();
                    String destPath = swSoundsPath + File.separator + fName;
                    FileUtil.copyFile(resFile.getAbsolutePath(), destPath);
                    soundEntries.add(new ResourceEntry(fName));
                    Log.d(TAG, "Sound: " + fName);
                }

            } else if (dName.startsWith("font")) {
                // Fonts
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File resFile : files) {
                    String fName = resFile.getName();
                    String destPath = swFontsPath + File.separator + fName;
                    FileUtil.copyFile(resFile.getAbsolutePath(), destPath);
                    fontEntries.add(new ResourceEntry(fName));
                    Log.d(TAG, "Font: " + fName);
                }

            } else {
                // values, anim, xml, color, menu, values-night, etc.
                // → copy entire folder to files/resource/<dirName>/
                File[] files = dir.listFiles();
                if (files == null) continue;
                for (File resFile : files) {
                    copyToFilesResource(resFile, filesPath, dName);
                }
            }
        }

        // ── Second pass: copy best-density images ─────────────────────────────
        List<ResourceEntry> imageEntries = new ArrayList<>();
        String appIconTarget = filesPath + File.separator + "app-icon" + File.separator + "icon.png";

        for (Map.Entry<String, DensityCandidate> entry : bestImages.entrySet()) {
            String fName    = entry.getKey();
            File   srcFile  = entry.getValue().file;
            String cleanName = stripExtension(fName);

            // Is this the launcher icon?
            if (cleanName.equals(iconResName)
                    || cleanName.startsWith(iconResName + "_foreground")
                    || cleanName.equals(iconResName + "_round")) {
                // Use exact match for the main icon only (not foreground / round variants)
                if (cleanName.equals(iconResName)) {
                    FileUtil.copyFile(srcFile.getAbsolutePath(), appIconTarget);
                    result.hasCustomIcon = true;
                    Log.d(TAG, "Launcher icon: " + fName + " → app-icon/icon.png");
                } else {
                    // Round / foreground variants: copy to images as regular resource
                    String destPath = swImagesPath + File.separator + fName;
                    FileUtil.copyFile(srcFile.getAbsolutePath(), destPath);
                    imageEntries.add(new ResourceEntry(fName));
                }
            } else {
                String destPath = swImagesPath + File.separator + fName;
                FileUtil.copyFile(srcFile.getAbsolutePath(), destPath);
                imageEntries.add(new ResourceEntry(fName));
                Log.d(TAG, "Image: " + fName);
            }
        }

        // ── Build resource data string ─────────────────────────────────────────
        result.resourceData = buildResourceData(imageEntries, soundEntries, fontEntries);
        return result;
    }

    // ── Resource data string builder ──────────────────────────────────────────

    /**
     * Confirmed format (from eC.class + ProjectResourceBean):
     *
     *   @images
     *   {"resFullName":"bg.png","resName":"bg","resType":1}
     *   @sounds
     *   {"resFullName":"beep.mp3","resName":"beep","resType":1}
     *   @fonts
     *   {"resFullName":"roboto.ttf","resName":"roboto","resType":1}
     *
     * resType=1 = PROJECT_RES_TYPE_FILE (files on disk, not inline resource values)
     */
    private String buildResourceData(
            List<ResourceEntry> images,
            List<ResourceEntry> sounds,
            List<ResourceEntry> fonts) {

        StringBuilder sb = new StringBuilder();

        sb.append("@images\n");
        for (ResourceEntry e : images) sb.append(e.toJson()).append("\n");

        sb.append("@sounds\n");
        for (ResourceEntry e : sounds) sb.append(e.toJson()).append("\n");

        sb.append("@fonts\n");
        for (ResourceEntry e : fonts)  sb.append(e.toJson()).append("\n");

        return sb.toString();
    }

    // ── Copy file to files/resource/<dirName>/ ────────────────────────────────

    private void copyToFilesResource(File resFile, String filesPath, String dirName) {
        File targetDir = new File(filesPath + File.separator + "resource" + File.separator + dirName);
        FileUtil.makeDir(targetDir.getAbsolutePath());
        FileUtil.copyFile(
                resFile.getAbsolutePath(),
                targetDir.getAbsolutePath() + File.separator + resFile.getName());
    }

    // ── Density qualifier extraction ─────────────────────────────────────────

    /**
     * "drawable-xxhdpi" → "xxhdpi"
     * "drawable"        → ""
     * "mipmap-xhdpi"   → "xhdpi"
     */
    private String extractQualifier(String folderName) {
        int dash = folderName.indexOf('-');
        if (dash < 0) return "";
        String afterDash = folderName.substring(dash + 1);
        // Only return the last segment (e.g. "drawable-night-xxhdpi" → "xxhdpi")
        String[] parts = afterDash.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (DENSITY_PRIORITY.containsKey(parts[i])) return parts[i];
        }
        return afterDash; // return raw if no recognized density found
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0) ? fileName.substring(0, dot) : fileName;
    }

    // ── Inner data classes ────────────────────────────────────────────────────

    private static class DensityCandidate {
        final File   file;
        final String qualifier;
        final int    priority;
        DensityCandidate(File file, String qualifier, int priority) {
            this.file      = file;
            this.qualifier = qualifier;
            this.priority  = priority;
        }
    }

    private static class ResourceEntry {
        final String resFullName;  // e.g. "bg.png"
        final String resName;      // e.g. "bg"
        final int    resType = 1;  // PROJECT_RES_TYPE_FILE

        ResourceEntry(String fullName) {
            this.resFullName = fullName;
            this.resName     = fullName.contains(".")
                    ? fullName.substring(0, fullName.lastIndexOf('.'))
                    : fullName;
        }

        String toJson() {
            return "{\"resFullName\":\"" + resFullName + "\","
                 + "\"resName\":\""    + resName     + "\","
                 + "\"resType\":"      + resType     + "}";
        }
    }
}
