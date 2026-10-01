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
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AiModelAdapter(this);
        recyclerView.setAdapter(adapter);

        editModelLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) refreshList();
        });

        MaterialButton retryButton = findViewById(R.id.buttonRetry);
        retryButton.setOnClickListener(v -> refreshList());

        FloatingActionButton fab = findViewById(R.id.fabAddAiModel);
        fab.setOnClickListener(v -> editModelLauncher.launch(new Intent(this, AiModelEditActivity.class)));

        refreshList();
    }

    /**
     * Config reads are just an encrypted local SharedPreferences lookup (see AiManager) - not
     * slow enough to need a background thread - but the loading/empty/error states are wired
     * properly here in case that storage ever becomes async or starts failing (e.g. Keystore
     * key access problems, which AiManager.decrypt() otherwise swallows into an empty list).
     */
    private void refreshList() {
        loadingIndicator.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.GONE);
        errorState.setVisibility(View.GONE);

        try {
            List<AiModelConfig> configs = AiManager.getConfigs(this);
            String activeId = AiManager.getActiveConfigId(this);
            adapter.submitList(configs, activeId);

            loadingIndicator.setVisibility(View.GONE);
            boolean empty = configs.isEmpty();
            emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        } catch (Exception e) {
            loadingIndicator.setVisibility(View.GONE);
            errorState.setVisibility(View.VISIBLE);
            errorMessageText.setText("Couldn't load your AI models: " +
                    (e.getMessage() != null ? e.getMessage() : "unknown error"));
        }
    }

    @Override
    public void onEditClicked(AiModelConfig config) {
        Intent intent = new Intent(this, AiModelEditActivity.class);
        intent.putExtra(AiModelEditActivity.EXTRA_CONFIG, config);
        editModelLauncher.launch(intent);
    }

    @Override
    public void onDuplicateClicked(AiModelConfig config) {
        AiModelConfig copy = new AiModelConfig(config.providerId, config.displayName + " (copy)", config.apiKey, config.modelName, config.customEndpoint);
        copy.temperature = config.temperature;
        copy.threads = config.threads;
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
                .setMessage(config.displayName + " will be used for all AI features (layout, logic, error fix, block generation).")
                .setPositiveButton("Set active", (dialog, which) -> {
                    AiManager.setActiveConfigId(this, config.id);
                    refreshList();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

}
