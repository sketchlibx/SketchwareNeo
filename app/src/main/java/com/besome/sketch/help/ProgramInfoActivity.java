package com.besome.sketch.help;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.PropertyOneLineItem;
import com.besome.sketch.lib.ui.PropertyTwoLineItem;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import a.a.a.GB;
import a.a.a.bB;
import a.a.a.mB;
import a.a.a.wB;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.ProgramInfoBinding;
import pro.sketchware.utility.SketchwareUtil;

public class ProgramInfoActivity extends BaseAppCompatActivity {

    private static final int ITEM_SYSTEM_INFORMATION = 1;
    private static final int ITEM_DOCS_LOG = 4;
    private static final int ITEM_SOCIAL_NETWORK = 5;
    private static final int ITEM_DISCORD = 6;
    private static final int ITEM_TELEGRAM = 8;
    private static final int ITEM_OPEN_SOURCE_LICENSES = 15;
    private static final int ITEM_SUGGEST_IDEAS = 17;

    private ProgramInfoBinding binding;
    
    private long downloadID = -1;

    // Broadcast receiver to Auto-Install APK after Download completes
    private final BroadcastReceiver onDownloadComplete = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            if (downloadID == id) {
                DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                Uri apkUri = downloadManager.getUriForDownloadedFile(downloadID);
                if (apkUri != null) {
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    try {
                        startActivity(installIntent);
                    } catch (Exception e) {
                        SketchwareUtil.toastError("Failed to start installation: " + e.getMessage());
                    }
                }
            }
        }
    };

    private void addTwoLineItem(int key, int name, int description) {
        addTwoLineItem(key, Helper.getResString(name), Helper.getResString(description));
    }

    private void addTwoLineItem(int key, int name, int description, boolean hideDivider) {
        addTwoLineItem(key, Helper.getResString(name), Helper.getResString(description), hideDivider);
    }

    private void addTwoLineItem(int key, String name, String description) {
        addTwoLineItem(key, name, description, false);
    }

    private void addTwoLineItem(int key, String name, String description, boolean hideDivider) {
        PropertyTwoLineItem item = new PropertyTwoLineItem(this);
        item.setKey(key);
        item.setName(name);
        item.setDesc(description);
        item.setHideDivider(hideDivider);
        binding.content.addView(item);
        item.setOnClickListener(this::handleItem);
    }

    private void addSingleLineItem(int key, int name) {
        addSingleLineItem(key, Helper.getResString(name));
    }

    private void addSingleLineItem(int key, int name, boolean hideDivider) {
        addSingleLineItem(key, Helper.getResString(name), hideDivider);
    }

    private void addSingleLineItem(int key, String name) {
        addSingleLineItem(key, name, false);
    }

    private void addSingleLineItem(int key, String name, boolean hideDivider) {
        PropertyOneLineItem item = new PropertyOneLineItem(this);
        item.setKey(key);
        item.setName(name);
        item.setHideDivider(hideDivider);
        binding.content.addView(item);
        if (key == ITEM_SYSTEM_INFORMATION || key == ITEM_OPEN_SOURCE_LICENSES) {
            item.setOnClickListener(this::handleItem);
        }
    }

    private void resetDialog(View view) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        dialog.setTitle(Helper.getResString(R.string.program_information_reset_system_title));
        dialog.setIcon(R.drawable.rollback_96);
        View rootView = wB.a(this, R.layout.all_init_popup);
        RadioGroup radioGroup = rootView.findViewById(R.id.rg_type);
        ((RadioButton) rootView.findViewById(R.id.rb_all)).setText(Helper.getResString(R.string.program_information_reset_system_title_all_settings_data));
        ((RadioButton) rootView.findViewById(R.id.rb_only_config)).setText(Helper.getResString(R.string.program_information_reset_system_title_all_settings));
        dialog.setView(rootView);
        dialog.setPositiveButton(Helper.getResString(R.string.common_word_yes), (v, which) -> {
            if (!mB.a()) {
                int buttonId = radioGroup.getCheckedRadioButtonId();
                boolean resetOnlySettings = buttonId != R.id.rb_all;
                v.dismiss();
                setResult(RESULT_OK, getIntent().putExtra("onlyConfig", resetOnlySettings));
                finish();
            }
        });
        dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
        dialog.show();
    }

    private void handleItem(View v) {
        if (!mB.a()) {
            int key;
            if (v instanceof PropertyOneLineItem) {
                key = ((PropertyOneLineItem) v).getKey();
                switch (key) {
                    case ITEM_SYSTEM_INFORMATION -> toSystemInfoActivity();
                    case ITEM_OPEN_SOURCE_LICENSES -> {
                        if (!GB.h(getApplicationContext())) {
                            bB.a(getApplicationContext(), Helper.getResString(R.string.common_message_check_network), bB.TOAST_NORMAL).show();
                        } else {
                            toLicenseActivity();
                        }
                    }
                }
            }

            if (v instanceof PropertyTwoLineItem) {
                key = ((PropertyTwoLineItem) v).getKey();
                switch (key) {
                    case ITEM_DOCS_LOG -> openUrl(Helper.getResString(R.string.link_docs_url));
                    case ITEM_SUGGEST_IDEAS ->
                            openUrl(Helper.getResString(R.string.link_ideas_url));
                    case ITEM_TELEGRAM ->
                            openUrl(Helper.getResString(R.string.link_telegram_invite));
                    case ITEM_DISCORD -> openUrl(Helper.getResString(R.string.link_discord_invite));
                }
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ProgramInfoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(onDownloadComplete, filter);
        }

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.appVersion.setText(GB.e(getApplicationContext()));
        binding.btnReset.setOnClickListener(this::resetDialog);
        
        binding.btnUpgrade.setOnClickListener(v -> checkForUpdates());

        addTwoLineItem(ITEM_DOCS_LOG, R.string.program_information_title_docs, R.string.link_docs_url);
        addTwoLineItem(ITEM_SUGGEST_IDEAS, R.string.program_information_title_suggest_ideas, R.string.link_ideas_url);
        addSingleLineItem(ITEM_SOCIAL_NETWORK, R.string.title_community);
        addTwoLineItem(ITEM_TELEGRAM, R.string.title_telegram_community, R.string.link_telegram_invite);
        addSingleLineItem(ITEM_SYSTEM_INFORMATION, R.string.program_information_title_system_information);
        addSingleLineItem(ITEM_OPEN_SOURCE_LICENSES, R.string.program_information_title_open_source_license, true);
    }
    
    // Core Update Checking Logic using GitHub API
    private void checkForUpdates() {
        if (!GB.h(getApplicationContext())) {
            bB.a(getApplicationContext(), Helper.getResString(R.string.common_message_check_network), bB.TOAST_NORMAL).show();
            return;
        }

        androidx.appcompat.app.AlertDialog loadingDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Checking for updates")
                .setMessage("Please wait...")
                .setCancelable(false)
                .show();

        new Thread(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/sketchlibx/SketchwareNeo/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();

                    JSONObject json = new JSONObject(response.toString());
                    String latestVersion = json.getString("tag_name");
                    JSONArray assets = json.getJSONArray("assets");
                    
                    String tempDownloadUrl = null;
                    String tempFileName = "SketchwareNeo_Update.apk";
                    
                    if (assets.length() > 0) {
                        JSONObject asset = assets.getJSONObject(0);
                        tempDownloadUrl = asset.getString("browser_download_url");
                        tempFileName = asset.getString("name");
                    }

                    String currentVersion = GB.e(getApplicationContext());
                    
                    String curVerClean = currentVersion.replaceAll("[^0-9.]", "");
                    String latestVerClean = latestVersion.replaceAll("[^0-9.]", "");

                    final String finalDownloadUrl = tempDownloadUrl;
                    final String finalFileName = tempFileName;

                    runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        if (!curVerClean.equals(latestVerClean) && finalDownloadUrl != null) {
                            showUpdateDialog(latestVersion, finalDownloadUrl, finalFileName);
                        } else {
                            bB.a(getApplicationContext(), "You are using the latest version", bB.TOAST_NORMAL).show();
                        }
                    });
                } else {
                
                final int responseCode = conn.getResponseCode();
                
                    runOnUiThread(() -> {
                        loadingDialog.dismiss();
                        SketchwareUtil.toastError("Failed to check updates. Error code: " + responseCode);
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    loadingDialog.dismiss();
                    SketchwareUtil.toastError("Error checking updates: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showUpdateDialog(String latestVersion, String downloadUrl, String fileName) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Update Available!")
                .setMessage("A new version (" + latestVersion + ") is available. Do you want to download and install it?")
                .setPositiveButton("Download", (dialog, which) -> startDownload(downloadUrl, fileName))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Handles the actual Download to the Downloads directory
    private void startDownload(String downloadUrl, String fileName) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
            request.setTitle("Downloading " + fileName);
            request.setDescription("Downloading latest Sketchware Neo update...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            //  Auto-Install
            request.setMimeType("application/vnd.android.package-archive");

            DownloadManager downloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            downloadID = downloadManager.enqueue(request);
            
            bB.a(getApplicationContext(), "Downloading update. Check your notification panel.", bB.TOAST_NORMAL).show();
        } catch (Exception e) {
            SketchwareUtil.toastError("Failed to start download: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(onDownloadComplete);
        } catch (IllegalArgumentException e) {
            // Ignored if receiver wasn't fully registered
        }
    }

    private void toLicenseActivity() {
        Intent intent = new Intent(getApplicationContext(), LicenseActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void toSystemInfoActivity() {
        Intent intent = new Intent(getApplicationContext(), SystemInfoActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void openUrl(String url) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        startActivity(intent);
    }
}
