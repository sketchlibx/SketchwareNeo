package mod.hey.studios.ide.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GradleLogParser {
    // Regex to match standard Javac errors: /path/to/File.java:42: error: message
    private static final Pattern JAVAC_PATTERN = Pattern.compile("([^/\\\\]+\\.java):(\\d+): (error|warning): (.+)");
    // Regex to match AAPT2/XML errors
    private static final Pattern AAPT_PATTERN = Pattern.compile("([^/\\\\]+\\.xml):(\\d+): (error|warning): (.+)");

    public static List<Diagnostic> parseLogs(String buildOutput) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (buildOutput == null || buildOutput.isEmpty()) return diagnostics;

        String[] lines = buildOutput.split("\n");
        for (String line : lines) {
            Matcher javaMatcher = JAVAC_PATTERN.matcher(line);
            if (javaMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        javaMatcher.group(3).equals("error") ? Diagnostic.Severity.ERROR : Diagnostic.Severity.WARNING,
                        javaMatcher.group(1),
                        Integer.parseInt(javaMatcher.group(2)),
                        0, // Javac output doesn't always provide col directly in single line
                        javaMatcher.group(4)
                ));
                continue;
            }

            Matcher aaptMatcher = AAPT_PATTERN.matcher(line);
            if (aaptMatcher.find()) {
                diagnostics.add(new Diagnostic(
                        aaptMatcher.group(3).equals("error") ? Diagnostic.Severity.ERROR : Diagnostic.Severity.WARNING,
                        aaptMatcher.group(1),
                        Integer.parseInt(aaptMatcher.group(2)),
                        0,
                        aaptMatcher.group(4)
                ));
            }
        }
        return diagnostics;
    }
}
