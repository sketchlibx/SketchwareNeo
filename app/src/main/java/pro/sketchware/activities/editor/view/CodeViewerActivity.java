package pro.sketchware.activities.editor.view;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import a.a.a.Lx;
import mod.hey.studios.util.Helper;
import pro.sketchware.databinding.ActivityCodeViewerBinding;
import pro.sketchware.utility.EditorUtils;
import mod.hey.studios.code.SrcCodeEditor;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.utility.UI;

public class CodeViewerActivity extends BaseAppCompatActivity {

    public static final String SCHEME_XML = "xml";
    public static final String SCHEME_JAVA = "java";

    private ActivityCodeViewerBinding binding;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityCodeViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        var code = getIntent().getStringExtra("code");
        var scheme = getIntent().getStringExtra("scheme");
        var scId = getIntent().getStringExtra("sc_id");

        binding.toolbar.setNavigationOnClickListener(Helper.getBackPressedClickListener(this));
        binding.toolbar.setSubtitle(scId);

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));
        binding.editor.setTextSize(14);
        binding.editor.setEditable(false);
        binding.editor.setWordwrap(false);
        loadColorScheme(scheme);
        binding.editor.setText(Lx.j(code == null ? "" : code, false));

        UI.addSystemWindowInsetToPadding(binding.appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) loadColorScheme(getIntent().getStringExtra("scheme"));
    }

    private void loadColorScheme(String scheme) {
        String fileName = SCHEME_XML.equals(scheme) ? "Preview.xml" : "Preview.java";
        SrcCodeEditor.applyLanguageSpec(this, binding.editor, CodeEditorLanguages.resolveLanguageSpec(fileName));
        binding.editor.setHighlightCurrentLine(true);
    }
}