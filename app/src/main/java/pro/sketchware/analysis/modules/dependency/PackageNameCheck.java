package pro.sketchware.analysis.modules.dependency;

import java.util.regex.Pattern;

import a.a.a.lC;

import pro.sketchware.analysis.core.AnalysisCheck;
import pro.sketchware.analysis.core.ProjectContext;
import pro.sketchware.analysis.core.SourceIndex;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public final class PackageNameCheck implements AnalysisCheck {

    private static final Pattern VALID_PACKAGE =
            Pattern.compile("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$");

    private static final java.util.Set<String> JAVA_RESERVED = java.util.Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while"
    );

    @Override public String id() { return "package_name_validation"; }
    @Override public String category() { return "dependency"; }

    @Override
    public void run(ProjectContext ctx, SourceIndex index, AnalysisReport.Builder report) {
        java.util.HashMap<String, Object> metadata = lC.b(ctx.getScId());
        if (metadata == null) return;

        Object pkgObj = metadata.get("my_sc_pkg_name");
        if (!(pkgObj instanceof String)) return;
        String pkgName = (String) pkgObj;

        if (pkgName.isEmpty() || !VALID_PACKAGE.matcher(pkgName).matches()) {
            report.addIssue(new Issue(
                    "INVALID_PACKAGE_NAME", category(), Severity.CRITICAL,
                    "Package name \"" + pkgName + "\" is not a valid Java package identifier.",
                    null, null
            ));
            return;
        }

        for (String segment : pkgName.split("\\.")) {
            if (JAVA_RESERVED.contains(segment)) {
                report.addIssue(new Issue(
                        "INVALID_PACKAGE_SEGMENT", category(), Severity.ERROR,
                        "Package segment \"" + segment + "\" in \"" + pkgName
                                + "\" is a reserved Java keyword and will fail to compile.",
                        null, null
                ));
            }
        }
    }
}
