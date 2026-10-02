package neo.sketchware.ai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import neo.sketchware.ai.local.LocalModel;
import neo.sketchware.ai.local.LocalModelDownloadWorker;
import neo.sketchware.ai.local.LocalModelManager;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityAiChatBinding;
import pro.sketchware.databinding.ActivityAiSettingsBinding;
import pro.sketchware.databinding.ActivityLocalModelsBinding;

public class AiSettingsActivity extends BaseAppCompatActivity implements AiModelAdapter.Listener {

    private static final int PICK_GGUF = 4321;

    private ActivityAiSettingsBinding settingsBinding;
    private ActivityLocalModelsBinding localModelsBinding;
    private ActivityAiChatBinding chatBinding;

    private AiModelAdapter adapter;
    private int filter = 0;
    private ActivityResultLauncher<Intent> editorLauncher;
    private int screen = 0;

    private TextView pendingChat;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && screen == 0) {
                        showAgents();
                    }
                }
        );
        showAgents();
    }

    @Override
    public void onBackPressed() {
        if (screen == 0) {
            super.onBackPressed();
        } else {
            showAgents();
        }
    }

    private void baseToolbar(MaterialToolbar toolbar, String title, View.OnClickListener back) {
        if (toolbar != null) {
            toolbar.setTitle(title);
            toolbar.setNavigationOnClickListener(back);
        }
    }

    private void showAgents() {
        screen = 0;
        settingsBinding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(settingsBinding.getRoot());
        baseToolbar(settingsBinding.topAppBar, "AI Agents", v -> finish());

        settingsBinding.recyclerViewAiModels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AiModelAdapter(this);
        settingsBinding.recyclerViewAiModels.setAdapter(adapter);

        settingsBinding.buttonAddAgent.setOnClickListener(v -> editorLauncher.launch(new Intent(this, AiModelEditActivity.class)));
        settingsBinding.navAiChat.setOnClickListener(v -> showChat());
        settingsBinding.navAiAgents.setOnClickListener(v -> showAgents());
        settingsBinding.navAiSettings.setOnClickListener(v -> showAiPreferences());
        settingsBinding.buttonLocalModels.setOnClickListener(v -> showLocalModels());
        settingsBinding.buttonChat.setOnClickListener(v -> showChat());

        settingsBinding.buttonTestActive.setOnClickListener(v -> testActive());
        
        settingsBinding.tabAll.setOnClickListener(v -> {
            filter = 0;
            refreshAgents();
        });
        
        settingsBinding.tabLocal.setOnClickListener(v -> {
            filter = 1;
            refreshAgents();
        });
        
        settingsBinding.tabCloud.setOnClickListener(v -> {
            filter = 2;
            refreshAgents();
        });

        refreshAgents();
    }

    private void refreshAgents() {
        if (screen != 0 || settingsBinding == null) return;

        List<AiModelConfig> all = AiManager.getConfigs(this);
        List<AiModelConfig> shown = new ArrayList<>();
        
        int localCount = 0;
        int cloudCount = 0;

        for (AiModelConfig c : all) {
            AiProvider p = AiProviderRegistry.get(c.providerId);
            boolean isLocal = p != null && p.isLocal();
            
            if (isLocal) {
                localCount++;
            } else {
                cloudCount++;
            }

            if (filter == 0 || (filter == 1 && isLocal) || (filter == 2 && !isLocal)) {
                shown.add(c);
            }
        }

        adapter.submitList(shown, AiManager.getActiveConfigId(this));
        
        settingsBinding.tabAll.setChecked(filter == 0);
        settingsBinding.tabLocal.setChecked(filter == 1);
        settingsBinding.tabCloud.setChecked(filter == 2);
        
        AiModelConfig active = AiManager.getActiveConfig(this);
        String activeLabel = active == null ? "No active agent" : (active.displayName + " · " + active.modelName);
        settingsBinding.textAgentSummary.setText(activeLabel + "\n" + localCount + " local • " + cloudCount + " cloud agents");
        
        settingsBinding.buttonTestActive.setEnabled(active != null);
    }

    private void testActive() {
        if (settingsBinding == null) return;
        
        settingsBinding.buttonTestActive.setEnabled(false);
        settingsBinding.buttonTestActive.setText("Testing…");
        
        AiManager.testActive(this, new AiResponseCallback() {
            @Override
            public void onSuccess(String s) {
                runOnUiThread(() -> {
                    if (settingsBinding != null) {
                        settingsBinding.buttonTestActive.setEnabled(true);
                        settingsBinding.buttonTestActive.setText("Test AI");
                    }
                    new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                            .setTitle("AI test successful")
                            .setMessage(s)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }

            @Override
            public void onFailure(String e) {
                runOnUiThread(() -> {
                    if (settingsBinding != null) {
                        settingsBinding.buttonTestActive.setEnabled(true);
                        settingsBinding.buttonTestActive.setText("Test AI");
                    }
                    new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                            .setTitle("AI test failed")
                            .setMessage(e)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private MaterialCardView card() {
        MaterialCardView c = new MaterialCardView(this);
        c.setRadius(24);
        c.setCardElevation(0);
        c.setStrokeWidth(1);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOutlineVariant, tv, true);
        c.setStrokeColor(tv.data);
        c.setUseCompatPadding(false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, 0, 0, 16);
        c.setLayoutParams(lp);
        return c;
    }

    private TextView text(String s, int appearance) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextAppearance(appearance);
        android.util.TypedValue tv = new android.util.TypedValue();
        getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, tv, true);
        t.setTextColor(tv.data);
        return t;
    }

    private void showAiPreferences() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(12, 0, 12, 0);

        MaterialSwitch auto = new MaterialSwitch(this);
        auto.setText("Use active agent for existing AI generation");
        auto.setChecked(getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("auto_agent", true));

        MaterialSwitch remember = new MaterialSwitch(this);
        remember.setText("Remember chat history on this device");
        remember.setChecked(getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("remember_chat", true));

        MaterialSwitch localFirst = new MaterialSwitch(this);
        localFirst.setText("Prefer local agent when available");
        localFirst.setChecked(getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("prefer_local", false));

        box.addView(auto);
        box.addView(remember);
        box.addView(localFirst);

        new MaterialAlertDialogBuilder(this)
                .setTitle("AI Settings")
                .setMessage("Control how AI Agents integrate with Sketchware Neo.")
                .setView(box)
                .setPositiveButton("Save", (d, w) -> {
                    getSharedPreferences("ai_prefs", MODE_PRIVATE).edit()
                            .putBoolean("auto_agent", auto.isChecked())
                            .putBoolean("remember_chat", remember.isChecked())
                            .putBoolean("prefer_local", localFirst.isChecked())
                            .apply();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLocalModels() {
        screen = 1;
        localModelsBinding = ActivityLocalModelsBinding.inflate(getLayoutInflater());
        setContentView(localModelsBinding.getRoot());
        
        baseToolbar(localModelsBinding.topAppBar, "Local AI Models", v -> showAgents());
        
        localModelsBinding.buttonImportGguf.setOnClickListener(v -> pickGguf());
        localModelsBinding.buttonConnectServer.setOnClickListener(v -> editorLauncher.launch(new Intent(this, AiModelEditActivity.class)));
        
        renderLocalList();
    }

    private void renderLocalList() {
        if (localModelsBinding == null) return;
        
        LinearLayout list = localModelsBinding.localModelList;
        list.removeAllViews();
        
        List<File> files = LocalModelManager.installed(this);
        localModelsBinding.textInstalled.setText(files.isEmpty() ? "Installed models · none" : "Installed models · " + files.size());
        
        for (File f : files) {
            MaterialCardView c = card();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            
            TextView t = text("Local GGUF\n" + f.getName() + "\n" + (f.length() / 1024 / 1024) + " MB", com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
            
            MaterialButton del = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            del.setText("Delete");
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete model?")
                    .setMessage(f.getName())
                    .setPositiveButton("Delete", (d, w) -> {
                        f.delete();
                        renderLocalList();
                    })
                    .setNegativeButton("Cancel", null)
                    .show());
            
            row.addView(del);
            c.addView(row);
            list.addView(c);
        }
        
        for (LocalModel m : LocalModelManager.catalog()) {
            addCatalogCard(list, m);
        }
    }

    private void addCatalogCard(LinearLayout list, LocalModel m) {
        MaterialCardView c = card();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(16, 16, 16, 16);
        
        TextView t = text(m.name + "\n" + m.publisher + " • " + m.sizeLabel + "\n" + m.description, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        box.addView(t);
        
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        
        MaterialButton cfg = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        cfg.setText("Configure");
        cfg.setOnClickListener(v -> showLocalDetails(m));
        
        MaterialButton dl = new MaterialButton(this);
        dl.setText(m.downloadUrl.isEmpty() ? "Download URL" : "Download");
        dl.setOnClickListener(v -> askDownload(m));
        
        actions.addView(cfg);
        actions.addView(dl);
        box.addView(actions);
        c.addView(box);
        list.addView(c);
    }

    private void showLocalDetails(LocalModel m) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(18, 0, 18, 0);
        
        box.addView(text(m.name + "\n" + m.publisher + " • " + m.sizeLabel, com.google.android.material.R.style.TextAppearance_Material3_TitleMedium));
        box.addView(text("Model\nSettings\nAdvanced", com.google.android.material.R.style.TextAppearance_Material3_TitleSmall));
        
        TextInputLayout pathLayout = new TextInputLayout(this);
        pathLayout.setHint("Model filename");
        TextInputEditText path = new TextInputEditText(this);
        path.setText(m.fileName);
        path.setEnabled(false);
        pathLayout.addView(path);
        box.addView(pathLayout);
        
        TextInputLayout ctxLayout = new TextInputLayout(this);
        ctxLayout.setHint("Context length");
        TextInputEditText ctx = new TextInputEditText(this);
        ctx.setText("4096");
        ctxLayout.addView(ctx);
        box.addView(ctxLayout);
        
        TextInputLayout threadsLayout = new TextInputLayout(this);
        threadsLayout.setHint("CPU threads");
        TextInputEditText th = new TextInputEditText(this);
        th.setText("4");
        th.setInputType(2);
        threadsLayout.addView(th);
        box.addView(threadsLayout);
        
        MaterialSwitch gpu = new MaterialSwitch(this);
        gpu.setText("GPU acceleration (runtime dependent)");
        box.addView(gpu);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Configure local AI")
                .setView(box)
                .setPositiveButton("Save", null)
                .setNeutralButton("Test endpoint", (d, w) -> {
                    Intent i = new Intent(this, AiModelEditActivity.class);
                    startActivity(i);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void askDownload(LocalModel m) {
        LinearLayout box = new LinearLayout(this);
        box.setPadding(10, 0, 10, 0);
        box.setOrientation(LinearLayout.VERTICAL);
        
        TextInputLayout u = new TextInputLayout(this);
        u.setHint("Direct model URL");
        TextInputEditText e = new TextInputEditText(this);
        e.setSingleLine(true);
        u.addView(e);
        box.addView(u);
        
        new MaterialAlertDialogBuilder(this)
                .setTitle("Download " + m.name)
                .setMessage("Enter a direct model URL from a source you trust. Neo does not hardcode third-party model links.")
                .setView(box)
                .setPositiveButton("Download", (d, w) -> {
                    String url = e.getText() == null ? "" : e.getText().toString().trim();
                    startDownload(m, url);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void startDownload(LocalModel m, String url) {
        if (url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) {
            Toast.makeText(this, "Enter a valid URL", Toast.LENGTH_LONG).show();
            return;
        }
        
        Data data = new Data.Builder()
                .putString("url", url)
                .putString("name", m.fileName)
                .build();
                
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(LocalModelDownloadWorker.class)
                .setInputData(data)
                .build();
                
        WorkManager.getInstance(this).enqueue(req);
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(req.getId()).observe(this, info -> {
            if (info == null) return;
            if (info.getState() == WorkInfo.State.SUCCEEDED) {
                Toast.makeText(this, m.name + " downloaded", Toast.LENGTH_LONG).show();
                if (screen == 1) {
                    renderLocalList();
                }
            } else if (info.getState() == WorkInfo.State.FAILED) {
                Toast.makeText(this, "Download failed", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void pickGguf() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/octet-stream");
        startActivityForResult(i, PICK_GGUF);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_GGUF && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            try {
                LocalModelManager.importModel(this, data.getData(), "imported-" + System.currentTimeMillis() + ".gguf");
                Toast.makeText(this, "GGUF imported", Toast.LENGTH_SHORT).show();
                if (screen == 1) {
                    renderLocalList();
                }
            } catch (Exception e) {
                Toast.makeText(this, "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showChat() {
        screen = 2;
        chatBinding = ActivityAiChatBinding.inflate(getLayoutInflater());
        setContentView(chatBinding.getRoot());
        
        baseToolbar(chatBinding.topAppBar, "AI Chat", v -> showAgents());
        
        chatBinding.buttonClear.setOnClickListener(v -> chatBinding.chatMessages.removeAllViews());
        
        AiModelConfig active = AiManager.getActiveConfig(this);
        addMessage("AI", active == null ? "Add an AI agent first." : active.displayName + " is ready. Ask about your Sketchware Neo project.");
        
        chatBinding.buttonSend.setOnClickListener(v -> sendChat());
    }

    private void addMessage(String who, String message) {
        if (chatBinding == null) return;
        
        MaterialCardView c = card();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(14, 12, 14, 12);
        
        TextView w = text(who, com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        TextView m = text(message, com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        
        box.addView(w);
        box.addView(m);
        c.addView(box);
        
        chatBinding.chatMessages.addView(c);
        pendingChat = m;
        
        chatBinding.chatScroll.post(() -> chatBinding.chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void sendChat() {
        if (chatBinding == null) return;
        
        String q = chatBinding.editMessage.getText() != null ? chatBinding.editMessage.getText().toString().trim() : "";
        if (q.isEmpty()) return;
        
        if (AiManager.getActiveConfig(this) == null) {
            Toast.makeText(this, "No active AI agent", Toast.LENGTH_SHORT).show();
            return;
        }
        
        addMessage("You", q);
        chatBinding.editMessage.setText("");
        chatBinding.buttonSend.setEnabled(false);
        addMessage("AI", "Thinking…");
        
        AiManager.sendPrompt(this, "You are the Sketchware Neo AI assistant. Keep existing project behaviour intact and give actionable answers.", q, new AiResponseCallback() {
            @Override
            public void onSuccess(String s) {
                runOnUiThread(() -> {
                    if (pendingChat != null) {
                        pendingChat.setText(s);
                    }
                    if (chatBinding != null) {
                        chatBinding.buttonSend.setEnabled(true);
                    }
                });
            }

            @Override
            public void onFailure(String e) {
                runOnUiThread(() -> {
                    if (pendingChat != null) {
                        pendingChat.setText("Error: " + e);
                    }
                    if (chatBinding != null) {
                        chatBinding.buttonSend.setEnabled(true);
                    }
                });
            }
        });
    }

    @Override
    public void onEditClicked(AiModelConfig c) {
        Intent i = new Intent(this, AiModelEditActivity.class);
        i.putExtra(AiModelEditActivity.EXTRA_CONFIG, c);
        editorLauncher.launch(i);
    }

    @Override
    public void onDuplicateClicked(AiModelConfig c) {
        AiModelConfig x = new AiModelConfig(c.providerId, c.displayName + " (copy)", c.apiKey, c.modelName, c.customEndpoint);
        x.temperature = c.temperature;
        x.threads = c.threads;
        x.maxTokens = c.maxTokens;
        x.topP = c.topP;
        x.systemPrompt = c.systemPrompt;
        x.enableChat = c.enableChat;
        x.enableBlocks = c.enableBlocks;
        x.enableLogic = c.enableLogic;
        x.enableLayouts = c.enableLayouts;
        x.enableCustomBlocks = c.enableCustomBlocks;
        x.enableErrorFix = c.enableErrorFix;
        AiManager.addConfig(this, x);
        refreshAgents();
    }

    @Override
    public void onDeleteClicked(AiModelConfig c) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Delete AI agent")
                .setMessage("Remove \"" + c.displayName + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    AiManager.removeConfig(this, c.id);
                    refreshAgents();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onItemClicked(AiModelConfig c) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Set active agent?")
                .setMessage(c.displayName + " will be used by existing AI generation flows.")
                .setPositiveButton("Set active", (d, w) -> {
                    AiManager.setActiveConfigId(this, c.id);
                    refreshAgents();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onChatClicked(AiModelConfig c) {
        AiManager.setActiveConfigId(this, c.id);
        showChat();
    }
}
