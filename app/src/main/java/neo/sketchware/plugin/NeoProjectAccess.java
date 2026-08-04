package neo.sketchware.plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import a.a.a.hC;
import a.a.a.jC;
import com.besome.sketch.beans.ProjectFileBean;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public final class NeoProjectAccess {

    private final String scId;
    private final boolean canWrite;
    private final FilePathUtil paths = new FilePathUtil();

    NeoProjectAccess(String scId, boolean canWrite) {
        this.scId = scId;
        this.canWrite = canWrite;
    }

    private void requireWritePermission() {
        if (!canWrite) {
            throw new SecurityException("This plugin does not have the 'project.write' permission");
        }
    }

    public String getScId() {
        return scId;
    }

    public List<String> listActivities() {
        List<String> names = new ArrayList<>();
        hC files = jC.b(scId);
        if (files == null) return names;

        List<ProjectFileBean> activities = files.b();
        if (activities == null) return names;

        for (ProjectFileBean bean : activities) {
            if (bean != null && bean.fileName != null) names.add(bean.fileName);
        }
        return names;
    }

    public String readJavaFile(String activityFileName) {
        File file = new File(paths.getPathJava(scId), activityFileName + ".java");
        if (!file.exists()) return null;
        return FileUtil.readFile(file.getAbsolutePath());
    }

    public boolean writeJavaFile(String activityFileName, String content) {
        requireWritePermission();
        File file = new File(paths.getPathJava(scId), activityFileName + ".java");
        try {
            FileUtil.writeFile(file.getAbsolutePath(), content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public String readLayoutXml(String xmlFileName) {
        File file = new File(paths.getPathResource(scId), xmlFileName);
        if (!file.exists()) return null;
        return FileUtil.readFile(file.getAbsolutePath());
    }

    public boolean writeLayoutXml(String xmlFileName, String content) {
        requireWritePermission();
        File file = new File(paths.getPathResource(scId), xmlFileName);
        try {
            FileUtil.writeFile(file.getAbsolutePath(), content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public File getProjectDataDir() {
        return new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId);
    }
}
