package mod.hey.studios.shizuku;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import mod.jbk.util.LogUtil;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;
import rikka.shizuku.Shizuku;

public class ShizukuAccessActivity extends BaseAppCompatActivity {
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";
    private TextView statusView;
    private TextView detailView;
    private MaterialButton requestButton;

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::refreshStatus;
    private final Shizuku.OnBinderDeadListener binderDeadListener = this::refreshStatus;
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener = (requestCode, grantResult) -> {
        if (requestCode == 4107) refreshStatus();
    };

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    public void onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        super.onDestroy();
    }

    private android.view.View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("Shizuku Features");
        toolbar.setNavigationIcon(R.drawable.ic_mtrl_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.addView(body, new LinearLayout.LayoutParams(-1, -1));

        MaterialCardView card = new MaterialCardView(this, null,
                com.google.android.material.R.attr.materialCardViewFilledStyle);
        card.setRadius(dp(22));
        card.setCardElevation(0);
        card.setCardBackgroundColor(ThemeUtils.getColor(this,
                com.google.android.material.R.attr.colorSurfaceContainer));
        LinearLayout statusContent = new LinearLayout(this);
        statusContent.setOrientation(LinearLayout.VERTICAL);
        statusContent.setPadding(dp(20), dp(20), dp(20), dp(20));
        statusView = new TextView(this);
        statusView.setTextSize(18);
        statusView.setTypeface(null, android.graphics.Typeface.BOLD);
        statusView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        statusContent.addView(statusView);
        detailView = new TextView(this);
        detailView.setTextSize(14);
        detailView.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        detailView.setPadding(0, dp(8), 0, 0);
        statusContent.addView(detailView);
        card.addView(statusContent);
        body.addView(card);

        TextView description = new TextView(this);
        description.setText("Shizuku lets Sketchware Neo install and launch built apps using Shizuku or Sui. Start Shizuku first, then grant permission here. ADB mode and root mode can have different access.");
        description.setTextSize(14);
        description.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        description.setPadding(dp(4), dp(18), dp(4), dp(16));
        body.addView(description);

        requestButton = new MaterialButton(this);
        requestButton.setText("Grant permission");
        requestButton.setOnClickListener(v -> requestPermission());
        body.addView(requestButton, new LinearLayout.LayoutParams(-1, dp(52)));

        MaterialButton openButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        openButton.setText("Open or install Shizuku");
        openButton.setOnClickListener(v -> openShizuku());
        LinearLayout.LayoutParams openParams = new LinearLayout.LayoutParams(-1, dp(52));
        openParams.topMargin = dp(10);
        body.addView(openButton, openParams);

        TextView footer = new TextView(this);
        footer.setText("If Shizuku is not running, start it from the Shizuku app. On non-rooted devices, wireless debugging setup may be required after a reboot.");
        footer.setTextSize(12);
        footer.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(4), dp(18), dp(4), dp(4));
        body.addView(footer);
        return root;
    }

    private void requestPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                refreshStatus();
                openShizuku();
                return;
            }
            if (Shizuku.isPreV11()) {
                detailView.setText("This Shizuku version is too old. Update Shizuku and try again.");
                return;
            }
            if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                refreshStatus();
                return;
            }
            Shizuku.requestPermission(4107);
        } catch (Exception | LinkageError error) {
            LogUtil.e("ShizukuAccess", "Permission request failed", error);
            detailView.setText("Could not request permission: " + error.getMessage());
        }
    }

    private void openShizuku() {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
            if (launchIntent != null) {
                startActivity(launchIntent);
            } else {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://shizuku.rikka.app/download/")));
            }
        } catch (Exception error) {
            detailView.setText("Could not open Shizuku: " + error.getMessage());
        }
    }

    private void refreshStatus() {
        runOnUiThread(() -> {
            boolean connected;
            boolean granted = false;
            int uid = -1;
            try {
                connected = Shizuku.pingBinder() && !Shizuku.isPreV11();
                if (connected) {
                    granted = Shizuku.checkSelfPermission()
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;
                    uid = Shizuku.getUid();
                }
            } catch (Exception | LinkageError error) {
                connected = false;
            }

            if (!connected) {
                statusView.setText("Shizuku is not running");
                detailView.setText("Open Shizuku or Sui and start its service.");
                requestButton.setEnabled(true);
                requestButton.setText("Start Shizuku");
            } else if (!granted) {
                statusView.setText("Permission needed");
                detailView.setText("Shizuku is connected. Grant access to continue.");
                requestButton.setEnabled(true);
                requestButton.setText("Grant permission");
            } else {
                statusView.setText("Connected and ready");
                detailView.setText(uid == 0 ? "Running with root access." :
                        uid == 2000 ? "Running with ADB shell access." : "Connected with UID " + uid + ".");
                requestButton.setEnabled(false);
                requestButton.setText("Permission granted");
            }
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
