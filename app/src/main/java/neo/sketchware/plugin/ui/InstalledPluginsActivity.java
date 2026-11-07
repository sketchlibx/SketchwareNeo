package neo.sketchware.plugin.ui;

import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;

import com.besome.sketch.lib.base.PaletteAwareAppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.sketchlibx.filepicker.FilePickerCallback;
import dev.sketchlibx.filepicker.FilePickerDialogFragment;
import dev.sketchlibx.filepicker.FilePickerOptions;
import neo.sketchware.plugin.PluginManager;
import pro.sketchware.utility.SketchwareUtil;

public class InstalledPluginsActivity extends PaletteAwareAppCompatActivity {

    private RecyclerView recyclerView;
    private PluginListAdapter adapter;
    private android.view.View emptyStateContainer;
    private TextView emptyStateText;

    private boolean isAscending = true;
    private final ExecutorService installExecutor = Executors.newSingleThreadExecutor();
    private AlertDialog installProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(pro.sketchware.R.layout.activity_manage_plugins);

        Toolbar toolbar = findViewById(pro.sketchware.R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setTitle("Installed Plugins");
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(pro.sketchware.R.id.recycler_plugins);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PluginListAdapter(PluginManager.getInstalledPluginInfos(this), this::confirmDelete, this::onToggle);
        recyclerView.setAdapter(adapter);

        emptyStateContainer = findViewById(pro.sketchware.R.id.empty_state_container);
        emptyStateText = findViewById(pro.sketchware.R.id.text_empty_state);
        emptyStateContainer.setOnClickListener(v -> showDiagnostics());

        TextInputEditText searchField = findViewById(pro.sketchware.R.id.edit_search);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        ExtendedFloatingActionButton fab = findViewById(pro.sketchware.R.id.fab_install_plugin);
        fab.setOnClickListener(v -> openPluginPicker());

        rescanAndRefresh();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(pro.sketchware.R.menu.menu_manage_plugins, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == pro.sketchware.R.id.action_view_diagnostics) {
            showDiagnostics();
            return true;
        } else if (id == pro.sketchware.R.id.action_sort) {
            isAscending = !isAscending;
            refresh();
            Snackbar.make(recyclerView, isAscending ? "Sorted A-Z" : "Sorted Z-A", Snackbar.LENGTH_SHORT).show();
            return true;
        } else if (id == pro.sketchware.R.id.action_browse_marketplace) {
            startActivity(new android.content.Intent(this, ManagePluginsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void rescanAndRefresh() {
        PluginManager.rescan(this);
        refresh();
    }

    private void refresh() {
        List<PluginManager.PluginInfo> infos = PluginManager.getInstalledPluginInfos(this);
        
        Collections.sort(infos, new Comparator<PluginManager.PluginInfo>() {
            @Override
            public int compare(PluginManager.PluginInfo p1, PluginManager.PluginInfo p2) {
                if (isAscending) {
                    return p1.pluginId().compareToIgnoreCase(p2.pluginId());
                } else {
                    return p2.pluginId().compareToIgnoreCase(p1.pluginId());
                }
            }
        });

        adapter.update(infos);

        boolean empty = infos.isEmpty();
        emptyStateContainer.setVisibility(empty ? android.view.View.VISIBLE : android.view.View.GONE);
        if (empty) {
            emptyStateText.setText(PluginManager.getLastLoadErrors().isEmpty()
                    ? "No plugins installed yet"
                    : "No plugins loaded - tap to see why");
        }
    }

    private void onToggle(PluginManager.PluginInfo info, boolean enabled) {
        PluginManager.setEnabled(this, info.pluginId(), enabled);
        refresh();
        Snackbar.make(recyclerView, info.pluginId() + (enabled ? " enabled" : " disabled"), Snackbar.LENGTH_SHORT).show();
    }

    private void showDiagnostics() {
        List<String> errors = PluginManager.getLastLoadErrors();
        String message = errors.isEmpty()
                ? "No load errors recorded."
                : String.join("\n\n", errors);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Plugin Load Diagnostics")
                .setMessage(message)
                .setPositiveButton("Rescan", (d, w) -> rescanAndRefresh())
                .setNegativeButton("Close", null)
                .show();
    }

    private void openPluginPicker() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"jar", "apk"});
        options.setTitle("Select Plugin (.jar / .apk)");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                installPlugin(file);
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "plugin_file_picker");
    }

    private void installPlugin(File file) {
        if (file == null || isFinishing()) return;
        showInstallProgress();
        installExecutor.execute(() -> {
            PluginManager.PluginInstallResult result = PluginManager.installAndLoad(getApplicationContext(), file);
            runOnUiThread(() -> {
                dismissInstallProgress();
                if (isFinishing() || isDestroyed()) return;
                if (result.success()) {
                    refresh();
                    Snackbar.make(recyclerView, "Plugin \"" + result.pluginId() + "\" installed", Snackbar.LENGTH_LONG).show();
                } else {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Plugin installation failed")
                            .setMessage(result.error() == null ? "Unknown plugin error" : result.error())
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    private void showInstallProgress() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int pad = SketchwareUtil.dpToPx(20);
        row.setPadding(pad, pad, pad, pad);
        CircularProgressIndicator indicator = new CircularProgressIndicator(this);
        row.addView(indicator, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(32), SketchwareUtil.dpToPx(32)));
        TextView message = new TextView(this);
        message.setText("Validating and installing plugin…");
        message.setPadding(SketchwareUtil.dpToPx(16), 0, 0, 0);
        row.addView(message, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        installProgressDialog = new MaterialAlertDialogBuilder(this).setTitle("Install plugin").setView(row).setCancelable(false).create();
        installProgressDialog.show();
    }

    private void dismissInstallProgress() {
        if (installProgressDialog != null) {
            installProgressDialog.dismiss();
            installProgressDialog = null;
        }
    }

    @Override
    protected void onDestroy() {
        dismissInstallProgress();
        installExecutor.shutdownNow();
        super.onDestroy();
    }

    private void confirmDelete(PluginManager.PluginInfo info) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove Plugin")
                .setMessage("Remove \"" + info.pluginId() + "\"? This will unload it immediately and delete its file. This cannot be undone.")
                .setPositiveButton("Remove", (d, w) -> {
                    boolean deleted = PluginManager.deletePlugin(this, info.pluginId());
                    refresh();
                    Snackbar.make(recyclerView, deleted ? "Plugin removed" : "Plugin unloaded, but its file could not be deleted", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
