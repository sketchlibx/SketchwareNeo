package neo.sketchware.plugin.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.besome.sketch.lib.base.PaletteAwareAppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.appbar.MaterialToolbar;

import org.json.JSONObject;

import dev.sketchlibx.filepicker.FilePickerCallback;
import dev.sketchlibx.filepicker.FilePickerDialogFragment;
import dev.sketchlibx.filepicker.FilePickerOptions;
import java.io.File;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import neo.sketchware.plugin.PluginManager;
import neo.sketchware.plugin.marketplace.PluginCatalogItem;
import neo.sketchware.plugin.marketplace.PluginMarketplaceRepository;
import neo.sketchware.plugin.marketplace.PluginVersionUtils;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;

public final class ManagePluginsActivity extends PaletteAwareAppCompatActivity implements PluginMarketplaceAdapter.Listener {

    private RecyclerView recyclerView;
    private PluginMarketplaceAdapter adapter;
    private ChipGroup categories;
    private TextView resultCount;
    private View stateContainer;
    private TextView stateTitle;
    private TextView stateMessage;
    private Button stateAction;
    private View progress;
    private String activeCategory = "All";
    private String query = "";
    private int sortMode = 0;
    private boolean loading = false;
    private boolean installing = false;
    private int catalogRequestId = 0;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<PluginCatalogItem> catalogItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_marketplace);
        MaterialToolbar toolbar = findViewById(R.id.marketplace_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.marketplace_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PluginMarketplaceAdapter(catalogItems, this);
        recyclerView.setAdapter(adapter);
        categories = findViewById(R.id.marketplace_categories);
        resultCount = findViewById(R.id.marketplace_result_count);
        stateContainer = findViewById(R.id.marketplace_state_container);
        stateTitle = findViewById(R.id.marketplace_state_title);
        stateMessage = findViewById(R.id.marketplace_state_message);
        stateAction = findViewById(R.id.marketplace_state_action);
        progress = findViewById(R.id.marketplace_progress);

        TextInputEditText search = findViewById(R.id.marketplace_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                applyFilter();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        findViewById(R.id.marketplace_sort).setOnClickListener(this::showSortMenu);
        stateAction.setOnClickListener(v -> runDefaultStateAction());
        View importPlugin = findViewById(R.id.marketplace_import_plugin);
        if (importPlugin != null) importPlugin.setOnClickListener(v -> openPluginPicker());
        View viewInstalled = findViewById(R.id.marketplace_view_installed);
        if (viewInstalled != null) viewInstalled.setOnClickListener(v -> startActivity(new Intent(this, InstalledPluginsActivity.class)));
        loadCatalog();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_plugin_marketplace, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_marketplace_refresh) {
            loadCatalog();
            return true;
        }
        if (item.getItemId() == R.id.action_marketplace_source) {
            showCatalogSourceDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadCatalog() {
        if (loading || installing) return;
        String url = PluginMarketplaceRepository.getCatalogUrl(this);
        if (url.isBlank()) {
            showState("Marketplace not configured", "No official plugin catalog is bundled with this source. Add a trusted HTTPS catalog URL to browse available extensions.", "Configure catalog", false);
            return;
        }
        loading = true;
        int requestId = ++catalogRequestId;
        showState("Loading catalog…", "Fetching and validating catalog metadata.", "", true);
        executor.execute(() -> {
            try {
                List<PluginCatalogItem> loaded = PluginMarketplaceRepository.fetchCatalog(getApplicationContext(), url);
                List<PluginManager.PluginInfo> installedSnapshot = PluginManager.getInstalledPluginInfos(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != catalogRequestId) return;
                    loading = false;
                    adapter.setInstalledPlugins(installedSnapshot);
                    catalogItems.clear();
                    catalogItems.addAll(loaded);
                    rebuildCategories();
                    sortItems();
                    applyFilter();
                    if (loaded.isEmpty()) showState("No extensions published", "The catalog is valid but currently contains no plugin entries.", "Refresh catalog", false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != catalogRequestId) return;
                    loading = false;
                    showState("Catalog unavailable", readableError(e), "Retry", false);
                });
            }
        });
    }

    private void showState(String title, String message, String action, boolean isLoading) {
        stateContainer.setVisibility(View.VISIBLE);
        stateTitle.setText(title);
        stateMessage.setText(message);
        stateAction.setText(action);
        stateAction.setVisibility(action == null || action.isBlank() ? View.GONE : View.VISIBLE);
        stateAction.setOnClickListener(v -> runDefaultStateAction());
        progress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        findViewById(R.id.marketplace_state_icon).setVisibility(isLoading ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(isLoading ? View.GONE : (catalogItems.isEmpty() ? View.GONE : View.VISIBLE));
    }

    private void rebuildCategories() {
        categories.removeAllViews();
        categories.setSingleSelection(true);
        categories.setSelectionRequired(true);
        List<String> names = new ArrayList<>();
        names.add("All");
        for (PluginCatalogItem item : catalogItems) {
            if (item.category() != null && !item.category().isBlank() && !names.contains(item.category())) names.add(item.category());
        }
        names.subList(1, names.size()).sort(String.CASE_INSENSITIVE_ORDER);
        if (!names.contains(activeCategory)) activeCategory = "All";
        for (String name : names) {
            Chip chip = new Chip(this);
            chip.setText(name);
            chip.setCheckable(true);
            chip.setClickable(true);
            chip.setId(View.generateViewId());
            chip.setOnClickListener(v -> {
                activeCategory = name;
                applyFilter();
            });
            categories.addView(chip);
            chip.setChecked(name.equalsIgnoreCase(activeCategory));
        }
    }

    private void applyFilter() {
        if (adapter == null) return;
        adapter.filter(query, activeCategory);
        resultCount.setText(adapter.getItemCount() + (adapter.getItemCount() == 1 ? " extension" : " extensions"));
        if (!catalogItems.isEmpty()) {
            stateContainer.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
            if (adapter.getItemCount() == 0) {
                stateTitle.setText("No matching extensions");
                stateMessage.setText("Try a different search term or category.");
                stateAction.setText("Clear filters");
                stateAction.setVisibility(View.VISIBLE);
                progress.setVisibility(View.GONE);
                stateAction.setOnClickListener(v -> {
                    activeCategory = "All";
                    query = "";
                    TextInputEditText search = findViewById(R.id.marketplace_search);
                    search.setText("");
                    rebuildCategories();
                    applyFilter();
                });
            }
            recyclerView.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void showSortMenu(View anchor) {
        String[] options = {"Name (A–Z)", "Name (Z–A)", "Rating", "Downloads", "Version"};
        new MaterialAlertDialogBuilder(this)
                .setTitle("Sort extensions")
                .setSingleChoiceItems(options, sortMode, (dialog, which) -> {
                    sortMode = which;
                    ((TextView) findViewById(R.id.marketplace_sort)).setText("Sort: " + options[which].replace("Name (A–Z)", "Name").replace("Name (Z–A)", "Name ↓"));
                    dialog.dismiss();
                    sortItems();
                    applyFilter();
                }).setNegativeButton("Cancel", null).show();
    }

    private void sortItems() {
        Comparator<PluginCatalogItem> comparator;
        switch (sortMode) {
            case 1 -> comparator = Comparator.comparing(PluginCatalogItem::name, String.CASE_INSENSITIVE_ORDER).reversed();
            case 2 -> comparator = (a, b) -> {
                boolean aMissing = a.rating() < 0;
                boolean bMissing = b.rating() < 0;
                if (aMissing != bMissing) return aMissing ? 1 : -1;
                return aMissing ? 0 : Double.compare(b.rating(), a.rating());
            };
            case 3 -> comparator = (a, b) -> {
                boolean aMissing = a.downloads() < 0;
                boolean bMissing = b.downloads() < 0;
                if (aMissing != bMissing) return aMissing ? 1 : -1;
                return aMissing ? 0 : Long.compare(b.downloads(), a.downloads());
            };
            case 4 -> comparator = (a, b) -> PluginVersionUtils.compareVersions(b.version(), a.version());
            default -> comparator = Comparator.comparing(PluginCatalogItem::name, String.CASE_INSENSITIVE_ORDER);
        }
        catalogItems.sort(comparator);
        adapter.setItems(catalogItems);
    }

    private void runDefaultStateAction() {
        if (PluginMarketplaceRepository.getCatalogUrl(this).isBlank()) showCatalogSourceDialog();
        else loadCatalog();
    }

    private void showCatalogSourceDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint("HTTPS catalog JSON URL");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText input = new TextInputEditText(inputLayout.getContext());
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(PluginMarketplaceRepository.getCatalogUrl(this));
        inputLayout.addView(input);
        int horizontal = (int) (24 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout wrap = new android.widget.FrameLayout(this);
        wrap.setPadding(horizontal, 8, horizontal, 0);
        wrap.addView(inputLayout);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Plugin catalog source")
                .setMessage("Use only a catalog you trust. The catalog must follow the schema documented in docs/plugin-system.md, and every downloadable archive must use HTTPS with a published SHA-256 digest.")
                .setView(wrap)
                .setPositiveButton("Save and load", (d, w) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    try {
                        PluginMarketplaceRepository.setCatalogUrl(this, value);
                        catalogRequestId++;
                        catalogItems.clear();
                        adapter.setItems(catalogItems);
                        activeCategory = "All";
                        if (!installing) {
                            loading = false;
                            loadCatalog();
                        }
                    } catch (Exception e) {
                        new MaterialAlertDialogBuilder(this).setTitle("Invalid catalog URL").setMessage(readableError(e)).setPositiveButton("OK", null).show();
                    }
                })
                .setNeutralButton("Clear", (d, w) -> {
                    PluginMarketplaceRepository.setCatalogUrl(this, "");
                    catalogRequestId++;
                    if (!installing) loading = false;
                    catalogItems.clear();
                    adapter.setItems(catalogItems);
                    showState("Marketplace not configured", "Add a trusted HTTPS catalog URL to browse available extensions.", "Configure catalog", false);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onOpenDetails(PluginCatalogItem item) {
        try {
            Intent intent = new Intent(this, PluginDetailsActivity.class);
            intent.putExtra(PluginDetailsActivity.EXTRA_CATALOG_JSON, item.toJson().toString());
            startActivity(intent);
        } catch (Exception e) {
            Snackbar.make(recyclerView, "Cannot open extension details: " + readableError(e), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onInstallOrUpdate(PluginCatalogItem item) {
        boolean installed = PluginManager.getInstalledPluginInfo(this, item.pluginId()) != null;
        new MaterialAlertDialogBuilder(this)
                .setTitle(installed ? "Update extension?" : "Install extension?")
                .setMessage("“" + item.name() + "” will be downloaded over HTTPS, verified against its published SHA-256 digest, and loaded into Sketchware Neo. Runtime plugins run inside the app process; only proceed if you trust this source.")
                .setPositiveButton(installed ? "Update" : "Install", (d, w) -> installItem(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void installItem(PluginCatalogItem item) {
        if (loading || installing || item == null) return;
        installing = true;
        loading = true;
        showState("Downloading " + item.name() + "…", "Verifying SHA-256 and validating the plugin before installation.", "", true);
        executor.execute(() -> {
            PluginManager.PluginInstallResult result = null;
            String failure = null;
            try {
                if (item.apiVersion() > PluginManager.getCurrentApiVersion()) throw new IllegalStateException("This extension requires plugin API v" + item.apiVersion());
                if (!item.minHostVersion().isBlank() && PluginVersionUtils.compareVersions(BuildConfig.VERSION_NAME, item.minHostVersion()) < 0) {
                    throw new IllegalStateException("This extension requires Sketchware Neo " + item.minHostVersion() + " or later");
                }
                java.io.File downloaded = PluginMarketplaceRepository.downloadAndVerify(getApplicationContext(), item);
                result = PluginManager.installAndLoad(getApplicationContext(), downloaded, item.pluginId(), item.apiVersion(), item.version());
                if (!result.success()) failure = result.error();
            } catch (Exception e) {
                failure = readableError(e);
            }
            PluginManager.PluginInstallResult finalResult = result;
            String finalFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                installing = false;
                loading = false;
                if (finalResult != null && finalResult.success() && (finalFailure == null || finalFailure.isBlank())) {
                    PluginMarketplaceRepository.recordInstalledVersion(this, item.pluginId(), item.version());
                    Snackbar.make(recyclerView, item.name() + " installed successfully", Snackbar.LENGTH_LONG).show();
                    loading = true;
                    showState("Refreshing catalog…", "Updating installed state.", "", true);
                    reloadCatalogAfterInstall();
                } else {
                    showState("Installation failed", finalFailure == null ? "Unknown installation error" : finalFailure, "Retry", false);
                    stateAction.setOnClickListener(v -> installItem(item));
                }
            });
        });
    }

    private void reloadCatalogAfterInstall() {
        String url = PluginMarketplaceRepository.getCatalogUrl(this);
        int requestId = ++catalogRequestId;
        executor.execute(() -> {
            try {
                List<PluginCatalogItem> loaded = PluginMarketplaceRepository.fetchCatalog(getApplicationContext(), url);
                List<PluginManager.PluginInfo> installedSnapshot = PluginManager.getInstalledPluginInfos(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || requestId != catalogRequestId) return;
                    loading = false;
                    adapter.setInstalledPlugins(installedSnapshot);
                    catalogItems.clear();
                    catalogItems.addAll(loaded);
                    rebuildCategories();
                    sortItems();
                    applyFilter();
                    if (loaded.isEmpty()) {
                        showState("No extensions published", "The catalog is valid but currently contains no plugin entries.", "Refresh catalog", false);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed() && requestId == catalogRequestId) {
                        loading = false;
                        showState("Installed; catalog refresh failed", readableError(e), "Retry", false);
                    }
                });
            }
        });
    }

    private void openPluginPicker() {
        FilePickerOptions options = new FilePickerOptions();
        options.setExtensions(new String[]{"jar", "apk"});
        options.setTitle("Select Plugin (.jar / .apk)");
        FilePickerCallback callback = new FilePickerCallback() {
            @Override public void onFileSelected(File file) { installLocalPlugin(file); }
        };
        new FilePickerDialogFragment(options, callback).show(getSupportFragmentManager(), "plugin_local_import");
    }

    private void installLocalPlugin(File file) {
        if (file == null || !file.isFile() || isFinishing() || installing || loading) return;
        installing = true;
        loading = true;
        showState("Installing local plugin…", "Validating the archive and plugin manifest.", "", true);
        executor.execute(() -> {
            PluginManager.PluginInstallResult result = PluginManager.installAndLoad(getApplicationContext(), file);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                installing = false;
                loading = false;
                if (result != null && result.success()) {
                    Snackbar.make(recyclerView, "Plugin installed: " + result.pluginId(), Snackbar.LENGTH_LONG).show();
                    loadCatalog();
                } else {
                    showState("Plugin import failed", result == null || result.error() == null ? "The plugin could not be installed." : result.error(), "Retry", false);
                }
            });
        });
    }

    private static String readableError(Throwable error) {
        if (error == null) return "Unknown error";
        String message = error.getMessage();
        if (message == null || message.isBlank()) message = error.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
