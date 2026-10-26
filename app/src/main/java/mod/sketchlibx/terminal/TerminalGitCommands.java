package mod.sketchlibx.terminal;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.sketchlibx.project.git.GitEngine;

/**
 * Executes Git commands
 */
public final class TerminalGitCommands {

    public static final int EXIT_OK = 0;
    public static final int EXIT_FAILED = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_NEEDS_CONFIRMATION = 10;
    public static final int EXIT_NOT_REPOSITORY = 128;
    public static final int EXIT_CANCELED = 130;

    public interface Settings {
        String username();

        String token();

        String authorName();

        String authorEmail();

        boolean confirmPush();
    }

    public interface Output {
        void line(String text);
    }

    public interface Cancellation {
        boolean isCancelled();
    }

    public interface SourceSync {
        void run() throws Exception;
    }

    private static final int MAX_LISTED = 200;
    private static final int DEFAULT_LOG = 20;
    private static final int MAX_LOG = 200;
    private static final Pattern BRANCH_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]*");
    private static final Pattern REMOTE_NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Pattern REMOTE_URL = Pattern.compile("^(https?|git|ssh)://.+|^[\\w.-]+@[\\w.-]+:.+");
    private static final Pattern URL_USERINFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/\\s@]*:[^/\\s@]*@");

    private final GitEngine engine;
    private final Settings settings;
    private final Function<Exception, String> errorMapper;
    private final SourceSync sourceSync;

    public TerminalGitCommands(GitEngine engine, Settings settings, Function<Exception, String> errorMapper, SourceSync sourceSync) {
        this.engine = engine;
        this.settings = settings;
        this.errorMapper = errorMapper;
        this.sourceSync = sourceSync;
    }

    public static String redact(String text, String token) {
        if (text == null) return "";
        String result = URL_USERINFO.matcher(text).replaceAll("$1***@");
        if (token != null && token.length() >= 4) result = result.replace(token, "***");
        return result;
    }

    public static List<String> helpLines() {
        return List.of(
                "Supported git commands (built-in Git client, not the git program):",
                "  git status                     Show branch, staged, unstaged and untracked files.",
                "  git init                       Create the project's Git workspace (never re-creates one).",
                "  git add <path>... | . | -A     Stage files relative to the workspace.",
                "  git commit -m <message> [-m <message>]   Commit staged changes with the Git client's author.",
                "  git log [-n <count>]           Show recent commits (default 20, at most 200).",
                "  git branch [-a]                List branches.",
                "  git branch <name>              Create a branch (does not switch to it).",
                "  git branch -d <name> | -D <name>   Delete a branch after confirmation.",
                "  git remote [-v]                List remotes.",
                "  git remote add <name> <url>    Add a remote (URLs with embedded passwords are refused).",
                "  git remote set-url <name> <url>   Change a remote's URL.",
                "  git remote remove <name>       Remove a remote after confirmation.",
                "  git pull                       Pull from the tracked remote.",
                "  git push                       Push the current branch (confirmation as set in Git Client settings).",
                "  git sync                       Regenerate the exported source in the Git workspace (same as Refresh in the Git Client).",
                "Not supported: clone, fetch, checkout, merge, rebase, reset, stash, diff, tag, config and all other git commands.",
                "Credentials, author name and email come from Git Client > Settings and are never printed.");
    }

    public int run(File workspace, List<String> args, boolean confirmed, Output rawOut, Cancellation cancel) {
        String token = settings.token();
        Output out = text -> rawOut.line(redact(text, token));
        if (args.isEmpty()) {
            for (String line : helpLines()) out.line(line);
            return EXIT_USAGE;
        }
        String command = args.get(0);
        List<String> rest = args.subList(1, args.size());
        try {
            switch (command) {
                case "help", "--help", "-h" -> {
                    for (String line : helpLines()) out.line(line);
                    return EXIT_OK;
                }
                case "init" -> {
                    return init(workspace, rest, out);
                }
                case "status" -> {
                    return needRepository(workspace, out) ? status(workspace, rest, out) : EXIT_NOT_REPOSITORY;
                }
                case "add" -> {
                    return needRepository(workspace, out) ? add(workspace, rest, out) : EXIT_NOT_REPOSITORY;
                }
                case "commit" -> {
                    return needRepository(workspace, out) ? commit(workspace, rest, out) : EXIT_NOT_REPOSITORY;
                }
                case "log" -> {
                    return needRepository(workspace, out) ? log(workspace, rest, out) : EXIT_NOT_REPOSITORY;
                }
                case "branch" -> {
                    return needRepository(workspace, out) ? branch(workspace, rest, confirmed, out) : EXIT_NOT_REPOSITORY;
                }
                case "remote" -> {
                    return needRepository(workspace, out) ? remote(workspace, rest, confirmed, out) : EXIT_NOT_REPOSITORY;
                }
                case "pull" -> {
                    return needRepository(workspace, out) ? pull(workspace, rest, out, cancel) : EXIT_NOT_REPOSITORY;
                }
                case "push" -> {
                    return needRepository(workspace, out) ? push(workspace, rest, confirmed, out, cancel) : EXIT_NOT_REPOSITORY;
                }
                case "sync" -> {
                    return sync(workspace, rest, out);
                }
                default -> {
                    out.line("git " + command + " is not supported by the built-in Git client in the terminal.");
                    out.line("Run 'git help' for the supported commands.");
                    return EXIT_USAGE;
                }
            }
        } catch (CancelledException e) {
            out.line("Operation cancelled.");
            return EXIT_CANCELED;
        } catch (Exception e) {
            if (cancel != null && cancel.isCancelled()) {
                out.line("Operation cancelled.");
                return EXIT_CANCELED;
            }
            out.line(describe(e));
            return EXIT_FAILED;
        }
    }

    private static final class CancelledException extends RuntimeException {
    }

    private String describe(Exception e) {
        String message = errorMapper.apply(e);
        return message == null || message.isEmpty() ? "Git operation failed." : message;
    }

    private boolean needRepository(File workspace, Output out) {
        if (engine.isRepository(workspace)) return true;
        out.line("fatal: this project has no Git repository yet. Run 'git init' to create it.");
        return false;
    }

    private int usage(Output out, String text) {
        out.line("usage: " + text);
        return EXIT_USAGE;
    }

    private int init(File workspace, List<String> args, Output out) throws Exception {
        if (!args.isEmpty()) return usage(out, "git init");
        if (engine.isRepository(workspace)) {
            out.line("Git repository already initialized in " + workspace + ". Nothing was changed.");
            return EXIT_OK;
        }
        engine.initialize(workspace);
        if (!engine.isRepository(workspace)) {
            out.line("Git initialization did not create a repository in " + workspace + ".");
            return EXIT_FAILED;
        }
        out.line("Initialized empty Git repository in " + workspace);
        return EXIT_OK;
    }

    private int status(File workspace, List<String> args, Output out) throws Exception {
        if (!args.isEmpty()) return usage(out, "git status");
        GitEngine.Status s = engine.status(workspace);
        out.line("On branch " + s.branch);
        if (!s.hasCommits) out.line("No commits yet");
        if (s.ahead > 0 || s.behind > 0) {
            StringBuilder sb = new StringBuilder("Your branch is");
            if (s.ahead > 0) sb.append(" ahead by ").append(s.ahead).append(s.ahead == 1 ? " commit" : " commits");
            if (s.ahead > 0 && s.behind > 0) sb.append(" and");
            if (s.behind > 0) sb.append(" behind by ").append(s.behind).append(s.behind == 1 ? " commit" : " commits");
            out.line(sb.append(" of its upstream.").toString());
        }
        if (!s.conflicting.isEmpty()) {
            out.line("");
            out.line("Unmerged paths:");
            listPaths(out, "both modified:   ", s.conflicting);
        }
        if (s.hasStagedChanges()) {
            out.line("");
            out.line("Changes to be committed:");
            listPaths(out, "new file:   ", s.added);
            listPaths(out, "modified:   ", s.changed);
            listPaths(out, "deleted:    ", s.removed);
        }
        if (!s.modified.isEmpty() || !s.missing.isEmpty()) {
            out.line("");
            out.line("Changes not staged for commit:");
            listPaths(out, "modified:   ", s.modified);
            listPaths(out, "deleted:    ", s.missing);
        }
        if (!s.untracked.isEmpty()) {
            out.line("");
            out.line("Untracked files:");
            listPaths(out, "", s.untracked);
        }
        if (s.isClean()) out.line("nothing to commit, working tree clean");
        return EXIT_OK;
    }

    private void listPaths(Output out, String label, List<String> paths) {
        int shown = 0;
        for (String path : paths) {
            if (shown++ >= MAX_LISTED) {
                out.line("        ... and " + (paths.size() - MAX_LISTED) + " more");
                return;
            }
            out.line("        " + label + path);
        }
    }

    static String validatePathspec(String spec) {
        if (spec.isEmpty()) return "empty path";
        if (spec.startsWith("/") || spec.startsWith("\\") || spec.indexOf('\\') >= 0) return "paths must be relative to the workspace";
        for (String part : spec.split("/")) {
            if (part.equals("..")) return "paths may not contain '..'";
            if (part.equals(".git")) return "the .git directory cannot be staged";
        }
        return null;
    }

    private int add(File workspace, List<String> args, Output out) throws Exception {
        if (args.isEmpty()) return usage(out, "git add <path>... | . | -A");
        boolean all = false;
        List<String> patterns = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("-A") || arg.equals("--all")) {
                all = true;
            } else if (arg.equals(".")) {
                all = true;
            } else if (arg.startsWith("-")) {
                return usage(out, "git add <path>... | . | -A  (unsupported option " + arg + ")");
            } else {
                String problem = validatePathspec(arg);
                if (problem != null) {
                    out.line("fatal: " + arg + ": " + problem);
                    return EXIT_FAILED;
                }
                patterns.add(arg);
            }
        }
        engine.add(workspace, patterns, all);
        out.line(all ? "Staged all changes." : "Staged " + patterns.size() + (patterns.size() == 1 ? " path." : " paths."));
        return EXIT_OK;
    }

    private int commit(File workspace, List<String> args, Output out) throws Exception {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (arg.equals("-m") || arg.equals("--message")) {
                if (i + 1 >= args.size()) return usage(out, "git commit -m <message>");
                parts.add(args.get(++i));
            } else if (arg.startsWith("-m") && arg.length() > 2) {
                parts.add(arg.substring(2));
            } else {
                return usage(out, "git commit -m <message> [-m <message>]  (unsupported argument " + arg + ")");
            }
        }
        if (parts.isEmpty()) return usage(out, "git commit -m <message>");
        StringBuilder message = new StringBuilder();
        for (String part : parts) {
            if (message.length() > 0) message.append("\n\n");
            message.append(part.trim());
        }
        if (message.toString().trim().isEmpty()) {
            out.line("fatal: the commit message is empty.");
            return EXIT_FAILED;
        }
        GitEngine.Status s = engine.status(workspace);
        if (!s.conflicting.isEmpty()) {
            out.line("error: resolve the conflicts first (see 'git status').");
            return EXIT_FAILED;
        }
        if (!s.hasStagedChanges()) {
            out.line(s.isClean() ? "nothing to commit, working tree clean"
                    : "nothing to commit: no changes are staged (use 'git add' first).");
            return EXIT_FAILED;
        }
        String name = settings.authorName().isEmpty() ? "Sketchware User" : settings.authorName();
        String email = settings.authorEmail().isEmpty() ? "user@sketchware.neo" : settings.authorEmail();
        out.line(engine.commit(workspace, message.toString(), name, email));
        return EXIT_OK;
    }

    private int log(File workspace, List<String> args, Output out) throws Exception {
        int count = DEFAULT_LOG;
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            String number = null;
            if (arg.equals("-n")) {
                if (i + 1 >= args.size()) return usage(out, "git log [-n <count>]");
                number = args.get(++i);
            } else if (arg.matches("-n\\d+")) {
                number = arg.substring(2);
            } else if (arg.matches("-\\d+")) {
                number = arg.substring(1);
            } else if (!arg.equals("--oneline")) {
                return usage(out, "git log [-n <count>]  (unsupported argument " + arg + ")");
            }
            if (number != null) {
                try {
                    count = Integer.parseInt(number);
                } catch (NumberFormatException e) {
                    return usage(out, "git log [-n <count>]");
                }
                if (count < 1 || count > MAX_LOG) {
                    out.line("fatal: the log count must be between 1 and " + MAX_LOG + ".");
                    return EXIT_FAILED;
                }
            }
        }
        List<GitEngine.LogEntry> entries = engine.log(workspace, count);
        if (entries.isEmpty()) {
            out.line("No commits yet.");
            return EXIT_OK;
        }
        for (GitEngine.LogEntry entry : entries) {
            out.line(entry.shortId + " " + entry.subject + " (" + entry.author + ", " + entry.date + ")");
        }
        return EXIT_OK;
    }

    private int branch(File workspace, List<String> args, boolean confirmed, Output out) throws Exception {
        if (args.isEmpty() || (args.size() == 1 && (args.get(0).equals("-a") || args.get(0).equals("--all")))) {
            boolean all = !args.isEmpty();
            List<GitEngine.Branch> branches = engine.branches(workspace, all);
            if (branches.isEmpty()) {
                out.line("No branches yet. Make the first commit to create one.");
                return EXIT_OK;
            }
            for (GitEngine.Branch b : branches) {
                out.line((b.current ? "* " : "  ") + (b.remote ? "remotes/" : "") + b.name);
            }
            return EXIT_OK;
        }
        String first = args.get(0);
        if (first.equals("-d") || first.equals("-D")) {
            if (args.size() != 2) return usage(out, "git branch -d <name>");
            String name = args.get(1);
            String problem = validateBranchName(name);
            if (problem != null) {
                out.line("fatal: " + problem);
                return EXIT_FAILED;
            }
            if (!confirmed) {
                out.line("Branch '" + name + "' will be deleted" + (first.equals("-D") ? " even if it is not fully merged." : "."));
                return EXIT_NEEDS_CONFIRMATION;
            }
            engine.deleteBranch(workspace, name, first.equals("-D"));
            out.line("Deleted branch " + name + ".");
            return EXIT_OK;
        }
        if (args.size() != 1 || first.startsWith("-")) return usage(out, "git branch [-a] | git branch <name> | git branch -d <name>");
        String problem = validateBranchName(first);
        if (problem != null) {
            out.line("fatal: " + problem);
            return EXIT_FAILED;
        }
        for (GitEngine.Branch b : engine.branches(workspace, true)) {
            if (b.name.equalsIgnoreCase(first)) {
                out.line("fatal: a branch named '" + first + "' already exists.");
                return EXIT_FAILED;
            }
        }
        engine.createBranch(workspace, first);
        out.line("Created branch " + first + ".");
        return EXIT_OK;
    }

    static String validateBranchName(String name) {
        if (name.isEmpty()) return "Branch name is required";
        if (!BRANCH_NAME.matcher(name).matches() || name.endsWith(".lock") || name.contains("..") || name.endsWith("/") || name.endsWith(".")) {
            return "Invalid branch name";
        }
        return null;
    }

    private static String validateRemoteUrl(String url) {
        if (url.isEmpty()) return "Remote URL is required";
        if (!REMOTE_URL.matcher(url).matches()) return "Enter a valid Git URL";
        Matcher embedded = URL_USERINFO.matcher(url);
        if (embedded.find()) {
            return "URLs with an embedded password are refused. Enter your username and token in Git Client > Settings instead";
        }
        return null;
    }

    private int remote(File workspace, List<String> args, boolean confirmed, Output out) throws Exception {
        if (args.isEmpty() || (args.size() == 1 && args.get(0).equals("-v"))) {
            boolean verbose = !args.isEmpty();
            List<GitEngine.Remote> remotes = engine.remotes(workspace);
            if (remotes.isEmpty()) {
                out.line("No remotes configured. Add one with: git remote add <name> <url>");
                return EXIT_OK;
            }
            for (GitEngine.Remote r : remotes) {
                if (!verbose) {
                    out.line(r.name);
                } else if (r.urls.isEmpty()) {
                    out.line(r.name + "\t(no URL)");
                } else {
                    for (String url : r.urls) out.line(r.name + "\t" + url);
                }
            }
            return EXIT_OK;
        }
        String sub = args.get(0);
        switch (sub) {
            case "add" -> {
                if (args.size() != 3) return usage(out, "git remote add <name> <url>");
                String name = args.get(1);
                String url = args.get(2);
                if (!REMOTE_NAME.matcher(name).matches()) {
                    out.line("fatal: Invalid remote name");
                    return EXIT_FAILED;
                }
                String problem = validateRemoteUrl(url);
                if (problem != null) {
                    out.line("fatal: " + problem);
                    return EXIT_FAILED;
                }
                for (GitEngine.Remote r : engine.remotes(workspace)) {
                    if (r.name.equalsIgnoreCase(name)) {
                        out.line("fatal: remote " + name + " already exists.");
                        return EXIT_FAILED;
                    }
                }
                engine.addRemote(workspace, name, url);
                out.line("Added remote " + name + ".");
                return EXIT_OK;
            }
            case "set-url" -> {
                if (args.size() != 3) return usage(out, "git remote set-url <name> <url>");
                String name = args.get(1);
                String url = args.get(2);
                String problem = validateRemoteUrl(url);
                if (problem != null) {
                    out.line("fatal: " + problem);
                    return EXIT_FAILED;
                }
                if (!hasRemote(workspace, name)) {
                    out.line("fatal: No such remote '" + name + "'.");
                    return EXIT_FAILED;
                }
                engine.setRemoteUrl(workspace, name, url);
                out.line("Updated remote " + name + ".");
                return EXIT_OK;
            }
            case "remove", "rm" -> {
                if (args.size() != 2) return usage(out, "git remote remove <name>");
                String name = args.get(1);
                if (!hasRemote(workspace, name)) {
                    out.line("fatal: No such remote '" + name + "'.");
                    return EXIT_FAILED;
                }
                if (!confirmed) {
                    out.line("Remote '" + name + "' will be removed from this repository's configuration.");
                    return EXIT_NEEDS_CONFIRMATION;
                }
                engine.removeRemote(workspace, name);
                out.line("Removed remote " + name + ".");
                return EXIT_OK;
            }
            default -> {
                return usage(out, "git remote [-v] | add <name> <url> | set-url <name> <url> | remove <name>");
            }
        }
    }

    private boolean hasRemote(File workspace, String name) throws Exception {
        for (GitEngine.Remote r : engine.remotes(workspace)) if (r.name.equals(name)) return true;
        return false;
    }

    private GitEngine.Progress progress(Output out, Cancellation cancel) {
        return new GitEngine.Progress() {
            private String last = "";

            @Override
            public void line(String text) {
                if (text == null || text.isEmpty() || text.equals(last)) return;
                last = text;
                out.line(text);
            }

            @Override
            public boolean isCancelled() {
                return cancel != null && cancel.isCancelled();
            }
        };
    }

    private int pull(File workspace, List<String> args, Output out, Cancellation cancel) throws Exception {
        if (!args.isEmpty()) return usage(out, "git pull");
        if (engine.remotes(workspace).isEmpty()) {
            out.line("fatal: no remote repository configured. Add one with: git remote add <name> <url>");
            return EXIT_FAILED;
        }
        out.line("Pulling...");
        GitEngine.Credentials credentials = new GitEngine.Credentials(settings.username(), settings.token());
        String result = engine.pull(workspace, credentials, progress(out, cancel));
        if (cancel != null && cancel.isCancelled()) throw new CancelledException();
        out.line(result);
        return result.toLowerCase(Locale.ROOT).contains("conflict") ? EXIT_FAILED : EXIT_OK;
    }

    private int push(File workspace, List<String> args, boolean confirmed, Output out, Cancellation cancel) throws Exception {
        if (!args.isEmpty()) return usage(out, "git push");
        List<GitEngine.Remote> remotes = engine.remotes(workspace);
        GitEngine.Remote origin = null;
        for (GitEngine.Remote r : remotes) if (r.name.equals("origin")) origin = r;
        if (origin == null) {
            out.line("fatal: no 'origin' remote configured. Add one with: git remote add origin <url>");
            return EXIT_FAILED;
        }
        if (settings.token().isEmpty()) {
            out.line("fatal: no access token is set. Enter your username and personal access token in Git Client > Settings.");
            return EXIT_FAILED;
        }
        if (settings.confirmPush() && !confirmed) {
            String branch = engine.currentBranch(workspace);
            out.line("Push branch '" + branch + "' to origin" + (origin.urls.isEmpty() ? "" : " (" + origin.urls.get(0) + ")") + "?");
            return EXIT_NEEDS_CONFIRMATION;
        }
        out.line("Pushing...");
        GitEngine.Credentials credentials = new GitEngine.Credentials(settings.username(), settings.token());
        String result = engine.push(workspace, credentials, progress(out, cancel));
        if (cancel != null && cancel.isCancelled()) throw new CancelledException();
        out.line(result);
        return EXIT_OK;
    }

    private int sync(File workspace, List<String> args, Output out) throws Exception {
        if (!args.isEmpty()) return usage(out, "git sync");
        if (!engine.isRepository(workspace)) return needRepository(workspace, out) ? EXIT_OK : EXIT_NOT_REPOSITORY;
        if (sourceSync == null) {
            out.line("The source export is not available right now.");
            return EXIT_FAILED;
        }
        out.line("Regenerating the exported Android Studio source into the Git workspace...");
        sourceSync.run();
        out.line("Source regenerated. Run 'git status' to review the changes.");
        return EXIT_OK;
    }
}
