package pro.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import a.a.a.ProjectBuilder;
import a.a.a.cC;
import a.a.a.jC;
import a.a.a.Lx;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.ide.diagnostics.Diagnostic;
import mod.hey.studios.ide.diagnostics.DiagnosticsAdapter;
import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.relativelayout.CircularDependencyDetector;

@SuppressWarnings({"deprecation", "removal"})
public class ViewCodeEditorActivity extends BaseAppCompatActivity {
    private ViewCodeEditorBinding binding;
    private CodeEditor editor;

    private SharedPreferences prefs;
    private String sc_id;
    private String filename;
    private String content;
    private boolean isEdited = false;

    private ProjectFileBean projectFile;
    private ProjectLibraryBean projectLibrary;
    private InjectRootLayoutManager rootLayoutManager;

    private MaterialCardView searchCard;
    private LinearLayout searchPanel;
    private ImageView prevBtn, nextBtn, replaceBtn, replaceAllBtn;
    private EditText findEdit, replaceEdit;

    private DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> diagnosticsBehavior;
    private TextView tvCursorPos;
    private TextView tvLanguage;
    private TabLayout editorTabs;
    private RecyclerView rvExplorer;

    private final BroadcastReceiver buildDiagnosticsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String output = intent.getStringExtra("build_output");
            if (output != null) {
                List<Diagnostic> parsedErrors = GradleLogParser.parseLogs(output);
                showDiagnostics(parsedErrors);
            }
        }
    };

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START);
                return;
            }
            if (searchCard != null && searchCard.getVisibility() == View.VISIBLE) {
                try { editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
                searchCard.setVisibility(View.GONE);
                findEdit.setText("");
                return;
            }

            if (isContentModified()) {
                MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(ViewCodeEditorActivity.this);
                dialog.setIcon(R.drawable.ic_warning_96dp);
                dialog.setTitle(Helper.getResString(R.string.common_word_warning));
                dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));

                dialog.setPositiveButton("Save", (v, which) -> {
                    if (filename.endsWith(".xml")) {
                        if (applyXmlChanges()) { v.dismiss(); finish(); }
                    } else {
                        FileUtil.writeFile(getIntent().getStringExtra("content"), editor.getText().toString());
                        v.dismiss(); finish();
                    }
                });
                dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> { v.dismiss(); finish(); });
                dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
                dialog.show();
            } else {
                if (isEdited) { setResult(RESULT_OK); finish(); return; }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        
        binding = ViewCodeEditorBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        prefs = getSharedPreferences("dce", Activity.MODE_PRIVATE);
        sc_id = savedInstanceState == null ? getIntent().getStringExtra("sc_id") : savedInstanceState.getString("sc_id");
        filename = getIntent().getStringExtra("title");
        content = getIntent().getStringExtra("content");
        
        rootLayoutManager = new InjectRootLayoutManager(sc_id);
        projectFile = jC.b(sc_id).b(filename);
        projectLibrary = jC.c(sc_id).c();
        editor = binding.editor;

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        drawerLayout = findViewById(R.id.drawer_layout);
        tvCursorPos = findViewById(R.id.tv_cursor_pos);
        tvLanguage = findViewById(R.id.tv_language);
        editorTabs = findViewById(R.id.editor_tabs);

        setupToolbar();
        setupTabs();
        setupEditor();
        applySyntaxHighlighting();
        setupSearchPanel();
        setupDiagnosticsPanel();
        setupProjectExplorer();
        
        if (projectFile != null && projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary != null && projectLibrary.isEnabled()) {
            setNote("Use AppCompat Manager to modify attributes for CoordinatorLayout, Toolbar, and other appcompat layouts/widgets.");
        }
        
        if (binding.close != null) binding.close.setOnClickListener(v -> { prefs.edit().putInt("note_" + sc_id, 1).apply(); setNote(null); });
        if (binding.noteCard != null) binding.noteCard.setOnClickListener(v -> toAppCompat());

        View appBarLayout = findViewById(R.id.app_bar_layout);
        if (appBarLayout != null) UI.addSystemWindowInsetToPadding(appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(editor, true, false, true, false);
        
        View navView = findViewById(R.id.nav_view_explorer);
        if(navView != null) UI.addSystemWindowInsetToPadding(navView, true, true, true, true);
    }

    private void applySyntaxHighlighting() {
        if (filename != null) {
            String name = filename.toLowerCase();
            if (name.endsWith(".java")) {
                editor.setEditorLanguage(new JavaLanguage());
                if(tvLanguage != null) tvLanguage.setText("Java");
            } else if (name.endsWith(".kt")) {
                editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN));
                editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
                if(tvLanguage != null) tvLanguage.setText("Kotlin");
            } else if (name.endsWith(".xml")) {
                editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
                editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(ThemeUtils.isDarkThemeEnabled(getApplicationContext()) ? CodeEditorColorSchemes.THEME_DRACULA : CodeEditorColorSchemes.THEME_GITHUB));
                if(tvLanguage != null) tvLanguage.setText("XML");
            } else {
                EditorUtils.loadXmlConfig(editor);
                if(tvLanguage != null) tvLanguage.setText("Plain Text");
            }
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(filename);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Specifically removed setHomeAsUpIndicator to restore the Back (<-) arrow
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupTabs() {
        if(editorTabs != null) {
            editorTabs.removeAllTabs();
            editorTabs.addTab(editorTabs.newTab().setText(filename != null ? filename : "layout.xml"));
        }
    }

    private class ScreenAdapter extends RecyclerView.Adapter<ScreenAdapter.VH> {
        List<ProjectFileBean> list;
        ScreenAdapter(List<ProjectFileBean> list) { this.list = list; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(android.view.Gravity.CENTER_VERTICAL);
            root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12));
            
            TypedValue outValue = new TypedValue();
            parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            root.setBackgroundResource(outValue.resourceId);

            ImageView icon = new ImageView(parent.getContext());
            root.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

            LinearLayout textContainer = new LinearLayout(parent.getContext());
            textContainer.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
            textParams.setMarginStart(SketchwareUtil.dpToPx(16));
            root.addView(textContainer, textParams);

            TextView title = new TextView(parent.getContext());
            title.setTextSize(16f);
            title.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            textContainer.addView(title);

            TextView subtitle = new TextView(parent.getContext());
            subtitle.setTextSize(12f);
            subtitle.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            textContainer.addView(subtitle);

            return new VH(root, icon, title, subtitle);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            ProjectFileBean bean = list.get(position);
            holder.title.setText(bean.getXmlName());
            if (bean.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY) {
                holder.icon.setImageResource(R.drawable.ic_mtrl_screen);
                holder.subtitle.setVisibility(View.VISIBLE);
                holder.subtitle.setText(bean.getJavaName());
            } else {
                holder.icon.setImageResource(R.drawable.ic_code_white_48dp);
                holder.subtitle.setVisibility(View.GONE);
            }
            holder.itemView.setOnClickListener(v -> {
                if (isContentModified()) {
                    if (filename.endsWith(".xml")) applyXmlChanges();
                    else FileUtil.writeFile(getIntent().getStringExtra("content"), editor.getText().toString());
                }
                projectFile = bean;
                filename = bean.getXmlName();
                String path = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/files/resource/layout/" + filename;
                content = FileUtil.readFile(path);
                editor.setText(content);
                getIntent().putExtra("content", path);
                getIntent().putExtra("title", filename);
                applySyntaxHighlighting();
                setupToolbar();
                setupTabs();
                drawerLayout.closeDrawer(GravityCompat.START);
                invalidateOptionsMenu();
            });
        }
        @Override
        public int getItemCount() { return list.size(); }

        class VH extends RecyclerView.ViewHolder {
            ImageView icon; TextView title, subtitle;
            VH(View v, ImageView ic, TextView t, TextView st) { super(v); icon = ic; title = t; subtitle = st; }
        }
    }

    private void setupProjectExplorer() {
        rvExplorer = new RecyclerView(this);
        rvExplorer.setLayoutManager(new LinearLayoutManager(this));
        
        ViewGroup navView = findViewById(R.id.nav_view_explorer);
        if (navView != null) {
            navView.removeAllViews();
            navView.addView(rvExplorer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            ArrayList<ProjectFileBean> screens = new ArrayList<>();
            if (jC.b(sc_id).b() != null) screens.addAll(jC.b(sc_id).b());
            if (jC.b(sc_id).c() != null) screens.addAll(jC.b(sc_id).c());
            
            ScreenAdapter adapter = new ScreenAdapter(screens);
            rvExplorer.setAdapter(adapter);
        }
    }

    private void setupEditor() {
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        editor.setTextSize(prefs.getInt("act_ts", 14));
        editor.setText(content);
        editor.setWordwrap(prefs.getBoolean("act_ww", false));
        editor.getComponent(EditorAutoCompletion.class).setEnabled(prefs.getBoolean("act_ac", true));
        editor.getProps().symbolPairAutoCompletion = prefs.getBoolean("act_acsp", true);
        editor.getComponent(Magnifier.class).setEnabled(true);
        editor.setHighlightCurrentLine(true);
        editor.setLineSpacing(2f, 1.1f);
        
        editor.getColorScheme().setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0x66FFEB3B);

        editor.getText().addContentListener(new ContentListener() {
            @Override public void beforeReplace(Content content) { }
            @Override public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) { 
                runOnUiThread(() -> invalidateOptionsMenu());
            }

            @Override
            public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                runOnUiThread(() -> invalidateOptionsMenu());
                if (insertedContent != null && insertedContent.toString().equals(">")) {
                    try {
                        String lineText = content.getLineString(endLine);
                        int tagStartIndex = lineText.lastIndexOf('<', endColumn - 1);
                        if (tagStartIndex != -1) {
                            String tagStr = lineText.substring(tagStartIndex + 1, endColumn - 1);
                            if (!tagStr.startsWith("/") && !tagStr.endsWith("/") && !tagStr.contains(" ") && tagStr.matches("[a-zA-Z0-9_.]+")) {
                                String closeTag = "</" + tagStr + ">";
                                editor.post(() -> {
                                    try { editor.getText().insert(endLine, endColumn, closeTag); } catch (Exception ignored) {}
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        });
    }

    private void setupDiagnosticsPanel() {
        LinearLayout bottomSheet = findViewById(R.id.bottom_sheet_diagnostics);
        if(bottomSheet != null) {
            diagnosticsBehavior = BottomSheetBehavior.from(bottomSheet);
            diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            View closeBtn = findViewById(R.id.btn_close_diagnostics);
            if (closeBtn != null) {
                closeBtn.setOnClickListener(v -> diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
            }
        }
    }

    public void showDiagnostics(List<Diagnostic> diagnostics) {
        if(diagnosticsBehavior == null) return;
        androidx.recyclerview.widget.RecyclerView rv = findViewById(R.id.rv_diagnostics);
        if (rv != null) {
            DiagnosticsAdapter adapter = new DiagnosticsAdapter(diagnostics, diagnostic -> {
                if(diagnostic.fileName.equals(filename)) {
                    editor.jumpToLine(diagnostic.line - 1); 
                }
                diagnosticsBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            });
            rv.setAdapter(adapter);
        }
        TextView title = findViewById(R.id.tv_diagnostic_title);
        if (title != null) title.setText("Issues (" + diagnostics.size() + ")");
        diagnosticsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void setupSearchPanel() {
        searchCard = new MaterialCardView(this);
        searchCard.setCardElevation(16f);
        searchCard.setRadius(24f);
        searchCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));
        searchCard.setVisibility(View.GONE);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        int margin = (int) (16 * getResources().getDisplayMetrics().density);
        cardParams.setMargins(margin, margin, margin, margin);
        searchCard.setLayoutParams(cardParams);

        searchPanel = new LinearLayout(this);
        searchPanel.setOrientation(LinearLayout.VERTICAL);
        searchPanel.setPadding(margin/2, margin/2, margin/2, margin/2);
        
        int iconColor = ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant);
        
        LinearLayout findRow = new LinearLayout(this);
        findRow.setOrientation(LinearLayout.HORIZONTAL);
        findRow.setPadding(16, 16, 16, 8);
        
        findEdit = new EditText(this);
        findEdit.setHint("Find...");
        findEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        prevBtn = new ImageView(this);
        prevBtn.setImageResource(R.drawable.ic_mtrl_arrow_up);
        prevBtn.setColorFilter(iconColor);
        prevBtn.setPadding(16, 16, 16, 16);
        prevBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { editor.getSearcher().gotoPrevious(); } catch (Exception ignored) {} });
        
        nextBtn = new ImageView(this);
        nextBtn.setImageResource(R.drawable.ic_mtrl_arrow_down);
        nextBtn.setColorFilter(iconColor);
        nextBtn.setPadding(16, 16, 16, 16);
        nextBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { editor.getSearcher().gotoNext(); } catch (Exception ignored) {} });
        
        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(R.drawable.ic_mtrl_close);
        closeBtn.setColorFilter(iconColor);
        closeBtn.setPadding(16, 16, 16, 16);
        closeBtn.setOnClickListener(v -> {
            try { editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
            searchCard.setVisibility(View.GONE);
            findEdit.setText("");
        });
        
        findRow.addView(findEdit); findRow.addView(prevBtn); findRow.addView(nextBtn); findRow.addView(closeBtn);
        
        LinearLayout replaceRow = new LinearLayout(this);
        replaceRow.setOrientation(LinearLayout.HORIZONTAL);
        replaceRow.setPadding(16, 8, 16, 16);
        
        replaceEdit = new EditText(this);
        replaceEdit.setHint("Replace...");
        replaceEdit.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        
        replaceBtn = new ImageView(this);
        replaceBtn.setImageResource(R.drawable.ic_mtrl_find_replace);
        replaceBtn.setColorFilter(iconColor);
        replaceBtn.setPadding(16, 16, 16, 16);
        replaceBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { editor.getSearcher().replaceThis(replaceEdit.getText().toString()); } catch (Exception ignored) {} });
        
        replaceAllBtn = new ImageView(this);
        replaceAllBtn.setImageResource(R.drawable.ic_done_all_white_24dp);
        replaceAllBtn.setColorFilter(iconColor);
        replaceAllBtn.setPadding(16, 16, 16, 16);
        replaceAllBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { editor.getSearcher().replaceAll(replaceEdit.getText().toString()); } catch (Exception ignored) {} });
        
        replaceRow.addView(replaceEdit); replaceRow.addView(replaceBtn); replaceRow.addView(replaceAllBtn);
        
        searchPanel.addView(findRow); searchPanel.addView(replaceRow);
        searchCard.addView(searchPanel);
        
        ViewGroup rootView = (ViewGroup) editor.getParent();
        if (rootView != null) rootView.addView(searchCard, 1);
        
        findEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try { editor.getSearcher().search(s.toString(), new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true)); } catch (Exception ignored) {}
                } else {
                    try { editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ProjectBuilder.ACTION_BUILD_DIAGNOSTICS);
        registerReceiver(buildDiagnosticsReceiver, filter, Context.RECEIVER_NOT_EXPORTED); 
    }

    @Override
    public void onStop() {
        super.onStop();
        try { unregisterReceiver(buildDiagnosticsReceiver); } catch (Exception ignored){}
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        prefs.edit().putInt("act_ts", (int) (editor.getTextSizePx() / scaledDensity)).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Core Action Icons (Always Visible)
        MenuItem undoItem = menu.add(Menu.NONE, 0, Menu.NONE, "Undo");
        undoItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo));
        undoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem redoItem = menu.add(Menu.NONE, 1, Menu.NONE, "Redo");
        redoItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo));
        redoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem saveItem = menu.add(Menu.NONE, 2, Menu.NONE, "Save");
        saveItem.setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save));
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        // Overflow Options (3-dots)
        menu.add(Menu.NONE, 12, Menu.NONE, "Project Explorer").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 4, Menu.NONE, "Find & Replace").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 5, Menu.NONE, "Pretty print").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        if (isFileInLayoutFolder() && getIntent().hasExtra("sc_id")) {
            menu.add(Menu.NONE, 6, Menu.NONE, "Layout Preview").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        
        menu.add(Menu.NONE, 7, Menu.NONE, "Select language").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 8, Menu.NONE, "Select theme").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        MenuItem wrapItem = menu.add(Menu.NONE, 9, Menu.NONE, "Word wrap");
        wrapItem.setCheckable(true).setChecked(prefs.getBoolean("act_ww", false)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        MenuItem acItem = menu.add(Menu.NONE, 10, Menu.NONE, "Auto complete");
        acItem.setCheckable(true).setChecked(prefs.getBoolean("act_ac", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        MenuItem acspItem = menu.add(Menu.NONE, 11, Menu.NONE, "Auto complete symbol pair");
        acspItem.setCheckable(true).setChecked(prefs.getBoolean("act_acsp", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem undoItem = menu.findItem(0);
        if (undoItem != null) {
            try { undoItem.setEnabled(editor.canUndo()); } catch (Exception ignored) {}
        }
        MenuItem redoItem = menu.findItem(1);
        if (redoItem != null) {
            try { redoItem.setEnabled(editor.canRedo()); } catch (Exception ignored) {}
        }
        MenuItem saveItem = menu.findItem(2);
        if (saveItem != null) saveItem.setEnabled(isContentModified());
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> { editor.undo(); return true; }
            case 1 -> { editor.redo(); return true; }
            case 2 -> {
                if (isContentModified()) {
                    if (filename.endsWith(".xml")) {
                        if (applyXmlChanges()) SketchwareUtil.toast("Saved XML");
                    } else {
                        FileUtil.writeFile(getIntent().getStringExtra("content"), editor.getText().toString());
                        isEdited = true;
                        content = editor.getText().toString();
                        SketchwareUtil.toast("Saved File");
                    }
                    invalidateOptionsMenu();
                } else { SketchwareUtil.toast("No changes to save"); }
                return true;
            }
            case 12 -> { 
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START); 
                return true; 
            }
            case 3 -> { toAppCompat(); return true; }
            case 4 -> { if(searchCard != null) searchCard.setVisibility(View.VISIBLE); return true; }
            case 5 -> {
                if (getIntent().hasExtra("java") || (filename != null && filename.endsWith(".java"))) {
                    StringBuilder b = new StringBuilder();
                    for (String line : editor.getText().toString().split("\n")) {
                        String trims = (line + "X").trim();
                        if (!trims.isEmpty()) b.append(trims.substring(0, Math.max(0, trims.length() - 1))).append("\n");
                    }
                    try { editor.setText(Lx.j(b.toString(), true)); SketchwareUtil.toast("Code Formatted!"); } 
                    catch (Exception e) { SketchwareUtil.toastError("Your code contains incorrectly nested parentheses"); }
                } else if (getIntent().hasExtra("xml") || (filename != null && filename.endsWith(".xml"))) {
                    String format = SrcCodeEditor.prettifyXml(editor.getText().toString(), 4, getIntent());
                    if (format != null) { editor.setText(format); SketchwareUtil.toast("XML Formatted!"); } 
                    else { SketchwareUtil.toastError("Failed to format XML file"); }
                } else { SketchwareUtil.toast("Only Java and XML files can be formatted"); }
                return true;
            }
            case 6 -> {
                Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra("xml", editor.getText().toString());
                startActivity(intent);
                return true; 
            }
            case 7 -> { SrcCodeEditor.showSwitchLanguageDialog(this, editor, (dialog, which) -> { SrcCodeEditor.selectLanguage(editor, which); dialog.dismiss(); }); return true; }
            case 8 -> {
                SrcCodeEditor.showSwitchThemeDialog(this, editor, (dialog, which) -> {
                    SrcCodeEditor.selectTheme(editor, which);
                    prefs.edit().putInt("act_theme", which).apply();
                    dialog.dismiss();
                });
                return true;
            }
            case 9 -> {
                item.setChecked(!item.isChecked());
                editor.setWordwrap(item.isChecked());
                prefs.edit().putBoolean("act_ww", item.isChecked()).apply();
                return true;
            }
            case 10 -> {
                item.setChecked(!item.isChecked());
                editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                prefs.edit().putBoolean("act_ac", item.isChecked()).apply();
                return true;
            }
            case 11 -> {
                item.setChecked(!item.isChecked());
                editor.getProps().symbolPairAutoCompletion = item.isChecked();
                prefs.edit().putBoolean("act_acsp", item.isChecked()).apply();
                return true;
            }
            default -> { return super.onOptionsItemSelected(item); }
        }
    }

    private void toAppCompat() {
        var intent = new Intent(getApplicationContext(), ManageAppCompatActivity.class);
        intent.putExtra("sc_id", sc_id);
        intent.putExtra("file_name", filename);
        startActivity(intent);
    }

    private void toLayoutPreview() {
        var intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
        intent.putExtras(getIntent());
        intent.putExtra("xml", editor.getText().toString());
        startActivity(intent);
    }

    private void setNote(String note) {
        if (prefs.getInt("note_" + sc_id, 0) < 1 && (note != null && !note.isEmpty())) {
            if(binding.noteCard != null) binding.noteCard.setVisibility(View.VISIBLE);
            if(binding.note != null) binding.note.setText(note);
        } else {
            if(binding.noteCard != null) binding.noteCard.setVisibility(View.GONE);
        }
    }

    private boolean applyXmlChanges() {
        try {
            ArrayList<ViewBean> oldLayout = jC.a(sc_id).d(filename);
            String xmlToParse = editor.getText().toString();

            var parser = new ViewBeanParser(xmlToParse, oldLayout);
            parser.setSkipRoot(true);
            ArrayList<ViewBean> parsedLayout = parser.parse();

            for (ViewBean bean : parsedLayout) {
                if (bean.convert != null && bean.convert.contains("ConstraintLayout")) {
                    bean.type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
                    bean.isCustomWidget = false;
                    bean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
                }
            }

            if (oldLayout != null) {
                for (ViewBean newBean : parsedLayout) {
                    for (ViewBean oldBean : oldLayout) {
                        if (newBean.id.equals(oldBean.id)) {
                            if (oldBean.type == ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT ||
                                (oldBean.convert != null && oldBean.convert.contains("ConstraintLayout"))) {
                                newBean.type = ViewBean.VIEW_TYPE_LAYOUT_CONSTRAINT;
                                newBean.convert = "androidx.constraintlayout.widget.ConstraintLayout";
                                newBean.isCustomWidget = false;
                                newBean.customView = oldBean.customView;
                            } else if (newBean.type == 0 || newBean.type == 14) {
                                newBean.type = oldBean.type;
                                newBean.clearClassInfo();
                            }
                            break;
                        }
                    }
                }
            }

            for (ViewBean child : parsedLayout) {
                if (!"root".equals(child.parent)) {
                    for (ViewBean parent : parsedLayout) {
                        if (child.parent.equals(parent.id)) {
                            child.parentType = parent.type;
                            break;
                        }
                    }
                }
                child.parentClassInfo = null;
            }

            for (ViewBean viewBean : parsedLayout) {
                CircularDependencyDetector detector = new CircularDependencyDetector(parsedLayout, viewBean);
                if (viewBean.parentAttributes != null) {
                    for (String attr : viewBean.parentAttributes.keySet()) {
                        String targetId = viewBean.parentAttributes.get(attr);
                        if (!detector.isLegalAttribute(targetId, attr)) {
                            SketchwareUtil.toastError("Circular dependency found in \"" + viewBean.name + "\"\nPlease resolve the issue before saving.");
                            return false;
                        }
                    }
                }
            }

            content = xmlToParse;
            if (!isEdited) isEdited = true;

            var root = parser.getRootAttributes();
            rootLayoutManager.set(filename, InjectRootLayoutManager.toRoot(root));

            HistoryViewBean bean = new HistoryViewBean();
            bean.actionOverride(parsedLayout, oldLayout);

            var cc = cC.c(sc_id);
            if (!cc.c.containsKey(filename)) cc.e(filename);

            cc.a(filename);
            cc.a(filename, bean);

            jC.a(sc_id).c.put(filename, parsedLayout);
            setResult(RESULT_OK);
            return true;

        } catch (Exception e) {
            SketchwareUtil.toastError("XML Syntax Error: " + e.getMessage());
            List<Diagnostic> errors = new ArrayList<>();
            errors.add(new Diagnostic(Diagnostic.Severity.ERROR, filename, 1, 0, e.getMessage()));
            showDiagnostics(errors);
            return false;
        }
    }

    private boolean isFileInLayoutFolder() {
    String contentLocal = getIntent().getStringExtra("content");
    if (contentLocal != null) {
        File file = new File(contentLocal);
        if (contentLocal.contains("/resource/layout/")) {
            String layoutFolder = file.getParent();
            return layoutFolder != null &&
                   layoutFolder.endsWith("/resource/layout");
        }
    }
    return false;
}

private boolean isContentModified() {
    return !content.equals(editor.getText().toString());
}
}
