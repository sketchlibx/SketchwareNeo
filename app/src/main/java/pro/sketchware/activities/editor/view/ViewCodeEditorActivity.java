package pro.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import io.github.rosemoe.sora.event.SelectionChangeEvent;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import mod.hey.studios.project.ProjectSettings;
import mod.hey.studios.code.SrcCodeEditor;
import mod.hey.studios.code.settings.CodeHighlightingSettingsActivity;
import mod.hey.studios.ide.diagnostics.Diagnostic;
import mod.hey.studios.ide.diagnostics.DiagnosticsAdapter;
import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages.LanguageSpec;
import mod.jbk.code.CodeEditorLanguages;

import neo.sketchware.ai.AiResponseCallback;
import neo.sketchware.ai.LayoutGenTask;

import pro.sketchware.R;
import pro.sketchware.activities.appcompat.ManageAppCompatActivity;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.ViewCodeEditorBinding;
import pro.sketchware.databinding.ViewCodeEditorExplorerItemBinding;
import pro.sketchware.databinding.ViewCodeEditorOptionsBinding;
import pro.sketchware.managers.inject.InjectRootLayoutManager;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;
import pro.sketchware.utility.relativelayout.CircularDependencyDetector;

import a.a.a.Lx;
import a.a.a.ProjectBuilder;
import a.a.a.cC;
import a.a.a.jC;

@SuppressWarnings({"deprecation", "removal"})

public class ViewCodeEditorActivity extends BaseAppCompatActivity {
    private ViewCodeEditorBinding binding;
    private CodeEditor editor;

    private SharedPreferences prefs;
    private String sc_id;
    private String filename;
    private int selectedLanguageId = -1;
    private String content;
    private String beforeContent;
    private boolean isEdited = false;

    private ContentListener editorContentListener;

    private ProjectFileBean projectFile;
    private ProjectLibraryBean projectLibrary;
    private InjectRootLayoutManager rootLayoutManager;

    private BottomSheetBehavior<LinearLayout> diagnosticsBehavior;
    private ExplorerTreeAdapter explorerAdapter;
    private boolean suppressTabChanges;
    private boolean isLoadingTab;
    private boolean tabListenerAdded;
    private final List<EditorTab> openTabs = new ArrayList<>();
    private EditorTab activeTab;
    private String projectName = "MyProject";
    private io.github.rosemoe.sora.event.SubscriptionReceipt<SelectionChangeEvent> selectionSubscription;

    private final Handler diagHandler = new Handler(Looper.getMainLooper());
    private Runnable diagRunnable;
    private final List<Diagnostic> liveDiagnostics = new ArrayList<>();
    private final List<Diagnostic> buildDiagnostics = new ArrayList<>();
    private DiagnosticsAdapter diagnosticsAdapter;
    private Diagnostic pendingDiagnosticJump;

    private final BroadcastReceiver buildDiagnosticsReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String output = intent.getStringExtra("build_output");
            if (output != null) {
                List<Diagnostic> parsedErrors = GradleLogParser.parseLogs(output);
                buildDiagnostics.clear();
                if (parsedErrors != null) buildDiagnostics.addAll(parsedErrors);
                refreshDiagnostics(!buildDiagnostics.isEmpty());
            }
        }
    };

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (binding.drawerLayout != null && binding.drawerLayout.isDrawerOpen(GravityCompat.END)) {
                binding.drawerLayout.closeDrawer(GravityCompat.END);
                return;
            }
            if (binding.searchCard != null && binding.searchCard.getVisibility() == View.VISIBLE) {
                hideSearchPanel();
                return;
            }
            if (hasUnsavedTabs()) {
                new MaterialAlertDialogBuilder(ViewCodeEditorActivity.this)
                        .setIcon(R.drawable.ic_mtrl_warning)
                        .setTitle(Helper.getResString(R.string.common_word_warning))
                        .setMessage("Some files have unsaved changes. Save them before leaving?")
                        .setPositiveButton("Save all", (dialog, which) -> {
                            if (saveAllTabs()) finishWithResultIfNeeded();
                        })
                        .setNegativeButton("Discard", (dialog, which) -> finish())
                        .setNeutralButton(Helper.getResString(R.string.common_word_cancel), null)
                        .show();
            } else {
                finishWithResultIfNeeded();
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
        if (content == null) content = "";
        beforeContent = content;

        if (sc_id == null || sc_id.trim().isEmpty()) {
            SketchwareUtil.toastError("Project ID is missing. Reopen the editor from Design Activity.");
            finish();
            return;
        }
        rootLayoutManager = new InjectRootLayoutManager(sc_id);
        projectFile = filename == null ? null : findProjectFileBean(filename);
        projectLibrary = jC.c(sc_id).c();
        editor = binding.editor;
        loadProjectHeaderData();
        activeTab = new EditorTab(filename == null ? "layout.xml" : filename, null, projectFile,
                projectFile != null && filename != null && filename.endsWith(".xml"), content == null ? "" : content);
        openTabs.add(activeTab);

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);

        if (binding.languageSelector != null) binding.languageSelector.setOnClickListener(v -> showLanguageSelector());
        if (binding.issueStatusCard != null) binding.issueStatusCard.setOnClickListener(v -> {
            if (diagnosticsBehavior != null) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        });

        setupToolbar();
        setupEditor();
        selectionSubscription = editor.subscribeEvent(SelectionChangeEvent.class, (event, unsubscribe) -> updateCursorPosition());
        updateCursorPosition();
        applySyntaxHighlighting();
        setupTabs();
        setupSearchPanel();
        setupDiagnosticsPanel();
        setupProjectExplorer();

        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            triggerRealtimeDiagnostics();
        }

        if (projectFile != null && projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary != null && projectLibrary.isEnabled()) {
            setNote("Use AppCompat Manager to modify attributes for CoordinatorLayout, Toolbar, and other appcompat layouts/widgets.");
        }

        if (binding.close != null) binding.close.setOnClickListener(v -> { prefs.edit().putInt("note_" + sc_id, 1).apply(); setNote(null); });
        if (binding.noteCard != null) binding.noteCard.setOnClickListener(v -> toAppCompat());

        if (binding.appBarLayout != null) UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(editor, true, false, true, false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (binding.editorContentRoot != null) {
            ViewCompat.setOnApplyWindowInsetsListener(binding.editorContentRoot, (view, windowInsets) -> {
                int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
                view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), imeBottom);
                return windowInsets;
            });
        }

        if (binding.navViewExplorer != null) UI.addSystemWindowInsetToPadding(binding.navViewExplorer, true, true, true, true);
    }

    private void syncStatusBarColor() {
        if (editor == null || binding == null || binding.statusBar == null) return;
        int bgColor = editor.getColorScheme().getColor(EditorColorScheme.WHOLE_BACKGROUND);
        int textColor = editor.getColorScheme().getColor(EditorColorScheme.TEXT_NORMAL);
        binding.statusBar.setBackgroundColor(bgColor);
        if (binding.tvLanguage != null) binding.tvLanguage.setTextColor(textColor);
        if (binding.tvEncoding != null) binding.tvEncoding.setTextColor(textColor);
        if (binding.tvCursorPos != null) binding.tvCursorPos.setTextColor(textColor);
        if (binding.languageSelector != null) binding.languageSelector.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerHighest));
    }

    private void updateCursorPosition() {
        if (editor == null || binding == null || binding.tvCursorPos == null) return;
        try {
            io.github.rosemoe.sora.text.Cursor cursor = editor.getCursor();
            if (cursor == null) return;
            int line = cursor.getRightLine() + 1;
            int column = cursor.getRightColumn() + 1;
            binding.tvCursorPos.setText("Ln " + line + ", Col " + column);
        } catch (Exception ignored) { }
    }

    private void applySyntaxHighlighting() {
        if (filename == null || editor == null) return;
        LanguageSpec spec = selectedLanguageId >= 0
                ? LanguageSpec.fromId(selectedLanguageId)
                : CodeEditorLanguages.resolveLanguageSpec(filename);
        selectedLanguageId = spec.id;
        SrcCodeEditor.applyLanguageSpec(this, editor, spec);
        SrcCodeEditor.languageId = spec.id;
        if (binding.tvLanguage != null) binding.tvLanguage.setText(spec.label);
        syncStatusBarColor();
    }

    private void showLanguageSelector() {
        SrcCodeEditor.showSwitchLanguageDialog(this, editor, (dialog, which) -> {
            SrcCodeEditor.selectLanguage(this, editor, which);
            selectedLanguageId = which;
            LanguageSpec selectedLanguage = LanguageSpec.fromId(which);
            if (binding.tvLanguage != null) binding.tvLanguage.setText(selectedLanguage.label);
            syncStatusBarColor();
            dialog.dismiss();
        });
    }

    private void loadProjectHeaderData() {
        try {
            HashMap<String, Object> metadata = a.a.a.lC.b(sc_id);
            if (metadata != null) {
                String name = a.a.a.yB.c(metadata, "my_ws_name");
                if (!TextUtils.isEmpty(name)) projectName = name;
            }
        } catch (Exception ignored) { }
        if (TextUtils.isEmpty(projectName)) projectName = "MyProject";
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        Drawable projectIcon = resolveProjectIcon();
        binding.toolbar.setLogo(projectIcon);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setLogo(projectIcon);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
            getSupportActionBar().setDisplayUseLogoEnabled(true);
            getSupportActionBar().setTitle(projectName);
            getSupportActionBar().setSubtitle(sc_id == null ? "" : sc_id);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_mtrl_menu);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (binding.drawerLayout != null && binding.drawerLayout.getDrawerLockMode(GravityCompat.END) != DrawerLayout.LOCK_MODE_LOCKED_CLOSED) {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.END)) binding.drawerLayout.closeDrawer(GravityCompat.END);
                else openProjectExplorer();
            } else {
                onBackPressedCallback.handleOnBackPressed();
            }
        });
        if (binding.btnCloseExplorer != null) binding.btnCloseExplorer.setOnClickListener(v -> {
            if (binding.drawerLayout != null) binding.drawerLayout.closeDrawer(GravityCompat.END);
        });
    }

    private Drawable resolveProjectIcon() {
        int iconSize = Math.max(1, dp(28f));
        try {
            HashMap<String, Object> metadata = a.a.a.lC.b(sc_id);
            if (metadata != null && a.a.a.yB.a(metadata, "custom_icon")) {
                File iconFile = new File(a.a.a.wq.e() + File.separator + sc_id, "icon.png");
                if (iconFile.isFile() && iconFile.canRead()) {
                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(iconFile.getAbsolutePath(), bounds);
                    int sampleSize = 1;
                    while (bounds.outWidth / (sampleSize * 2) >= iconSize && bounds.outHeight / (sampleSize * 2) >= iconSize) {
                        sampleSize *= 2;
                    }
                    BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
                    decodeOptions.inSampleSize = sampleSize;
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath(), decodeOptions);
                    if (bitmap != null) {
                        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, iconSize, iconSize, true);
                        if (scaled != bitmap) bitmap.recycle();
                        return new FixedSizeDrawable(new BitmapDrawable(getResources(), scaled), iconSize);
                    }
                }
            }
        } catch (Exception ignored) { }
        Drawable fallback = AppCompatResources.getDrawable(this, R.drawable.sketch_app_icon);
        return fallback == null ? null : new FixedSizeDrawable(fallback, iconSize);
    }

    private void setupTabs() {
        if (binding.editorTabs == null) return;
        if (!tabListenerAdded) {
            binding.editorTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override public void onTabSelected(TabLayout.Tab tab) {
                    if (suppressTabChanges || tab == null) return;
                    int position = tab.getPosition();
                    if (position < 0 || position >= openTabs.size()) return;
                    EditorTab target = openTabs.get(position);
                    if (target != activeTab) requestTabSwitch(target);
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) { }
                @Override public void onTabReselected(TabLayout.Tab tab) { }
            });
            tabListenerAdded = true;
        }
        rebuildTabStrip(activeTab);
    }

    private void rebuildTabStrip(EditorTab selected) {
        if (binding.editorTabs == null) return;
        suppressTabChanges = true;
        binding.editorTabs.removeAllTabs();
        for (EditorTab model : openTabs) {
            TabLayout.Tab tab = binding.editorTabs.newTab().setText(model.title);
            tab.setCustomView(createTabView(model));
            binding.editorTabs.addTab(tab, false);
        }
        int index = openTabs.indexOf(selected);
        if (index >= 0 && index < binding.editorTabs.getTabCount()) binding.editorTabs.selectTab(binding.editorTabs.getTabAt(index));
        suppressTabChanges = false;
        for (EditorTab model : openTabs) updateTabTitle(model);
    }

    private View createTabView(EditorTab model) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(12), 0, dp(8), 0);
        TextView title = new TextView(this);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        title.setMaxWidth(dp(170));
        title.setTextSize(13);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        title.setText(model.title);
        row.addView(title, new LinearLayout.LayoutParams(-2, -1));
        ImageView close = new ImageView(this);
        close.setImageResource(R.drawable.ic_mtrl_close);
        close.setColorFilter(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        close.setPadding(dp(6), dp(6), dp(6), dp(6));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(28), dp(28));
        closeParams.setMarginStart(dp(4));
        row.addView(close, closeParams);
        model.tabTitleView = title;
        close.setContentDescription("Close " + model.title);
        close.setOnClickListener(v -> closeTab(model));
        return row;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void updateTabTitle(EditorTab tab) {
        if (tab == null || tab.tabTitleView == null) return;
        boolean modified = isTabModified(tab);
        tab.tabTitleView.setText(modified ? tab.title + "  •" : tab.title);
        tab.tabTitleView.setTextColor(ThemeUtils.getColor(this,
                tab == activeTab ? R.attr.colorPrimary : R.attr.colorOnSurface));
    }

    private static final class FixedSizeDrawable extends Drawable {
        private final Drawable delegate;
        private final int size;

        FixedSizeDrawable(Drawable delegate, int size) {
            this.delegate = delegate;
            this.size = size;
        }

        @Override public int getIntrinsicWidth() { return size; }

        @Override public int getIntrinsicHeight() { return size; }

        @Override public void draw(@NonNull Canvas canvas) {
            delegate.setBounds(getBounds());
            delegate.draw(canvas);
        }

        @Override public void setAlpha(int alpha) { delegate.setAlpha(alpha); }

        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { delegate.setColorFilter(colorFilter); }

        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static final class EditorTab {
        final String title;
        final String path;
        final ProjectFileBean layoutBean;
        final boolean databaseLayout;
        String savedContent;
        String workingContent;
        TextView tabTitleView;

        EditorTab(String title, String path, ProjectFileBean layoutBean, boolean databaseLayout, String initialContent) {
            this.title = title == null ? "File" : title;
            this.path = path;
            this.layoutBean = layoutBean;
            this.databaseLayout = databaseLayout;
            this.savedContent = initialContent == null ? "" : initialContent;
            this.workingContent = this.savedContent;
        }

        String key() {
            if (databaseLayout) return "layout:" + title;
            if (path == null) return "name:" + title;
            try { return "file:" + new File(path).getCanonicalPath(); }
            catch (Exception ignored) { return "file:" + new File(path).getAbsolutePath(); }
        }
    }

    private void registerEditorContentListener() {
        if (editorContentListener != null) {
            editor.getText().removeContentListener(editorContentListener);
            editor.getText().addContentListener(editorContentListener);
        }
    }

    private void loadXmlToEditor(ProjectFileBean bean) {
        try {
            a.a.a.jq buildConfig = new a.a.a.jq();
            a.a.a.Ox xmlGenerator = new a.a.a.Ox(buildConfig, bean);
            xmlGenerator.setExcludeAppCompat(true);
            ArrayList<ViewBean> viewBeans = jC.a(sc_id).d(bean.getXmlName());
            ViewBean viewFab = jC.a(sc_id).h(bean.getXmlName());
            xmlGenerator.a(a.a.a.eC.a(viewBeans), viewFab);
            
            content = xmlGenerator.b();
            beforeContent = content;
            editor.setText(content);
            registerEditorContentListener();
        } catch(Exception e) {
            SketchwareUtil.toastError("Failed to generate layout.");
        }
    }

    private void checkAndApplySnippets(int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
        if (insertedContent == null || insertedContent.length() != 1) return;
        char trigger = insertedContent.charAt(0);
        if (trigger != ' ' && trigger != '\n') return;
        
        try {
            Content content = editor.getText();
            String lineText = content.getLineString(startLine);
            
            int wordEnd = startColumn;
            int wordStart = wordEnd - 1;
            
            while (wordStart >= 0 && Character.isLetterOrDigit(lineText.charAt(wordStart))) {
                wordStart--;
            }
            wordStart++; 
            
            if (wordStart < wordEnd) {
                String word = lineText.substring(wordStart, wordEnd);
                String snippet = getSnippetFor(word);
                
                if (snippet != null) {
                    final int fWordStart = wordStart;
                    editor.post(() -> {
                        try {
                            int cursorIndex = snippet.indexOf('|');
                            String cleanSnippet = snippet.replace("|", "");
                            
                            editor.getText().delete(startLine, fWordStart, endLine, endColumn);
                            editor.getText().insert(startLine, fWordStart, cleanSnippet);
                            
                            if (cursorIndex != -1) {
                                int linesAdded = 0;
                                int lastNewLine = -1;
                                for(int i=0; i<cursorIndex; i++) {
                                    if(cleanSnippet.charAt(i) == '\n') {
                                        linesAdded++;
                                        lastNewLine = i;
                                    }
                                }
                                int targetLine = startLine + linesAdded;
                                int targetColumn = (lastNewLine == -1) ? (fWordStart + cursorIndex) : (cursorIndex - lastNewLine - 1);
                                editor.setSelection(targetLine, targetColumn);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }
    
    private String getSnippetFor(String word) {
        boolean isJava = (SrcCodeEditor.languageId == 1) || (filename != null && filename.endsWith(".java"));
        boolean isXml = (filename != null && filename.endsWith(".xml"));
        
        if (isJava) {
            switch (word) {
                case "toast": return "Toast.makeText(getApplicationContext(), \"|\", Toast.LENGTH_SHORT).show();";
                case "logd": return "Log.d(\"TAG\", \"|\");";
                case "loge": return "Log.e(\"TAG\", \"|\", e);";
                case "intent": return "Intent intent = new Intent(|);\nstartActivity(intent);";
                case "fori": return "for (int i = 0; i < |; i++) {\n    \n}";
                case "psfs": return "public static final String | = \"\";";
                case "if": return "if (|) {\n    \n}";
                case "ifelse": return "if (|) {\n    \n} else {\n    \n}";
                case "tryc": return "try {\n    |\n} catch (Exception e) {\n    e.printStackTrace();\n}";
                case "find": return "findViewById(R.id.|);";
                case "et": return "EditText | = findViewById(R.id.|);";
                case "tvf": return "TextView | = findViewById(R.id.|);";
                case "btnf": return "Button | = findViewById(R.id.|);";
                case "ivf": return "ImageView | = findViewById(R.id.|);";
                case "cbf": return "CheckBox | = findViewById(R.id.|);";
                case "rbf": return "RadioButton | = findViewById(R.id.|);";
                case "swf": return "Switch | = findViewById(R.id.|);";
                case "spf": return "Spinner | = findViewById(R.id.|);";
                case "rvf": return "RecyclerView | = findViewById(R.id.|);";
                case "lvf": return "ListView | = findViewById(R.id.|);";
                case "gvf": return "GridView | = findViewById(R.id.|);";
                case "cvf": return "CardView | = findViewById(R.id.|);";
                case "pbf": return "ProgressBar | = findViewById(R.id.|);";
                case "fabf": return "FloatingActionButton | = findViewById(R.id.|);";
                case "onclick": return "setOnClickListener(v -> {\n    |\n});";
                case "thread": return "new Thread(() -> {\n    |\n}).start();";
                case "runui": return "runOnUiThread(() -> {\n    |\n});";
                case "handler": return "new Handler().postDelayed(() -> {\n    |\n}, 1000);";
                case "finish": return "finish();";
                case "finisha": return "finishAffinity();";
                case "back": return "onBackPressed();";
                case "random": return "Random random = new Random();\nint | = random.nextInt();";
                case "array": return "ArrayList<String> | = new ArrayList<>();";
                case "hashmap": return "HashMap<String, Object> | = new HashMap<>();";
                case "dialog": return "new AlertDialog.Builder(this)\n.setTitle(\"|\")\n.setMessage(\"\")\n.setPositiveButton(\"OK\", null)\n.show();";
                case "date": return "String | = new SimpleDateFormat(\"dd/MM/yyyy\", Locale.getDefault()).format(new Date());";
                case "time": return "String | = new SimpleDateFormat(\"HH:mm\", Locale.getDefault()).format(new Date());";
                case "switch": return "switch (|) {\n    case :\n        break;\n    default:\n        break;\n}";
                case "while": return "while (|) {\n    \n}";
                case "dowhile": return "do {\n    |\n} while ();";
                case "foreach": return "for (Object item : |) {\n    \n}";
                case "return": return "return |;";
                case "newobj": return "| obj = new |();";
                case "bundle": return "Bundle bundle = new Bundle();";
                case "putextra": return "intent.putExtra(\"|\", \"\");";
                case "getextra": return "String | = getIntent().getStringExtra(\"\");";
                case "shared": return "SharedPreferences sp = getSharedPreferences(\"data\", MODE_PRIVATE);";
                case "editor": return "SharedPreferences.Editor editor = sp.edit();";
                case "commit": return "editor.commit();";
                case "apply": return "editor.apply();";
                case "prefput": return "editor.putString(\"|\", \"\");";
                case "prefget": return "String | = sp.getString(\"\", \"\");";
                case "anim": return "overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);";
                case "post": return "view.post(() -> {\n    |\n});";
                case "visible": return "|.setVisibility(View.VISIBLE);";
                case "gone": return "|.setVisibility(View.GONE);";
                case "invisible": return "|.setVisibility(View.INVISIBLE);";
                case "enable": return "|.setEnabled(true);";
                case "disable": return "|.setEnabled(false);";
                case "text": return "|.setText(\"\");";
                case "gettext": return "|.getText().toString();";
                case "hint": return "|.setHint(\"\");";
                case "focus": return "|.requestFocus();";
                case "clear": return "|.setText(\"\");";
                case "img": return "|.setImageResource(R.drawable.|);";
                case "bg": return "|.setBackgroundResource(R.drawable.|);";
                case "color": return "|.setTextColor(Color.parseColor(\"#000000\"));";
                case "parse": return "Integer.parseInt(|);";
                case "str": return "String.valueOf(|);";
                case "equals": return "|.equals(\"\")";
                case "isempty": return "TextUtils.isEmpty(|)";
                case "len": return "|.length()";
                case "trim": return "|.trim()";
                case "upper": return "|.toUpperCase()";
                case "lower": return "|.toLowerCase()";
                case "contains": return "|.contains(\"\")";
                case "replace": return "|.replace(\"\", \"\")";
                case "split": return "|.split(\",\")";
                case "sleep": return "Thread.sleep(|);";
                case "exit": return "System.exit(0);";
            }
        } else if (isXml) {
            switch (word) {
                case "ll": return "<LinearLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:orientation=\"vertical\">\n    |\n</LinearLayout>";
                case "rl": return "<RelativeLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n    |\n</RelativeLayout>";
                case "tv": return "<TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
                case "btn": return "<Button\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
                case "iv": return "<ImageView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:src=\"@drawable/|\" />";
                case "cl": return "<androidx.constraintlayout.widget.ConstraintLayout\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    |\n\n</androidx.constraintlayout.widget.ConstraintLayout>";
                case "sv": return "<ScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\">\n\n    |\n\n</ScrollView>";
                case "hsv": return "<HorizontalScrollView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    |\n\n</HorizontalScrollView>";
                case "et": return "<EditText\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\"\n    android:hint=\"|\" />";
                case "cb": return "<CheckBox\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
                case "rb": return "<RadioButton\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"\n    android:text=\"|\" />";
                case "rg": return "<RadioGroup\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\">\n    |\n</RadioGroup>";
                case "sw": return "<Switch\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
                case "pb": return "<ProgressBar\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
                case "seek": return "<SeekBar\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\" />";
                case "rating": return "<RatingBar\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\" />";
                case "spinner": return "<Spinner\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\" />";
                case "lv": return "<ListView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
                case "gv": return "<GridView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
                case "rv": return "<RecyclerView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
                case "card": return "<androidx.cardview.widget.CardView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"wrap_content\">\n\n    |\n\n</androidx.cardview.widget.CardView>";
                case "space": return "<Space\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"16dp\" />";
                case "view": return "<View\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"1dp\"\n    android:background=\"#DDDDDD\" />";
                case "include": return "<include layout=\"@layout/|\" />";
                case "merge": return "<merge>\n    |\n</merge>";
                case "frag": return "<fragment\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\"\n    android:name=\"|\" />";
            }
        }
        return null;
    }

    private static final class ExplorerNode {
        final String name;
        final File file;
        final ProjectFileBean layoutBean;
        final ProjectFileBean javaBean;
        final boolean directory;
        final int depth;
        final List<ExplorerNode> children = new ArrayList<>();
        boolean expanded;

        ExplorerNode(String name, File file, ProjectFileBean layoutBean, ProjectFileBean javaBean,
                     boolean directory, int depth, boolean expanded) {
            this.name = name;
            this.file = file;
            this.layoutBean = layoutBean;
            this.javaBean = javaBean;
            this.directory = directory;
            this.depth = depth;
            this.expanded = expanded;
        }
    }

    private void setupProjectExplorer() {
        if (binding.rvExplorer == null) return;
        binding.rvExplorer.setLayoutManager(new LinearLayoutManager(this));
        explorerAdapter = new ExplorerTreeAdapter(buildExplorerTree());
        binding.rvExplorer.setAdapter(explorerAdapter);
        if (binding.tvExplorerProjectId != null) binding.tvExplorerProjectId.setText(sc_id == null ? projectName : projectName + " \u00b7 " + sc_id);
        if (binding.etExplorerSearch != null) {
            binding.etExplorerSearch.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (explorerAdapter != null) explorerAdapter.setQuery(s == null ? "" : s.toString());
                }
                @Override public void afterTextChanged(Editable s) { }
            });
        }
    }

    private void openProjectExplorer() {
        if (binding.drawerLayout == null) return;
        if (explorerAdapter == null) {
            setupProjectExplorer();
        } else {
            explorerAdapter.setRoot(buildExplorerTree());
            if (binding.etExplorerSearch != null) explorerAdapter.setQuery(binding.etExplorerSearch.getText() == null ? "" : binding.etExplorerSearch.getText().toString());
        }
        binding.drawerLayout.openDrawer(GravityCompat.END);
    }

    private boolean isCustomJavaEnabled() {
        return new ProjectSettings(sc_id)
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_JAVA, "false")
                .equals("true");
    }

    private ExplorerNode buildExplorerTree() {
        ExplorerNode root = new ExplorerNode(projectName, null, null, null, true, 0, true);
        ExplorerNode app = childDirectory(root, "app", 1, true);
        ExplorerNode src = childDirectory(app, "src", 2, true);
        ExplorerNode main = childDirectory(src, "main", 3, true);

        if (isCustomJavaEnabled()) {
            ExplorerNode javaNode = childDirectory(main, "java", 4, true);
            File customJavaDir = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java");
            Set<String> javaNames = new HashSet<>();
            ArrayList<ProjectFileBean> beans = getProjectFileBeans();
            for (ProjectFileBean bean : beans) {
                if (bean == null) continue;
                String javaName;
                try { javaName = bean.getJavaName(); } catch (Exception ignored) { javaName = null; }
                if (TextUtils.isEmpty(javaName) || !javaName.toLowerCase(Locale.ROOT).endsWith(".java")) continue;
                String normalized = javaName.toLowerCase(Locale.ROOT);
                if (javaNames.add(normalized)) {
                    javaNode.children.add(new ExplorerNode(javaName, new File(customJavaDir, javaName), null, bean,
                            false, 5, false));
                }
            }
            if (customJavaDir.isDirectory()) {
                addRealFiles(javaNode, customJavaDir, 5, javaNames, true, 5);
            }
        }

        ExplorerNode res = childDirectory(main, "res", 4, true);
        ExplorerNode layout = childDirectory(res, "layout", 5, true);
        Set<String> layoutNames = new HashSet<>();
        for (ProjectFileBean bean : getProjectFileBeans()) {
            if (bean == null) continue;
            String xmlName;
            try { xmlName = bean.getXmlName(); } catch (Exception ignored) { xmlName = null; }
            if (TextUtils.isEmpty(xmlName) || !xmlName.endsWith(".xml")) continue;
            String normalized = xmlName.toLowerCase(Locale.ROOT);
            if (layoutNames.add(normalized)) {
                layout.children.add(new ExplorerNode(xmlName, null, bean, null, false, 6, false));
            }
        }
        File layoutFolder = new File(new FilePathUtil().getPathResource(sc_id), "layout");
        addRealFiles(layout, layoutFolder, 6, layoutNames, false, 5);
        layout.children.sort(Comparator.comparing(node -> node.name.toLowerCase(Locale.ROOT)));

        File resourceDir = new File(new FilePathUtil().getPathResource(sc_id));
        addResourceDirectories(res, resourceDir, 5);

        String customManifestPath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_manifest.xml";
        if (new ProjectSettings(sc_id)
                .getValue(ProjectSettings.SETTING_ENABLE_CUSTOM_MANIFEST, "false").equals("true")) {
            File manifest = new File(customManifestPath);
            if (manifest.isFile()) main.children.add(new ExplorerNode("AndroidManifest.xml", manifest, null, null, false, 4, false));
        }

        File customGradle = new File(new FilePathUtil().getPathCustomGradle(sc_id), "app_build.gradle");
        if (customGradle.isFile()) root.children.add(new ExplorerNode("app_build.gradle", customGradle, null, null, false, 1, false));

        root.children.sort(Comparator.comparing(node -> node.name.equals("app") ? "" : node.name.toLowerCase(Locale.ROOT)));
        return root;
    }

    private ExplorerNode childDirectory(ExplorerNode parent, String name, int depth, boolean expanded) {
        ExplorerNode node = new ExplorerNode(name, null, null, null, true, depth, expanded);
        parent.children.add(node);
        return node;
    }

    private ArrayList<ProjectFileBean> getProjectFileBeans() {
        ArrayList<ProjectFileBean> result = new ArrayList<>();
        try {
            ArrayList<ProjectFileBean> mainFiles = jC.b(sc_id).b();
            ArrayList<ProjectFileBean> extraFiles = jC.b(sc_id).c();
            if (mainFiles != null) result.addAll(mainFiles);
            if (extraFiles != null) {
                Set<String> seen = new HashSet<>();
                for (ProjectFileBean bean : result) if (bean != null && bean.getXmlName() != null) seen.add(bean.getXmlName());
                for (ProjectFileBean bean : extraFiles) {
                    if (bean != null && (bean.getXmlName() == null || seen.add(bean.getXmlName()))) result.add(bean);
                }
            }
        } catch (Exception ignored) { }
        return result;
    }

    private ProjectFileBean findProjectFileBean(String xmlName) {
        if (TextUtils.isEmpty(xmlName)) return null;
        for (ProjectFileBean bean : getProjectFileBeans()) {
            if (bean != null && xmlName.equals(bean.getXmlName())) return bean;
        }
        return null;
    }

    private void addResourceDirectories(ExplorerNode parent, File realRoot, int depth) {
        if (realRoot == null || !realRoot.isDirectory() || depth > 8) return;
        File[] children = realRoot.listFiles();
        if (children == null) return;
        ArrayList<File> dirs = new ArrayList<>();
        ArrayList<File> files = new ArrayList<>();
        for (File child : children) {
            if (child == null || child.getName().startsWith(".")) continue;
            if (child.isDirectory()) {
                if (!"layout".equalsIgnoreCase(child.getName())) dirs.add(child);
            } else if (isTextFile(child) && (isCustomJavaEnabled() || !child.getName().toLowerCase(Locale.ROOT).endsWith(".java"))) files.add(child);
        }
        dirs.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        files.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        for (File dir : dirs) {
            ExplorerNode folder = new ExplorerNode(dir.getName(), dir, null, null, true, depth, false);
            parent.children.add(folder);
            addResourceDirectories(folder, dir, depth + 1);
        }
        for (File file : files) parent.children.add(new ExplorerNode(file.getName(), file, null, null, false, depth, false));
    }

    private void addRealFiles(ExplorerNode parent, File directory, int depth, Set<String> alreadyAdded,
                              boolean javaOnly, int maxDepth) {
        if (directory == null || !directory.isDirectory() || depth > maxDepth) return;
        File[] files = directory.listFiles();
        if (files == null) return;
        ArrayList<File> ordered = new ArrayList<>();
        Collections.addAll(ordered, files);
        ordered.sort((first, second) -> {
            if (first.isDirectory() != second.isDirectory()) return first.isDirectory() ? -1 : 1;
            return first.getName().compareToIgnoreCase(second.getName());
        });
        for (File file : ordered) {
            if (file == null || file.getName().startsWith(".")) continue;
            if (file.isDirectory()) {
                ExplorerNode child = new ExplorerNode(file.getName(), file, null, null, true, depth, false);
                parent.children.add(child);
                addRealFiles(child, file, depth + 1, alreadyAdded, javaOnly, maxDepth);
                continue;
            }
            if (!isTextFile(file)) continue;
            if (!isCustomJavaEnabled() && file.getName().toLowerCase(Locale.ROOT).endsWith(".java")) continue;
            if (javaOnly && !file.getName().toLowerCase(Locale.ROOT).endsWith(".java")) continue;
            String normalized = file.getName().toLowerCase(Locale.ROOT);
            if (alreadyAdded != null && !alreadyAdded.add(normalized)) continue;
            parent.children.add(new ExplorerNode(file.getName(), file, null, null, false, depth, false));
        }
    }

    private boolean isTextFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".kt")
                || name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".gradle")
                || name.endsWith(".properties") || name.endsWith(".pro") || name.endsWith(".md")
                || name.endsWith(".sh") || name.endsWith(".c") || name.endsWith(".h")
                || name.endsWith(".cpp") || name.endsWith(".hpp") || name.endsWith(".cmake");
    }

    private void onExplorerFileClick(ExplorerNode node) {
        if (node.javaBean != null) {
            if (!isCustomJavaEnabled()) {
                SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
                return;
            }
            openCustomJavaTab(node.javaBean, node.name);
            return;
        }
        if (node.layoutBean != null) {
            openLayoutTab(node.layoutBean);
            return;
        }
        if (node.file != null && node.file.isFile()) openPhysicalFileTab(node.file);
    }

    private void openLayoutTab(ProjectFileBean bean) {
        if (bean == null) return;
        k();
        new Thread(() -> {
            String xml = generateXmlContent(bean);
            runOnUiThread(() -> {
                h();
                if (isFinishing() || isDestroyed()) return;
                if (xml == null) {
                    SketchwareUtil.toastError("Could not load layout " + bean.getXmlName());
                    return;
                }
                requestOpenTab(new EditorTab(bean.getXmlName(), null, bean, true, xml));
            });
        }, "sketchware-layout-load").start();
    }

    private String generateXmlContent(ProjectFileBean bean) {
        try {
            a.a.a.jq buildConfig = new a.a.a.jq();
            a.a.a.Ox xmlGenerator = new a.a.a.Ox(buildConfig, bean);
            xmlGenerator.setExcludeAppCompat(true);
            ArrayList<ViewBean> viewBeans = jC.a(sc_id).d(bean.getXmlName());
            ViewBean viewFab = jC.a(sc_id).h(bean.getXmlName());
            xmlGenerator.a(a.a.a.eC.a(viewBeans), viewFab);
            return xmlGenerator.b();
        } catch (Exception e) {
            return null;
        }
    }

    private void openCustomJavaTab(ProjectFileBean bean, String javaName) {
        if (!isCustomJavaEnabled()) {
            SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
            return;
        }
        File target = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + sc_id + "/custom_java/" + javaName);
        if (target.isFile()) {
            openPhysicalFileTab(target);
            return;
        }
        if (bean == null || TextUtils.isEmpty(javaName)) {
            SketchwareUtil.toastError("No Java source is available for this file.");
            return;
        }
        k();
        new Thread(() -> {
            Exception failure = null;
            try {
                String source = new a.a.a.yq(getApplicationContext(), sc_id)
                        .getFileSrc(javaName, jC.b(sc_id), jC.a(sc_id), jC.c(sc_id));
                if (TextUtils.isEmpty(source)) {
                    File generated = new File(new FilePathUtil().getPathJava(sc_id), javaName);
                    if (generated.isFile()) source = FileUtil.readFile(generated.getAbsolutePath());
                }
                if (TextUtils.isEmpty(source)) throw new IOException("Generated source is empty.");
                File parent = target.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    throw new IOException("Could not create the custom Java directory.");
                }
                FileUtil.writeFile(target.getAbsolutePath(), source);
                String persisted = FileUtil.readFile(target.getAbsolutePath());
                if (!Objects.equals(source, persisted)) throw new IOException("The generated source could not be verified after writing.");
            } catch (Exception error) {
                failure = error;
            }
            final Exception finalFailure = failure;
            runOnUiThread(() -> {
                h();
                if (isFinishing() || isDestroyed()) return;
                if (finalFailure != null) {
                    SketchwareUtil.toastError("Could not prepare custom Java file: " + finalFailure.getMessage());
                    return;
                }
                openPhysicalFileTab(target);
            });
        }, "sketchware-java-source-load").start();
    }

    private void openPhysicalFileTab(File file) {
        if (file == null || !file.isFile() || !file.canRead()) {
            SketchwareUtil.toastError("File cannot be opened.");
            return;
        }
        if (file.getName().toLowerCase(Locale.ROOT).endsWith(".java") && !isCustomJavaEnabled()) {
            SketchwareUtil.toastError("Custom Java is disabled. Enable it in Advanced Settings first.");
            return;
        }
        if (!isTextFile(file)) {
            SketchwareUtil.toastError("This file type is not supported by the text editor.");
            return;
        }
        try {
            String text = FileUtil.readFile(file.getAbsolutePath());
            requestOpenTab(new EditorTab(file.getName(), file.getAbsolutePath(), null, false, text == null ? "" : text));
        } catch (Exception e) {
            SketchwareUtil.toastError("Could not read file: " + e.getMessage());
        }
    }

    private void requestOpenTab(EditorTab requested) {
        for (EditorTab existing : openTabs) {
            if (existing.key().equals(requested.key())) {
                requestTabSwitch(existing);
                if (binding.drawerLayout != null) binding.drawerLayout.closeDrawer(GravityCompat.END);
                return;
            }
        }
        Runnable openRequested = () -> {
            openTabs.add(requested);
            rebuildTabStrip(requested);
            switchToTab(requested);
            if (binding.drawerLayout != null) binding.drawerLayout.closeDrawer(GravityCompat.END);
        };
        if (isContentModified()) {
            EditorTab current = activeTab;
            showUnsavedChangesDialog(current, "open " + requested.title,
                    () -> { if (saveCurrentTab()) openRequested.run(); },
                    () -> { current.workingContent = current.savedContent; openRequested.run(); });
        } else {
            if (activeTab != null) activeTab.workingContent = editor.getText().toString();
            openRequested.run();
        }
    }

    private void requestTabSwitch(EditorTab target) {
        if (target == null || target == activeTab) return;
        if (isContentModified()) {
            EditorTab current = activeTab;
            restoreSelectedTabVisual();
            showUnsavedChangesDialog(current, "switch files",
                    () -> { if (saveCurrentTab()) switchToTab(target); },
                    () -> { current.workingContent = current.savedContent; switchToTab(target); });
        } else {
            if (activeTab != null) activeTab.workingContent = editor.getText().toString();
            switchToTab(target);
        }
    }

    private void restoreSelectedTabVisual() {
        int selected = openTabs.indexOf(activeTab);
        if (selected >= 0 && selected < binding.editorTabs.getTabCount()) {
            suppressTabChanges = true;
            binding.editorTabs.selectTab(binding.editorTabs.getTabAt(selected));
            suppressTabChanges = false;
        }
    }

    private void switchToTab(EditorTab target) {
        if (target == null || editor == null) return;
        int targetIndex = openTabs.indexOf(target);
        if (targetIndex >= 0 && targetIndex < binding.editorTabs.getTabCount()
                && binding.editorTabs.getSelectedTabPosition() != targetIndex) {
            suppressTabChanges = true;
            binding.editorTabs.selectTab(binding.editorTabs.getTabAt(targetIndex));
            suppressTabChanges = false;
        }
        try { editor.getText().removeContentListener(editorContentListener); } catch (Exception ignored) { }
        activeTab = target;
        filename = target.title;
        selectedLanguageId = -1;
        projectFile = target.layoutBean;
        content = target.workingContent;
        beforeContent = target.savedContent;
        isLoadingTab = true;
        editor.setText(target.workingContent == null ? "" : target.workingContent);
        isLoadingTab = false;
        updateCursorPosition();
        registerEditorContentListener();
        getIntent().putExtra("title", target.title);
        getIntent().putExtra("sc_id", sc_id);
        getIntent().putExtra("content", target.workingContent == null ? "" : target.workingContent);
        if (target.path != null) getIntent().putExtra("file_path", target.path);
        else getIntent().removeExtra("file_path");
        applySyntaxHighlighting();
        updateTabTitle(target);
        invalidateOptionsMenu();
        updateMenuState();
        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        diagGeneration.incrementAndGet();
        liveDiagnostics.clear();
        refreshDiagnostics(false);
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml")) {
            triggerRealtimeDiagnostics();
        }
        updateFindResults();
        if (explorerAdapter != null) explorerAdapter.notifyDataSetChanged();
        if (pendingDiagnosticJump != null) {
            Diagnostic jump = pendingDiagnosticJump;
            pendingDiagnosticJump = null;
            if (diagnosticFileMatches(jump.fileName, filename, target.path)) editor.post(() -> jumpToDiagnosticLocation(jump));
        }
    }

    private void closeTab(EditorTab target) {
        if (target == null || openTabs.size() <= 1) {
            SketchwareUtil.toast("Keep at least one file open.");
            return;
        }
        boolean modified = isTabModified(target);
        Runnable closeWithoutSave = () -> closeTabNow(target);
        if (modified) {
            String text = target == activeTab ? editor.getText().toString() : target.workingContent;
            new MaterialAlertDialogBuilder(this)
                    .setIcon(R.drawable.ic_mtrl_warning)
                    .setTitle("Unsaved changes")
                    .setMessage("Save changes to " + target.title + " before closing this tab?")
                    .setPositiveButton("Save", (dialog, which) -> {
                        if (saveTab(target, text)) closeTabNow(target);
                    })
                    .setNegativeButton("Don't save", (dialog, which) -> closeWithoutSave.run())
                    .setNeutralButton("Cancel", null)
                    .show();
        } else closeTabNow(target);
    }

    private void closeTabNow(EditorTab target) {
        int index = openTabs.indexOf(target);
        if (index < 0 || openTabs.size() <= 1) return;
        EditorTab next = target == activeTab
                ? openTabs.get(index > 0 ? index - 1 : Math.min(index + 1, openTabs.size() - 1))
                : activeTab;
        openTabs.remove(target);
        if (target == activeTab) {
            activeTab = null;
            rebuildTabStrip(next);
            switchToTab(next);
        } else {
            rebuildTabStrip(activeTab);
        }
    }

    private void showUnsavedChangesDialog(EditorTab tab, String action, Runnable onSave, Runnable onDiscard) {
        new MaterialAlertDialogBuilder(this)
                .setIcon(R.drawable.ic_mtrl_warning)
                .setTitle("Unsaved changes")
                .setMessage("Save changes to " + (tab == null ? "this file" : tab.title) + " before you " + action + "?")
                .setPositiveButton("Save", (dialog, which) -> { if (onSave != null) onSave.run(); })
                .setNegativeButton("Don't save", (dialog, which) -> { if (onDiscard != null) onDiscard.run(); })
                .setNeutralButton("Cancel", null)
                .show();
    }

    private class ExplorerTreeAdapter extends RecyclerView.Adapter<ExplorerTreeAdapter.NodeHolder> {
        private ExplorerNode root;
        private final List<ExplorerNode> visibleNodes = new ArrayList<>();
        private String query = "";

        ExplorerTreeAdapter(ExplorerNode root) {
            this.root = root;
            rebuild();
        }

        void setQuery(String value) {
            query = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
            rebuild();
        }

        void setRoot(ExplorerNode value) {
            if (value != null) root = value;
            rebuild();
        }

        private void rebuild() {
            visibleNodes.clear();
            if (query.isEmpty()) addExpanded(root);
            else addMatches(root);
            notifyDataSetChanged();
        }

        private void addExpanded(ExplorerNode node) {
            visibleNodes.add(node);
            if (node.directory && node.expanded) for (ExplorerNode child : node.children) addExpanded(child);
        }

        private void addMatches(ExplorerNode node) {
            if (node.name.toLowerCase(Locale.ROOT).contains(query)) visibleNodes.add(node);
            for (ExplorerNode child : node.children) addMatches(child);
        }

        ExplorerNode findNode(String fileName) {
            return findNode(root, fileName);
        }

        private ExplorerNode findNode(ExplorerNode node, String fileName) {
            if (!node.directory && diagnosticFileMatches(fileName, node.name, node.file == null ? null : node.file.getAbsolutePath())) {
                return node;
            }
            for (ExplorerNode child : node.children) {
                ExplorerNode found = findNode(child, fileName);
                if (found != null) return found;
            }
            return null;
        }

        private boolean isActiveNode(ExplorerNode node) {
            if (activeTab == null || node.directory) return false;
            if (node.layoutBean != null) return activeTab.databaseLayout && node.layoutBean.getXmlName().equals(activeTab.title);
            return node.file != null && activeTab.path != null
                    && node.file.getAbsolutePath().equals(new File(activeTab.path).getAbsolutePath());
        }

        @NonNull @Override
        public NodeHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new NodeHolder(ViewCodeEditorExplorerItemBinding.inflate(getLayoutInflater(), parent, false));
        }

        @Override public void onBindViewHolder(@NonNull NodeHolder holder, int position) {
            ExplorerNode node = visibleNodes.get(position);
            ViewCodeEditorExplorerItemBinding row = holder.binding;
            ViewGroup.LayoutParams indentParams = row.explorerItemIndent.getLayoutParams();
            indentParams.width = query.isEmpty() ? dp(node.depth * 12f) : 0;
            row.explorerItemIndent.setLayoutParams(indentParams);
            row.explorerItemName.setText(node.name);
            row.explorerItemChevron.setVisibility(node.directory ? View.VISIBLE : View.INVISIBLE);
            row.explorerItemChevron.setImageResource(node.expanded ? R.drawable.ic_mtrl_arrow_down : R.drawable.ic_mtrl_arrow_right);
            row.explorerItemChevron.setColorFilter(ThemeUtils.getColor(ViewCodeEditorActivity.this, R.attr.colorOnSurfaceVariant));

            boolean selected = isActiveNode(node);
            int iconTint = ThemeUtils.getColor(ViewCodeEditorActivity.this,
                    selected ? R.attr.colorOnSecondaryContainer : node.directory ? R.attr.colorPrimary : R.attr.colorOnSurfaceVariant);
            String lowerName = node.name.toLowerCase(Locale.ROOT);
            if (node.directory) row.explorerItemIcon.setImageResource(node.expanded ? R.drawable.ic_mtrl_folder_open : R.drawable.ic_mtrl_folder);
            else if (lowerName.endsWith(".java")) row.explorerItemIcon.setImageResource(R.drawable.ic_mtrl_java);
            else if (lowerName.endsWith(".gradle")) row.explorerItemIcon.setImageResource(R.drawable.ic_mtrl_gradle);
            else if (lowerName.endsWith(".xml")) row.explorerItemIcon.setImageResource(R.drawable.ic_mtrl_code);
            else row.explorerItemIcon.setImageResource(R.drawable.ic_mtrl_file);
            row.explorerItemIcon.setColorFilter(iconTint);
            row.explorerItemName.setTextColor(ThemeUtils.getColor(ViewCodeEditorActivity.this,
                    selected ? R.attr.colorOnSecondaryContainer : R.attr.colorOnSurface));
            if (selected) {
                GradientDrawable background = new GradientDrawable();
                background.setColor(ThemeUtils.getColor(ViewCodeEditorActivity.this, R.attr.colorSecondaryContainer));
                background.setCornerRadius(dp(10f));
                row.explorerItemRoot.setBackground(background);
            } else {
                row.explorerItemRoot.setBackground(null);
            }

            row.explorerItemRoot.setOnClickListener(v -> {
                if (node.directory) {
                    node.expanded = !node.expanded;
                    rebuild();
                } else {
                    onExplorerFileClick(node);
                }
            });
        }

        @Override public int getItemCount() { return visibleNodes.size(); }

        class NodeHolder extends RecyclerView.ViewHolder {
            final ViewCodeEditorExplorerItemBinding binding;
            NodeHolder(ViewCodeEditorExplorerItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private final AtomicInteger diagGeneration = new AtomicInteger(0);

    private void triggerRealtimeDiagnostics() {
        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        final int myGen = diagGeneration.incrementAndGet();
        diagRunnable = () -> {
            final String text = editor.getText().toString();
            final String diagFile = filename;
            if (diagFile != null && diagFile.toLowerCase(Locale.ROOT).endsWith(".xml")) {
                new Thread(() -> {
                    List<Diagnostic> diags = new ArrayList<>();
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                            @Override public void warning(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.WARNING, diagFile, e.getLineNumber(), e.getColumnNumber(), diagnosticMessage(e)));
                            }
                            @Override public void error(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.ERROR, diagFile, e.getLineNumber(), e.getColumnNumber(), diagnosticMessage(e)));
                            }
                            @Override public void fatalError(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.ERROR, diagFile, e.getLineNumber(), e.getColumnNumber(), diagnosticMessage(e)));
                            }
                        });
                        builder.parse(new org.xml.sax.InputSource(new StringReader(text)));
                    } catch (Exception e) {
                        if (e instanceof org.xml.sax.SAXParseException) {
                            org.xml.sax.SAXParseException sax = (org.xml.sax.SAXParseException) e;
                            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, diagFile, sax.getLineNumber(), sax.getColumnNumber(), diagnosticMessage(sax)));
                        } else if (diags.isEmpty()) {
                            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, diagFile, 1, 0, String.valueOf(e.getMessage())));
                        }
                    }
                    runOnUiThread(() -> {
                        if (myGen != diagGeneration.get()) return;
                        liveDiagnostics.clear();
                        liveDiagnostics.addAll(diags);
                        refreshDiagnostics(false);
                    });
                }).start();
            }
        };
        diagHandler.postDelayed(diagRunnable, 800);
    }

    private String diagnosticMessage(org.xml.sax.SAXParseException e) {
        return humanizeXmlError(e).replaceFirst("^Line \\d+: ", "");
    }

    private List<Diagnostic> mergedDiagnostics() {
        LinkedHashMap<String, Diagnostic> unique = new LinkedHashMap<>();
        for (List<Diagnostic> source : Arrays.asList(liveDiagnostics, buildDiagnostics)) {
            for (Diagnostic diagnostic : source) {
                unique.putIfAbsent(diagnostic.severity + "|" + diagnostic.fileName + "|" + diagnostic.line + "|"
                        + diagnostic.column + "|" + diagnostic.message, diagnostic);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private void refreshDiagnostics(boolean expand) {
        List<Diagnostic> merged = mergedDiagnostics();
        updateIssueStatus(merged);
        if (diagnosticsAdapter != null) diagnosticsAdapter.update(merged);
        if (binding != null && binding.tvDiagnosticTitle != null) {
            binding.tvDiagnosticTitle.setText(merged.isEmpty() ? "No issues" : "Issues (" + merged.size() + ")");
        }
        if (diagnosticsBehavior == null) return;
        if (merged.isEmpty()) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        else if (expand) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private boolean diagnosticFileMatches(@Nullable String target, @Nullable String title, @Nullable String path) {
        if (target == null || target.isEmpty()) return true;
        String targetName = new File(target).getName();
        if (title != null && (title.equals(target) || title.equals(targetName))) return true;
        if (path != null) {
            if (path.equals(target) || path.endsWith(File.separator + target)) return true;
            return new File(path).getName().equals(targetName);
        }
        return false;
    }

    private void jumpToDiagnosticLocation(Diagnostic diagnostic) {
        Content text = editor.getText();
        int line = Math.max(0, Math.min(diagnostic.line - 1, text.getLineCount() - 1));
        int column = diagnostic.column > 0 ? Math.min(diagnostic.column - 1, text.getLineString(line).length()) : 0;
        editor.jumpToLine(line);
        editor.setSelection(line, column);
        editor.requestFocus();
    }

    private void navigateToDiagnostic(Diagnostic diagnostic) {
        if (diagnosticsBehavior != null) diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        if (diagnostic == null || diagnostic.line <= 0) return;
        String target = diagnostic.fileName;
        if (diagnosticFileMatches(target, filename, activeTab == null ? null : activeTab.path)) {
            pendingDiagnosticJump = null;
            jumpToDiagnosticLocation(diagnostic);
            return;
        }
        EditorTab openTab = null;
        for (EditorTab tab : openTabs) {
            if (diagnosticFileMatches(target, tab.title, tab.path)) {
                openTab = tab;
                break;
            }
        }
        if (openTab != null) {
            pendingDiagnosticJump = diagnostic;
            requestTabSwitch(openTab);
            return;
        }
        ExplorerNode node = explorerAdapter == null ? null : explorerAdapter.findNode(target);
        if (node != null) {
            pendingDiagnosticJump = diagnostic;
            onExplorerFileClick(node);
            return;
        }
        SketchwareUtil.toast("File not found in project: " + target);
    }

    private String humanizeXmlError(org.xml.sax.SAXParseException e) {
        String raw = e.getMessage() == null ? "" : e.getMessage();
        int line = e.getLineNumber();
        String lower = raw.toLowerCase();

        if (lower.contains("must be terminated by the matching end-tag")) {
            String tag = extractQuoted(raw, 0);
            return "Line " + line + ": <" + tag + "> tag is not closed. Add a matching </" + tag + "> tag.";
        }
        if (lower.contains("element type") && lower.contains("must be followed by either attribute specifications")) {
            return "Line " + line + ": Tag is not written correctly — check for a missing space, '>' or '/>' after the tag name.";
        }
        if (lower.contains("the markup in the document following the root element must be well-formed")) {
            return "Line " + line + ": Extra content found after the closing tag of the root element. Remove anything after the last closing tag.";
        }
        if (lower.contains("attribute") && lower.contains("was already specified")) {
            String attr = extractQuoted(raw, 0);
            return "Line " + line + ": Attribute \"" + attr + "\" is duplicated on this tag. Remove the repeated one.";
        }
        if (lower.contains("not allowed to start with the '?' character") || lower.contains("content is not allowed in prolog")) {
            return "Line " + line + ": Unexpected text before the XML declaration. Make sure nothing is written before <?xml ...?>.";
        }
        if (lower.contains("referenced entity") || lower.contains("entityref")) {
            return "Line " + line + ": Invalid character like \"&\" used directly. Use \"&amp;\" instead.";
        }
        if (lower.contains("must be well-formed")) {
            return "Line " + line + ": XML structure is broken here — a tag is likely missing, extra, or not closed properly.";
        }
        return "Line " + line + ": " + raw;
    }

    private String extractQuoted(String text, int occurrence) {
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(text);
        int count = 0;
        while (m.find()) {
            if (count == occurrence) return m.group(1);
            count++;
        }
        return "tag";
    }

    private void setupEditor() {
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        editor.setTextSize(prefs.getInt("act_ts", 14));
        editor.setText(content == null ? "" : content);
        editor.setWordwrap(prefs.getBoolean("act_ww", false));
        editor.getComponent(EditorAutoCompletion.class).setEnabled(prefs.getBoolean("act_ac", true));
        editor.getProps().symbolPairAutoCompletion = prefs.getBoolean("act_acsp", true);
        editor.getComponent(Magnifier.class).setEnabled(true);
        editor.setHighlightCurrentLine(true);
        editor.setLineSpacing(2f, 1.1f);

        editor.getColorScheme().setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND,
                CodeEditorColorSchemes.getSearchHighlightColor(this));

        editorContentListener = new ContentListener() {
            @Override public void beforeReplace(Content content) { }
            @Override public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
                if (isLoadingTab) return;
                scheduleEditorStateRefresh();
                triggerRealtimeDiagnostics();
            }

            @Override
            public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                if (isLoadingTab) return;
                scheduleEditorStateRefresh();
                triggerRealtimeDiagnostics();

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

                checkAndApplySnippets(startLine, startColumn, endLine, endColumn, insertedContent);
            }
        };
        registerEditorContentListener();
    }

    private void setupDiagnosticsPanel() {
        if (binding.bottomSheetDiagnostics != null) {
            diagnosticsBehavior = BottomSheetBehavior.from(binding.bottomSheetDiagnostics);
            diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
            if (binding.btnCloseDiagnostics != null) {
                binding.btnCloseDiagnostics.setOnClickListener(v -> diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN));
            }
        }
        if (binding.rvDiagnostics != null) {
            diagnosticsAdapter = new DiagnosticsAdapter(new ArrayList<>(), this::navigateToDiagnostic);
            binding.rvDiagnostics.setAdapter(diagnosticsAdapter);
        }
        refreshDiagnostics(false);
    }

    private void updateIssueStatus(List<Diagnostic> diagnostics) {
        if (binding == null || binding.tvIssueStatus == null || binding.imgIssueStatus == null) return;
        int errors = 0;
        int others = 0;
        if (diagnostics != null) {
            for (Diagnostic diagnostic : diagnostics) {
                if (diagnostic.severity == Diagnostic.Severity.ERROR) errors++;
                else others++;
            }
        }
        int count = errors + others;
        if (count == 0) {
            binding.tvIssueStatus.setText("No issues");
            binding.tvIssueStatus.setTextColor(0xFF2EA866);
            binding.imgIssueStatus.setImageResource(R.drawable.ic_mtrl_check);
            binding.imgIssueStatus.setColorFilter(0xFF2EA866);
        } else {
            StringBuilder label = new StringBuilder();
            if (errors > 0) label.append(errors).append(errors == 1 ? " error" : " errors");
            if (others > 0) {
                if (label.length() > 0) label.append(", ");
                label.append(others).append(others == 1 ? " warning" : " warnings");
            }
            binding.tvIssueStatus.setText(label);
            int statusColor = ThemeUtils.getColor(this, errors > 0 ? R.attr.colorError : R.attr.colorPrimary);
            binding.tvIssueStatus.setTextColor(statusColor);
            binding.imgIssueStatus.setImageResource(R.drawable.ic_mtrl_warning);
            binding.imgIssueStatus.setColorFilter(statusColor);
        }
        if (binding.issueStatusCard != null) {
            binding.issueStatusCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));
            binding.issueStatusCard.setContentDescription(count == 0 ? "No issues. Open diagnostics." : count + (count == 1 ? " issue. Open diagnostics." : " issues. Open diagnostics."));
        }
    }

    public void showDiagnostics(List<Diagnostic> diagnostics) {
        liveDiagnostics.clear();
        if (diagnostics != null) liveDiagnostics.addAll(diagnostics);
        refreshDiagnostics(true);
    }

    private void setupSearchPanel() {
        binding.btnCloseSearch.setOnClickListener(v -> hideSearchPanel());

        binding.btnPrev.setOnClickListener(v -> moveToSearchMatch(false));
        binding.btnNext.setOnClickListener(v -> moveToSearchMatch(true));

        binding.btnReplace.setOnClickListener(v -> replaceNextSearchMatch());
        binding.btnReplaceAll.setOnClickListener(v -> replaceAllSearchMatches());

        TextWatcher searchWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void afterTextChanged(Editable s) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateFindResults(); }
        };
        binding.findEdit.addTextChangedListener(searchWatcher);
        binding.findEdit.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                moveToSearchMatch(true);
                return true;
            }
            return false;
        });
        binding.searchOptionsGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> updateFindResults());
    }

    private void openSearchPanel() {
        if (binding.searchCard == null) return;
        binding.searchCard.setVisibility(View.VISIBLE);
        String selected = getSelectedSingleLineText();
        if (!selected.isEmpty()) {
            binding.findEdit.setText(selected);
            binding.findEdit.setSelection(selected.length());
        }
        updateFindResults();
        binding.findEdit.requestFocus();
        binding.findEdit.selectAll();
        showKeyboard(binding.findEdit);
    }

    private String getSelectedSingleLineText() {
        try {
            io.github.rosemoe.sora.text.Cursor cursor = editor.getCursor();
            if (!cursor.isSelected()) return "";
            CharSequence selected = editor.getText().subSequence(cursor.getLeft(), cursor.getRight());
            if (selected.length() > 120) return "";
            for (int i = 0; i < selected.length(); i++) if (selected.charAt(i) == '\n') return "";
            return selected.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void showKeyboard(View view) {
        view.post(() -> {
            InputMethodManager manager =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null && view != null) manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void hideSearchPanel() {
        try { editor.getSearcher().stopSearch(); } catch (Exception ignored) { }
        hideKeyboard(binding.findEdit);
        binding.searchCard.setVisibility(View.GONE);
        if (binding.findEdit != null) binding.findEdit.setText("");
        editor.requestFocus();
    }

    private String getSearchExpression() {
        String query = binding.findEdit == null || binding.findEdit.getText() == null ? "" : binding.findEdit.getText().toString();
        if (query.isEmpty()) return "";
        boolean regex = binding.btnRegex != null && binding.btnRegex.isChecked();
        boolean wholeWord = binding.btnWholeWord != null && binding.btnWholeWord.isChecked();
        if (!regex) query = Pattern.quote(query);
        if (wholeWord) query = "\\b(?:" + query + ")\\b";
        return query;
    }

    private Pattern buildSearchPattern() {
        String expression = getSearchExpression();
        int flags = Pattern.MULTILINE;
        if (binding.btnMatchCase == null || !binding.btnMatchCase.isChecked()) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        return Pattern.compile(expression, flags);
    }

    private void runEditorSearch(boolean focusNext) {
        if (binding.findEdit == null || binding.findEdit.getText().length() == 0) return;
        try {
            String rawQuery = binding.findEdit.getText().toString();
            boolean regexMode = binding.btnRegex != null && binding.btnRegex.isChecked();
            boolean wholeWord = binding.btnWholeWord != null && binding.btnWholeWord.isChecked();
            String expression = regexMode || wholeWord ? getSearchExpression() : rawQuery;
            int type = regexMode || wholeWord
                    ? EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION
                    : EditorSearcher.SearchOptions.TYPE_NORMAL;
            boolean ignoreCase = binding.btnMatchCase == null || !binding.btnMatchCase.isChecked();
            editor.getSearcher().search(expression, new EditorSearcher.SearchOptions(type, ignoreCase));
            if (focusNext) editor.getSearcher().gotoNext();
        } catch (Exception e) {
            if (binding.searchCountView != null) binding.searchCountView.setText("Invalid pattern");
        }
    }

    private static final class SearchMatch {
        final int start;
        final int end;

        SearchMatch(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private ArrayList<SearchMatch> findSearchMatches() {
        ArrayList<SearchMatch> matches = new ArrayList<>();
        if (binding.findEdit == null || binding.findEdit.getText() == null || binding.findEdit.getText().length() == 0) return matches;
        String source = editor.getText().toString();
        Matcher matcher = buildSearchPattern().matcher(source);
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            matches.add(new SearchMatch(matcher.start(), matcher.end()));
        }
        return matches;
    }

    private void moveToSearchMatch(boolean forward) {
        if (binding.findEdit == null || binding.findEdit.getText() == null || binding.findEdit.getText().length() == 0) return;
        try {
            runEditorSearch(false);
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                if (binding.searchCountView != null) binding.searchCountView.setText("0 matches");
                return;
            }
            int cursor = forward ? editor.getCursor().getRight() : editor.getCursor().getLeft();
            SearchMatch selected = null;
            if (forward) {
                for (SearchMatch match : matches) {
                    if (match.start >= cursor) { selected = match; break; }
                }
                if (selected == null) selected = matches.get(0);
            } else {
                for (int i = matches.size() - 1; i >= 0; i--) {
                    SearchMatch match = matches.get(i);
                    if (match.start < cursor) { selected = match; break; }
                }
                if (selected == null) selected = matches.get(matches.size() - 1);
            }
            selectSearchMatch(selected);
            int matchIndex = matches.indexOf(selected);
            if (binding.searchCountView != null && matchIndex >= 0) {
                binding.searchCountView.setText((matchIndex + 1) + "/" + matches.size() + " matches");
            }
        } catch (Exception error) {
            if (binding.searchCountView != null) binding.searchCountView.setText("Invalid pattern");
        }
    }

    private void selectSearchMatch(SearchMatch match) {
        if (match == null) return;
        Content text = editor.getText();
        io.github.rosemoe.sora.text.CharPosition start = text.getIndexer().getCharPosition(match.start);
        io.github.rosemoe.sora.text.CharPosition end = text.getIndexer().getCharPosition(match.end);
        editor.setSelectionRegion(start.line, start.column, end.line, end.column, SelectionChangeEvent.CAUSE_SEARCH);
    }

    private String expandSearchReplacement(SearchMatch match, String replacement, String source) {
        if (replacement == null) return "";
        if (binding.btnRegex == null || !binding.btnRegex.isChecked()) return replacement;
        Matcher matcher = buildSearchPattern().matcher(source);
        while (matcher.find()) {
            if (matcher.start() != match.start || matcher.end() != match.end) continue;
            StringBuffer expanded = new StringBuffer();
            matcher.appendReplacement(expanded, replacement);
            return expanded.substring(match.start);
        }
        return replacement;
    }

    private ArrayList<String> expandAllSearchReplacements(List<SearchMatch> matches, String replacement, String source) {
        ArrayList<String> expandedReplacements = new ArrayList<>(matches.size());
        if (binding.btnRegex == null || !binding.btnRegex.isChecked()) {
            for (int i = 0; i < matches.size(); i++) expandedReplacements.add(replacement == null ? "" : replacement);
            return expandedReplacements;
        }
        Matcher matcher = buildSearchPattern().matcher(source);
        StringBuffer output = new StringBuffer();
        int previousMatchEnd = 0;
        int matchIndex = 0;
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            if (matchIndex >= matches.size()) break;
            SearchMatch expected = matches.get(matchIndex);
            if (matcher.start() != expected.start || matcher.end() != expected.end) continue;
            int replacementStart = output.length() + matcher.start() - previousMatchEnd;
            matcher.appendReplacement(output, replacement == null ? "" : replacement);
            expandedReplacements.add(output.substring(replacementStart));
            previousMatchEnd = matcher.end();
            matchIndex++;
        }
        if (expandedReplacements.size() != matches.size()) {
            throw new IllegalStateException("Search results changed before replacement completed.");
        }
        return expandedReplacements;
    }

    private void replaceNextSearchMatch() {
        if (binding.findEdit == null || binding.findEdit.getText() == null || binding.findEdit.getText().length() == 0) return;
        try {
            runEditorSearch(false);
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            int left = editor.getCursor().getLeft();
            int right = editor.getCursor().getRight();
            SearchMatch target = null;
            for (SearchMatch match : matches) {
                if (match.start == left && match.end == right) { target = match; break; }
            }
            if (target == null) {
                for (SearchMatch match : matches) {
                    if (match.start >= right) { target = match; break; }
                }
                if (target == null) target = matches.get(0);
            }
            String sourceSnapshot = editor.getText().toString();
            String replacement = expandSearchReplacement(target, binding.replaceEdit == null ? "" : binding.replaceEdit.getText().toString(), sourceSnapshot);
            Content text = editor.getText();
            boolean wasLoadingTab = isLoadingTab;
            isLoadingTab = true;
            try {
                text.replace(target.start, target.end, replacement);
            } finally {
                isLoadingTab = wasLoadingTab;
            }
            int caret = Math.min(target.start + replacement.length(), text.length());
            io.github.rosemoe.sora.text.CharPosition pos = text.getIndexer().getCharPosition(caret);
            editor.setSelection(pos.line, pos.column);
            updateMenuState();
            updateFindResults();
            if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml")) triggerRealtimeDiagnostics();
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace match: " + error.getMessage());
        }
    }

    private void replaceAllSearchMatches() {
        if (binding.findEdit == null || binding.findEdit.getText() == null || binding.findEdit.getText().length() == 0) return;
        try {
            String sourceSnapshot = editor.getText().toString();
            ArrayList<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            Content text = editor.getText();
            String replacementText = binding.replaceEdit == null ? "" : binding.replaceEdit.getText().toString();
            ArrayList<String> expandedReplacements = expandAllSearchReplacements(matches, replacementText, sourceSnapshot);
            
            boolean wasLoadingTab = isLoadingTab;
            isLoadingTab = true;
            try {
                text.beginBatchEdit();
                try {
                    for (int i = matches.size() - 1; i >= 0; i--) {
                        SearchMatch match = matches.get(i);
                        text.replace(match.start, match.end, expandedReplacements.get(i));
                    }
                } finally {
                    text.endBatchEdit();
                }
            } finally {
                isLoadingTab = wasLoadingTab;
            }
            updateMenuState();
            updateFindResults();
            if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml")) triggerRealtimeDiagnostics();
            SketchwareUtil.toast("Replaced " + matches.size() + (matches.size() == 1 ? " occurrence" : " occurrences"));
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace matches: " + error.getMessage());
        }
    }

    private void updateFindResults() {
        if (binding.findEdit == null || binding.searchCountView == null) return;
        String query = binding.findEdit.getText() == null ? "" : binding.findEdit.getText().toString();
        if (query.isEmpty()) {
            try { editor.getSearcher().stopSearch(); } catch (Exception ignored) { }
            binding.searchCountView.setText("0 matches");
            return;
        }
        try {
            runEditorSearch(false);
            Pattern pattern = buildSearchPattern();
            String text = editor.getText().toString();
            Matcher matcher = pattern.matcher(text);
            int count = 0;
            while (matcher.find()) {
                if (matcher.start() == matcher.end()) continue;
                count++;
            }
            binding.searchCountView.setText(count + (count == 1 ? " match" : " matches"));
        } catch (Exception e) {
            binding.searchCountView.setText("Invalid pattern");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Code Highlighting settings can be changed in a child Activity. Re-apply the
        // shared palette when this editor returns to the foreground.
        if (editor != null && filename != null) applySyntaxHighlighting();
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ProjectBuilder.ACTION_BUILD_DIAGNOSTICS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(buildDiagnosticsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(buildDiagnosticsReceiver, filter);
        }
    }

    @Override
    public void onDestroy() {
        stateHandler.removeCallbacks(stateRefreshRunnable);
        if (selectionSubscription != null) {
            try { selectionSubscription.unsubscribe(); } catch (Throwable ignored) { }
            selectionSubscription = null;
        }
        super.onDestroy();
    }

    @Override
    public void onStop() {
        super.onStop();
        try { unregisterReceiver(buildDiagnosticsReceiver); } catch (Exception ignored){}
        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        prefs.edit().putInt("act_ts", (int) (editor.getTextSizePx() / scaledDensity)).apply();
    }

    private Drawable mutableIcon(int resource) {
        Drawable icon = AppCompatResources.getDrawable(this, resource);
        return icon == null ? null : icon.mutate();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem undoItem = menu.add(Menu.NONE, 0, Menu.NONE, "Undo");
        undoItem.setIcon(mutableIcon(R.drawable.ic_mtrl_undo));
        undoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem redoItem = menu.add(Menu.NONE, 1, Menu.NONE, "Redo");
        redoItem.setIcon(mutableIcon(R.drawable.ic_mtrl_redo));
        redoItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        MenuItem saveItem = menu.add(Menu.NONE, 2, Menu.NONE, "Save");
        saveItem.setIcon(mutableIcon(R.drawable.ic_mtrl_save));
        saveItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        if (canFormatLayoutTools()) {
            MenuItem designItem = menu.add(Menu.NONE, 18, Menu.NONE, "Design");
            designItem.setIcon(mutableIcon(R.drawable.ic_mtrl_design));
            designItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            MenuItem previewItem = menu.add(Menu.NONE, 17, Menu.NONE, "Preview");
            previewItem.setIcon(mutableIcon(R.drawable.ic_mtrl_preview));
            previewItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }

        MenuItem moreItem = menu.add(Menu.NONE, 16, Menu.NONE, "More options");
        moreItem.setIcon(mutableIcon(R.drawable.ic_mtrl_more_vertical));
        moreItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    private Menu optionsMenu;

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        optionsMenu = menu;
        updateMenuState();
        return super.onPrepareOptionsMenu(menu);
    }

    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable stateRefreshRunnable = this::refreshEditorState;
    private boolean stateRefreshPosted;

    private void scheduleEditorStateRefresh() {
        if (stateRefreshPosted) return;
        stateRefreshPosted = true;
        stateHandler.postDelayed(stateRefreshRunnable, 80);
    }

    private void updateMenuState() {
        stateHandler.removeCallbacks(stateRefreshRunnable);
        refreshEditorState();
    }

    private void refreshEditorState() {
        stateRefreshPosted = false;
        if (editor == null) return;
        if (activeTab != null && !isLoadingTab) {
            activeTab.workingContent = editor.getText().toString();
            updateTabTitle(activeTab);
        }
        if (optionsMenu == null) return;
        boolean canUndo = false;
        boolean canRedo = false;
        try { canUndo = editor.canUndo(); } catch (Exception ignored) { }
        try { canRedo = editor.canRedo(); } catch (Exception ignored) { }
        setMenuItemEnabled(optionsMenu.findItem(0), canUndo);
        setMenuItemEnabled(optionsMenu.findItem(1), canRedo);
        setMenuItemEnabled(optionsMenu.findItem(2), isContentModified());
    }

    private void setMenuItemEnabled(@Nullable MenuItem item, boolean enabled) {
        if (item == null) return;
        item.setEnabled(enabled);
        Drawable icon = item.getIcon();
        if (icon != null) icon.setAlpha(enabled ? 255 : 97);
    }

    private void performUndo() {
        try { if (editor.canUndo()) editor.undo(); } catch (Exception ignored) { }
        updateMenuState();
        scheduleEditorStateRefresh();
    }

    private void performRedo() {
        try { if (editor.canRedo()) editor.redo(); } catch (Exception ignored) { }
        updateMenuState();
        scheduleEditorStateRefresh();
    }

    private boolean canFormatLayoutTools() {
        return activeTab != null && activeTab.databaseLayout
                && filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xml");
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> { performUndo(); return true; }
            case 1 -> { performRedo(); return true; }
            case 2 -> {
                if (isContentModified()) saveCurrentTab();
                else SketchwareUtil.toast("No changes to save");
                return true;
            }
            case 17 -> { dispatchOption(6); return true; }
            case 18 -> { dispatchOption(16); return true; }
            case 16 -> { showMoreOptionsSheet(); return true; }
            default -> { return dispatchOption(item.getItemId()) || super.onOptionsItemSelected(item); }
        }
    }

    private boolean dispatchOption(int optionId) {
        switch (optionId) {
            case 12 -> {
                openProjectExplorer();
                return true;
            }
            case 4 -> {
                openSearchPanel();
                return true;
            }
            case 5 -> {
                formatCurrentFile();
                return true;
            }
            case 6 -> {
                if (activeTab == null || !activeTab.databaseLayout) return true;
                Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra("xml", editor.getText().toString());
                startActivity(intent);
                return true;
            }
            case 16 -> {
                if (!canFormatLayoutTools()) return true;
                if (isContentModified()) saveCurrentTab();
                Intent intent = new Intent(this, DesignActivity.class);
                intent.putExtra("sc_id", sc_id);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            }
            case 7 -> { showLanguageSelector(); return true; }
            case 13 -> { if (activeTab != null && activeTab.databaseLayout) showGenerateLayoutWithAiDialog(); return true; }
            case 14 -> { showEditorSettingsDialog(); return true; }
            case 15 -> {
                String help = "Project: " + projectName + "\nProject ID: " + sc_id + "\nFile: " + filename
                        + "\n\nJava snippets\n"
                        + "toast, logd, loge, intent, fori, psfs, if, ifelse, tryc, find, et, tvf, btnf, ivf, cbf, rbf, swf, spf, rvf, lvf, gvf, cvf, pbf, fabf, onclick, thread, runui, handler, finish, finisha, back, random, array, hashmap, dialog, date, time, switch, while, dowhile, foreach, return, newobj, bundle, putextra, getextra, shared, editor, commit, apply, prefput, prefget, anim, post, visible, gone, invisible, enable, disable, text, gettext, hint, focus, clear, img, bg, color, parse, str, equals, isempty, len, trim, upper, lower, contains, replace, split, sleep, exit"
                        + "\n\nXML snippets\n"
                        + "ll, rl, tv, btn, iv, cl, sv, hsv, et, cb, rb, rg, sw, pb, seek, rating, spinner, lv, gv, rv, card, space, view, include, merge, frag"
                        + "\n\nType a shortcut and press Space or Enter to insert its code. The | marker shows the cursor position.";
                android.widget.TextView message = new android.widget.TextView(this);
                message.setText(help);
                message.setTextSize(14);
                message.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
                int padding = (int) (20 * getResources().getDisplayMetrics().density);
                message.setPadding(padding, padding, padding, padding);
                android.widget.ScrollView scroll = new android.widget.ScrollView(this);
                scroll.addView(message);
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Editor Help")
                        .setView(scroll)
                        .setPositiveButton("Close", null)
                        .show();
                return true;
            }
            default -> { return false; }
        }
    }

    private void formatCurrentFile() {
        if (filename == null) {
            SketchwareUtil.toast("Formatting is available for Java and XML files.");
            return;
        }
        String lowerName = filename.toLowerCase(Locale.ROOT);
        String original = editor.getText().toString();
        String formatted;
        String successMessage;
        if (lowerName.endsWith(".java")) {
            try {
                formatted = Lx.j(original, true);
            } catch (Exception e) {
                SketchwareUtil.toastError("Could not format code. Check the syntax first.");
                return;
            }
            successMessage = "Code formatted";
        } else if (lowerName.endsWith(".xml")) {
            formatted = SrcCodeEditor.prettifyXml(original, 4, getIntent());
            if (formatted == null) {
                SketchwareUtil.toastError("Could not format XML. Check the syntax first.");
                return;
            }
            successMessage = "XML formatted";
        } else {
            SketchwareUtil.toast("Formatting is available for Java and XML files.");
            return;
        }
        if (formatted.equals(original)) {
            SketchwareUtil.toast("Already formatted");
            return;
        }
        replaceEditorContentKeepingHistory(formatted);
        SketchwareUtil.toast(successMessage);
    }

    private void replaceEditorContentKeepingHistory(String newContent) {
        Content text = editor.getText();
        int cursorLine = editor.getCursor().getLeftLine();
        int cursorColumn = editor.getCursor().getLeftColumn();
        isLoadingTab = true;
        try {
            text.beginBatchEdit();
            try {
                text.replace(0, text.length(), newContent);
            } finally {
                text.endBatchEdit();
            }
        } finally {
            isLoadingTab = false;
        }
        int line = Math.max(0, Math.min(cursorLine, text.getLineCount() - 1));
        int column = Math.max(0, Math.min(cursorColumn, text.getLineString(line).length()));
        editor.setSelection(line, column);
        updateMenuState();
        triggerRealtimeDiagnostics();
        updateFindResults();
    }

    private void showMoreOptionsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        ViewCodeEditorOptionsBinding sheet = ViewCodeEditorOptionsBinding.inflate(getLayoutInflater());
        int layoutToolsVisibility = canFormatLayoutTools() ? View.VISIBLE : View.GONE;
        sheet.optExplorer.setVisibility(View.GONE);
        sheet.optPreview.setVisibility(View.GONE);
        sheet.optQuickLayout.setVisibility(layoutToolsVisibility);
        compactOptionGrid(sheet.getRoot());
        bindOptionTile(dialog, sheet.optExplorer, 12);
        bindOptionTile(dialog, sheet.optFind, 4);
        bindOptionTile(dialog, sheet.optFormat, 5);
        bindOptionTile(dialog, sheet.optPreview, 6);
        bindOptionTile(dialog, sheet.optQuickLayout, 13);
        bindOptionTile(dialog, sheet.optLanguage, 7);
        bindOptionTile(dialog, sheet.optSettings, 14);
        bindOptionTile(dialog, sheet.optAbout, 15);
        sheet.switchWordWrap.setChecked(prefs.getBoolean("act_ww", false));
        sheet.switchWordWrap.setOnCheckedChangeListener((button, checked) -> {
            editor.setWordwrap(checked);
            prefs.edit().putBoolean("act_ww", checked).apply();
        });
        sheet.switchAutoComplete.setChecked(prefs.getBoolean("act_ac", true));
        sheet.switchAutoComplete.setOnCheckedChangeListener((button, checked) -> {
            editor.getComponent(EditorAutoCompletion.class).setEnabled(checked);
            prefs.edit().putBoolean("act_ac", checked).apply();
        });
        sheet.switchSymbolPair.setChecked(prefs.getBoolean("act_acsp", true));
        sheet.switchSymbolPair.setOnCheckedChangeListener((button, checked) -> {
            editor.getProps().symbolPairAutoCompletion = checked;
            prefs.edit().putBoolean("act_acsp", checked).apply();
        });
        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    private void compactOptionGrid(View sheetRoot) {
        GridLayout grid = sheetRoot.findViewById(R.id.options_grid);
        if (grid == null) return;
        for (int index = grid.getChildCount() - 1; index >= 0; index--) {
            View tile = grid.getChildAt(index);
            if (tile.getVisibility() != View.VISIBLE) grid.removeViewAt(index);
        }
        int visibleCount = grid.getChildCount();
        grid.setColumnCount(Math.max(1, Math.min(3, visibleCount)));
    }

    private void bindOptionTile(BottomSheetDialog dialog, View tile, int optionId) {
        tile.setOnClickListener(v -> {
            dialog.dismiss();
            dispatchOption(optionId);
        });
    }

    private void showEditorSettingsDialog() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24f), dp(8f), dp(24f), dp(8f));
        TextView label = new TextView(this);
        int initialSize = prefs.getInt("act_ts", 14);
        label.setText("Font size: " + initialSize + " sp");
        label.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(label);
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(14);
        sizeSeek.setProgress(Math.max(0, Math.min(14, initialSize - 10)));
        root.addView(sizeSeek, new LinearLayout.LayoutParams(-1, dp(44f)));
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                label.setText("Font size: " + (progress + 10) + " sp");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        new MaterialAlertDialogBuilder(this)
                .setTitle("Editor Settings")
                .setView(root)
                .setNeutralButton("Syntax highlighting & colors", (dialog, which) ->
                        startActivity(new Intent(this, CodeHighlightingSettingsActivity.class)))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Apply", (dialog, which) -> {
                    int size = sizeSeek.getProgress() + 10;
                    prefs.edit().putInt("act_ts", size).apply();
                    editor.setTextSize(size);
                })
                .show();
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
            if (binding.noteCard != null) binding.noteCard.setVisibility(View.VISIBLE);
            if (binding.note != null) binding.note.setText(note);
        } else {
            if (binding.noteCard != null) binding.noteCard.setVisibility(View.GONE);
        }
    }

    private void showGenerateLayoutWithAiDialog() {
        int dp24 = dp(24f);
        int dp16 = dp(16f);
        int dp8 = dp(8f);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp24, dp24, dp24, dp8);

        TextView title = new TextView(this);
        title.setText("Generate Layout with AI");
        title.setTextSize(20);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Describe what this layout should look like or how it should change. AI will rewrite the XML.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.setMargins(0, dp8, 0, dp16);
        subtitle.setLayoutParams(subParams);
        root.addView(subtitle);

        MaterialCardView card = new MaterialCardView(this);
        card.setCardElevation(0);
        card.setRadius(dp8);
        card.setStrokeWidth(dp(1f));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));

        EditText promptInput = new EditText(this);
        promptInput.setHint("e.g. add a button below the title that says Submit");
        promptInput.setBackground(null);
        promptInput.setPadding(dp16, dp16, dp16, dp16);
        promptInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        promptInput.setMinLines(4);
        promptInput.setMaxLines(10);
        promptInput.setGravity(Gravity.TOP | Gravity.START);
        promptInput.setVerticalScrollBarEnabled(true);
        promptInput.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        promptInput.setHintTextColor(ThemeUtils.getColor(this, R.attr.colorOutline));

        card.addView(promptInput, new ViewGroup.LayoutParams(-1, -2));
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        new MaterialAlertDialogBuilder(this)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Generate", (dialog, which) -> {
                    String prompt = promptInput.getText() == null ? "" : promptInput.getText().toString().trim();
                    if (prompt.isEmpty()) {
                        SketchwareUtil.toastError("Describe the layout change first.");
                        return;
                    }
                    runAiLayoutGeneration(prompt);
                })
                .show();
    }

    private void runAiLayoutGeneration(String prompt) {
        if (activeTab == null || !activeTab.databaseLayout) {
            SketchwareUtil.toastError("AI layout generation is only available for Sketchware layout files.");
            return;
        }
        k();
        final EditorTab targetTab = activeTab;
        final String targetXmlName = targetTab.title;
        String currentXml = editor.getText().toString();

        LayoutGenTask.generate(this, currentXml, prompt, new AiResponseCallback() {
            @Override
            public void onSuccess(String generatedXml) {
                boolean wasActive = activeTab == targetTab;
                String previousXml = targetTab.workingContent;
                boolean saved = applyXmlChanges(targetXmlName, generatedXml);
                if (saved) {
                    targetTab.savedContent = generatedXml;
                    targetTab.workingContent = generatedXml;
                    if (wasActive) {
                        isLoadingTab = true;
                        editor.setText(generatedXml);
                        registerEditorContentListener();
                        isLoadingTab = false;
                        beforeContent = generatedXml;
                        content = generatedXml;
                        updateMenuState();
                    }
                    Intent result = new Intent();
                    result.putExtra("saved_file", targetXmlName);
                    result.putExtra("sc_id", sc_id);
                    setResult(RESULT_OK, result);
                    invalidateOptionsMenu();
                    updateTabTitle(targetTab);
                    SketchwareUtil.toast("Layout updated by AI");
                } else if (wasActive && previousXml != null) {
                    isLoadingTab = true;
                    editor.setText(previousXml);
                    registerEditorContentListener();
                    isLoadingTab = false;
                }
                h();
            }

            @Override
            public void onFailure(String errorMessage) {
                h();
                SketchwareUtil.toastError("AI generation failed: " + errorMessage);
            }
        });
    }

    private boolean saveCurrentTab() {
        if (activeTab == null || editor == null) return false;
        boolean saved = saveTab(activeTab, editor.getText().toString());
        if (saved) {
            beforeContent = activeTab.savedContent;
            content = activeTab.savedContent;
            invalidateOptionsMenu();
            updateMenuState();
            SketchwareUtil.toast(activeTab.databaseLayout ? "Saved XML" : "Saved " + activeTab.title);
        }
        return saved;
    }

    private boolean saveTab(EditorTab tab, String text) {
        if (tab == null) return false;
        String value = text == null ? "" : text;
        if (Objects.equals(tab.savedContent, value)) return true;
        if (tab.databaseLayout) {
            if (!applyXmlChanges(tab.title, value)) return false;
        } else {
            if (TextUtils.isEmpty(tab.path)) {
                SketchwareUtil.toastError("This tab does not have a writable file path.");
                return false;
            }
            File destination = new File(tab.path);
            File parent = destination.getParentFile();
            try {
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    SketchwareUtil.toastError("Could not create the file directory.");
                    return false;
                }
                try (FileOutputStream output = new FileOutputStream(destination, false)) {
                    output.write(value.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                }
                String persisted = FileUtil.readFile(destination.getAbsolutePath());
                if (!Objects.equals(value, persisted)) {
                    SketchwareUtil.toastError("File save could not be verified.");
                    return false;
                }
            } catch (IOException e) {
                SketchwareUtil.toastError("Could not save file: " + e.getMessage());
                return false;
            }
            isEdited = true;
        }
        tab.savedContent = value;
        tab.workingContent = value;
        if (tab == activeTab) {
            beforeContent = value;
            content = value;
        }
        Intent result = new Intent();
        result.putExtra("saved_file", tab.title);
        result.putExtra("sc_id", sc_id);
        setResult(RESULT_OK, result);
        updateTabTitle(tab);
        return true;
    }

    private boolean isTabModified(EditorTab tab) {
        if (tab == null) return false;
        String current = tab == activeTab && editor != null ? editor.getText().toString() : tab.workingContent;
        return !Objects.equals(tab.savedContent, current);
    }

    private boolean hasUnsavedTabs() {
        for (EditorTab tab : openTabs) if (isTabModified(tab)) return true;
        return false;
    }

    private boolean saveAllTabs() {
        for (EditorTab tab : new ArrayList<>(openTabs)) {
            String value = tab == activeTab && editor != null ? editor.getText().toString() : tab.workingContent;
            if (!Objects.equals(tab.savedContent, value) && !saveTab(tab, value)) return false;
        }
        return true;
    }

    private void finishWithResultIfNeeded() {
        finish();
    }

    private boolean applyXmlChanges() {
        return activeTab != null && activeTab.databaseLayout
                && applyXmlChanges(activeTab.title, editor.getText().toString());
    }

    private boolean applyXmlChanges(String targetFilename, String xmlToParse) {
        try {
            ArrayList<ViewBean> oldLayout = jC.a(sc_id).d(targetFilename);
            
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
            
            if (activeTab == null || targetFilename.equals(filename)) content = xmlToParse;
            if (!isEdited) isEdited = true;
            
            var root = parser.getRootAttributes();
            rootLayoutManager.set(targetFilename, InjectRootLayoutManager.toRoot(root));
            
            HistoryViewBean bean = new HistoryViewBean();
            bean.actionOverride(parsedLayout, oldLayout);
            
            var cc = cC.c(sc_id);
            if (!cc.c.containsKey(targetFilename)) cc.e(targetFilename);
            
            cc.a(targetFilename);
            cc.a(targetFilename, bean);
            
            jC.a(sc_id).c.put(targetFilename, parsedLayout);
            return true;
            
        } catch (Exception e) {
            String friendlyMsg;
            int line = 1;
            int column = 0;
            if (e instanceof org.xml.sax.SAXParseException) {
                org.xml.sax.SAXParseException sax = (org.xml.sax.SAXParseException) e;
                line = sax.getLineNumber();
                column = sax.getColumnNumber();
                friendlyMsg = humanizeXmlError(sax);
            } else {
                friendlyMsg = e.getMessage();
            }
            SketchwareUtil.toastError("XML Syntax Error: " + friendlyMsg);
            List<Diagnostic> errors = new ArrayList<>();
            errors.add(new Diagnostic(Diagnostic.Severity.ERROR, targetFilename, line, column, String.valueOf(friendlyMsg).replaceFirst("^Line \\d+: ", "")));
            showDiagnostics(errors);
            return false;
        }
    }

    private boolean isContentModified() {
        if (editor == null) return false;
        if (activeTab != null) return isTabModified(activeTab);
        return !Objects.equals(beforeContent, editor.getText().toString());
    }
}
