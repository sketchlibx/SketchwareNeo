package neo.sketchware.activities.editor.view;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;

import com.besome.sketch.beans.HistoryViewBean;
import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectLibraryBean;
import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import a.a.a.cC;
import a.a.a.jC;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.hey.studios.util.Helper;
import neo.sketchware.R;
import neo.sketchware.activities.appcompat.ManageAppCompatActivity;
import neo.sketchware.activities.preview.LayoutPreviewActivity;
import neo.sketchware.databinding.ViewCodeEditorBinding;
import neo.sketchware.managers.inject.InjectRootLayoutManager;
import neo.sketchware.tools.ViewBeanParser;
import neo.sketchware.utility.EditorUtils;
import neo.sketchware.utility.SketchwareUtil;
import neo.sketchware.utility.ThemeUtils;
import neo.sketchware.utility.relativelayout.CircularDependencyDetector;

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
    private ImageView prevBtn;
    private ImageView nextBtn;
    private ImageView replaceBtn;
    private ImageView replaceAllBtn;
    private EditText findEdit;
    private EditText replaceEdit;

    private final OnBackPressedCallback onBackPressedCallback =
            new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
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
                            if (applyXmlChanges()) {
                                v.dismiss();
                                finish();
                            }
                        });

                        dialog.setNegativeButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                            v.dismiss();
                            finish();
                        });
                        
                        dialog.setNeutralButton(Helper.getResString(R.string.common_word_cancel), null);
                        dialog.show();
                    } else {
                        if (isEdited) {
                            setResult(RESULT_OK);
                            finish();
                            return;
                        }
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
        
        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }
        
        rootLayoutManager = new InjectRootLayoutManager(sc_id);
        filename = getIntent().getStringExtra("title");
        projectFile = jC.b(sc_id).b(filename);
        projectLibrary = jC.c(sc_id).c();
        
        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setTitle("XML Editor");
        getSupportActionBar().setSubtitle(filename);
        
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (onBackPressedCallback.isEnabled()) {
                onBackPressedCallback.handleOnBackPressed();
            }
        });
        
        content = getIntent().getStringExtra("content");
        editor = binding.editor;
        
        setupEditor();
        setupSearchPanel();
        
        if (projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary.isEnabled()) {
            setNote("Use AppCompat Manager to modify attributes for CoordinatorLayout, Toolbar, and other appcompat layout/widget.");
        }
        
        binding.close.setOnClickListener(v -> {
            prefs.edit().putInt("note_" + sc_id, 1).apply();
            setNote(null);
        });
        
        binding.noteCard.setOnClickListener(v -> toAppCompat());
    }

    private void setupEditor() {
        editor.setTypefaceText(EditorUtils.getTypeface(this));
        
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        int textSize = prefs.getInt("act_ts", 14);
        editor.setTextSize(textSize);
        editor.setText(content);
        
        boolean wordWrap = prefs.getBoolean("act_ww", false);
        boolean autoComp = prefs.getBoolean("act_ac", true);
        boolean autoSymb = prefs.getBoolean("act_acsp", true);
        
        editor.setWordwrap(wordWrap);
        editor.getComponent(EditorAutoCompletion.class).setEnabled(autoComp);
        editor.getProps().symbolPairAutoCompletion = autoSymb;
        editor.getComponent(Magnifier.class).setEnabled(true);
        editor.setHighlightCurrentLine(true);
        editor.setLineSpacing(2f, 1.1f);
        
        EditorUtils.loadXmlConfig(editor);
        
        editor.getColorScheme().setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, 0x66FFEB3B);
        
        editor.getText().addContentListener(new ContentListener() {
            @Override
            public void beforeReplace(Content content) { }

            @Override
            public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                if (insertedContent != null && insertedContent.toString().equals(">")) {
                    try {
                        String lineText = content.getLineString(endLine);
                        int tagStartIndex = lineText.lastIndexOf('<', endColumn - 1);
                        if (tagStartIndex != -1) {
                            String tagStr = lineText.substring(tagStartIndex + 1, endColumn - 1);
                            if (!tagStr.startsWith("/") && !tagStr.endsWith("/") && !tagStr.contains(" ") && tagStr.matches("[a-zA-Z0-9_.]+")) {
                                String closeTag = "</" + tagStr + ">";
                                editor.post(() -> {
                                    try {
                                        editor.getText().insert(endLine, endColumn, closeTag);
                                    } catch (Exception ignored) {}
                                });
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            @Override
            public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) { }
        });
    }

    private void setupSearchPanel() {
        searchCard = new MaterialCardView(this);
        searchCard.setCardElevation(16f);
        searchCard.setRadius(24f);
        searchCard.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceVariant));
        searchCard.setVisibility(View.GONE);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
        prevBtn.setOnClickListener(v -> {
            if (findEdit.getText().length() > 0) {
                try { editor.getSearcher().gotoPrevious(); } catch (Exception ignored) {}
            }
        });
        
        nextBtn = new ImageView(this);
        nextBtn.setImageResource(R.drawable.ic_mtrl_arrow_down);
        nextBtn.setColorFilter(iconColor);
        nextBtn.setPadding(16, 16, 16, 16);
        nextBtn.setOnClickListener(v -> {
            if (findEdit.getText().length() > 0) {
                try { editor.getSearcher().gotoNext(); } catch (Exception ignored) {}
            }
        });
        
        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(R.drawable.ic_mtrl_close);
        closeBtn.setColorFilter(iconColor);
        closeBtn.setPadding(16, 16, 16, 16);
        closeBtn.setOnClickListener(v -> {
            try { editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
            searchCard.setVisibility(View.GONE);
            findEdit.setText("");
        });
        
        findRow.addView(findEdit);
        findRow.addView(prevBtn);
        findRow.addView(nextBtn);
        findRow.addView(closeBtn);
        
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
        replaceBtn.setOnClickListener(v -> {
    if (findEdit.getText().length() > 0) {
        try { editor.getSearcher().replaceThis(replaceEdit.getText().toString()); } catch (Exception ignored) {}
    }
});
        
        replaceAllBtn = new ImageView(this);
        replaceAllBtn.setImageResource(R.drawable.ic_done_all_white_24dp);
        replaceAllBtn.setColorFilter(iconColor);
        replaceAllBtn.setPadding(16, 16, 16, 16);
        replaceAllBtn.setOnClickListener(v -> {
            if (findEdit.getText().length() > 0) {
                try { editor.getSearcher().replaceAll(replaceEdit.getText().toString()); } catch (Exception ignored) {}
            }
        });
        
        replaceRow.addView(replaceEdit);
        replaceRow.addView(replaceBtn);
        replaceRow.addView(replaceAllBtn);
        
        searchPanel.addView(findRow);
        searchPanel.addView(replaceRow);
        
        searchCard.addView(searchPanel);
        
        ViewGroup rootView = (ViewGroup) editor.getParent();
        if (rootView != null) {
            rootView.addView(searchCard, 1);
        }
        
        updateSearchButtonsState(false);
        
        findEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean hasText = s != null && s.length() > 0;
                updateSearchButtonsState(hasText);
                
                if (hasText) {
                    try {
                        editor.getSearcher().search(s.toString(), 
                            new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true));
                    } catch (Exception ignored) {}
                } else {
                    try { editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void updateSearchButtonsState(boolean enabled) {
        float alpha = enabled ? 1.0f : 0.4f;
        prevBtn.setAlpha(alpha);
        nextBtn.setAlpha(alpha);
        replaceBtn.setAlpha(alpha);
        replaceAllBtn.setAlpha(alpha);
        
        prevBtn.setEnabled(enabled);
        nextBtn.setEnabled(enabled);
        replaceBtn.setEnabled(enabled);
        replaceAllBtn.setEnabled(enabled);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onStop() {
        super.onStop();
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        prefs.edit().putInt("act_ts", (int) (editor.getTextSizePx() / scaledDensity)).apply();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, 0, Menu.NONE, "Undo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_undo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 1, Menu.NONE, "Redo").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_redo)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, 2, Menu.NONE, "Save").setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_save)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        
        if (projectFile.fileType == ProjectFileBean.PROJECT_FILE_TYPE_ACTIVITY && projectLibrary.isEnabled()) {
            menu.add(Menu.NONE, 3, Menu.NONE, "Edit AppCompat").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        
        menu.add(Menu.NONE, 4, Menu.NONE, "Find & Replace").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 5, Menu.NONE, "Layout Preview").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 6, Menu.NONE, "Word wrap").setCheckable(true).setChecked(prefs.getBoolean("act_ww", false)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 7, Menu.NONE, "Auto complete").setCheckable(true).setChecked(prefs.getBoolean("act_ac", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 8, Menu.NONE, "Auto complete symbol pair").setCheckable(true).setChecked(prefs.getBoolean("act_acsp", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, 9, Menu.NONE, "Reload syntax highlighting").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> {
                editor.undo();
                return true;
            }
            case 1 -> {
                editor.redo();
                return true;
            }
            case 2 -> {
                if (isContentModified()) {
                    if (applyXmlChanges()) {
                        SketchwareUtil.toast("Saved");
                    }
                } else {
                    SketchwareUtil.toast("No changes to save");
                }
                return true;
            }
            case 3 -> {
                toAppCompat();
                return true;
            }
            case 4 -> {
                searchCard.setVisibility(View.VISIBLE);
                return true;
            }
            case 5 -> {
                toLayoutPreview();
                return true;
            }
            case 6 -> {
                item.setChecked(!item.isChecked());
                editor.setWordwrap(item.isChecked());
                prefs.edit().putBoolean("act_ww", item.isChecked()).apply();
                return true;
            }
            case 7 -> {
                item.setChecked(!item.isChecked());
                editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                prefs.edit().putBoolean("act_ac", item.isChecked()).apply();
                return true;
            }
            case 8 -> {
                item.setChecked(!item.isChecked());
                editor.getProps().symbolPairAutoCompletion = item.isChecked();
                prefs.edit().putBoolean("act_acsp", item.isChecked()).apply();
                return true;
            }
            case 9 -> {
                EditorUtils.loadXmlConfig(editor);
                return true;
            }
            default -> {
                return super.onOptionsItemSelected(item);
            }
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
            binding.noteCard.setVisibility(View.VISIBLE);
            binding.note.setText(note);
            binding.note.setSelected(true);
        } else {
            binding.noteCard.setVisibility(View.GONE);
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
            if (!isEdited) {
                isEdited = true;
            }

            var root = parser.getRootAttributes();
            rootLayoutManager.set(filename, InjectRootLayoutManager.toRoot(root));

            HistoryViewBean bean = new HistoryViewBean();
            bean.actionOverride(parsedLayout, oldLayout);

            var cc = cC.c(sc_id);
            if (!cc.c.containsKey(filename)) {
                cc.e(filename);
            }

            cc.a(filename);
            cc.a(filename, bean);

            jC.a(sc_id).c.put(filename, parsedLayout);
            setResult(RESULT_OK);
            return true;

        } catch (Exception e) {
            SketchwareUtil.toastError("XML Syntax Error: " + e.getMessage());
            return false;
        }
    }

    private boolean isContentModified() {
        return !content.equals(editor.getText().toString());
    }
}
