package mod.sketchlibx.project.backup;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.WorkManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.bumptech.glide.Glide;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.Scope;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import a.a.a.lC;
import a.a.a.wq;
import a.a.a.yB;
import mod.hey.studios.project.backup.BackupFactory;
import pro.sketchware.R;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class CloudBackupManagerActivity extends BaseAppCompatActivity {

    private static final String PREFS_NAME = "cloud_backup_prefs";
    private static final String[] INTERVAL_LABELS = {"Off (Manual Only)", "Daily", "Weekly", "Monthly"};
    private static final int SORT_NAME = 0;
    private static final int SORT_NEWEST = 1;
    private static final int SORT_SIZE = 2;

    private ViewFlipper viewFlipper;
    private MaterialToolbar topAppBar;

    private ImageView imageAccountPhoto;
    private TextView textAccountInitial;
    private TextView textAccountName;
    private TextView textAccountEmail;
    private TextView textConnectionStatus;
    private MaterialCardView cardConnectionStatus;
    private View layoutDashboardStats;
    private TextView textCloudCount;
    private TextView textAutoBackupStatus;
    private MaterialSwitch switchAutoBackup;

    private LinearProgressIndicator progressStorage;
    private TextView textStorageInfo;
    private TextView textStorageBackups;
    private TextView textStorageOther;
    private TextView textStorageFree;
    private View btnRefreshStorage;

    private SelectionScreen backupScreen;
    private SelectionScreen restoreScreen;

    private GoogleSignInClient mGoogleSignInClient;
    private GoogleSignInAccount currentAccount;
    private CloudBackupManager cloudManager;

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
        setContentView(R.layout.activity_cloud_backup_manager);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        viewFlipper = findViewById(R.id.viewFlipper);
        topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setTitle("");
        topAppBar.setSubtitle("");
        topAppBar.setNavigationOnClickListener(v -> handleBack());
        topAppBar.inflateMenu(R.menu.cloud_backup_menu);
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.menu_backup_disclaimer) {
                showDisclaimerDialog(null);
                return true;
            }
            if (item.getItemId() == R.id.menu_backup_account && currentAccount != null) {
                showAccountDialog();
                return true;
            }
            return false;
        });

        imageAccountPhoto = findViewById(R.id.imageAccountPhoto);
        textAccountInitial = findViewById(R.id.textAccountInitial);
        textAccountName = findViewById(R.id.textAccountName);
        textAccountEmail = findViewById(R.id.textAccountEmail);
        textConnectionStatus = findViewById(R.id.textConnectionStatus);
        cardConnectionStatus = findViewById(R.id.cardConnectionStatus);
        layoutDashboardStats = findViewById(R.id.layoutDashboardStats);
        textCloudCount = findViewById(R.id.textCloudCount);
        textAutoBackupStatus = findViewById(R.id.textAutoBackupStatus);
        switchAutoBackup = findViewById(R.id.switchAutoBackup);

        progressStorage = findViewById(R.id.progressStorage);
        textStorageInfo = findViewById(R.id.textStorageInfo);
        textStorageBackups = findViewById(R.id.textStorageBackups);
        textStorageOther = findViewById(R.id.textStorageOther);
        textStorageFree = findViewById(R.id.textStorageFree);
        btnRefreshStorage = findViewById(R.id.btnRefreshStorage);

        backupScreen = new SelectionScreen(true);
        restoreScreen = new SelectionScreen(false);

        setupDashboardListeners();
        checkDisclaimer();
    }

    private void handleBack() {
        if (viewFlipper.getDisplayedChild() != 0) {
            viewFlipper.setDisplayedChild(0);
            topAppBar.setTitle("");
            topAppBar.setSubtitle("");
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
    public void onResume() {
        super.onResume();
        onAccountUpdated(GoogleSignIn.getLastSignedInAccount(this));
    }

    @Override
    public void onDestroy() {
        // Glide is lifecycle-aware and clears Activity requests automatically.
        // Calling Glide.with(Activity) from onDestroy can throw after the Activity is marked destroyed.
        if (cloudManager != null) {
            cloudManager.shutdown();
            cloudManager = null;
        }
        super.onDestroy();
    }

    private void checkDisclaimer() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean("disclaimer_accepted", false)) {
            showDisclaimerDialog(() -> prefs.edit().putBoolean("disclaimer_accepted", true).apply());
        }
    }

    private void onAccountUpdated(GoogleSignInAccount account) {
        if (isFinishing() || isDestroyed()) return;
        boolean sameAccount = account != null && currentAccount != null
                && account.getEmail() != null
                && account.getEmail().equals(currentAccount.getEmail())
                && cloudManager != null;
        currentAccount = account;

        if (account == null) {
            if (topAppBar != null && topAppBar.getMenu().findItem(R.id.menu_backup_account) != null) {
                topAppBar.getMenu().findItem(R.id.menu_backup_account).setVisible(false);
            }
            if (imageAccountPhoto != null) {
                Glide.with(imageAccountPhoto).clear(imageAccountPhoto);
                imageAccountPhoto.setVisibility(View.GONE);
            }
            textAccountInitial.setVisibility(View.VISIBLE);
            textAccountInitial.setText("G");
            textAccountName.setText("Not signed in");
            textAccountEmail.setText("Tap to sign in with Google");
            textConnectionStatus.setText("Sign in");
            applyStatusColors(false);
            layoutDashboardStats.setVisibility(View.GONE);
            if (cloudManager != null) cloudManager.shutdown();
            cloudManager = null;
            return;
        }

        if (topAppBar != null && topAppBar.getMenu().findItem(R.id.menu_backup_account) != null) {
            topAppBar.getMenu().findItem(R.id.menu_backup_account).setVisible(true);
        }
        String email = account.getEmail() != null ? account.getEmail() : "";
        String displayName = account.getDisplayName();
        if (displayName == null || displayName.trim().isEmpty()) {
            displayName = email.contains("@") ? email.substring(0, email.indexOf('@')) : "Google Account";
        }
        textAccountInitial.setText(displayName.substring(0, 1).toUpperCase(Locale.ROOT));
        Uri photoUri = account.getPhotoUrl();
        if (imageAccountPhoto != null && photoUri != null) {
            imageAccountPhoto.setVisibility(View.VISIBLE);
            textAccountInitial.setVisibility(View.GONE);
            Glide.with(imageAccountPhoto).load(photoUri).circleCrop()
                    .placeholder(R.drawable.ic_user).error(R.drawable.ic_user).into(imageAccountPhoto);
        } else if (imageAccountPhoto != null) {
            Glide.with(imageAccountPhoto).clear(imageAccountPhoto);
            imageAccountPhoto.setVisibility(View.GONE);
            textAccountInitial.setVisibility(View.VISIBLE);
        }
        textAccountName.setText(displayName);
        textAccountEmail.setText(email);
        textConnectionStatus.setText("Connected");
        applyStatusColors(true);
        layoutDashboardStats.setVisibility(View.VISIBLE);

        if (!sameAccount) {
            if (cloudManager != null) cloudManager.shutdown();
            cloudManager = new CloudBackupManager(this, account);
        }
        refreshDashboardData();
    }

    private void applyStatusColors(boolean connected) {
        int container = MaterialColors.getColor(cardConnectionStatus,
                connected ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainerHigh, Color.DKGRAY);
        int content = MaterialColors.getColor(cardConnectionStatus,
                connected ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant, Color.LTGRAY);
        cardConnectionStatus.setCardBackgroundColor(container);
        textConnectionStatus.setTextColor(content);
    }

    private void refreshDashboardData() {
        if (cloudManager == null) return;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int interval = prefs.getInt("auto_backup_interval", 0);
        textAutoBackupStatus.setText(interval == 0 ? "Off" : INTERVAL_LABELS[Math.min(interval, 3)]);
        switchAutoBackup.setChecked(interval > 0);

        fetchDriveStorageInfo();
    }

    private void fetchDriveStorageInfo() {
        if (cloudManager == null) return;
        progressStorage.setIndeterminate(true);
        textStorageInfo.setText("Calculating...");
        textCloudCount.setText("Loading...");

        cloudManager.getStorageInfo(new CloudBackupManager.StorageCallback() {
            @Override
            public void onResult(long limit, long usage, long backupBytes, int backupCount) {
                if (isFinishing() || isDestroyed()) return;
                progressStorage.setIndeterminate(false);
                textCloudCount.setText(backupCount + " backup" + (backupCount == 1 ? "" : "s") + " found in your Drive");
                textStorageBackups.setText(formatSize(backupBytes));
                textStorageOther.setText(formatSize(Math.max(0, usage - backupBytes)));

                if (limit > 0) {
                    progressStorage.setProgress((int) Math.min(100, (usage * 100) / limit));
                    textStorageInfo.setText(formatSize(usage) + " used of " + formatSize(limit));
                    textStorageFree.setText(formatSize(Math.max(0, limit - usage)));
                } else {
                    progressStorage.setProgress(0);
                    textStorageInfo.setText(formatSize(usage) + " used");
                    textStorageFree.setText("Unlimited");
                }
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                progressStorage.setIndeterminate(false);
                progressStorage.setProgress(0);
                textStorageInfo.setText("Storage unavailable");
                textCloudCount.setText("Fetch from Drive");
                textStorageBackups.setText("--");
                textStorageOther.setText("--");
                textStorageFree.setText("--");
            }
        });
    }

    private void setupDashboardListeners() {
        findViewById(R.id.cardGoogleAccount).setOnClickListener(v -> {
            if (currentAccount == null) {
                googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent());
            } else {
                showAccountDialog();
            }
        });

        findViewById(R.id.buttonManualBackup).setOnClickListener(v -> {
            if (requireSignedIn()) openManualBackup();
        });
        findViewById(R.id.buttonRestoreBackup).setOnClickListener(v -> {
            if (requireSignedIn()) openRestoreBackup();
        });
        findViewById(R.id.buttonAutoBackupSettings).setOnClickListener(v -> {
            if (requireSignedIn()) showFrequencyDialog();
        });
        findViewById(R.id.buttonBackupSettings).setOnClickListener(v -> {
            if (requireSignedIn()) showBackupConditionsDialog();
        });
        findViewById(R.id.buttonViewDisclaimer).setOnClickListener(v -> showDisclaimerDialog(null));
        btnRefreshStorage.setOnClickListener(v -> fetchDriveStorageInfo());

        switchAutoBackup.setOnClickListener(v -> {
            if (!requireSignedIn()) {
                switchAutoBackup.setChecked(false);
                return;
            }
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            if (switchAutoBackup.isChecked()) {
                int last = prefs.getInt("auto_backup_last_interval", 1);
                applyAutoBackupInterval(last > 0 ? last : 1);
            } else {
                applyAutoBackupInterval(0);
            }
        });
    }

    private boolean requireSignedIn() {
        if (currentAccount == null || cloudManager == null) {
            SketchwareUtil.toast("Sign in with Google first");
            return false;
        }
        return true;
    }

    private void showAccountDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(currentAccount.getEmail())
                .setMessage("Disconnecting revokes Google Drive access for Cloud Backup and turns off Auto Backup.")
                .setPositiveButton("Disconnect", (d, w) -> mGoogleSignInClient.revokeAccess().addOnCompleteListener(task -> {
                    SketchwareUtil.toast("Disconnected");
                    getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().putInt("auto_backup_interval", 0).apply();
                    WorkManager.getInstance(this).cancelUniqueWork(CloudBackupScheduler.WORK_NAME);
                    onAccountUpdated(null);
                }))
                .setNeutralButton("Switch Account", (d, w) -> mGoogleSignInClient.signOut().addOnCompleteListener(task -> {
                    onAccountUpdated(null);
                    googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent());
                }))
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        digitGroups = Math.max(0, Math.min(digitGroups, units.length - 1));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    private String formatDate(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(new Date(millis));
    }

    private String formatDateTime(long millis) {
        if (millis <= 0) return "";
        return new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(new Date(millis));
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String badgeOf(String versionName, String versionCode) {
        if (versionName == null || versionName.isEmpty()) return "";
        return "v" + versionName + (versionCode == null || versionCode.isEmpty() ? "" : " (" + versionCode + ")");
    }

    private void openManualBackup() {
        viewFlipper.setDisplayedChild(1);
        topAppBar.setTitle("Select Projects");
        topAppBar.setSubtitle("Choose projects to backup");
        backupScreen.setLoading(true);

        new Thread(() -> {
            ArrayList<HashMap<String, Object>> raw = lC.a();
            List<ProjectItem> items = new ArrayList<>();
            if (raw != null) {
                for (HashMap<String, Object> map : raw) {
                    if (map == null) continue;
                    ProjectItem item = new ProjectItem();
                    item.id = str(map, "sc_id");
                    if (item.id.isEmpty()) continue;

                    String appName = str(map, "my_app_name");
                    String workspace = str(map, "my_ws_name");
                    item.name = !appName.isEmpty() ? appName : (!workspace.isEmpty() ? workspace : "Untitled project");
                    item.pkg = str(map, "my_sc_pkg_name");
                    item.badge = badgeOf(str(map, "sc_ver_name"), str(map, "sc_ver_code"));

                    File projectFile = new File(wq.c(item.id), "project");
                    if (projectFile.exists()) item.time = projectFile.lastModified();
                    item.detail = item.time > 0 ? "Modified " + formatDate(item.time) : "";

                    try {
                        if (yB.a(map, "custom_icon")) {
                            File iconFile = new File(wq.e() + File.separator + item.id, "icon.png");
                            if (iconFile.isFile()) item.iconFile = iconFile;
                        }
                    } catch (Exception ignored) {
                    }
                    items.add(item);
                }
            }

            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                backupScreen.items.clear();
                backupScreen.items.addAll(items);
                backupScreen.setLoading(false);
                backupScreen.applyFilter();
                loadLastBackupTimes();
            });
        }).start();
    }

    private void loadLastBackupTimes() {
        if (cloudManager == null) return;
        cloudManager.getCloudBackupsList(new CloudBackupManager.FileListCallback() {
            @Override
            public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                if (isFinishing() || isDestroyed() || files == null) return;
                Map<String, Long> lastByPkg = new HashMap<>();
                for (com.google.api.services.drive.model.File f : files) {
                    Map<String, String> props = f.getProperties();
                    String pkg = props != null ? props.get(CloudBackupManager.PROP_PKG) : null;
                    if (pkg == null || pkg.isEmpty()) continue;
                    long time = cloudTimeOf(f);
                    Long previous = lastByPkg.get(pkg);
                    if (previous == null || time > previous) lastByPkg.put(pkg, time);
                }
                for (ProjectItem item : backupScreen.items) {
                    Long last = item.pkg == null ? null : lastByPkg.get(item.pkg);
                    if (last != null && last > 0) {
                        item.detail = "Last backup " + formatDate(last);
                    } else if (item.time > 0) {
                        item.detail = "Not backed up • Modified " + formatDate(item.time);
                    }
                }
                backupScreen.adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private static long cloudTimeOf(com.google.api.services.drive.model.File f) {
        if (f.getModifiedTime() != null) return f.getModifiedTime().getValue();
        if (f.getCreatedTime() != null) return f.getCreatedTime().getValue();
        return 0;
    }

    private void openRestoreBackup() {
        viewFlipper.setDisplayedChild(2);
        topAppBar.setTitle("Select Backups");
        topAppBar.setSubtitle("Choose backups to restore");
        restoreScreen.setLoading(true);

        cloudManager.getCloudBackupsList(new CloudBackupManager.FileListCallback() {
            @Override
            public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                if (isFinishing() || isDestroyed()) return;
                List<ProjectItem> items = new ArrayList<>();
                if (files != null) {
                    for (com.google.api.services.drive.model.File f : files) {
                        ProjectItem item = new ProjectItem();
                        item.id = f.getId();
                        item.fileName = f.getName();
                        item.isCloud = true;

                        Map<String, String> props = f.getProperties();
                        String propName = props != null ? props.get(CloudBackupManager.PROP_NAME) : null;
                        String baseName = f.getName() == null ? "Backup" : f.getName();
                        if (baseName.toLowerCase(Locale.ROOT).endsWith("." + CloudBackupFactory.EXTENSION)) {
                            baseName = baseName.substring(0, baseName.length() - CloudBackupFactory.EXTENSION.length() - 1);
                        }
                        item.name = propName != null && !propName.isEmpty() ? propName : baseName;
                        item.pkg = props != null ? props.get(CloudBackupManager.PROP_PKG) : null;
                        item.badge = badgeOf(props != null ? props.get(CloudBackupManager.PROP_VER_NAME) : null,
                                props != null ? props.get(CloudBackupManager.PROP_VER_CODE) : null);
                        item.size = f.getSize() != null ? f.getSize() : 0;
                        item.time = cloudTimeOf(f);
                        String when = formatDateTime(item.time);
                        item.detail = (when.isEmpty() ? "" : when + " • ") + formatSize(item.size);
                        items.add(item);
                    }
                }
                restoreScreen.items.clear();
                restoreScreen.items.addAll(items);
                restoreScreen.setLoading(false);
                restoreScreen.applyFilter();
            }

            @Override
            public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                restoreScreen.items.clear();
                restoreScreen.setLoading(false);
                restoreScreen.applyFilter();
                showErrorDialog("Failed to fetch backups", error);
            }
        });
    }

    private List<ProjectItem> selectedOf(SelectionScreen screen) {
        List<ProjectItem> selected = new ArrayList<>();
        for (ProjectItem item : screen.items) if (item.isSelected) selected.add(item);
        return selected;
    }

    private void executeManualBackupSequence() {
        List<ProjectItem> toBackup = selectedOf(backupScreen);
        if (toBackup.isEmpty() || !requireSignedIn()) return;

        ProgressHolder progress = createProgressDialog("Preparing backup...");
        progress.dialog.show();
        isOperationCancelled = false;
        final CloudBackupManager manager = cloudManager;
        final Context appContext = getApplicationContext();

        new Thread(() -> {
            int success = 0;
            List<String> failures = new ArrayList<>();
            int index = 0;

            for (ProjectItem p : toBackup) {
                if (isOperationCancelled) break;
                index++;
                final String label = index + "/" + toBackup.size() + "  " + p.name;
                runOnUiThread(() -> progress.text.setText("Creating backup\n" + label));

                CloudBackupFactory factory = new CloudBackupFactory(p.id);
                factory.backup(appContext, p.name);
                File swb = factory.getOutFile();

                if (swb == null || !swb.exists() || swb.length() == 0) {
                    failures.add(p.name + ": could not create backup file");
                    continue;
                }

                runOnUiThread(() -> progress.text.setText("Uploading to Drive\n" + label));

                CountDownLatch latch = new CountDownLatch(1);
                final boolean[] isSuccess = {false};
                final String[] errorHolder = {null};

                manager.uploadBackupToCloud(swb, p.name, new CloudBackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String msg) {
                        isSuccess[0] = true;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String err) {
                        errorHolder[0] = err;
                        latch.countDown();
                    }
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (isSuccess[0]) {
                    success++;
                } else {
                    failures.add(p.name + ": " + firstLine(errorHolder[0]));
                }
                swb.delete();
            }

            FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir());

            final int fSuccess = success;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                progress.dialog.dismiss();
                showSummaryDialog("Backup Summary", buildSummary(fSuccess, "uploaded", failures));
                for (ProjectItem item : backupScreen.items) item.isSelected = false;
                backupScreen.applyFilter();
                loadLastBackupTimes();
                fetchDriveStorageInfo();
            });
        }).start();
    }

    private String buildSummary(int success, String verb, List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append(success).append(" ").append(verb).append(", ").append(failures.size()).append(" failed.");
        if (isOperationCancelled) sb.append("\n(Operation cancelled)");
        if (!failures.isEmpty()) {
            sb.append("\n");
            for (String failure : failures) sb.append("\n• ").append(failure);
        }
        return sb.toString();
    }

    private static String firstLine(String text) {
        if (text == null || text.isEmpty()) return "unknown error";
        int end = text.indexOf('\n');
        String line = end > 0 ? text.substring(0, end) : text;
        return line.length() > 160 ? line.substring(0, 160) : line;
    }

    private void executeRestoreSequence() {
        List<ProjectItem> toRestore = selectedOf(restoreScreen);
        if (toRestore.isEmpty() || !requireSignedIn()) return;

        new MaterialAlertDialogBuilder(this)
                .setTitle("Restore Behavior")
                .setMessage("How do you want to restore these backups?")
                .setPositiveButton("Restore as New", (d, w) -> startRestore(toRestore, true, false))
                .setNegativeButton("Replace Existing", (d, w) -> new MaterialAlertDialogBuilder(this)
                        .setTitle("Safety Copy")
                        .setMessage("Do you want to create a local safety backup of existing projects before replacing them?")
                        .setPositiveButton("Yes, create copy", (d2, w2) -> startRestore(toRestore, false, true))
                        .setNegativeButton("No, just replace", (d2, w2) -> startRestore(toRestore, false, false))
                        .show())
                .setNeutralButton(R.string.common_word_cancel, null)
                .show();
    }

    private void startRestore(List<ProjectItem> toRestore, boolean asNew, boolean safetyCopy) {
        ProgressHolder progress = createProgressDialog("Preparing restore...");
        progress.dialog.show();
        isOperationCancelled = false;
        final CloudBackupManager manager = cloudManager;
        final Context appContext = getApplicationContext();
        final String tempDir = new File(Environment.getExternalStorageDirectory(), ".sketchware/.cloudbackup/temp_restore").getAbsolutePath();

        new Thread(() -> {
            int success = 0;
            List<String> failures = new ArrayList<>();
            int index = 0;

            for (ProjectItem p : toRestore) {
                if (isOperationCancelled) break;
                index++;
                final String label = index + "/" + toRestore.size() + "  " + p.name;
                runOnUiThread(() -> progress.text.setText("Downloading\n" + label));

                CountDownLatch latch = new CountDownLatch(1);
                final String[] downloadedPath = new String[1];
                final String[] errorHolder = new String[1];

                manager.downloadBackupFromCloud(p.id, p.fileName, tempDir, new CloudBackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String path) {
                        downloadedPath[0] = path;
                        latch.countDown();
                    }

                    @Override
                    public void onError(String err) {
                        errorHolder[0] = err;
                        latch.countDown();
                    }
                });

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                if (downloadedPath[0] == null) {
                    failures.add(p.name + ": " + firstLine(errorHolder[0]));
                    continue;
                }

                File downloaded = new File(downloadedPath[0]);
                runOnUiThread(() -> progress.text.setText("Restoring\n" + label));

                try {
                    String error = restoreOne(downloaded, p, asNew, safetyCopy, appContext);
                    if (error == null) {
                        success++;
                    } else {
                        failures.add(p.name + ": " + error);
                    }
                } catch (Exception e) {
                    failures.add(p.name + ": " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
                } finally {
                    downloaded.delete();
                }
            }

            FileUtil.deleteFile(tempDir);

            final int fSuccess = success;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                progress.dialog.dismiss();
                showSummaryDialog("Restore Summary", buildSummary(fSuccess, "restored", failures)
                        + (fSuccess > 0 ? "\n\nRefresh the project list to see restored projects." : ""));
                for (ProjectItem item : restoreScreen.items) item.isSelected = false;
                restoreScreen.applyFilter();
            });
        }).start();
    }

    private String restoreOne(File swb, ProjectItem p, boolean asNew, boolean safetyCopy, Context appContext) {
        String pkg = p.pkg;
        if (pkg == null || pkg.isEmpty()) {
            pkg = CloudBackupManager.readProjectMeta(swb).get(CloudBackupManager.PROP_PKG);
        }

        String targetId = null;
        String targetName = p.name;
        if (!asNew && pkg != null && !pkg.isEmpty()) {
            ArrayList<HashMap<String, Object>> locals = lC.a();
            if (locals != null) {
                for (HashMap<String, Object> local : locals) {
                    if (local != null && pkg.equals(str(local, "my_sc_pkg_name")) && !str(local, "sc_id").isEmpty()) {
                        targetId = str(local, "sc_id");
                        String localName = str(local, "my_app_name");
                        if (!localName.isEmpty()) targetName = localName;
                        break;
                    }
                }
            }
        }

        if (targetId != null && safetyCopy) {
            BackupFactory safety = new BackupFactory(targetId);
            safety.setBackupLocalLibs(false);
            safety.backup(appContext, targetName + "_Safety");
            if (safety.getOutFile() == null) {
                return "safety backup failed, project was not replaced";
            }
        }

        BackupFactory factory = new BackupFactory(targetId != null ? targetId : lC.b());
        factory.setBackupLocalLibs(true);
        factory.restore(swb);

        if (factory.isRestoreSuccess()) return null;
        String error = factory.getError();
        return error == null || error.isEmpty() ? "restore failed" : error;
    }

    private void showFrequencyDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int current = prefs.getInt("auto_backup_interval", 0);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Auto-Backup Frequency")
                .setSingleChoiceItems(INTERVAL_LABELS, Math.min(current, 3), (dialog, which) -> {
                    dialog.dismiss();
                    applyAutoBackupInterval(which);
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void applyAutoBackupInterval(int interval) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit().putInt("auto_backup_interval", interval);
        if (interval > 0) editor.putInt("auto_backup_last_interval", interval);
        editor.apply();

        configureWorkManager(interval);
        refreshDashboardData();
        SketchwareUtil.toast("Auto-backup set to: " + (interval == 0 ? "Off" : INTERVAL_LABELS[interval]));
    }

    private void showBackupConditionsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = SketchwareUtil.dpToPx(24);
        layout.setPadding(pad, SketchwareUtil.dpToPx(8), pad, 0);

        MaterialSwitch swWifi = new MaterialSwitch(this);
        swWifi.setText("Require Wi-Fi");
        swWifi.setChecked(prefs.getBoolean("auto_backup_wifi_only", true));
        layout.addView(swWifi);

        MaterialSwitch swCharging = new MaterialSwitch(this);
        swCharging.setText("Require Charging");
        swCharging.setChecked(prefs.getBoolean("auto_backup_charging_only", false));
        layout.addView(swCharging);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Backup Settings")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    prefs.edit()
                            .putBoolean("auto_backup_wifi_only", swWifi.isChecked())
                            .putBoolean("auto_backup_charging_only", swCharging.isChecked())
                            .apply();
                    configureWorkManager(prefs.getInt("auto_backup_interval", 0));
                    SketchwareUtil.toast("Settings saved");
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    private void configureWorkManager(int intervalType) {
        if (intervalType == 0) {
            CloudBackupScheduler.cancel(this);
        } else {
            long hours = intervalType == 1 ? 24 : (intervalType == 2 ? 168 : 720);
            CloudBackupScheduler.schedule(this, hours);
        }
    }

    private static class ProgressHolder {
        AlertDialog dialog;
        TextView text;
    }

    private ProgressHolder createProgressDialog(String message) {
        ProgressHolder holder = new ProgressHolder();

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setGravity(Gravity.CENTER_VERTICAL);
        int pad = SketchwareUtil.dpToPx(24);
        container.setPadding(pad, pad, pad, pad);

        com.google.android.material.progressindicator.CircularProgressIndicator indicator =
                new com.google.android.material.progressindicator.CircularProgressIndicator(this);
        indicator.setIndeterminate(true);
        container.addView(indicator);

        TextView t = new TextView(this);
        t.setText(message);
        t.setPadding(SketchwareUtil.dpToPx(16), 0, 0, 0);
        t.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        container.addView(t);

        holder.text = t;
        holder.dialog = new MaterialAlertDialogBuilder(this)
                .setView(container)
                .setCancelable(false)
                .setNegativeButton("Cancel", (d, w) -> {
                    isOperationCancelled = true;
                    SketchwareUtil.toast("Cancelling safely...");
                })
                .create();
        return holder;
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

        bottomSheet.setContentView(container);

        bottomSheet.setOnShowListener(dialog -> {
            View sheet = bottomSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        bottomSheet.show();
    }

    private static class ProjectItem {
        String id;
        String fileName;
        String name;
        String pkg;
        String badge;
        String detail;
        File iconFile;
        long size;
        long time;
        boolean isCloud = false;
        boolean isSelected = false;

        boolean matches(String query) {
            return contains(name, query) || contains(pkg, query) || contains(fileName, query) || contains(badge, query);
        }

        private static boolean contains(String value, String query) {
            return value != null && value.toLowerCase(Locale.ROOT).contains(query);
        }
    }

    private final class SelectionScreen {
        final boolean forBackup;
        final MaterialButtonToggleGroup toggle;
        final EditText search;
        final View filterButton;
        final TextView countText;
        final TextView selectedText;
        final TextView emptyText;
        final CheckBox selectAll;
        final RecyclerView recycler;
        final View progress;
        final MaterialButton startButton;
        final ProjectAdapter adapter;
        final List<ProjectItem> items = new ArrayList<>();
        boolean onlySelected = false;
        int sortMode;

        SelectionScreen(boolean forBackup) {
            this.forBackup = forBackup;
            toggle = findViewById(forBackup ? R.id.toggleBackupFilter : R.id.toggleRestoreFilter);
            search = findViewById(forBackup ? R.id.editSearchBackup : R.id.editSearchRestore);
            filterButton = findViewById(forBackup ? R.id.btnFilterBackup : R.id.btnFilterRestore);
            countText = findViewById(forBackup ? R.id.textCountBackup : R.id.textCountRestore);
            selectedText = findViewById(forBackup ? R.id.textSelectedBackup : R.id.textSelectedRestore);
            emptyText = findViewById(forBackup ? R.id.textEmptyBackup : R.id.textEmptyRestore);
            selectAll = findViewById(forBackup ? R.id.checkSelectAllBackup : R.id.checkSelectAllRestore);
            recycler = findViewById(forBackup ? R.id.recyclerViewBackup : R.id.recyclerViewRestore);
            progress = findViewById(forBackup ? R.id.progressBackupLoading : R.id.progressRestoreLoading);
            startButton = findViewById(forBackup ? R.id.buttonStartBackup : R.id.buttonStartRestore);
            sortMode = forBackup ? SORT_NAME : SORT_NEWEST;

            adapter = new ProjectAdapter(this::onSelectionChanged);
            recycler.setLayoutManager(new LinearLayoutManager(CloudBackupManagerActivity.this));
            recycler.setAdapter(adapter);

            final int selectedTabId = forBackup ? R.id.btnBackupTabSelected : R.id.btnRestoreTabSelected;
            toggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (!isChecked) return;
                onlySelected = checkedId == selectedTabId;
                applyFilter();
            });

            search.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    applyFilter();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });

            selectAll.setOnClickListener(v -> {
                boolean target = selectAll.isChecked();
                for (ProjectItem item : adapter.displayed) item.isSelected = target;
                adapter.notifyDataSetChanged();
                if (onlySelected) {
                    applyFilter();
                } else {
                    updateSummary();
                }
            });

            filterButton.setOnClickListener(v -> showSortDialog());
            startButton.setOnClickListener(v -> {
                if (forBackup) {
                    executeManualBackupSequence();
                } else {
                    executeRestoreSequence();
                }
            });

            updateSummary();
        }

        void setLoading(boolean loading) {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            recycler.setVisibility(loading ? View.GONE : View.VISIBLE);
            if (loading) emptyText.setVisibility(View.GONE);
        }

        void onSelectionChanged() {
            if (onlySelected) {
                recycler.post(this::applyFilter);
            } else {
                updateSummary();
            }
        }

        void applyFilter() {
            String q = search.getText() == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<ProjectItem> out = new ArrayList<>();
            for (ProjectItem item : items) {
                if (onlySelected && !item.isSelected) continue;
                if (!q.isEmpty() && !item.matches(q)) continue;
                out.add(item);
            }

            final int mode = sortMode;
            Collections.sort(out, (a, b) -> {
                if (mode == SORT_NEWEST) return Long.compare(b.time, a.time);
                if (mode == SORT_SIZE) return Long.compare(b.size, a.size);
                String an = a.name == null ? "" : a.name;
                String bn = b.name == null ? "" : b.name;
                return an.compareToIgnoreCase(bn);
            });

            adapter.setDisplayed(out);
            updateSummary();
        }

        void updateSummary() {
            int selected = 0;
            for (ProjectItem item : items) if (item.isSelected) selected++;

            boolean allDisplayedSelected = !adapter.displayed.isEmpty();
            for (ProjectItem item : adapter.displayed) {
                if (!item.isSelected) {
                    allDisplayedSelected = false;
                    break;
                }
            }

            int total = items.size();
            countText.setText(total + (forBackup ? (total == 1 ? " project" : " projects") : (total == 1 ? " backup" : " backups")));
            selectedText.setText(selected + " selected");
            selectAll.setChecked(allDisplayedSelected);

            startButton.setEnabled(selected > 0);
            if (forBackup) {
                startButton.setText(selected > 0
                        ? "Backup " + selected + " selected project" + (selected == 1 ? "" : "s")
                        : "Select projects to backup");
            } else {
                startButton.setText(selected > 0
                        ? "Restore " + selected + " selected backup" + (selected == 1 ? "" : "s")
                        : "Select backups to restore");
            }

            boolean loading = progress.getVisibility() == View.VISIBLE;
            boolean empty = adapter.displayed.isEmpty() && !loading;
            emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                if (total == 0) {
                    emptyText.setText(forBackup ? "No projects found" : "No cloud backups found");
                } else if (onlySelected && search.getText().toString().trim().isEmpty()) {
                    emptyText.setText("Nothing selected yet");
                } else {
                    emptyText.setText("No matches found");
                }
            }
        }

        void showSortDialog() {
            final String[] options = forBackup
                    ? new String[]{"Name (A-Z)", "Recently modified"}
                    : new String[]{"Name (A-Z)", "Newest first", "Largest first"};
            int checked = Math.min(sortMode, options.length - 1);

            new MaterialAlertDialogBuilder(CloudBackupManagerActivity.this)
                    .setTitle("Sort by")
                    .setSingleChoiceItems(options, checked, (dialog, which) -> {
                        sortMode = which;
                        dialog.dismiss();
                        applyFilter();
                    })
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .show();
        }
    }

    private static class ProjectAdapter extends RecyclerView.Adapter<ProjectAdapter.VH> {
        final List<ProjectItem> displayed = new ArrayList<>();
        private final Runnable onSelectionChanged;

        ProjectAdapter(Runnable onSelectionChanged) {
            this.onSelectionChanged = onSelectionChanged;
        }

        void setDisplayed(List<ProjectItem> list) {
            displayed.clear();
            displayed.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cloud_project, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ProjectItem item = displayed.get(position);
            Context context = holder.itemView.getContext();

            holder.title.setText(item.name);
            holder.subtitle.setText(item.pkg != null && !item.pkg.isEmpty() ? item.pkg : (item.isCloud ? "Unknown Package" : ""));
            holder.subtitle.setVisibility(holder.subtitle.getText().length() == 0 ? View.GONE : View.VISIBLE);
            holder.detail.setText(item.detail != null ? item.detail : "");
            holder.detail.setVisibility(item.detail == null || item.detail.isEmpty() ? View.GONE : View.VISIBLE);
            holder.badge.setText(item.badge);
            holder.badgeContainer.setVisibility(item.badge == null || item.badge.isEmpty() ? View.GONE : View.VISIBLE);

            int primary = MaterialColors.getColor(holder.card, R.attr.colorPrimary, Color.BLUE);
            if (item.isCloud) {
                holder.icon.setImageResource(R.drawable.ic_mtrl_cloud);
                ImageViewCompat.setImageTintList(holder.icon, ColorStateList.valueOf(primary));
            } else {
                ImageViewCompat.setImageTintList(holder.icon, null);
                holder.icon.setImageResource(R.drawable.default_icon);
                if (item.iconFile != null) {
                    try {
                        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", item.iconFile);
                        holder.icon.setImageURI(uri);
                    } catch (Exception e) {
                        holder.icon.setImageResource(R.drawable.default_icon);
                    }
                }
            }

            holder.check.setChecked(item.isSelected);
            int stroke = MaterialColors.getColor(holder.card, R.attr.colorOutlineVariant, Color.GRAY);
            int normalBg = MaterialColors.getColor(holder.card, R.attr.colorSurfaceContainerLow, Color.DKGRAY);
            int selectedBg = MaterialColors.getColor(holder.card, R.attr.colorSecondaryContainer, normalBg);
            holder.card.setStrokeColor(item.isSelected ? primary : stroke);
            holder.card.setStrokeWidth(SketchwareUtil.dpToPx(item.isSelected ? 2 : 1));
            holder.card.setCardBackgroundColor(item.isSelected ? selectedBg : normalBg);

            holder.itemView.setOnClickListener(v -> {
                int current = holder.getBindingAdapterPosition();
                if (current == RecyclerView.NO_POSITION) return;
                ProjectItem chosen = displayed.get(current);
                chosen.isSelected = !chosen.isSelected;
                notifyItemChanged(current);
                if (onSelectionChanged != null) onSelectionChanged.run();
            });
        }

        @Override
        public int getItemCount() {
            return displayed.size();
        }

        static class VH extends RecyclerView.ViewHolder {
            final MaterialCardView card;
            final ImageView icon;
            final TextView title;
            final TextView subtitle;
            final TextView detail;
            final MaterialCardView badgeContainer;
            final TextView badge;
            final CheckBox check;

            VH(@NonNull View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cloud_item_card);
                icon = itemView.findViewById(R.id.cloud_item_icon);
                title = itemView.findViewById(R.id.cloud_item_title);
                subtitle = itemView.findViewById(R.id.cloud_item_subtitle);
                detail = itemView.findViewById(R.id.cloud_item_detail);
                badgeContainer = itemView.findViewById(R.id.cloud_item_badge_container);
                badge = itemView.findViewById(R.id.cloud_item_badge);
                check = itemView.findViewById(R.id.cloud_item_check);
            }
        }
    }
}
