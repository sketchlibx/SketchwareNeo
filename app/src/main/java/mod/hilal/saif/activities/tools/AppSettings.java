package mod.hilal.saif.activities.tools;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.besome.sketch.editor.manage.library.LibraryCategoryView;
import com.besome.sketch.editor.manage.library.LibraryItemView;
import com.besome.sketch.help.SystemSettingActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.api.services.drive.DriveScopes;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import a.a.a.lC;
import dev.aldi.sayuti.editor.manage.ManageLocalLibraryActivity;
import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import dev.pranav.filepicker.SelectionMode;
import mod.alucard.tn.apksigner.ApkSigner;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.project.backup.BackupRestoreManager;
import mod.sketchlibx.project.backup.AutoBackupWorker;
import mod.sketchlibx.project.backup.CloudBackupManager;
import mod.sketchlibx.project.backup.CloudBackupFactory;
import mod.hey.studios.util.Helper;
import mod.khaled.logcat.LogReaderActivity;
import pro.sketchware.R;
import pro.sketchware.activities.editor.component.ManageCustomComponentActivity;
import pro.sketchware.activities.settings.SettingsActivity;
import pro.sketchware.databinding.ActivityAppSettingsBinding;
import pro.sketchware.databinding.DialogSelectApkToSignBinding;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;

public class AppSettings extends BaseAppCompatActivity {

    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private GoogleSignInClient mGoogleSignInClient;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        var binding = ActivityAppSettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Fix padding for edge-to-edge
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        binding.topAppBar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));

        googleSignInLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                try {
                    GoogleSignInAccount account = task.getResult(ApiException.class);
                    SketchwareUtil.toast("Signed in as " + account.getEmail());
                    showCloudDashboard(account);
                } catch (ApiException e) {
                    showErrorDialog("Sign-In Error", "Google Sign-In failed with code: " + e.getStatusCode() + "\n" + e.getMessage());
                }
            }
        });

        setupPreferences(binding.content);
    }

    private void setupPreferences(ViewGroup content) {
        var preferences = new ArrayList<LibraryCategoryView>();

        LibraryCategoryView managersCategory = new LibraryCategoryView(this);
        managersCategory.setTitle("Managers");
        preferences.add(managersCategory);

        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_block, "Block manager", "Manage your own blocks to use in Logic Editor", new ActivityLauncher(new Intent(getApplicationContext(), BlocksManager.class))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_pull_down, "Block selector menu manager", "Manage your own block selector menus", openSettingsActivity(SettingsActivity.BLOCK_SELECTOR_MANAGER_FRAGMENT)), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_component, "Component manager", "Manage your own components", new ActivityLauncher(new Intent(getApplicationContext(), ManageCustomComponentActivity.class))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_list, "Event manager", "Manage your own events", openSettingsActivity(SettingsActivity.EVENTS_MANAGER_FRAGMENT)), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_box, "Local library manager", "Manage and download local libraries", new ActivityLauncher(new Intent(getApplicationContext(), ManageLocalLibraryActivity.class), new Pair<>("sc_id", "system"))), true);
        managersCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_article, Helper.getResString(R.string.design_drawer_menu_title_logcat_reader), Helper.getResString(R.string.design_drawer_menu_subtitle_logcat_reader), new ActivityLauncher(new Intent(getApplicationContext(), LogReaderActivity.class))), false);

        LibraryCategoryView cloudCategory = new LibraryCategoryView(this);
        cloudCategory.setTitle("Cloud & Sync");
        preferences.add(cloudCategory);
        cloudCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_sync, "Cloud Backup Dashboard", "Backup and restore projects securely to Google Drive", v -> checkDisclaimerAndOpenCloud()), false);

        LibraryCategoryView generalCategory = new LibraryCategoryView(this);
        generalCategory.setTitle("General");
        preferences.add(generalCategory);

        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_settings_applications, "App settings", "Change general app settings", new ActivityLauncher(new Intent(getApplicationContext(), ConfigActivity.class))), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_palette, Helper.getResString(R.string.settings_appearance), Helper.getResString(R.string.settings_appearance_description), openSettingsActivity(SettingsActivity.SETTINGS_APPEARANCE_FRAGMENT)), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_folder, "Open working directory", "Open Sketchware Neo's directory and edit files in it", v -> openWorkingDirectory()), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_apk_document, "Sign an APK file with testkey", "Sign an already existing APK file with testkey and signature schemes up to V4", v -> signApkFileDialog()), true);
        generalCategory.addLibraryItem(createPreference(R.drawable.ic_mtrl_settings, Helper.getResString(R.string.main_drawer_title_system_settings), "Auto-save and vibrations", new ActivityLauncher(new Intent(getApplicationContext(), SystemSettingActivity.class))), false);

        preferences.forEach(content::addView);
    }

    private void checkDisclaimerAndOpenCloud() {
        SharedPreferences prefs = getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE);
        boolean isAccepted = prefs.getBoolean("disclaimer_accepted", false);

        if (!isAccepted) {
            showCloudDisclaimerDialog(() -> {
                prefs.edit().putBoolean("disclaimer_accepted", true).apply();
                processCloudBackupEntry();
            });
        } else {
            processCloudBackupEntry();
        }
    }

    // Modern BottomSheet Design for Cloud Disclaimer
    private void showCloudDisclaimerDialog(Runnable onAccepted) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = SketchwareUtil.dpToPx(24);
        container.setPadding(padding, padding, padding, padding);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_mtrl_sync);
        icon.setColorFilter(getResources().getColor(R.color.color_primary, getTheme()));
        container.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(48), SketchwareUtil.dpToPx(48)));

        TextView title = new TextView(this);
        title.setText("Cloud Backup Policy");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, SketchwareUtil.dpToPx(16), 0, SketchwareUtil.dpToPx(8));
        container.addView(title);

        TextView message = new TextView(this);
        message.setText("Securely backup and sync your Sketchware Neo projects directly to your personal Google Drive.\n\n" +
                "• BYOK Structure: We do NOT host your backups on our servers. Your data is synced directly to your own Google Drive's hidden AppData folder.\n" +
                "• No Data Collection: Sketchware Neo contributors do not collect, view, or have access to your personal files, Google account, or backups.\n" +
                "• Liability: This tool is provided 'AS-IS'. The developers are not responsible for any data loss, corruption, or legal issues regarding the content you backup.");
        message.setTextSize(14f);
        message.setLineSpacing(0, 1.2f);
        container.addView(message);

        Button acceptBtn = new Button(this);
        acceptBtn.setText("Accept (10s)");
        acceptBtn.setEnabled(false);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, SketchwareUtil.dpToPx(24), 0, 0);
        container.addView(acceptBtn, btnParams);

        bottomSheet.setContentView(container);
        bottomSheet.setCancelable(false);

        new CountDownTimer(10000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                acceptBtn.setText("Accept (" + (millisUntilFinished / 1000) + "s)");
            }

            @Override
            public void onFinish() {
                acceptBtn.setText("Accept & Continue");
                acceptBtn.setEnabled(true);
                acceptBtn.setOnClickListener(v -> {
                    bottomSheet.dismiss();
                    onAccepted.run();
                });
            }
        }.start();

        bottomSheet.show();
    }

    private void processCloudBackupEntry() {
        GoogleSignInAccount account = GoogleSignIn.getLastSignedInAccount(this);
        
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(new Scope(DriveScopes.DRIVE_APPDATA))
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        if (account == null) {
            new MaterialAlertDialogBuilder(this)
                .setTitle("Sign In Required")
                .setMessage("Please sign in with your Google Account to link your personal Google Drive for cloud backups.")
                .setPositiveButton("Sign In", (dialog, which) -> {
                    googleSignInLauncher.launch(mGoogleSignInClient.getSignInIntent());
                })
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
        } else {
            showCloudDashboard(account);
        }
    }

    // Modern BottomSheet Dashboard for Cloud Actions
    private void showCloudDashboard(GoogleSignInAccount account) {
        BottomSheetDialog bottomSheet = new BottomSheetDialog(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = SketchwareUtil.dpToPx(16);
        container.setPadding(padding, padding, padding, padding);

        TextView title = new TextView(this);
        title.setText("Cloud Sync Dashboard");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(padding, padding, padding, padding);
        container.addView(title);

        TextView emailDesc = new TextView(this);
        emailDesc.setText("Signed in as: " + account.getEmail());
        emailDesc.setTextSize(14f);
        emailDesc.setPadding(padding, 0, padding, SketchwareUtil.dpToPx(16));
        container.addView(emailDesc);

        container.addView(createDashboardAction(R.drawable.ic_mtrl_upload, "Manual Backup", "Upload selected projects to Cloud", v -> {
            bottomSheet.dismiss();
            triggerSelectiveCloudBackup(account);
        }));
        
        container.addView(createDashboardAction(R.drawable.ic_mtrl_download, "Restore Projects", "Download projects from Cloud", v -> {
            bottomSheet.dismiss();
            triggerSelectiveCloudRestore(account);
        }));

        container.addView(createDashboardAction(R.drawable.ic_mtrl_settings, "Auto-Backup Schedule", "Set daily, weekly or monthly backups", v -> {
            bottomSheet.dismiss();
            configureAutoBackupProjects();
        }));

        container.addView(createDashboardAction(R.drawable.ic_mtrl_exit, "Disconnect Account", "Revoke Google Drive access", v -> {
            bottomSheet.dismiss();
            mGoogleSignInClient.revokeAccess().addOnCompleteListener(task -> {
                SketchwareUtil.toast("Google Drive access revoked.");
                getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE).edit().putInt("auto_backup_interval", 0).apply();
                WorkManager.getInstance(this).cancelUniqueWork("CloudAutoBackup_Recurring");
            });
        }));

        bottomSheet.setContentView(container);
        bottomSheet.show();
    }

    private View createDashboardAction(int iconRes, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        
        // Android Standard Ripple Effect
        TypedValue outValue = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        row.setBackgroundResource(outValue.resourceId);
        
        int pad = SketchwareUtil.dpToPx(16);
        row.setPadding(pad, pad, pad, pad);
        row.setOnClickListener(listener);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(getResources().getColor(R.color.color_text_onSurfaceVariant, getTheme()));
        row.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        LinearLayout textContainer = new LinearLayout(this);
        textContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textParams.setMargins(SketchwareUtil.dpToPx(16), 0, 0, 0);
        row.addView(textContainer, textParams);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(16f);
        tvTitle.setTextColor(getResources().getColor(R.color.color_text_onSurface, getTheme()));
        textContainer.addView(tvTitle);

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText(subtitle);
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(getResources().getColor(R.color.color_text_onSurfaceVariant, getTheme()));
        textContainer.addView(tvSubtitle);

        return row;
    }

    private AlertDialog createPaddedProgressDialog(String title) {
        LinearLayout container = new LinearLayout(this);
        container.setGravity(Gravity.CENTER);
        int padding = SketchwareUtil.dpToPx(24);
        container.setPadding(padding, padding, padding, padding);

        ProgressBar progressBar = new ProgressBar(this);
        container.addView(progressBar);

        return new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(container)
                .setCancelable(false)
                .create();
    }

    // --- Rest of your original methods below, unchanged but perfectly integrated ---
    
    private void showErrorDialog(String title, String errorMessage) {
        new MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(errorMessage)
            .setPositiveButton("Copy Error", (dialog, which) -> {
                try {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("Error Log", errorMessage);
                    if (clipboard != null) clipboard.setPrimaryClip(clip);
                    SketchwareUtil.toast("Copied to clipboard!");
                } catch (Exception ignored) {
                    SketchwareUtil.toastError("Failed to copy");
                }
            })
            .setNegativeButton("Close", null)
            .show();
    }

    private void triggerSelectiveCloudBackup(GoogleSignInAccount account) {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            SketchwareUtil.toast("No local projects found.");
            return;
        }

        String[] projectNames = new String[projects.size()];
        boolean[] checkedItems = new boolean[projects.size()];
        
        for (int i = 0; i < projects.size(); i++) {
            projectNames[i] = (String) projects.get(i).get("my_app_name");
            checkedItems[i] = true;
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle("Select Projects to Backup")
            .setMultiChoiceItems(projectNames, checkedItems, (d, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Start Backup", (d, which) -> {
                ArrayList<HashMap<String, Object>> selectedProjects = new ArrayList<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) selectedProjects.add(projects.get(i));
                }
                if (selectedProjects.isEmpty()) {
                    SketchwareUtil.toast("No projects selected!");
                    return;
                }
                processManualBackupSequence(account, selectedProjects);
            })
            .setNeutralButton("Deselect All", null)
            .setNegativeButton(R.string.common_word_cancel, null)
            .create();

        dialog.setOnShowListener(d -> {
            Button neutralButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                boolean isAnyChecked = false;
                for (boolean isChecked : checkedItems) {
                    if (isChecked) { isAnyChecked = true; break; }
                }
                boolean newState = !isAnyChecked;
                for (int i = 0; i < checkedItems.length; i++) {
                    checkedItems[i] = newState;
                    dialog.getListView().setItemChecked(i, newState);
                }
                neutralButton.setText(newState ? "Deselect All" : "Select All");
            });
        });

        dialog.show();
    }

    private void processManualBackupSequence(GoogleSignInAccount account, ArrayList<HashMap<String, Object>> projectsToBackup) {
        AlertDialog progress = createPaddedProgressDialog("Uploading Backups...");
        progress.show();

        CloudBackupManager cloudManager = new CloudBackupManager(this, account);
        
        new Thread(() -> {
            int successCount = 0;
            for (HashMap<String, Object> proj : projectsToBackup) {
                String scId = (String) proj.get("sc_id");
                String projectName = (String) proj.get("my_app_name");

                CloudBackupFactory factory = new CloudBackupFactory(scId);
                factory.backup(null, projectName);
                
                java.io.File swbFile = factory.getOutFile();
                if (swbFile != null && swbFile.exists()) {
                    final Object lock = new Object();
                    cloudManager.uploadBackupToCloud(swbFile, projectName, new CloudBackupManager.BackupCallback() {
                        @Override public void onSuccess(String msg) { synchronized (lock) { lock.notify(); } }
                        @Override public void onError(String err) { synchronized (lock) { lock.notify(); } }
                    });
                    try { synchronized (lock) { lock.wait(); } successCount++; } catch (Exception ignored) {}
                }
            }
            FileUtil.deleteFile(CloudBackupFactory.getCloudBackupDir()); 
            
            int finalSuccessCount = successCount;
            runOnUiThread(() -> {
                progress.dismiss();
                SketchwareUtil.toast("Successfully backed up " + finalSuccessCount + "/" + projectsToBackup.size() + " projects!");
            });
        }).start();
    }

    private void triggerSelectiveCloudRestore(GoogleSignInAccount account) {
        AlertDialog loadingDialog = createPaddedProgressDialog("Fetching cloud backups...");
        loadingDialog.show();

        CloudBackupManager cloudManager = new CloudBackupManager(this, account);
        cloudManager.getCloudBackupsList(new CloudBackupManager.FileListCallback() {
            @Override
            public void onSuccess(List<com.google.api.services.drive.model.File> files) {
                loadingDialog.dismiss();
                if (files == null || files.isEmpty()) {
                    SketchwareUtil.toast("No backups found in Google Drive.");
                    return;
                }

                String[] fileNames = new String[files.size()];
                boolean[] checkedItems = new boolean[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    String pName = files.get(i).getProperties() != null ? files.get(i).getProperties().get("projectName") : null;
                    fileNames[i] = pName != null ? pName + " (" + files.get(i).getName() + ")" : files.get(i).getName();
                    checkedItems[i] = true;
                }

                AlertDialog restoreDialog = new MaterialAlertDialogBuilder(AppSettings.this)
                    .setTitle("Select Projects to Restore")
                    .setMultiChoiceItems(fileNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                    .setPositiveButton("Restore", (dialog, which) -> {
                        ArrayList<com.google.api.services.drive.model.File> toRestore = new ArrayList<>();
                        for (int i = 0; i < checkedItems.length; i++) if (checkedItems[i]) toRestore.add(files.get(i));
                        if (toRestore.isEmpty()) return;
                        processRestoreSequence(cloudManager, toRestore);
                    })
                    .setNeutralButton("Deselect All", null)
                    .setNegativeButton(R.string.common_word_cancel, null)
                    .create();
                    
                restoreDialog.setOnShowListener(d -> {
                    Button neutralButton = restoreDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    neutralButton.setOnClickListener(v -> {
                        boolean isAnyChecked = false;
                        for (boolean isChecked : checkedItems) {
                            if (isChecked) { isAnyChecked = true; break; }
                        }
                        boolean newState = !isAnyChecked;
                        for (int i = 0; i < checkedItems.length; i++) {
                            checkedItems[i] = newState;
                            restoreDialog.getListView().setItemChecked(i, newState);
                        }
                        neutralButton.setText(newState ? "Deselect All" : "Select All");
                    });
                });
                
                restoreDialog.show();
            }

            @Override
            public void onError(String error) {
                loadingDialog.dismiss();
                showErrorDialog("Fetch Error", error);
            }
        });
    }

    private void processRestoreSequence(CloudBackupManager cloudManager, List<com.google.api.services.drive.model.File> filesToRestore) {
        AlertDialog progress = createPaddedProgressDialog("Downloading & Restoring...");
        progress.show();

        String downloadPath = new java.io.File(Environment.getExternalStorageDirectory(), ".sketchware/.cloudbackup/temp_restore").getAbsolutePath();

        new Thread(() -> {
            for (com.google.api.services.drive.model.File driveFile : filesToRestore) {
                final Object lock = new Object();
                cloudManager.downloadBackupFromCloud(driveFile.getId(), driveFile.getName(), downloadPath, new CloudBackupManager.BackupCallback() {
                    @Override
                    public void onSuccess(String message) {
                        String fullPath = new java.io.File(downloadPath, driveFile.getName()).getAbsolutePath();
                        runOnUiThread(() -> new BackupRestoreManager(AppSettings.this, null).doRestore(fullPath, true));
                        synchronized (lock) { lock.notify(); }
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> showErrorDialog("Restore Failed", "File: " + driveFile.getName() + "\n" + error));
                        synchronized (lock) { lock.notify(); }
                    }
                });
                try { synchronized (lock) { lock.wait(); } } catch (Exception ignored) {}
            }
            FileUtil.deleteFile(downloadPath); 
            runOnUiThread(progress::dismiss);
        }).start();
    }

    private void configureAutoBackupProjects() {
        ArrayList<HashMap<String, Object>> projects = lC.a();
        if (projects == null || projects.isEmpty()) {
            SketchwareUtil.toast("No local projects found.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE);
        Set<String> selectedScIds = prefs.getStringSet("auto_backup_sc_ids", new HashSet<>());

        String[] projectNames = new String[projects.size()];
        String[] scIds = new String[projects.size()];
        boolean[] checkedItems = new boolean[projects.size()];

        for (int i = 0; i < projects.size(); i++) {
            scIds[i] = (String) projects.get(i).get("sc_id");
            projectNames[i] = (String) projects.get(i).get("my_app_name");
            checkedItems[i] = selectedScIds.isEmpty() || selectedScIds.contains(scIds[i]);
        }

        new MaterialAlertDialogBuilder(this)
            .setTitle("Select Projects for Auto-Backup")
            .setMultiChoiceItems(projectNames, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
            .setPositiveButton("Next", (dialog, which) -> {
                Set<String> newSelectedScIds = new HashSet<>();
                for (int i = 0; i < checkedItems.length; i++) {
                    if (checkedItems[i]) newSelectedScIds.add(scIds[i]);
                }
                prefs.edit().putStringSet("auto_backup_sc_ids", newSelectedScIds).apply();
                openAutoBackupSettings();
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void openAutoBackupSettings() {
        String[] intervals = {"Off (Manual Only)", "Daily", "Weekly", "Monthly"};
        SharedPreferences prefs = getSharedPreferences("cloud_backup_prefs", MODE_PRIVATE);
        int currentSelection = prefs.getInt("auto_backup_interval", 2);

        new MaterialAlertDialogBuilder(this)
            .setTitle("Auto-Backup Frequency")
            .setSingleChoiceItems(intervals, currentSelection, (dialog, which) -> {
                prefs.edit().putInt("auto_backup_interval", which).apply();
                configureWorkManager(which);
                dialog.dismiss();
                SketchwareUtil.toast("Auto-backup schedule updated to: " + intervals[which]);
            })
            .setNegativeButton(R.string.common_word_cancel, null)
            .show();
    }

    private void configureWorkManager(int intervalType) {
        WorkManager workManager = WorkManager.getInstance(this);
        if (intervalType == 0) {
            workManager.cancelUniqueWork("CloudAutoBackup_Recurring");
        } else {
            long days = switch (intervalType) {
                case 1 -> 1;
                case 2 -> 7;
                case 3 -> 30;
                default -> 7;
            };
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                AutoBackupWorker.class, days, TimeUnit.DAYS
            ).build();
            workManager.enqueueUniquePeriodicWork("CloudAutoBackup_Recurring", ExistingPeriodicWorkPolicy.KEEP, request);
        }
    }

    private View.OnClickListener openSettingsActivity(String fragmentTag) {
        return v -> {
            Intent intent = new Intent(v.getContext(), SettingsActivity.class);
            intent.putExtra(SettingsActivity.FRAGMENT_TAG_EXTRA, fragmentTag);
            v.getContext().startActivity(intent);
        };
    }

    private LibraryItemView createPreference(int icon, String title, String desc, View.OnClickListener listener) {
        LibraryItemView preference = new LibraryItemView(this);
        preference.enabled.setVisibility(View.GONE);
        preference.icon.setImageResource(icon);
        preference.title.setText(title);
        preference.description.setText(desc);
        preference.setOnClickListener(listener);
        return preference;
    }

    private void openWorkingDirectory() {
        FilePickerOptions options = new FilePickerOptions();
        options.setSelectionMode(SelectionMode.BOTH);
        options.setMultipleSelection(true);
        options.setTitle("Select an entry to modify");
        options.setInitialDirectory(getFilesDir().getParentFile().getAbsolutePath());

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFilesSelected(@NonNull List<? extends java.io.File> files) {
                boolean isDirectory = files.get(0).isDirectory();
                if (files.size() > 1 || isDirectory) {
                    new MaterialAlertDialogBuilder(AppSettings.this)
                            .setTitle("Select an action")
                            .setSingleChoiceItems(new String[]{"Delete"}, -1, (actionDialog, which) -> {
                                new MaterialAlertDialogBuilder(AppSettings.this)
                                        .setTitle("Delete " + (isDirectory ? "folder" : "file") + "?")
                                        .setMessage("Are you sure you want to delete this " + (isDirectory ? "folder" : "file") + " permanently? This cannot be undone.")
                                        .setPositiveButton(R.string.common_word_delete, (deleteConfirmationDialog, pressedButton) -> {
                                            for (java.io.File file : files) {
                                                FileUtil.deleteFile(file.getAbsolutePath());
                                                deleteConfirmationDialog.dismiss();
                                            }
                                        })
                                        .setNegativeButton(R.string.common_word_cancel, null)
                                        .show();
                                actionDialog.dismiss();
                            })
                            .show();
                } else {
                    new MaterialAlertDialogBuilder(AppSettings.this)
                            .setTitle("Select an action")
                            .setSingleChoiceItems(new String[]{"Edit", "Delete"}, -1, (actionDialog, which) -> {
                                switch (which) {
                                    case 0 -> {
                                        Intent intent = new Intent(getApplicationContext(), SrcCodeEditor.class);
                                        intent.putExtra("title", Uri.fromFile(files.get(0)).getLastPathSegment());
                                        intent.putExtra("content", files.get(0).getAbsolutePath());
                                        intent.putExtra("xml", "");
                                        startActivity(intent);
                                    }
                                    case 1 -> new MaterialAlertDialogBuilder(AppSettings.this)
                                            .setTitle("Delete file?")
                                            .setMessage("Are you sure you want to delete this file permanently? This cannot be undone.")
                                            .setPositiveButton(R.string.common_word_delete, (deleteDialog, pressedButton) ->
                                                    FileUtil.deleteFile(files.get(0).getAbsolutePath()))
                                            .setNegativeButton(R.string.common_word_cancel, null)
                                            .show();
                                }
                                actionDialog.dismiss();
                            })
                            .show();
                }
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "file_picker");
    }

    private void signApkFileDialog() {
        boolean[] isAPKSelected = {false};
        MaterialAlertDialogBuilder apkPathDialog = new MaterialAlertDialogBuilder(this);
        apkPathDialog.setTitle("Sign APK with testkey");

        DialogSelectApkToSignBinding binding = DialogSelectApkToSignBinding.inflate(getLayoutInflater());
        View testkey_root = binding.getRoot();

        int tvId = getResources().getIdentifier("tv_path", "id", getPackageName());
        if (tvId == 0) tvId = getResources().getIdentifier("apk_path", "id", getPackageName());
        if (tvId == 0) tvId = getResources().getIdentifier("tv_apk_path", "id", getPackageName());
        
        TextView foundTextView = null;
        if (tvId != 0) {
            foundTextView = testkey_root.findViewById(tvId);
        }
        
        if (foundTextView == null) {
            ArrayList<View> views = new ArrayList<>();
            views.add(testkey_root);
            while (!views.isEmpty()) {
                View v = views.remove(0);
                if (v instanceof TextView && !(v instanceof Button) && v.getId() != binding.selectFile.getId()) {
                    foundTextView = (TextView) v;
                    break;
                }
                if (v instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) v;
                    for (int i = 0; i < vg.getChildCount(); i++) views.add(vg.getChildAt(i));
                }
            }
        }
        
        final TextView apk_path_txt = foundTextView; 

        binding.selectFile.setOnClickListener(v -> {
            FilePickerOptions options = new FilePickerOptions();
            options.setExtensions(new String[]{"apk"});
            FilePickerCallback callback = new FilePickerCallback() {
                @Override
                public void onFileSelected(java.io.File file) {
                    isAPKSelected[0] = true;
                    if (apk_path_txt != null) {
                        apk_path_txt.setText(file.getAbsolutePath());
                    }
                }
            };
            FilePickerDialogFragment dialog = new FilePickerDialogFragment(options, callback);
            dialog.show(getSupportFragmentManager(), "file_picker");
        });

        apkPathDialog.setPositiveButton("Continue", (v, which) -> {
            if (!isAPKSelected[0]) {
                SketchwareUtil.toast("Please select an APK file to sign");
                return;
            }
            String input_apk_path = apk_path_txt != null ? Helper.getText(apk_path_txt) : "";
            String output_apk_file_name = Uri.fromFile(new java.io.File(input_apk_path)).getLastPathSegment();
            String output_apk_path = new java.io.File(Environment.getExternalStorageDirectory(),
                    "sketchware/signed_apk/" + output_apk_file_name).getAbsolutePath();

            if (new java.io.File(output_apk_path).exists()) {
                MaterialAlertDialogBuilder confirmOverwrite = new MaterialAlertDialogBuilder(this);
                confirmOverwrite.setIcon(R.drawable.color_save_as_new_96);
                confirmOverwrite.setTitle("File exists");
                confirmOverwrite.setMessage("An APK named " + output_apk_file_name + " already exists at /sketchware/signed_apk/.  Overwrite it?");

                confirmOverwrite.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
                confirmOverwrite.setPositiveButton("Overwrite", (view, which1) -> {
                    v.dismiss();
                    signApkFileWithDialog(input_apk_path, output_apk_path, true,
                            null, null, null, null);
                });
                confirmOverwrite.show();
            } else {
                signApkFileWithDialog(input_apk_path, output_apk_path, true,
                        null, null, null, null);
            }
        });

        apkPathDialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        apkPathDialog.setView(testkey_root);
        apkPathDialog.setCancelable(false);
        apkPathDialog.show();
    }

    private void signApkFileWithDialog(String inputApkPath, String outputApkPath, boolean useTestkey, String keyStorePath, String keyStorePassword, String keyStoreKeyAlias, String keyPassword) {
        View building_root = getLayoutInflater().inflate(R.layout.build_progress_msg_box, null, false);
        LinearLayout layout_quiz = building_root.findViewById(R.id.layout_quiz);
        TextView tv_progress = building_root.findViewById(R.id.tv_progress);

        ScrollView scroll_view = new ScrollView(this);
        TextView tv_log = new TextView(this);
        scroll_view.addView(tv_log);
        
        if (layout_quiz != null) {
            layout_quiz.addView(scroll_view);
        }

        if (tv_progress != null) {
            tv_progress.setText("Signing APK...");
        }

        AlertDialog building_dialog = new MaterialAlertDialogBuilder(this)
                .setView(building_root)
                .create();

        ApkSigner signer = new ApkSigner();
        new Thread() {
            @Override
            public void run() {
                super.run();

                ApkSigner.LogCallback callback = line -> runOnUiThread(() ->
                        tv_log.setText(Helper.getText(tv_log) + line));

                if (useTestkey) {
                    signer.signWithTestKey(inputApkPath, outputApkPath, callback);
                } else {
                    signer.signWithKeyStore(inputApkPath, outputApkPath,
                            keyStorePath, keyStorePassword, keyStoreKeyAlias, keyPassword, callback);
                }

                runOnUiThread(() -> {
                    if (ApkSigner.LogCallback.errorCount.get() == 0) {
                        building_dialog.dismiss();
                        SketchwareUtil.toast("Successfully saved signed APK to: /Internal storage/sketchware/signed_apk/"
                                        + Uri.fromFile(new java.io.File(outputApkPath)).getLastPathSegment());
                    } else {
                        if (tv_progress != null) {
                            tv_progress.setText("An error occurred. Check the log for more details.");
                        }
                    }
                });
            }
        }.start();

        building_dialog.show();
    }

    private class ActivityLauncher implements View.OnClickListener {
        private final Intent launchIntent;
        private Pair<String, String> optionalExtra;

        ActivityLauncher(Intent launchIntent) {
            this.launchIntent = launchIntent;
        }

        ActivityLauncher(Intent launchIntent, Pair<String, String> optionalExtra) {
            this(launchIntent);
            this.optionalExtra = optionalExtra;
        }

        @Override
        public void onClick(View v) {
            if (optionalExtra != null) {
                launchIntent.putExtra(optionalExtra.first, optionalExtra.second);
            }
            startActivity(launchIntent);
        }
    }
}
