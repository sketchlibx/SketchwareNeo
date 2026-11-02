package mod.hey.studios.code.settings;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import io.github.rosemoe.sora.widget.CodeEditor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import mod.hey.studios.code.SrcCodeEditor;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class CodeHighlightingSettingsActivity extends BaseAppCompatActivity {
    private final Map<String, TextView> colorValues = new LinkedHashMap<>();
    private final Map<String, View> colorSwatches = new LinkedHashMap<>();
    private final ArrayList<ProfileCard> profileCards = new ArrayList<>();
    private LinearLayout content;
    private CodeEditor previewEditor;
    private TextView previewJavaTab;
    private TextView previewXmlTab;
    private String previewFileName = "MainActivity.java";
    private TextView appearanceSummary;
    private LinearLayout appearanceChoices;
    private View applyButton;

    private static final String SAMPLE_CODE =
            "public class MainActivity extends AppCompatActivity {\n" +
            "    private static final int MAX_RETRIES = 3;\n" +
            "    private static final String TITLE = \"Home\";\n\n" +
            "    @Override\n" +
            "    protected void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.activity_main);\n" +
            "        int retries = MAX_RETRIES;\n" +
            "    }\n" +
            "}";

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContent());
    }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        Toolbar toolbar = new Toolbar(this);
        toolbar.setNavigationIcon(R.drawable.ic_mtrl_arrow_back);
        toolbar.setNavigationOnClickListener(v -> finish());
        LinearLayout titleStack = new LinearLayout(this);
        titleStack.setOrientation(LinearLayout.VERTICAL);
        titleStack.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("Code Highlighting", 20, R.attr.colorOnSurface, true);
        TextView subtitle = text("Customize syntax colors for the code editor", 12, R.attr.colorOnSurfaceVariant, false);
        titleStack.addView(title);
        titleStack.addView(subtitle);
        Toolbar.LayoutParams titleParams = new Toolbar.LayoutParams(-2, -1, Gravity.START | Gravity.CENTER_VERTICAL);
        titleParams.leftMargin = dp(12);
        toolbar.addView(titleStack, titleParams);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(64)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        addHeroCard();
        addProfileSection();
        addAppearanceSection();
        addPreviewSection();
        addCustomColorsSection();

        MaterialButton apply = new MaterialButton(this);
        apply.setText("Apply changes");
        apply.setIconResource(R.drawable.ic_mtrl_check);
        apply.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        apply.setTextSize(15);
        apply.setCornerRadius(dp(28));
        apply.setOnClickListener(v -> {
            refreshPreview();
            SketchwareUtil.toast("Code highlighting preferences applied");
            finish();
        });
        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(16), dp(8), dp(16), dp(12));
        footer.addView(apply, new LinearLayout.LayoutParams(-1, dp(52)));
        root.addView(footer);
        return root;
    }

    private void addHeroCard() {
        MaterialCardView hero = card(0);
        hero.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSecondaryContainer));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(18), dp(18), dp(18));
        TextView glyph = text("</>", 25, R.attr.colorOnSecondaryContainer, true);
        glyph.setGravity(Gravity.CENTER);
        glyph.setBackground(round(ThemeUtils.getColor(this, R.attr.colorSurface), dp(18)));
        row.addView(glyph, new LinearLayout.LayoutParams(dp(64), dp(64)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        TextView h = text("Editor Theme & Colors", 17, R.attr.colorOnSecondaryContainer, true);
        TextView d = text("Manage theme profile, appearance and syntax colors", 12, R.attr.colorOnSecondaryContainer, false);
        d.setMaxLines(2);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -2, 1f);
        cp.leftMargin = dp(14);
        copy.addView(h);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(-1, -2);
        dp.topMargin = dp(4);
        copy.addView(d, dp);
        row.addView(copy, cp);
        hero.addView(row);
        content.addView(hero, sectionMargins());
    }

    private void addProfileSection() {
        LinearLayout header = sectionHeader("Theme profile", "Choose a syntax theme or create a custom one");
        MaterialButton create = new MaterialButton(this, null, R.attr.materialButtonOutlinedStyle);
        create.setText("＋ Create");
        create.setTextSize(12);
        create.setMinHeight(dp(38));
        create.setOnClickListener(v -> createCustomProfile());
        header.addView(create);
        content.addView(header, sectionMargins());

        MaterialCardView profileCard = card(1);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setPadding(dp(10), dp(12), dp(10), dp(12));
        addProfileCard(cards, "Material Dark", CodeEditorColorSchemes.PROFILE_MATERIAL, CodeEditorColorSchemes.APPEARANCE_DARK,
                new int[]{0xFF17191C, 0xFFD0BCFF, 0xFF82B1FF, 0xFFA6D189});
        addProfileCard(cards, "Material Light", CodeEditorColorSchemes.PROFILE_MATERIAL, CodeEditorColorSchemes.APPEARANCE_LIGHT,
                new int[]{0xFFFAF8FF, 0xFF6750A4, 0xFF005CA9, 0xFF386A20});
        addProfileCard(cards, "GitHub", CodeEditorColorSchemes.PROFILE_GITHUB, null,
                new int[]{0xFF0D1117, 0xFFFF7B72, 0xFF79C0FF, 0xFFA5D6FF});
        addProfileCard(cards, "Ocean", CodeEditorColorSchemes.PROFILE_OCEAN, null,
                new int[]{0xFF10212B, 0xFF79C0FF, 0xFF89DDFF, 0xFFA5D6A7});
        addProfileCard(cards, "Monokai", CodeEditorColorSchemes.PROFILE_MONOKAI, null,
                new int[]{0xFF272822, 0xFFF92672, 0xFFA6E22E, 0xFFAE81FF});
        addProfileCard(cards, "Dracula", CodeEditorColorSchemes.PROFILE_DRACULA, null,
                new int[]{0xFF282A36, 0xFFFF79C6, 0xFF8BE9FD, 0xFFF1FA8C});
        addProfileCard(cards, "Custom", CodeEditorColorSchemes.PROFILE_CUSTOM, null,
                new int[]{CodeEditorColorSchemes.getTokenColor(this, CodeEditorColorSchemes.COLOR_BACKGROUND),
                        CodeEditorColorSchemes.getTokenColor(this, CodeEditorColorSchemes.COLOR_KEYWORD),
                        CodeEditorColorSchemes.getTokenColor(this, CodeEditorColorSchemes.COLOR_TYPE),
                        CodeEditorColorSchemes.getTokenColor(this, CodeEditorColorSchemes.COLOR_STRING)});
        horizontal.addView(cards);
        profileCard.addView(horizontal);
        content.addView(profileCard, sectionMargins());
        refreshProfileCards();
    }

    private void addProfileCard(LinearLayout parent, String label, String profile, String appearance, int[] colors) {
        MaterialCardView item = new MaterialCardView(this);
        item.setRadius(dp(17));
        item.setCardElevation(0);
        item.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(126), dp(132));
        params.setMargins(dp(4), 0, dp(4), 0);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(8), dp(8), dp(8), dp(8));
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setBackground(round(colors[0], dp(12)));
        mini.setPadding(dp(8), dp(8), dp(8), dp(8));
        for (int i = 1; i < colors.length; i++) {
            View line = new View(this);
            line.setBackground(round(colors[i], dp(4)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == 1 ? dp(62) : i == 2 ? dp(86) : dp(72), dp(6));
            lp.topMargin = dp(7);
            mini.addView(line, lp);
        }
        body.addView(mini, new LinearLayout.LayoutParams(-1, dp(78)));
        TextView name = text(label, 12, R.attr.colorOnSurface, true);
        name.setGravity(Gravity.CENTER);
        name.setMaxLines(1);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        nameParams.topMargin = dp(6);
        body.addView(name, nameParams);
        item.addView(body);
        item.setOnClickListener(v -> {
            SharedPreferences preferences = getPreferences();
            SharedPreferences.Editor editor = preferences.edit();
            if (CodeEditorColorSchemes.PROFILE_CUSTOM.equals(profile)) {
                String currentProfile = CodeEditorColorSchemes.getSelectedProfile(this);
                if (!CodeEditorColorSchemes.PROFILE_CUSTOM.equals(currentProfile)) {
                    editor.putString(CodeEditorColorSchemes.KEY_CUSTOM_BASE_PROFILE, currentProfile);
                }
            }
            editor.putString(CodeEditorColorSchemes.KEY_PROFILE, profile);
            if (appearance != null) editor.putString(CodeEditorColorSchemes.KEY_APPEARANCE, appearance);
            editor.apply();
            refreshSelections();
        });
        parent.addView(item, params);
        profileCards.add(new ProfileCard(item, name, profile, appearance, label));
    }

    private void addAppearanceSection() {
        LinearLayout header = sectionHeader("Appearance", "Follow your device theme or choose manually");
        content.addView(header, sectionMargins());
        MaterialCardView card = card(1);
        appearanceChoices = new LinearLayout(this);
        appearanceChoices.setOrientation(LinearLayout.HORIZONTAL);
        appearanceChoices.setPadding(dp(8), dp(8), dp(8), dp(8));
        addAppearanceChoice("☀  Light", CodeEditorColorSchemes.APPEARANCE_LIGHT);
        addAppearanceChoice("☾  Dark", CodeEditorColorSchemes.APPEARANCE_DARK);
        addAppearanceChoice("▣  System", CodeEditorColorSchemes.APPEARANCE_SYSTEM);
        card.addView(appearanceChoices);
        content.addView(card, sectionMargins());
        refreshAppearanceChoices();
    }

    private void addAppearanceChoice(String label, String value) {
        TextView choice = text(label, 13, R.attr.colorOnSurface, true);
        choice.setGravity(Gravity.CENTER);
        choice.setPadding(dp(8), dp(11), dp(8), dp(11));
        choice.setOnClickListener(v -> {
            getPreferences().edit().putString(CodeEditorColorSchemes.KEY_APPEARANCE, value).apply();
            refreshSelections();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.setMargins(dp(2), 0, dp(2), 0);
        appearanceChoices.addView(choice, params);
    }

    private void addPreviewSection() {
        LinearLayout header = sectionHeader("Preview", "Compare Java and XML with the selected syntax palette");
        TextView fullScreen = text("◉  Full screen", 12, R.attr.colorPrimary, true);
        fullScreen.setPadding(dp(10), dp(8), dp(4), dp(8));
        fullScreen.setOnClickListener(v -> showFullScreenPreview());
        header.addView(fullScreen);
        content.addView(header, sectionMargins());

        MaterialCardView card = card(1);
        LinearLayout previewTabs = new LinearLayout(this);
        previewTabs.setOrientation(LinearLayout.HORIZONTAL);
        previewTabs.setPadding(dp(8), dp(8), dp(8), dp(2));
        previewJavaTab = text("Java", 13, R.attr.colorOnSurface, true);
        previewXmlTab = text("XML", 13, R.attr.colorOnSurface, true);
        previewJavaTab.setGravity(Gravity.CENTER);
        previewXmlTab.setGravity(Gravity.CENTER);
        previewJavaTab.setPadding(dp(18), dp(9), dp(18), dp(9));
        previewXmlTab.setPadding(dp(18), dp(9), dp(18), dp(9));
        LinearLayout.LayoutParams javaTabParams = new LinearLayout.LayoutParams(-2, -2);
        javaTabParams.setMargins(dp(4), 0, dp(4), 0);
        LinearLayout.LayoutParams xmlTabParams = new LinearLayout.LayoutParams(-2, -2);
        xmlTabParams.setMargins(dp(4), 0, dp(4), 0);
        previewTabs.addView(previewJavaTab, javaTabParams);
        previewTabs.addView(previewXmlTab, xmlTabParams);
        previewJavaTab.setOnClickListener(v -> selectPreviewLanguage("MainActivity.java"));
        previewXmlTab.setOnClickListener(v -> selectPreviewLanguage("activity_main.xml"));
        card.addView(previewTabs);

        previewEditor = new CodeEditor(this);
        previewEditor.setTextSize(13);
        previewEditor.setWordwrap(false);
        previewEditor.setEditable(false);
        previewEditor.setTypefaceText(Typeface.MONOSPACE);
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(-1, dp(194));
        editorParams.setMargins(dp(8), dp(6), dp(8), dp(8));
        card.addView(previewEditor, editorParams);
        content.addView(card, sectionMargins());
        refreshPreviewTabs();
        refreshPreview();
    }

    private void selectPreviewLanguage(String fileName) {
        if (fileName == null || fileName.equals(previewFileName)) return;
        previewFileName = fileName;
        refreshPreviewTabs();
        refreshPreview();
    }

    private void refreshPreviewTabs() {
        if (previewJavaTab == null || previewXmlTab == null) return;
        boolean javaSelected = previewFileName.endsWith(".java");
        previewJavaTab.setTextColor(ThemeUtils.getColor(this, javaSelected ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant));
        previewJavaTab.setBackground(round(ThemeUtils.getColor(this, javaSelected ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainer), dp(18)));
        previewXmlTab.setTextColor(ThemeUtils.getColor(this, !javaSelected ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurfaceVariant));
        previewXmlTab.setBackground(round(ThemeUtils.getColor(this, !javaSelected ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainer), dp(18)));
    }

    private void addCustomColorsSection() {
        LinearLayout header = sectionHeader("Custom colors", "Modify syntax colors for the selected theme");
        TextView reset = text("↻  Reset", 12, R.attr.colorOnSurfaceVariant, true);
        reset.setPadding(dp(10), dp(8), dp(4), dp(8));
        reset.setOnClickListener(v -> resetColors());
        header.addView(reset);
        content.addView(header, sectionMargins());

        MaterialCardView card = card(1);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        String[][] tokens = {
                {CodeEditorColorSchemes.COLOR_KEYWORD, "Keyword", "e.g. public, class, return"},
                {CodeEditorColorSchemes.COLOR_TYPE, "Type", "e.g. String, int, Bundle"},
                {CodeEditorColorSchemes.COLOR_STRING, "String", "e.g. \"text\", 'value'"},
                {CodeEditorColorSchemes.COLOR_NUMBER, "Number", "e.g. 10, 0xFF, 3.14"},
                {CodeEditorColorSchemes.COLOR_COMMENT, "Comment", "e.g. // notes and explanations"},
                {CodeEditorColorSchemes.COLOR_FUNCTION, "Function", "e.g. onCreate(), findViewById()"},
                {CodeEditorColorSchemes.COLOR_BACKGROUND, "Background", "Editor canvas"},
                {CodeEditorColorSchemes.COLOR_FOREGROUND, "Text", "Normal code text"},
                {CodeEditorColorSchemes.COLOR_TAG, "XML tag", "e.g. LinearLayout, TextView"},
                {CodeEditorColorSchemes.COLOR_ATTRIBUTE, "Attribute", "e.g. android:id, operators"},
                {CodeEditorColorSchemes.COLOR_LINE_NUMBER, "Line number", "Gutter line-number text"},
                {CodeEditorColorSchemes.COLOR_LINE_BACKGROUND, "Current line", "Background behind the active line"}
        };
        for (int i = 0; i < tokens.length; i++) {
            String key = tokens[i][0];
            rows.addView(createColorRow(key, tokens[i][1], tokens[i][2]));
            if (i != tokens.length - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
                rows.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
        card.addView(rows);
        content.addView(card, sectionMargins());
        TextView note = text("Color changes affect Java, XML, Kotlin, JSON, Gradle and other supported code views. They do not change the compiler or source code.",
                12, R.attr.colorOnSurfaceVariant, false);
        note.setPadding(dp(4), dp(10), dp(4), dp(8));
        content.addView(note);
    }

    private View createColorRow(String key, String label, String description) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(10), dp(14), dp(10));
        View swatch = new View(this);
        swatch.setBackground(round(CodeEditorColorSchemes.getTokenColor(this, key), dp(7)));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(30), dp(30)));
        LinearLayout nameColumn = new LinearLayout(this);
        nameColumn.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(label, 14, R.attr.colorOnSurface, true);
        TextView hint = text(description, 11, R.attr.colorOnSurfaceVariant, false);
        hint.setMaxLines(1);
        hint.setEllipsize(TextUtils.TruncateAt.END);
        nameColumn.addView(name);
        nameColumn.addView(hint);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(12);
        row.addView(nameColumn, nameParams);
        TextView value = text(CodeEditorColorSchemes.getTokenColorHex(this, key), 12, R.attr.colorOnSurfaceVariant, true);
        row.addView(value);
        TextView chevron = text("›", 26, R.attr.colorOnSurfaceVariant, false);
        chevron.setPadding(dp(8), 0, 0, 0);
        row.addView(chevron);
        colorValues.put(key, value);
        colorSwatches.put(key, swatch);
        row.setOnClickListener(v -> openColorPicker(key, label, swatch));
        row.setBackground(selectableBackground());
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private void openColorPicker(String key, String label, View anchor) {
        int current = CodeEditorColorSchemes.getTokenColor(this, key);
        ColorPickerDialog picker = new ColorPickerDialog(this, current, false, false);
        picker.a(new ColorPickerDialog.b() {
            @Override public void a(int color) { saveColor(key, color); }
            @Override public void a(String resourceName, int color) { saveColor(key, color); }
        });
        picker.showAtLocation(anchor, Gravity.CENTER, 0, 0);
    }

    private void saveColor(String key, int color) {
        CodeEditorColorSchemes.saveCustomColor(this, key, color);
        refreshSelections();
    }

    private void createCustomProfile() {
        TextInputEditText input = new TextInputEditText(this);
        input.setSingleLine(true);
        input.setHint("My editor theme");
        input.setText(getPreferences().getString(CodeEditorColorSchemes.KEY_CUSTOM_PROFILE_NAME, "Custom"));
        int pad = dp(20);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(pad, dp(8), pad, 0);
        frame.addView(input, new FrameLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(this).setTitle("Create custom profile")
                .setMessage("A custom profile starts with the current palette. You can change each token color below.")
                .setView(frame)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = input.getText() == null ? "Custom" : input.getText().toString().trim();
                    if (name.isEmpty()) name = "Custom";
                    String activeProfile = CodeEditorColorSchemes.getSelectedProfile(this);
                    String baseProfile = CodeEditorColorSchemes.PROFILE_CUSTOM.equals(activeProfile)
                            ? getPreferences().getString(CodeEditorColorSchemes.KEY_CUSTOM_BASE_PROFILE,
                                    CodeEditorColorSchemes.PROFILE_MATERIAL) : activeProfile;
                    if (CodeEditorColorSchemes.PROFILE_CUSTOM.equals(baseProfile)) {
                        baseProfile = CodeEditorColorSchemes.PROFILE_MATERIAL;
                    }
                    getPreferences().edit().putString(CodeEditorColorSchemes.KEY_CUSTOM_PROFILE_NAME, name)
                            .putString(CodeEditorColorSchemes.KEY_CUSTOM_BASE_PROFILE, baseProfile)
                            .putString(CodeEditorColorSchemes.KEY_PROFILE, CodeEditorColorSchemes.PROFILE_CUSTOM).apply();
                    refreshSelections();
                }).show();
    }

    private void resetColors() {
        String[] keys = {CodeEditorColorSchemes.COLOR_BACKGROUND, CodeEditorColorSchemes.COLOR_FOREGROUND,
                CodeEditorColorSchemes.COLOR_KEYWORD, CodeEditorColorSchemes.COLOR_STRING,
                CodeEditorColorSchemes.COLOR_NUMBER, CodeEditorColorSchemes.COLOR_COMMENT,
                CodeEditorColorSchemes.COLOR_FUNCTION, CodeEditorColorSchemes.COLOR_TYPE,
                CodeEditorColorSchemes.COLOR_TAG, CodeEditorColorSchemes.COLOR_ATTRIBUTE,
                CodeEditorColorSchemes.COLOR_LINE_NUMBER, CodeEditorColorSchemes.COLOR_LINE_BACKGROUND};
        SharedPreferences.Editor editor = getPreferences().edit();
        for (String key : keys) {
            editor.remove(key).remove(CodeEditorColorSchemes.preferenceKey(key, true))
                    .remove(CodeEditorColorSchemes.preferenceKey(key, false));
        }
        editor.remove(CodeEditorColorSchemes.KEY_CUSTOM_BASE_PROFILE)
                .putString(CodeEditorColorSchemes.KEY_PROFILE, CodeEditorColorSchemes.PROFILE_MATERIAL).apply();
        refreshSelections();
    }

    private void refreshSelections() {
        refreshProfileCards();
        refreshAppearanceChoices();
        refreshColorRows();
        refreshPreview();
    }

    private void refreshProfileCards() {
        String profile = CodeEditorColorSchemes.getSelectedProfile(this);
        String appearance = CodeEditorColorSchemes.getSelectedAppearance(this);
        for (ProfileCard card : profileCards) {
            boolean selected = card.profile.equals(profile)
                    && (card.appearance == null || card.appearance.equals(appearance));
            if (CodeEditorColorSchemes.PROFILE_CUSTOM.equals(card.profile)) {
                String customName = getPreferences().getString(
                        CodeEditorColorSchemes.KEY_CUSTOM_PROFILE_NAME, "Custom");
                card.nameView.setText(TextUtils.isEmpty(customName) ? "Custom" : customName);
            }
            card.card.setStrokeColor(ThemeUtils.getColor(this, selected ? R.attr.colorPrimary : R.attr.colorOutlineVariant));
            card.card.setStrokeWidth(selected ? dp(2) : dp(1));
            card.card.setCardBackgroundColor(ThemeUtils.getColor(this, selected ? R.attr.colorSecondaryContainer : R.attr.colorSurfaceContainer));
        }
    }

    private void refreshAppearanceChoices() {
        if (appearanceChoices == null) return;
        String active = CodeEditorColorSchemes.getSelectedAppearance(this);
        for (int i = 0; i < appearanceChoices.getChildCount(); i++) {
            TextView choice = (TextView) appearanceChoices.getChildAt(i);
            String value = i == 0 ? CodeEditorColorSchemes.APPEARANCE_LIGHT
                    : i == 1 ? CodeEditorColorSchemes.APPEARANCE_DARK : CodeEditorColorSchemes.APPEARANCE_SYSTEM;
            boolean selected = value.equals(active);
            choice.setTextColor(ThemeUtils.getColor(this, selected ? R.attr.colorOnPrimaryContainer : R.attr.colorOnSurface));
            choice.setBackground(round(ThemeUtils.getColor(this, selected ? R.attr.colorPrimaryContainer : R.attr.colorSurfaceContainer), dp(22)));
        }
    }

    private void refreshColorRows() {
        for (Map.Entry<String, TextView> entry : colorValues.entrySet()) {
            entry.getValue().setText(CodeEditorColorSchemes.getTokenColorHex(this, entry.getKey()));
            View swatch = colorSwatches.get(entry.getKey());
            if (swatch != null) swatch.setBackground(round(CodeEditorColorSchemes.getTokenColor(this, entry.getKey()), dp(7)));
        }
    }

    private static final String XML_SAMPLE_CODE =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    android:orientation=\"vertical\">\n" +
            "    <TextView\n" +
            "        android:id=\"@+id/title\"\n" +
            "        android:layout_width=\"match_parent\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:text=\"Sketchware Neo\" />\n" +
            "</LinearLayout>";

    private void refreshPreview() {
        if (previewEditor == null) return;
        SrcCodeEditor.applyLanguageSpec(this, previewEditor,
                CodeEditorLanguages.resolveLanguageSpec(previewFileName));
        previewEditor.setText(previewFileName.endsWith(".xml") ? XML_SAMPLE_CODE : SAMPLE_CODE);
        previewEditor.setEditable(false);
        previewEditor.setWordwrap(false);
        previewEditor.setHighlightCurrentLine(true);
    }

    private void showFullScreenPreview() {
        Dialog dialog = new Dialog(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurface));

        Toolbar toolbar = new Toolbar(this);
        toolbar.setNavigationIcon(R.drawable.ic_mtrl_arrow_back);
        toolbar.setNavigationOnClickListener(v -> dialog.dismiss());
        TextView heading = text(previewFileName.endsWith(".xml") ? "XML preview" : "Java preview", 18, R.attr.colorOnSurface, true);
        Toolbar.LayoutParams headingParams = new Toolbar.LayoutParams(-2, -1, Gravity.START | Gravity.CENTER_VERTICAL);
        headingParams.leftMargin = dp(12);
        toolbar.addView(heading, headingParams);
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(56)));

        CodeEditor editor = new CodeEditor(this);
        editor.setTypefaceText(Typeface.MONOSPACE);
        editor.setTextSize(14);
        editor.setEditable(false);
        editor.setWordwrap(false);
        SrcCodeEditor.applyLanguageSpec(this, editor,
                CodeEditorLanguages.resolveLanguageSpec(previewFileName));
        editor.setText(previewFileName.endsWith(".xml") ? XML_SAMPLE_CODE : SAMPLE_CODE);
        editor.setHighlightCurrentLine(true);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1f));

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    private LinearLayout sectionHeader(String title, String subtitle) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(title, 16, R.attr.colorOnSurface, true));
        labels.addView(text(subtitle, 11, R.attr.colorOnSurfaceVariant, false));
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        return header;
    }

    private MaterialCardView card(int style) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20));
        card.setCardElevation(0);
        card.setStrokeWidth(dp(1));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainer));
        return card;
    }

    private LinearLayout.LayoutParams sectionMargins() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(14);
        return params;
    }

    private TextView text(String value, int size, int colorAttr, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(ThemeUtils.getColor(this, colorAttr));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private Drawable selectableBackground() {
        TypedValue value = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId != 0 ? getDrawable(value.resourceId) : new ColorDrawable(Color.TRANSPARENT);
    }

    private SharedPreferences getPreferences() {
        return getSharedPreferences(CodeEditorColorSchemes.PREFS_NAME, MODE_PRIVATE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class ProfileCard {
        final MaterialCardView card;
        final TextView nameView;
        final String profile;
        final String appearance;
        final String label;
        ProfileCard(MaterialCardView card, TextView nameView, String profile, String appearance, String label) {
            this.card = card;
            this.nameView = nameView;
            this.profile = profile;
            this.appearance = appearance;
            this.label = label;
        }
    }
}
