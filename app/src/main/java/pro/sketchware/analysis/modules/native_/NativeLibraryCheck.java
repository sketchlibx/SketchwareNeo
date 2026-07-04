package pro.sketchware.analysis.modules.native_;

import java.io.File;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class NativeLibraryCheck implements AnalysisCheck {

    @Override public String id() { return "native_cmake_check"; }
    @Override public String category() { return "native"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        String cppPath = ctx.paths().getPathCpp(ctx.getScId());
        File cppDir = new File(cppPath);
        if (!cppDir.exists() || !cppDir.isDirectory()) return;

        boolean hasNativeSource = containsExtension(cppDir, ".cpp", ".c", ".h", ".hpp");
        if (!hasNativeSource) return;

        boolean hasCMakeLists = new File(cppDir, "CMakeLists.txt").exists();
        if (!hasCMakeLists) {
            report.addIssue(new Issue(
                    "MISSING_CMAKELISTS", category(), Severity.ERROR,
                    "C/C++ source files exist but no CMakeLists.txt was found — native build will fail.",
                    cppPath, null
            ));
        }
    }

    private static boolean containsExtension(File dir, String... extensions) {
        File[] children = dir.listFiles();
        if (children == null) return false;
        for (File child : children) {
            if (child.isDirectory()) {
                if (containsExtension(child, extensions)) return true;
                continue;
            }
            for (String ext : extensions) {
                if (child.getName().endsWith(ext)) return true;
            }
        }
        return false;
    }
}
