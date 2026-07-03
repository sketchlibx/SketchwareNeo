package mod.sketchlibx.project.git;

import org.eclipse.jgit.api.errors.CanceledException;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.api.errors.NotMergedException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.api.errors.WrongRepositoryStateException;

/**
 * Translates JGit / IO exceptions into clear, user-facing messages.
 * Root cause fix for bug #14 ("Operation Failed" / "Unknown Error").
 *
 * Every public Git operation in the Git Client MUST route its caught
 * exception through {@link #map(Exception)} before showing it to the user.
 */
public final class GitErrorMapper {

    private GitErrorMapper() {}

    public static String map(Exception e) {
        if (e == null) return "Unknown error.";
        String raw = e.getMessage();

        if (e instanceof InvalidRemoteException) {
            return "No remote repository configured. Add a remote in the Remotes tab.";
        }
        if (e instanceof TransportException) {
            String low = raw == null ? "" : raw.toLowerCase();
            if (low.contains("not authorized") || low.contains("auth fail") || low.contains("authentication")
                    || low.contains("401") || low.contains("403")) {
                return "Authentication failed. Check your username and personal access token in Settings.";
            }
            if (low.contains("timed out") || low.contains("timeout") || low.contains("unable to access")
                    || low.contains("unknown host") || low.contains("connection refused")) {
                return "Network timeout. Check your internet connection.";
            }
            if (low.contains("not found") || low.contains("repository not found")) {
                return "Remote repository not found. Check the remote URL.";
            }
            return raw != null && !raw.isEmpty() ? raw : "Network error while contacting the remote repository.";
        }
        if (e instanceof EmptyCommitException) {
            return "Nothing to commit. Working tree is clean.";
        }
        if (e instanceof NoHeadException) {
            return "No commits yet. Make your first commit before this operation.";
        }
        if (e instanceof RefAlreadyExistsException) {
            return "Branch already exists.";
        }
        if (e instanceof RefNotFoundException) {
            return "Branch not found.";
        }
        if (e instanceof CheckoutConflictException) {
            return "Checkout would overwrite local changes. Commit or discard changes first.";
        }
        if (e instanceof NotMergedException) {
            return "Branch is not fully merged. Force delete to remove it anyway.";
        }
        if (e instanceof WrongRepositoryStateException) {
            return "Repository is in the middle of another operation (merge/rebase). Finish or abort it first.";
        }
        if (e instanceof CanceledException) {
            return "Operation cancelled.";
        }
        if (raw != null && raw.toLowerCase().contains("conflict")) {
            return "Merge conflict detected. Resolve conflicts in the Changes tab.";
        }
        return raw != null && !raw.isEmpty() ? raw : e.getClass().getSimpleName();
    }
}
