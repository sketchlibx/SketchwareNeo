package pro.sketchware.analysis.prebuild;

import java.io.File;
import java.util.List;

import pro.sketchware.analysis.core.AnalysisEngine;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Severity;

public final class PreBuildGate {

    public interface Callback {
        void onAllowed();
        void onBlocked(AnalysisReport report);
    }

    public static void check(String scId, List<File> sourceRoots, Callback callback) {
        AnalysisEngine engine = new AnalysisEngine(scId);
        AnalysisReport report = engine.run(sourceRoots);

        if (report.hasAnyAtLeast(Severity.CRITICAL)) {
            callback.onBlocked(report);
        } else {
            callback.onAllowed();
        }
    }
}
