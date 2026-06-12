package com.besome.sketch.common;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.besome.sketch.beans.SrcCodeBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;

import a.a.a.ProjectBuilder;
import a.a.a.bB;
import a.a.a.jC;
import a.a.a.yq;
import mod.hey.studios.util.Helper;
import pro.sketchware.R;
import pro.sketchware.databinding.SrcViewerBinding;
import pro.sketchware.utility.EditorUtils;

public class SrcViewerActivity extends BaseAppCompatActivity {

    private SrcViewerBinding binding;
    private String sc_id;
    private ArrayList<SrcCodeBean> sourceCodeBeans;

    private String currentFileName;
    private int editorFontSize = 14; // Default to 14 for better readability

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = SrcViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        currentFileName = getIntent().hasExtra("current") ? getIntent().getStringExtra("current") : "";
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id") : getIntent().getStringExtra("sc_id");

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBarLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), systemBars.top, v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });

        ViewCompat.setOnApplyWindowInsetsListener(binding.editor, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
        setupToolbarMenu();

        configureEditor();

        k(); // show loading dialog

        new Thread(() -> {
            var yq = new yq(getBaseContext(), sc_id);
            var fileManager = jC.b(sc_id);
            var dataManager = jC.a(sc_id);
            var libraryManager = jC.c(sc_id);
            yq.a(libraryManager, fileManager, dataManager, a.a.a.yq.ExportType.SOURCE_CODE_VIEWING);
            ProjectBuilder builder = new ProjectBuilder(this, yq);
            builder.buildBuiltInLibraryInformation();
            sourceCodeBeans = yq.a(fileManager, dataManager, builder.getBuiltInLibraryManager());

            try {
                runOnUiThread(() -> {
                    if (sourceCodeBeans == null || sourceCodeBeans.isEmpty()) {
                        bB.b(getApplicationContext(), Helper.getResString(R.string.common_error_unknown), bB.TOAST_NORMAL).show();
                        finish();
                    } else {
                        // Find current file or default to first
                        boolean fileFound = false;
                        for (SrcCodeBean src : sourceCodeBeans) {
                            if (src.srcFileName.equals(currentFileName)) {
                                fileFound = true;
                                break;
                            }
                        }
                        if (!fileFound) {
                            currentFileName = sourceCodeBeans.get(0).srcFileName;
                        }
                        
                        loadSelectedFile(currentFileName);
                        h(); // hide loading
                    }
                });
            } catch (Exception ignored) {
                // May occur if the activity is killed
            }
        }).start();
    }

    private void setupToolbarMenu() {
        Menu menu = binding.toolbar.getMenu();
        menu.clear();
        
        menu.add(Menu.NONE, 1, Menu.NONE, "Search File")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_search_white_24dp))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                
        menu.add(Menu.NONE, 2, Menu.NONE, "Change Font Size")
                .setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_mtrl_formattext))
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);

        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                if (sourceCodeBeans != null) {
                    showFileSearchDialog();
                }
                return true;
            } else if (item.getItemId() == 2) {
                showChangeFontSizeDialog();
                return true;
            }
            return false;
        });
    }

    private void configureEditor() {
        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setEditable(false);
        binding.editor.setTextSize(editorFontSize);
        binding.editor.setPinLineNumber(true);
        binding.editor.setLineSpacing(2f, 1.1f);
    }

    private void loadSelectedFile(String fileName) {
        for (SrcCodeBean bean : sourceCodeBeans) {
            if (bean.srcFileName.equals(fileName)) {
                binding.editor.setText(bean.source);
                currentFileName = bean.srcFileName;
                binding.toolbar.setSubtitle(currentFileName);
                
                // Switch Language highlighting
                if (currentFileName.endsWith(".xml")) {
                    EditorUtils.loadXmlConfig(binding.editor);
                } else if (currentFileName.endsWith(".java")) {
                    EditorUtils.loadJavaConfig(binding.editor);
                } else if (currentFileName.endsWith(".kt")) {
                     // Adding Kotlin highlighting support just in case
                     EditorUtils.loadJavaConfig(binding.editor); 
                }
                break;
            }
        }
    }

    private void showFileSearchDialog() {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("Search File");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 24, 32, 24);

        TextInputLayout til = new TextInputLayout(this);
        til.setHint("Type file name (e.g. MainActivity.java)");
        TextInputEditText searchInput = new TextInputEditText(til.getContext());
        searchInput.setSingleLine(true);
        til.addView(searchInput);
        layout.addView(til);

        ListView listView = new ListView(this);
        layout.addView(listView);

        // Prepare lists
        ArrayList<String> allFileNames = new ArrayList<>();
        for (SrcCodeBean bean : sourceCodeBeans) {
            allFileNames.add(bean.srcFileName);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, allFileNames);
        listView.setAdapter(adapter);

        builder.setView(layout);
        builder.setNegativeButton(android.R.string.cancel, null);
        
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        // Search Filter Logic
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Click to load
        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selectedFileName = adapter.getItem(position);
            loadSelectedFile(selectedFileName);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showChangeFontSizeDialog() {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(8);
        picker.setMaxValue(30);
        picker.setWrapSelectorWheel(false);
        picker.setValue(editorFontSize);

        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(0, 32, 0, 0);
        layout.addView(picker);

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select font size")
                .setIcon(R.drawable.ic_mtrl_formattext)
                .setView(layout)
                .setPositiveButton("Apply", (dialog, which) -> {
                    editorFontSize = picker.getValue();
                    binding.editor.setTextSize(editorFontSize);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }
}
