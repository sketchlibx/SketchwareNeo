package pro.sketchware.analysis.modules.manifest;

import java.util.List;

import a.a.a.hC;
import com.besome.sketch.beans.ProjectFileBean;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class MissingLauncherActivityCheck implements AnalysisCheck {

    public static final String FIX_ID = "fix_missing_launcher_activity";

    @Override public String id() { return "missing_launcher_activity"; }
    @Override public String category() { return "manifest"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        String launcher = AndroidManifestInjector.getLauncherActivity(ctx.getScId());

        hC files = ctx.getFileManager();
        List<ProjectFileBean> activities = files != null ? files.b() : null;

        if (launcher == null || launcher.isEmpty()) {
            report.addIssue(new Issue(
                    "MISSING_LAUNCHER_ACTIVITY", category(), Severity.CRITICAL,
                    "No launcher activity is set. The app will have no entry point and cannot be launched.",
                    null, FIX_ID
            ));
            return;
        }

        if (activities != null) {
            boolean exists = false;
            for (ProjectFileBean bean : activities) {
                if (bean != null && launcher.equals(bean.fileName)) { exists = true; break; }
            }
            if (!exists) {
                report.addIssue(new Issue(
                        "INVALID_LAUNCHER_ACTIVITY", category(), Severity.CRITICAL,
                        "Launcher activity \"" + launcher + "\" is set but no longer exists in the project.",
                        null, FIX_ID
                ));
            }
        }
    }
}
