package neo.sketchware.plugin.ui;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.List;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import neo.sketchware.plugin.PluginManager;
import pro.sketchware.utility.SketchwareUtil;

public class ManagePluginsActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private PluginListAdapter adapter;
    private android.view.View emptyStateContainer;
    private TextView emptyStateText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(pro.sketchware.R.layout.activity_manage_plugins);

        Toolbar toolbar = findViewById(pro.sketchware.R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

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
        if (item.getItemId() == pro.sketchware.R.id.action_view_diagnostics) {
            showDiagnostics();
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
        options.setExtensions(new String[]{"jar"});
        options.setTitle("Select Plugin (.jar)");

        FilePickerCallback callback = new FilePickerCallback() {
            @Override
            public void onFileSelected(File file) {
                installPlugin(file);
            }
        };

        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "plugin_file_picker");
    }

    private void installPlugin(File file) {
        PluginManager.PluginInstallResult result = PluginManager.installAndLoad(this, file);

        if (result.success()) {
            Snackbar.make(recyclerView, "Plugin \"" + result.pluginId() + "\" installed and loaded", Snackbar.LENGTH_LONG).show();
            refresh();
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Plugin Failed to Load")
                    .setMessage(result.error())
                    .setPositiveButton("OK", null)
                    .show();
        }
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
