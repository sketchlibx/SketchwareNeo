package neo.sketchware.plugin.ui;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.List;

import dev.pranav.filepicker.FilePickerCallback;
import dev.pranav.filepicker.FilePickerDialogFragment;
import dev.pranav.filepicker.FilePickerOptions;
import neo.sketchware.plugin.PluginManager;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Embeds the plugin list/management UI directly as a tab inside DesignActivity
 * (alongside View / Event / Component), instead of requiring a separate
 * ManagePluginsActivity screen. Only added to the tab layout when
 * ConfigActivity.SETTING_SHOW_PLUGINS_TAB is enabled (beta, off by default).
 */
public class PluginsTabFragment extends Fragment {

    private RecyclerView recyclerView;
    private PluginListAdapter adapter;
    private View emptyStateContainer;
    private TextView emptyStateText;
    private LinearLayout searchContainer;
    private TextInputEditText searchField;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context context = requireContext();

        FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Inline search bar, hidden until the toolbar search icon is tapped
        searchContainer = new LinearLayout(context);
        searchContainer.setOrientation(LinearLayout.HORIZONTAL);
        searchContainer.setVisibility(View.GONE);
        int pad = SketchwareUtil.dpToPx(12);
        searchContainer.setPadding(pad, pad, pad, pad);

        TextInputLayout til = new TextInputLayout(context);
        til.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        til.setHint("Search plugins");
        searchField = new TextInputEditText(context);
        searchField.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurface));
        til.addView(searchField);
        searchContainer.addView(til, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(searchContainer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Plugin list
        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new PluginListAdapter(PluginManager.getInstalledPluginInfos(context), this::confirmDelete, this::onToggle);
        recyclerView.setAdapter(adapter);
        content.addView(recyclerView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // Empty state overlay
        emptyStateContainer = new LinearLayout(context);
        ((LinearLayout) emptyStateContainer).setOrientation(LinearLayout.VERTICAL);
        ((LinearLayout) emptyStateContainer).setGravity(Gravity.CENTER);
        emptyStateContainer.setClickable(true);
        emptyStateContainer.setFocusable(true);
        emptyStateContainer.setOnClickListener(v -> showDiagnostics());

        emptyStateText = new TextView(context);
        emptyStateText.setGravity(Gravity.CENTER);
        emptyStateText.setTextColor(ThemeUtils.getColor(context, R.attr.colorOnSurfaceVariant));
        int textPad = SketchwareUtil.dpToPx(32);
        emptyStateText.setPadding(textPad, textPad, textPad, textPad);
        ((LinearLayout) emptyStateContainer).addView(emptyStateText);

        root.addView(emptyStateContainer, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // Install FAB
        ExtendedFloatingActionButton fab = new ExtendedFloatingActionButton(context);
        fab.setText("Install Plugin");
        fab.setOnClickListener(v -> openPluginPicker());
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16));
        root.addView(fab, fabParams);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rescanAndRefresh();
    }

    /** Toggled from the toolbar search icon when this tab is active. */
    public void toggleSearchBar() {
        if (searchContainer == null || searchField == null) return;
        boolean show = searchContainer.getVisibility() != View.VISIBLE;
        searchContainer.setVisibility(show ? View.VISIBLE : View.GONE);
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (show) {
            searchField.requestFocus();
            if (imm != null) imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchField.setText("");
            if (imm != null) imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
    }

    /** Called when leaving this tab so the search bar doesn't stay open. */
    public void closeSearchBar() {
        if (searchContainer != null && searchContainer.getVisibility() == View.VISIBLE) {
            searchContainer.setVisibility(View.GONE);
            if (searchField != null) searchField.setText("");
        }
    }

    /** Called when this tab becomes selected, to pick up newly installed/removed plugins. */
    public void refreshPlugins() {
        if (isAdded()) rescanAndRefresh();
    }

    private void rescanAndRefresh() {
        if (!isAdded()) return;
        PluginManager.rescan(requireContext());
        refresh();
    }

    private void refresh() {
        if (!isAdded()) return;
        List<PluginManager.PluginInfo> infos = PluginManager.getInstalledPluginInfos(requireContext());
        adapter.update(infos);

        boolean empty = infos.isEmpty();
        emptyStateContainer.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) {
            emptyStateText.setText(PluginManager.getLastLoadErrors().isEmpty()
                    ? "No plugins installed yet"
                    : "No plugins loaded - tap to see why");
        }
    }

    private void onToggle(PluginManager.PluginInfo info, boolean enabled) {
        if (!isAdded()) return;
        PluginManager.setEnabled(requireContext(), info.pluginId(), enabled);
        refresh();
        Snackbar.make(recyclerView, info.pluginId() + (enabled ? " enabled" : " disabled"), Snackbar.LENGTH_SHORT).show();
    }

    private void showDiagnostics() {
        List<String> errors = PluginManager.getLastLoadErrors();
        String message = errors.isEmpty()
                ? "No load errors recorded."
                : String.join("\n\n", errors);

        new MaterialAlertDialogBuilder(requireContext())
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

        new FilePickerDialogFragment(options, callback).show(getParentFragmentManager(), "plugin_file_picker");
    }

    private void installPlugin(File file) {
        if (!isAdded()) return;
        PluginManager.PluginInstallResult result = PluginManager.installAndLoad(requireContext(), file);

        if (result.success()) {
            Snackbar.make(recyclerView, "Plugin \"" + result.pluginId() + "\" installed and loaded", Snackbar.LENGTH_LONG).show();
            refresh();
        } else {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Plugin Failed to Load")
                    .setMessage(result.error())
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void confirmDelete(PluginManager.PluginInfo info) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Remove Plugin")
                .setMessage("Remove \"" + info.pluginId() + "\"? This will unload it immediately and delete its file. This cannot be undone.")
                .setPositiveButton("Remove", (d, w) -> {
                    if (!isAdded()) return;
                    boolean deleted = PluginManager.deletePlugin(requireContext(), info.pluginId());
                    refresh();
                    Snackbar.make(recyclerView, deleted ? "Plugin removed" : "Plugin unloaded, but its file could not be deleted", Snackbar.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
