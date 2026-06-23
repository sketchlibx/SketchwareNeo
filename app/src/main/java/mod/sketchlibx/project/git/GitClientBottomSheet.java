package mod.sketchlibx.project.git;

import android.app.Dialog;
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
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
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
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.NoHeadException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
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

public class GitClientBottomSheet extends BottomSheetDialogFragment {

    private String sc_id;
    private ViewPager viewPager;
    private TabLayout tabLayout;
    private final String[] tabTitles = {"Changes", "History", "Branches", "Remotes", "Settings"};

    private Git git;
    private File repoDir;
    private ChangesAdapter changesAdapter;
    private HistoryAdapter historyAdapter;
    private GitBranchAdapter branchAdapter;
    private GitRemoteAdapter remoteAdapter;

    private AlertDialog progressDialog;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
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

        TextView tvRepoName = view.findViewById(R.id.tv_repo_name);
        Chip chipBranch = view.findViewById(R.id.chip_current_branch);
        tvRepoName.setText("Project: " + sc_id);
        
        try {
            if (git != null) chipBranch.setText(git.getRepository().getBranch());
        } catch (Exception ignored) {}

        viewPager = view.findViewById(R.id.view_pager);
        tabLayout = view.findViewById(R.id.tab_layout);
        viewPager.setAdapter(new GitPagerAdapter(requireContext()));
        tabLayout.setupWithViewPager(viewPager);

        view.findViewById(R.id.btn_action_refresh).setOnClickListener(v -> refreshGitData(view, true));
        
        refreshGitData(view, false);
    }

    private CredentialsProvider getCredentials() {
        SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);
        String token = prefs.getString("token", "");
        String username = prefs.getString("name", "");
        return new UsernamePasswordCredentialsProvider(username, token);
    }

    private void showProgressDialog(String message) {
        mainHandler.post(() -> {
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
                if(msgView != null) msgView.setText(message);
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

    private void refreshGitData(View view, boolean generateSourceFirst) {
        if (git == null) return;
        
        if (generateSourceFirst) showProgressDialog("Generating Sketchware Source Code...");

        executorService.execute(() -> {
            try {
                if (generateSourceFirst) {
                    jC.a(sc_id).j();
                    jC.b(sc_id).m();
                    jC.c(sc_id).l();
                    jC.d(sc_id).x();

                    hC hCVar = new hC(sc_id);
                    kC kCVar = new kC(sc_id);
                    eC eCVar = new eC(sc_id);
                    iC iCVar = new iC(sc_id);

                    hCVar.i(); kCVar.s(); eCVar.g(); eCVar.e(); iCVar.i();

                    java.util.HashMap<String, Object> projectInfo = lC.b(sc_id);
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

                Status status = git.status().call();
                List<ChangesAdapter.GitFile> changeList = new ArrayList<>();
                
                int colorMod = ThemeUtils.getColor(requireContext(), R.attr.colorAccent);
                int colorAdd = ThemeUtils.getColor(requireContext(), R.attr.colorPrimary);
                int colorDel = ThemeUtils.getColor(requireContext(), R.attr.colorError);
                int colorUntrack = ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant);

                for (String s : status.getModified()) changeList.add(new ChangesAdapter.GitFile(s, "Modified", false, colorMod));
                for (String s : status.getChanged()) changeList.add(new ChangesAdapter.GitFile(s, "Modified", true, colorMod));
                for (String s : status.getAdded()) changeList.add(new ChangesAdapter.GitFile(s, "Added", true, colorAdd));
                for (String s : status.getUntracked()) changeList.add(new ChangesAdapter.GitFile(s, "Untracked", false, colorUntrack));
                for (String s : status.getRemoved()) changeList.add(new ChangesAdapter.GitFile(s, "Deleted", true, colorDel));
                for (String s : status.getMissing()) changeList.add(new ChangesAdapter.GitFile(s, "Deleted", false, colorDel));

                List<RevCommit> commitList = new ArrayList<>();
                try {
                    if (git.getRepository().resolve(Constants.HEAD) != null) {
                        Iterable<RevCommit> logs = git.log().call();
                        for (RevCommit rev : logs) commitList.add(rev);
                    }
                } catch (NoHeadException ignored) {}

                List<Ref> branchList = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
                String currentBranch = git.getRepository().getBranch();

                List<RemoteConfig> remoteList = RemoteConfig.getAllRemoteConfigs(git.getRepository().getConfig());

                mainHandler.post(() -> {
                    if (changesAdapter != null) changesAdapter.updateData(changeList);
                    if (historyAdapter != null) historyAdapter.updateData(commitList);
                    if (branchAdapter != null) branchAdapter.updateData(branchList, currentBranch);
                    if (remoteAdapter != null) remoteAdapter.updateData(remoteList);
                    
                    TextView tvStatus = view.findViewById(R.id.tv_repo_status);
                    if (tvStatus != null) tvStatus.setText(status.getUncommittedChanges().size() + " modified • " + status.getChanged().size() + " staged");
                    if (generateSourceFirst) hideProgressDialog();
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    if (generateSourceFirst) hideProgressDialog();
                    SketchwareUtil.toastError("Failed to sync status: " + e.getMessage());
                });
            }
        });
    }

    private void executeGitAction(String progressMessage, GitCallable action) {
        if (git == null) return;
        showProgressDialog(progressMessage);
        executorService.execute(() -> {
            try {
                action.call();
                mainHandler.post(() -> {
                    hideProgressDialog();
                    SketchwareUtil.toast("Operation successful!");
                    refreshGitData(getView(), false);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgressDialog();
                    SketchwareUtil.toastError("Git action failed: " + e.getMessage());
                });
            }
        });
    }

    interface GitCallable { void call() throws Exception; }

    private void setupChangesTab(View view) {
        RecyclerView rvChanges = view.findViewById(R.id.rv_changes);
        rvChanges.setLayoutManager(new LinearLayoutManager(requireContext()));
        
        changesAdapter = new ChangesAdapter(git, new ChangesAdapter.ChangeActionCallback() {
            @Override public void onActionStart(String msg) { showProgressDialog(msg); }
            @Override public void onActionSuccess() { mainHandler.post(() -> { hideProgressDialog(); refreshGitData(getView(), false); }); }
            @Override public void onActionError(String title, String msg) { mainHandler.post(() -> { hideProgressDialog(); SketchwareUtil.toastError(msg); }); }
        });
        rvChanges.setAdapter(changesAdapter);

        TextInputEditText etCommit = view.findViewById(R.id.et_commit_message);
        view.findViewById(R.id.btn_commit).setOnClickListener(v -> {
            String msg = etCommit.getText().toString();
            if (msg.isEmpty()) { SketchwareUtil.toast("Enter a commit message"); return; }
            executeGitAction("Committing...", () -> {
                SharedPreferences prefs = requireActivity().getSharedPreferences("git_config", Context.MODE_PRIVATE);
                String name = prefs.getString("name", "Sketchware User");
                String email = prefs.getString("email", "user@sketchware.neo");
                git.commit().setAuthor(name, email).setCommitter(name, email).setMessage(msg).call();
            });
            etCommit.setText("");
        });
    }

    private void setupHistoryTab(View view) {
        RecyclerView rvHistory = view.findViewById(R.id.rv_history);
        rvHistory.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyAdapter = new HistoryAdapter();
        rvHistory.setAdapter(historyAdapter);
    }

    private void setupBranchesTab(View view) {
        RecyclerView rvBranches = view.findViewById(R.id.rv_branches);
        rvBranches.setLayoutManager(new LinearLayoutManager(requireContext()));
        branchAdapter = new GitBranchAdapter();
        rvBranches.setAdapter(branchAdapter);

        TextInputEditText searchBranch = view.findViewById(R.id.et_search_branch);
        searchBranch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (branchAdapter != null) branchAdapter.filter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        
        view.findViewById(R.id.fab_new_branch).setOnClickListener(v -> {
            showSingleInputDialog("New Branch", "Branch Name", "", text -> {
                executeGitAction("Creating branch...", () -> git.branchCreate().setName(text).call());
            });
        });
    }

    private void setupRemotesTab(View view) {
        RecyclerView rvRemotes = view.findViewById(R.id.rv_remotes);
        rvRemotes.setLayoutManager(new LinearLayoutManager(requireContext()));
        remoteAdapter = new GitRemoteAdapter();
        rvRemotes.setAdapter(remoteAdapter);

        // Network Fix: Pushing and Pulling now uses Credentials
        view.findViewById(R.id.btn_git_fetch).setOnClickListener(v -> executeGitAction("Fetching...", () -> git.fetch().setCredentialsProvider(getCredentials()).call()));
        view.findViewById(R.id.btn_git_pull).setOnClickListener(v -> executeGitAction("Pulling...", () -> git.pull().setCredentialsProvider(getCredentials()).call()));
        view.findViewById(R.id.btn_git_push).setOnClickListener(v -> executeGitAction("Pushing...", () -> git.push().setCredentialsProvider(getCredentials()).call()));
        
        view.findViewById(R.id.fab_add_remote).setOnClickListener(v -> {
            showDoubleInputDialog("Add Remote", "Remote Name (e.g. origin)", "Remote URL (https://github.com/...)", (name, url) -> {
                executeGitAction("Adding remote...", () -> {
                    StoredConfig config = git.getRepository().getConfig();
                    RemoteConfig remoteConfig = new RemoteConfig(config, name);
                    remoteConfig.addURI(new URIish(url));
                    remoteConfig.update(config);
                    config.save();
                });
            });
        });
    }

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

            prefs.edit()
                 .putString("name", name)
                 .putString("email", email)
                 .putString("token", token)
                 .apply();

            if (git != null) {
                try {
                    StoredConfig config = git.getRepository().getConfig();
                    config.setString("user", null, "name", name);
                    config.setString("user", null, "email", email);
                    config.save();
                } catch (Exception ignored) {}
            }
            SketchwareUtil.toast("Settings Saved");
        });
    }

    private void showSingleInputDialog(String title, String hint, String defaultText, OnInputSaved listener) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(24), 0);

        TextInputLayout til = new TextInputLayout(requireContext());
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        TextInputEditText et = new TextInputEditText(requireContext());
        et.setText(defaultText);
        et.setHint(hint);
        et.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
        
        til.addView(et);
        layout.addView(til, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    String text = et.getText().toString().trim();
                    if (!text.isEmpty()) listener.onSave(text);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            et.requestFocus();
        });
        dialog.show();
    }

    private void showDoubleInputDialog(String title, String hint1, String hint2, OnDoubleInputSaved listener) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(24), 0);

        TextInputLayout til1 = new TextInputLayout(requireContext());
        til1.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText et1 = new TextInputEditText(requireContext());
        et1.setHint(hint1);
        til1.addView(et1);
        layout.addView(til1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputLayout til2 = new TextInputLayout(requireContext());
        til2.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText et2 = new TextInputEditText(requireContext());
        et2.setHint(hint2);
        til2.addView(et2);
        LinearLayout.LayoutParams params2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params2.setMargins(0, SketchwareUtil.dpToPx(16), 0, 0);
        layout.addView(til2, params2);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(layout)
                .setPositiveButton("Add", (d, w) -> {
                    String text1 = et1.getText().toString().trim();
                    String text2 = et2.getText().toString().trim();
                    if (!text1.isEmpty() && !text2.isEmpty()) listener.onSave(text1, text2);
                    else SketchwareUtil.toastError("Both fields are required");
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.setOnShowListener(d -> {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            et1.requestFocus();
        });
        dialog.show();
    }

    interface OnInputSaved { void onSave(String text); }
    interface OnDoubleInputSaved { void onSave(String t1, String t2); }

    class GitBranchAdapter extends RecyclerView.Adapter<GitBranchAdapter.VH> {
        private final List<Ref> items = new ArrayList<>();
        private final List<Ref> filtered = new ArrayList<>();
        private String currentBranch = "";

        public void updateData(List<Ref> refs, String current) {
            items.clear(); items.addAll(refs);
            filtered.clear(); filtered.addAll(refs);
            currentBranch = current;
            notifyDataSetChanged();
        }

        public void filter(String q) {
            filtered.clear();
            if (q.isEmpty()) filtered.addAll(items);
            else {
                for (Ref r : items) {
                    if (r.getName().toLowerCase().contains(q.toLowerCase())) filtered.add(r);
                }
            }
            notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12));
            
            ImageView icon = new ImageView(parent.getContext());
            icon.setImageResource(R.drawable.ic_mtrl_share);
            root.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));
            
            TextView title = new TextView(parent.getContext());
            title.setTextSize(16f);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.setMarginStart(SketchwareUtil.dpToPx(16));
            root.addView(title, lp);
            
            return new VH(root, title, icon);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            Ref ref = filtered.get(position);
            String name = ref.getName().replace("refs/heads/", "").replace("refs/remotes/", "");
            holder.title.setText(name);
            if (currentBranch.equals(ref.getName()) || currentBranch.equals(name)) {
                holder.title.setTypeface(null, android.graphics.Typeface.BOLD);
                holder.title.setTextColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorAccent));
                holder.icon.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorAccent));
            } else {
                holder.title.setTypeface(null, android.graphics.Typeface.NORMAL);
                holder.title.setTextColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOnSurface));
                holder.icon.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOnSurfaceVariant));
            }
        }
        @Override public int getItemCount() { return filtered.size(); }
        class VH extends RecyclerView.ViewHolder { TextView title; ImageView icon; VH(View v, TextView t, ImageView i) { super(v); title = t; icon = i; } }
    }

    class GitRemoteAdapter extends RecyclerView.Adapter<GitRemoteAdapter.VH> {
        private final List<RemoteConfig> items = new ArrayList<>();

        public void updateData(List<RemoteConfig> remotes) {
            items.clear(); items.addAll(remotes); notifyDataSetChanged();
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12));
            
            TextView title = new TextView(parent.getContext());
            title.setTextSize(16f);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            title.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            root.addView(title);
            
            TextView url = new TextView(parent.getContext());
            url.setTextSize(14f);
            url.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            url.setPadding(0, SketchwareUtil.dpToPx(4), 0, 0);
            root.addView(url);
            
            return new VH(root, title, url);
        }

        @Override public void onBindViewHolder(@NonNull VH holder, int position) {
            RemoteConfig rc = items.get(position);
            holder.title.setText(rc.getName());
            if (!rc.getURIs().isEmpty()) holder.url.setText(rc.getURIs().get(0).toString());
            else holder.url.setText("No URL");
        }
        @Override public int getItemCount() { return items.size(); }
        class VH extends RecyclerView.ViewHolder { TextView title, url; VH(View v, TextView t, TextView u) { super(v); title = t; url = u; } }
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
