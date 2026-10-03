package pro.sketchware.activities.editor.gradle;

import com.google.gson.Gson;

import java.io.File;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.build.BuildSettings;
import mod.hey.studios.util.Helper;
import mod.sketchlibx.importer.GradleParser;
import mod.sketchlibx.importer.ParsedGradle;
import pro.sketchware.utility.FileUtil;

public final class GradleFileValidator {

    public enum Severity {ERROR, WARNING}

    public static final class Issue {
        public final String file;
        public final int line;
        public final Severity severity;
        public final String message;

        Issue(String file, int line, Severity severity, String message) {
            this.file = file;
            this.line = line;
            this.severity = severity;
            this.message = message;
        }

        @Override
        public String toString() {
            return file + ":" + line + ": " + (severity == Severity.ERROR ? "error" : "warning") + ": " + message;
        }
    }

    private static final Pattern DEPENDENCIES_OPEN = Pattern.compile("^\\s*dependencies\\s*\\{");
    private static final Pattern DEPENDENCY_LINE = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\(?\\s*([\"'])([^\"']*)\\2");
    private static final Pattern COORDINATE = Pattern.compile("^[A-Za-z0-9_.\\-]+:[A-Za-z0-9_.\\-]+(?::[A-Za-z0-9_.\\-+]+)?(?::[A-Za-z0-9_.\\-]+)?(?:@[A-Za-z0-9]+)?$");
    private static final Pattern REPOSITORY_URL = Pattern.compile("\\burl\\s*=?\\s*(?:uri\\s*\\(\\s*)?[\"']([^\"']*)[\"']");

    private static final Set<String> SUPPORTED_JAVA_VERSIONS = Set.of(
            BuildSettings.SETTING_JAVA_VERSION_1_8,
            BuildSettings.SETTING_JAVA_VERSION_11,
            BuildSettings.SETTING_JAVA_VERSION_17);

    private GradleFileValidator() {
    }

    public static boolean hasErrors(List<Issue> issues) {
        for (Issue issue : issues) {
            if (issue.severity == Severity.ERROR) return true;
        }
        return false;
    }

    public static List<Issue> validate(File dir) {
        List<Issue> issues = new ArrayList<>();
        File app = new File(dir, CustomGradleBuildManager.FILE_APP_BUILD);
        if (!app.isFile()) {
            issues.add(new Issue(CustomGradleBuildManager.FILE_APP_BUILD, 0, Severity.ERROR, "File is missing"));
        } else {
            issues.addAll(validateContent(CustomGradleBuildManager.FILE_APP_BUILD, FileUtil.readFile(app.getAbsolutePath()), dir));
        }
        for (String name : new String[]{CustomGradleBuildManager.FILE_BUILD, CustomGradleBuildManager.FILE_SETTINGS, CustomGradleBuildManager.FILE_PROPERTIES}) {
            File file = new File(dir, name);
            if (!file.isFile()) {
                issues.add(new Issue(name, 0, Severity.WARNING, "File is missing"));
                continue;
            }
            issues.addAll(validateContent(name, FileUtil.readFile(file.getAbsolutePath()), dir));
        }
        return issues;
    }

    public static List<Issue> validateContent(String name, String text, File dir) {
        List<Issue> issues = new ArrayList<>();
        if (text == null) text = "";
        if (name.equals(CustomGradleBuildManager.FILE_PROPERTIES)) {
            checkProperties(name, text, issues);
            return issues;
        }
        if (text.trim().isEmpty()) {
            issues.add(new Issue(name, 1, Severity.ERROR, "File is empty"));
            return issues;
        }
        checkBalance(name, text, issues);
        if (hasErrors(issues)) return issues;
        checkRepositories(name, text, issues);
        if (name.equals(CustomGradleBuildManager.FILE_APP_BUILD)) {
            checkDependencies(name, text, issues);
            checkAppModule(name, text, dir, issues);
        }
        return issues;
    }

    private static void checkAppModule(String name, String text, File dir, List<Issue> issues) {
        if (!Pattern.compile("(?m)^\\s*android\\s*\\{").matcher(text).find()) {
            issues.add(new Issue(name, 1, Severity.ERROR, "Missing android { } block"));
        }
        File temp = null;
        try {
            temp = File.createTempFile("gradle_validate", ".gradle");
            FileUtil.writeFile(temp.getAbsolutePath(), text);
            ParsedGradle parsed = new GradleParser().parseFile(temp, null);
            if (parsed.explicitFields.contains(ParsedGradle.FIELD_JAVA_VERSION)
                    && !SUPPORTED_JAVA_VERSIONS.contains(parsed.javaVersion)) {
                issues.add(new Issue(name, lineOf(text, "sourceCompatibility"), Severity.ERROR,
                        "Unsupported Java source compatibility '" + parsed.javaVersion + "'. Supported: 1.8, 11, 17"));
            }
        } catch (Exception e) {
            issues.add(new Issue(name, 1, Severity.WARNING, "Could not inspect module settings: " + e.getMessage()));
        } finally {
            if (temp != null) temp.delete();
        }
    }

    private static int lineOf(String text, String needle) {
        int index = text.indexOf(needle);
        if (index < 0) return 1;
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (text.charAt(i) == '\n') line++;
        }
        return line;
    }

    private static void checkProperties(String name, String text, List<Issue> issues) {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
            if (line.endsWith("\\")) continue;
            if (line.indexOf('=') < 0 && line.indexOf(':') < 0) {
                issues.add(new Issue(name, i + 1, Severity.ERROR, "Invalid property line '" + line + "', expected key=value"));
            }
        }
        try {
            new Properties().load(new StringReader(text));
        } catch (Exception e) {
            issues.add(new Issue(name, 1, Severity.ERROR, "Cannot parse properties: " + e.getMessage()));
        }
    }

    private static void checkBalance(String name, String text, List<Issue> out) {
        Deque<int[]> stack = new ArrayDeque<>();
        int line = 1;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\n') {
                line++;
                i++;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                while (i < n && text.charAt(i) != '\n') i++;
                continue;
            }
            if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int startLine = line;
                i += 2;
                while (i + 1 < n && !(text.charAt(i) == '*' && text.charAt(i + 1) == '/')) {
                    if (text.charAt(i) == '\n') line++;
                    i++;
                }
                if (i + 1 >= n) {
                    out.add(new Issue(name, startLine, Severity.ERROR, "Unterminated block comment"));
                    return;
                }
                i += 2;
                continue;
            }
            if (c == '"' || c == '\'') {
                String triple = c == '"' ? "\"\"\"" : "'''";
                int startLine = line;
                if (text.startsWith(triple, i)) {
                    i += 3;
                    while (i < n && !text.startsWith(triple, i)) {
                        if (text.charAt(i) == '\n') line++;
                        i++;
                    }
                    if (i >= n) {
                        out.add(new Issue(name, startLine, Severity.ERROR, "Unterminated multi-line string"));
                        return;
                    }
                    i += 3;
                    continue;
                }
                i++;
                boolean closed = false;
                while (i < n) {
                    char d = text.charAt(i);
                    if (d == '\\') {
                        i += 2;
                        continue;
                    }
                    if (d == '\n') break;
                    if (d == c) {
                        closed = true;
                        i++;
                        break;
                    }
                    i++;
                }
                if (!closed) {
                    out.add(new Issue(name, startLine, Severity.ERROR, "Unterminated string literal"));
                }
                continue;
            }
            if (c == '{' || c == '(' || c == '[') {
                stack.push(new int[]{c, line});
            } else if (c == '}' || c == ')' || c == ']') {
                char expected = c == '}' ? '{' : c == ')' ? '(' : '[';
                if (stack.isEmpty()) {
                    out.add(new Issue(name, line, Severity.ERROR, "Unexpected '" + c + "'"));
                    return;
                }
                int[] top = stack.pop();
                if (top[0] != expected) {
                    out.add(new Issue(name, line, Severity.ERROR,
                            "Unexpected '" + c + "', '" + (char) top[0] + "' opened at line " + top[1] + " is not closed"));
                    return;
                }
            }
            i++;
        }
        if (!stack.isEmpty()) {
            int[] top = stack.peek();
            out.add(new Issue(name, top[1], Severity.ERROR, "Unclosed '" + (char) top[0] + "'"));
        }
    }

    private static void checkDependencies(String name, String text, List<Issue> issues) {
        String[] lines = text.split("\n", -1);
        boolean inside = false;
        int depth = 0;
        Set<String> seen = new HashSet<>();
        for (int idx = 0; idx < lines.length; idx++) {
            String line = lines[idx];
            if (!inside) {
                if (DEPENDENCIES_OPEN.matcher(line).find()) {
                    inside = true;
                    depth = count(line, '{') - count(line, '}');
                    if (depth <= 0) inside = false;
                }
                continue;
            }
            if (depth == 1) {
                Matcher m = DEPENDENCY_LINE.matcher(line);
                if (m.find()) {
                    String value = m.group(3);
                    if (value.contains("$")) {
                        issues.add(new Issue(name, idx + 1, Severity.ERROR,
                                "Variable in dependency '" + value + "' cannot be resolved; use a literal coordinate"));
                    } else if (!COORDINATE.matcher(value).matches()) {
                        issues.add(new Issue(name, idx + 1, Severity.ERROR,
                                "Malformed dependency coordinate '" + value + "', expected group:artifact:version"));
                    } else {
                        String[] parts = value.split(":");
                        if (parts.length < 3) {
                            issues.add(new Issue(name, idx + 1, Severity.WARNING,
                                    "Dependency '" + value + "' has no version and cannot be resolved"));
                        }
                        String key = parts[0] + ":" + parts[1];
                        if (!seen.add(key)) {
                            issues.add(new Issue(name, idx + 1, Severity.WARNING, "Duplicate dependency " + key));
                        }
                    }
                }
            }
            depth += count(line, '{') - count(line, '}');
            if (depth <= 0) inside = false;
        }
    }

    private static int count(String s, char c) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) total++;
        }
        return total;
    }

    private static void checkRepositories(String name, String text, List<Issue> issues) {
        Set<String> known = loadKnownRepositoryUrls();
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String code = lines[i];
            int comment = code.indexOf("//");
            if (comment >= 0) code = code.substring(0, comment);
            Matcher m = REPOSITORY_URL.matcher(code);
            while (m.find()) {
                String url = m.group(1).trim();
                if (!isValidRepositoryUrl(url)) {
                    issues.add(new Issue(name, i + 1, Severity.ERROR, "Invalid repository URL '" + url + "'"));
                } else if (known != null && !known.contains(normalizeUrl(url))) {
                    issues.add(new Issue(name, i + 1, Severity.WARNING,
                            "Repository " + url + " is not in Neo's repository list and will not be used to resolve dependencies"));
                }
            }
        }
    }

    private static boolean isValidRepositoryUrl(String url) {
        if (!(url.startsWith("http://") || url.startsWith("https://"))) return false;
        try {
            URI uri = new URI(url);
            return uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeUrl(String url) {
        String value = url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.toLowerCase();
    }

    private static Set<String> loadKnownRepositoryUrls() {
        String path = FileUtil.getExternalStorageDir() + "/.sketchware/libs/repositories.json";
        if (!FileUtil.isExistFile(path)) return null;
        try {
            ArrayList<HashMap<String, Object>> repos = new Gson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
            if (repos == null) return null;
            Set<String> urls = new HashSet<>();
            for (HashMap<String, Object> repo : repos) {
                Object url = repo.get("url");
                if (url instanceof String) urls.add(normalizeUrl((String) url));
            }
            return urls;
        } catch (Exception e) {
            return null;
        }
    }
}
