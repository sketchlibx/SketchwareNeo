package neo.sketchware.ai;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import io.noties.markwon.Markwon;
import neo.sketchware.ai.local.LocalModelManager;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityAiChatBinding;
import pro.sketchware.databinding.ActivityAiSettingsBinding;
import pro.sketchware.databinding.ActivityLocalModelsBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class AiSettingsActivity extends BaseAppCompatActivity {

    private ActivityAiSettingsBinding settingsBinding;
    private ActivityLocalModelsBinding localModelsBinding;
    private ActivityAiChatBinding chatBinding;

    private AgentAdapter adapter;
    private ChatAdapter chatAdapter;
    private int filter = 0;
    private ActivityResultLauncher<Intent> editorLauncher;
    private ActivityResultLauncher<Intent> speechLauncher;
    private ActivityResultLauncher<Intent> filePickerLauncher;
    private ActivityResultLauncher<Intent> modelPickerLauncher;

    private int screen = 0;
    private String chatConfigId = null;
    
    private final List<ChatMessage> currentChatMessages = new ArrayList<>();
    private final Set<String> cancelledRequests = new HashSet<>();
    private String activeRequestId = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> pendingUiUpdates = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        editorLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && screen == 0) refreshAgents();
                }
        );

        speechLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty() && chatBinding != null) {
                            String currentText = chatBinding.editMessage.getText().toString();
                            chatBinding.editMessage.setText(currentText + " " + matches.get(0));
                            chatBinding.editMessage.setSelection(chatBinding.editMessage.getText().length());
                        }
                    }
                }
        );

        filePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null || chatBinding == null) return;
                    Uri uri = data.getData();
                    String displayName = getUriDisplayName(uri);
                    String mime = getContentResolver().getType(uri);
                    if (isReadableTextAttachment(displayName, mime)) {
                        new Thread(() -> {
                            String contents = readTextAttachment(uri);
                            runOnUiThread(() -> {
                                if (chatBinding == null) return;
                                String addition = "\n[Attached text file: " + displayName + "]\n" + contents + "\n[End attachment]\n";
                                appendToMessageInput(addition);
                            });
                        }, "ai-text-attachment").start();
                    } else {
                        appendToMessageInput("\n[Attached file: " + displayName + "; binary contents are not embedded]\n");
                    }
                }
        );

        modelPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Intent data = result.getData();
                    if (result.getResultCode() != RESULT_OK || data == null || data.getData() == null) return;
                    Uri uri = data.getData();
                    String displayName = getUriDisplayName(uri);
                    new Thread(() -> {
                        try {
                            LocalModelManager.importModel(getApplicationContext(), uri, displayName);
                            runOnUiThread(() -> {
                                if (localModelsBinding != null) renderLocalList();
                                new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                                    .setTitle("GGUF imported")
                                    .setMessage("The GGUF file is stored in Sketchware Neo's private storage, but importing it does not run the model by itself. Start an inference server/runtime that supports this GGUF and exposes an Ollama or OpenAI-compatible API, then connect using the exact model ID shown by that server. Use 127.0.0.1 only when the server runs on this same device; for a computer on the same network, enter its reachable LAN IP and ensure the server accepts LAN connections.")
                                    .setPositiveButton("Configure local server", (dialog, which) -> {
                                        Intent intent = new Intent(AiSettingsActivity.this, AiModelEditActivity.class);
                                        intent.putExtra(AiModelEditActivity.EXTRA_PROVIDER_ID, "local");
                                        editorLauncher.launch(intent);
                                    })
                                    .setNegativeButton("Later", null)
                                    .show();
                            });
                        } catch (Exception e) {
                            String message = e.getMessage() == null ? "Model import failed" : e.getMessage();
                            runOnUiThread(() -> {
                                if (isFinishing() || isDestroyed()) return;
                                new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                                        .setTitle("Could not import model").setMessage(message)
                                        .setPositiveButton("OK", null).show();
                            });
                        }
                    }, "ai-model-import").start();
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

    private void showAgents() {
        releaseChatUi();
        screen = 0;
        settingsBinding = ActivityAiSettingsBinding.inflate(getLayoutInflater());
        setContentView(settingsBinding.getRoot());

        settingsBinding.topAppBar.setNavigationOnClickListener(v -> finish());
        settingsBinding.buttonSettings.setOnClickListener(v -> showAiPreferences());

        settingsBinding.recyclerViewAiModels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AgentAdapter(this);
        settingsBinding.recyclerViewAiModels.setAdapter(adapter);

        settingsBinding.buttonAddAgent.setOnClickListener(v -> editorLauncher.launch(new Intent(this, AiModelEditActivity.class)));
        settingsBinding.buttonChat.setOnClickListener(v -> showChat(null));
        settingsBinding.buttonLocalModels.setOnClickListener(v -> showLocalModels());
        settingsBinding.buttonTestActive.setOnClickListener(v -> testActive());
        View workspaceButton = findViewById(R.id.buttonAgentWorkspace);
        if (workspaceButton != null) workspaceButton.setOnClickListener(v -> startActivity(new Intent(this, AgentWorkspaceActivity.class)));

        settingsBinding.tabAll.setOnClickListener(v -> { filter = 0; refreshAgents(); });
        settingsBinding.tabLocal.setOnClickListener(v -> { filter = 1; refreshAgents(); });
        settingsBinding.tabCloud.setOnClickListener(v -> { filter = 2; refreshAgents(); });

        refreshAgents();
    }

    private void refreshAgents() {
        if (screen != 0 || settingsBinding == null) return;

        List<AiModelConfig> all = AiManager.getConfigs(this);
        List<AiModelConfig> shown = new ArrayList<>();
        int localCount = 0, cloudCount = 0;

        for (AiModelConfig c : all) {
            AiProvider p = AiProviderRegistry.get(c.providerId);
            boolean isLocal = p != null && p.isLocal();
            if (isLocal) localCount++; else cloudCount++;
            if (filter == 0 || (filter == 1 && isLocal) || (filter == 2 && !isLocal)) shown.add(c);
        }

        adapter.setConfigs(shown);
        settingsBinding.emptyStateLayout.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
        settingsBinding.tabAll.setChecked(filter == 0);
        settingsBinding.tabLocal.setChecked(filter == 1);
        settingsBinding.tabCloud.setChecked(filter == 2);

        AiModelConfig active = AiManager.getActiveConfig(this);
        String prefix = active != null && !active.id.equals(AiManager.getActiveConfigId(this)) ? "Preferred local agent: " : "Active agent: ";
        String activeLabel = active == null ? "No active agent" : (prefix + active.displayName + " · " + active.modelName);
        settingsBinding.textAgentSummary.setText(activeLabel + "\n" + localCount + " local • " + cloudCount + " cloud agents");
        settingsBinding.buttonTestActive.setEnabled(active != null);
    }

    private void testActive() {
        AiModelConfig active = AiManager.getActiveConfig(this);
        if (active != null) testAgent(active);
    }

    private void testAgent(AiModelConfig config) {
        final androidx.appcompat.app.AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Testing Connection")
                .setMessage("Connecting to " + config.displayName + "…")
                .setCancelable(false)
                .show();

        AiManager.testConfig(this, config, new AiResponseCallback() {
            @Override
            public void onSuccess(String s) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    if (isFinishing() || isDestroyed()) return;
                    new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                            .setTitle("Connection successful").setMessage(s)
                            .setPositiveButton("OK", null).show();
                });
            }

            @Override
            public void onFailure(String e) {
                runOnUiThread(() -> {
                    if (progressDialog.isShowing()) progressDialog.dismiss();
                    if (isFinishing() || isDestroyed()) return;
                    new MaterialAlertDialogBuilder(AiSettingsActivity.this)
                            .setTitle("Connection failed").setMessage(e)
                            .setPositiveButton("OK", null).show();
                });
            }
        });
    }

    private void showAiPreferences() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 16, 36, 16);

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

        if (localModelsBinding.topAppBar != null) {
            localModelsBinding.topAppBar.setNavigationOnClickListener(v -> showAgents());
        }

        localModelsBinding.buttonImportGguf.setOnClickListener(v -> pickGguf());
        localModelsBinding.buttonConnectServer.setOnClickListener(v -> {
            Intent intent = new Intent(this, AiModelEditActivity.class);
            intent.putExtra(AiModelEditActivity.EXTRA_PROVIDER_ID, "local");
            editorLauncher.launch(intent);
        });

        renderLocalList();
    }

    private void renderLocalList() {
        if (localModelsBinding == null) return;

        LinearLayout list = localModelsBinding.localModelList;
        list.removeAllViews();

        List<File> files = LocalModelManager.installed(this);
        localModelsBinding.textInstalled.setText(files.isEmpty() ? "Installed models · none" : "Installed models · " + files.size());

        for (File f : files) {
            View card = getLayoutInflater().inflate(R.layout.item_ai_model, list, false);
            TextView name = card.findViewById(R.id.textModelName);
            TextView desc = card.findViewById(R.id.textModelProvider);
            TextView status = card.findViewById(R.id.textStatus);
            card.findViewById(R.id.textActiveBadge).setVisibility(View.GONE);
            card.findViewById(R.id.iconStatus).setVisibility(View.GONE);
            AiProviderVisuals.bind(card.findViewById(R.id.imageProviderIcon), "local");

            name.setText(f.getName());
            boolean validGguf = LocalModelManager.isValidGguf(f);
            desc.setText(validGguf
                    ? "GGUF header verified · private device storage"
                    : "Invalid or incomplete GGUF · delete and re-import this file");
            status.setText((f.length() / 1024 / 1024) + " MB · " + (validGguf ? "runtime required" : "not usable"));

            card.findViewById(R.id.buttonMore).setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(this, v);
                popup.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Delete model?")
                            .setMessage(f.getName())
                            .setPositiveButton("Delete", (d, w) -> {
                                if (LocalModelManager.delete(AiSettingsActivity.this, f.getName())) {
                                    renderLocalList();
                                } else {
                                    Toast.makeText(AiSettingsActivity.this, "Could not delete this model file.", Toast.LENGTH_LONG).show();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                });
                popup.show();
            });
            list.addView(card);
        }
    }

    private void pickGguf() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        String[] types = {"application/octet-stream", "application/x-gguf", "application/x-binary"};
        i.putExtra(Intent.EXTRA_MIME_TYPES, types);
        modelPickerLauncher.launch(i);
    }

    private void showChat(String explicitConfigId) {
        releaseChatUi();
        screen = 2;
        chatBinding = ActivityAiChatBinding.inflate(getLayoutInflater());
        setContentView(chatBinding.getRoot());

        if (chatBinding.topAppBar != null) {
            chatBinding.topAppBar.setNavigationOnClickListener(v -> showAgents());
        }

        chatBinding.buttonClear.setOnClickListener(v -> {
            if (activeRequestId != null) markRequestCancelled(activeRequestId);
            activeRequestId = null;
            currentChatMessages.clear();
            saveChatHistory();
            chatAdapter.notifyDataSetChanged();
            updateInputState();
        });
        chatBinding.buttonHistory.setOnClickListener(v -> showChatHistoryPicker());

        if (explicitConfigId != null) {
            chatConfigId = explicitConfigId;
        } else {
            // General Chat targets the preferred active agent; a previous per-card choice must not leak.
            AiModelConfig preferred = AiManager.getActiveConfig(this);
            chatConfigId = preferred == null ? AiManager.getActiveConfigId(this) : preferred.id;
        }

        AiModelConfig config = null;
        if (chatConfigId != null) {
            for (AiModelConfig c : AiManager.getConfigs(this)) {
                if (c.id.equals(chatConfigId)) { config = c; break; }
            }
        }

        if (config != null) {
            AiProvider p = AiProviderRegistry.get(config.providerId);
            chatBinding.textChatAgentName.setText(config.displayName + " (" + (p != null && p.isLocal() ? "LOCAL" : "CLOUD") + ")");
            AiProviderVisuals.bind(chatBinding.imgChatAgentIcon, config.providerId);
        } else {
            chatBinding.textChatAgentName.setText("No Agent Selected");
        }

        chatBinding.recyclerChat.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter();
        chatBinding.recyclerChat.setAdapter(chatAdapter);

        loadChatHistory();

        chatBinding.buttonSend.setOnClickListener(v -> handleSendOrStop());
        chatBinding.buttonSend.setVisibility(View.GONE);

        chatBinding.editMessage.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateInputState(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        chatBinding.buttonMic.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            try { speechLauncher.launch(intent); } catch (Exception ignored) {}
        });

        chatBinding.buttonAttach.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            filePickerLauncher.launch(intent);
        });

        chatBinding.chipGenBlocks.setOnClickListener(v -> startActivity(new Intent(this, BlockGenerationActivity.class)));
        chatBinding.chipFixErrors.setOnClickListener(v -> chatBinding.editMessage.setText("I am facing a build error in Sketchware Neo. Here is the log: \n"));
        chatBinding.chipExplain.setOnClickListener(v -> chatBinding.editMessage.setText("Explain this code:\n"));
    }

    private void appendToMessageInput(String addition) {
        if (chatBinding == null || addition == null) return;
        String current = chatBinding.editMessage.getText().toString();
        chatBinding.editMessage.setText(current + addition);
        chatBinding.editMessage.setSelection(chatBinding.editMessage.getText().length());
    }

    private String cancelledMessageContent(String requestId, String reason) {
        String existing = "";
        for (int i = currentChatMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = currentChatMessages.get(i);
            if (message != null && requestId.equals(message.id)) {
                existing = message.content == null || "Thinking…".equals(message.content) ? "" : message.content.trim();
                break;
            }
        }
        return (existing.isEmpty() ? "" : existing + "\n\n") + "*[" + reason + "]*";
    }

    private void markRequestCancelled(String requestId) {
        if (requestId == null || requestId.isEmpty()) return;
        cancelledRequests.add(requestId);
        // Bound stale markers if a provider never returns its terminal callback.
        while (cancelledRequests.size() > 128) {
            java.util.Iterator<String> iterator = cancelledRequests.iterator();
            if (!iterator.hasNext()) break;
            iterator.next();
            iterator.remove();
        }
    }

    private void releaseChatUi() {
        if (activeRequestId != null) {
            String requestId = activeRequestId;
            markRequestCancelled(requestId);
            // Save the in-flight user message and a clear terminal status before detaching views.
            updateAiMessage(requestId, cancelledMessageContent(requestId, "Generation stopped when leaving chat."), false, true);
            activeRequestId = null;
        }
        for (Runnable pending : pendingUiUpdates.values()) {
            mainHandler.removeCallbacks(pending);
        }
        pendingUiUpdates.clear();
        if (chatBinding != null) {
            try { chatBinding.recyclerChat.setAdapter(null); } catch (Exception ignored) { }
        }
        chatBinding = null;
        chatAdapter = null;
    }

    @Override
    public void onDestroy() {
        releaseChatUi();
        mainHandler.removeCallbacksAndMessages(null);
        settingsBinding = null;
        localModelsBinding = null;
        super.onDestroy();
    }

    private String getUriDisplayName(Uri uri) {
        if (uri == null) return "attachment";
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String value = cursor.getString(index);
                    if (value != null && !value.trim().isEmpty()) return value.replaceAll("[\\r\\n]", "_");
                }
            }
        } catch (Exception ignored) { }
        String last = uri.getLastPathSegment();
        return last == null ? "attachment" : last;
    }

    private boolean isReadableTextAttachment(String name, String mime) {
        if (mime != null && (mime.startsWith("text/") || mime.equals("application/json") || mime.equals("application/xml"))) return true;
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".java") || lower.endsWith(".kt") ||
                lower.endsWith(".xml") || lower.endsWith(".json") || lower.endsWith(".gradle") || lower.endsWith(".properties") || lower.endsWith(".log");
    }

    private String readTextAttachment(Uri uri) {
        final int maxBytes = 96 * 1024;
        try (java.io.InputStream in = getContentResolver().openInputStream(uri);
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            if (in == null) return "[Attachment could not be opened.]";
            byte[] buffer = new byte[8192];
            int remaining = maxBytes;
            int n;
            while (remaining > 0 && (n = in.read(buffer, 0, Math.min(buffer.length, remaining))) != -1) {
                out.write(buffer, 0, n);
                remaining -= n;
            }
            String text = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            if (remaining == 0) text += "\n[Attachment truncated at 32 KB to keep AI requests responsive.]";
            return text;
        } catch (Exception e) {
            return "[Could not read attachment: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()) + "]";
        }
    }

    private String buildConversationPrompt(String currentPrompt) {
        StringBuilder prompt = new StringBuilder();
        int placeholderIndex = currentChatMessages.size() - 1;
        int currentUserIndex = currentChatMessages.size() - 2;
        int end = Math.min(currentUserIndex - 1, placeholderIndex - 2);
        int start = Math.max(0, end - 5); // at most the three most recent user/assistant turns
        boolean hasHistory = false;
        int historyChars = 0;
        for (int i = start; i <= end; i++) {
            ChatMessage message = currentChatMessages.get(i);
            if (message == null || message.isError || message.isStreaming || message.content == null || message.content.trim().isEmpty()) continue;
            String content = message.content.trim();
            if (content.length() > 1800) content = content.substring(0, 1100) + "\n[earlier content omitted]\n" + content.substring(content.length() - 550);
            if (historyChars + content.length() > 6500) continue;
            prompt.append("You".equals(message.role) ? "User: " : "Assistant: ")
                    .append(content).append("\n\n");
            historyChars += content.length();
            hasHistory = true;
        }
        if (hasHistory) prompt.insert(0, "Recent conversation excerpts (oldest first; omit if irrelevant):\n");
        String current = currentPrompt == null ? "" : currentPrompt.trim();
        if (current.length() > 30000) current = current.substring(0, 20000) + "\n[request middle shortened]\n" + current.substring(current.length() - 9000);
        prompt.append("Current user message:\n").append(current);
        return AiPromptOptimizer.optimizeUser(prompt.toString());
    }

    private void updateInputState() {
        if (chatBinding == null) return;
        boolean hasText = chatBinding.editMessage.getText().toString().trim().length() > 0;
        boolean isGenerating = activeRequestId != null;

        if (isGenerating) {
            chatBinding.buttonSend.setImageResource(R.drawable.ic_mtrl_close); // Act as Stop
            chatBinding.buttonSend.setVisibility(View.VISIBLE);
            chatBinding.buttonMic.setVisibility(View.GONE);
        } else {
            chatBinding.buttonSend.setImageResource(R.drawable.ic_mtrl_arrow_up);
            chatBinding.buttonSend.setVisibility(hasText ? View.VISIBLE : View.GONE);
            chatBinding.buttonMic.setVisibility(hasText ? View.GONE : View.VISIBLE);
        }
    }

    private void handleSendOrStop() {
        if (activeRequestId != null) {
            String requestId = activeRequestId;
            markRequestCancelled(requestId);
            updateAiMessage(requestId, cancelledMessageContent(requestId, "Generation stopped."), false, true);
            activeRequestId = null;
            updateInputState();
            return;
        }

        if (chatBinding == null || chatConfigId == null) {
            Toast.makeText(this, "Select an AI Agent first", Toast.LENGTH_SHORT).show();
            return;
        }

        String q = chatBinding.editMessage.getText().toString().trim();
        if (q.isEmpty()) return;

        chatBinding.editMessage.setText("");
        invokeAi(q);
    }

    private void invokeAi(String userPrompt) {
        String reqId = UUID.randomUUID().toString();
        activeRequestId = reqId;
        updateInputState();

        currentChatMessages.add(new ChatMessage(UUID.randomUUID().toString(), "You", userPrompt, false, false));
        chatAdapter.notifyItemInserted(currentChatMessages.size() - 1);

        currentChatMessages.add(new ChatMessage(reqId, "AI", "Thinking…", true, false));
        chatAdapter.notifyItemInserted(currentChatMessages.size() - 1);
        scrollToBottom(true);

        AiModelConfig selectedConfig = null;
        for (AiModelConfig configured : AiManager.getConfigs(this)) {
            if (configured != null && chatConfigId != null && chatConfigId.equals(configured.id)) {
                selectedConfig = configured;
                break;
            }
        }
        final AiModelConfig requestConfig = selectedConfig;
        String promptWithContext = buildConversationPrompt(userPrompt);
        AtomicBoolean terminalDelivered = new AtomicBoolean(false);
        AiManager.sendPrompt(this, requestConfig, "You are Sketchware Neo's coding assistant. Answer in concise Markdown. Prefer correct Android Java/XML and Sketchware Neo conventions. For code requests, give usable code and state essential assumptions only.", promptWithContext, AiManager.Feature.CHAT, new AiResponseCallback() {
            @Override public void onStart(String id) { /* Provider may add progress/streaming in the future. */ }

            @Override
            public void onChunk(String id, String chunk) {
                if (terminalDelivered.get()) return;
                mainHandler.post(() -> {
                    if (!terminalDelivered.get() && !cancelledRequests.contains(reqId) && screen == 2) {
                        appendAiMessageChunk(reqId, chunk);
                    }
                });
            }

            @Override
            public void onComplete(String id, String fullText) {
                mainHandler.post(() -> {
                    if (!terminalDelivered.compareAndSet(false, true)) return;
                    if (cancelledRequests.remove(reqId)) return;
                    updateAiMessage(reqId, fullText, false, false);
                    finalizeRequest(reqId);
                });
            }

            @Override
            public void onError(String id, String err) {
                mainHandler.post(() -> {
                    if (!terminalDelivered.compareAndSet(false, true)) return;
                    if (cancelledRequests.remove(reqId)) return;
                    updateAiMessage(reqId, "Error: " + (err == null ? "Unknown AI error" : err), false, true);
                    finalizeRequest(reqId);
                });
            }

            @Override public void onSuccess(String s) { onComplete(reqId, s); }
            @Override public void onFailure(String e) { onError(reqId, e); }
        });
    }

    private void appendAiMessageChunk(String messageId, String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        for (int i = currentChatMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = currentChatMessages.get(i);
            if (!message.id.equals(messageId)) continue;
            if (message.content == null || "Thinking…".equals(message.content)) message.content = "";
            message.content += chunk;
            message.isStreaming = true;
            final int index = i;
            mainHandler.post(() -> {
                if (chatAdapter != null && index < currentChatMessages.size() && !isDestroyed()) {
                    chatAdapter.notifyItemChanged(index);
                    scrollToBottom(false);
                }
            });
            return;
        }
    }

    private void updateAiMessage(String messageId, String newContent, boolean isStreaming, boolean isError) {
        int idx = -1;
        for (int i = currentChatMessages.size() - 1; i >= 0; i--) {
            ChatMessage message = currentChatMessages.get(i);
            if (message != null && messageId.equals(message.id)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) return;

        ChatMessage msg = currentChatMessages.get(idx);
        msg.content = newContent == null ? "" : newContent;
        msg.isStreaming = isStreaming;
        msg.isError = isError;
        final int finalIdx = idx;

        Runnable pending = pendingUiUpdates.remove(messageId);
        if (pending != null) mainHandler.removeCallbacks(pending);
        Runnable updateTask = () -> {
            if (chatBinding != null && chatAdapter != null && screen == 2 &&
                    !isFinishing() && !isDestroyed() && finalIdx < currentChatMessages.size()) {
                chatAdapter.notifyItemChanged(finalIdx);
                scrollToBottom(false);
            }
            if (!isStreaming) saveChatHistory();
        };

        if (!isStreaming) {
            if (Looper.myLooper() == Looper.getMainLooper()) updateTask.run();
            else mainHandler.post(updateTask);
        } else if (!pendingUiUpdates.containsKey(messageId)) {
            Runnable delayedUpdate = () -> {
                pendingUiUpdates.remove(messageId);
                if (!isDestroyed()) updateTask.run();
            };
            pendingUiUpdates.put(messageId, delayedUpdate);
            mainHandler.postDelayed(delayedUpdate, 100L);
        }
    }

    private void finalizeRequest(String reqId) {
        if (activeRequestId != null && activeRequestId.equals(reqId)) {
            activeRequestId = null;
            runOnUiThread(this::updateInputState);
        }
    }

    private void scrollToBottom(boolean force) {
        if (chatBinding == null || chatAdapter == null || screen != 2 || chatAdapter.getItemCount() == 0) return;
        LinearLayoutManager lm = (LinearLayoutManager) chatBinding.recyclerChat.getLayoutManager();
        if (lm != null) {
            int lastVisible = lm.findLastVisibleItemPosition();
            if (force || lastVisible >= chatAdapter.getItemCount() - 3) {
                chatBinding.recyclerChat.smoothScrollToPosition(chatAdapter.getItemCount() - 1);
            }
        }
    }

    /** Shows saved conversations grouped by configured agent. History stays on-device. */
    private void showChatHistoryPicker() {
        if (!getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("remember_chat", true)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Chat history is off")
                    .setMessage("Enable ‘Remember chat history on this device’ in AI Settings to save and reopen conversations.")
                    .setPositiveButton("Open AI Settings", (dialog, which) -> showAiPreferences())
                    .setNegativeButton("Close", null)
                    .show();
            return;
        }

        SharedPreferences history = getSharedPreferences("ai_chat_history", MODE_PRIVATE);
        List<AiModelConfig> savedConfigs = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        Gson gson = new Gson();
        java.lang.reflect.Type listType = new TypeToken<ArrayList<ChatMessage>>() {}.getType();
        for (AiModelConfig config : AiManager.getConfigs(this)) {
            String json = history.getString("chat_" + config.id, null);
            if (json == null || json.trim().isEmpty()) continue;
            try {
                List<ChatMessage> messages = gson.fromJson(json, listType);
                if (messages == null || messages.isEmpty()) continue;
                savedConfigs.add(config);
                labels.add(config.displayName + " · " + messages.size() + (messages.size() == 1 ? " message" : " messages"));
            } catch (RuntimeException ignored) {
                history.edit().remove("chat_" + config.id).apply();
            }
        }

        if (savedConfigs.isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("No saved chats")
                    .setMessage("Send a message with an AI agent first. Saved conversations appear here while chat history is enabled.")
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("Recent conversations")
                .setItems(labels.toArray(new String[0]), (dialog, which) -> {
                    if (which >= 0 && which < savedConfigs.size()) showChat(savedConfigs.get(which).id);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveChatHistory() {
        if (!getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("remember_chat", true)) return;
        SharedPreferences prefs = getSharedPreferences("ai_chat_history", MODE_PRIVATE);
        prefs.edit().putString("chat_" + chatConfigId, new Gson().toJson(currentChatMessages)).apply();
    }

    private void loadChatHistory() {
        currentChatMessages.clear();
        if (getSharedPreferences("ai_prefs", MODE_PRIVATE).getBoolean("remember_chat", true)) {
            SharedPreferences prefs = getSharedPreferences("ai_chat_history", MODE_PRIVATE);
            String json = prefs.getString("chat_" + chatConfigId, null);
            if (json != null) {
                try {
                    List<ChatMessage> list = new Gson().fromJson(json, new TypeToken<ArrayList<ChatMessage>>(){}.getType());
                    if (list != null) {
                        for (ChatMessage message : list) {
                            if (message == null) continue;
                            if (message.id == null) message.id = UUID.randomUUID().toString();
                            if (message.role == null) message.role = "AI";
                            if (message.content == null) message.content = "";
                            currentChatMessages.add(message);
                        }
                    }
                } catch (RuntimeException e) {
                    currentChatMessages.clear();
                    getSharedPreferences("ai_chat_history", MODE_PRIVATE).edit().remove("chat_" + chatConfigId).apply();
                }
            }
        }
        if (currentChatMessages.isEmpty()) {
            AiModelConfig config = null;
            for (AiModelConfig c : AiManager.getConfigs(this)) if (c.id.equals(chatConfigId)) config = c;
            currentChatMessages.add(new ChatMessage(UUID.randomUUID().toString(), "AI", config == null ? "Please add an AI Agent." : "Hello! I am " + config.displayName + ".\nAsk me about your project.", false, false));
        }
        chatAdapter.notifyDataSetChanged();
        scrollToBottom(true);
    }

    private static class ChatMessage {
        String id;
        String role;
        String content;
        boolean isStreaming;
        boolean isError;

        ChatMessage(String id, String role, String content, boolean isStreaming, boolean isError) {
            this.id = id;
            this.role = role;
            this.content = content;
            this.isStreaming = isStreaming;
            this.isError = isError;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatVH> {
        private final Markwon markwon = Markwon.create(AiSettingsActivity.this);

        @NonNull @Override
        public ChatVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_chat_message, parent, false);
            return new ChatVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatVH holder, int position) {
            ChatMessage msg = currentChatMessages.get(position);
            holder.textSender.setText(msg.role == null ? "AI" : msg.role);

            boolean isUser = "You".equals(msg.role);
            holder.messageRoot.setGravity(isUser ? Gravity.END : Gravity.START);
            holder.textSender.setGravity(isUser ? Gravity.END : Gravity.START);
            holder.cardBubble.setCardBackgroundColor(ThemeUtils.getColor(AiSettingsActivity.this, isUser ? R.attr.colorSurfaceContainerHigh : R.attr.colorSurfaceContainerLow));
            holder.textSender.setTextColor(ThemeUtils.getColor(AiSettingsActivity.this, isUser ? R.attr.colorPrimary : R.attr.colorOnSurfaceVariant));

            holder.layoutContent.removeAllViews();

            if (msg.isStreaming && "Thinking…".equals(msg.content)) {
                TextView tv = new TextView(AiSettingsActivity.this);
                tv.setText(msg.content);
                tv.setTextColor(ThemeUtils.getColor(AiSettingsActivity.this, R.attr.colorOnSurfaceVariant));
                holder.layoutContent.addView(tv);
                return;
            }

            // Safe Markdown Code Splitter for Fenced Blocks
            String[] parts = msg.content.split("```", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i % 2 == 0) {
                    if (!parts[i].trim().isEmpty()) {
                        TextView tv = new TextView(AiSettingsActivity.this);
                        tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
                        tv.setTextColor(ThemeUtils.getColor(AiSettingsActivity.this, msg.isError ? R.attr.colorError : R.attr.colorOnSurface));
                        markwon.setMarkdown(tv, parts[i].trim());
                        holder.layoutContent.addView(tv);
                    }
                } else {
                    String block = parts[i];
                    int newlineIdx = block.indexOf('\n');
                    String lang = "Code";
                    String code = block;
                    if (newlineIdx != -1 && newlineIdx < 20) {
                        lang = block.substring(0, newlineIdx).trim();
                        if (lang.isEmpty()) lang = "Code";
                        code = block.substring(newlineIdx + 1);
                    }
                    View codeView = getLayoutInflater().inflate(R.layout.item_code_block, holder.layoutContent, false);
                    TextView tvLang = codeView.findViewById(R.id.textLanguage);
                    TextView tvCode = codeView.findViewById(R.id.textCode);
                    tvLang.setText(lang);
                    tvCode.setText(code);
                    String finalCode = code;
                    codeView.findViewById(R.id.buttonCopyCode).setOnClickListener(v -> {
                        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(ClipData.newPlainText("Code", finalCode));
                        Toast.makeText(AiSettingsActivity.this, "Code copied", Toast.LENGTH_SHORT).show();
                    });
                    holder.layoutContent.addView(codeView);
                }
            }

            holder.cardBubble.setOnLongClickListener(v -> {
                PopupMenu popup = new PopupMenu(AiSettingsActivity.this, v);
                popup.getMenu().add("Copy").setOnMenuItemClickListener(item -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("Message", msg.content));
                    Toast.makeText(AiSettingsActivity.this, "Copied", Toast.LENGTH_SHORT).show();
                    return true;
                });
                if (!isUser && !msg.isStreaming) {
                    popup.getMenu().add("Regenerate").setOnMenuItemClickListener(item -> {
                        for (int i = position - 1; i >= 0; i--) {
                            if ("You".equals(currentChatMessages.get(i).role)) {
                                String userText = currentChatMessages.get(i).content;
                                invokeAi(userText);
                                break;
                            }
                        }
                        return true;
                    });
                }
                popup.show();
                return true;
            });
        }

        @Override public int getItemCount() { return currentChatMessages.size(); }

        class ChatVH extends RecyclerView.ViewHolder {
            LinearLayout messageRoot;
            TextView textSender;
            MaterialCardView cardBubble;
            LinearLayout layoutContent;
            ChatVH(View v) {
                super(v);
                messageRoot = v.findViewById(R.id.layoutMessageRoot);
                textSender = v.findViewById(R.id.textSenderName);
                cardBubble = v.findViewById(R.id.cardMessageBubble);
                layoutContent = v.findViewById(R.id.layoutContent);
            }
        }
    }

    private class AgentAdapter extends RecyclerView.Adapter<AgentAdapter.AgentVH> {
        private final List<AiModelConfig> configs = new ArrayList<>();
        private String activeId;
        private final AiSettingsActivity activity;

        public AgentAdapter(AiSettingsActivity activity) {
            this.activity = activity;
        }

        public void setConfigs(List<AiModelConfig> newConfigs) {
            this.configs.clear();
            this.configs.addAll(newConfigs);
            this.activeId = AiManager.getActiveConfigId(AiSettingsActivity.this);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public AgentVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_ai_model, parent, false);
            return new AgentVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AgentVH holder, int position) {
            AiModelConfig c = configs.get(position);
            AiProvider p = AiProviderRegistry.get(c.providerId);

            holder.textName.setText(c.displayName == null || c.displayName.trim().isEmpty() ? "Unnamed AI agent" : c.displayName);
            holder.textProvider.setText((p != null ? p.getProviderName() : "Unknown provider") + " • " + (c.modelName == null ? "Model not set" : c.modelName));
            AiProviderVisuals.bind(holder.imageProviderIcon, c.providerId);

            boolean isActive = c.id.equals(activeId);
            holder.textBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
            holder.cardRoot.setStrokeColor(ColorStateList.valueOf(ThemeUtils.getColor(AiSettingsActivity.this, isActive ? R.attr.colorPrimary : R.attr.colorOutlineVariant)));
            holder.cardRoot.setStrokeWidth(isActive ? SketchwareUtil.dpToPx(2) : SketchwareUtil.dpToPx(1));

            holder.textStatus.setText(AiProviderVisuals.statusLabel(c));
            holder.cardRoot.setOnClickListener(v -> activity.showChat(c.id));

            holder.buttonMore.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(AiSettingsActivity.this, v);
                popup.getMenu().add("Chat").setOnMenuItemClickListener(item -> { activity.showChat(c.id); return true; });
                if (!isActive) popup.getMenu().add("Set Active").setOnMenuItemClickListener(item -> { AiManager.setActiveConfigId(AiSettingsActivity.this, c.id); refreshAgents(); return true; });
                popup.getMenu().add("Test").setOnMenuItemClickListener(item -> { testAgent(c); return true; });
                popup.getMenu().add("Edit").setOnMenuItemClickListener(item -> {
                    Intent i = new Intent(activity, AiModelEditActivity.class);
                    i.putExtra(AiModelEditActivity.EXTRA_CONFIG, c);
                    activity.editorLauncher.launch(i);
                    return true;
                });
                popup.getMenu().add("Duplicate").setOnMenuItemClickListener(item -> {
                    AiModelConfig x = new AiModelConfig(c.providerId, c.displayName + " (copy)", c.apiKey, c.modelName, c.customEndpoint);
                    x.temperature = c.temperature; x.threads = c.threads; x.maxTokens = c.maxTokens; x.topP = c.topP;
                    x.systemPrompt = c.systemPrompt; x.enableChat = c.enableChat; x.enableBlocks = c.enableBlocks; x.enableLogic = c.enableLogic;
                    x.enableLayouts = c.enableLayouts; x.enableCustomBlocks = c.enableCustomBlocks; x.enableErrorFix = c.enableErrorFix;
                    AiManager.addConfig(activity, x);
                    refreshAgents();
                    return true;
                });
                popup.getMenu().add("Delete").setOnMenuItemClickListener(item -> {
                    new MaterialAlertDialogBuilder(activity)
                            .setTitle("Delete AI agent")
                            .setMessage("Remove \"" + c.displayName + "\"?")
                            .setPositiveButton("Delete", (d, w) -> { AiManager.removeConfig(activity, c.id); refreshAgents(); })
                            .setNegativeButton("Cancel", null).show();
                    return true;
                });
                popup.show();
            });
        }

        @Override public int getItemCount() { return configs.size(); }

        class AgentVH extends RecyclerView.ViewHolder {
            MaterialCardView cardRoot;
            TextView textName, textProvider, textBadge, textStatus;
            android.widget.ImageView imageProviderIcon;
            ImageButton buttonMore;
            AgentVH(View v) {
                super(v);
                cardRoot = v.findViewById(R.id.cardModel);
                textName = v.findViewById(R.id.textModelName);
                textProvider = v.findViewById(R.id.textModelProvider);
                textBadge = v.findViewById(R.id.textActiveBadge);
                textStatus = v.findViewById(R.id.textStatus);
                imageProviderIcon = v.findViewById(R.id.imageProviderIcon);
                buttonMore = v.findViewById(R.id.buttonMore);
            }
        }
    }
}
