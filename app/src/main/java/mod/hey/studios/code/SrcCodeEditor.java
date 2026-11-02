package mod.hey.studios.code;

import static pro.sketchware.utility.GsonUtils.getGson;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
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
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;

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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

import mod.hey.studios.code.settings.CodeHighlightingSettingsActivity;
import mod.hey.studios.ide.diagnostics.Diagnostic;
import mod.hey.studios.ide.diagnostics.DiagnosticsAdapter;
import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.hey.studios.util.Helper;
import mod.jbk.code.CodeEditorColorSchemes;
import mod.jbk.code.CodeEditorLanguages.LanguageSpec;
import mod.jbk.code.CodeEditorLanguages;
import mod.sketchlibx.sync.BlockSyncEngine;

import neo.sketchware.plugin.NeoDiagnostic;
import neo.sketchware.plugin.NeoEditorAction;
import neo.sketchware.plugin.NeoEditorContext;
import neo.sketchware.plugin.NeoEditorLanguage;
import neo.sketchware.plugin.NeoEvent;
import neo.sketchware.plugin.PluginManager;

import pro.sketchware.R;
import pro.sketchware.SketchApplication;
import pro.sketchware.activities.preview.LayoutPreviewActivity;
import pro.sketchware.databinding.CodeEditorHsBinding;
import pro.sketchware.databinding.CodeEditorHsOptionsBinding;
import pro.sketchware.databinding.CodeEditorHsSearchPanelBinding;
import pro.sketchware.utility.EditorUtils;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

import a.a.a.Lx;
import a.a.a.ProjectBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

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

    private DrawerLayout drawerLayout;
    private BottomSheetBehavior<LinearLayout> diagnosticsBehavior;
    private TextView tvCursorPos;
    private TextView tvLanguage;
    private TabLayout editorTabs;
    private RecyclerView rvExplorer;

    private CodeEditorHsSearchPanelBinding searchBinding;
    private Menu optionsMenu;
    private ContentListener editorContentListener;
    private boolean isProgrammaticEdit;
    private final Handler stateHandler = new Handler(Looper.getMainLooper());
    private final Runnable stateRefreshRunnable = this::refreshEditorState;
    private boolean stateRefreshPosted;

    private final Handler diagHandler = new Handler(Looper.getMainLooper());
    private Runnable diagRunnable;
    private Runnable pluginChangeRunnable;
    private Runnable cursorEventRunnable;
    private io.github.rosemoe.sora.event.SubscriptionReceipt<io.github.rosemoe.sora.event.SelectionChangeEvent> selectionSubscription;
    // Last position actually published, so CursorChanged/SelectionChanged only
    // fire when the position genuinely differs from what plugins were last told -
    // not on every repaint/no-op event.
    private int lastPublishedCursorLine = Integer.MIN_VALUE, lastPublishedCursorColumn = Integer.MIN_VALUE;
    private int lastPublishedSelStartLine = Integer.MIN_VALUE, lastPublishedSelStartColumn = Integer.MIN_VALUE;
    private int lastPublishedSelEndLine = Integer.MIN_VALUE, lastPublishedSelEndColumn = Integer.MIN_VALUE;

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

        if (loadTheme) {
            selectTheme(ed, theme);
            CodeEditorColorSchemes.applyNativeColorProfile(c, ed.getColorScheme());
        }
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
            CodeEditorColorSchemes.applyNativeColorProfile(ed.getContext(), scheme);
            ed.setColorScheme(scheme);
        }
    }

    public static void selectLanguage(Context context, CodeEditor ed, int which) {
        LanguageSpec spec = LanguageSpec.fromId(which);
        applyLanguageSpec(context, ed, spec);
        languageId = spec.id;
    }

    @Deprecated
    public static void selectLanguage(CodeEditor ed, int which) {
        selectLanguage(SketchApplication.getContext(), ed, which);
    }

    public static void applyLanguageSpec(Context context, CodeEditor ed, LanguageSpec spec) {
        if (context == null || ed == null || spec == null) return;
        switch (spec.kind) {
            case JAVA:
                ed.setEditorLanguage(new JavaLanguage());
                ed.setColorScheme(CodeEditorColorSchemes.loadNativeColorScheme(context));
                break;
            case TEXTMATE:
                ed.setEditorLanguage(CodeEditorLanguages.loadTextMateLanguage(spec.scopeName));
                ed.setColorScheme(CodeEditorColorSchemes.loadTextMateColorScheme(
                        CodeEditorColorSchemes.isDarkAppearance(context)
                                ? CodeEditorColorSchemes.THEME_DRACULA
                                : CodeEditorColorSchemes.THEME_GITHUB));
                break;
            case PLAIN:
            default:
                EditorUtils.loadXmlConfig(ed);
                ed.setColorScheme(CodeEditorColorSchemes.loadNativeColorScheme(context));
                break;
        }
        ed.getColorScheme().setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND,
                CodeEditorColorSchemes.getTokenColor(context, CodeEditorColorSchemes.COLOR_ATTRIBUTE));
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
                Node parent = node.getParentNode();
                boolean structuralWhitespace = false;
                for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                    if (child.getNodeType() == Node.ELEMENT_NODE) {
                        structuralWhitespace = true;
                        break;
                    }
                }
                if (structuralWhitespace) parent.removeChild(node);
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

            return reflowStartTags(result).trim();
        } catch (Exception e) { return null; }
    }

    private static String reflowStartTags(String xml) {
        String[] lines = xml.split("\n", -1);
        StringBuilder formatted = new StringBuilder(xml.length() + 256);
        boolean insideComment = false;
        boolean insideCdata = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            boolean untouched = insideComment || insideCdata;
            int position = 0;
            while (position < line.length()) {
                if (insideComment) {
                    int end = line.indexOf("-->", position);
                    if (end < 0) break;
                    insideComment = false;
                    position = end + 3;
                } else if (insideCdata) {
                    int end = line.indexOf("]]>", position);
                    if (end < 0) break;
                    insideCdata = false;
                    position = end + 3;
                } else {
                    int comment = line.indexOf("<!--", position);
                    int cdata = line.indexOf("<![CDATA[", position);
                    if (comment < 0 && cdata < 0) break;
                    untouched = true;
                    if (cdata < 0 || (comment >= 0 && comment < cdata)) {
                        insideComment = true;
                        position = comment + 4;
                    } else {
                        insideCdata = true;
                        position = cdata + 9;
                    }
                }
            }
            String reflowed = untouched ? null : reflowStartTag(line);
            formatted.append(reflowed == null ? line : reflowed);
            if (index < lines.length - 1) formatted.append('\n');
        }
        return formatted.toString();
    }

    private static String reflowStartTag(String line) {
        String trimmed = line.trim();
        if (!trimmed.startsWith("<") || trimmed.startsWith("</") || trimmed.startsWith("<?") || trimmed.startsWith("<!")) return null;
        int nameEnd = -1;
        for (int i = 1; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '>') return null;
            if (Character.isWhitespace(c)) {
                nameEnd = i;
                break;
            }
        }
        if (nameEnd < 0) return null;
        char quote = 0;
        int tagEnd = -1;
        for (int i = nameEnd; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                tagEnd = i;
                break;
            }
        }
        if (tagEnd != trimmed.length() - 1) return null;
        boolean selfClosing = trimmed.charAt(tagEnd - 1) == '/';
        List<String> attributes = splitAttributes(trimmed.substring(nameEnd + 1, selfClosing ? tagEnd - 1 : tagEnd));
        if (attributes.isEmpty()) return null;
        String baseIndent = " ".repeat(Math.max(0, line.indexOf('<')));
        String attributeIndent = baseIndent + "    ";
        StringBuilder reflowed = new StringBuilder();
        reflowed.append(baseIndent).append(trimmed, 0, nameEnd);
        for (String attribute : attributes) reflowed.append('\n').append(attributeIndent).append(attribute);
        reflowed.append(selfClosing ? " />" : ">");
        return reflowed.toString();
    }

    private static List<String> splitAttributes(String attributes) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < attributes.length(); i++) {
            char c = attributes.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) result.add(current.toString());
        return result;
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
        new MaterialAlertDialogBuilder(activity)
                .setTitle("Select Language")
                .setSingleChoiceItems(LanguageSpec.LANGUAGE_LABELS, languageId, listener)
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
        rvExplorer = findViewById(R.id.nav_view_explorer) instanceof ViewGroup ? new RecyclerView(this) : null;

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

        PluginManager.publishEvent(new NeoEvent.FileOpened(
                scId, currentTitle, NeoEditorLanguage.fromFilePath(currentTitle)));

        selectionSubscription = binding.editor.subscribeEvent(io.github.rosemoe.sora.event.SelectionChangeEvent.class, (event, unsubscribe) -> {
            // Debounced (150ms) so rapid cursor movement (e.g. holding an arrow
            // key, or the cursor advancing on every keystroke while typing)
            // doesn't flood plugins with an event per repaint. Shorter than the
            // 800ms content debounce since cursor feedback should feel responsive.
            if (cursorEventRunnable != null) diagHandler.removeCallbacks(cursorEventRunnable);
            cursorEventRunnable = () -> publishCursorOrSelectionChange();
            diagHandler.postDelayed(cursorEventRunnable, 150);
        });
        
        if (drawerLayout != null) {
            if (isLayoutFile()) {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
                setupProjectExplorer();
            } else {
                drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
                ViewGroup navContainer = findViewById(R.id.nav_view_explorer);
                if (navContainer != null) navContainer.setVisibility(View.GONE);
            }
        } else {
            if (isLayoutFile()) {
                setupProjectExplorer();
            }
        }

        registerContentListener();

        View appBarLayout = findViewById(R.id.app_bar_layout);
        if(appBarLayout != null) UI.addSystemWindowInsetToPadding(appBarLayout, true, true, true, false);
        UI.addSystemWindowInsetToMargin(binding.editor, true, false, true, false);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            int bottomInset = Math.max(
                    windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                    windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom);
            view.setPadding(view.getPaddingLeft(), view.getPaddingTop(), view.getPaddingRight(), bottomInset);
            return windowInsets;
        });
        
        View navView = findViewById(R.id.nav_view_explorer);
        if(navView != null) UI.addSystemWindowInsetToPadding(navView, true, true, true, true);
    }
    
    private void checkAndApplySnippets(int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
        if (insertedContent == null || insertedContent.length() != 1) return;
        char trigger = insertedContent.charAt(0);
        if (trigger != ' ' && trigger != '\n') return;

        try {
            Content content = binding.editor.getText();
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
                    binding.editor.post(() -> {
                        try {
                            int cursorIndex = snippet.indexOf('|');
                            String cleanSnippet = snippet.replace("|", "");
                            
                            binding.editor.getText().delete(startLine, fWordStart, endLine, endColumn);
                            binding.editor.getText().insert(startLine, fWordStart, cleanSnippet);
                            
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
                                binding.editor.setSelection(targetLine, targetColumn);
                            }
                        } catch (Exception e) { e.printStackTrace(); }
                    });
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    private String getSnippetFor(String word) {
        boolean isJava = (languageId == 1) || (currentTitle != null && currentTitle.endsWith(".java"));
        boolean isXml = (currentTitle != null && currentTitle.endsWith(".xml"));

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
				
				case "rv": return "<androidx.recyclerview.widget.RecyclerView\n    android:layout_width=\"match_parent\"\n    android:layout_height=\"match_parent\" />";
				
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
    
    private boolean isLayoutFile() {
        return currentTitle != null && currentTitle.endsWith(".xml") && isFileInLayoutFolder();
    }
    
    /**
     * Reads the current cursor/selection state and publishes CursorChanged or
     * SelectionChanged - but ONLY if it actually differs from the last
     * position published. This is what stops the event from firing on
     * every repaint/no-op SelectionChangeEvent: sora-editor's own event
     * doesn't distinguish "the cursor genuinely moved" from "the selection
     * API was touched with no net change" as cleanly as comparing the
     * actual line/column ourselves does.
     */
    private void publishCursorOrSelectionChange() {
        try {
            io.github.rosemoe.sora.text.Cursor c = binding.editor.getCursor();
            if (c == null) return;

            if (c.isSelected()) {
                int sl = c.getLeftLine(), sc = c.getLeftColumn(), el = c.getRightLine(), ec = c.getRightColumn();
                if (sl == lastPublishedSelStartLine && sc == lastPublishedSelStartColumn
                        && el == lastPublishedSelEndLine && ec == lastPublishedSelEndColumn) {
                    return; // identical to last published selection - skip
                }
                lastPublishedSelStartLine = sl;
                lastPublishedSelStartColumn = sc;
                lastPublishedSelEndLine = el;
                lastPublishedSelEndColumn = ec;
                PluginManager.publishEvent(new NeoEvent.SelectionChanged(
                        scId, currentTitle, sl, sc, el, ec));
            } else {
                int l = c.getRightLine(), col = c.getRightColumn();
                if (l == lastPublishedCursorLine && col == lastPublishedCursorColumn) {
                    return; // identical to last published cursor position - skip
                }
                lastPublishedCursorLine = l;
                lastPublishedCursorColumn = col;
                PluginManager.publishEvent(new NeoEvent.CursorChanged(
                        scId, currentTitle, l, col));
            }
        } catch (Throwable ignored) {
            // Defensive: never let a plugin-event hookup break the actual editor.
        }
    }

    /**
     * Debounced (~800ms) notification to plugins that the file's content
     * changed. Fires the generic TextChanged event for every file type
     * (Java/XML/C-C++), and additionally the older Java-specific
     * JavaCodeChanged for .java files, unchanged, so existing plugins built
     * against it keep working. Reuses diagHandler so it's cancelled together
     * with diagnostics in onStop().
     */
    private void notifyPluginsCodeChanged() {
        boolean isJava = (languageId == 1) || (currentTitle != null && currentTitle.endsWith(".java"));

        if (pluginChangeRunnable != null) diagHandler.removeCallbacks(pluginChangeRunnable);
        pluginChangeRunnable = () -> {
            PluginManager.publishEvent(new NeoEvent.TextChanged(
                    scId, currentTitle, NeoEditorLanguage.fromFilePath(currentTitle)));
            if (isJava) {
                PluginManager.publishEvent(new NeoEvent.JavaCodeChanged(scId, currentTitle));
            }
        };
        diagHandler.postDelayed(pluginChangeRunnable, 800);
    }

    private void triggerRealtimeDiagnostics() {        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        diagRunnable = () -> {
            String text = binding.editor.getText().toString();
            if (currentTitle != null && currentTitle.endsWith(".xml")) {
                new Thread(() -> {
                    List<Diagnostic> diags = new ArrayList<>();
                    try {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        builder.setErrorHandler(new org.xml.sax.ErrorHandler() {
                            @Override public void warning(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.WARNING, currentTitle, e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                            }
                            @Override public void error(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.ERROR, currentTitle, e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                            }
                            @Override public void fatalError(org.xml.sax.SAXParseException e) {
                                diags.add(new Diagnostic(Diagnostic.Severity.ERROR, currentTitle, e.getLineNumber(), e.getColumnNumber(), e.getMessage()));
                            }
                        });
                        builder.parse(new org.xml.sax.InputSource(new StringReader(text)));
                    } catch (Exception e) {
                        if (e instanceof org.xml.sax.SAXParseException) {
                            org.xml.sax.SAXParseException sax = (org.xml.sax.SAXParseException) e;
                            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, currentTitle, sax.getLineNumber(), sax.getColumnNumber(), sax.getMessage()));
                        } else if (diags.isEmpty()) {
                            diags.add(new Diagnostic(Diagnostic.Severity.ERROR, currentTitle, 1, 0, e.getMessage()));
                        }
                    }

                    List<NeoDiagnostic> neoDiags = new ArrayList<>();
                    for (Diagnostic d : diags) {
                        NeoDiagnostic.Severity severity =
                                d.severity == Diagnostic.Severity.ERROR ? NeoDiagnostic.Severity.ERROR
                                        : d.severity == Diagnostic.Severity.WARNING ? NeoDiagnostic.Severity.WARNING
                                        : NeoDiagnostic.Severity.INFO;
                        neoDiags.add(new NeoDiagnostic(severity, d.fileName, d.line, d.column, d.message, "xml"));
                    }
                    PluginManager.publishEvent(new NeoEvent.DiagnosticsChanged(
                            scId, currentTitle, NeoEditorLanguage.XML, neoDiags));

                    runOnUiThread(() -> {
                        if (diagnosticsBehavior != null && !diags.isEmpty()) {
                            showDiagnostics(diags);
                        } else if (diagnosticsBehavior != null && diags.isEmpty()) {
                            diagnosticsBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
                        }
                    });
                }).start();
            }
        };
        diagHandler.postDelayed(diagRunnable, 800);
    }

    private void applySyntaxHighlighting() {
        if (currentTitle == null) return;
        LanguageSpec spec = CodeEditorLanguages.resolveLanguageSpec(currentTitle);
        applyLanguageSpec(this, binding.editor, spec);
        languageId = spec.id;
        if (tvLanguage != null) tvLanguage.setText(spec.label);
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(currentTitle);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            if (isLayoutFile() && drawerLayout != null) {
                getSupportActionBar().setHomeAsUpIndicator(R.drawable.ic_mtrl_menu); 
            }
        }
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (drawerLayout != null && isLayoutFile()) {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) drawerLayout.closeDrawer(GravityCompat.START);
                else drawerLayout.openDrawer(GravityCompat.START);
            } else {
                onBackPressed();
            }
        });
    }

    private void setupTabs() {
        if(editorTabs != null) {
            editorTabs.removeAllTabs();
            editorTabs.addTab(editorTabs.newTab().setText(currentTitle != null ? currentTitle : "File"));
        }
    }

    private void setupProjectExplorer() {
        if (rvExplorer == null) return;
        rvExplorer.setLayoutManager(new LinearLayoutManager(this));
        
        ViewGroup navView = findViewById(R.id.nav_view_explorer);
        if (navView != null) {
            navView.removeAllViews();
            navView.addView(rvExplorer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            
            File layoutFolder = new File(FileUtil.getExternalStorageDir() + "/.sketchware/data/" + scId + "/files/resource/layout/");
            if (layoutFolder.exists()) {
                FileTreeAdapter adapter = new FileTreeAdapter(layoutFolder, file -> {
                    if (isContentModified()) save();
                    
                    currentTitle = file.getName();
                    beforeContent = FileUtil.readFile(file.getAbsolutePath());
                    binding.editor.setText(beforeContent);
                    registerContentListener();
                    
                    getIntent().putExtra("content", file.getAbsolutePath());
                    getIntent().putExtra("title", currentTitle);
                    
                    applySyntaxHighlighting();
                    if (getSupportActionBar() != null) getSupportActionBar().setTitle(currentTitle);
                    setupTabs();
                    if(drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
                    updateMenuState();
                });
                rvExplorer.setAdapter(adapter);
            }
        }
    }

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
            root.setGravity(Gravity.CENTER_VERTICAL);
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
        RecyclerView rv = findViewById(R.id.rv_diagnostics);
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

    private static final class SearchMatch {
        final int start;
        final int end;
        final String replacement;

        SearchMatch(int start, int end, String replacement) {
            this.start = start;
            this.end = end;
            this.replacement = replacement;
        }
    }

    private void registerContentListener() {
        Content text = binding.editor.getText();
        if (editorContentListener == null) {
            editorContentListener = new ContentListener() {
                @Override public void beforeReplace(Content content) {}
                @Override public void afterDelete(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence deletedContent) {
                    scheduleEditorStateRefresh();
                    triggerRealtimeDiagnostics();
                    notifyPluginsCodeChanged();
                }
                @Override public void afterInsert(Content content, int startLine, int startColumn, int endLine, int endColumn, CharSequence insertedContent) {
                    scheduleEditorStateRefresh();
                    triggerRealtimeDiagnostics();
                    notifyPluginsCodeChanged();
                    if (!isProgrammaticEdit) checkAndApplySnippets(startLine, startColumn, endLine, endColumn, insertedContent);
                }
            };
        }
        text.removeContentListener(editorContentListener);
        text.addContentListener(editorContentListener);
    }

    private boolean isContentModified() {
        return !beforeContent.equals(binding.editor.getText().toString());
    }

    private void setupSearchPanel() {
        ViewGroup rootView = (ViewGroup) binding.getRoot();
        searchBinding = CodeEditorHsSearchPanelBinding.inflate(getLayoutInflater(), rootView, false);
        rootView.addView(searchBinding.searchCard);
        UI.addSystemWindowInsetToMargin(searchBinding.searchCard, true, false, true, false);

        searchBinding.btnPrev.setOnClickListener(v -> gotoSearchMatch(false));
        searchBinding.btnNext.setOnClickListener(v -> gotoSearchMatch(true));
        searchBinding.btnCloseSearch.setOnClickListener(v -> hideSearchPanel());
        searchBinding.btnReplace.setOnClickListener(v -> replaceCurrentSearchMatch());
        searchBinding.btnReplaceAll.setOnClickListener(v -> replaceAllSearchMatches());
        searchBinding.findEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { updateFindResults(); }
        });
        searchBinding.findEdit.setOnEditorActionListener((view, actionId, event) -> {
            boolean enterPressed = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                gotoSearchMatch(true);
                return true;
            }
            return false;
        });
        searchBinding.searchOptionsGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> updateFindResults());
    }

    private void openSearchPanel() {
        if (searchBinding == null) return;
        searchBinding.searchCard.setVisibility(View.VISIBLE);
        String selected = getSelectedSingleLineText();
        if (!selected.isEmpty()) {
            searchBinding.findEdit.setText(selected);
            searchBinding.findEdit.setSelection(selected.length());
        }
        updateFindResults();
        searchBinding.findEdit.requestFocus();
        searchBinding.findEdit.selectAll();
        showKeyboard(searchBinding.findEdit);
    }

    private void hideSearchPanel() {
        if (searchBinding == null) return;
        try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
        hideKeyboard(searchBinding.findEdit);
        searchBinding.searchCard.setVisibility(View.GONE);
        searchBinding.findEdit.setText("");
        binding.editor.requestFocus();
    }

    private String getSelectedSingleLineText() {
        try {
            io.github.rosemoe.sora.text.Cursor cursor = binding.editor.getCursor();
            if (!cursor.isSelected()) return "";
            CharSequence selected = binding.editor.getText().subSequence(cursor.getLeft(), cursor.getRight());
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

    private String currentQuery() {
        return searchBinding == null || searchBinding.findEdit.getText() == null ? "" : searchBinding.findEdit.getText().toString();
    }

    private String buildSearchExpression() {
        String query = currentQuery();
        boolean regex = searchBinding.btnRegex.isChecked();
        String expression = regex ? query : Pattern.quote(query);
        if (searchBinding.btnWholeWord.isChecked()) expression = "\\b(?:" + expression + ")\\b";
        return expression;
    }

    private Pattern buildSearchPattern() {
        int flags = searchBinding.btnMatchCase.isChecked() ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        if (searchBinding.btnRegex.isChecked()) flags |= Pattern.MULTILINE;
        return Pattern.compile(buildSearchExpression(), flags);
    }

    private void runEditorSearch() {
        boolean regexMode = searchBinding.btnRegex.isChecked() || searchBinding.btnWholeWord.isChecked();
        String expression = regexMode ? buildSearchExpression() : currentQuery();
        int type = regexMode ? EditorSearcher.SearchOptions.TYPE_REGULAR_EXPRESSION : EditorSearcher.SearchOptions.TYPE_NORMAL;
        binding.editor.getSearcher().search(expression, new EditorSearcher.SearchOptions(type, !searchBinding.btnMatchCase.isChecked()));
    }

    private void updateFindResults() {
        if (searchBinding == null) return;
        if (currentQuery().isEmpty()) {
            try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
            searchBinding.searchCountView.setText("0 matches");
            return;
        }
        try {
            Pattern pattern = buildSearchPattern();
            runEditorSearch();
            Matcher matcher = pattern.matcher(binding.editor.getText().toString());
            int count = 0;
            while (matcher.find()) {
                if (matcher.start() != matcher.end()) count++;
            }
            searchBinding.searchCountView.setText(count + (count == 1 ? " match" : " matches"));
        } catch (Exception e) {
            try { binding.editor.getSearcher().stopSearch(); } catch (Exception ignored) {}
            searchBinding.searchCountView.setText("Invalid pattern");
        }
    }

    private void gotoSearchMatch(boolean next) {
        if (currentQuery().isEmpty()) return;
        try {
            if (next) binding.editor.getSearcher().gotoNext();
            else binding.editor.getSearcher().gotoPrevious();
        } catch (Exception ignored) {}
    }

    private List<SearchMatch> findSearchMatches() {
        Pattern pattern = buildSearchPattern();
        String template = searchBinding.replaceEdit.getText() == null ? "" : searchBinding.replaceEdit.getText().toString();
        if (!searchBinding.btnRegex.isChecked()) template = Matcher.quoteReplacement(template);
        String text = binding.editor.getText().toString();
        Matcher matcher = pattern.matcher(text);
        StringBuffer buffer = new StringBuffer();
        List<SearchMatch> matches = new ArrayList<>();
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) continue;
            int before = buffer.length();
            matcher.appendReplacement(buffer, template);
            String appended = buffer.substring(before);
            matches.add(new SearchMatch(matcher.start(), matcher.end(), appended.substring(matcher.start() - lastEnd)));
            lastEnd = matcher.end();
        }
        return matches;
    }

    private void replaceCurrentSearchMatch() {
        if (currentQuery().isEmpty()) return;
        try {
            List<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            int left = binding.editor.getCursor().getLeft();
            int right = binding.editor.getCursor().getRight();
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
            Content text = binding.editor.getText();
            isProgrammaticEdit = true;
            try {
                text.replace(target.start, target.end, target.replacement);
            } finally {
                isProgrammaticEdit = false;
            }
            int caret = Math.min(target.start + target.replacement.length(), text.length());
            io.github.rosemoe.sora.text.CharPosition position = text.getIndexer().getCharPosition(caret);
            binding.editor.setSelection(position.line, position.column);
            updateMenuState();
            updateFindResults();
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace match: " + error.getMessage());
        }
    }

    private void replaceAllSearchMatches() {
        if (currentQuery().isEmpty()) return;
        try {
            List<SearchMatch> matches = findSearchMatches();
            if (matches.isEmpty()) {
                SketchwareUtil.toast("No matches to replace");
                return;
            }
            Content text = binding.editor.getText();
            isProgrammaticEdit = true;
            try {
                text.beginBatchEdit();
                try {
                    for (int i = matches.size() - 1; i >= 0; i--) {
                        SearchMatch match = matches.get(i);
                        text.replace(match.start, match.end, match.replacement);
                    }
                } finally {
                    text.endBatchEdit();
                }
            } finally {
                isProgrammaticEdit = false;
            }
            updateMenuState();
            updateFindResults();
            SketchwareUtil.toast("Replaced " + matches.size() + (matches.size() == 1 ? " occurrence" : " occurrences"));
        } catch (Exception error) {
            SketchwareUtil.toastError("Could not replace matches: " + error.getMessage());
        }
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
                        updateMenuState();
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
            String filePath = getIntent().getStringExtra("content");
            if (filePath != null && filePath.endsWith(".java")) {
                try {
                    BlockSyncEngine.syncJavaToBlocks(scId, activityName, beforeContent);
                } catch (Exception e) {
                    SketchwareUtil.toastError("Sync failed: " + e.getMessage());
                }
            }
            FileUtil.writeFile(filePath, beforeContent);
            if (filePath != null && filePath.endsWith(".java")) {
                PluginManager.publishEvent(new NeoEvent.JavaCodeSaved(scId, filePath));
            }
            PluginManager.publishEvent(new NeoEvent.FileSaved(
                    scId, filePath, NeoEditorLanguage.fromFilePath(filePath)));
        }
        SketchwareUtil.toast("Saved successfully!");
        updateMenuState();
    }
    
        @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(ProjectBuilder.ACTION_BUILD_DIAGNOSTICS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(buildDiagnosticsReceiver, filter, Context.RECEIVER_NOT_EXPORTED); 
        } else {
            registerReceiver(buildDiagnosticsReceiver, filter); 
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try { unregisterReceiver(buildDiagnosticsReceiver); } catch (Exception ignored){}
        if (diagRunnable != null) diagHandler.removeCallbacks(diagRunnable);
        if (pluginChangeRunnable != null) diagHandler.removeCallbacks(pluginChangeRunnable);
        if (cursorEventRunnable != null) diagHandler.removeCallbacks(cursorEventRunnable);
        PluginManager.publishEvent(new NeoEvent.FileClosed(
                scId, currentTitle, NeoEditorLanguage.fromFilePath(currentTitle)));
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        pref.edit().putInt("act_ts", (int) (binding.editor.getTextSizePx() / scaledDensity)).apply();
    }

    @Override
    public void onDestroy() {
        stateHandler.removeCallbacks(stateRefreshRunnable);
        super.onDestroy();
        if (selectionSubscription != null) { try { selectionSubscription.unsubscribe(); } catch (Throwable ignored) {} }
        try { binding.editor.release(); } catch (Throwable ignored) {}
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
            return;
        }
        if (searchBinding != null && searchBinding.searchCard.getVisibility() == View.VISIBLE) {
            hideSearchPanel();
            return;
        }

        if (!isContentModified()) {
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

        if (isLayoutFile() && getIntent().hasExtra("sc_id")) {
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

    private void showPluginActionsDialog() {
        java.util.List<NeoEditorAction> actions = PluginManager.getAllEditorActions();
        if (actions.isEmpty()) return;

        String[] labels = new String[actions.size()];
        for (int i = 0; i < actions.size(); i++) labels[i] = actions.get(i).label();

        new MaterialAlertDialogBuilder(this)
                .setTitle("Plugin Actions")
                .setItems(labels, (dialog, which) -> runPluginAction(actions.get(which)))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runPluginAction(NeoEditorAction action) {
        String fullText = binding.editor.getText().toString();
        NeoEditorContext context =
                new NeoEditorContext(currentTitle, fullText, 0, fullText.length());

        String replacement;
        try {
            replacement = action.callback().onInvoke(context);
        } catch (Throwable t) {
            SketchwareUtil.toastError("Plugin action failed: " + t.getMessage());
            return;
        }

        if (replacement != null && !replacement.equals(fullText)) {
            replaceEditorContentKeepingHistory(replacement);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        optionsMenu = menu;
        updateMenuState();
        return super.onPrepareOptionsMenu(menu);
    }

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
        if (optionsMenu == null) return;
        boolean canUndo = false;
        boolean canRedo = false;
        try { canUndo = binding.editor.canUndo(); } catch (Exception ignored) {}
        try { canRedo = binding.editor.canRedo(); } catch (Exception ignored) {}
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
        boolean hadUndo = binding.editor.canUndo();
        if (hadUndo) {
            binding.editor.undo();
            PluginManager.publishEvent(new NeoEvent.EditorUndo(scId, currentTitle));
        }
        updateMenuState();
        scheduleEditorStateRefresh();
    }

    private void performRedo() {
        boolean hadRedo = binding.editor.canRedo();
        if (hadRedo) {
            binding.editor.redo();
            PluginManager.publishEvent(new NeoEvent.EditorRedo(scId, currentTitle));
        }
        updateMenuState();
        scheduleEditorStateRefresh();
    }

    private void replaceEditorContentKeepingHistory(String newContent) {
        Content text = binding.editor.getText();
        int cursorLine = binding.editor.getCursor().getLeftLine();
        int cursorColumn = binding.editor.getCursor().getLeftColumn();
        isProgrammaticEdit = true;
        try {
            text.beginBatchEdit();
            try {
                text.replace(0, text.length(), newContent);
            } finally {
                text.endBatchEdit();
            }
        } finally {
            isProgrammaticEdit = false;
        }
        int line = Math.max(0, Math.min(cursorLine, text.getLineCount() - 1));
        int column = Math.max(0, Math.min(cursorColumn, text.getLineString(line).length()));
        binding.editor.setSelection(line, column);
        updateMenuState();
        updateFindResults();
    }

    private void formatCurrentFile() {
        String original = binding.editor.getText().toString();
        boolean isJava = getIntent().hasExtra("java") || (currentTitle != null && currentTitle.endsWith(".java"));
        boolean isXml = getIntent().hasExtra("xml") || (currentTitle != null && currentTitle.endsWith(".xml"));
        String formatted;
        String successMessage;
        if (isJava) {
            try {
                formatted = Lx.j(original, true);
            } catch (Exception e) {
                SketchwareUtil.toastError("Your code contains incorrectly nested parentheses");
                return;
            }
            successMessage = "Code Formatted!";
        } else if (isXml) {
            formatted = prettifyXml(original, 4, getIntent());
            if (formatted == null) {
                SketchwareUtil.toastError("Failed to format XML file");
                return;
            }
            successMessage = "XML Formatted!";
        } else {
            SketchwareUtil.toast("Only Java and XML files can be formatted");
            return;
        }
        if (formatted.equals(original)) {
            SketchwareUtil.toast("Already formatted");
            return;
        }
        replaceEditorContentKeepingHistory(formatted);
        SketchwareUtil.toast(successMessage);
    }

    private boolean dispatchOption(int optionId) {
        switch (optionId) {
            case 12 -> {
                if (drawerLayout != null) drawerLayout.openDrawer(GravityCompat.START);
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
                Intent intent = new Intent(getApplicationContext(), LayoutPreviewActivity.class);
                intent.putExtras(getIntent());
                intent.putExtra("xml", binding.editor.getText().toString());
                startActivity(intent);
                return true;
            }
            case 7 -> {
                showSwitchLanguageDialog(this, binding.editor, (dialog, which) -> { selectLanguage(this, binding.editor, which); dialog.dismiss(); });
                return true;
            }
            case 8 -> {
                // Keep the legacy Theme entry routed to the shared profile editor so
                // it cannot override the new global palette with an older Sora scheme.
                startActivity(new Intent(this, CodeHighlightingSettingsActivity.class));
                return true;
            }
            case 9 -> {
                showSnippetHelpDialog();
                return true;
            }
            case 10 -> {
                if (!isLayoutFile() || scId == null || scId.isEmpty()) return true;
                if (isContentModified()) save();
                Intent designIntent = new Intent(this, DesignActivity.class);
                designIntent.putExtra("sc_id", scId);
                designIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(designIntent);
                finish();
                return true;
            }
            case 13 -> {
                showPluginActionsDialog();
                return true;
            }
            case 14 -> {
                startActivity(new Intent(this, CodeHighlightingSettingsActivity.class));
                return true;
            }
            default -> { return false; }
        }
    }

    private void showMoreOptionsSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        CodeEditorHsOptionsBinding sheet = CodeEditorHsOptionsBinding.inflate(getLayoutInflater());
        sheet.optExplorer.setVisibility(View.GONE);
        sheet.optPreview.setVisibility(View.GONE);
        sheet.optPlugins.setVisibility(PluginManager.getAllEditorActions().isEmpty() ? View.GONE : View.VISIBLE);
        sheet.optTheme.setVisibility(View.GONE);
        compactOptionGrid(sheet.getRoot());
        bindOptionTile(dialog, sheet.optExplorer, 12);
        bindOptionTile(dialog, sheet.optFind, 4);
        bindOptionTile(dialog, sheet.optFormat, 5);
        bindOptionTile(dialog, sheet.optPreview, 6);
        bindOptionTile(dialog, sheet.optLanguage, 7);
        bindOptionTile(dialog, sheet.optTheme, 8);
        bindOptionTile(dialog, sheet.optHighlighting, 14);
        bindOptionTile(dialog, sheet.optPlugins, 13);
        bindOptionTile(dialog, sheet.optAbout, 9);
        sheet.switchWordWrap.setChecked(pref.getBoolean("act_ww", false));
        sheet.switchWordWrap.setOnCheckedChangeListener((button, checked) -> {
            binding.editor.setWordwrap(checked);
            pref.edit().putBoolean("act_ww", checked).apply();
        });
        sheet.switchAutoComplete.setChecked(pref.getBoolean("act_ac", true));
        sheet.switchAutoComplete.setOnCheckedChangeListener((button, checked) -> {
            binding.editor.getComponent(EditorAutoCompletion.class).setEnabled(checked);
            pref.edit().putBoolean("act_ac", checked).apply();
        });
        sheet.switchSymbolPair.setChecked(pref.getBoolean("act_acsp", true));
        sheet.switchSymbolPair.setOnCheckedChangeListener((button, checked) -> {
            binding.editor.getProps().symbolPairAutoCompletion = checked;
            pref.edit().putBoolean("act_acsp", checked).apply();
        });
        dialog.setContentView(sheet.getRoot());
        dialog.show();
    }

    private void showSnippetHelpDialog() {
        boolean xml = currentTitle != null && currentTitle.toLowerCase(Locale.ROOT).endsWith(".xml");
        String shortcuts = xml
                ? "XML layout snippets\n\n"
                        + "ll — LinearLayout\nrl — RelativeLayout\ntv — TextView\nbtn — Button\niv — ImageView\ncl — ConstraintLayout\nsv — ScrollView\nhsv — HorizontalScrollView\net — EditText\ncb — CheckBox\nrb — RadioButton\nrg — RadioGroup\nsw — Switch\npb — ProgressBar\nseek — SeekBar\nrating — RatingBar\nspinner — Spinner\nlv — ListView\ngv — GridView\nrv — RecyclerView\ncard — CardView\nspace — Space\nview — Divider View\ninclude — Include layout\nmerge — Merge root\nfrag — Fragment"
                : "Java snippets\n\n"
                        + "toast — Create a Toast message\nlogd / loge — Add debug or error logs\nintent — Create an Intent and start an Activity\nfori — Create an indexed for loop\nif / ifelse — Create condition blocks\ntryc — Create a try-catch block\nfind — Find a view by its ID\net / tvf / btnf / ivf — Create common view references\nonclick — Add a click listener\nthread / runui / handler — Run delayed or UI-thread code\narray / hashmap — Create collection objects\ndialog — Create an AlertDialog\ndate / time — Format date or time\nshared / prefput / prefget — Use SharedPreferences\nvisible / gone / invisible — Change view visibility\ntext / gettext / hint — Read or update view text\nimg / bg / color — Change image, background or text color\nparse / str — Convert values\nlen / trim / upper / lower — Common String operations\nsleep / finish / finisha / back — Common flow helpers\n\nMore Java shortcuts: "
                        + "All Java shortcuts: toast, logd, loge, intent, fori, psfs, if, ifelse, tryc, find, "
                        + "et, tvf, btnf, ivf, cbf, rbf, swf, spf, rvf, lvf, gvf, cvf, pbf, fabf, "
                        + "onclick, thread, runui, handler, finish, finisha, back, random, array, hashmap, dialog, "
                        + "date, time, switch, while, dowhile, foreach, return, newobj, bundle, putextra, getextra, "
                        + "shared, editor, commit, apply, prefput, prefget, anim, post, visible, gone, invisible, "
                        + "enable, disable, text, gettext, hint, focus, clear, img, bg, color, parse, str, equals, "
                        + "isempty, len, trim, upper, lower, contains, replace, split, sleep, exit";

        TextView content = new TextView(this);
        content.setText(shortcuts + "\n\nType a shortcut and press Space or Enter to insert its code. The | marker shows where the cursor will be placed.");
        content.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        content.setTextSize(14f);
        content.setLineSpacing(SketchwareUtil.dpToPx(3), 1.05f);
        int padding = SketchwareUtil.dpToPx(20);
        content.setPadding(padding, padding, padding, padding);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(content);
        new MaterialAlertDialogBuilder(this)
                .setTitle("Editor shortcuts")
                .setView(scrollView)
                .setPositiveButton(R.string.common_word_close, null)
                .show();
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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case 0 -> { performUndo(); return true; }
            case 1 -> { performRedo(); return true; }
            case 2 -> {
                if (isContentModified()) save();
                else SketchwareUtil.toast("No changes to save");
                return true;
            }
            case 17 -> { dispatchOption(6); return true; }
            case 18 -> { dispatchOption(10); return true; }
            case 16 -> { showMoreOptionsSheet(); return true; }
            default -> { return dispatchOption(item.getItemId()) || super.onOptionsItemSelected(item); }
        }
    }
    

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null && binding.editor != null && currentTitle != null) {
            // Keep a manually selected language while refreshing only the palette.
            applyLanguageSpec(this, binding.editor, LanguageSpec.fromId(languageId));
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
