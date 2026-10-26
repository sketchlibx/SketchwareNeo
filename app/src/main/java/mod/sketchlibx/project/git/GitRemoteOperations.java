package mod.sketchlibx.project.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteRefUpdate;

import java.util.Collection;

public final class GitRemoteOperations {

    private GitRemoteOperations() {
    }

    public static void verifyAndPush(Git git, CredentialsProvider credentials, String remoteName, String branch, ObjectId localHead, ProgressMonitor monitor) throws Exception {
        if (localHead == null) throw new IllegalStateException("Nothing to commit. Working tree is clean.");
        PushCommand pushCommand = git.push()
                .setRemote(remoteName)
                .setCredentialsProvider(credentials);
        if (monitor != null) pushCommand.setProgressMonitor(monitor);
        Iterable<PushResult> results = pushCommand.call();

        boolean anySuccess = false;
        StringBuilder failures = new StringBuilder();
        for (PushResult pr : results) {
            for (RemoteRefUpdate rru : pr.getRemoteUpdates()) {
                RemoteRefUpdate.Status st = rru.getStatus();
                if (st == RemoteRefUpdate.Status.OK || st == RemoteRefUpdate.Status.UP_TO_DATE) {
                    anySuccess = true;
                } else {
                    failures.append(describePushFailure(st, rru)).append(' ');
                }
            }
        }
        if (!anySuccess) throw new IllegalStateException(failures.length() > 0 ? failures.toString().trim() : "Push rejected by remote.");

        Collection<Ref> remoteRefs = git.lsRemote().setRemote(remoteName).setCredentialsProvider(credentials).call();
        String fullRef = "refs/heads/" + branch;
        boolean verified = false;
        for (Ref r : remoteRefs) {
            ObjectId remoteId = r.getObjectId();
            if (remoteId != null && fullRef.equals(r.getName()) && remoteId.equals(localHead)) { verified = true; break; }
        }
        if (!verified) throw new IllegalStateException("Push reported success but the remote branch does not match local HEAD yet. Please verify manually.");
    }

    public static String describePushFailure(RemoteRefUpdate.Status status, RemoteRefUpdate rru) {
        switch (status) {
            case REJECTED_NONFASTFORWARD: return "Remote has new commits. Pull or fetch before pushing.";
            case REJECTED_NODELETE: return "The server does not allow deleting that ref.";
            case REJECTED_REMOTE_CHANGED: return "Remote ref changed unexpectedly. Try again.";
            case REJECTED_OTHER_REASON: return "Push rejected by remote" + (rru.getMessage() != null ? ": " + rru.getMessage() : ".");
            case NON_EXISTING: return "Local branch does not exist.";
            case NOT_ATTEMPTED: return "Push was not attempted.";
            case AWAITING_REPORT: return "No confirmation received from the server.";
            default: return "Push failed (" + status + ").";
        }
    }

    public static String describePullResult(PullResult result) {
        MergeResult mr = result.getMergeResult();
        if (mr != null) {
            MergeResult.MergeStatus status = mr.getMergeStatus();
            if (status == MergeResult.MergeStatus.CONFLICTING) return "Merge conflict detected. Resolve conflicts in the Changes tab.";
            if (status == MergeResult.MergeStatus.ALREADY_UP_TO_DATE) return "Already up to date.";
            if (status == MergeResult.MergeStatus.FAST_FORWARD) return "Fast-forwarded to the latest changes.";
            if (status == MergeResult.MergeStatus.MERGED) return "Merged remote changes.";
            return "Pull complete (" + status + ").";
        }
        return "Pull complete.";
    }
}
