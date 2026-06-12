package mod.hey.studios.project.backup;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.besome.sketch.beans.BlockBean;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import a.a.a.lC;
import a.a.a.yB;
import mod.hey.studios.editor.manage.block.ExtraBlockInfo;
import mod.hey.studios.editor.manage.block.v2.BlockLoader;
import mod.hey.studios.project.custom_blocks.CustomBlocksManager;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class BackupFactory {
    public static final String EXTENSION = "swb";
    public static final String DEF_PATH = ".sketchware/backups/";

    private static final String[] resSubfolders = {
            "fonts", "icons", "images", "sounds"
    };

    final String sc_id;
    File outPath;
    boolean backupLocalLibs;
    boolean backupCustomBlocks;
    String error = "";
    boolean restoreSuccess = true;
    
    private String customBackupDir = null;

    public BackupFactory(String sc_id) {
        this.sc_id = sc_id;
    }

    public void setCustomBackupDir(String dir) {
        this.customBackupDir = dir;
    }

    public static String getBackupDir() {
        return new File(Environment.getExternalStorageDirectory(), ConfigActivity.getBackupPath())
                .getAbsolutePath();
    }

    private static File getAllLocalLibsDir() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/libs/local_libs");
    }

    private static HashMap<String, Object> getProject(File file) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes();
            cipher.init(2, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] encrypted;
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                encrypted = new byte[(int) raf.length()];
                raf.readFully(encrypted);
            }
            byte[] decrypted = cipher.doFinal(encrypted);
            String decryptedString = new String(decrypted);

            return new Gson().fromJson(decryptedString.trim(), Helper.TYPE_MAP);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean writeEncrypted(File file, String string) {
        String path = file.getAbsolutePath();

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] key = "sketchwaresecure".getBytes();
            cipher.init(1, new SecretKeySpec(key, "AES"), new IvParameterSpec(key));
            byte[] encrypted = cipher.doFinal((string.trim()).getBytes());
            try (RandomAccessFile raf = new RandomAccessFile(path, "rw")) {
                raf.setLength(0);
                raf.write(encrypted);
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String getNewScId() {
        File myscList = new File(Environment.getExternalStorageDirectory(),
                ".sketchware/mysc/list/");

        ArrayList<String> list = new ArrayList<>();
        FileUtil.listDir(myscList.getAbsolutePath(), list);
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);

        int id = list.isEmpty() ? 600 : Integer.parseInt(new File(list.get(list.size() - 1)).getName());
        return String.valueOf(id + 1);
    }

    public static boolean unzip(File zipFile, File destinationDir) {
        int DEFAULT_BUFFER = 2048;
        try (ZipFile zip = new ZipFile(zipFile)) {
            destinationDir.mkdirs();
            Enumeration<? extends ZipEntry> zipFileEntries = zip.entries();
            while (zipFileEntries.hasMoreElements()) {
                ZipEntry entry = zipFileEntries.nextElement();
                String entryName = entry.getName();
                File destFile = new File(destinationDir, entryName);
                File destinationParent = destFile.getParentFile();
                if (destinationParent != null && !destinationParent.exists()) {
                    destinationParent.mkdirs();
                }
                if (!entry.isDirectory()) {
                    try (BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry))) {
                        int currentByte;
                        byte[] data = new byte[DEFAULT_BUFFER];
                        try (FileOutputStream fos = new FileOutputStream(destFile)) {
                            try (BufferedOutputStream dest = new BufferedOutputStream(fos, DEFAULT_BUFFER)) {
                                while ((currentByte = is.read(data, 0, DEFAULT_BUFFER)) != -1) {
                                    dest.write(data, 0, currentByte);
                                }
                                dest.flush();
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void zipFolder(File srcFolder, File destZipFile) throws Exception {
        try (FileOutputStream fileWriter = new FileOutputStream(destZipFile)) {
            try (ZipOutputStream zip = new ZipOutputStream(fileWriter)) {
                addFolderToZip(srcFolder, srcFolder, zip);
                zip.flush();
            }
        }
    }

    private static void addFileToZip(File rootPath, File srcFile, ZipOutputStream zip) throws Exception {

        if (srcFile.isDirectory()) {
            addFolderToZip(rootPath, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            try (FileInputStream in = new FileInputStream(srcFile)) {
                String name = srcFile.getPath();
                name = name.replace(rootPath.getPath() + "/", "");
                zip.putNextEntry(new ZipEntry(name));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private static void addFolderToZip(File rootPath, File srcFolder, ZipOutputStream zip) throws Exception {
        File[] srcFolderFiles = srcFolder.listFiles();
        if (srcFolderFiles != null) {
            for (File fileName : srcFolderFiles) {
                addFileToZip(rootPath, fileName, zip);
            }
        }
    }

    public static void createNomediaFileIn(File dir) {
        FileUtil.writeFile(new File(dir, ".nomedia").getAbsolutePath(), "");
    }

    public static void copySafe(File source, File destination) {
        if (!source.exists()) {
            destination.mkdirs();
            createNomediaFileIn(destination);
        } else {
            copy(source, destination);
        }
    }

    public static void copy(File source, File destination) {
        if (source.isDirectory()) {
            if (!destination.exists()) destination.mkdirs();

            String[] files = source.list();
            if (files != null) {

                for (String file : files) {
                    File srcFile = new File(source, file);
                    File destFile = new File(destination, file);

                    copy(srcFile, destFile);
                }
            }
        } else {
            if (source.getName().equals(".nomedia")) return;

            try (InputStream in = new FileInputStream(source);
                 OutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean zipContainsFile(String zipPath, String fileName) {
        try {
            ZipInputStream zp = new ZipInputStream(new FileInputStream(zipPath));
            ZipEntry en;
            while ((en = zp.getNextEntry()) != null) {
                String name = en.getName();
                if (name.equals(fileName) || name.startsWith(fileName + File.separator)) {
                    zp.close();
                    return true;
                }
            }
            zp.close();
        } catch (Exception ignored) {
        }
        return false;
    }

    public void backup(Context context, String project_name) {
        String customFileName = ConfigActivity.getBackupFileName();

        String versionName = yB.c(lC.b(sc_id), "sc_ver_name");
        String versionCode = yB.c(lC.b(sc_id), "sc_ver_code");
        String pkgName = yB.c(lC.b(sc_id), "my_sc_pkg_name");
        String projectNameOnly = project_name.replace("_d", "").replace(File.separator, "");
        String finalFileName;

        try {
            finalFileName = customFileName
                    .replace("$projectName", projectNameOnly)
                    .replace("$versionCode", versionCode)
                    .replace("$versionName", versionName)
                    .replace("$pkgName", pkgName)
                    .replace("$versionCode", versionCode)
                    .replace("$timeInMs", String.valueOf(Calendar.getInstance(Locale.ENGLISH).getTimeInMillis()));
            Matcher matcher = Pattern.compile("\\$time\\((.*?)\\)").matcher(customFileName);
            while (matcher.find()) {
                finalFileName = finalFileName.replaceFirst(Pattern.quote(Objects.requireNonNull(matcher.group(0))), getFormattedDateFrom(matcher.group(1)));
            }
        } catch (Exception ignored) {
            if (context != null) {
                SketchwareUtil.toastError("Failed To Parse Custom Filename For Backup. Using default");
            }
            finalFileName = projectNameOnly + " v" + versionName + " (" + pkgName + ", " + versionCode + ") " + getFormattedDateFrom("yyyy-M-dd'T'HHmmss");
        }
        
        String backupDirToUse = customBackupDir != null ? customBackupDir : getBackupDir();
        if (customBackupDir == null) createBackupsFolder();

        File outFolder = new File(backupDirToUse, project_name + "_temp");
        File outZip = new File(backupDirToUse + (customBackupDir == null ? File.separator + projectNameOnly : ""), finalFileName +
                (project_name.contains("_d") ? project_name.replace(projectNameOnly, "") : "") + "." + EXTENSION);

        if (outZip.exists()) {
            backup(context, project_name + "_d");
            return;
        }
        
        if (outFolder.exists()) {
            FileUtil.deleteFile(outFolder.getAbsolutePath());
        }

        FileUtil.makeDir(outFolder.getAbsolutePath());
        if (customBackupDir == null) {
            FileUtil.makeDir(new File(backupDirToUse + File.separator + projectNameOnly).getAbsolutePath());
        }

        File dataF = new File(outFolder, "data");
        FileUtil.makeDir(dataF.getAbsolutePath());
        copySafe(getDataDir(), dataF);

        File resF = new File(outFolder, "resources");
        FileUtil.makeDir(resF.getAbsolutePath());

        for (String subfolder : resSubfolders) {
            File resSubf = new File(resF, subfolder);
            FileUtil.makeDir(resSubf.getAbsolutePath());
            copySafe(getResDir(subfolder), resSubf);
            if (!subfolder.equals("icons")) {
                createNomediaFileIn(resSubf);
            }
        }

        File projectF = new File(outFolder, "project");
        copy(getProjectPath(), projectF);

        if (backupLocalLibs) {
            File localLibs = getLocalLibsPath();
            if (localLibs.exists()) {
                try {
                    JSONArray ja = new JSONArray(FileUtil.readFile(localLibs.getAbsolutePath()));
                    File libsF = new File(outFolder, "local_libs");
                    libsF.mkdirs();
                    for (int i = 0; i < ja.length(); i++) {
                        JSONObject jo = ja.getJSONObject(i);
                        File f = new File(jo.getString("dexPath")).getParentFile();
                        copy(f, new File(libsF, f.getName()));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (backupCustomBlocks && context != null) {
            CustomBlocksManager cbm = new CustomBlocksManager(context, sc_id);

            Set<ExtraBlockInfo> blocks = new HashSet<>();
            Set<String> block_names = new HashSet<>();
            for (BlockBean bean : cbm.getUsedBlocks()) {
                if (!block_names.contains(bean.opCode)) {
                    block_names.add(bean.opCode);
                    if (cbm.contains(bean.opCode)) {
                        blocks.add(cbm.getExtraBlockInfo(bean.opCode));
                    } else {
                        var block = BlockLoader.getBlockInfo(bean.opCode);
                        blocks.add(block);
                    }
                }
            }

            String json = new Gson().toJson(blocks);

            File customBlocksF = new File(dataF, "custom_blocks");
            FileUtil.writeFile(customBlocksF.getAbsolutePath(), json);
        }

        try {
            zipFolder(outFolder, outZip);
            outPath = outZip;
        } catch (Exception e) {
            error = Log.getStackTraceString(e);
            outPath = null;
        } finally {
            FileUtil.deleteFile(outFolder.getAbsolutePath());
        }
    }

    private String getFormattedDateFrom(String format) {
        return new SimpleDateFormat(format, Locale.ENGLISH).format(Calendar.getInstance().getTime());
    }

    public File getOutFile() {
        return outPath;
    }

    public void setBackupLocalLibs(boolean b) {
        backupLocalLibs = b;
    }

    public void setBackupCustomBlocks(boolean b) {
        backupCustomBlocks = b;
    }

    public void restore(File swbPath) {
        String name = swbPath.getName();
        if (name.contains(".")) {
            name = name.substring(0, name.lastIndexOf("."));
        }
        restore(swbPath, name);
    }

    private void restore(File swbPath, String name) {
        createBackupsFolder();
        File outFolder = new File(getBackupDir(), name);

        if (outFolder.exists()) {
            restore(swbPath, name + "_d");
            return;
        }

        if (!unzip(swbPath, outFolder)) {
            error = "couldn't unzip the backup";
            restoreSuccess = false;
            return;
        }

        File project = new File(outFolder, "project");
        File data = new File(outFolder, "data");
        File res = new File(outFolder, "resources");

        HashMap<String, Object> map = getProject(project);

        if (map == null) {
            error = "couldn't read the project file";
            restoreSuccess = false;
            return;
        }

        map.put("sc_id", sc_id);

        if (!writeEncrypted(project, new Gson().toJson(map))) {
            error = "couldn't write to the project file";
            restoreSuccess = false;
            return;
        }

        copy(data, getDataDir());

        for (String subfolder : resSubfolders) {
            File subf = new File(res, subfolder);
            copySafe(subf, getResDir(subfolder));
        }

        getProjectPath().getParentFile().mkdirs();
        copy(project, getProjectPath());

        if (backupLocalLibs) {
            File local_libs = new File(outFolder, "local_libs");
            if (local_libs.exists()) {
                File[] local_libs_content = local_libs.listFiles();
                if (local_libs_content != null) {
                    for (File local_lib : local_libs_content) {
                        File local_lib_real_path = new File(getAllLocalLibsDir(), local_lib.getName());
                        if (!local_lib_real_path.exists()) {
                            local_lib_real_path.mkdirs();
                            copy(local_lib, local_lib_real_path);
                        }
                    }
                }
            }
        }

        FileUtil.deleteFile(outFolder.getAbsolutePath());
        restoreSuccess = true;
    }

    public String getError() {
        return error;
    }

    public boolean isRestoreSuccess() {
        return restoreSuccess;
    }

    private void createBackupsFolder() {
        String backupsPath = getBackupDir();
        FileUtil.makeDir(backupsPath);
    }

    private File getDataDir() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + sc_id);
    }

    private File getResDir(String subfolder) {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/resources/" + subfolder + "/" + sc_id);
    }

    private File getProjectPath() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/mysc/list/" + sc_id + "/project");
    }

    private File getLocalLibsPath() {
        return new File(Environment.getExternalStorageDirectory(),
                ".sketchware/data/" + sc_id + "/local_library");
    }
}
