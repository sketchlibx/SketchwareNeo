package neo.sketchware.ai;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.R;

public class BlockGenerationActivity extends BaseAppCompatActivity {

    public static final String EXTRA_TOPIC = "topic";
    public static final String EXTRA_PROMPT = "prompt";
    public static final String EXTRA_EXISTING_NAMES = "existingNames"; // comma-separated
    public static final String EXTRA_RESULT_BLOCKS_JSON = "resultBlocksJson";

    private static final int CHUNK_SIZE = 4;

    private enum Stage { PREPARING, PLANNING, GENERATING, VALIDATING, PREVIEW, ERROR }

    private TextView textStage;
    private TextView textDetail;
    private TextView textRequestSummary;
    private CircularProgressIndicator spinner;
    private LinearProgressIndicator chunkProgress;
    private View progressGroup;
    private View errorGroup;
    private TextView textErrorMessage;
    private MaterialButton buttonRetry;
    private MaterialButton buttonCancel;
    private View previewGroup;
    private RecyclerView recyclerPreview;
    private TextView textPreviewSummary;
    private MaterialButton buttonImport;

    private String topic;
    private String userPrompt;
    private final Set<String> existingNames = new HashSet<>();
    private final Set<String> allGeneratedNames = new HashSet<>();

    private BlockPlanEntry.Plan plan;
    private List<List<BlockPlanEntry>> chunks;
    private int currentChunkIndex;
    private String lastPlanJson;

    private final List<JSONObject> validBlocks = new ArrayList<>();
    private final List<String> invalidReports = new ArrayList<>();
    private final Set<String> checkedNames = new HashSet<>();
    private PreviewAdapter previewAdapter;
    private volatile boolean cancelled = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_generation);

        MaterialToolbar toolbar = findViewById(R.id.topAppBar);
        toolbar.setNavigationOnClickListener(v -> { cancelled = true; finish(); });

        textStage = findViewById(R.id.textStage);
        textDetail = findViewById(R.id.textDetail);
        textRequestSummary = findViewById(R.id.textRequestSummary);
        spinner = findViewById(R.id.spinner);
        chunkProgress = findViewById(R.id.chunkProgress);
        progressGroup = findViewById(R.id.progressGroup);
        errorGroup = findViewById(R.id.errorGroup);
        textErrorMessage = findViewById(R.id.textErrorMessage);
        buttonRetry = findViewById(R.id.buttonRetry);
        buttonCancel = findViewById(R.id.buttonCancel);
        previewGroup = findViewById(R.id.previewGroup);
        recyclerPreview = findViewById(R.id.recyclerPreview);
        textPreviewSummary = findViewById(R.id.textPreviewSummary);
        buttonImport = findViewById(R.id.buttonImport);

        topic = getIntent().getStringExtra(EXTRA_TOPIC);
        userPrompt = getIntent().getStringExtra(EXTRA_PROMPT);
        String existing = getIntent().getStringExtra(EXTRA_EXISTING_NAMES);
        if (existing != null) {
            for (String n : existing.split(",")) {
                if (!n.trim().isEmpty()) existingNames.add(n.trim());
            }
        }

        textRequestSummary.setText((topic != null && !topic.isEmpty() ? topic + " — " : "") + userPrompt);

        recyclerPreview.setLayoutManager(new LinearLayoutManager(this));
        previewAdapter = new PreviewAdapter();
        recyclerPreview.setAdapter(previewAdapter);

        buttonCancel.setOnClickListener(v -> { cancelled = true; finish(); });
        buttonImport.setOnClickListener(v -> approveAndFinish());

        startPipeline();
    }

    private void startPipeline() {
        showStage(Stage.PREPARING, "Preparing request...", null);
        showStage(Stage.PLANNING, "Understanding the SDK and planning the blocks needed...", null);

        BlockPlanTask.plan(this, topic, userPrompt, new AiResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                if (cancelled) return;
                try {
                    plan = BlockPlanEntry.parse(responseText);
                    lastPlanJson = responseText;
                    if (plan.entries.isEmpty()) {
                        showError("AI didn't plan any blocks. Try describing the request differently.", this::retryPlanning);
                        return;
                    }
                    chunks = buildChunks(plan.entries);
                    currentChunkIndex = 0;
                    generateNextChunk();
                } catch (JSONException e) {
                    showError("Couldn't understand the plan AI returned: " + e.getMessage(), BlockGenerationActivity.this::retryPlanning);
                }
            }

            @Override
            public void onFailure(String errorMessage) {
                if (cancelled) return;
                showError("Planning failed: " + errorMessage, BlockGenerationActivity.this::retryPlanning);
            }
        });
    }

    private void retryPlanning() {
        startPipeline();
    }

    /** Groups plan entries by category (keeping a listener_wrapper with its callbacks together
     *  where possible) into chunks no larger than CHUNK_SIZE. */
    private List<List<BlockPlanEntry>> buildChunks(List<BlockPlanEntry> entries) {
        Map<String, List<BlockPlanEntry>> byCategory = new java.util.LinkedHashMap<>();
        for (BlockPlanEntry e : entries) {
            byCategory.computeIfAbsent(e.category, k -> new ArrayList<>()).add(e);
        }
        List<List<BlockPlanEntry>> result = new ArrayList<>();
        for (List<BlockPlanEntry> group : byCategory.values()) {
            for (int i = 0; i < group.size(); i += CHUNK_SIZE) {
                result.add(new ArrayList<>(group.subList(i, Math.min(i + CHUNK_SIZE, group.size()))));
            }
        }
        return result;
    }

    private void generateNextChunk() {
        if (cancelled) return;
        if (currentChunkIndex >= chunks.size()) {
            finalizeAndShowPreview();
            return;
        }
        int totalChunks = chunks.size();
        showStage(Stage.GENERATING, "Generating blocks " + (currentChunkIndex + 1) + " of " + totalChunks + "...",
                chunks.get(currentChunkIndex).size() + " block(s) in this batch");
        chunkProgress.setVisibility(View.VISIBLE);
        chunkProgress.setMax(totalChunks);
        chunkProgress.setProgress(currentChunkIndex);

        List<BlockPlanEntry> chunk = chunks.get(currentChunkIndex);
        JSONArray chunkJson = new JSONArray();
        try {
            for (BlockPlanEntry e : chunk) {
                JSONObject o = new JSONObject();
                o.put("name", e.name);
                o.put("role", e.role);
                o.put("purpose", e.purpose);
                if (e.parentListener != null) o.put("parentListener", e.parentListener);
                if (e.exposesValueName != null) {
                    JSONObject ev = new JSONObject();
                    ev.put("name", e.exposesValueName);
                    ev.put("typeName", e.exposesValueTypeName);
                    o.put("exposesValue", ev);
                }
                chunkJson.put(o);
            }
        } catch (JSONException ignored) {}

        Set<String> namesToAvoid = new HashSet<>(existingNames);
        namesToAvoid.addAll(allGeneratedNames);

        MultiBlockGenTask.generateChunk(this, lastPlanJson, chunkJson.toString(), String.join(", ", namesToAvoid), new AiResponseCallback() {
            @Override
            public void onSuccess(String responseText) {
                if (cancelled) return;
                processChunkResponse(responseText, chunk);
            }

            @Override
            public void onFailure(String errorMessage) {
                if (cancelled) return;
                showError("Generating batch " + (currentChunkIndex + 1) + "/" + totalChunks + " failed: " + errorMessage,
                        BlockGenerationActivity.this::generateNextChunk);
            }
        });
    }

    private void processChunkResponse(String responseText, List<BlockPlanEntry> chunk) {
        String jsonText = responseText.trim();
        int start = jsonText.indexOf('{');
        int end = jsonText.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            invalidReports.add("Batch " + (currentChunkIndex + 1) + ": AI response wasn't valid JSON, batch skipped");
            currentChunkIndex++;
            generateNextChunk();
            return;
        }
        JSONArray blocksArray;
        try {
            JSONObject root = new JSONObject(jsonText.substring(start, end + 1));
            blocksArray = root.getJSONArray("blocks");
        } catch (JSONException e) {
            invalidReports.add("Batch " + (currentChunkIndex + 1) + ": didn't match {\"blocks\":[...]}, batch skipped");
            currentChunkIndex++;
            generateNextChunk();
            return;
        }

        Map<String, BlockPlanEntry> byName = new HashMap<>();
        for (BlockPlanEntry e : chunk) byName.put(e.name, e);

        for (int i = 0; i < blocksArray.length(); i++) {
            JSONObject b;
            try {
                b = blocksArray.getJSONObject(i);
            } catch (JSONException e) {
                invalidReports.add("Batch " + (currentChunkIndex + 1) + " item " + (i + 1) + ": not a JSON object");
                continue;
            }
            String name = b.optString("name", "");
            BlockPlanEntry planEntry = byName.get(name);
            GeneratedBlockValidator.Result result = GeneratedBlockValidator.validate(b, planEntry, existingNames, allGeneratedNames);
            if (!result.ok) {
                invalidReports.add((name.isEmpty() ? "Unnamed block" : name) + ": " + result.error);
                continue;
            }
            validBlocks.add(b);
            allGeneratedNames.add(name);

            if (planEntry != null && planEntry.exposesValueName != null) {
                try {
                    JSONObject getter = GeneratedBlockValidator.buildValueGetterBlock(planEntry, b.optString("color", "#4A90D9"));
                    if (!allGeneratedNames.contains(planEntry.exposesValueName) && !existingNames.contains(planEntry.exposesValueName)) {
                        validBlocks.add(getter);
                        allGeneratedNames.add(planEntry.exposesValueName);
                    }
                } catch (JSONException ignored) {}
            }
        }

        currentChunkIndex++;
        generateNextChunk();
    }

    private void finalizeAndShowPreview() {
        showStage(Stage.VALIDATING, "Checking the full set for consistency...", null);
        chunkProgress.setVisibility(View.GONE);

        checkedNames.clear();
        for (JSONObject b : validBlocks) checkedNames.add(b.optString("name"));

        stage = Stage.PREVIEW;
        progressGroup.setVisibility(View.GONE);
        errorGroup.setVisibility(View.GONE);
        previewGroup.setVisibility(View.VISIBLE);

        int issues = invalidReports.size();
        textPreviewSummary.setText(validBlocks.size() + " block(s) ready" + (issues > 0 ? ", " + issues + " skipped (see below)" : ""));
        previewAdapter.notifyDataSetChanged();
        buttonImport.setText("Import " + checkedNames.size() + " block(s)");
        buttonImport.setEnabled(!validBlocks.isEmpty());
    }

    private Stage stage = Stage.PREPARING;

    private void showStage(Stage s, String detail, String extra) {
        stage = s;
        progressGroup.setVisibility(View.VISIBLE);
        errorGroup.setVisibility(View.GONE);
        previewGroup.setVisibility(View.GONE);
        String label;
        switch (s) {
            case PREPARING: label = "Preparing request"; break;
            case PLANNING: label = "Planning blocks"; break;
            case GENERATING: label = "Generating blocks"; break;
            case VALIDATING: label = "Validating schema"; break;
            default: label = s.name();
        }
        textStage.setText(label);
        textDetail.setText(detail + (extra != null ? "\n" + extra : ""));
    }

    private void showError(String message, Runnable retryAction) {
        stage = Stage.ERROR;
        progressGroup.setVisibility(View.GONE);
        previewGroup.setVisibility(View.GONE);
        errorGroup.setVisibility(View.VISIBLE);
        textErrorMessage.setText(message);
        buttonRetry.setOnClickListener(v -> retryAction.run());
    }

    private void approveAndFinish() {
        JSONArray array = new JSONArray();
        for (JSONObject b : validBlocks) {
            if (checkedNames.contains(b.optString("name"))) array.put(b);
        }
        if (array.length() == 0) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Nothing selected")
                    .setMessage("Select at least one block to import.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        JSONObject result = new JSONObject();
        try {
            result.put("blocks", array);
        } catch (JSONException ignored) {}

        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_BLOCKS_JSON, result.toString());
        setResult(RESULT_OK, data);
        finish();
    }

    // --- Preview list: grouped by category, each row a checkbox + name + purpose, plus a
    // trailing "Issues" section listing anything that was skipped. ---

    private static final int VIEW_HEADER = 0;
    private static final int VIEW_BLOCK = 1;
    private static final int VIEW_ISSUE_HEADER = 2;
    private static final int VIEW_ISSUE = 3;

    private class PreviewAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final List<Object> rows = new ArrayList<>(); // String (header) | JSONObject (block) | "ISSUES" marker | String (issue text)

        void rebuild() {
            rows.clear();
            Map<String, List<JSONObject>> byCategory = new java.util.LinkedHashMap<>();
            for (JSONObject b : validBlocks) {
                BlockPlanEntry entry = findPlanEntry(b.optString("name"));
                String category = entry != null ? entry.category : "Other";
                byCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(b);
            }
            for (Map.Entry<String, List<JSONObject>> e : byCategory.entrySet()) {
                rows.add(e.getKey());
                rows.addAll(e.getValue());
            }
            if (!invalidReports.isEmpty()) {
                rows.add("ISSUES_HEADER");
                rows.addAll(invalidReports);
            }
        }

        private BlockPlanEntry findPlanEntry(String name) {
            if (plan == null) return null;
            for (BlockPlanEntry e : plan.entries) if (e.name.equals(name)) return e;
            return null;
        }

        @Override
        public void notifyDataSetChanged() {
            rebuild();
            super.notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            Object row = rows.get(position);
            if ("ISSUES_HEADER".equals(row)) return VIEW_ISSUE_HEADER;
            if (row instanceof JSONObject) return VIEW_BLOCK;
            if (row instanceof String && invalidReports.contains(row)) return VIEW_ISSUE;
            return VIEW_HEADER; // a category name
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            if (viewType == VIEW_BLOCK) {
                return new BlockRowHolder(inflater.inflate(R.layout.item_generated_block, parent, false));
            } else if (viewType == VIEW_ISSUE) {
                TextView tv = new TextView(parent.getContext());
                int pad = (int) (16 * getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad / 2, pad, pad / 2);
                tv.setTextColor(pro.sketchware.utility.ThemeUtils.getColor(parent.getContext(), com.google.android.material.R.attr.colorError));
                return new SimpleTextHolder(tv);
            } else {
                TextView tv = new TextView(parent.getContext());
                int padH = (int) (16 * getResources().getDisplayMetrics().density);
                int padV = (int) (12 * getResources().getDisplayMetrics().density);
                tv.setPadding(padH, padV, padH, padV / 2);
                tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
                tv.setTextColor(pro.sketchware.utility.ThemeUtils.getColor(parent.getContext(), com.google.android.material.R.attr.colorPrimary));
                return new SimpleTextHolder(tv);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object row = rows.get(position);
            if (holder instanceof BlockRowHolder) {
                JSONObject b = (JSONObject) row;
                BlockRowHolder h = (BlockRowHolder) holder;
                String name = b.optString("name");
                h.checkBox.setOnCheckedChangeListener(null);
                h.checkBox.setChecked(checkedNames.contains(name));
                h.textName.setText(name);
                BlockPlanEntry entry = findPlanEntry(name);
                h.textPurpose.setText(entry != null ? entry.purpose : b.optString("spec"));
                h.textRole.setText(entry != null ? entry.role : b.optString("type"));
                h.checkBox.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) checkedNames.add(name); else checkedNames.remove(name);
                    buttonImport.setText("Import " + checkedNames.size() + " block(s)");
                    buttonImport.setEnabled(!checkedNames.isEmpty());
                });
            } else if (holder instanceof SimpleTextHolder) {
                String text = "ISSUES_HEADER".equals(row) ? "Skipped (not imported)" : String.valueOf(row);
                ((SimpleTextHolder) holder).textView.setText(text);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private static class BlockRowHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView textName;
        TextView textPurpose;
        TextView textRole;
        BlockRowHolder(View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkBoxBlock);
            textName = itemView.findViewById(R.id.textBlockName);
            textPurpose = itemView.findViewById(R.id.textBlockPurpose);
            textRole = itemView.findViewById(R.id.textBlockRole);
        }
    }

    private static class SimpleTextHolder extends RecyclerView.ViewHolder {
        TextView textView;
        SimpleTextHolder(View itemView) {
            super(itemView);
            this.textView = (TextView) itemView;
        }
    }
}
