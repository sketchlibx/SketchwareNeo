package neo.sketchware.plugin.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;

import androidx.annotation.Nullable;
import com.besome.sketch.lib.base.PaletteAwareAppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import a.a.a.lC;
import a.a.a.yB;
import a.a.a.jC;
import com.besome.sketch.design.DesignActivity;
import mod.hey.studios.project.ProjectTracker;
import neo.sketchware.plugin.NeoEditorAction;
import neo.sketchware.plugin.PluginManager;
import neo.sketchware.plugin.marketplace.PluginCatalogItem;
import neo.sketchware.plugin.marketplace.PluginMarketplaceRepository;
import neo.sketchware.plugin.marketplace.PluginVersionUtils;
import pro.sketchware.BuildConfig;
import pro.sketchware.R;

public final class PluginDetailsActivity extends PaletteAwareAppCompatActivity {
    public static final String EXTRA_PLUGIN_ID = "plugin_id";
    public static final String EXTRA_CATALOG_JSON = "catalog_item_json";

    private MaterialToolbar toolbar;
    private ImageView icon;
    private TextView name;
    private TextView developer;
    private TextView version;
    private TextView pluginIdText;
    private ChipGroup badges;
    private LinearLayout sections;
    private LinearProgressIndicator progress;
    private MaterialButton primaryAction;
    private MaterialButton toggleAction;
    private MaterialButton removeAction;
    private MaterialButton useAction;

    private PluginCatalogItem catalogItem;
    private PluginManager.PluginInfo installedInfo;
    private String pluginId;
    private boolean busy;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_plugin_details);
        toolbar = findViewById(R.id.plugin_details_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        icon = findViewById(R.id.plugin_details_icon);
        name = findViewById(R.id.plugin_details_name);
        developer = findViewById(R.id.plugin_details_developer);
        version = findViewById(R.id.plugin_details_version);
        pluginIdText = findViewById(R.id.plugin_details_id);
        badges = findViewById(R.id.plugin_details_badges);
        sections = findViewById(R.id.plugin_details_sections);
        progress = findViewById(R.id.plugin_details_progress);
        primaryAction = findViewById(R.id.plugin_details_primary_action);
        toggleAction = findViewById(R.id.plugin_details_toggle_action);
        removeAction = findViewById(R.id.plugin_details_remove_action);
        useAction = findViewById(R.id.plugin_details_use_action);

        String serialized = getIntent().getStringExtra(EXTRA_CATALOG_JSON);
        if (serialized != null && !serialized.isBlank()) {
            try {
                catalogItem = PluginCatalogItem.fromJson(new JSONObject(serialized));
                pluginId = catalogItem.pluginId();
            } catch (Exception e) {
                showFatal("Catalog entry is invalid: " + readableError(e));
                return;
            }
        } else {
            pluginId = getIntent().getStringExtra(EXTRA_PLUGIN_ID);
            if (pluginId == null || pluginId.isBlank()) {
                showFatal("Plugin ID was not supplied.");
                return;
            }
        }
        render();
    }

    private void render() {
        installedInfo = PluginManager.getInstalledPluginInfo(this, pluginId);
        if (catalogItem == null && installedInfo == null) {
            showFatal("This plugin is no longer installed. Rescan the plugin manager and try again.");
            return;
        }
        String displayName = catalogItem != null ? catalogItem.name() : installedInfo.name();
        String displayDeveloper = choose(catalogItem != null ? catalogItem.developer() : installedInfo.developer(), "Developer not provided");
        String installedVersion = installedInfo == null ? "" : installedInfo.version();
        if ((installedVersion == null || installedVersion.isBlank()) && installedInfo != null) {
            installedVersion = PluginMarketplaceRepository.getInstalledVersion(this, pluginId);
        }
        String displayVersion = choose(catalogItem != null ? catalogItem.version() : installedVersion, "Version not provided");
        String category = catalogItem != null ? catalogItem.category() : installedInfo.category();
        String description = catalogItem != null ? catalogItem.description() : installedInfo.description();
        String iconUrl = catalogItem != null ? catalogItem.iconUrl() : installedInfo.iconUrl();
        String minHostVersion = catalogItem != null ? catalogItem.minHostVersion() : installedInfo.minHostVersion();
        int apiVersion = catalogItem != null ? catalogItem.apiVersion() : installedInfo.apiVersion();

        toolbar.setTitle("Extension details");
        name.setText(displayName);
        developer.setText(displayDeveloper);
        version.setText("Version " + displayVersion + " · Plugin API v" + apiVersion);
        pluginIdText.setText(pluginId);
        if (!iconUrl.isBlank() && iconUrl.startsWith("https://")) {
            icon.setImageTintList(null);
            Glide.with(icon).load(iconUrl).centerCrop().placeholder(R.drawable.ic_mtrl_plugin).error(R.drawable.ic_mtrl_plugin).into(icon);
        } else {
            Glide.with(icon).clear(icon);
            icon.setImageResource(R.drawable.ic_mtrl_plugin);
        }

        badges.removeAllViews();
        addBadge(choose(category, "Other"));
        if (catalogItem != null) addBadge("Marketplace");
        if (installedInfo != null) addBadge(installedInfo.loaded() ? "Active" : installedInfo.enabled() ? "Not loaded" : "Disabled");
        double rating = catalogItem != null ? catalogItem.rating() : installedInfo.rating();
        long downloads = catalogItem != null ? catalogItem.downloads() : installedInfo.downloads();
        if (rating >= 0) addBadge(String.format(Locale.ROOT, "★ %.1f", rating));
        if (downloads >= 0) addBadge(downloadsLabel(downloads));

        sections.removeAllViews();
        addSection("Description", choose(description, "The developer has not provided a description."));
        String compatibility = "Required plugin API: v" + apiVersion + "; this host supports v" + PluginManager.getCurrentApiVersion() + ".";
        if (!minHostVersion.isBlank()) compatibility += "\nMinimum Sketchware Neo version: " + minHostVersion + ".";
        boolean compatible = apiVersion <= PluginManager.getCurrentApiVersion()
                && (minHostVersion.isBlank() || PluginVersionUtils.compareVersions(BuildConfig.VERSION_NAME, minHostVersion) >= 0);
        compatibility += compatible ? "\nCompatible with this app version." : "\nThis extension is not compatible with the current app version.";
        addSection("Compatibility", compatibility);
        if (catalogItem != null && !catalogItem.updatedAt().isBlank()) {
            addSection("Last updated", catalogItem.updatedAt());
        }

        List<String> features = catalogItem != null ? catalogItem.features() : installedInfo.features();
        List<String> components = catalogItem != null ? catalogItem.components() : installedInfo.components();
        List<String> blocks = catalogItem != null ? catalogItem.blocks() : installedInfo.blocks();
        List<String> dependencies = catalogItem != null ? catalogItem.dependencies() : installedInfo.dependencies();
        List<String> screenshots = catalogItem != null ? catalogItem.screenshots() : installedInfo.screenshots();
        addSection("Features", listOrUnavailable(features));
        addSection("Components", listOrUnavailable(components));
        addSection("Blocks", listOrUnavailable(blocks));
        addSection("Dependencies", dependencies.isEmpty() ? "No dependencies declared." : String.join("\n", dependencies));
        addScreenshots(screenshots);

        addLinksSection(
                catalogItem != null ? catalogItem.homepageUrl() : installedInfo.homepageUrl(),
                catalogItem != null ? catalogItem.documentationUrl() : installedInfo.documentationUrl(),
                catalogItem != null ? catalogItem.changelogUrl() : installedInfo.changelogUrl(),
                catalogItem != null ? catalogItem.sourceCodeUrl() : installedInfo.sourceCodeUrl(),
                catalogItem != null ? catalogItem.license() : installedInfo.license()
        );

        if (installedInfo != null) {
            addSection("Installation", "File: " + installedInfo.fileName() + "\nSize: " + formatBytes(installedInfo.fileSizeBytes())
                    + "\nState: " + (installedInfo.loaded() ? "Loaded and active" : installedInfo.enabled() ? "Enabled but not loaded" : "Disabled"));
        }
        bindActions(compatible);
    }

    private void bindActions(boolean compatible) {
        boolean hasInstalled = installedInfo != null;
        String installedVersion = hasInstalled ? installedInfo.version() : "";
        if ((installedVersion == null || installedVersion.isBlank()) && hasInstalled) {
            installedVersion = PluginMarketplaceRepository.getInstalledVersion(this, pluginId);
        }
        boolean updateAvailable = hasInstalled && catalogItem != null && !catalogItem.version().isBlank()
                && (installedVersion.isBlank() || PluginVersionUtils.compareVersions(catalogItem.version(), installedVersion) > 0);
        primaryAction.setVisibility(View.VISIBLE);
        primaryAction.setEnabled(!busy && compatible && (catalogItem != null ? (!hasInstalled || updateAvailable) : true));
        if (catalogItem != null) {
            if (!compatible) primaryAction.setText("Incompatible version");
            else if (!hasInstalled) primaryAction.setText("Install extension");
            else if (updateAvailable) primaryAction.setText("Update to " + catalogItem.version());
            else primaryAction.setText("Latest version installed");
            primaryAction.setOnClickListener(v -> {
                if (!hasInstalled || updateAvailable) confirmInstallOrUpdate();
                else Toast.makeText(this, "This version is already installed.", Toast.LENGTH_SHORT).show();
            });
        } else {
            primaryAction.setText("Browse marketplace for updates");
            primaryAction.setEnabled(!busy);
            primaryAction.setOnClickListener(v -> startActivity(new Intent(this, ManagePluginsActivity.class)));
        }

        toggleAction.setVisibility(hasInstalled ? View.VISIBLE : View.GONE);
        if (hasInstalled) {
            toggleAction.setText(installedInfo.enabled() ? "Disable extension" : "Enable extension");
            toggleAction.setOnClickListener(v -> togglePlugin());
        }
        removeAction.setVisibility(hasInstalled ? View.VISIBLE : View.GONE);
        removeAction.setOnClickListener(v -> confirmRemove());
        useAction.setVisibility(hasInstalled && installedInfo.loaded() ? View.VISIBLE : View.GONE);
        useAction.setOnClickListener(v -> openProjectForEditor());
    }

    private void addBadge(String text) {
        Chip chip = new Chip(this);
        chip.setText(text);
        chip.setCheckable(false);
        chip.setClickable(false);
        badges.addView(chip);
    }

    private void addSection(String title, String content) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.topMargin = dp(12);
        card.setLayoutParams(cardParams);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(resolveOutlineVariant());
        card.setCardBackgroundColor(resolveSurfaceContainer());

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(14), dp(16), dp(16));
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextSize(16);
        heading.setTextColor(resolveOnSurface());
        inner.addView(heading);
        TextView body = new TextView(this);
        body.setText(content);
        body.setTextSize(14);
        body.setTextColor(resolveOnSurfaceVariant());
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = dp(8);
        inner.addView(body, bodyParams);
        card.addView(inner);
        sections.addView(card);
    }

    private void addScreenshots(List<String> screenshots) {
        if (screenshots == null || screenshots.isEmpty()) {
            addSection("Screenshots & previews", "No screenshots or previews provided.");
            return;
        }
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(resolveOutlineVariant());
        card.setCardBackgroundColor(resolveSurfaceContainer());
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setPadding(dp(16), dp(14), dp(16), dp(16));
        wrapper.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(this);
        heading.setText("Screenshots & previews");
        heading.setTextSize(16);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setTextColor(resolveOnSurface());
        wrapper.addView(heading);
        android.widget.HorizontalScrollView horizontalScrollView = new android.widget.HorizontalScrollView(this);
        horizontalScrollView.setHorizontalScrollBarEnabled(false);
        LinearLayout imageRow = new LinearLayout(this);
        imageRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String url : screenshots) {
            if (url == null || !url.startsWith("https://")) continue;
            ImageView preview = new ImageView(this);
            preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
            LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(dp(240), dp(150));
            imageParams.rightMargin = dp(10);
            imageParams.topMargin = dp(12);
            preview.setLayoutParams(imageParams);
            preview.setContentDescription("Plugin screenshot preview");
            Glide.with(preview).load(url).centerCrop().placeholder(R.drawable.ic_mtrl_plugin).error(R.drawable.ic_mtrl_plugin).into(preview);
            preview.setOnClickListener(v -> openExternalUrl(url));
            imageRow.addView(preview);
        }
        horizontalScrollView.addView(imageRow);
        wrapper.addView(horizontalScrollView);
        card.addView(wrapper);
        sections.addView(card);
    }

    private void addLinksSection(String homepage, String documentation, String changelog, String sourceCode, String license) {
        List<String[]> links = new ArrayList<>();
        addLinkIfPresent(links, "Website", homepage);
        addLinkIfPresent(links, "Documentation", documentation);
        addLinkIfPresent(links, "Changelog", changelog);
        addLinkIfPresent(links, "Source code", sourceCode);
        if (license != null && !license.isBlank()) links.add(new String[]{"License", license});
        if (links.isEmpty()) {
            addSection("Links", "No external links provided.");
            return;
        }
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(12);
        card.setLayoutParams(params);
        card.setRadius(dp(16));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(resolveOutlineVariant());
        card.setCardBackgroundColor(resolveSurfaceContainer());
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(14), dp(16), dp(12));
        TextView title = new TextView(this);
        title.setText("Links");
        title.setTextSize(16);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(resolveOnSurface());
        inner.addView(title);
        for (String[] link : links) {
            TextView value = new TextView(this);
            value.setText(link[0] + (link[1].startsWith("https://") ? "  ↗" : "") + "\n" + link[1]);
            value.setTextSize(14);
            value.setTextColor(resolvePrimary());
            value.setPadding(0, dp(10), 0, dp(10));
            if (link[1].startsWith("https://")) value.setOnClickListener(v -> openExternalUrl(link[1]));
            inner.addView(value);
        }
        card.addView(inner);
        sections.addView(card);
    }

    private void addLinkIfPresent(List<String[]> links, String label, String url) {
        if (url != null && !url.isBlank()) links.add(new String[]{label, url});
    }

    private void confirmInstallOrUpdate() {
        if (catalogItem == null || busy) return;
        boolean update = installedInfo != null;
        new MaterialAlertDialogBuilder(this)
                .setTitle(update ? "Update extension?" : "Install extension?")
                .setMessage("Download “" + catalogItem.name() + "”, verify its SHA-256 digest, then load it into Sketchware Neo? Plugins execute inside the app process and should only come from trusted sources.")
                .setPositiveButton(update ? "Update" : "Install", (dialog, which) -> installCatalogItem())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void installCatalogItem() {
        if (catalogItem == null || busy) return;
        setBusy(true, "Downloading and verifying extension…");
        PluginCatalogItem item = catalogItem;
        executor.execute(() -> {
            String failure = null;
            try {
                if (item.apiVersion() > PluginManager.getCurrentApiVersion()) {
                    throw new IllegalStateException("This extension requires plugin API v" + item.apiVersion());
                }
                if (!item.minHostVersion().isBlank() && PluginVersionUtils.compareVersions(BuildConfig.VERSION_NAME, item.minHostVersion()) < 0) {
                    throw new IllegalStateException("This extension requires Sketchware Neo " + item.minHostVersion() + " or later");
                }
                java.io.File file = PluginMarketplaceRepository.downloadAndVerify(getApplicationContext(), item);
                PluginManager.PluginInstallResult result = PluginManager.installAndLoad(getApplicationContext(), file, item.pluginId(), item.apiVersion(), item.version());
                if (!result.success()) failure = result.error();
            } catch (Exception e) {
                failure = readableError(e);
            }
            String finalFailure = failure;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false, null);
                if (finalFailure == null) {
                    PluginMarketplaceRepository.recordInstalledVersion(this, item.pluginId(), item.version());
                    Toast.makeText(this, "Extension installed", Toast.LENGTH_SHORT).show();
                    render();
                } else {
                    new MaterialAlertDialogBuilder(this).setTitle("Installation failed").setMessage(finalFailure).setPositiveButton("OK", null).show();
                }
            });
        });
    }

    private void togglePlugin() {
        if (installedInfo == null || busy) return;
        boolean enable = !installedInfo.enabled();
        PluginManager.setEnabled(this, pluginId, enable);
        PluginManager.rescan(this);
        render();
        PluginManager.PluginInfo updated = PluginManager.getInstalledPluginInfo(this, pluginId);
        if (updated != null && enable && !updated.loaded()) {
            List<String> errors = PluginManager.getLastLoadErrors();
            new MaterialAlertDialogBuilder(this).setTitle("Plugin could not be enabled")
                    .setMessage(errors.isEmpty() ? "The plugin file was found, but the plugin did not load." : String.join("\n\n", errors))
                    .setPositiveButton("OK", null).show();
        } else {
            Snackbar.make(findViewById(R.id.plugin_details_content), enable ? "Extension enabled" : "Extension disabled", Snackbar.LENGTH_SHORT).show();
        }
    }

    private void confirmRemove() {
        if (installedInfo == null || busy) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Uninstall extension?")
                .setMessage("Remove “" + installedInfo.name() + "” and its contributed blocks? This cannot be undone.")
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    boolean removed = PluginManager.deletePlugin(this, pluginId);
                    if (removed) {
                        Toast.makeText(this, "Extension uninstalled", Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        new MaterialAlertDialogBuilder(this).setTitle("Unable to uninstall").setMessage("The plugin was unloaded, but one or more installed files could not be deleted.").setPositiveButton("OK", null).show();
                        render();
                    }
                }).setNegativeButton("Cancel", null).show();
    }

    private void openProjectForEditor() {
        if (installedInfo == null || !installedInfo.loaded()) {
            Toast.makeText(this, "Install and enable the extension before opening a project.", Toast.LENGTH_SHORT).show();
            return;
        }
        List<HashMap<String, Object>> projects = lC.a();
        ArrayList<HashMap<String, Object>> validProjects = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (HashMap<String, Object> project : projects) {
            if (project == null) continue;
            String scId = yB.c(project, "sc_id");
            if (scId == null || scId.isBlank() || jC.b(scId) == null) continue;
            String title = yB.c(project, "my_app_name");
            if (title == null || title.isBlank()) title = "Project " + scId;
            validProjects.add(project);
            labels.add(title + "  ·  " + scId);
        }
        if (validProjects.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("Open extension in editor")
                    .setMessage("Create or open a Sketchware Neo project first. The extension's contributed blocks and components become available in the project editor when the extension is loaded.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        List<NeoEditorAction> actions = PluginManager.getEditorActions(pluginId);
        String detail = actions.isEmpty()
                ? "Choose a project to open its editor. This extension has not registered source-editor commands; it may contribute blocks, components, or other hooks."
                : "Choose a project to open the editor. " + actions.size() + " source-editor command(s) are registered; open a Java source file to use those commands from the editor actions menu.";
        new MaterialAlertDialogBuilder(this).setTitle("Open extension in editor")
                .setMessage(detail)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    String scId = yB.c(validProjects.get(which), "sc_id");
                    try {
                        ProjectTracker.setScId(scId);
                        Intent intent = new Intent(this, DesignActivity.class);
                        intent.putExtra("sc_id", scId);
                        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                    } catch (Exception exception) {
                        Toast.makeText(this, "Could not open the selected project: " + readableError(exception), Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        if (value) {
            primaryAction.setEnabled(false);
            toggleAction.setEnabled(false);
            removeAction.setEnabled(false);
            useAction.setEnabled(false);
            if (message != null) toolbar.setSubtitle(message);
        } else {
            toolbar.setSubtitle(null);
            render();
        }
    }

    private void openExternalUrl(String url) {
        if (url == null || !url.startsWith("https://")) {
            Toast.makeText(this, "Only HTTPS links are opened", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser is available to open this link", Toast.LENGTH_SHORT).show();
        }
    }

    private void showFatal(String message) {
        new MaterialAlertDialogBuilder(this).setTitle("Extension details unavailable").setMessage(message)
                .setPositiveButton("Close", (d, w) -> finish()).setOnCancelListener(d -> finish()).show();
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    private int resolveSurfaceContainer() { return resolveAttr(R.attr.colorSurfaceContainerLow, R.color.md_theme_light_surface_container_low); }
    private int resolveOnSurface() { return resolveAttr(R.attr.colorOnSurface, R.color.md_theme_light_on_surface); }
    private int resolveOnSurfaceVariant() { return resolveAttr(R.attr.colorOnSurfaceVariant, R.color.md_theme_light_on_surface_variant); }
    private int resolvePrimary() { return resolveAttr(R.attr.colorPrimary, R.color.md_theme_light_primary); }
    private int resolveOutlineVariant() { return resolveAttr(R.attr.colorOutlineVariant, R.color.md_theme_light_outline_variant); }
    private int resolveAttr(int attr, int fallbackRes) {
        android.util.TypedValue value = new android.util.TypedValue();
        if (getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) return ContextCompat.getColor(this, value.resourceId);
            return value.data;
        }
        return ContextCompat.getColor(this, fallbackRes);
    }

    private static String choose(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String listOrUnavailable(List<String> values) { return values == null || values.isEmpty() ? "Not provided by this plugin." : "• " + TextUtils.join("\n• ", values); }
    private static String downloadsLabel(long value) { return value + " downloads"; }
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
    private static String readableError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (message == null || message.isBlank()) message = error == null ? "Unknown error" : error.getClass().getSimpleName();
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

    @Override
    protected void onDestroy() {
        if (icon != null) Glide.with(icon).clear(icon);
        executor.shutdownNow();
        super.onDestroy();
    }
}
