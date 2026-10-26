package mod.sketchlibx.project.git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public interface GitEngine {

    interface Progress {
        void line(String text);

        boolean isCancelled();
    }

    final class Credentials {
        public final String username;
        public final String token;

        public Credentials(String username, String token) {
            this.username = username == null ? "" : username;
            this.token = token == null ? "" : token;
        }
    }

    final class Status {
        public String branch = "";
        public boolean hasCommits;
        public int ahead;
        public int behind;
        public final List<String> added = new ArrayList<>();
        public final List<String> changed = new ArrayList<>();
        public final List<String> removed = new ArrayList<>();
        public final List<String> modified = new ArrayList<>();
        public final List<String> missing = new ArrayList<>();
        public final List<String> untracked = new ArrayList<>();
        public final List<String> conflicting = new ArrayList<>();

        public boolean hasStagedChanges() {
            return !added.isEmpty() || !changed.isEmpty() || !removed.isEmpty();
        }

        public boolean isClean() {
            return !hasStagedChanges() && modified.isEmpty() && missing.isEmpty() && untracked.isEmpty() && conflicting.isEmpty();
        }
    }

    final class LogEntry {
        public final String shortId;
        public final String subject;
        public final String author;
        public final String date;

        public LogEntry(String shortId, String subject, String author, String date) {
            this.shortId = shortId;
            this.subject = subject;
            this.author = author;
            this.date = date;
        }
    }

    final class Branch {
        public final String name;
        public final boolean remote;
        public final boolean current;

        public Branch(String name, boolean remote, boolean current) {
            this.name = name;
            this.remote = remote;
            this.current = current;
        }
    }

    final class Remote {
        public final String name;
        public final List<String> urls;

        public Remote(String name, List<String> urls) {
            this.name = name;
            this.urls = urls;
        }
    }

    String describe();

    boolean isRepository(File workspace);

    void initialize(File workspace) throws Exception;

    Status status(File workspace) throws Exception;

    void add(File workspace, List<String> patterns, boolean all) throws Exception;

    String commit(File workspace, String message, String authorName, String authorEmail) throws Exception;

    List<LogEntry> log(File workspace, int max) throws Exception;

    List<Branch> branches(File workspace, boolean includeRemote) throws Exception;

    void createBranch(File workspace, String name) throws Exception;

    void deleteBranch(File workspace, String name, boolean force) throws Exception;

    List<Remote> remotes(File workspace) throws Exception;

    void addRemote(File workspace, String name, String url) throws Exception;

    void removeRemote(File workspace, String name) throws Exception;

    void setRemoteUrl(File workspace, String name, String url) throws Exception;

    String pull(File workspace, Credentials credentials, Progress progress) throws Exception;

    String push(File workspace, Credentials credentials, Progress progress) throws Exception;

    String currentBranch(File workspace) throws Exception;
}
