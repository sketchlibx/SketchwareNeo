package mod.hey.studios.ide.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleLogParser {
    private static final Pattern JAVAC_PATTERN = Pattern.compile("([^/\\\\]+\\.java):(\\d+): (error|warning): (.+)");
    private static final Pattern AAPT_PATTERN = Pattern.compile("([^/\\\\]+\\.xml):(\\d+): (error|warning): (.+)");
    private static final Pattern GRADLE_FILE_PATTERN = Pattern.compile("([^/\\\\\\s']+\\.(?:gradle|gradle\\.kts|properties)):(\\d+): (error|warning): (.+)");
    private static final Pattern GRADLE_SCRIPT_PATTERN = Pattern.compile("([^/\\\\\\s']+\\.gradle(?:\\.kts)?)'?: ?(?:line: )?(\\d+): (.+)");
    private static final Pattern KOTLIN_PATTERN = Pattern.compile("^([ew]): (?:file://)?(.*?\\.kts?):? ?\\((\\d+), ?(\\d+)\\): (.+)");
    private static final Pattern NATIVE_PATTERN = Pattern.compile("([^/\\\\\\s]+\\.(?:c|cc|cpp|cxx|h|hh|hpp)):(\\d+):(\\d+): (fatal error|error|warning): (.+)");
    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("Could not (find|resolve|download|get) (.+?)(?:\\.$| required by| in |$)");
    private static final Pattern DEXER_PATTERN = Pattern.compile("^(?:ERROR: |Error: )?(D8|R8|Dx|DEX): (.+)");
    private static final Pattern UNSUPPORTED_VERSION_PATTERN = Pattern.compile("(Unsupported class[ -]file major version \\d+|Unsupported (?:Java|JDK) (?:version|class)[^\\n]*|invalid source release: [\\w.]+|invalid target release: [\\w.]+)");
    private static final Pattern AAPT_NO_FILE_PATTERN = Pattern.compile("^(?:AAPT2?: )?error: (.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NETWORK_PATTERN = Pattern.compile("(UnknownHostException|ConnectException|SocketTimeoutException|SSLHandshakeException|Network is unreachable|Unable to resolve host)[^\\n]*");

    public static List<Diagnostic> parseLogs(String buildOutput) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (buildOutput == null || buildOutput.isEmpty()) return diagnostics;

        String[] lines = buildOutput.split("\n");
        for (String line : lines) {
            Matcher javaMatcher = JAVAC_PATTERN.matcher(line);
            if (javaMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        severityOf(javaMatcher.group(3)),
                        javaMatcher.group(1),
                        Integer.parseInt(javaMatcher.group(2)),
                        0,
                        javaMatcher.group(4)
                ));
                continue;
            }

            Matcher aaptMatcher = AAPT_PATTERN.matcher(line);
            if (aaptMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        severityOf(aaptMatcher.group(3)),
                        aaptMatcher.group(1),
                        Integer.parseInt(aaptMatcher.group(2)),
                        0,
                        aaptMatcher.group(4)
                ));
                continue;
            }

            Matcher gradleFileMatcher = GRADLE_FILE_PATTERN.matcher(line);
            if (gradleFileMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        severityOf(gradleFileMatcher.group(3)),
                        gradleFileMatcher.group(1),
                        Integer.parseInt(gradleFileMatcher.group(2)),
                        0,
                        gradleFileMatcher.group(4)
                ));
                continue;
            }

            Matcher kotlinMatcher = KOTLIN_PATTERN.matcher(line);
            if (kotlinMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        kotlinMatcher.group(1).equals("e") ? Diagnostic.Severity.ERROR : Diagnostic.Severity.WARNING,
                        baseName(kotlinMatcher.group(2)),
                        Integer.parseInt(kotlinMatcher.group(3)),
                        Integer.parseInt(kotlinMatcher.group(4)),
                        kotlinMatcher.group(5)
                ));
                continue;
            }

            Matcher nativeMatcher = NATIVE_PATTERN.matcher(line);
            if (nativeMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        nativeMatcher.group(4).equals("warning") ? Diagnostic.Severity.WARNING : Diagnostic.Severity.ERROR,
                        nativeMatcher.group(1),
                        Integer.parseInt(nativeMatcher.group(2)),
                        Integer.parseInt(nativeMatcher.group(3)),
                        nativeMatcher.group(5)
                ));
                continue;
            }

            Matcher scriptMatcher = GRADLE_SCRIPT_PATTERN.matcher(line);
            if (scriptMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        scriptMatcher.group(1),
                        Integer.parseInt(scriptMatcher.group(2)),
                        0,
                        scriptMatcher.group(3)
                ));
                continue;
            }

            Matcher dependencyMatcher = DEPENDENCY_PATTERN.matcher(line);
            if (dependencyMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        "dependencies",
                        0,
                        0,
                        line.trim()
                ));
                continue;
            }

            Matcher unsupportedMatcher = UNSUPPORTED_VERSION_PATTERN.matcher(line);
            if (unsupportedMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        "toolchain",
                        0,
                        0,
                        unsupportedMatcher.group(1)
                ));
                continue;
            }

            Matcher dexerMatcher = DEXER_PATTERN.matcher(line);
            if (dexerMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        dexerMatcher.group(1),
                        0,
                        0,
                        dexerMatcher.group(2)
                ));
                continue;
            }

            Matcher networkMatcher = NETWORK_PATTERN.matcher(line);
            if (networkMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        "network",
                        0,
                        0,
                        networkMatcher.group(0).trim()
                ));
                continue;
            }

            Matcher aaptNoFileMatcher = AAPT_NO_FILE_PATTERN.matcher(line.trim());
            if (aaptNoFileMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        Diagnostic.Severity.ERROR,
                        "aapt2",
                        0,
                        0,
                        aaptNoFileMatcher.group(1)
                ));
            }
        }
        return diagnostics;
    }

    private static Diagnostic.Severity severityOf(String level) {
        return level.equals("error") ? Diagnostic.Severity.ERROR : Diagnostic.Severity.WARNING;
    }

    private static String baseName(String path) {
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
