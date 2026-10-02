package mod.sketchlibx.project.backup;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import a.a.a.lC;
import mod.hey.studios.project.backup.BackupFactory;
import mod.hey.studios.project.backup.BackupRestoreManager;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityCloudBackupManagerBinding;
import pro.sketchware.databinding.ItemCloudProjectBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class CloudBackupManagerActivity extends BaseAppCompatActivity {

    private static final String PREFS_NAME = "cloud_backup_prefs";

    private ActivityCloudBackupManagerBinding binding;

    private GoogleSignInClient mGoogleSignInClient;
    private GoogleSignInAccount currentAccount;
    private CloudBackupManager cloudManager;

    private final List<ProjectItem> localProjects = new ArrayList<>();
    private final List<ProjectItem> cloudProjects = new ArrayList<>();
    private ProjectAdapter backupAdapter;
    private ProjectAdapter restoreAdapter;

    private volatile boolean isOperationCancelled = false;

    private final ActivityResultLauncher<Intent> googleSignInLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    try {
                        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult();
                        SketchwareUtil.toast("Signed in as " + account.getEmail());
                        onAccountUpdated(account);
                    } catch (Exception e) {
                        showErrorDialog("Sign-In Error", e.getMessage());
                        onAccountUpdated(null);
                    }
                }
            });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCloudBackupManagerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        binding.topAppBar.setNavigationOnClickListener(v -> handleBack());

        binding.recyclerViewBackup.setLayoutManager(new LinearLayoutManager(this));
        backupAdapter = new ProjectAdapter(localProjects, this::updateBackupSelectionState);
        binding.recyclerViewBackup.setAdapter(backupAdapter);

        binding.recyclerViewRestore.setLayoutManager(new LinearLayoutManager(this));
        restoreAdapter = new ProjectAdapter(cloudProjects, this::updateRestoreSelectionState);
        binding.recyclerViewRestore.setAdapter(restoreAdapter);

        setupDashboardListeners();
        setupSearchAndFilterListeners();
        checkDisclaimer();
    }

    private void handleBack() {
        if (binding.viewFlipper.getDisplayedChild() != 0) {
            binding.viewFlipper.setDisplayedChild(0);
            binding.topAppBar.setTitle("Cloud Backup");
            refreshDashboardData();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
    }

    @Override
    protected void onResume() {
        super.onResume();
        onAccountUpdated(GoogleSignIn.getLastSignedInAccount(this));
    }

    private void checkDisclaimer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("disclaimer_accepted", false)) {
            showDisclaimerDialog(() -> prefs.edit().putBoolean("disclaimer_accepted", true).apply());
        }
    }

    private void onAccountUpdated(GoogleSignInAccount account) {
        currentAccount = account;
        if (account == null) {
            binding.textAccountEmail.setText("Not signed in");
            binding.buttonSignInOut.setText("Sign In with Google");
            binding.layoutDashboardStats.setVisibility(View.GONE);
            if (cloudManager != null) {
                cloudManager.shutdown();
            }
            cloudManager = null;
        } else {
            binding.textAccountEmail.setText("Signed in as: " + account.getEmail());
            binding.buttonSignInOut.setText("Disconnect Account");
            binding.layoutDashboardStats.setVisibility(View.VISIBLE);
            cloudManager = new CloudBackupManager(this, account);
            refreshDashboardData();
        }
    }

    private void refreshDashboardData() {
        if (cloudManager == null) return;
        binding.textCloudCount.setText("Loading...");
        cloudManager.getCloudBackupCount(new CloudBackupManager.CountCallback() {
            @Override
            public void onResult(int count) {
                binding.textCloudCount.setText(count + " backup" + (count == 1 ? "" : "s") + " found");
            }

            @Override
            public void onError(String error) {
                binding.textCloudCount.setText("Error loading count");
            }
        });

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int interval = prefs.getInt("auto_backup_interval", 0);
        String txt = interval == 0 ? "Off" : (interval == 1 ? "Daily" : (interval == 2 ? "Weekly" : "Monthly"));
        binding.textAutoBackupStatus.setText(txt);
    }

    private void setupDashboardListeners() {
        binding.buttonSignInOut.setOnClickListener(v -> {
            if (currentAccount == null) {
                googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent());
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Disconnect")
                        .setMessage("Revoke Google Drive access for Cloud Backup?")
                        .setPositiveButton("Disconnect", (d, w) -> {
                            mGoogleSignInClient.revokeAccess().addOnCompleteListener(task -> {
                                SketchwareUtil.toast("Disconnected");
                                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("auto_backup_interval", 0).apply();
                                WorkManager.getInstance(this).cancelUniqueWork("CloudAutoBackup_Recurring");
                                onAccountUpdated(null);
                            });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        binding.buttonManualBackup.setOnClickListener(v -> openManualBackup());
        binding.buttonRestoreBackup.setOnClickListener(v -> openRestoreBackup());
        binding.buttonAutoBackupSettings.setOnClickListener(v -> configureAutoBackup());
        binding.buttonViewDisclaimer.setOnClickListener(v -> showDisclaimerDialog(null));
    }

    private void setupSearchAndFilterListeners() {
        binding.editSearchBackup.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                backupAdapter.filter(s.toString());
                updateBackupSelectionState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.checkSelectAllBackup.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            for (ProjectItem item : backupAdapter.getDisplayedItems()) {
                item.isSelected = isChecked;
            }
            backupAdapter.notifyDataSetChanged();
            updateBackupSelectionState();
        });

        binding.buttonStartBackup.setOnClickListener(v -> executeManualBackupSequence());

        binding.editSearchRestore.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                restoreAdapter.filter(s.toString());
                updateRestoreSelectionState();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        binding.checkSelectAllRestore.setOnCheckedChangeListener((btn, isChecked) -> {
            if (!btn.isPressed()) return;
            for (ProjectItem item : restoreAdapter.getDisplayedItems()) {
                item.isSelected = isChecked;
            }
            restoreAdapter.notifyDataSetChanged();
            updateRestoreSelectionState();
        });

        binding.buttonStartRestore.setOnClickListener(v -> executeRestoreSequence());
    }

    private void updateBackupSelectionState() {
        int count = 0;
        for (ProjectItem item : backupAdapter.getDisplayedItems()) {
            if (item.isSelected) count++;
        }
        binding.checkSelectAllBackup.setChecked(count > 0 && count == backupAdapter.getDisplayedItems().size());
        binding.buttonStartBackup.setText("Backup " + count + " selected");
        binding.buttonStartBackup.setEnabled(count > 0);
    }

    private void updateRestoreSelectionState() {
        int count = 0;
        for (ProjectItem item : restoreAdapter.getDisplayedItems()) {
            if (item.isSelected) count++;
        }
        binding.checkSelectAllRestore.setChecked(count > 0 && count == restoreAdapter.getDisplayedItems().size());
        binding.buttonStartRestore.setText("Restore " + count + " selected");
        binding.buttonStartRestore.setEnabled(count > 0);
    }

    private void openManualBackup() {
        binding.viewFlipper.setDisplayedChild(1);
        binding.topAppBar.setTitle("Select for Backup");
        binding.progressBackupLoading.setVisibility(View.VISIBLE);
        binding.recyclerViewBackup.setVisibility(View.GONE);

        new Thread(() -> {
            ArrayList<HashMap<String, Object>> raw = lC.a();
            List<ProjectItem> items = new ArrayList<>();
            if (raw != null) {
                for (HashMap<String, Object> map : raw) {
                    ProjectItem item = new ProjectItem();
                    item.id = (String) map.get("sc_id");
                    item.name = (String) map.get("my_app_name");
                    item.pkg = (String) map.get("my_sc_pkg_name");
                    item.version = "v" + map.get("sc_ver_name");
                    
                    String path = new pro.sketchware.utility.FilePathUtil().getProjectDir() + File.separator + item.id + File.separator + "project";
                    File f = new File(path);
                    if (f.exists()) {
                        long diff = System.currentTimeMillis() - f.lastModified();
                        item.detail = "Last modified: " + (diff / 60000) + " mins ago";
                    }
                    items.add(item);
                }
            }
            runOnUiThread(() -> {
                localProjects.clear();
                localProjects.addAll(items);
                backupAdapter.setOriginalList(localProjects);
                binding.progressBackupLoading.setVisibility(View.GONE);
                binding.recyclerViewBackup.setVisibility(View.VISIBLE);
                updateBackupSelectionState();
            });
        }).start();
    }

    private void openRestoreBackup() {
        binding.viewFlipper.setDisplayedChild(2);
        binding.topAppBar.setTitle("Select for Restore");
        binding.progressRestoreLoading.setVisibility(View.VISIBLE);
        binding.recyclerViewRestore.setVisibility(View.GONE);

        if (cloudManager != null) {
            cloudManager.getCloudBackupsList(new CloudBackupManager.FileListCallback() {
                @Override
                public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                    List<ProjectItem> items = new ArrayList<>();
                    if (files != null) {
                        for (com.google.api.services.drive.model.File f : files) {
                            ProjectItem item = new ProjectItem();
                            item.id = f.getId();
                            item.fileName = f.getName();
                            item.name = f.getProperties() != null ? f.getProperties().get("projectName") : f.getName();
                            if (item.name == null) item.name = f.getName();
                            
                            item.detail = "Cloud Backup • " + formatSize(f.getSize() != null ? f.getSize() : 0);
                            items.add(item);
                        }
                    }
                    localProjects.clear();
                    cloudProjects.clear();
                    cloudProjects.addAll(items);
                    restoreAdapter.setOriginalList(cloudProjects);
                    binding.progressRestoreLoading.setVisibility(View.GONE);
                    binding.recyclerViewRestore.setVisibility(View.VISIBLE);
                    updateRestoreSelectionState();
                }

                @Override
                public void onError(String error) {
                    binding.progressRestoreLoading.setVisibility(View.GONE);
                    showErrorDialog("Failed to fetch backups", error);
                }
            });
        }
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private void executeManualBackupSequence() {
        List<ProjectItem> toBackup = new ArrayList<>();
        for (ProjectItem item : backupAdapter.getDisplayedItems()) {
            if (item.isSelected) {
                toBackup.add(item);
            }
        }

        if (toBackup.isEmpty()) return;

        AlertDialog progress = createProgressDialog("Backing up " + toBackup.size() + " projects...");
        progress.show();
        isOperationCancelled = false;
        TextView tvProgress = progress.findViewById(android.R.id.message);

        new Thread(() -> {
            int success = 0;
            int failed = 0;
            for (ProjectItem p : toBackup) {
                if (isOperationCancelled) break;
                
                runOnUiThread(() -> {
                    if (tvProgress != null) {
                        tvProgress.setText("Creating .swb for " + p.name + "...");
                    }
                });
                
                CloudBackupFactory factory = new CloudBackupFactory(p.id);
                factory.backup(null, p.name);
                File swb = factory.getOutFile();
                
                if (swb != null && swb.exists()) {
                    runOnUiThread(() -> {
                        if (tvProgress != null) {
                            tvProgress.setText("Uploading " + p.name + " to Drive...");
                        }
                    });
                    
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] isSuccess = {false};
                    
                    cloudManager.uploadBackupToCloud(swb, p.name, new CloudBackupManager.BackupCallback() {
                        @Override 
                        public void onSuccess(String msg) { 
                            isSuccess[0] = true; 
                            latch.countDown(); 
                        }
                        @Override 
                        public void onError(String err) { 
                            latch.countDown(); 
                        }
                    });
                    try { 
                        latch.await(); 
                    } catch (Exception ignored) { }
                    
                    if (isSuccess[0]) {
                        success++;
                    } else {
                        failed++;
                    }
                } else {
                    failed++;
                }
            }
            
            FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir()); 
            
            final int fSuccess = success;
            final int fFailed = failed;
            runOnUiThread(() -> {
                progress.dismiss();
                showSummaryDialog("Backup Summary", fSuccess + " uploaded, " + fFailed + " failed." + (isOperationCancelled ? "\n(Operation cancelled)" : ""));
                updateBackupSelectionState();
            });
        }).start();
    }

    private void executeRestoreSequence() {
        List<ProjectItem> toRestore = new ArrayList<>();
        for (ProjectItem item : restoreAdapter.getDisplayedItems()) {
            if (item.isSelected) {
                toRestore.add(item);
            }
        }

        if (toRestore.isEmpty()) return;

        new MaterialAlertDialogBuilder(this)
            .setTitle("Restore Behavior")
            .setMessage("How do you want to restore these backups?")
            .setPositiveButton("Restore as New", (d, w) -> startRestore(toRestore, true, false))
            .setNegativeButton("Replace Existing", (d, w) -> {
                new MaterialAlertDialogBuilder(this)
                    .setTitle("Safety Copy")
                    .setMessage("Do you want to create a local safety backup of existing projects before replacing them?")
                    .setPositiveButton("Yes, create copy", (d2, w2) -> startRestore(toRestore, false, true))
                    .setNegativeButton("No, just replace", (d2, w2) -> startRestore(toRestore, false, false))
                    .show();
            })
            .setNeutralButton(R.string.common_word_cancel, null)
            .show();
    }

    private void startRestore(List<ProjectItem> toRestore, boolean asNew, boolean safetyCopy) {
        AlertDialog progress = createProgressDialog("Restoring " + toRestore.size() + " projects...");
        progress.show();
        isOperationCancelled = false;
        TextView tvProgress = progress.findViewById(android.R.id.message);
        
        String tempDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/.cloudbackup/temp_restore").getAbsolutePath();

        new Thread(() -> {
            int success = 0;
            int failed = 0;

            for (ProjectItem p : toRestore) {
                if (isOperationCancelled) break;
                
                runOnUiThread(() -> {
                    if (tvProgress != null) {
                        tvProgress.setText("Downloading " + p.name + "...");
                    }
                });

                CountDownLatch latch = new CountDownLatch(1);
                final boolean[] isSuccess = {false};
                final String[] downloadedPath = new String[1];

                cloudManager.downloadBackupFromCloud(p.id, p.fileName, tempDir, new CloudBackupManager.BackupCallback() {
                    @Override 
                    public void onSuccess(String msg) { 
                        isSuccess[0] = true; 
                        downloadedPath[0] = new File(tempDir, p.fileName).getAbsolutePath();
                        latch.countDown(); 
                    }
                    @Override 
                    public void onError(String err) { 
                        latch.countDown(); 
                    }
                });
                try { 
                    latch.await(); 
                } catch (Exception ignored) { }

                if (isSuccess[0]) {
                    runOnUiThread(() -> {
                        if (tvProgress != null) {
                            tvProgress.setText("Extracting " + p.name + "...");
                        }
                        try {
                            if (safetyCopy && !asNew) {
                                BackupFactory bf = new BackupFactory(p.id); 
                                bf.backup(null, p.name + "_SafetyBackup");
                            }
                            new BackupRestoreManager(CloudBackupManagerActivity.this, null).doRestore(downloadedPath[0], asNew);
                        } catch(Exception ignored) { }
                    });
                    success++;
                } else {
                    failed++;
                }
            }
            
            FileUtil.deleteFile(tempDir); 
            
            final int fSuccess = success;
            final int fFailed = failed;
            runOnUiThread(() -> {
                progress.dismiss();
                showSummaryDialog("Restore Summary", fSuccess + " restored, " + fFailed + " failed." + (isOperationCancelled ? "\n(Operation cancelled)" : ""));
                updateRestoreSelectionState();
            });
        }).start();
    }

    private void configureAutoBackup() {
        String[] intervals = {"Off (Manual Only)", "Daily", "Weekly", "Monthly"};
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentSelection = prefs.getInt("auto_backup_interval", 0);
        boolean wifiOnly = prefs.getBoolean("auto_backup_wifi_only", true);
        boolean chargingOnly = prefs.getBoolean("auto_backup_charging_only", false);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        MaterialSwitch swWifi = new MaterialSwitch(this);
        swWifi.setText("Require Wi-Fi");
        swWifi.setChecked(wifiOnly);
        layout.addView(swWifi);

        MaterialSwitch swCharging = new MaterialSwitch(this);
        swCharging.setText("Require Charging");
        swCharging.setChecked(chargingOnly);
        layout.addView(swCharging);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Auto-Backup Schedule")
            .setSingleChoiceItems(intervals, currentSelection, (dialog, which) -> {
                prefs.edit()
                    .putInt("auto_backup_interval", which)
                    .putBoolean("auto_backup_wifi_only", swWifi.isChecked())
                    .putBoolean("auto_backup_charging_only", swCharging.isChecked())
                    .apply();
                configureWorkManager(which, swWifi.isChecked(), swCharging.isChecked());
                dialog.dismiss();
                refreshDashboardData();
                SketchwareUtil.toast("Auto-backup set to: " + intervals[which]);
            })
            .setView(layout)
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void configureWorkManager(int intervalType, boolean wifiOnly, boolean chargingOnly) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (intervalType == 0) {
            workManager.cancelUniqueWork("CloudAutoBackup_Recurring");
        } else {
            long days = intervalType == 1 ? 1 : (intervalType == 2 ? 7 : 30);
            Constraints.Builder builder = new Constraints.Builder();
            if (wifiOnly) builder.setRequiredNetworkType(NetworkType.UNMETERED);
            if (chargingOnly) builder.setRequiresCharging(true);
            
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(AutoBackupWorker.class, days, TimeUnit.DAYS)
                .setConstraints(builder.build())
                .build();
            workManager.enqueueUniquePeriodicWork("CloudAutoBackup_Recurring", ExistingPeriodicWorkPolicy.UPDATE, request);
        }
    }

    private AlertDialog createProgressDialog(String message) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SketchwareUtil.dpToPx(24);
        container.setPadding(pad, pad, pad, pad);
        
        ProgressBar p = new ProgressBar(this);
        container.addView(p);
        
        TextView t = new TextView(this);
        t.setId(android.R.id.message);
        t.setText(message);
        t.setPadding(SketchwareUtil.dpToPx(16), 0, 0, 0);
        t.setTextAppearance(this, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        container.addView(t);

        return new MaterialAlertDialogBuilder(this)
            .setView(container)
            .setCancelable(false)
            .setNegativeButton("Cancel", (d, w) -> {
                isOperationCancelled = true;
                SketchwareUtil.toast("Cancelling safely...");
            })
            .create();
    }

    private void showSummaryDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private void showErrorDialog(String title, String errorMessage) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(errorMessage)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Error Log", errorMessage);
                if (clipboard != null) clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast("Copied to clipboard!");
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void showDisclaimerDialog(Runnable onAccept) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = SketchwareUtil.dpToPx(24);
        container.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Cloud Backup Policy");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        container.addView(title);

        TextView message = new TextView(this);
        message.setText("\nSecurely backup and sync your Sketchware Neo projects directly to your personal Google Drive.\n\n" +
                "• BYOK Structure: We do NOT host your backups on our servers. Your data is synced directly to your own Google Drive's hidden AppData folder.\n" +
                "• No Data Collection: Sketchware Neo contributors do not collect, view, or have access to your personal files, Google account, or backups.\n" +
                "• Liability: This tool is provided 'AS-IS'. The developers are not responsible for any data loss.");
        message.setTextSize(14f);
        container.addView(message);

        MaterialButton btn = new MaterialButton(this);
        btn.setText(onAccept == null ? "Close" : "Accept");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, SketchwareUtil.dpToPx(16), 0, 0);
        container.addView(btn, params);

        btn.setOnClickListener(v -> {
            if (onAccept != null) onAccept.run();
            bottomSheet.dismiss();
        });
        bottomSheet.show();
    }

    private static class ProjectItem {
        String id;
        String fileName;
        String name;
        String pkg;
        String version;
        String detail;
        boolean isSelected = false;
    }

    private static class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {
        private final List<ProjectItem> originalList = new ArrayList<>();
        private final List<ProjectItem> displayedList = new ArrayList<>();
        private final Runnable onSelectionChanged;

        public ProjectAdapter(List<ProjectItem> list, Runnable onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
            setOriginalList(list);
        }

        public void setOriginalList(List<ProjectItem> list) {
            originalList.clear();
            originalList.addAll(list);
            filter("");
        }

        public void filter(String query) {
            displayedList.clear();
            String q = query.toLowerCase(Locale.ROOT);
            for (ProjectItem item : originalList) {
                if (q.isEmpty() || (item.name != null && item.name.toLowerCase(Locale.ROOT).contains(q)) || 
                   (item.pkg != null && item.pkg.toLowerCase(Locale.ROOT).contains(q))) {
                    displayedList.add(item);
                }
            }
            notifyDataSetChanged();
        }

        public List<ProjectItem> getDisplayedItems() {
            return displayedList;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(ItemCloudProjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ProjectItem item = displayedList.get(position);
            holder.itemBinding.textTitle.setText(item.name);
            holder.itemBinding.textSubtitle.setText((item.pkg != null ? item.pkg : "Unknown Package") + (item.version != null ? " • " + item.version : ""));
            holder.itemBinding.textDetail.setText(item.detail != null ? item.detail : "");
            
            holder.itemBinding.checkboxSelected.setOnCheckedChangeListener(null);
            holder.itemBinding.checkboxSelected.setChecked(item.isSelected);
            holder.itemBinding.checkboxSelected.setOnCheckedChangeListener((btn, checked) -> {
                item.isSelected = checked;
                if (onSelectionChanged != null) {
                    onSelectionChanged.run();
                }
            });
            holder.itemView.setOnClickListener(v -> holder.itemBinding.checkboxSelected.setChecked(!item.isSelected));
        }

        @Override
        public int getItemCount() {
            return displayedList.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            ItemCloudProjectBinding itemBinding;

            VH(@NonNull ItemCloudProjectBinding binding) {
                super(binding.getRoot());
                this.itemBinding = binding;
            }
        }
    }
}
