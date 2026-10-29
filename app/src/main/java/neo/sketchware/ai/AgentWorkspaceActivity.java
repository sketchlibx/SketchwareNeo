package neo.sketchware.ai;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.projects.MyProjectSettingActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import a.a.a.wq;
import a.a.a.lC;
import mod.hey.studios.project.ProjectTracker;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.R;

import org.json.JSONArray;
import org.json.JSONObject;

public class AgentWorkspaceActivity extends BaseAppCompatActivity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (isFinishing() || isDestroyed()) return;
            refreshRuns();
            handler.postDelayed(this, 1500L);
        }
    };

    private TextInputEditText editTask;
    private TextInputEditText editReusableContext;
    private TextView textContextSaved;
    private MaterialAutoCompleteTextView plannerDropdown, builderDropdown, reviewerDropdown;
    private MaterialSwitch switchMultiAgent;
    private LinearLayout roleContainer;
    private TextView textNoRuns, textRunCount, textTaskHint;
    private RecyclerView recyclerRuns;
    private RunsAdapter runsAdapter;
    private final List<AgentChoice> agentChoices = new ArrayList<>();
    private final Map<String, String> agentIdByLabel = new HashMap<>();
    private ActivityResultLauncher<Intent> createProjectLauncher;
    private String pendingProjectRunId;
    private String pendingProjectBrief;
    private JSONObject pendingGeneratedProject;
    private androidx.appcompat.app.AlertDialog projectProgressDialog;
    private LinearProgressIndicator projectProgressBar;
    private TextView projectProgressTitle;
    private TextView projectProgressDetail;
    private final TextView[] projectStageLabels = new TextView[6];
    private boolean aiProjectGenerationRunning;
    private boolean aiProjectApplyRunning;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_agent_workspace);

        editTask = findViewById(R.id.editAgentTask);
        editReusableContext = findViewById(R.id.editReusableContext);
        textContextSaved = findViewById(R.id.textContextSaved);
        editReusableContext.setText(AiMemoryStore.getReusableContext(this));
        textContextSaved.setText("Saved · used automatically for new tasks");
        plannerDropdown = findViewById(R.id.dropdownPlanner);
        builderDropdown = findViewById(R.id.dropdownBuilder);
        reviewerDropdown = findViewById(R.id.dropdownReviewer);
        switchMultiAgent = findViewById(R.id.switchMultiAgent);
        roleContainer = findViewById(R.id.layoutAgentRoles);
        textNoRuns = findViewById(R.id.textNoRuns);
        textRunCount = findViewById(R.id.textRunCount);
        textTaskHint = findViewById(R.id.textTaskHint);
        recyclerRuns = findViewById(R.id.recyclerAgentRuns);

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbarAgentWorkspace);
        toolbar.setNavigationOnClickListener(v -> finish());

        createProjectLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            Intent data = result.getData();
            if (result.getResultCode() != RESULT_OK || data == null || !data.getBooleanExtra("is_new", false)) {
                closeProjectProgressDialog();
                pendingProjectRunId = null;
                pendingProjectBrief = null;
                pendingGeneratedProject = null;
                return;
            }
            String scId = data.getStringExtra("sc_id");
            if (TextUtils.isEmpty(scId)) {
                Toast.makeText(this, "Project was saved, but its identifier was not returned.", Toast.LENGTH_LONG).show();
                pendingProjectRunId = null;
                pendingGeneratedProject = null;
                return;
            }
            if (pendingGeneratedProject != null) {
                applyPendingGeneratedProject(scId, pendingGeneratedProject, pendingProjectRunId);
                return;
            }

            AgentRun run = TextUtils.isEmpty(pendingProjectRunId) ? null : AgentRunStore.get(this, pendingProjectRunId);
            if (run != null) {
                run.createdProjectId = scId;
                try {
                    writeProjectBlueprint(scId, run);
                    run.record("Project created", "A native Sketchware project was created; agent notes are attached as a blueprint.");
                    Toast.makeText(this, "Project created and agent blueprint attached.", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    run.record("Project created", "Project was created, but blueprint saving failed: " + safeMessage(e));
                    Toast.makeText(this, "Project created, but blueprint could not be saved: " + safeMessage(e), Toast.LENGTH_LONG).show();
                }
                try { AgentRunStore.save(this, run); } catch (Exception ignored) { }
            }
            openProjectInEditor(scId, false);
        });

        findViewById(R.id.buttonCreateProject).setOnClickListener(v -> openProjectWizard(null));
        findViewById(R.id.buttonManageAgents).setOnClickListener(v -> {
            startActivity(new Intent(this, AiSettingsActivity.class));
            finish();
        });
        findViewById(R.id.buttonRunTask).setOnClickListener(v -> enqueueTask());
        findViewById(R.id.buttonGenerateProject).setOnClickListener(v -> startAiProjectGeneration(
                editTask.getText() == null ? "" : editTask.getText().toString().trim(), null));
        findViewById(R.id.buttonSaveContext).setOnClickListener(v -> saveReusableContext(true));
        editReusableContext.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                textContextSaved.setText("Unsaved changes");
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        switchMultiAgent.setChecked(true);
        switchMultiAgent.setOnCheckedChangeListener((button, checked) -> {
            roleContainer.setVisibility(checked ? View.VISIBLE : View.GONE);
            findViewById(R.id.textWorkflowDescription).setVisibility(checked ? View.VISIBLE : View.GONE);
        });

        recyclerRuns.setLayoutManager(new LinearLayoutManager(this));
        runsAdapter = new RunsAdapter();
        recyclerRuns.setAdapter(runsAdapter);
        loadAgentChoices();
        refreshRuns();
    }

    @Override public void onResume() {
        super.onResume();
        loadAgentChoices();
        refreshRuns();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, 1500L);
    }

    @Override public void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void loadAgentChoices() {
        List<AiModelConfig> configs = AiManager.getConfigs(this);
        agentChoices.clear();
        agentIdByLabel.clear();
        AiModelConfig preferredConfig = AiManager.getActiveConfig(this);
        String activeId = preferredConfig == null ? AiManager.getActiveConfigId(this) : preferredConfig.id;
        int defaultIndex = 0;
        List<String> labels = new ArrayList<>();
        for (AiModelConfig config : configs) {
            AiProvider provider = AiProviderRegistry.get(config.providerId);
            String baseLabel = config.displayName + " · " + (provider == null ? config.providerId : provider.getProviderName()) + " · " + config.modelName;
            String label = baseLabel;
            // Configurations can legitimately share the same display/provider/model values.
            // Keep their dropdown entries distinct so every agent profile remains selectable.
            if (agentIdByLabel.containsKey(label)) {
                String suffix = config.id == null ? "agent" : config.id.substring(0, Math.min(6, config.id.length()));
                label = baseLabel + " · " + suffix;
                int duplicate = 2;
                while (agentIdByLabel.containsKey(label)) label = baseLabel + " · " + suffix + " (" + duplicate++ + ")";
            }
            agentChoices.add(new AgentChoice(config.id, label));
            agentIdByLabel.put(label, config.id);
            labels.add(label);
            if (config.id.equals(activeId)) defaultIndex = labels.size() - 1;
        }
        if (labels.isEmpty()) labels.add("No configured AI agent");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, labels);
        plannerDropdown.setAdapter(adapter);
        builderDropdown.setAdapter(adapter);
        reviewerDropdown.setAdapter(adapter);
        String defaultLabel = labels.get(Math.min(defaultIndex, labels.size() - 1));
        if (TextUtils.isEmpty(String.valueOf(plannerDropdown.getText())) || !agentIdByLabel.containsKey(String.valueOf(plannerDropdown.getText()))) plannerDropdown.setText(defaultLabel, false);
        if (TextUtils.isEmpty(String.valueOf(builderDropdown.getText())) || !agentIdByLabel.containsKey(String.valueOf(builderDropdown.getText()))) builderDropdown.setText(defaultLabel, false);
        if (TextUtils.isEmpty(String.valueOf(reviewerDropdown.getText())) || !agentIdByLabel.containsKey(String.valueOf(reviewerDropdown.getText()))) reviewerDropdown.setText(defaultLabel, false);

        boolean haveAgent = !configs.isEmpty();
        findViewById(R.id.buttonRunTask).setEnabled(haveAgent);
        View generateProjectButton = findViewById(R.id.buttonGenerateProject);
        if (generateProjectButton != null) generateProjectButton.setEnabled(haveAgent && !aiProjectGenerationRunning && !aiProjectApplyRunning);
        if (!haveAgent) {
            textTaskHint.setText("First configure an AI agent and test its connection. Use “Configure AI agents” above.");
        } else {
            textTaskHint.setText("Saved runs continue through Android WorkManager. Keep agent API keys and local server details private.");
        }
    }

    private String selectedAgentId(MaterialAutoCompleteTextView view) {
        return agentIdByLabel.get(String.valueOf(view.getText()).trim());
    }

    private boolean saveReusableContext(boolean showFeedback) {
        String value = editReusableContext.getText() == null ? "" : editReusableContext.getText().toString();
        if (value.length() > AiMemoryStore.MAX_CONTEXT_LENGTH) {
            editReusableContext.setError("Use 8,000 characters or fewer");
            return false;
        }
        boolean saved = AiMemoryStore.saveReusableContext(this, value);
        if (saved) {
            textContextSaved.setText("Saved · used automatically for new tasks");
            if (showFeedback) Toast.makeText(this, "Reusable context saved", Toast.LENGTH_SHORT).show();
        } else {
            textContextSaved.setText("Could not save permanently; current task can still use this context");
            if (showFeedback) Toast.makeText(this, "Context could not be saved", Toast.LENGTH_LONG).show();
        }
        return saved;
    }

    private void enqueueTask() {
        String prompt = editTask.getText() == null ? "" : editTask.getText().toString().trim();
        if (prompt.isEmpty()) {
            editTask.setError("Describe the task first");
            editTask.requestFocus();
            return;
        }
        if (prompt.length() > 24000) {
            editTask.setError("Use 24,000 characters or fewer");
            return;
        }
        List<AiModelConfig> configs = AiManager.getConfigs(this);
        if (configs.isEmpty()) {
            new MaterialAlertDialogBuilder(this).setTitle("Configure an AI agent")
                    .setMessage("Add and test at least one AI agent before starting a task.")
                    .setPositiveButton("Configure", (d, w) -> {
                        startActivity(new Intent(this, AiSettingsActivity.class));
                        finish();
                    }).setNegativeButton("Cancel", null).show();
            return;
        }

        AgentRun run = new AgentRun();
        run.prompt = prompt;
        run.title = makeTitle(prompt);
        run.reusableContext = editReusableContext.getText() == null ? "" : editReusableContext.getText().toString().trim();
        if (run.reusableContext.length() > AiMemoryStore.MAX_CONTEXT_LENGTH) {
            editReusableContext.setError("Use 8,000 characters or fewer");
            return;
        }
        // Save this context for future tasks as well as snapshotting it into this run.
        saveReusableContext(false);
        run.multiAgent = switchMultiAgent.isChecked();
        run.plannerAgentId = selectedAgentId(plannerDropdown);
        run.builderAgentId = selectedAgentId(builderDropdown);
        run.reviewerAgentId = selectedAgentId(reviewerDropdown);
        if (TextUtils.isEmpty(run.plannerAgentId)) {
            AiModelConfig preferred = AiManager.getActiveConfig(this);
            run.plannerAgentId = preferred == null ? AiManager.getActiveConfigId(this) : preferred.id;
        }
        if (TextUtils.isEmpty(run.builderAgentId)) run.builderAgentId = run.plannerAgentId;
        if (TextUtils.isEmpty(run.reviewerAgentId)) run.reviewerAgentId = run.builderAgentId;
        run.status = AgentRun.QUEUED;
        run.progress = 0;
        run.record("Queued", run.multiAgent ? "Waiting for Planner → Builder → Reviewer." : "Waiting for the selected agent.");

        try {
            AgentRunStore.save(this, run);
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(AgentRunWorker.class)
                    .setInputData(new androidx.work.Data.Builder().putString("run_id", run.id).build())
                    .addTag("neo-ai-agent-run")
                    .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build();
            WorkManager.getInstance(getApplicationContext()).enqueueUniqueWork(
                    "neo-ai-agent-run-" + run.id, ExistingWorkPolicy.KEEP, request);
            refreshRuns();
            Toast.makeText(this, "Task queued. You can leave this screen.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            run.status = AgentRun.FAILED;
            run.errorMessage = "Could not queue task: " + safeMessage(e);
            run.record("Failed", run.errorMessage);
            try { AgentRunStore.save(this, run); } catch (Exception ignored) { }
            refreshRuns();
            new MaterialAlertDialogBuilder(this).setTitle("Could not start agents")
                    .setMessage(run.errorMessage).setPositiveButton("OK", null).show();
        }
    }

    private void refreshRuns() {
        if (runsAdapter == null) return;
        List<AgentRun> runs;
        try {
            runs = AgentRunStore.list(this);
        } catch (RuntimeException e) {
            runs = new ArrayList<>();
            textTaskHint.setText("Agent history could not be read: " + safeMessage(e));
        }
        runsAdapter.setRuns(runs);
        textRunCount.setText(runs.size() + (runs.size() == 1 ? " task" : " tasks"));
        textNoRuns.setVisibility(runs.isEmpty() ? View.VISIBLE : View.GONE);
        recyclerRuns.setVisibility(runs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showRunDetails(String runId) {
        AgentRun run = AgentRunStore.get(this, runId);
        if (run == null) {
            Toast.makeText(this, "This run is no longer available.", Toast.LENGTH_SHORT).show();
            refreshRuns();
            return;
        }
        StringBuilder body = new StringBuilder();
        body.append("Status: ").append(run.status).append("\nStage: ").append(run.currentStage)
                .append("\nProgress: ").append(run.progress).append("%\n\nTask\n").append(run.prompt);
        if (!TextUtils.isEmpty(run.planOutput)) body.append("\n\n— Planner —\n").append(run.planOutput);
        if (!TextUtils.isEmpty(run.draftOutput)) body.append("\n\n— Builder —\n").append(run.draftOutput);
        if (!TextUtils.isEmpty(run.reviewOutput)) body.append("\n\n— Reviewer / final —\n").append(run.reviewOutput);
        if (!TextUtils.isEmpty(run.finalOutput) && TextUtils.isEmpty(run.reviewOutput)) body.append("\n\n— Result —\n").append(run.finalOutput);
        if (!TextUtils.isEmpty(run.errorMessage)) body.append("\n\n— Error —\n").append(run.errorMessage);
        body.append("\n\n— Stage history —\n");
        if (run.events != null) {
            for (AgentRun.Event event : run.events) {
                body.append("• ").append(event.stage == null ? "Stage" : event.stage).append(": ")
                        .append(event.message == null ? "" : event.message).append('\n');
            }
        }
        final String details = body.length() > 45000
                ? body.substring(0, 45000) + "\n\n[Result display truncated. The saved run remains intact.]"
                : body.toString();
        TextView content = new TextView(this);
        content.setText(details);
        content.setTextIsSelectable(true);
        content.setTextAppearance(R.style.TextAppearance_Material3_BodyMedium);
        content.setTextColor(ThemeUtils.getColor(content, R.attr.colorOnSurface));
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding, padding, padding);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        new MaterialAlertDialogBuilder(this).setTitle(run.title).setView(scroll)
                .setNeutralButton("Copy", (d, w) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    String copyValue = !TextUtils.isEmpty(run.finalOutput) ? run.finalOutput
                            : !TextUtils.isEmpty(run.draftOutput) ? run.draftOutput
                            : !TextUtils.isEmpty(run.planOutput) ? run.planOutput : run.errorMessage;
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Agent result", copyValue == null ? "" : copyValue));
                    Toast.makeText(this, "Result copied", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .setPositiveButton("Build project from run", (d, w) -> startAiProjectGeneration(buildProjectBriefFromRun(run), run.id))
                .show();
    }

    private void cancelRun(AgentRun run) {
        if (run == null || !(AgentRun.QUEUED.equals(run.status) || AgentRun.RUNNING.equals(run.status) || AgentRun.CANCELLING.equals(run.status))) return;
        WorkManager.getInstance(getApplicationContext()).cancelUniqueWork("neo-ai-agent-run-" + run.id);
        run.status = AgentRun.CANCELLED;
        run.record("Cancelled", "Stopped by the user.");
        try { AgentRunStore.save(this, run); } catch (Exception e) { Toast.makeText(this, "Run stopped; history update failed.", Toast.LENGTH_SHORT).show(); }
        refreshRuns();
    }

    private void openProjectWizard(String runId) {
        pendingProjectRunId = runId;
        AgentRun run = TextUtils.isEmpty(runId) ? null : AgentRunStore.get(this, runId);
        String seed = pendingGeneratedProject != null
                ? pendingGeneratedProject.optString("appName", "Generated Project")
                : run == null ? "Generated Project" : run.title;
        String projectName = safeProjectName(seed);
        String slug = projectName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "").trim();
        if (slug.isEmpty() || !Character.isLetter(slug.charAt(0))) slug = "app" + slug;
        if (slug.length() > 22) slug = slug.substring(0, 22);
        String suffix = run != null && run.id != null ? run.id.replace("-", "").substring(0, 6) : Long.toString(System.currentTimeMillis() % 1000000L);
        Intent intent = new Intent(this, MyProjectSettingActivity.class);
        // Pass the same native generated ID that the project wizard would create for a new project;
        // this lets it preserve the agent-provided name/package prefill while still using its save flow.
        intent.putExtra("sc_id", lC.b());
        intent.putExtra("my_ws_name", projectName);
        intent.putExtra("my_app_name", projectName);
        intent.putExtra("my_sc_pkg_name", "com.ai." + slug + suffix);
        createProjectLauncher.launch(intent);
    }

    private void startAiProjectGeneration(String brief, String runId) {
        if (aiProjectGenerationRunning || aiProjectApplyRunning) return;
        String prompt = brief == null ? "" : brief.trim();
        if (prompt.isEmpty()) {
            editTask.setError("Describe the app you want to build first");
            editTask.requestFocus();
            return;
        }
        if (prompt.length() > 24000) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Project brief is too long")
                    .setMessage("Use 24,000 characters or fewer so the generated project can be validated safely.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        if (AiManager.getConfigs(this).isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Configure an AI agent")
                    .setMessage("Add and test at least one AI agent before asking it to implement a project.")
                    .setPositiveButton("Configure", (d, w) -> {
                        startActivity(new Intent(this, AiSettingsActivity.class));
                        finish();
                    }).setNegativeButton("Cancel", null).show();
            return;
        }

        pendingProjectRunId = runId;
        pendingProjectBrief = prompt;
        pendingGeneratedProject = null;
        aiProjectGenerationRunning = true;
        View generateButton = findViewById(R.id.buttonGenerateProject);
        if (generateButton != null) generateButton.setEnabled(false);
        showProjectProgressDialog();
        updateProjectProgress(1, "Understanding your request", "AI is analyzing the requirements and planning the app structure.", true);

        AiModelConfig selectedBuilder = null;
        String selectedBuilderId = selectedAgentId(builderDropdown);
        for (AiModelConfig config : AiManager.getConfigs(this)) {
            if (config.id != null && config.id.equals(selectedBuilderId)) { selectedBuilder = config; break; }
        }
        if (selectedBuilder == null) selectedBuilder = AiManager.getActiveConfig(this);
        AiProjectGenerator.generate(this, selectedBuilder, prompt, new AiResponseCallback() {
            @Override public void onSuccess(String responseText) {
                runOnUiThread(() -> {
                    aiProjectGenerationRunning = false;
                    View button = findViewById(R.id.buttonGenerateProject);
                    if (button != null) button.setEnabled(true);
                    try {
                        pendingGeneratedProject = new JSONObject(responseText);
                        updateProjectProgress(3, "Layouts and logic are ready", "The generated specification passed initial structural checks. Choose the project details to continue.", false);
                        closeProjectProgressDialog();
                        openProjectWizard(pendingProjectRunId);
                    } catch (Exception exception) {
                        pendingGeneratedProject = null;
                        pendingProjectRunId = null;
                        closeProjectProgressDialog();
                        new MaterialAlertDialogBuilder(AgentWorkspaceActivity.this)
                                .setTitle("Could not prepare the generated project")
                                .setMessage(safeMessage(exception))
                                .setPositiveButton("OK", null).show();
                    }
                });
            }

            @Override public void onFailure(String errorMessage) {
                runOnUiThread(() -> {
                    aiProjectGenerationRunning = false;
                    View button = findViewById(R.id.buttonGenerateProject);
                    if (button != null) button.setEnabled(true);
                    closeProjectProgressDialog();
                    pendingProjectRunId = null;
                    pendingProjectBrief = null;
                    new MaterialAlertDialogBuilder(AgentWorkspaceActivity.this)
                            .setTitle("Project generation failed")
                            .setMessage(TextUtils.isEmpty(errorMessage) ? "The AI did not return a valid project specification." : errorMessage)
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private String buildProjectBriefFromRun(AgentRun run) {
        if (run == null) return editTask.getText() == null ? "" : editTask.getText().toString();
        StringBuilder brief = new StringBuilder();
        brief.append("Build a working Android application from this saved AI-agent task. Implement the best coherent MVP; do not return only a plan.\n\n")
                .append("Original request:\n").append(run.prompt == null ? "" : run.prompt).append("\n\n");
        if (!TextUtils.isEmpty(run.planOutput)) brief.append("Plan:\n").append(run.planOutput).append("\n\n");
        if (!TextUtils.isEmpty(run.draftOutput)) brief.append("Builder implementation proposal:\n").append(run.draftOutput).append("\n\n");
        if (!TextUtils.isEmpty(run.reviewOutput)) brief.append("Reviewer notes and final requirements:\n").append(run.reviewOutput).append("\n\n");
        else if (!TextUtils.isEmpty(run.finalOutput)) brief.append("Final output:\n").append(run.finalOutput).append("\n\n");
        String result = brief.toString().trim();
        if (result.length() > 23000) result = result.substring(0, 23000);
        return result;
    }

    private void showProjectProgressDialog() {
        closeProjectProgressDialog();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_ai_project_progress, null, false);
        projectProgressTitle = content.findViewById(R.id.textAiProjectProgressTitle);
        projectProgressDetail = content.findViewById(R.id.textAiProjectProgressDetail);
        projectProgressBar = content.findViewById(R.id.progressAiProjectGeneration);
        projectStageLabels[0] = content.findViewById(R.id.textAiProjectStage1);
        projectStageLabels[1] = content.findViewById(R.id.textAiProjectStage2);
        projectStageLabels[2] = content.findViewById(R.id.textAiProjectStage3);
        projectStageLabels[3] = content.findViewById(R.id.textAiProjectStage4);
        projectStageLabels[4] = content.findViewById(R.id.textAiProjectStage5);
        projectStageLabels[5] = content.findViewById(R.id.textAiProjectStage6);
        projectProgressDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setCancelable(false)
                .create();
        projectProgressDialog.show();
    }

    private void updateProjectProgress(int stage, String title, String detail, boolean indeterminate) {
        if (projectProgressDialog == null || !projectProgressDialog.isShowing()) return;
        if (projectProgressTitle != null) projectProgressTitle.setText(title == null ? "Working on your project" : title);
        if (projectProgressDetail != null) projectProgressDetail.setText(detail == null ? "" : detail);
        if (projectProgressBar != null) {
            projectProgressBar.setIndeterminate(indeterminate);
            if (!indeterminate) projectProgressBar.setProgressCompat(Math.min(100, Math.max(0, stage * 16)), true);
        }
        String[] labels = {
                "Understanding request", "Planning screens and flows", "Generating layouts and logic",
                "Packaging native screen data", "Applying files and resources", "Preparing build verification"
        };
        int currentColor = ThemeUtils.getColor(this, R.attr.colorPrimary);
        int normalColor = ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant);
        for (int i = 0; i < projectStageLabels.length; i++) {
            TextView label = projectStageLabels[i];
            if (label == null) continue;
            String prefix = i + 1 < stage ? "✓  " : i + 1 == stage ? "●  " : "○  ";
            label.setText(prefix + labels[i]);
            label.setTextColor(i + 1 <= stage ? currentColor : normalColor);
            label.setTypeface(null, i + 1 == stage ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void closeProjectProgressDialog() {
        if (projectProgressDialog != null) {
            try { projectProgressDialog.dismiss(); } catch (Exception ignored) { }
        }
        projectProgressDialog = null;
        projectProgressBar = null;
        projectProgressTitle = null;
        projectProgressDetail = null;
        for (int i = 0; i < projectStageLabels.length; i++) projectStageLabels[i] = null;
    }

    private void applyPendingGeneratedProject(String scId, JSONObject specification, String runId) {
        if (aiProjectApplyRunning) return;
        aiProjectApplyRunning = true;
        showProjectProgressDialog();
        updateProjectProgress(3, "Packaging native screen data", "Validating layouts and converting them into Sketchware Neo's screen format.", false);
        Thread worker = new Thread(() -> {
            AiProjectApplier.Result result = AiProjectApplier.apply(getApplicationContext(), scId, specification,
                    (stage, title, detail) -> runOnUiThread(() -> updateProjectProgress(stage, title, detail, false)));
            runOnUiThread(() -> {
                aiProjectApplyRunning = false;
                closeProjectProgressDialog();
                AgentRun run = TextUtils.isEmpty(runId) ? null : AgentRunStore.get(this, runId);
                if (!result.success) {
                    if (run != null) {
                        run.errorMessage = result.message;
                        run.record("Implementation failed", result.message);
                        try { AgentRunStore.save(this, run); } catch (Exception ignored) { }
                    }
                    pendingGeneratedProject = null;
                    pendingProjectBrief = null;
                    pendingProjectRunId = null;
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Project implementation needs attention")
                            .setMessage(result.message + "\n\nThe native importer validates changes before adding screens. Open the project to inspect any files already saved.")
                            .setNegativeButton("Close", null)
                            .setPositiveButton("Open project", (d, w) -> openProjectInEditor(scId, false))
                            .show();
                    return;
                }

                if (run != null) {
                    run.createdProjectId = scId;
                    run.record("Native implementation applied", result.message);
                    try {
                        writeProjectBlueprint(scId, run);
                        AgentRunStore.save(this, run);
                    } catch (Exception exception) {
                        run.record("Blueprint note", "The app screens were created, but saving the agent note failed: " + safeMessage(exception));
                        try { AgentRunStore.save(this, run); } catch (Exception ignored) { }
                    }
                }
                Toast.makeText(this, "Project implementation saved. Starting the build check…", Toast.LENGTH_LONG).show();
                pendingGeneratedProject = null;
                pendingProjectBrief = null;
                pendingProjectRunId = null;
                openProjectInEditor(scId, true);
            });
        }, "neo-ai-project-apply");
        worker.start();
    }

    private void openProjectInEditor(String scId, boolean autoBuild) {
        ProjectTracker.setScId(scId);
        Intent open = new Intent(this, DesignActivity.class);
        open.putExtra("sc_id", scId);
        if (autoBuild) open.putExtra("auto_build", true);
        open.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(open);
        pendingGeneratedProject = null;
        pendingProjectBrief = null;
        pendingProjectRunId = null;
        finish();
    }

    private void writeProjectBlueprint(String scId, AgentRun run) throws Exception {
        // Store outside the app's compiled /files resources so the blueprint cannot affect builds.
        File aiFolder = new File(wq.b(scId), "ai");
        if (!aiFolder.exists() && !aiFolder.mkdirs() && !aiFolder.isDirectory()) {
            throw new IllegalStateException("Cannot create project AI folder");
        }
        File target = new File(aiFolder, "agent-blueprint.md");
        File temp = new File(aiFolder, "agent-blueprint.md.tmp");
        File backup = new File(aiFolder, "agent-blueprint.md.bak");
        if (temp.exists() && !temp.delete()) throw new IllegalStateException("Cannot clear temporary blueprint");
        StringBuilder out = new StringBuilder();
        out.append("# AI Agent Project Blueprint\n\n")
                .append("Generated by Sketchware Neo AI Agent Workspace. This file preserves the project brief and agent notes.\n\n")
                .append("## Original task\n\n").append(run.prompt == null ? "" : run.prompt).append("\n\n");
        if (pendingGeneratedProject != null) {
            out.append("## Native implementation\n\n")
                    .append(pendingGeneratedProject.optString("summary", "AI-generated native screen implementation."))
                    .append("\n\n");
            JSONArray generatedScreens = pendingGeneratedProject.optJSONArray("screens");
            if (generatedScreens != null) {
                out.append("Screens: ");
                for (int i = 0; i < generatedScreens.length(); i++) {
                    JSONObject screen = generatedScreens.optJSONObject(i);
                    if (screen != null) {
                        if (i > 0) out.append(", ");
                        out.append(screen.optString("name", "screen_" + (i + 1)));
                    }
                }
                out.append("\n\n");
            }
        }
        if (!TextUtils.isEmpty(run.planOutput)) out.append("## Plan\n\n").append(run.planOutput).append("\n\n");
        if (!TextUtils.isEmpty(run.finalOutput)) out.append("## Final deliverable\n\n").append(run.finalOutput).append("\n\n");
        else if (!TextUtils.isEmpty(run.draftOutput)) out.append("## Builder draft\n\n").append(run.draftOutput).append("\n\n");
        try (FileOutputStream stream = new FileOutputStream(temp);
             OutputStreamWriter writer = new OutputStreamWriter(stream, StandardCharsets.UTF_8)) {
            writer.write(out.toString());
            writer.flush();
            stream.getFD().sync();
        }
        if (backup.exists() && !backup.delete()) throw new IllegalStateException("Cannot clear previous blueprint backup");
        if (target.exists() && !target.renameTo(backup)) throw new IllegalStateException("Cannot preserve previous blueprint");
        if (!temp.renameTo(target)) {
            if (backup.exists()) backup.renameTo(target);
            temp.delete();
            throw new IllegalStateException("Cannot commit blueprint file");
        }
        if (backup.exists()) backup.delete();
    }

    private String makeTitle(String prompt) {
        String cleaned = prompt.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 70) cleaned = cleaned.substring(0, 67).trim() + "…";
        return TextUtils.isEmpty(cleaned) ? "Agent task" : cleaned;
    }

    private String safeProjectName(String value) {
        String cleaned = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").replaceAll("[^A-Za-z0-9 _-]", "").trim();
        while (cleaned.contains("  ")) cleaned = cleaned.replace("  ", " ");
        if (cleaned.length() > 30) cleaned = cleaned.substring(0, 30).trim();
        return cleaned.isEmpty() ? "Generated Project" : cleaned;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return TextUtils.isEmpty(message) ? e.getClass().getSimpleName() : message;
    }

    private static class AgentChoice {
        final String id;
        final String label;
        AgentChoice(String id, String label) { this.id = id; this.label = label; }
    }

    private class RunsAdapter extends RecyclerView.Adapter<RunsAdapter.RunHolder> {
        private final List<AgentRun> items = new ArrayList<>();

        void setRuns(List<AgentRun> runs) {
            items.clear();
            if (runs != null) items.addAll(runs);
            notifyDataSetChanged();
        }

        @NonNull @Override public RunHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RunHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_agent_run, parent, false));
        }

        @Override public void onBindViewHolder(@NonNull RunHolder holder, int position) {
            AgentRun run = items.get(position);
            holder.title.setText(run.title == null ? "Agent task" : run.title);
            holder.status.setText(run.status == null ? "UNKNOWN" : run.status);
            holder.stage.setText(run.currentStage == null ? "Waiting for agent" : run.currentStage);
            holder.progress.setProgress(Math.max(0, Math.min(100, run.progress)));
            String summary = !TextUtils.isEmpty(run.errorMessage) ? run.errorMessage
                    : !TextUtils.isEmpty(run.finalOutput) ? run.finalOutput
                    : !TextUtils.isEmpty(run.prompt) ? run.prompt : "No task details";
            if (summary.length() > 220) summary = summary.substring(0, 217) + "…";
            holder.summary.setText(summary);
            boolean active = AgentRun.QUEUED.equals(run.status) || AgentRun.RUNNING.equals(run.status) || AgentRun.CANCELLING.equals(run.status);
            holder.cancel.setVisibility(active ? View.VISIBLE : View.GONE);
            holder.card.setOnClickListener(v -> showRunDetails(run.id));
            holder.view.setOnClickListener(v -> showRunDetails(run.id));
            holder.cancel.setOnClickListener(v -> cancelRun(run));
        }

        @Override public int getItemCount() { return items.size(); }

        class RunHolder extends RecyclerView.ViewHolder {
            com.google.android.material.card.MaterialCardView card;
            TextView title, status, stage, summary;
            com.google.android.material.progressindicator.LinearProgressIndicator progress;
            com.google.android.material.button.MaterialButton cancel, view;
            RunHolder(View itemView) {
                super(itemView);
                card = itemView.findViewById(R.id.cardAgentRun);
                title = itemView.findViewById(R.id.textRunTitle);
                status = itemView.findViewById(R.id.textRunStatus);
                stage = itemView.findViewById(R.id.textRunStage);
                summary = itemView.findViewById(R.id.textRunSummary);
                progress = itemView.findViewById(R.id.progressRun);
                cancel = itemView.findViewById(R.id.buttonCancelRun);
                view = itemView.findViewById(R.id.buttonViewRun);
            }
        }
    }
}
