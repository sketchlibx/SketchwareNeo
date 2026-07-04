package pro.sketchware.analysis.autofix.fixes;

import java.util.ArrayList;
import java.util.List;

import a.a.a.hC;
import com.besome.sketch.beans.ProjectFileBean;
import mod.hilal.saif.android_manifest.AndroidManifestInjector;

import pro.sketchware.analysis.autofix.FixAction;
import pro.sketchware.analysis.autofix.FixPreview;
import pro.sketchware.analysis.autofix.UndoManager;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.model.Issue;

public final class MissingLauncherActivityFix implements FixAction {

    @Override
    public FixPreview preview(ProjectContext ctx, Issue issue) {
        hC files = ctx.getFileManager();
        List<ProjectFileBean> activities = files != null ? files.b() : null;
        if (activities == null || activities.isEmpty()) {
            return new FixPreview("No activities exist in this project — cannot auto-fix.", new ArrayList<>());
        }

        String candidate = activities.get(0).fileName;
        List<String> affected = new ArrayList<>();
        affected.add("activity_launcher.txt");

        return new FixPreview(
                "Set \"" + candidate + "\" (first activity in the project) as the launcher activity.",
                affected
        );
    }

    @Override
    public void apply(ProjectContext ctx, Issue issue) {
        hC files = ctx.getFileManager();
        List<ProjectFileBean> activities = files != null ? files.b() : null;
        if (activities == null || activities.isEmpty()) return;

        String candidate = activities.get(0).fileName;
        AndroidManifestInjector.setLauncherActivity(ctx.getScId(), candidate);
    }

    public static UndoManager.Snapshot snapshotBefore(ProjectContext ctx) {
        String previous = AndroidManifestInjector.getLauncherActivity(ctx.getScId());
        return () -> AndroidManifestInjector.setLauncherActivity(ctx.getScId(), previous == null ? "" : previous);
    }
}
