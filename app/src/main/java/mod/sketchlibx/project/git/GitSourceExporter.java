package mod.sketchlibx.project.git;

import android.content.Context;

import java.io.File;
import java.util.HashMap;

import a.a.a.ProjectBuilder;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.xq;
import a.a.a.yq;
import pro.sketchware.utility.FileUtil;

public final class GitSourceExporter {

    private GitSourceExporter() {
    }

    public static void regenerate(Context context, String sc_id, File repoDir) throws Exception {
        jC.a(sc_id).j();
        jC.b(sc_id).m();
        jC.c(sc_id).l();
        jC.d(sc_id).x();

        hC hCVar = new hC(sc_id);
        kC kCVar = new kC(sc_id);
        eC eCVar = new eC(sc_id);
        iC iCVar = new iC(sc_id);

        hCVar.i(); kCVar.s(); eCVar.g(); eCVar.e(); iCVar.i();

        HashMap<String, Object> projectInfo = lC.b(sc_id);
        yq project_metadata = new yq(context, wq.d(sc_id), projectInfo);
        project_metadata.a(context, wq.e(xq.a(sc_id) ? "600" : sc_id));

        ProjectBuilder builder = new ProjectBuilder(null, context, project_metadata);
        project_metadata.a(iCVar, hCVar, eCVar, yq.ExportType.ANDROID_STUDIO);
        builder.buildBuiltInLibraryInformation();
        project_metadata.b(hCVar, eCVar, iCVar, builder.getBuiltInLibraryManager());
        project_metadata.a();
        project_metadata.f();

        File appDir = new File(repoDir, "app/src/main");
        FileUtil.makeDir(appDir.getAbsolutePath());

        File javaSrc = new File(project_metadata.javaFilesPath);
        if (javaSrc.exists() && javaSrc.isDirectory()) FileUtil.copyDirectory(javaSrc, new File(appDir, "java"));

        File resSrc = new File(project_metadata.resDirectoryPath);
        if (resSrc.exists() && resSrc.isDirectory()) FileUtil.copyDirectory(resSrc, new File(appDir, "res"));

        File assetsSrc = new File(project_metadata.assetsPath);
        if (assetsSrc.exists() && assetsSrc.isDirectory()) FileUtil.copyDirectory(assetsSrc, new File(appDir, "assets"));

        File manifestSrc = new File(project_metadata.projectMyscPath, "AndroidManifest.xml");
        if (manifestSrc.exists()) FileUtil.copyFile(manifestSrc.getAbsolutePath(), new File(appDir, "AndroidManifest.xml").getAbsolutePath());

        File rootGradleSrc = new File(project_metadata.projectMyscPath, "build.gradle");
        if (rootGradleSrc.exists()) FileUtil.copyFile(rootGradleSrc.getAbsolutePath(), new File(repoDir, "build.gradle").getAbsolutePath());

        File settingsGradleSrc = new File(project_metadata.projectMyscPath, "settings.gradle");
        if (settingsGradleSrc.exists()) FileUtil.copyFile(settingsGradleSrc.getAbsolutePath(), new File(repoDir, "settings.gradle").getAbsolutePath());

        File gradlePropertiesSrc = new File(project_metadata.projectMyscPath, "gradle.properties");
        if (gradlePropertiesSrc.exists()) FileUtil.copyFile(gradlePropertiesSrc.getAbsolutePath(), new File(repoDir, "gradle.properties").getAbsolutePath());

        File appGradleSrc = new File(project_metadata.projectMyscPath, "app" + File.separator + "build.gradle");
        if (appGradleSrc.exists()) FileUtil.copyFile(appGradleSrc.getAbsolutePath(), new File(repoDir, "app" + File.separator + "build.gradle").getAbsolutePath());
    }
}
