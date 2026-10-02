package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.List;

import pro.sketchware.R;

public class AiSettingsActivity extends BaseAppCompatActivity implements AiModelAdapter.Listener {

    private RecyclerView recyclerView;
    private View emptyState;
    private View errorState;
    private TextView errorMessageText;
    private CircularProgressIndicator loadingIndicator;
    private AiModelAdapter adapter;

    private TextView activeModelName;
    private TextView activeModelRuntime;
    private MaterialButton testActiveButton;
    private ActivityResultLauncher<Intent> editModelLauncher;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_settings);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> finish());

        recyclerView = findViewById(R.id.recyclerViewAiModels);
        emptyState = findViewById(R.id.emptyStateLayout);
        errorState = findViewById(R.id.errorStateLayout);
        errorMessageText = findViewById(R.id.textErrorMessage);
        loadingIndicator = findViewById(R.id.loadingIndicator);
        activeModelName = findViewById(R.id.textActiveModelName);
        activeModelRuntime = findViewById(R.id.textActiveModelRuntime);
        testActiveButton = findViewById(R.id.buttonTestActive);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AiModelAdapter(this);
        recyclerView.setAdapter(adapter);

        editModelLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) refreshList();
                }
        );

        MaterialButton retryButton = findViewById(R.id.buttonRetry);
        retryButton.setOnClickListener(v -> refreshList());

        FloatingActionButton fab = findViewById(R.id.fabAddAiModel);
        fab.setOnClickListener(v ->
                editModelLauncher.launch(new Intent(this, AiModelEditActivity.class))
        );

        testActiveButton.setOnClickListener(v -> testActiveModel());

        refreshList();
    }

    private void refreshList() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);

        try {
            List<AiModelConfig> configs = AiManager.getConfigs(this);
            String activeId = AiManager.getActiveConfigId(this);
            adapter.submitList(configs, activeId);
            updateActiveSummary(configs, activeId);

            loadingIndicator.setVisibility(View.GONE);
            boolean empty = configs.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            loadingIndicator.setVisibility(View.GONE);
            errorState.setVisibility(View.VISIBLE);
            errorMessageText.setText(
                    "Couldn't load your AI models: " +
                            (e.getMessage() != null ? e.getMessage() : "unknown error")
            );
            activeModelName.setText("AI is not configured");
            activeModelRuntime.setText("Add a cloud or local/self-hosted model");
            testActiveButton.setEnabled(false);
        }
    }

    private void updateActiveSummary(List<AiModelConfig> configs, String activeId) {
        AiModelConfig active = null;
        if (activeId != null) {
            for (AiModelConfig config : configs) {
                if (activeId.equals(config.id)) {
                    active = config;
                    break;
                }
            }
        }

        if (active == null) {
            activeModelName.setText("No active AI model");
            activeModelRuntime.setText("Choose a model below to use AI generation");
            testActiveButton.setEnabled(false);
            return;
        }

        AiProvider provider = AiProviderRegistry.get(active.providerId);
        activeModelName.setText(active.displayName + " · " + active.modelName);
        activeModelRuntime.setText(
                provider != null && provider.isLocal()
                        ? "Local / self-hosted AI"
                        : provider != null ? provider.getProviderName() : "AI provider"
        );
        testActiveButton.setEnabled(provider != null);
    }

    private void testActiveModel() {
        testActiveButton.setEnabled(false);
        testActiveButton.setText("Testing…");

        AiManager.testActive(this, new AiResponseCallback() {
            @Override
            public void onSuccess(String response) {
                testActiveButton.setEnabled(true);
                testActiveButton.setText("Test connection");
                new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                        .setTitle("Connection successful")
                        .setMessage(response)
                        .setPositiveButton("OK", null)
                        .show();
            }

            @Override
            public void onFailure(String errorMessage) {
                testActiveButton.setEnabled(true);
                testActiveButton.setText("Test connection");
                new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                        .setTitle("Connection failed")
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    @Override
    public void onEditClicked(AiModelConfig config) {
        Intent intent = new Intent(this, AiModelEditActivity.class);
        intent.putExtra(AiModelEditActivity.EXTRA_CONFIG, config);
        editModelLauncher.launch(intent);
    }

    @Override
    public void onDuplicateClicked(AiModelConfig config) {
        AiModelConfig copy = new AiModelConfig(
                config.providerId,
                config.displayName + " (copy)",
                config.apiKey,
                config.modelName,
                config.customEndpoint
        );
        copy.temperature = config.temperature;
        copy.threads = config.threads;
        copy.maxTokens = config.maxTokens;
        copy.topP = config.topP;
        AiManager.addConfig(this, copy);
        refreshList();
    }

    @Override
    public void onDeleteClicked(AiModelConfig config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete AI model")
                .setMessage("Remove \"" + config.displayName + "\"? This cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    AiManager.removeConfig(this, config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClicked(AiModelConfig config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Set as active model?")
                .setMessage(
                        config.displayName +
                                " will be used for AI features such as block, logic, layout and error generation."
                )
                .setPositiveButton("Set active", (dialog, which) -> {
                    AiManager.setActiveConfigId(this, config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
