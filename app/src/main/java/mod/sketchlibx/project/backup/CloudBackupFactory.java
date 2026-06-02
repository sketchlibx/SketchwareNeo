package mod.sketchlibx.project.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.custom_blocks.CustomBlocksManager;
import mod.hilal.saif.activities.tools.ConfigActivity;
import neo.sketchware.utility.FileUtil;

/**
 * CloudBackupFactory
 *
 * Generates a .swb (zip) archive for a single Sketchware project and writes it
 * into a temporary directory under {@link #CLOUD_TEMP_DIR}.
 *
 * Key fixes applied (v2):
 *  - Every major step (folder creation, file copy, zip) is individually logged.
 *  - Zip output is validated (exists + non-zero size) before setting outPath.
 *  - Folder creation is logged with mkdirs() result for easy diagnosis.
 *  - Local-library copy errors are logged with full stack traces, not swallowed.
 *  - Custom-block serialisation errors similarly surfaced in logcat.
 */
public class CloudBackupFactory {

    private static final String TAG = "CloudBackupFactory";

    public static final String EXTENSION = "swb";

    /** Temporary working directory inside external storage. */
    public static final String CLOUD_TEMP_DIR = ".sketchware/.cloudbackup/";

    private final String sc_id;
    private File outPath;

    public CloudBackupFactory(String sc_id) {
        this.sc_id = sc_id;
    }

    /** Absolute path of the shared cloud-backup temp directory. */
    public static String getCloudBackupDir() {
        return new File(
                Environment.getExternalStorageDirectory(),
                CLOUD_TEMP_DIR
        ).getAbsolutePath();
    }

    // ────────────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ────────────────────────────────────────────────────────────────────────────────

    /**
     * Assembles the project directory tree and zips it to a .swb file.
     * After this call, retrieve the output with {@link #getOutFile()}.
     * If anything fails, {@code getOutFile()} returns {@code null}.
     *
     * @param context used for custom-block lookup; may be {@code null} to skip
     * @param project_name display name used in the output filename
     */
    public void backup(Context context, String project_name) {

        Log.i(
                TAG,
                "backup() START | sc_id=" + sc_id
                        + " | project=" + project_name
        );

        // ── Resolve filename template ────────────────────────────────────────────

        String customFileName = ConfigActivity.getBackupFileName();

        String versionName = yB.c(
                lC.b(sc_id),
                "sc_ver_name"
        );

        String versionCode = yB.c(
                lC.b(sc_id),
                "sc_ver_code"
        );

        String pkgName = yB.c(
                lC.b(sc_id),
                "my_sc_pkg_name"
        );

        if (project_name == null) {
            project_name = "Unknown_Project";
        }

        String projectNameOnly = project_name
                .replace("_d", "")
                .replace(File.separator, "")
                .replaceAll("[^a-zA-Z0-9.\\- ]", "_");

        Log.d(
                TAG,
                "projectNameOnly=" + projectNameOnly
                        + " | ver=" + versionName
                        + " | code=" + versionCode
                        + " | pkg=" + pkgName
        );

        String finalFileName;

        try {

            finalFileName = customFileName
                    .replace("$projectName", projectNameOnly)
                    .replace("$versionCode", versionCode)
                    .replace("$versionName", versionName)
                    .replace("$pkgName", pkgName)
                    .replace(
                            "$timeInMs",
                            String.valueOf(
                                    Calendar.getInstance(
                                            Locale.ENGLISH
                                    ).getTimeInMillis()
                            )
                    );

            Matcher matcher = Pattern.compile(
                    "\\$time\\((.*?)\\)"
            ).matcher(customFileName);

            while (matcher.find()) {

                finalFileName = finalFileName.replaceFirst(
                        Pattern.quote(
                                Objects.requireNonNull(
                                        matcher.group(0)
                                )
                        ),
                        new SimpleDateFormat(
                                matcher.group(1),
                                Locale.ENGLISH
                        ).format(
                                Calendar.getInstance().getTime()
                        )
                );
            }

        } catch (Exception e) {

            Log.w(
                    TAG,
                    "Custom filename template failed; using default. Error: "
                            + e.getMessage()
            );

            finalFileName =
                    projectNameOnly
                            + " v"
                            + versionName
                            + " ("
                            + pkgName
                            + ", "
                            + versionCode
                            + ") "
                            + new SimpleDateFormat(
                                    "yyyy-M-dd'T'HHmmss",
                                    Locale.ENGLISH
                            ).format(
                                    Calendar.getInstance().getTime()
                            );
        }

        Log.d(TAG, "finalFileName=" + finalFileName);

        // ── Set up directories ───────────────────────────────────────────────────

        String backupDir = getCloudBackupDir();

        // FileUtil.makeDir() returns void
        FileUtil.makeDir(backupDir);

        boolean dirMade = new File(backupDir).exists();

        Log.d(
                TAG,
                "Cloud backup dir created/exists: "
                        + dirMade
                        + " | path="
                        + backupDir
        );

        File outFolder = new File(
                backupDir,
                projectNameOnly + "_temp_" + sc_id
        );

        File outZip = new File(
                backupDir,
                finalFileName + "." + EXTENSION
        );

        // Delete old temp folder if exists
        if (outFolder.exists()) {

            FileUtil.deleteFile(
                    outFolder.getAbsolutePath()
            );

            Log.d(
                    TAG,
                    "Removed stale temp folder: "
                            + outFolder.getAbsolutePath()
            );
        }

        // Create temp folder
        FileUtil.makeDir(
                outFolder.getAbsolutePath()
        );

        boolean folderCreated = outFolder.exists();

        Log.d(
                TAG,
                "Temp folder created="
                        + folderCreated
                        + " | path="
                        + outFolder.getAbsolutePath()
        );

        try {

            // ── data/ ────────────────────────────────────────────────────────────

            File dataF = new File(outFolder, "data");

            File srcData = new File(
                    Environment.getExternalStorageDirectory(),
                    ".sketchware/data/" + sc_id
            );

            FileUtil.makeDir(
                    dataF.getAbsolutePath()
            );

            if (srcData.exists()) {

                BackupFactory.copySafe(
                        srcData,
                        dataF
                );

                Log.d(
                        TAG,
                        "data/ copied from "
                                + srcData.getAbsolutePath()
                );

            } else {

                Log.w(
                        TAG,
                        "data/ source missing: "
                                + srcData.getAbsolutePath()
                );
            }

            // ── resources/ ──────────────────────────────────────────────────────

            File resF = new File(outFolder, "resources");

            FileUtil.makeDir(
                    resF.getAbsolutePath()
            );

            String[] resSubfolders = {
                    "fonts",
                    "icons",
                    "images",
                    "sounds"
            };

            for (String subfolder : resSubfolders) {

                File resSubf = new File(
                        resF,
                        subfolder
                );

                FileUtil.makeDir(
                        resSubf.getAbsolutePath()
                );

                File srcRes = new File(
                        Environment.getExternalStorageDirectory(),
                        ".sketchware/resources/"
                                + subfolder
                                + "/"
                                + sc_id
                );

                if (srcRes.exists()) {

                    BackupFactory.copySafe(
                            srcRes,
                            resSubf
                    );

                    Log.d(
                            TAG,
                            "resources/" + subfolder + " copied."
                    );

                } else {

                    Log.d(
                            TAG,
                            "resources/"
                                    + subfolder
                                    + " not present — skipping."
                    );
                }

                if (!subfolder.equals("icons")) {

                    BackupFactory.createNomediaFileIn(
                            resSubf
                    );
                }
            }

            // ── project/ ────────────────────────────────────────────────────────

            File projectF = new File(
                    outFolder,
                    "project"
            );

            File srcProj = new File(
                    Environment.getExternalStorageDirectory(),
                    ".sketchware/mysc/list/"
                            + sc_id
                            + "/project"
            );

            if (srcProj.exists()) {

                BackupFactory.copy(
                        srcProj,
                        projectF
                );

                Log.d(
                        TAG,
                        "project/ copied from "
                                + srcProj.getAbsolutePath()
                );

            } else {

                Log.w(
                        TAG,
                        "project/ source missing: "
                                + srcProj.getAbsolutePath()
                );
            }

            // ── local_libs/ ─────────────────────────────────────────────────────

            File localLibs = new File(
                    Environment.getExternalStorageDirectory(),
                    ".sketchware/data/"
                            + sc_id
                            + "/local_library"
            );

            if (localLibs.exists() && localLibs.length() > 0) {

                try {

                    String libsContent = FileUtil.readFile(
                            localLibs.getAbsolutePath()
                    );

                    if (!libsContent.trim().isEmpty()) {

                        JSONArray ja = new JSONArray(
                                libsContent
                        );

                        Log.d(
                                TAG,
                                "local_library: "
                                        + ja.length()
                                        + " lib(s) found."
                        );

                        File libsF = new File(
                                outFolder,
                                "local_libs"
                        );

                        libsF.mkdirs();

                        for (int i = 0; i < ja.length(); i++) {

                            JSONObject jo = ja.getJSONObject(i);

                            File f = new File(
                                    jo.getString("dexPath")
                            ).getParentFile();

                            if (f != null && f.exists()) {

                                BackupFactory.copy(
                                        f,
                                        new File(
                                                libsF,
                                                f.getName()
                                        )
                                );

                                Log.d(
                                        TAG,
                                        "  Copied local lib: "
                                                + f.getName()
                                );

                            } else {

                                Log.w(
                                        TAG,
                                        "  local lib dexPath parent missing: "
                                                + jo.getString("dexPath")
                                );
                            }
                        }
                    }

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "Failed to copy local libraries — continuing",
                            e
                    );
                }

            } else {

                Log.d(
                        TAG,
                        "No local_library file or it is empty — skipping."
                );
            }

            // ── custom_blocks ────────────────────────────────────────────────────

            if (context != null) {

                try {

                    CustomBlocksManager cbm =
                            new CustomBlocksManager(
                                    context,
                                    sc_id
                            );

                    Set<ExtraBlockInfo> blocks =
                            new HashSet<>();

                    Set<String> blockNames =
                            new HashSet<>();

                    for (BlockBean bean : cbm.getUsedBlocks()) {

                        if (!blockNames.contains(bean.opCode)) {

                            blockNames.add(bean.opCode);

                            blocks.add(
                                    cbm.contains(bean.opCode)
                                            ? cbm.getExtraBlockInfo(bean.opCode)
                                            : BlockLoader.getBlockInfo(bean.opCode)
                            );
                        }
                    }

                    if (!blocks.isEmpty()) {

                        String cbJson = new Gson().toJson(
                                blocks
                        );

                        FileUtil.writeFile(
                                new File(
                                        dataF,
                                        "custom_blocks"
                                ).getAbsolutePath(),
                                cbJson
                        );

                        Log.d(
                                TAG,
                                "custom_blocks written: "
                                        + blocks.size()
                                        + " block(s)."
                        );

                    } else {

                        Log.d(
                                TAG,
                                "No custom blocks found for this project."
                        );
                    }

                } catch (Exception e) {

                    Log.e(
                            TAG,
                            "Failed to serialise custom blocks — continuing",
                            e
                    );
                }
            }

            // ── Zip ──────────────────────────────────────────────────────────────

            Log.d(
                    TAG,
                    "Zipping "
                            + outFolder.getAbsolutePath()
                            + " → "
                            + outZip.getAbsolutePath()
            );

            BackupFactory.zipFolder(
                    outFolder,
                    outZip
            );

            if (outZip.exists() && outZip.length() > 0) {

                outPath = outZip;

                Log.i(
                        TAG,
                        "backup() SUCCESS | zip="
                                + outZip.getName()
                                + " | size="
                                + outZip.length()
                                + " bytes"
                );

            } else {

                Log.e(
                        TAG,
                        "backup() FAILED — zip missing or empty."
                                + " exists="
                                + outZip.exists()
                                + " size="
                                + (outZip.exists()
                                ? outZip.length()
                                : "n/a")
                                + " path="
                                + outZip.getAbsolutePath()
                );

                outPath = null;
            }

        } catch (Exception e) {

            Log.e(
                    TAG,
                    "backup() threw an unexpected exception:\n"
                            + Log.getStackTraceString(e)
            );

            outPath = null;

        } finally {

            // Always clean up the unzipped staging folder.

            FileUtil.deleteFile(
                    outFolder.getAbsolutePath()
            );

            Log.d(
                    TAG,
                    "Temp folder deleted: "
                            + outFolder.getAbsolutePath()
            );
        }
    }

    /** Returns the generated .swb File, or null if backup failed. */
    public File getOutFile() {
        return outPath;
    }
}