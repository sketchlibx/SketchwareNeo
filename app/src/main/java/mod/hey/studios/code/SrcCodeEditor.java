package mod.hey.studios.code;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import a.a.a.Lx;
import a.a.a.ProjectBuilder;
import io.github.rosemoe.sora.langs.java.JavaLanguage;
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion;
import io.github.rosemoe.sora.widget.component.Magnifier;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula;
import io.github.rosemoe.sora.widget.schemes.SchemeEclipse;
import io.github.rosemoe.sora.widget.schemes.SchemeGitHub;
import io.github.rosemoe.sora.widget.schemes.SchemeNotepadXX;
import io.github.rosemoe.sora.widget.schemes.SchemeVS2019;
import mod.hey.studios.ide.diagnostics.Diagnostic;
import mod.hey.studios.ide.diagnostics.DiagnosticsAdapter;
import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages;
import pro.sketchware.R;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.CodeEditorHsBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

@SuppressWarnings({"deprecation", "removal"})
public class SrcCodeEditor extends BaseAppCompatActivity {
    public static final String FLAG_FROM_ANDROID_MANIFEST = "from_android_manifest";
    public static final List<Pair<String, Class<? extends EditorColorScheme>>> KNOWN_COLOR_SCHEMES = List.of(
            new Pair<>("Default", EditorColorScheme.class),
            new Pair<>("GitHub", SchemeGitHub.class),
            new Pair<>("Eclipse", SchemeEclipse.class),
            new Pair<>("Darcula", SchemeDarcula.class),
            new Pair<>("VS2019", SchemeVS2019.class),
            new Pair<>("NotepadXX", SchemeNotepadXX.class)
    );
    public static SharedPreferences pref;
    public static int languageId;

    private String beforeContent = "";
    private CodeEditorHsBinding binding;
    private boolean fromAndroidManifest;
    private String scId;
    private String activityName;
    private String currentTitle;

    // IDE UI Elements
    private DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> diagnosticsBehavior;
    private TextView tvCursorPos;
    private TextView tvLanguage;
    private TabLayout editorTabs;
    private RecyclerView rvExplorer;

    // Search UI Elements
    private MaterialCardView searchCard;
    private LinearLayout searchPanel;
    private ImageView prevBtn, nextBtn, replaceBtn, replaceAllBtn;
    private EditText findEdit, replaceEdit;

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

    public static void loadCESettings(Context c, CodeEditor ed, String prefix) {
        loadCESettings(c, ed, prefix, false);
    }

    public static void loadCESettings(Context c, CodeEditor ed, String prefix, boolean loadTheme) {
        pref = c.getSharedPreferences("hsce", Activity.MODE_PRIVATE);
        int text_size = pref.getInt(prefix + "_ts", 12);
        int theme = pref.getInt(prefix + "_theme", 3);
        boolean word_wrap = pref.getBoolean(prefix + "_ww", false);
        boolean auto_c = pref.getBoolean(prefix + "_ac", true);
        boolean auto_complete_symbol_pairs = pref.getBoolean(prefix + "_acsp", true);

        if (loadTheme) selectTheme(ed, theme);
        ed.setTextSize(text_size);
        ed.setWordwrap(word_wrap);
        ed.getProps().symbolPairAutoCompletion = auto_complete_symbol_pairs;
        ed.getComponent(EditorAutoCompletion.class).setEnabled(auto_c);
        ed.getComponent(Magnifier.class).setEnabled(true);
        ed.setHighlightCurrentLine(true);
        ed.setLineSpacing(2f, 1.1f);
    }

    public static void selectTheme(CodeEditor ed, int which) {
        if (!(ed.getColorScheme() instanceof TextMateColorScheme)) {
            EditorColorScheme scheme = switch (which) {
                case 1 -> new SchemeGitHub();
                case 2 -> new SchemeEclipse();
                case 3 -> new SchemeDarcula();
                case 4 -> new SchemeVS2019();
                case 5 -> new SchemeNotepadXX();
                default -> new EditorColorScheme();
            };
            ed.setColorScheme(scheme);
        }
    }

    public static void selectLanguage(CodeEditor ed, int which) {
        switch (which) {
            default:
            case 0: ed.setEditorLanguage(new JavaLanguage()); languageId = 0; break;
            case 1: ed.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN)); languageId = 1; break;
            case 2: ed.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML)); languageId = 2; break;
        }
    }

    public static String prettifyXml(String xml, int indentAmount, Intent extras) {
        if (xml == null || xml.trim().isEmpty()) return xml;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))));
            document.normalize();
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xPath.evaluate("//text()[normalize-space()='']", document, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); ++i) {
                Node node = nodeList.item(i);
                node.getParentNode().removeChild(node);
            }
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", String.valueOf(indentAmount));

            boolean omitXmlDecl = extras != null && extras.hasExtra("disableHeader");
            if (omitXmlDecl) transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            String result = writer.toString();

            if (!omitXmlDecl && result.startsWith("<?xml")) {
                int endOfDecl = result.indexOf("?>");
                if (endOfDecl != -1 && endOfDecl + 2 < result.length() && result.charAt(endOfDecl + 2) != '\n') {
                    result = result.substring(0, endOfDecl + 2) + "\n" + result.substring(endOfDecl + 2);
                }
            }

            String[] lines = result.split("\n");
            StringBuilder formatted = new StringBuilder();
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.startsWith("<") && !trimmed.startsWith("<?") && !trimmed.startsWith("<!") && trimmed.contains(" ") && !trimmed.startsWith("</")) {
                    int indentBase = line.indexOf('<');
                    String baseIndent = " ".repeat(Math.max(0, indentBase));
                    String attrIndent = baseIndent + "    ";
                    boolean selfClosing = trimmed.endsWith("/>");
                    int tagEnd = trimmed.indexOf(' ');
                    if (tagEnd > 0) {
                        String tagName = trimmed.substring(1, tagEnd);
                        String attrPart = trimmed.substring(tagEnd + 1).replaceAll("/?>$", "").trim();
                        String[] attrs = attrPart.split("\\s+(?=[^=]+\\=)");
                        formatted.append(baseIndent).append("<").append(tagName).append("\n");
                        for (String attr : attrs) { formatted.append(attrIndent).append(attr.trim()).append("\n"); }
                        int lastNewline = formatted.lastIndexOf("\n");
                        if (lastNewline != -1) formatted.delete(lastNewline, formatted.length());
                        formatted.append(selfClosing ? " />" : ">").append("\n");
                    } else { formatted.append(line).append("\n"); }
                } else { formatted.append(line).append("\n"); }
            }
            return formatted.toString().trim();
        } catch (Exception e) { return null; }
    }

    public static void showSwitchThemeDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        EditorColorScheme currentScheme = codeEditor.getColorScheme();
        var knownColorSchemesProperlyOrdered = new ArrayList<>(KNOWN_COLOR_SCHEMES);
        Collections.reverse(knownColorSchemesProperlyOrdered);
        int selectedThemeIndex = knownColorSchemesProperlyOrdered.stream().filter(pair -> pair.second.equals(currentScheme.getClass())).map(KNOWN_COLOR_SCHEMES::indexOf).findFirst().orElse(-1);
        String[] themeItems = KNOWN_COLOR_SCHEMES.stream().map(pair -> pair.first).toArray(String[]::new);
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Theme")
                .setSingleChoiceItems(themeItems, selectedThemeIndex, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    public static void showSwitchLanguageDialog(Activity activity, CodeEditor codeEditor, DialogInterface.OnClickListener listener) {
        CharSequence[] languagesList = { "Java", "Kotlin", "XML" };
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Language")
                .setSingleChoiceItems(languagesList, languageId, listener)
                .setNegativeButton(R.string.common_word_cancel, null)
                .show();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = CodeEditorHsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fromAndroidManifest = getIntent().getBooleanExtra(FLAG_FROM_ANDROID_MANIFEST, false);
        currentTitle = getIntent().getStringExtra("title");
        scId = getIntent().getStringExtra("sc_id");
        activityName = getIntent().getStringExtra("activity_name");

        drawerLayout = findViewById(R.id.drawer_layout);
        tvCursorPos = findViewById(R.id.tv_cursor_pos);
        tvLanguage = findViewById(R.id.tv_language);
        editorTabs = findViewById(R.id.editor_tabs);

        binding.editor.setTypefaceText(EditorUtils.getTypeface(this));

        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> arrayList = getGson().fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < arrayList.size(); i++) {
                    if (arrayList.get(i).get("name").equals(activityName)) {
                        beforeContent = (String) arrayList.get(i).get("value");
                    }
                }
            }
        } else {
            beforeContent = FileUtil.readFile(getIntent().getStringExtra("content"));
        }

        binding.editor.setText(beforeContent);
        applySyntaxHighlighting();

        loadCESettings(this, binding.editor, "act", languageId == 0);
        
        setupToolbar();
        setupTabs();
        setupSearchPanel();
        setupDiagnosticsPanel();
        setupProjectExplorer();
        
        // Listen to text changes to instantly enable/disable Undo, Redo, Save icons
        binding.editor.getText().addContentListener(new ContentListener() {
            @Override public void beforeReplace(Content content) {}
            @Override public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
                runOnUiThread(() -> invalidateOptionsMenu());
            }
            @Override public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                runOnUiThread(() -> invalidateOptionsMenu());
            }
        });

        View appBarLayout = findViewById(R.id.app_bar_layout);
        if(appBarLayout != null) UI.addSystemWindowInsetToPadding(appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, true);
        
        View navView = findViewById(R.id.nav_view_explorer);
        if(navView != null) UI.addSystemWindowInsetToPadding(navView, true, true, true, true);
    }
    
    private void applySyntaxHighlighting() {
        if (currentTitle != null) {
            String name = currentTitle.toLowerCase();
            if (name.endsWith(".java")) {
                binding.editor.setEditorLanguage(new JavaLanguage());
                languageId = 0;
                if(tvLanguage != null) tvLanguage.setText("Java");
            } else if (name.endsWith(".kt")) {
                binding.editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_KOTLIN));
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(CodeEditorColorSchemes.THEME_DRACULA));
                languageId = 1;
                if(tvLanguage != null) tvLanguage.setText("Kotlin");
            } else if (name.endsWith(".xml") || name.endsWith(".gradle")) { // Treat gradle roughly similar syntax wise if needed
                binding.editor.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(CodeEditorLanguages.SCOPE_NAME_XML));
                binding.editor.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(ThemeUtils.isDarkThemeEnabled(getApplicationContext()) ? CodeEditorColorSchemes.THEME_DRACULA : CodeEditorColorSchemes.THEME_GITHUB));
                languageId = 2;
                if(tvLanguage != null) tvLanguage.setText(name.endsWith(".gradle") ? "Gradle" : "XML");
            } else {
                EditorUtils.loadXmlConfig(binding.editor);
                if(tvLanguage != null) tvLanguage.setText("Plain Text");
            }
        }
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentTitle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Specifically removed setHomeAsUpIndicator to restore the Back (<-) arrow
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    private void setupTabs() {
        if(editorTabs != null) {
            editorTabs.removeAllTabs();
            editorTabs.addTab(editorTabs.newTab().setText(currentTitle != null ? currentTitle : "File"));
        }
    }

    private void setupProjectExplorer() {
        rvExplorer = new RecyclerView(this);
        rvExplorer.setLayoutManager(new LinearLayoutManager(this));
        
        ViewGroup navView = findViewById(R.id.nav_view_explorer);
        if (navView != null) {
            navView.removeAllViews();
            navView.addView(rvExplorer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            File projectRoot = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/");
            if (projectRoot.exists()) {
                FileTreeAdapter adapter = new FileTreeAdapter(projectRoot, file -> {
                    save(); 
                    currentTitle = file.getName();
                    beforeContent = FileUtil.readFile(file.getAbsolutePath());
                    binding.editor.setText(beforeContent);
                    
                    getIntent().putExtra("content", file.getAbsolutePath());
                    getIntent().putExtra("title", currentTitle);
                    
                    applySyntaxHighlighting();
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentTitle);
                    setupTabs();
                    drawerLayout.closeDrawer(GravityCompat.START);
                    invalidateOptionsMenu();
                });
                rvExplorer.setAdapter(adapter);
            }
        }
    }

    // Inner class for File Tree (raw file system)
    private class FileTreeAdapter extends RecyclerView.Adapter<FileTreeAdapter.FileViewHolder> {
        private final List<FileNode> nodes = new ArrayList<>();
        private final OnFileClickListener listener;

        class FileNode {
            File file; int depth; boolean isExpanded; boolean isDirectory;
            FileNode(File file, int depth) { this.file = file; this.depth = depth; this.isDirectory = file.isDirectory(); this.isExpanded = false; }
        }

        public interface OnFileClickListener { void onFileClick(File file); }

        public FileTreeAdapter(File rootDir, OnFileClickListener listener) {
            this.listener = listener;
            if (rootDir != null && rootDir.exists()) loadDirectory(rootDir, 0);
        }

        private void loadDirectory(File dir, int depth) {
            File[] files = dir.listFiles();
            if (files == null) return;
            List<File> directories = new ArrayList<>();
            List<File> plainFiles = new ArrayList<>();
            for (File f : files) { if (f.isDirectory()) directories.add(f); else plainFiles.add(f); }
            directories.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            plainFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
            for (File d : directories) nodes.add(new FileNode(d, depth));
            for (File f : plainFiles) nodes.add(new FileNode(f, depth));
        }

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout root = new LinearLayout(parent.getContext());
            root.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setGravity(android.view.Gravity.CENTER_VERTICAL);
            root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(8));
            
            TypedValue outValue = new TypedValue();
            parent.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
            root.setBackgroundResource(outValue.resourceId);

            ImageView chevron = new ImageView(parent.getContext());
            chevron.setId(View.generateViewId());
            chevron.setImageResource(R.drawable.ic_mtrl_chevron_right_24);
            chevron.setColorFilter(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurfaceVariant));
            root.addView(chevron, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

            ImageView icon = new ImageView(parent.getContext());
            icon.setId(View.generateViewId());
            LinearLayout.LayoutParams icParams = new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24));
            icParams.setMarginStart(SketchwareUtil.dpToPx(8));
            root.addView(icon, icParams);

            TextView title = new TextView(parent.getContext());
            title.setId(View.generateViewId());
            title.setTextSize(14f);
            title.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorOnSurface));
            LinearLayout.LayoutParams tParams = new LinearLayout.LayoutParams(-1, -2);
            tParams.setMarginStart(SketchwareUtil.dpToPx(12));
            root.addView(title, tParams);

            return new FileViewHolder(root, chevron, icon, title);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            FileNode node = nodes.get(position);
            holder.title.setText(node.file.getName());
            
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.chevron.getLayoutParams();
            params.setMarginStart((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, node.depth * 20, holder.itemView.getContext().getResources().getDisplayMetrics()));
            holder.chevron.setLayoutParams(params);

            if (node.isDirectory) {
                holder.chevron.setVisibility(View.VISIBLE);
                holder.chevron.setRotation(node.isExpanded ? 90 : 0);
                holder.icon.setImageResource(R.drawable.ic_mtrl_folder);
                holder.icon.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorPrimary));
                holder.itemView.setOnClickListener(v -> toggleDirectory(position));
            } else {
                holder.chevron.setVisibility(View.INVISIBLE);
                holder.icon.clearColorFilter();
                String name = node.file.getName().toLowerCase();
                if (name.endsWith(".java")) holder.icon.setImageResource(R.drawable.ic_mtrl_java);
                else if (name.endsWith(".kt")) holder.icon.setImageResource(R.drawable.ic_mtrl_kotlin);
                else if (name.endsWith(".xml")) holder.icon.setImageResource(R.drawable.ic_code_white_48dp);
                else holder.icon.setImageResource(R.drawable.ic_insert_drive_file_white_48dp);
                holder.itemView.setOnClickListener(v -> listener.onFileClick(node.file));
            }
        }

        private void toggleDirectory(int position) {
            FileNode node = nodes.get(position);
            if (!node.isDirectory) return;
            if (node.isExpanded) {
                int count = 0;
                for (int i = position + 1; i < nodes.size(); i++) {
                    if (nodes.get(i).depth > node.depth) count++; else break;
                }
                nodes.subList(position + 1, position + 1 + count).clear();
                node.isExpanded = false;
                notifyItemRangeRemoved(position + 1, count);
                notifyItemChanged(position);
            } else {
                File[] children = node.file.listFiles();
                if (children != null && children.length > 0) {
                    List<FileNode> newNodes = new ArrayList<>();
                    List<File> directories = new ArrayList<>();
                    List<File> plainFiles = new ArrayList<>();
                    for (File f : children) { if (f.isDirectory()) directories.add(f); else plainFiles.add(f); }
                    directories.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                    plainFiles.sort((f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));
                    for (File d : directories) newNodes.add(new FileNode(d, node.depth + 1));
                    for (File f : plainFiles) newNodes.add(new FileNode(f, node.depth + 1));
                    nodes.addAll(position + 1, newNodes);
                    node.isExpanded = true;
                    notifyItemRangeInserted(position + 1, newNodes.size());
                    notifyItemChanged(position);
                }
            }
        }

        @Override
        public int getItemCount() { return nodes.size(); }

        class FileViewHolder extends RecyclerView.ViewHolder {
            ImageView chevron, icon; TextView title;
            FileViewHolder(View v, ImageView c, ImageView i, TextView t) { super(v); chevron = c; icon = i; title = t; }
        }
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
                if(diagnostic.fileName.equals(currentTitle)) {
                    binding.editor.jumpToLine(diagnostic.line - 1); 
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
        prevBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { binding.editor.getSearcher().gotoPrevious(); } catch (Exception ignored) {} });
        
        nextBtn = new ImageView(this);
        nextBtn.setImageResource(R.drawable.ic_mtrl_arrow_down);
        nextBtn.setColorFilter(iconColor);
        nextBtn.setPadding(16, 16, 16, 16);
        nextBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { binding.editor.getSearcher().gotoNext(); } catch (Exception ignored) {} });
        
        ImageView closeBtn = new ImageView(this);
        closeBtn.setImageResource(R.drawable.ic_mtrl_close);
        closeBtn.setColorFilter(iconColor);
        closeBtn.setPadding(16, 16, 16, 16);
        closeBtn.setOnClickListener(v -> {
            try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
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
        replaceBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { binding.editor.getSearcher().replaceThis(replaceEdit.getText().toString()); } catch (Exception ignored) {} });
        
        replaceAllBtn = new ImageView(this);
        replaceAllBtn.setImageResource(R.drawable.ic_done_all_white_24dp);
        replaceAllBtn.setColorFilter(iconColor);
        replaceAllBtn.setPadding(16, 16, 16, 16);
        replaceAllBtn.setOnClickListener(v -> { if (findEdit.getText().length() > 0) try { binding.editor.getSearcher().replaceAll(replaceEdit.getText().toString()); } catch (Exception ignored) {} });
        
        replaceRow.addView(replaceEdit); replaceRow.addView(replaceBtn); replaceRow.addView(replaceAllBtn);
        
        searchPanel.addView(findRow); searchPanel.addView(replaceRow);
        searchCard.addView(searchPanel);
        
        ViewGroup rootView = (ViewGroup) binding.editor.getParent();
        if (rootView != null) rootView.addView(searchCard, 1);
        
        findEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s != null && s.length() > 0) {
                    try { binding.editor.getSearcher().search(s.toString(), new EditorSearcher.SearchOptions(EditorSearcher.SearchOptions.TYPE_NORMAL, true)); } catch (Exception ignored) {}
                } else {
                    try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
                }
            }
        });
    }

    public void save() {
        beforeContent = binding.editor.getText().toString();
        if (fromAndroidManifest) {
            String filePath = FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/Injection/androidmanifest/activities_components.json";
            if (FileUtil.isExistFile(filePath)) {
                ArrayList<HashMap<String, Object>> activitiesComponents = getGson().fromJson(FileUtil.readFile(filePath), Helper.TYPE_MAP_LIST);
                for (int i = 0; i < activitiesComponents.size(); i++) {
                    if (activitiesComponents.get(i).get("name").equals(activityName)) {
                        activitiesComponents.get(i).put("value", beforeContent);
                        FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
                        SketchwareUtil.toast("Saved successfully!");
                        invalidateOptionsMenu();
                        return;
                    }
                }
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                activitiesComponents.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(activitiesComponents));
            } else {
                ArrayList<HashMap<String, Object>> arrayList = new ArrayList<>();
                HashMap<String, Object> map = new HashMap<>();
                map.put("name", activityName);
                map.put("value", beforeContent);
                arrayList.add(map);
                FileUtil.writeFile(filePath, getGson().toJson(arrayList));
            }
        } else {
            FileUtil.writeFile(getIntent().getStringExtra("content"), beforeContent);
        }
        SketchwareUtil.toast("Saved successfully!");
        invalidateOptionsMenu();
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
        pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / scaledDensity)).apply();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (searchCard != null && searchCard.getVisibility() == View.VISIBLE) {
            try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
            searchCard.setVisibility(View.GONE);
            findEdit.setText("");
            return;
        }

        if (beforeContent.equals(binding.editor.getText().toString())) {
            super.onBackPressed();
        } else {
            MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
            dialog.setIcon(R.drawable.ic_mtrl_warning);
            dialog.setTitle(Helper.getResString(R.string.common_word_warning));
            dialog.setMessage(Helper.getResString(R.string.src_code_editor_unsaved_changes_dialog_warning_message));
            dialog.setPositiveButton(Helper.getResString(R.string.common_word_exit), (v, which) -> {
                v.dismiss();
                finish();
            });
            dialog.setNegativeButton(Helper.getResString(R.string.common_word_cancel), null);
            dialog.show();
        }
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
        wrapItem.setCheckable(true).setChecked(pref.getBoolean("act_ww", false)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        MenuItem acItem = menu.add(Menu.NONE, 10, Menu.NONE, "Auto complete");
        acItem.setCheckable(true).setChecked(pref.getBoolean("act_ac", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        MenuItem acspItem = menu.add(Menu.NONE, 11, Menu.NONE, "Auto complete symbol pair");
        acspItem.setCheckable(true).setChecked(pref.getBoolean("act_acsp", true)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem undoItem = menu.findItem(0);
        if (undoItem != null) {
            try { undoItem.setEnabled(binding.editor.canUndo()); } catch (Exception ignored) {}
        }
        MenuItem redoItem = menu.findItem(1);
        if (redoItem != null) {
            try { redoItem.setEnabled(binding.editor.canRedo()); } catch (Exception ignored) {}
        }
        MenuItem saveItem = menu.findItem(2);
        if (saveItem != null) {
            boolean isModified = !beforeContent.equals(binding.editor.getText().toString());
            saveItem.setEnabled(isModified);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> { binding.editor.undo(); return true; }
            case 1 -> { binding.editor.redo(); return true; }
            case 2 -> { save(); return true; }
            case 12 -> { 
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START); 
                return true; 
            }
            case 4 -> { if(searchCard != null) searchCard.setVisibility(View.VISIBLE); return true; }
            case 5 -> {
                if (getIntent().hasExtra("java") || (currentTitle != null && currentTitle.endsWith(".java"))) {
                    StringBuilder b = new StringBuilder();
                    for (String line : binding.editor.getText().toString().split("\n")) {
                        String trims = (line + "X").trim();
                        if (!trims.isEmpty()) b.append(trims.substring(0, Math.max(0, trims.length() - 1))).append("\n");
                    }
                    try { binding.editor.setText(Lx.j(b.toString(), true)); SketchwareUtil.toast("Code Formatted!"); } 
                    catch (Exception e) { SketchwareUtil.toastError("Your code contains incorrectly nested parentheses"); }
                } else if (getIntent().hasExtra("xml") || (currentTitle != null && currentTitle.endsWith(".xml"))) {
                    String format = prettifyXml(binding.editor.getText().toString(), 4, getIntent());
                    if (format != null) { binding.editor.setText(format); SketchwareUtil.toast("XML Formatted!"); } 
                    else { SketchwareUtil.toastError("Failed to format XML file"); }
                } else { SketchwareUtil.toast("Only Java and XML files can be formatted"); }
                return true;
            }
            case 6 -> {
                Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra("xml", binding.editor.getText().toString());
                startActivity(intent);
                return true; 
            }
            case 7 -> { showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> { selectLanguage(binding.editor, which); dialog.dismiss(); }); return true; }
            case 8 -> {
                showSwitchThemeDialog(this, binding.editor, (dialog, which) -> {
                    selectTheme(binding.editor, which);
                    pref.edit().putInt("act_theme", which).apply();
                    dialog.dismiss();
                });
                return true;
            }
            case 9 -> {
                item.setChecked(!item.isChecked());
                binding.editor.setWordwrap(item.isChecked());
                pref.edit().putBoolean("act_ww", item.isChecked()).apply();
                return true;
            }
            case 10 -> {
                item.setChecked(!item.isChecked());
                binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(item.isChecked());
                pref.edit().putBoolean("act_ac", item.isChecked()).apply();
                return true;
            }
            case 11 -> {
                item.setChecked(!item.isChecked());
                binding.editor.getProps().symbolPairAutoCompletion = item.isChecked();
                pref.edit().putBoolean("act_acsp", item.isChecked()).apply();
                return true;
            }
            default -> { return super.onOptionsItemSelected(item); }
        }
    }
    
    private boolean isFileInLayoutFolder() {
        String contentLocal = getIntent().getStringExtra("content");
        if (contentLocal != null) {
            File file = new File(contentLocal);
            if (contentLocal.contains("/resource/layout/")) {
                String layoutFolder = file.getParent();
                return layoutFolder != null && layoutFolder.endsWith("/resource/layout");
            }
        }
        return false;
    }
}