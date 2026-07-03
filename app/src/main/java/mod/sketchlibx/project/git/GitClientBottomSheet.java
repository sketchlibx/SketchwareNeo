package mod.sketchlibx.project.git;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.MergeResult;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.lib.BranchTrackingStatus;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.ProjectBuilder;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.xq;
import a.a.a.yq;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Git Client bottom sheet — full audit/refactor.
 *
 * Architectural fixes baked into this version (see inline comments at each
 * relevant method for the bug number they resolve):
 *  1) Commit & Push is now one atomic, verified workflow (commitAndPush()).
 *  2) Status uses the real JGit Status API plus rename detection; clean/
 *     ignored files are never shown.
 *  3) tv_repo_status is rebuilt from a single source of truth (applyStatus)
 *     after every operation that can change it.
 *  4) Push is only ever reported successful after inspecting RemoteRefUpdate
 *     statuses AND independently verifying via git.lsRemote().
 *  5) Remotes are always reloaded from a freshly-loaded StoredConfig.
 *  6) Branches always show local + remote, grouped, with Checkout/Rename/
 *     Delete, and deleting/checking out the active branch is disabled.
 *  7) Fetch/Pull show the exact outcome (ref count, fast-forward, conflict,
 *     already up to date) instead of a generic toast.
 *  8/9) ALL JGit calls funnel through a single-thread executor (no more raw
 *     per-adapter Thread spawning, which caused races and inconsistent
 *     index reads). ViewPager keeps every tab alive via offscreenPageLimit
 *     so adapters are never null and never lose scroll position on swipe.
 *  10) Branch/Remote dialogs validate in real time and disable the action
 *      button until valid.
 *  14) All exceptions are translated by GitErrorMapper before being shown.
 */
public class GitClientBottomSheet extends BottomSheetDialogFragment {

    private String sc_id;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private final String[] tabTitles = {"Changes", "History", "Branches", "Remotes", "Settings"};

    private Git git;
    private File repoDir;
    private View rootView;
    private View changesTabView;
    private View historyTabView;

    private ChangesAdapter changesAdapter;
    private HistoryAdapter historyAdapter;
    private GitBranchAdapter branchAdapter;
    private GitRemoteAdapter remoteAdapter;

    // Cached snapshots: whichever tab is (re)created always has data to show
    // immediately, instead of being empty until the next manual refresh.
    private List<ChangesAdapter.GitFile> lastChangeList = new ArrayList<>();
    private List<RevCommit> lastCommitList = new ArrayList<>();
    private List<Ref> lastBranchList = new ArrayList<>();
    private List<RemoteConfig> lastRemoteList = new ArrayList<>();
    private RepoStatusSummary lastStatusSummary = new RepoStatusSummary();

    private AlertDialog progressDialog;

    // Single thread: every JGit command in this class runs here, in order.
    // Running JGit commands from multiple uncoordinated threads against the
    // same Repository was the root cause behind several reported bugs
    // (random freezes, wrong file lists, index.lock contention).
    private final ExecutorService gitExecutor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public GitClientBottomSheet(String sc_id) {
        this.sc_id = sc_id;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String repoPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/git_workspace";
        repoDir = new File(repoPath);
        try {
            if (new File(repoDir, ".git").exists()) {
                git = Git.open(repoDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bsd = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bsd.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                behavior.setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_git_client, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rootView = view;

        TextView tvRepoName = view.findViewById(R.id.tv_repo_name);
        tvRepoName.setText("Project: " + sc_id);

        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager.setAdapter(new GitPagerAdapter(requireContext()));
        // Bug #8/#9 root cause: with the default offscreenPageLimit (1), tabs
        // 2+ swipes away get destroyed and their RecyclerView/adapter
        // recreated from scratch, which both causes the visible "freeze" on
        // swipe and explains data silently not showing (a refresh that ran
        // before a tab was ever instantiated had nowhere to deliver its
        // result). Keeping all tabs alive for the life of this small, fixed
        // 5-tab sheet eliminates both problems.
        viewPager.setOffscreenPageLimit(Math.max(1, tabTitles.length - 1));
        tabLayout.setupWithViewPager(viewPager);

        view.findViewById(R.id.btn_action_refresh).setOnClickListener(v -> refreshSourceThenAll());

        if (git == null) {
            SketchwareUtil.toastError("Git repository not found for this project.");
        } else {
            refreshAll();
        }
    }

    private CredentialsProvider getCredentials() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);
        String token = prefs.getString("token", "");
        String username = prefs.getString("name", "");
        return new UsernamePasswordCredentialsProvider(username, token);
    }

    private void showProgressDialog(String message) {
        mainHandler.post(() -> {
            if (!isAdded()) return;
            if (progressDialog == null) {
                LinearLayout container = new LinearLayout(requireContext());
                container.setGravity(Gravity.CENTER_VERTICAL);
                container.setOrientation(LinearLayout.HORIZONTAL);
                int padding = SketchwareUtil.dpToPx(24);
                container.setPadding(padding, padding, padding, padding);

                ProgressBar progressBar = new ProgressBar(requireContext());
                container.addView(progressBar);

                TextView tvMessage = new TextView(requireContext());
                tvMessage.setId(android.R.id.message);
                tvMessage.setText(message);
                tvMessage.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
                tvMessage.setTextSize(16f);
                tvMessage.setPadding(SketchwareUtil.dpToPx(16), 0, 0, 0);
                container.addView(tvMessage);

                progressDialog = new MaterialAlertDialogBuilder(requireContext())
                        .setView(container)
                        .setCancelable(false)
                        .create();
            } else {
                TextView msgView = progressDialog.findViewById(android.R.id.message);
                if (msgView != null) msgView.setText(message);
            }
            if (!progressDialog.isShowing()) progressDialog.show();
        });
    }

    private void hideProgressDialog() {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }

    // ---------------------------------------------------------------------
    // Generic task runner. EVERY Git operation in this class (besides the
    // two bespoke multi-stage flows, commitAndPush/refreshSourceThenAll,
    // which still execute on the same gitExecutor) goes through here so
    // there is exactly one place that serializes JGit access and maps
    // exceptions to friendly text.
    // ---------------------------------------------------------------------
    private interface GitTask<T> { T run() throws Exception; }
    private interface GitTaskCallback<T> { void onSuccess(T result); void onError(String message); }

    private <T> void runGitTask(@Nullable String progressMessage, boolean showProgress, GitTask<T> task, GitTaskCallback<T> callback) {
        if (git == null) {
            SketchwareUtil.toastError("Git repository not initialized for this project.");
            return;
        }
        if (showProgress && progressMessage != null) showProgressDialog(progressMessage);
        gitExecutor.execute(() -> {
            try {
                T result = task.run();
                mainHandler.post(() -> {
                    if (showProgress) hideProgressDialog();
                    if (isAdded()) callback.onSuccess(result);
                });
            } catch (Exception e) {
                e.printStackTrace();
                String friendly = GitErrorMapper.map(e);
                mainHandler.post(() -> {
                    if (showProgress) hideProgressDialog();
                    if (isAdded()) callback.onError(friendly);
                });
            }
        });
    }

    // ---------------------------------------------------------------------
    // Status (bug #2 + #3): real JGit Status API, plus a from-scratch rename
    // scan since plain Status never detects renames. Two scans are run:
    // HEAD-vs-Index for staged renames, Index-vs-WorkingTree for unstaged
    // renames. Matched pairs are removed from Added/Removed/Untracked/
    // Missing so a rename never shows up twice.
    // ---------------------------------------------------------------------
    private static final class RepoStatusSummary {
        int stagedCount, unstagedCount, untrackedCount, conflictCount, ahead, behind;
        String currentBranch = "";
    }

    private static final class RawStatusData {
        final Set<String> modified = new HashSet<>();
        final Set<String> changed = new HashSet<>();
        final Set<String> added = new HashSet<>();
        final Set<String> removed = new HashSet<>();
        final Set<String> missing = new HashSet<>();
        final Set<String> untracked = new HashSet<>();
        final Set<String> conflicting = new HashSet<>();
        final Map<String, String> stagedRenames = new LinkedHashMap<>();   // newPath -> oldPath
        final Map<String, String> unstagedRenames = new LinkedHashMap<>(); // newPath -> oldPath
        String currentBranch = "";
        int ahead, behind;
    }

    private void collectRenames(Repository repo, boolean staged, Map<String, String> outNewToOld) {
        try (ObjectReader reader = repo.newObjectReader()) {
            AbstractTreeIterator oldIter;
            AbstractTreeIterator newIter;
            if (staged) {
                ObjectId headTree = repo.resolve("HEAD^{tree}");
                if (headTree == null) return; // no commits yet, nothing to diff against
                CanonicalTreeParser headIter = new CanonicalTreeParser();
                headIter.reset(reader, headTree);
                oldIter = headIter;
                newIter = new DirCacheIterator(repo.readDirCache());
            } else {
                oldIter = new DirCacheIterator(repo.readDirCache());
                newIter = new FileTreeIterator(repo);
            }
            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repo);
                df.setDiffComparator(RawTextComparator.DEFAULT);
                df.setDetectRenames(true);
                List<DiffEntry> entries = df.scan(oldIter, newIter);
                for (DiffEntry entry : entries) {
                    if (entry.getChangeType() == DiffEntry.ChangeType.RENAME) {
                        outNewToOld.put(entry.getNewPath(), entry.getOldPath());
                    }
                }
            }
        } catch (IOException ignored) {
            // Rename detection is a best-effort enhancement; on failure we
            // simply fall back to showing the plain Added/Deleted pair.
        }
    }

    private RawStatusData computeRawStatus() throws Exception {
        Status status = git.status().call();
        Repository repo = git.getRepository();

        RawStatusData data = new RawStatusData();
        data.modified.addAll(status.getModified());
        data.changed.addAll(status.getChanged());
        data.added.addAll(status.getAdded());
        data.removed.addAll(status.getRemoved());
        data.missing.addAll(status.getMissing());
        data.untracked.addAll(status.getUntracked());
        data.conflicting.addAll(status.getConflicting());

        collectRenames(repo, true, data.stagedRenames);
        collectRenames(repo, false, data.unstagedRenames);

        for (Map.Entry<String, String> e : data.stagedRenames.entrySet()) {
            data.added.remove(e.getKey());
            data.removed.remove(e.getValue());
        }
        for (Map.Entry<String, String> e : data.unstagedRenames.entrySet()) {
            data.untracked.remove(e.getKey());
            data.missing.remove(e.getValue());
        }

        data.currentBranch = repo.getBranch();
        try {
            BranchTrackingStatus bts = BranchTrackingStatus.of(repo, data.currentBranch);
            if (bts != null) {
                data.ahead = bts.getAheadCount();
                data.behind = bts.getBehindCount();
            }
        } catch (Exception ignored) {}

        return data;
    }

    private void applyStatus(RawStatusData data) {
        if (!isAdded()) return;
        Context ctx = requireContext();
        int colorModified = ThemeUtils.getColor(ctx, R.attr.colorAccent);
        int colorAdded = ThemeUtils.getColor(ctx, R.attr.colorPrimary);
        int colorDeleted = ThemeUtils.getColor(ctx, R.attr.colorError);
        int colorUntracked = ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant);
        int colorConflict = ThemeUtils.getColor(ctx, R.attr.colorError);
        int colorRenamed = ThemeUtils.getColor(ctx, R.attr.colorPrimary);

        List<ChangesAdapter.GitFile> files = new ArrayList<>();
        for (String p : data.conflicting) files.add(new ChangesAdapter.GitFile(p, "Conflict", true, colorConflict));
        for (Map.Entry<String, String> e : data.stagedRenames.entrySet())
            files.add(ChangesAdapter.GitFile.renamed(e.getValue(), e.getKey(), true, colorRenamed));
        for (Map.Entry<String, String> e : data.unstagedRenames.entrySet())
            files.add(ChangesAdapter.GitFile.renamed(e.getValue(), e.getKey(), false, colorRenamed));
        for (String p : data.modified) files.add(new ChangesAdapter.GitFile(p, "Modified", false, colorModified));
        for (String p : data.changed) files.add(new ChangesAdapter.GitFile(p, "Modified", true, colorModified));
        for (String p : data.added) files.add(new ChangesAdapter.GitFile(p, "Added", true, colorAdded));
        for (String p : data.untracked) files.add(new ChangesAdapter.GitFile(p, "Untracked", false, colorUntracked));
        for (String p : data.removed) files.add(new ChangesAdapter.GitFile(p, "Deleted", true, colorDeleted));
        for (String p : data.missing) files.add(new ChangesAdapter.GitFile(p, "Deleted", false, colorDeleted));

        lastChangeList = files;
        if (changesAdapter != null) changesAdapter.updateData(files);
        if (changesTabView != null) {
            boolean empty = files.isEmpty();
            View rv = changesTabView.findViewById(R.id.rv_changes);
            View emptyView = changesTabView.findViewById(R.id.layout_empty_changes);
            if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }

        RepoStatusSummary summary = new RepoStatusSummary();
        summary.stagedCount = data.changed.size() + data.added.size() + data.removed.size() + data.stagedRenames.size();
        summary.unstagedCount = data.modified.size() + data.missing.size() + data.unstagedRenames.size();
        summary.untrackedCount = data.untracked.size();
        summary.conflictCount = data.conflicting.size();
        summary.ahead = data.ahead;
        summary.behind = data.behind;
        summary.currentBranch = data.currentBranch;
        lastStatusSummary = summary;

        if (rootView != null) {
            TextView tvStatus = rootView.findViewById(R.id.tv_repo_status);
            if (tvStatus != null) tvStatus.setText(buildStatusText(summary));
            Chip chipBranch = rootView.findViewById(R.id.chip_current_branch);
            if (chipBranch != null && data.currentBranch != null) chipBranch.setText(data.currentBranch);
        }
    }

    private String buildStatusText(RepoStatusSummary s) {
        StringBuilder sb = new StringBuilder();
        if (s.conflictCount > 0) {
            sb.append("\u26A0 ").append(s.conflictCount).append(s.conflictCount == 1 ? " Conflict" : " Conflicts");
            appendAheadBehind(sb, s);
            return sb.toString();
        }
        List<String> parts = new ArrayList<>();
        if (s.unstagedCount > 0) parts.add(s.unstagedCount + " Modified");
        if (s.stagedCount > 0) parts.add(s.stagedCount + " Staged");
        if (s.untrackedCount > 0) parts.add(s.untrackedCount + " Untracked");
        sb.append(parts.isEmpty() ? "Working tree clean" : String.join(" \u2022 ", parts));
        appendAheadBehind(sb, s);
        return sb.toString();
    }

    private void appendAheadBehind(StringBuilder sb, RepoStatusSummary s) {
        if (s.ahead > 0 || s.behind > 0) {
            sb.append("  ");
            if (s.ahead > 0) sb.append("\u2191").append(s.ahead).append(' ');
            if (s.behind > 0) sb.append("\u2193").append(s.behind);
        }
    }

    private void refreshGitStatus() {
        runGitTask(null, false, this::computeRawStatus, new GitTaskCallback<RawStatusData>() {
            @Override public void onSuccess(RawStatusData data) { applyStatus(data); }
            @Override public void onError(String message) { SketchwareUtil.toastError("Failed to read status: " + message); }
        });
    }

    private List<RevCommit> computeCommitLog() throws Exception {
        List<RevCommit> list = new ArrayList<>();
        try {
            if (git.getRepository().resolve(Constants.HEAD) != null) {
                for (RevCommit c : git.log().call()) list.add(c);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void applyHistory(List<RevCommit> commits) {
        lastCommitList = commits;
        if (historyAdapter != null) historyAdapter.updateData(commits);
        if (historyTabView != null) {
            boolean empty = commits.isEmpty();
            View rv = historyTabView.findViewById(R.id.rv_history);
            View emptyView = historyTabView.findViewById(R.id.layout_empty_history);
            if (rv != null) rv.setVisibility(empty ? View.GONE : View.VISIBLE);
            if (emptyView != null) emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    private void refreshHistory() {
        runGitTask(null, false, this::computeCommitLog, new GitTaskCallback<List<RevCommit>>() {
            @Override public void onSuccess(List<RevCommit> commits) { applyHistory(commits); }
            @Override public void onError(String message) { SketchwareUtil.toastError("Failed to load history: " + message); }
        });
    }

    private List<Ref> computeBranchList() throws Exception {
        return git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
    }

    private void applyBranches(List<Ref> refs) {
        lastBranchList = refs;
        String current = lastStatusSummary.currentBranch;
        try { current = git.getRepository().getBranch(); } catch (Exception ignored) {}
        if (branchAdapter != null) branchAdapter.updateData(refs, current);
        if (rootView != null) {
            Chip chipBranch = rootView.findViewById(R.id.chip_current_branch);
            if (chipBranch != null && current != null) chipBranch.setText(current);
        }
    }

    private void refreshBranches() {
        runGitTask(null, false, this::computeBranchList, new GitTaskCallback<List<Ref>>() {
            @Override public void onSuccess(List<Ref> refs) { applyBranches(refs); }
            @Override public void onError(String message) { SketchwareUtil.toastError("Failed to load branches: " + message); }
        });
    }

    // Bug #5: always force a reload of the StoredConfig from disk before
    // reading remotes, so stale in-memory state is never the reason the
    // Remotes tab looks empty.
    private List<RemoteConfig> computeRemoteList() throws Exception {
        StoredConfig config = git.getRepository().getConfig();
        try { config.load(); } catch (Exception ignored) {}
        return RemoteConfig.getAllRemoteConfigs(config);
    }

    private void applyRemotes(List<RemoteConfig> remotes) {
        lastRemoteList = remotes;
        if (remoteAdapter != null) remoteAdapter.updateData(remotes);
    }

    private void refreshRemotes() {
        runGitTask(null, false, this::computeRemoteList, new GitTaskCallback<List<RemoteConfig>>() {
            @Override public void onSuccess(List<RemoteConfig> remotes) { applyRemotes(remotes); }
            @Override public void onError(String message) { SketchwareUtil.toastError("Failed to load remotes: " + message); }
        });
    }

    private void refreshAll() {
        refreshGitStatus();
        refreshHistory();
        refreshBranches();
        refreshRemotes();
    }

    // ---------------------------------------------------------------------
    // Bug #11: the heavy "regenerate Sketchware source" step is now its own
    // method, only ever invoked by the explicit "Refresh & Sync" button.
    // Every other action (stage, commit, push, checkout, ...) calls the
    // cheap refreshGitStatus()/refreshHistory()/refreshBranches()/
    // refreshRemotes() combination it actually needs instead of paying for
    // a full source regeneration every time.
    // ---------------------------------------------------------------------
    private void refreshSourceThenAll() {
        if (git == null) return;
        showProgressDialog("Generating Sketchware Source Code...");
        gitExecutor.execute(() -> {
            try {
                regenerateSketchwareSource();
                mainHandler.post(() -> {
                    hideProgressDialog();
                    refreshAll();
                });
            } catch (Exception e) {
                e.printStackTrace();
                String friendly = GitErrorMapper.map(e);
                mainHandler.post(() -> {
                    hideProgressDialog();
                    SketchwareUtil.toastError("Failed to generate source: " + friendly);
                });
            }
        });
    }

    private void regenerateSketchwareSource() throws Exception {
        jC.a(sc_id).j();
        jC.b(sc_id).m();
        jC.c(sc_id).l();
        jC.d(sc_id).x();

        hC hCVar = new hC(sc_id);
        kC kCVar = new kC(sc_id);
        eC eCVar = new eC(sc_id);
        iC iCVar = new iC(sc_id);

        hCVar.i(); kCVar.s(); eCVar.g(); eCVar.e(); iCVar.i();

        HashMap<String, Object> projectInfo = lC.b(sc_id);
        yq project_metadata = new yq(requireContext(), wq.d(sc_id), projectInfo);
        project_metadata.a(requireContext(), wq.e(xq.a(sc_id) ? "600" : sc_id));

        ProjectBuilder builder = new ProjectBuilder(null, requireContext(), project_metadata);
        project_metadata.a(iCVar, hCVar, eCVar, yq.ExportType.ANDROID_STUDIO);
        builder.buildBuiltInLibraryInformation();
        project_metadata.b(hCVar, eCVar, iCVar, builder.getBuiltInLibraryManager());
        project_metadata.a();
        project_metadata.f();

        File appDir = new File(repoDir, "app/src/main");
        FileUtil.makeDir(appDir.getAbsolutePath());

        File javaSrc = new File(project_metadata.javaFilesPath);
        if (javaSrc.exists() && javaSrc.isDirectory()) FileUtil.copyDirectory(javaSrc, new File(appDir, "java"));

        File resSrc = new File(project_metadata.resDirectoryPath);
        if (resSrc.exists() && resSrc.isDirectory()) FileUtil.copyDirectory(resSrc, new File(appDir, "res"));

        File assetsSrc = new File(project_metadata.assetsPath);
        if (assetsSrc.exists() && assetsSrc.isDirectory()) FileUtil.copyDirectory(assetsSrc, new File(appDir, "assets"));

        File manifestSrc = new File(project_metadata.projectMyscPath, "AndroidManifest.xml");
        if (manifestSrc.exists()) FileUtil.copyFile(manifestSrc.getAbsolutePath(), new File(appDir, "AndroidManifest.xml").getAbsolutePath());
    }

    // ---------------------------------------------------------------------
    // Bug #1 + #4: Commit & Push as ONE atomic, verified workflow.
    //   Stage All -> Commit (only if there's something to commit) -> verify
    //   a remote exists -> Fetch -> Push -> verify the push via
    //   RemoteRefUpdate status AND an independent git.lsRemote() check ->
    //   refresh UI. The success toast only ever fires after every step
    //   above has actually succeeded; any failure shows the real JGit
    //   reason and stops immediately (no fake success).
    // ---------------------------------------------------------------------
    private void commitAndPush(String rawMessage) {
        String message = rawMessage == null ? "" : rawMessage.trim();
        if (git == null) return;
        showProgressDialog("Checking working tree...");
        gitExecutor.execute(() -> {
            try {
                Status preStatus = git.status().call();
                boolean willCommit = !preStatus.isClean();
                if (willCommit && message.isEmpty()) {
                    throw new IllegalArgumentException("Enter a commit message before committing.");
                }

                if (willCommit) {
                    showProgressDialog("Staging all changes...");
                    git.add().addFilepattern(".").call();
                    git.add().setUpdate(true).addFilepattern(".").call();

                    showProgressDialog("Committing...");
                    SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);
                    String name = prefs.getString("name", "Sketchware User");
                    String email = prefs.getString("email", "user@sketchware.neo");
                    git.commit().setAuthor(name, email).setCommitter(name, email).setMessage(message).call();
                }

                ObjectId localHead = git.getRepository().resolve(Constants.HEAD);
                if (localHead == null) {
                    throw new IllegalStateException("Nothing to commit. Working tree is clean.");
                }

                String remoteName = "origin";
                List<RemoteConfig> remotes = RemoteConfig.getAllRemoteConfigs(git.getRepository().getConfig());
                boolean hasRemote = false;
                for (RemoteConfig rc : remotes) if (rc.getName().equals(remoteName)) { hasRemote = true; break; }
                if (!hasRemote) {
                    throw new IllegalStateException("No remote repository configured. Add a remote in the Remotes tab.");
                }

                showProgressDialog("Fetching from remote...");
                git.fetch().setRemote(remoteName).setCredentialsProvider(getCredentials()).call();

                showProgressDialog("Pushing...");
                String branch = git.getRepository().getBranch();
                verifyAndPush(remoteName, branch, localHead);

                mainHandler.post(() -> {
                    hideProgressDialog();
                    SketchwareUtil.toast("Commit & Push successful");
                    refreshAll();
                });
            } catch (Exception e) {
                e.printStackTrace();
                String friendly = GitErrorMapper.map(e);
                mainHandler.post(() -> {
                    hideProgressDialog();
                    SketchwareUtil.toastError(friendly);
                    refreshGitStatus(); // reflect whatever partial state actually happened
                });
            }
        });
    }

    private void verifyAndPush(String remoteName, String branch, ObjectId localHead) throws Exception {
        if (localHead == null) {
            throw new IllegalStateException("Nothing to commit. Working tree is clean.");
        }
        Iterable<PushResult> results = git.push()
                .setRemote(remoteName)
                .setCredentialsProvider(getCredentials())
                .call();

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
        if (!anySuccess) {
            throw new IllegalStateException(failures.length() > 0 ? failures.toString().trim() : "Push rejected by remote.");
        }

        // Do not trust the push result alone — independently verify against
        // the remote (bug #4 requirement).
        Collection<Ref> remoteRefs = git.lsRemote()
                .setRemote(remoteName)
                .setCredentialsProvider(getCredentials())
                .call();
        String fullRef = "refs/heads/" + branch;
        boolean verified = false;
        for (Ref r : remoteRefs) {
            ObjectId remoteId = r.getObjectId();
            if (remoteId != null && fullRef.equals(r.getName()) && remoteId.equals(localHead)) {
                verified = true;
                break;
            }
        }
        if (!verified) {
            throw new IllegalStateException("Push reported success but the remote branch does not match local HEAD yet. Please verify manually.");
        }
    }

    private String describePushFailure(RemoteRefUpdate.Status status, RemoteRefUpdate rru) {
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

    // Bug #7: fetch/pull/push always show the exact outcome.
    private void doFetch() {
        runGitTask("Fetching...", true,
                () -> git.fetch().setRemote("origin").setTagOpt(TagOpt.FETCH_TAGS).setCredentialsProvider(getCredentials()).call(),
                new GitTaskCallback<FetchResult>() {
                    @Override public void onSuccess(FetchResult result) {
                        int n = result.getTrackingRefUpdates().size();
                        SketchwareUtil.toast(n == 0 ? "Already up to date." : "Fetched \u2014 " + n + " ref(s) updated.");
                        refreshBranches(); refreshRemotes(); refreshGitStatus();
                    }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                });
    }

    private String describePullResult(PullResult result) {
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

    private void doPull() {
        runGitTask("Pulling...", true,
                () -> git.pull().setCredentialsProvider(getCredentials()).call(),
                new GitTaskCallback<PullResult>() {
                    @Override public void onSuccess(PullResult result) {
                        MergeResult mr = result.getMergeResult();
                        boolean conflict = mr != null && mr.getMergeStatus() == MergeResult.MergeStatus.CONFLICTING;
                        String msg = describePullResult(result);
                        if (conflict) SketchwareUtil.toastError(msg); else SketchwareUtil.toast(msg);
                        refreshGitStatus(); refreshHistory(); refreshBranches();
                    }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                });
    }

    private void doPush() {
        runGitTask("Pushing...", true, () -> {
            String branch = git.getRepository().getBranch();
            ObjectId localHead = git.getRepository().resolve(Constants.HEAD);
            verifyAndPush("origin", branch, localHead);
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) { SketchwareUtil.toast("Push successful"); refreshGitStatus(); refreshBranches(); }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        });
    }

    // ---------------------------------------------------------------------
    // Changes tab (feature request #12: Stage All / Unstage All / Discard /
    // View Diff). Every action below routes through runGitTask -> the single
    // gitExecutor, never a per-row Thread.
    // ---------------------------------------------------------------------
    private void setupChangesTab(View view) {
        changesTabView = view;
        RecyclerView rvChanges = view.findViewById(R.id.rv_changes);
        rvChanges.setLayoutManager(new LinearLayoutManager(requireContext()));

        changesAdapter = new ChangesAdapter(new ChangesAdapter.ChangeActionCallback() {
            @Override public void onStageToggle(ChangesAdapter.GitFile file) { toggleStage(file); }
            @Override public void onDiscard(ChangesAdapter.GitFile file) { discardFile(file); }
            @Override public void onViewDiff(ChangesAdapter.GitFile file) { showDiffDialog(file); }
        });
        rvChanges.setAdapter(changesAdapter);
        if (!lastChangeList.isEmpty()) changesAdapter.updateData(lastChangeList);

        TextInputEditText etCommit = view.findViewById(R.id.et_commit_message);

        view.findViewById(R.id.btn_commit).setOnClickListener(v -> {
            String msg = etCommit.getText() != null ? etCommit.getText().toString().trim() : "";
            if (msg.isEmpty()) { SketchwareUtil.toast("Enter a commit message"); return; }
            runGitTask("Committing...", true, () -> {
                Status before = git.status().call();
                boolean hasStaged = !before.getAdded().isEmpty() || !before.getChanged().isEmpty() || !before.getRemoved().isEmpty();
                if (!hasStaged) throw new IllegalStateException("Nothing to commit. Stage some changes first.");
                SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);
                String name = prefs.getString("name", "Sketchware User");
                String email = prefs.getString("email", "user@sketchware.neo");
                git.commit().setAuthor(name, email).setCommitter(name, email).setMessage(msg).call();
                return null;
            }, new GitTaskCallback<Void>() {
                @Override public void onSuccess(Void r) {
                    etCommit.setText("");
                    SketchwareUtil.toast("Commit successful");
                    refreshGitStatus(); refreshHistory();
                }
                @Override public void onError(String message) { SketchwareUtil.toastError(message); }
            });
        });

        // Bug #1: this button previously had NO click listener at all.
        view.findViewById(R.id.btn_commit_push).setOnClickListener(v -> {
            String msg = etCommit.getText() != null ? etCommit.getText().toString() : "";
            commitAndPush(msg);
            etCommit.setText("");
        });

        View stageAll = view.findViewById(R.id.btn_stage_all);
        if (stageAll != null) stageAll.setOnClickListener(v -> runGitTask("Staging all changes...", true, () -> {
            git.add().addFilepattern(".").call();
            git.add().setUpdate(true).addFilepattern(".").call();
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) { refreshGitStatus(); }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        }));

        View unstageAll = view.findViewById(R.id.btn_unstage_all);
        if (unstageAll != null) unstageAll.setOnClickListener(v -> runGitTask("Unstaging all changes...", true, () -> {
            git.reset().call();
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) { refreshGitStatus(); }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        }));
    }

    private void toggleStage(ChangesAdapter.GitFile file) {
        runGitTask(null, false, () -> {
            if ("Renamed".equals(file.statusLabel)) {
                if (file.isStaged) {
                    git.reset().addPath(file.path).call();
                    if (file.oldPath != null) git.reset().addPath(file.oldPath).call();
                } else {
                    git.add().addFilepattern(file.path).call();
                    if (file.oldPath != null) git.rm().addFilepattern(file.oldPath).call();
                }
            } else if (file.isStaged) {
                git.reset().addPath(file.path).call();
            } else if ("Deleted".equals(file.statusLabel)) {
                git.rm().addFilepattern(file.path).call();
            } else {
                git.add().addFilepattern(file.path).call(); // also marks a Conflict entry resolved
            }
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) { refreshGitStatus(); }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        });
    }

    private void discardFile(ChangesAdapter.GitFile file) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Discard changes?")
                .setMessage("This will permanently discard changes to \"" + file.filename + "\". This cannot be undone.")
                .setPositiveButton("Discard", (d, w) -> runGitTask("Discarding...", false, () -> {
                    if (file.isStaged) {
                        try {
                            git.reset().addPath(file.path).call();
                            if (file.oldPath != null) git.reset().addPath(file.oldPath).call();
                        } catch (Exception ignored) {}
                    }
                    String label = file.statusLabel;
                    if ("Untracked".equals(label) || "Added".equals(label)) {
                        File f = new File(repoDir, file.path);
                        if (f.exists() && !f.delete()) throw new IllegalStateException("Could not delete file: " + file.path);
                    } else {
                        git.checkout().addPath(file.path).call();
                    }
                    return null;
                }, new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { refreshGitStatus(); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String computeDiffText(ChangesAdapter.GitFile file) throws Exception {
        if ("Untracked".equals(file.statusLabel)) {
            File f = new File(repoDir, file.path);
            if (!f.exists()) return "File not found.";
            if (f.length() > 200_000) return "File too large to preview (" + (f.length() / 1024) + " KB).";
            byte[] bytes = Files.readAllBytes(f.toPath());
            return "New file (untracked):\n\n" + new String(bytes, StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DiffFormatter df = new DiffFormatter(out)) {
            df.setRepository(git.getRepository());
            df.setContext(3);
            List<DiffEntry> entries = git.diff()
                    .setCached(file.isStaged)
                    .setPathFilter(PathFilter.create(file.path))
                    .call();
            if (entries.isEmpty()) return "No textual differences found.";
            for (DiffEntry entry : entries) df.format(entry);
        }
        String result = out.toString("UTF-8");
        return result.isEmpty() ? "No textual differences (binary file?)." : result;
    }

    private void showDiffDialog(ChangesAdapter.GitFile file) {
        runGitTask("Loading diff...", true, () -> computeDiffText(file), new GitTaskCallback<String>() {
            @Override public void onSuccess(String diffText) {
                TextView tv = new TextView(requireContext());
                tv.setText(diffText);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                tv.setTextSize(12f);
                tv.setTextIsSelectable(true);
                int pad = SketchwareUtil.dpToPx(16);
                tv.setPadding(pad, pad, pad, pad);
                ScrollView scroll = new ScrollView(requireContext());
                scroll.addView(tv);
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(file.filename)
                        .setView(scroll)
                        .setPositiveButton("Close", null)
                        .show();
            }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        });
    }

    // ---------------------------------------------------------------------
    // History tab (feature request #12: Commit Details / Copy Hash / Revert)
    // ---------------------------------------------------------------------
    private void setupHistoryTab(View view) {
        historyTabView = view;
        RecyclerView rvHistory = view.findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyAdapter = new HistoryAdapter(this::showCommitDetailsDialog);
        rvHistory.setAdapter(historyAdapter);
        if (!lastCommitList.isEmpty()) applyHistory(lastCommitList);
    }

    private void showCommitDetailsDialog(RevCommit commit) {
        String hash = commit.getName();
        String shortHash = hash.substring(0, 7);
        String author = commit.getAuthorIdent().getName() + " <" + commit.getAuthorIdent().getEmailAddress() + ">";
        String date = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(new Date(commit.getCommitTime() * 1000L));
        String details = "Hash: " + shortHash + "\nAuthor: " + author + "\nDate: " + date + "\n\n" + commit.getFullMessage();

        TextView tv = new TextView(requireContext());
        tv.setText(details);
        tv.setTextIsSelectable(true);
        int pad = SketchwareUtil.dpToPx(20);
        tv.setPadding(pad, pad, pad, pad);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Commit Details")
                .setView(tv)
                .setPositiveButton("Copy Hash", (d, w) -> {
                    ClipboardManager cm = (ClipboardManager) requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("Commit Hash", hash));
                        SketchwareUtil.toast("Hash copied");
                    }
                })
                .setNegativeButton("Close", null)
                .setNeutralButton("Revert", (d, w) -> confirmRevert(commit))
                .show();
    }

    private void confirmRevert(RevCommit commit) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Revert commit?")
                .setMessage("This creates a new commit that undoes \"" + commit.getShortMessage() + "\".")
                .setPositiveButton("Revert", (d, w) -> runGitTask("Reverting commit...", true, () -> {
                    RevCommit result = git.revert().include(commit).call();
                    if (result == null) {
                        throw new IllegalStateException("Merge conflict detected. Resolve conflicts in the Changes tab, then commit to finish the revert.");
                    }
                    return null;
                }, new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { SketchwareUtil.toast("Commit reverted"); refreshGitStatus(); refreshHistory(); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------------------------------------------------------------------
    // Branches tab (bug #6 + feature request #12: Checkout / Rename /
    // Delete, grouped Local/Remote, active branch protected from delete).
    // ---------------------------------------------------------------------
    private void setupBranchesTab(View view) {
        RecyclerView rvBranches = view.findViewById(R.id.rv_branches);
        rvBranches.setLayoutManager(new LinearLayoutManager(requireContext()));
        branchAdapter = new GitBranchAdapter(new GitBranchAdapter.BranchActionCallback() {
            @Override public void onCheckout(Ref ref) { checkoutBranch(ref); }
            @Override public void onRename(Ref ref) { renameBranchDialog(ref); }
            @Override public void onDelete(Ref ref) { confirmDeleteBranch(ref); }
        });
        rvBranches.setAdapter(branchAdapter);
        if (!lastBranchList.isEmpty()) branchAdapter.updateData(lastBranchList, lastStatusSummary.currentBranch);

        TextInputEditText searchBranch = view.findViewById(R.id.et_search_branch);
        searchBranch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (branchAdapter != null) branchAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        view.findViewById(R.id.fab_new_branch).setOnClickListener(v -> showNewBranchDialog());
    }

    private void checkoutBranch(Ref ref) {
        String full = ref.getName();
        String shortName = full.replace("refs/heads/", "").replace("refs/remotes/", "");
        boolean isRemote = full.startsWith("refs/remotes/");
        runGitTask("Checking out " + shortName + "...", true, () -> {
            if (isRemote) {
                String localName = shortName.contains("/") ? shortName.substring(shortName.indexOf('/') + 1) : shortName;
                boolean localExists = false;
                for (Ref r : git.branchList().call()) {
                    if (r.getName().equals("refs/heads/" + localName)) { localExists = true; break; }
                }
                git.checkout().setName(localName).setCreateBranch(!localExists).setStartPoint(full).call();
            } else {
                git.checkout().setName(shortName).call();
            }
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) {
                SketchwareUtil.toast("Switched to " + shortName);
                refreshGitStatus(); refreshHistory(); refreshBranches();
            }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        });
    }

    private String isValidNewBranchName(String name) {
        if (name.isEmpty()) return "Branch name is required";
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9._/-]*") || name.endsWith(".lock") || name.contains("..") || name.endsWith("/") || name.endsWith(".")) {
            return "Invalid branch name";
        }
        for (Ref r : lastBranchList) {
            String shortName = r.getName().replace("refs/heads/", "").replace("refs/remotes/", "");
            if (shortName.equalsIgnoreCase(name)) return "Branch already exists";
        }
        return null;
    }

    private void showNewBranchDialog() {
        showValidatedInputDialog("New Branch", "Branch Name", "", this::isValidNewBranchName, name ->
                runGitTask("Creating branch...", true, () -> { git.branchCreate().setName(name).call(); return null; },
                        new GitTaskCallback<Void>() {
                            @Override public void onSuccess(Void r) { SketchwareUtil.toast("Branch created"); refreshBranches(); }
                            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                        }));
    }

    private void renameBranchDialog(Ref ref) {
        String oldName = ref.getName().replace("refs/heads/", "");
        showValidatedInputDialog("Rename Branch", "Branch Name", oldName, this::isValidNewBranchName, newName ->
                runGitTask("Renaming branch...", true, () -> { git.branchRename().setOldName(oldName).setNewName(newName).call(); return null; },
                        new GitTaskCallback<Void>() {
                            @Override public void onSuccess(Void r) { SketchwareUtil.toast("Branch renamed"); refreshBranches(); refreshGitStatus(); }
                            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                        }));
    }

    private void confirmDeleteBranch(Ref ref) {
        String name = ref.getName().replace("refs/heads/", "");
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete branch?")
                .setMessage("Delete local branch \"" + name + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> doDeleteBranch(name, false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void doDeleteBranch(String name, boolean force) {
        runGitTask("Deleting branch...", true, () -> { git.branchDelete().setBranchNames(name).setForce(force).call(); return null; },
                new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { SketchwareUtil.toast("Branch deleted"); refreshBranches(); }
                    @Override public void onError(String message) {
                        if (!force && message != null && message.toLowerCase(Locale.getDefault()).contains("not fully merged")) {
                            new MaterialAlertDialogBuilder(requireContext())
                                    .setTitle("Branch not fully merged")
                                    .setMessage("\"" + name + "\" has unmerged changes. Force delete anyway?")
                                    .setPositiveButton("Force Delete", (d, w) -> doDeleteBranch(name, true))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        } else {
                            SketchwareUtil.toastError(message);
                        }
                    }
                });
    }

    // ---------------------------------------------------------------------
    // Remotes tab (bug #5 + feature request #12: Edit / Remove / Test
    // Connection, plus a validated Add Remote dialog with its own Test
    // Connection action before saving).
    // ---------------------------------------------------------------------
    private void setupRemotesTab(View view) {
        RecyclerView rvRemotes = view.findViewById(R.id.rv_remotes);
        rvRemotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        remoteAdapter = new GitRemoteAdapter(new GitRemoteAdapter.RemoteActionCallback() {
            @Override public void onEdit(RemoteConfig remote) { editRemoteDialog(remote); }
            @Override public void onRemove(RemoteConfig remote) { confirmRemoveRemote(remote); }
            @Override public void onTestConnection(RemoteConfig remote) { testConfiguredRemote(remote); }
        });
        rvRemotes.setAdapter(remoteAdapter);
        if (!lastRemoteList.isEmpty()) remoteAdapter.updateData(lastRemoteList);

        view.findViewById(R.id.btn_git_fetch).setOnClickListener(v -> doFetch());
        view.findViewById(R.id.btn_git_pull).setOnClickListener(v -> doPull());
        view.findViewById(R.id.btn_git_push).setOnClickListener(v -> doPush());

        view.findViewById(R.id.fab_add_remote).setOnClickListener(v ->
                showRemoteDialog(null, (name, url) -> runGitTask("Adding remote...", true, () -> {
                    git.remoteAdd().setName(name).setUri(new URIish(url)).call();
                    return null;
                }, new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { SketchwareUtil.toast("Remote added"); refreshRemotes(); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                })));
    }

    private void editRemoteDialog(RemoteConfig remote) {
        showRemoteDialog(remote, (name, url) -> runGitTask("Updating remote...", true, () -> {
            git.remoteSetUrl().setRemoteName(remote.getName()).setRemoteUri(new URIish(url)).call();
            try {
                org.eclipse.jgit.api.RemoteSetUrlCommand cmd = git.remoteSetUrl();
                cmd.setRemoteName(remote.getName());
                cmd.setRemoteUri(new URIish(url));
                cmd.setPush(true);
                cmd.call();
            } catch (Exception ignored) {}
            return null;
        }, new GitTaskCallback<Void>() {
            @Override public void onSuccess(Void r) { SketchwareUtil.toast("Remote updated"); refreshRemotes(); }
            @Override public void onError(String message) { SketchwareUtil.toastError(message); }
        }));
    }

    private void confirmRemoveRemote(RemoteConfig remote) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove remote?")
                .setMessage("Remove remote \"" + remote.getName() + "\"? This only affects your local configuration.")
                .setPositiveButton("Remove", (d, w) -> runGitTask("Removing remote...", true, () -> {
                    git.remoteRemove().setRemoteName(remote.getName()).call();
                    return null;
                }, new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { SketchwareUtil.toast("Remote removed"); refreshRemotes(); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                }))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void testConfiguredRemote(RemoteConfig remote) {
        runGitTask("Testing connection...", true,
                () -> git.lsRemote().setRemote(remote.getName()).setCredentialsProvider(getCredentials()).call().size(),
                new GitTaskCallback<Integer>() {
                    @Override public void onSuccess(Integer count) { SketchwareUtil.toast("Connection OK \u2014 " + count + " ref(s) found"); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                });
    }

    private void testRemoteUrlConnection(String url) {
        runGitTask("Testing connection...", true,
                () -> Git.lsRemoteRepository().setRemote(url).setCredentialsProvider(getCredentials()).call().size(),
                new GitTaskCallback<Integer>() {
                    @Override public void onSuccess(Integer count) { SketchwareUtil.toast("Connection OK \u2014 " + count + " ref(s) found"); }
                    @Override public void onError(String message) { SketchwareUtil.toastError(message); }
                });
    }

    private String validateRemoteName(String name) {
        if (name.isEmpty()) return "Remote name is required";
        if (!name.matches("[A-Za-z0-9._-]+")) return "Invalid remote name";
        for (RemoteConfig rc : lastRemoteList) if (rc.getName().equalsIgnoreCase(name)) return "Remote already exists";
        return null;
    }

    private String validateRemoteUrl(String url) {
        if (url.isEmpty()) return "Remote URL is required";
        boolean looksValid = url.matches("^(https?|git|ssh)://.+") || url.matches("^[\\w.-]+@[\\w.-]+:.+");
        return looksValid ? null : "Enter a valid Git URL";
    }

    private interface OnRemoteSaved { void onSave(String name, String url); }

    private void showRemoteDialog(@Nullable RemoteConfig existing, OnRemoteSaved listener) {
        boolean isEdit = existing != null;
        String initialName = isEdit ? existing.getName() : "";
        String initialUrl = isEdit && !existing.getURIs().isEmpty() ? existing.getURIs().get(0).toString() : "";

        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int hPad = SketchwareUtil.dpToPx(24), tPad = SketchwareUtil.dpToPx(16);
        layout.setPadding(hPad, tPad, hPad, 0);

        TextInputLayout tilName = new TextInputLayout(requireContext());
        tilName.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText etName = new TextInputEditText(requireContext());
        etName.setHint("Remote Name (e.g. origin)");
        etName.setText(initialName);
        etName.setEnabled(!isEdit);
        tilName.addView(etName);
        layout.addView(tilName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputLayout tilUrl = new TextInputLayout(requireContext());
        tilUrl.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText etUrl = new TextInputEditText(requireContext());
        etUrl.setHint("Remote URL (https://github.com/...)");
        etUrl.setText(initialUrl);
        tilUrl.addView(etUrl);
        LinearLayout.LayoutParams urlLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        urlLp.topMargin = SketchwareUtil.dpToPx(12);
        layout.addView(tilUrl, urlLp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isEdit ? "Edit Remote" : "Add Remote")
                .setView(layout)
                .setPositiveButton(isEdit ? "Save" : "Add", null)
                .setNeutralButton("Test Connection", null)
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);

            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);

            Runnable validate = () -> {
                String name = etName.getText().toString().trim();
                String url = etUrl.getText().toString().trim();
                String nameError = isEdit ? null : validateRemoteName(name);
                String urlError = validateRemoteUrl(url);
                tilName.setError(nameError);
                tilUrl.setError(urlError);
                positive.setEnabled(nameError == null && urlError == null);
                neutral.setEnabled(urlError == null && !url.isEmpty());
            };
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) { validate.run(); }
                @Override public void afterTextChanged(Editable s) {}
            };
            etName.addTextChangedListener(watcher);
            etUrl.addTextChangedListener(watcher);
            validate.run();

            positive.setOnClickListener(v -> {
                listener.onSave(etName.getText().toString().trim(), etUrl.getText().toString().trim());
                dialog.dismiss();
            });
            neutral.setOnClickListener(v -> testRemoteUrlConnection(etUrl.getText().toString().trim()));
        });
        dialog.show();
    }

    // ---------------------------------------------------------------------
    // Settings tab — unchanged in behaviour, but the config write now also
    // goes through gitExecutor instead of the UI thread.
    // ---------------------------------------------------------------------
    private void setupSettingsTab(View view) {
        SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);

        TextInputEditText etName = view.findViewById(R.id.et_git_name);
        TextInputEditText etEmail = view.findViewById(R.id.et_git_email);
        TextInputEditText etToken = view.findViewById(R.id.et_git_token);

        etName.setText(prefs.getString("name", ""));
        etEmail.setText(prefs.getString("email", ""));
        etToken.setText(prefs.getString("token", ""));

        view.findViewById(R.id.btn_save_settings).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String token = etToken.getText().toString().trim();

            prefs.edit().putString("name", name).putString("email", email).putString("token", token).apply();

            if (git != null) {
                runGitTask(null, false, () -> {
                    StoredConfig config = git.getRepository().getConfig();
                    config.setString("user", null, "name", name);
                    config.setString("user", null, "email", email);
                    config.save();
                    return null;
                }, new GitTaskCallback<Void>() {
                    @Override public void onSuccess(Void r) { SketchwareUtil.toast("Settings Saved"); }
                    @Override public void onError(String message) { SketchwareUtil.toastError("Settings saved, but git config update failed: " + message); }
                });
            } else {
                SketchwareUtil.toast("Settings Saved");
            }
        });
    }

    // ---------------------------------------------------------------------
    // Bug #10: Material 3 dialog with real-time validation — the action
    // button stays disabled until the input is valid, and the reason is
    // shown inline via TextInputLayout.setError() instead of a toast.
    // ---------------------------------------------------------------------
    private interface NameValidator { String validate(String text); } // null == valid
    private interface OnValidatedSave { void onSave(String text); }

    private void showValidatedInputDialog(String title, String hint, String defaultText, NameValidator validator, OnValidatedSave listener) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int hPad = SketchwareUtil.dpToPx(24), tPad = SketchwareUtil.dpToPx(16);
        layout.setPadding(hPad, tPad, hPad, 0);

        TextInputLayout til = new TextInputLayout(requireContext());
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText et = new TextInputEditText(requireContext());
        et.setText(defaultText);
        et.setHint(hint);
        til.addView(et);
        layout.addView(til, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> listener.onSave(et.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            et.requestFocus();
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            TextWatcher watcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                    String error = validator.validate(s.toString().trim());
                    til.setError(error);
                    positive.setEnabled(error == null);
                }
                @Override public void afterTextChanged(Editable s) {}
            };
            et.addTextChangedListener(watcher);
            watcher.onTextChanged(et.getText(), 0, 0, 0);
        });
        dialog.show();
    }

    private class GitPagerAdapter extends PagerAdapter {
        private final LayoutInflater inflater;
        GitPagerAdapter(Context context) { this.inflater = LayoutInflater.from(context); }
        @Override public int getCount() { return tabTitles.length; }
        @NonNull @Override public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view;
            if (position == 0) { view = inflater.inflate(R.layout.tab_git_changes, container, false); setupChangesTab(view); }
            else if (position == 1) { view = inflater.inflate(R.layout.tab_git_history, container, false); setupHistoryTab(view); }
            else if (position == 2) { view = inflater.inflate(R.layout.tab_git_branches, container, false); setupBranchesTab(view); }
            else if (position == 3) { view = inflater.inflate(R.layout.tab_git_remotes, container, false); setupRemotesTab(view); }
            else { view = inflater.inflate(R.layout.tab_git_settings, container, false); setupSettingsTab(view); }
            container.addView(view); return view;
        }
        @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) { return view == object; }
        @Override public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) { container.removeView((View) object); }
        @Nullable @Override public CharSequence getPageTitle(int position) { return tabTitles[position]; }
    }
}
