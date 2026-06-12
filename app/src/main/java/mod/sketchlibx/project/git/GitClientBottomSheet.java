package mod.sketchlibx.project.git;

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import a.a.a.ProjectBuilder;
import a.a.a.eC;
import a.a.a.hC;
import a.a.a.iC;
import a.a.a.jC;
import a.a.a.kC;
import a.a.a.lC;
import a.a.a.wq;
import a.a.a.xq;
import a.a.a.yB;
import a.a.a.yq;
import mod.hilal.saif.activities.tools.ConfigActivity;
import mod.jbk.build.BuildProgressReceiver;
import pro.sketchware.R;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class GitClientBottomSheet extends BottomSheetDialogFragment {

    private String sc_id;
    private Git git;
    private String gitWorkspacePath;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isDirectPushEnabled = false;
    private Dialog progressDialog;
    private TextView progressDialogText;

    private RecyclerView rvChanges;
    private GitChangeAdapter changesAdapter;
    private TextView tvStatusEmpty;
    private MaterialButton btnCommit;
    private MaterialButton btnCommitPush;
    private TextInputEditText etCommitMsg;
    private TextInputLayout tilCommitMsg;

    private RecyclerView rvHistory;
    private TextView tvHistoryEmpty;
    private GitHistoryAdapter historyAdapter;

    private RecyclerView rvBranches;
    private GitBranchAdapter branchAdapter;

    private RecyclerView rvRemotes;
    private GitRemoteAdapter remoteAdapter;

    public GitClientBottomSheet() {
    }

    public GitClientBottomSheet(String sc_id) {
        this.sc_id = sc_id;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        isDirectPushEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_GIT_DIRECT_PUSH);
        gitWorkspacePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/git_workspace";
        
        try {
            File gitDir = new File(gitWorkspacePath, ".git");
            if (gitDir.exists()) {
                git = Git.open(new File(gitWorkspacePath));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NonNull
    @Override
    public android.app.Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        dialog.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            View bottomSheetInternal = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheetInternal != null) {
                BottomSheetBehavior.from(bottomSheetInternal).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheetInternal).setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_git_client, container, false);

        TabLayout tabLayout = view.findViewById(R.id.tab_layout);
        ViewPager viewPager = view.findViewById(R.id.view_pager);

        GitPagerAdapter adapter = new GitPagerAdapter(requireContext(), inflater);
        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);

        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (git != null) git.close();
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
    }

    private void showProgress(String message) {
        mainHandler.post(() -> {
            if (progressDialog == null) {
                LinearLayout container = new LinearLayout(requireContext());
                container.setOrientation(LinearLayout.HORIZONTAL);
                container.setGravity(Gravity.CENTER_VERTICAL);
                container.setPadding(64, 48, 64, 48);

                CircularProgressIndicator progressIndicator = new CircularProgressIndicator(requireContext(), null, com.google.android.material.R.style.Widget_Material3Expressive_CircularProgressIndicator_Wavy);
                progressIndicator.setIndeterminate(true);
                
                progressDialogText = new TextView(requireContext());
                progressDialogText.setTextSize(16f);
                progressDialogText.setPadding(48, 0, 0, 0);
                progressDialogText.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
                progressDialogText.setTypeface(null, Typeface.BOLD);

                container.addView(progressIndicator);
                container.addView(progressDialogText);

                progressDialog = new MaterialAlertDialogBuilder(requireContext())
                        .setView(container)
                        .setCancelable(false)
                        .create();
            }
            progressDialogText.setText(message);
            if (!progressDialog.isShowing()) progressDialog.show();
        });
    }

    private void hideProgress() {
        mainHandler.post(() -> {
            if (progressDialog != null && progressDialog.isShowing()) {
                progressDialog.dismiss();
            }
        });
    }
    
    private void showErrorDialog(String errorTitle, String errorMessage) {
        mainHandler.post(() -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(errorTitle)
                    .setMessage(errorMessage)
                    .setIcon(R.drawable.ic_cancel_48dp)
                    .setPositiveButton("OK", null)
                    .show();
        });
    }

    private void setupChangesTab(View view) {
        rvChanges = view.findViewById(R.id.rv_changes);
        tvStatusEmpty = view.findViewById(R.id.tv_status_empty);
        btnCommit = view.findViewById(R.id.btn_commit);
        btnCommitPush = view.findViewById(R.id.btn_commit_push);
        etCommitMsg = view.findViewById(R.id.et_commit_msg);
        tilCommitMsg = view.findViewById(R.id.til_commit_msg);
        MaterialButton btnStageAll = view.findViewById(R.id.btn_stage_all);
        ImageView btnRefresh = view.findViewById(R.id.btn_refresh);

        rvChanges.setLayoutManager(new LinearLayoutManager(getContext()));
        changesAdapter = new GitChangeAdapter();
        rvChanges.setAdapter(changesAdapter);

        if (isDirectPushEnabled) btnCommitPush.setVisibility(View.VISIBLE);

        updateCommitButtonState();
        etCommitMsg.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateCommitButtonState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnRefresh.setOnClickListener(v -> loadGitStatus());
        btnStageAll.setOnClickListener(v -> performStageAll());
        btnCommit.setOnClickListener(v -> performCommit(false));
        btnCommitPush.setOnClickListener(v -> performCommit(true));

        loadGitStatus();
    }

    private void updateCommitButtonState() {
        boolean hasMessage = etCommitMsg.getText() != null && !etCommitMsg.getText().toString().trim().isEmpty();
        boolean hasStagedFiles = changesAdapter != null && changesAdapter.hasStagedFiles();
        btnCommit.setEnabled(hasMessage && hasStagedFiles);
        btnCommitPush.setEnabled(hasMessage && hasStagedFiles);
        tilCommitMsg.setHelperText(hasStagedFiles ? null : "Stage files to commit");
    }

    private void loadGitStatus() {
        if (git == null) return;
        new Thread(() -> {
            try {
                Status status = git.status().call();
                List<GitFile> fileList = new ArrayList<>();

                for (String s : status.getUntracked()) fileList.add(new GitFile(s, "Untracked", false, Color.parseColor("#4CAF50")));
                for (String s : status.getModified()) fileList.add(new GitFile(s, "Modified", false, Color.parseColor("#2196F3")));
                for (String s : status.getMissing()) fileList.add(new GitFile(s, "Deleted", false, Color.parseColor("#F44336")));

                for (String s : status.getAdded()) fileList.add(new GitFile(s, "Added", true, Color.parseColor("#4CAF50")));
                for (String s : status.getChanged()) fileList.add(new GitFile(s, "Modified", true, Color.parseColor("#2196F3")));
                for (String s : status.getRemoved()) fileList.add(new GitFile(s, "Deleted", true, Color.parseColor("#F44336")));

                mainHandler.post(() -> {
                    if (fileList.isEmpty()) {
                        tvStatusEmpty.setVisibility(View.VISIBLE);
                        rvChanges.setVisibility(View.GONE);
                    } else {
                        tvStatusEmpty.setVisibility(View.GONE);
                        rvChanges.setVisibility(View.VISIBLE);
                    }
                    changesAdapter.setFiles(fileList);
                    updateCommitButtonState();
                });
            } catch (Exception e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Failed to get status: " + e.getMessage()));
            }
        }).start();
    }

    private void performStageAll() {
        if (git == null) return;
        showProgress("Generating Source Code & Staging...");
        new Thread(() -> {
            try {
                hC hCVar = new hC(sc_id);
                kC kCVar = new kC(sc_id);
                eC eCVar = new eC(sc_id);
                iC iCVar = new iC(sc_id);

                hCVar.i();
                kCVar.s();
                eCVar.g();
                eCVar.e();
                iCVar.i();

                java.util.HashMap<String, Object> projectInfo = lC.b(sc_id);
                yq project_metadata = new yq(requireContext(), wq.d(sc_id), projectInfo);

                project_metadata.a(requireContext(), wq.e(xq.a(sc_id) ? "600" : sc_id));

                BuildProgressReceiver progressReceiver = new BuildProgressReceiver() {
                    @Override
                    public void onProgress(String progress, int step) {
                        mainHandler.post(() -> {
                            if (progressDialogText != null) {
                                progressDialogText.setText(progress);
                            }
                        });
                    }
                };

                ProjectBuilder builder = new ProjectBuilder(progressReceiver, requireContext(), project_metadata);
                
                project_metadata.a(iCVar, hCVar, eCVar, yq.ExportType.ANDROID_STUDIO);
                builder.buildBuiltInLibraryInformation();
                project_metadata.b(hCVar, eCVar, iCVar, builder.getBuiltInLibraryManager());

                if (yB.a(lC.b(sc_id), "custom_icon")) {
                    project_metadata.aa(wq.e() + File.separator + sc_id + File.separator + "mipmaps");
                }

                project_metadata.a();
                kCVar.b(project_metadata.resDirectoryPath + File.separator + "drawable-xhdpi");
                kCVar.c(project_metadata.resDirectoryPath + File.separator + "raw");
                kCVar.a(project_metadata.assetsPath + File.separator + "fonts");
                project_metadata.f();

                FilePathUtil util = new FilePathUtil();
                File pathJava = new File(util.getPathJava(sc_id));
                File pathRes = new File(util.getPathResource(sc_id));
                File pathAssets = new File(util.getPathAssets(sc_id));

                if (pathJava.exists()) {
                    FileUtil.copyDirectory(pathJava, new File(project_metadata.javaFilesPath + File.separator + project_metadata.packageNameAsFolders));
                }
                if (pathRes.exists()) {
                    FileUtil.copyDirectory(pathRes, new File(project_metadata.resDirectoryPath));
                }
                if (pathAssets.exists()) {
                    FileUtil.copyDirectory(pathAssets, new File(project_metadata.assetsPath));
                }

                File exportDir = new File(gitWorkspacePath);
                File myscRoot = new File(project_metadata.projectMyscPath);
                
                copyFileIfExists(new File(myscRoot, "build.gradle"), new File(exportDir, "build.gradle"));
                copyFileIfExists(new File(myscRoot, "settings.gradle"), new File(exportDir, "settings.gradle"));
                copyFileIfExists(new File(myscRoot, "gradle.properties"), new File(exportDir, "gradle.properties"));
                
                File appDir = new File(exportDir, "app");
                FileUtil.makeDir(appDir.getAbsolutePath());
                
                copyFileIfExists(new File(myscRoot, "app/build.gradle"), new File(appDir, "build.gradle"));
                copyFileIfExists(new File(myscRoot, "app/proguard-rules.pro"), new File(appDir, "proguard-rules.pro"));
                
                File srcDir = new File(myscRoot, "app/src");
                if (srcDir.exists()) {
                    FileUtil.copyDirectory(srcDir, new File(appDir, "src"));
                }

                FileUtil.writeFile(new File(exportDir, "README.md").getAbsolutePath(), "# " + project_metadata.projectName);

                git.add().addFilepattern(".").call();
                
                Status status = git.status().call();
                if (!status.getMissing().isEmpty()) {
                    org.eclipse.jgit.api.RmCommand rm = git.rm();
                    for (String missing : status.getMissing()) rm.addFilepattern(missing);
                    rm.call();
                }

                mainHandler.post(() -> {
                    hideProgress();
                    SketchwareUtil.toast("Full AS Project generated & staged!");
                    loadGitStatus();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    showErrorDialog("Stage Failed", e.getMessage());
                });
            }
        }).start();
    }

    private void copyFileIfExists(File src, File dest) {
        if (src.exists()) {
            FileUtil.copyFile(src.getAbsolutePath(), dest.getAbsolutePath());
        }
    }

    private void performCommit(boolean pushAfter) {
        if (git == null) return;
        String message = etCommitMsg.getText().toString().trim();
        showProgress("Committing changes...");
        new Thread(() -> {
            try {
                git.commit().setMessage(message).call();
                mainHandler.post(() -> {
                    hideProgress();
                    etCommitMsg.setText("");
                    SketchwareUtil.toast("Committed successfully!");
                    loadGitStatus();
                    loadHistory();
                });
                if (pushAfter) {
                    performPushOperation();
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    showErrorDialog("Commit Failed", e.getMessage());
                });
            }
        }).start();
    }

    private void setupHistoryTab(View view) {
        rvHistory = view.findViewById(R.id.rv_history);
        tvHistoryEmpty = view.findViewById(R.id.tv_history_empty);
        ImageView btnRefresh = view.findViewById(R.id.btn_refresh_history);

        rvHistory.setLayoutManager(new LinearLayoutManager(getContext()));
        historyAdapter = new GitHistoryAdapter();
        rvHistory.setAdapter(historyAdapter);

        btnRefresh.setOnClickListener(v -> loadHistory());
        loadHistory();
    }

    private void loadHistory() {
        if (git == null || historyAdapter == null) return;
        new Thread(() -> {
            try {
                Iterable<RevCommit> commits = git.log().call();
                List<RevCommit> commitList = new ArrayList<>();
                for (RevCommit commit : commits) {
                    commitList.add(commit);
                }
                mainHandler.post(() -> {
                    if (commitList.isEmpty()) {
                        tvHistoryEmpty.setVisibility(View.VISIBLE);
                        rvHistory.setVisibility(View.GONE);
                    } else {
                        tvHistoryEmpty.setVisibility(View.GONE);
                        rvHistory.setVisibility(View.VISIBLE);
                        historyAdapter.setCommits(commitList);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    tvHistoryEmpty.setVisibility(View.VISIBLE);
                    rvHistory.setVisibility(View.GONE);
                });
            }
        }).start();
    }

    private void setupBranchesTab(View view) {
        rvBranches = view.findViewById(R.id.rv_branches);
        ExtendedFloatingActionButton fabNewBranch = view.findViewById(R.id.fab_new_branch);

        rvBranches.setLayoutManager(new LinearLayoutManager(getContext()));
        branchAdapter = new GitBranchAdapter();
        rvBranches.setAdapter(branchAdapter);

        fabNewBranch.setOnClickListener(v -> showAddBranchDialog());

        loadBranches();
    }

    private void loadBranches() {
        if (git == null || branchAdapter == null) return;
        new Thread(() -> {
            try {
                List<Ref> call = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call();
                String currentBranch = git.getRepository().getBranch();
                mainHandler.post(() -> branchAdapter.setBranches(call, currentBranch));
            } catch (Exception e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Failed to load branches: " + e.getMessage()));
            }
        }).start();
    }

    private void showAddBranchDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(64, 24, 64, 24);

        TextInputLayout tilName = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("Branch name");
        TextInputEditText etName = new TextInputEditText(requireContext());
        etName.setSingleLine(true);
        tilName.addView(etName);
        container.addView(tilName);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Create Branch")
                .setView(container)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createNewBranch(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createNewBranch(String name) {
        showProgress("Creating branch...");
        new Thread(() -> {
            try {
                git.branchCreate().setName(name).call();
                mainHandler.post(() -> {
                    hideProgress();
                    SketchwareUtil.toast("Branch created!");
                    loadBranches();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    showErrorDialog("Failed to create branch", e.getMessage());
                });
            }
        }).start();
    }

    private void setupRemotesTab(View view) {
        rvRemotes = view.findViewById(R.id.rv_remotes);
        ExtendedFloatingActionButton fabNewRemote = view.findViewById(R.id.fab_new_remote);
        MaterialButton btnFetch = view.findViewById(R.id.btn_git_fetch);
        MaterialButton btnPull = view.findViewById(R.id.btn_git_pull);
        MaterialButton btnPush = view.findViewById(R.id.btn_git_push);

        rvRemotes.setLayoutManager(new LinearLayoutManager(getContext()));
        remoteAdapter = new GitRemoteAdapter();
        rvRemotes.setAdapter(remoteAdapter);

        fabNewRemote.setOnClickListener(v -> showAddRemoteDialog());

        btnFetch.setOnClickListener(v -> performNetworkOperation("Fetching from remote...", "Fetch Completed!", () -> git.fetch().setCredentialsProvider(getCredentials()).call()));
        btnPull.setOnClickListener(v -> performNetworkOperation("Pulling from remote...", "Pull Completed!", () -> git.pull().setCredentialsProvider(getCredentials()).call()));
        
        btnPush.setOnClickListener(v -> performPushOperation());

        loadRemotes();
    }

    private void loadRemotes() {
        if (git == null || remoteAdapter == null) return;
        new Thread(() -> {
            try {
                git.getRepository().getConfig().load();
                List<RemoteConfig> remotes = RemoteConfig.getAllRemoteConfigs(git.getRepository().getConfig());
                mainHandler.post(() -> remoteAdapter.setRemotes(remotes));
            } catch (Exception e) {
                mainHandler.post(() -> SketchwareUtil.toastError("Failed to load remotes: " + e.getMessage()));
            }
        }).start();
    }

    private void showAddRemoteDialog() {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(64, 24, 64, 24);

        TextInputLayout tilName = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilName.setHint("Remote name (e.g. origin)");
        TextInputEditText etName = new TextInputEditText(requireContext());
        etName.setText("origin");
        tilName.addView(etName);

        TextInputLayout tilUrl = new TextInputLayout(requireContext(), null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox);
        tilUrl.setHint("Repository URL (https://...)");
        TextInputEditText etUrl = new TextInputEditText(requireContext());
        tilUrl.addView(etUrl);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 24, 0, 0);
        tilUrl.setLayoutParams(params);

        container.addView(tilName);
        container.addView(tilUrl);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Remote")
                .setView(container)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String url = etUrl.getText().toString().trim();
                    if (!name.isEmpty() && !url.isEmpty()) {
                        addNewRemote(name, url);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addNewRemote(String name, String url) {
        if (!url.startsWith("http") && !url.startsWith("git@")) {
            showErrorDialog("Invalid URL", "Please enter a valid Git URL (must start with http/https or git@)");
            return;
        }

        showProgress("Adding remote...");
        new Thread(() -> {
            try {
                StoredConfig config = git.getRepository().getConfig();
                RemoteConfig remoteConfig = new RemoteConfig(config, name);
                remoteConfig.addURI(new URIish(url));
                remoteConfig.update(config);
                config.save();

                mainHandler.post(() -> {
                    hideProgress();
                    SketchwareUtil.toast("Remote added!");
                    loadRemotes();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    showErrorDialog("Failed to add remote", e.getMessage());
                });
            }
        }).start();
    }

    private CredentialsProvider getCredentials() {
        SharedPreferences prefs = requireContext().getSharedPreferences("GitConfig_" + sc_id, Context.MODE_PRIVATE);
        String token = prefs.getString("pat_token", "");
        String email = "developer@sketchware.pro";
        try {
            email = git.getRepository().getConfig().getString("user", null, "email");
        } catch (Exception ignored) {}

        return new UsernamePasswordCredentialsProvider(email, token);
    }

    private void performPushOperation() {
        showProgress("Pushing to remote...");
        new Thread(() -> {
            try {
                String currentBranch = git.getRepository().getBranch();
                Iterable<PushResult> results = git.push()
                        .add(currentBranch)
                        .setCredentialsProvider(getCredentials())
                        .call();

                boolean hasError = false;
                StringBuilder errorMsg = new StringBuilder();

                for (PushResult result : results) {
                    for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                        if (update.getStatus() != RemoteRefUpdate.Status.OK && update.getStatus() != RemoteRefUpdate.Status.UP_TO_DATE) {
                            hasError = true;
                            errorMsg.append(update.getStatus().name());
                            if (update.getMessage() != null) {
                                errorMsg.append(" (").append(update.getMessage()).append(")");
                            }
                        }
                    }
                }

                if (hasError) {
                    throw new Exception("Push rejected by remote: " + errorMsg.toString());
                }

                mainHandler.post(() -> {
                    hideProgress();
                    SketchwareUtil.toast("Push Completed Successfully!");
                    loadGitStatus();
                    loadHistory();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    Log.e("GitClient", "Network Operation Error", e);
                    showErrorDialog("Push Failed", e.getMessage());
                });
            }
        }).start();
    }

    private void performNetworkOperation(String startMessage, String successMessage, NetworkAction action) {
        showProgress(startMessage);
        new Thread(() -> {
            try {
                action.execute();
                mainHandler.post(() -> {
                    hideProgress();
                    SketchwareUtil.toast(successMessage);
                    loadGitStatus();
                    loadHistory();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    hideProgress();
                    Log.e("GitClient", "Network Operation Error", e);
                    showErrorDialog("Operation Failed", e.getMessage());
                });
            }
        }).start();
    }

    private interface NetworkAction {
        void execute() throws Exception;
    }

    private void setupSettingsTab(View view) {
        TextInputEditText etName = view.findViewById(R.id.et_git_name);
        TextInputEditText etEmail = view.findViewById(R.id.et_git_email);
        TextInputEditText etToken = view.findViewById(R.id.et_git_token);
        MaterialButton btnSave = view.findViewById(R.id.btn_save_settings);

        SharedPreferences prefs = requireContext().getSharedPreferences("GitConfig_" + sc_id, Context.MODE_PRIVATE);
        
        try {
            StoredConfig config = git.getRepository().getConfig();
            etName.setText(config.getString("user", null, "name"));
            etEmail.setText(config.getString("user", null, "email"));
        } catch (Exception ignored) {}

        etToken.setText(prefs.getString("pat_token", ""));

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String token = etToken.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty()) {
                SketchwareUtil.toastError("Name and Email are required!");
                return;
            }

            showProgress("Saving configuration...");
            try {
                StoredConfig config = git.getRepository().getConfig();
                config.setString("user", null, "name", name);
                config.setString("user", null, "email", email);
                config.save();

                prefs.edit().putString("pat_token", token).apply();
                mainHandler.postDelayed(() -> {
                    hideProgress();
                    SketchwareUtil.toast("Git Configuration Saved!");
                }, 500);
            } catch (Exception e) {
                hideProgress();
                showErrorDialog("Failed to save", e.getMessage());
            }
        });
    }

    private static class GitFile {
        String path, statusLabel;
        boolean isStaged;
        int color;
        GitFile(String path, String statusLabel, boolean isStaged, int color) {
            this.path = path; this.statusLabel = statusLabel; this.isStaged = isStaged; this.color = color;
        }
    }

    private class GitChangeAdapter extends RecyclerView.Adapter<GitChangeAdapter.ViewHolder> {
        private List<GitFile> files = new ArrayList<>();
        public void setFiles(List<GitFile> files) { this.files = files; notifyDataSetChanged(); }
        public boolean hasStagedFiles() { for (GitFile f : files) if (f.isStaged) return true; return false; }
        
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext());
            card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            ((RecyclerView.LayoutParams) card.getLayoutParams()).setMargins(0, 0, 0, 16);
            card.setRadius(12f);
            card.setCardElevation(0f);
            card.setCardBackgroundColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorSurfaceVariant));

            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2));
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(32, 24, 32, 24);
            card.addView(root);

            TextView tvBadge = new TextView(parent.getContext());
            tvBadge.setTextSize(12f);
            tvBadge.setPadding(16, 6, 16, 6);
            tvBadge.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
            badgeParams.setMarginEnd(24);
            root.addView(tvBadge, badgeParams);

            TextView tvPath = new TextView(parent.getContext());
            tvPath.setTextSize(14f);
            tvPath.setMaxLines(1);
            tvPath.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            tvPath.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
            root.addView(tvPath, nameParams);

            MaterialButton btnAction = new MaterialButton(parent.getContext(), null, com.google.android.material.R.style.Widget_Material3_Button_TonalButton);
            root.addView(btnAction);

            return new ViewHolder(card, tvBadge, tvPath, btnAction);
        }
        
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GitFile file = files.get(position);
            holder.tvBadge.setText(file.statusLabel.substring(0, 1));
            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
            gd.setColor(file.color); gd.setCornerRadius(12f);
            holder.tvBadge.setBackground(gd);
            holder.tvPath.setText(file.path);
            
            ((MaterialButton) holder.tvAction).setText(file.isStaged ? "UNSTAGE" : "STAGE");
            
            holder.tvAction.setOnClickListener(v -> {
                if (git == null) return;
                showProgress("Updating index...");
                new Thread(() -> {
                    try {
                        if (file.isStaged) git.reset().addPath(file.path).call();
                        else if (file.statusLabel.equals("Deleted")) git.rm().addFilepattern(file.path).call();
                        else git.add().addFilepattern(file.path).call();
                        mainHandler.post(() -> {
                            hideProgress();
                            loadGitStatus();
                        });
                    } catch (Exception e) { 
                        mainHandler.post(() -> {
                            hideProgress();
                            showErrorDialog("Action Failed", e.getMessage()); 
                        });
                    }
                }).start();
            });
        }
        @Override public int getItemCount() { return files.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView tvBadge, tvPath; View tvAction; ViewHolder(View i, TextView b, TextView p, View a) { super(i); tvBadge=b; tvPath=p; tvAction=a; } }
    }

    private class GitHistoryAdapter extends RecyclerView.Adapter<GitHistoryAdapter.ViewHolder> {
        private List<RevCommit> commits = new ArrayList<>();
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());
        public void setCommits(List<RevCommit> commits) { this.commits = commits; notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext()); card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); ((RecyclerView.LayoutParams) card.getLayoutParams()).setMargins(0, 0, 0, 16); card.setRadius(16f); card.setCardElevation(0f); card.setCardBackgroundColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorSurfaceVariant));
            LinearLayout root = new LinearLayout(parent.getContext()); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 24, 32, 24); card.addView(root);
            TextView tvMessage = new TextView(parent.getContext()); tvMessage.setTextSize(16f); tvMessage.setTypeface(null, Typeface.BOLD); tvMessage.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface)); root.addView(tvMessage);
            TextView tvDetails = new TextView(parent.getContext()); tvDetails.setTextSize(12f); tvDetails.setPadding(0, 8, 0, 0); tvDetails.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant)); root.addView(tvDetails);
            return new ViewHolder(card, tvMessage, tvDetails);
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RevCommit commit = commits.get(position);
            holder.tvMessage.setText(commit.getShortMessage());
            holder.tvDetails.setText(String.format("%s • %s • %s", commit.getName().substring(0, 7), commit.getAuthorIdent().getName(), sdf.format(new Date(commit.getCommitTime() * 1000L))));
        }
        @Override public int getItemCount() { return commits.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView tvMessage, tvDetails; ViewHolder(View i, TextView m, TextView d) { super(i); tvMessage=m; tvDetails=d; } }
    }

    private class GitBranchAdapter extends RecyclerView.Adapter<GitBranchAdapter.ViewHolder> {
        private List<Ref> branches = new ArrayList<>();
        private String currentBranch = "";
        public void setBranches(List<Ref> branches, String currentBranch) { this.branches = branches; this.currentBranch = currentBranch; notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext()); card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); ((RecyclerView.LayoutParams) card.getLayoutParams()).setMargins(0, 0, 0, 16); card.setRadius(12f); card.setCardElevation(0f); card.setCardBackgroundColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorSurfaceVariant));
            LinearLayout root = new LinearLayout(parent.getContext()); root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2)); root.setOrientation(LinearLayout.HORIZONTAL); root.setGravity(Gravity.CENTER_VERTICAL); root.setPadding(32, 24, 32, 24); card.addView(root);
            
            TextView tvName = new TextView(parent.getContext()); tvName.setTextSize(16f); tvName.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
            root.addView(tvName, nameParams);
            
            MaterialButton btnCheckout = new MaterialButton(parent.getContext(), null, com.google.android.material.R.style.Widget_Material3_Button);
            LinearLayout.LayoutParams checkoutParams = new LinearLayout.LayoutParams(-2, -2);
            checkoutParams.setMarginEnd((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, parent.getContext().getResources().getDisplayMetrics()));
            btnCheckout.setLayoutParams(checkoutParams);
            root.addView(btnCheckout);

            MaterialButton btnDelete = new MaterialButton(parent.getContext(), null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton);
            btnDelete.setTextColor(Color.parseColor("#B3261E"));
            btnDelete.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#B3261E")));
            root.addView(btnDelete);

            return new ViewHolder(card, tvName, btnCheckout, btnDelete);
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Ref ref = branches.get(position);
            String name = ref.getName().replace("refs/heads/", "").replace("refs/remotes/", "");
            boolean isCurrent = ref.getName().equals(currentBranch) || name.equals(currentBranch.replace("refs/heads/", ""));
            
            holder.tvName.setText(isCurrent ? "✓ " + name : name);
            holder.tvName.setTypeface(null, isCurrent ? Typeface.BOLD : Typeface.NORMAL);
            
            MaterialButton btnCheckout = (MaterialButton) holder.tvCheckout;
            MaterialButton btnDelete = (MaterialButton) holder.tvDelete;

            if (isCurrent) {
                btnCheckout.setText("ACTIVE");
                btnCheckout.setEnabled(false);
                btnDelete.setVisibility(View.GONE);
            } else {
                btnCheckout.setText("CHECKOUT");
                btnCheckout.setEnabled(true);
                btnDelete.setVisibility(View.VISIBLE);
                btnDelete.setText("DELETE");
                
                btnCheckout.setOnClickListener(v -> {
                    showProgress("Switching branch...");
                    new Thread(() -> {
                        try { git.checkout().setName(name).call(); mainHandler.post(() -> { hideProgress(); SketchwareUtil.toast("Switched to " + name); loadBranches(); });
                        } catch (Exception e) { mainHandler.post(() -> { hideProgress(); showErrorDialog("Checkout Failed", e.getMessage()); }); }
                    }).start();
                });
                btnDelete.setOnClickListener(v -> {
                    showProgress("Deleting branch...");
                    new Thread(() -> {
                        try { git.branchDelete().setBranchNames(name).setForce(true).call(); mainHandler.post(() -> { hideProgress(); SketchwareUtil.toast("Branch deleted"); loadBranches(); });
                        } catch (Exception e) { mainHandler.post(() -> { hideProgress(); showErrorDialog("Delete Failed", e.getMessage()); }); }
                    }).start();
                });
            }
        }
        @Override public int getItemCount() { return branches.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView tvName; View tvCheckout, tvDelete; ViewHolder(View i, TextView n, View c, View d) { super(i); tvName=n; tvCheckout=c; tvDelete=d; } }
    }

    private class GitRemoteAdapter extends RecyclerView.Adapter<GitRemoteAdapter.ViewHolder> {
        private List<RemoteConfig> remotes = new ArrayList<>();
        public void setRemotes(List<RemoteConfig> remotes) { this.remotes = remotes; notifyDataSetChanged(); }
        @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            MaterialCardView card = new MaterialCardView(parent.getContext()); card.setLayoutParams(new RecyclerView.LayoutParams(-1, -2)); ((RecyclerView.LayoutParams) card.getLayoutParams()).setMargins(0, 0, 0, 16); card.setRadius(12f); card.setCardElevation(0f); card.setCardBackgroundColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorSurfaceVariant));
            LinearLayout root = new LinearLayout(parent.getContext()); root.setLayoutParams(new ViewGroup.LayoutParams(-1, -2)); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(32, 24, 32, 24); card.addView(root);
            
            LinearLayout top = new LinearLayout(parent.getContext()); top.setOrientation(LinearLayout.HORIZONTAL); top.setGravity(Gravity.CENTER_VERTICAL); root.addView(top);
            TextView tvName = new TextView(parent.getContext()); tvName.setTextSize(16f); tvName.setTypeface(null, Typeface.BOLD); tvName.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface)); 
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
            top.addView(tvName, nameParams);
            
            MaterialButton btnDelete = new MaterialButton(parent.getContext(), null, com.google.android.material.R.style.Widget_Material3_Button_OutlinedButton);
            btnDelete.setTextColor(Color.parseColor("#B3261E"));
            btnDelete.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#B3261E")));
            btnDelete.setText("REMOVE");
            top.addView(btnDelete);
            
            TextView tvUrl = new TextView(parent.getContext()); tvUrl.setTextSize(12f); tvUrl.setPadding(0, 8, 0, 0); tvUrl.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant)); root.addView(tvUrl);
            return new ViewHolder(card, tvName, tvUrl, btnDelete);
        }
        @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            RemoteConfig remote = remotes.get(position);
            holder.tvName.setText(remote.getName());
            holder.tvUrl.setText(remote.getURIs().isEmpty() ? "No URL" : remote.getURIs().get(0).toString());
            holder.tvDelete.setOnClickListener(v -> {
                showProgress("Removing remote...");
                new Thread(() -> {
                    try { git.remoteRemove().setRemoteName(remote.getName()).call(); mainHandler.post(() -> { hideProgress(); SketchwareUtil.toast("Removed remote"); loadRemotes(); });
                    } catch (Exception e) { mainHandler.post(() -> { hideProgress(); showErrorDialog("Remove Failed", e.getMessage()); }); }
                }).start();
            });
        }
        @Override public int getItemCount() { return remotes.size(); }
        class ViewHolder extends RecyclerView.ViewHolder { TextView tvName, tvUrl; View tvDelete; ViewHolder(View i, TextView n, TextView u, View d) { super(i); tvName=n; tvUrl=u; tvDelete=d; } }
    }

    private class GitPagerAdapter extends PagerAdapter {
        private final Context context;
        private final LayoutInflater inflater;
        private final String[] tabTitles = {"Changes", "History", "Branches", "Remotes", "Settings"};

        public GitPagerAdapter(Context context, LayoutInflater inflater) {
            this.context = context;
            this.inflater = inflater;
        }

        @Override public int getCount() { return tabTitles.length; }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View view;
            if (position == 0) {
                view = inflater.inflate(R.layout.tab_git_changes, container, false);
                setupChangesTab(view);
            } else if (position == 1) {
                view = inflater.inflate(R.layout.tab_git_history, container, false);
                setupHistoryTab(view);
            } else if (position == 2) {
                view = inflater.inflate(R.layout.tab_git_branches, container, false);
                setupBranchesTab(view);
            } else if (position == 3) {
                view = inflater.inflate(R.layout.tab_git_remotes, container, false);
                setupRemotesTab(view);
            } else if (position == 4) {
                view = inflater.inflate(R.layout.tab_git_settings, container, false);
                setupSettingsTab(view);
            } else {
                FrameLayout frameLayout = new FrameLayout(context);
                view = frameLayout;
            }
            container.addView(view);
            return view;
        }

        @Override public boolean isViewFromObject(@NonNull View view, @NonNull Object object) { return view == object; }
        @Override public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) { container.removeView((View) object); }
        @Nullable @Override public CharSequence getPageTitle(int position) { return tabTitles[position]; }
    }
}
