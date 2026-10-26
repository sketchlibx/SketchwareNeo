package mod.sketchlibx.project.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.EmptyProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class JGitEngine implements GitEngine {

    @Override
    public String describe() {
        Package pkg = Git.class.getPackage();
        String version = pkg == null ? null : pkg.getImplementationVersion();
        return version == null || version.isEmpty() ? "JGit" : "JGit " + version;
    }

    @Override
    public boolean isRepository(File workspace) {
        return new File(workspace, ".git").isDirectory();
    }

    @Override
    public void initialize(File workspace) throws Exception {
        GitWorkspace.initialize(workspace);
    }

    @Override
    public Status status(File workspace) throws Exception {
        try (Git git = Git.open(workspace)) {
            org.eclipse.jgit.api.Status jgitStatus = git.status().call();
            Repository repository = git.getRepository();
            Status result = new Status();
            String branch = repository.getBranch();
            result.branch = branch == null ? "HEAD" : branch;
            result.hasCommits = repository.resolve(Constants.HEAD) != null;
            copySorted(jgitStatus.getAdded(), result.added);
            copySorted(jgitStatus.getChanged(), result.changed);
            copySorted(jgitStatus.getRemoved(), result.removed);
            copySorted(jgitStatus.getModified(), result.modified);
            copySorted(jgitStatus.getMissing(), result.missing);
            copySorted(jgitStatus.getUntracked(), result.untracked);
            copySorted(jgitStatus.getConflicting(), result.conflicting);
            try {
                BranchTrackingStatus tracking = BranchTrackingStatus.of(repository, result.branch);
                if (tracking != null) {
                    result.ahead = tracking.getAheadCount();
                    result.behind = tracking.getBehindCount();
                }
            } catch (Exception ignored) {
            }
            return result;
        }
    }

    private static void copySorted(Collection<String> source, List<String> target) {
        target.addAll(source);
        Collections.sort(target);
    }

    @Override
    public void add(File workspace, List<String> patterns, boolean all) throws Exception {
        try (Git git = Git.open(workspace)) {
            if (all) {
                git.add().addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                return;
            }
            for (String pattern : patterns) {
                git.add().addFilepattern(pattern).call();
                git.add().setUpdate(true).addFilepattern(pattern).call();
            }
        }
    }

    @Override
    public String commit(File workspace, String message, String authorName, String authorEmail) throws Exception {
        try (Git git = Git.open(workspace)) {
            RevCommit commit = git.commit()
                    .setMessage(message)
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .call();
            String branch = git.getRepository().getBranch();
            String subject = commit.getShortMessage();
            return "[" + (branch == null ? "HEAD" : branch) + " " + commit.getId().abbreviate(7).name() + "] " + subject;
        }
    }

    @Override
    public List<LogEntry> log(File workspace, int max) throws Exception {
        List<LogEntry> entries = new ArrayList<>();
        try (Git git = Git.open(workspace)) {
            if (git.getRepository().resolve(Constants.HEAD) == null) return entries;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
            for (RevCommit commit : git.log().setMaxCount(max).call()) {
                PersonIdent author = commit.getAuthorIdent();
                entries.add(new LogEntry(commit.getId().abbreviate(7).name(), commit.getShortMessage(),
                        author.getName(), format.format(author.getWhen())));
            }
        }
        return entries;
    }

    @Override
    public List<Branch> branches(File workspace, boolean includeRemote) throws Exception {
        List<Branch> branches = new ArrayList<>();
        try (Git git = Git.open(workspace)) {
            Repository repository = git.getRepository();
            String fullBranch = repository.getFullBranch();
            for (Ref ref : git.branchList().call()) {
                branches.add(new Branch(Repository.shortenRefName(ref.getName()), false, ref.getName().equals(fullBranch)));
            }
            if (includeRemote) {
                for (Ref ref : git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call()) {
                    String name = Repository.shortenRefName(ref.getName());
                    if (name.endsWith("/HEAD")) continue;
                    branches.add(new Branch(name, true, false));
                }
            }
        }
        return branches;
    }

    @Override
    public void createBranch(File workspace, String name) throws Exception {
        try (Git git = Git.open(workspace)) {
            if (git.getRepository().resolve(Constants.HEAD) == null) {
                throw new IllegalStateException("No commits yet. Make your first commit before creating a branch.");
            }
            git.branchCreate().setName(name).call();
        }
    }

    @Override
    public void deleteBranch(File workspace, String name, boolean force) throws Exception {
        try (Git git = Git.open(workspace)) {
            git.branchDelete().setBranchNames(name).setForce(force).call();
        }
    }

    @Override
    public List<Remote> remotes(File workspace) throws Exception {
        List<Remote> remotes = new ArrayList<>();
        try (Git git = Git.open(workspace)) {
            for (RemoteConfig config : git.remoteList().call()) {
                List<String> urls = new ArrayList<>();
                for (URIish uri : config.getURIs()) urls.add(uri.toString());
                remotes.add(new Remote(config.getName(), urls));
            }
        }
        return remotes;
    }

    @Override
    public void addRemote(File workspace, String name, String url) throws Exception {
        try (Git git = Git.open(workspace)) {
            git.remoteAdd().setName(name).setUri(new URIish(url)).call();
        }
    }

    @Override
    public void removeRemote(File workspace, String name) throws Exception {
        try (Git git = Git.open(workspace)) {
            git.remoteRemove().setRemoteName(name).call();
        }
    }

    @Override
    public void setRemoteUrl(File workspace, String name, String url) throws Exception {
        try (Git git = Git.open(workspace)) {
            git.remoteSetUrl().setRemoteName(name).setRemoteUri(new URIish(url)).call();
        }
    }

    @Override
    public String pull(File workspace, Credentials credentials, Progress progress) throws Exception {
        try (Git git = Git.open(workspace)) {
            PullCommand command = git.pull()
                    .setCredentialsProvider(provider(credentials))
                    .setProgressMonitor(monitor(progress));
            PullResult result = command.call();
            return GitRemoteOperations.describePullResult(result);
        }
    }

    @Override
    public String push(File workspace, Credentials credentials, Progress progress) throws Exception {
        try (Git git = Git.open(workspace)) {
            Repository repository = git.getRepository();
            String branch = repository.getBranch();
            ObjectId localHead = repository.resolve(Constants.HEAD);
            GitRemoteOperations.verifyAndPush(git, provider(credentials), "origin", branch, localHead, monitor(progress));
            return "Push successful.";
        }
    }

    @Override
    public String currentBranch(File workspace) throws Exception {
        try (Git git = Git.open(workspace)) {
            String branch = git.getRepository().getBranch();
            return branch == null ? "HEAD" : branch;
        }
    }

    private static CredentialsProvider provider(Credentials credentials) {
        return new UsernamePasswordCredentialsProvider(credentials.username, credentials.token);
    }

    private static ProgressMonitor monitor(Progress progress) {
        return new EmptyProgressMonitor() {
            @Override
            public void beginTask(String title, int totalWork) {
                if (title != null && !title.isEmpty()) progress.line(title + "...");
            }

            @Override
            public boolean isCancelled() {
                return progress.isCancelled();
            }
        };
    }
}
