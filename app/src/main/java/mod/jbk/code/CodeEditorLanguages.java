package mod.jbk.code;

import io.github.rosemoe.sora.lang.EmptyLanguage;
import io.github.rosemoe.sora.lang.Language;
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import mod.jbk.util.LogUtil;
import pro.sketchware.SketchApplication;

/**
 * Central registry for all TextMate grammar languages used by Sketchware Neo.
 *
 * <h3>Adding a new language</h3>
 * <ol>
 *   <li>Add a {@code SCOPE_NAME_*} constant.</li>
 *   <li>Add a {@code LANG_*} id constant inside {@link LanguageSpec}.</li>
 *   <li>Add the grammar + language-configuration to {@code assets/textmate/}.</li>
 *   <li>Register the grammar in {@code assets/textmate/languages.json}.</li>
 *   <li>Add a {@code resolveLanguageSpec} branch for the file extension(s).</li>
 *   <li>Add a case in {@code LanguageSpec.fromId()}.</li>
 * </ol>
 */
public class CodeEditorLanguages {

    // ── Scope name constants ───────────────────────────────────────────────────
    // Keep these in sync with the scopeName fields in languages.json.

    /** Java — handled by Sora's built-in JavaLanguage, not TextMate. */
    public static final String SCOPE_NAME_JAVA       = "source.java";
    /** Kotlin and Gradle Kotlin DSL (.gradle.kts). */
    public static final String SCOPE_NAME_KOTLIN     = "source.kotlin";
    /** Android / generic XML. */
    public static final String SCOPE_NAME_XML        = "text.xml";
    /** JSON data files. */
    public static final String SCOPE_NAME_JSON       = "source.json";
    /** Groovy — used for .gradle files. */
    public static final String SCOPE_NAME_GROOVY     = "source.groovy";
    /** C source files. */
    public static final String SCOPE_NAME_C          = "source.c";
    /** C++ source and header files (.cpp, .cc, .cxx, .h, .hpp, .hxx). */
    public static final String SCOPE_NAME_CPP        = "source.cpp";
    /** Unix shell scripts. */
    public static final String SCOPE_NAME_SHELL      = "source.shell";
    /** CMake list files and .cmake scripts. */
    public static final String SCOPE_NAME_CMAKE      = "source.cmake";
    /** Markdown documents. */
    public static final String SCOPE_NAME_MARKDOWN   = "text.html.markdown";
    /** Java .properties / generic INI files. */
    public static final String SCOPE_NAME_PROPERTIES = "source.ini";

    private static final String TAG = "CodeEditorLanguages";

    // Static initializer: run once per process. Sets up the asset resolver and
    // loads all language registrations from languages.json.
    static {
        try {
            FileProviderRegistry.getInstance().addFileProvider(
                    new AssetsFileResolver(SketchApplication.getContext().getAssets()));
        } catch (Exception e) {
            LogUtil.e(TAG, "Failed to register asset file provider", e);
        }
        try {
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json");
        } catch (Exception | NoSuchMethodError e) {
            LogUtil.e(TAG, "Failed to load grammars from languages.json — " +
                    "syntax highlighting will be unavailable for TextMate languages", e);
        }
    }

    // ── LanguageSpec ───────────────────────────────────────────────────────────

    /**
     * Describes how a {@link io.github.rosemoe.sora.widget.CodeEditor} should be
     * configured for a specific file.
     *
     * <p>Obtain instances via {@link #resolveLanguageSpec(String)} or
     * {@link #fromId(int)}.
     */
    public static final class LanguageSpec {

        // ── Language ID constants ──────────────────────────────────────────────
        // These integers are:
        //   • stored in SrcCodeEditor.languageId
        //   • used as positions in showSwitchLanguageDialog()
        //   • used as switch keys in selectLanguage()
        // Do NOT reorder existing IDs; add new ones at the end.

        public static final int LANG_JAVA        = 0;   // Sora JavaLanguage
        public static final int LANG_KOTLIN      = 1;   // TextMate source.kotlin
        public static final int LANG_XML         = 2;   // TextMate text.xml
        public static final int LANG_JSON        = 3;   // TextMate source.json
        public static final int LANG_GROOVY      = 4;   // TextMate source.groovy (.gradle)
        public static final int LANG_KOTLIN_DSL  = 5;   // TextMate source.kotlin (.gradle.kts)
        public static final int LANG_C           = 6;   // TextMate source.c
        public static final int LANG_CPP         = 7;   // TextMate source.cpp
        public static final int LANG_SHELL       = 8;   // TextMate source.shell
        public static final int LANG_CMAKE       = 9;   // TextMate source.cmake
        public static final int LANG_MARKDOWN    = 10;  // TextMate text.html.markdown
        public static final int LANG_PROPERTIES  = 11;  // TextMate source.ini
        public static final int LANG_PLAIN       = 12;  // No TextMate grammar

        // ── Display labels for the Switch Language dialog ──────────────────────
        // Indexed by LANG_* constants. Used in showSwitchLanguageDialog().
        public static final String[] LANGUAGE_LABELS = {
                "Java",                 // 0
                "Kotlin",              // 1
                "XML",                 // 2
                "JSON",                // 3
                "Gradle (Groovy)",     // 4
                "Gradle Kotlin DSL",   // 5
                "C",                   // 6
                "C++",                 // 7
                "Shell Script",        // 8
                "CMake",               // 9
                "Markdown",            // 10
                "Properties",          // 11
                "Plain Text"           // 12
        };

        // ── Kind: how the editor should be configured ──────────────────────────

        /** Which editor engine to use for this language. */
        public enum Kind {
            /** Use Sora's built-in {@code JavaLanguage} (best Java support). */
            JAVA,
            /** Use a TextMate grammar via {@code TextMateLanguage.create(scopeName)}. */
            TEXTMATE,
            /** No grammar — load editor config only (EditorUtils.loadXmlConfig). */
            PLAIN
        }

        // ── Fields ────────────────────────────────────────────────────────────

        /** Numeric language identifier. Matches {@code LANG_*} constants. */
        public final int  id;
        /** Determines how the editor language is applied. */
        public final Kind kind;
        /** TextMate scope name. Non-null only when {@code kind == TEXTMATE}. */
        public final String scopeName;
        /** Human-readable label shown in {@code tvLanguage} and the language dialog. */
        public final String label;

        // ── Factory methods ────────────────────────────────────────────────────

        private LanguageSpec(int id, Kind kind, String scopeName, String label) {
            this.id        = id;
            this.kind      = kind;
            this.scopeName = scopeName;
            this.label     = label;
        }

        static LanguageSpec java() {
            return new LanguageSpec(LANG_JAVA, Kind.JAVA, null, "Java");
        }

        static LanguageSpec textmate(int id, String scope, String label) {
            return new LanguageSpec(id, Kind.TEXTMATE, scope, label);
        }

        static LanguageSpec plain() {
            return new LanguageSpec(LANG_PLAIN, Kind.PLAIN, null, "Plain Text");
        }

        /**
         * Returns the {@link LanguageSpec} for a numeric language ID.
         * Used by {@code selectLanguage()} to convert a dialog selection to a spec.
         * Returns {@link #plain()} for any unrecognised ID.
         */
        public static LanguageSpec fromId(int id) {
            switch (id) {
                case LANG_JAVA:       return java();
                case LANG_KOTLIN:     return textmate(LANG_KOTLIN,     SCOPE_NAME_KOTLIN,     "Kotlin");
                case LANG_XML:        return textmate(LANG_XML,         SCOPE_NAME_XML,        "XML");
                case LANG_JSON:       return textmate(LANG_JSON,        SCOPE_NAME_JSON,       "JSON");
                case LANG_GROOVY:     return textmate(LANG_GROOVY,      SCOPE_NAME_GROOVY,     "Gradle (Groovy)");
                case LANG_KOTLIN_DSL: return textmate(LANG_KOTLIN_DSL,  SCOPE_NAME_KOTLIN,     "Gradle Kotlin DSL");
                case LANG_C:          return textmate(LANG_C,           SCOPE_NAME_C,          "C");
                case LANG_CPP:        return textmate(LANG_CPP,         SCOPE_NAME_CPP,        "C++");
                case LANG_SHELL:      return textmate(LANG_SHELL,       SCOPE_NAME_SHELL,      "Shell Script");
                case LANG_CMAKE:      return textmate(LANG_CMAKE,       SCOPE_NAME_CMAKE,      "CMake");
                case LANG_MARKDOWN:   return textmate(LANG_MARKDOWN,    SCOPE_NAME_MARKDOWN,   "Markdown");
                case LANG_PROPERTIES: return textmate(LANG_PROPERTIES,  SCOPE_NAME_PROPERTIES, "Properties");
                default:              return plain();
            }
        }
    }

    // ── Language detection ─────────────────────────────────────────────────────

    /**
     * Resolves the appropriate {@link LanguageSpec} for a file by its name.
     *
     * <p>Matching is case-insensitive. The {@code fileName} may be a bare file
     * name ({@code "MyClass.java"}) or a full path.
     *
     * <h3>Extension priority rules</h3>
     * <ol>
     *   <li>{@code .gradle.kts} is checked <em>before</em> {@code .kts} to
     *       distinguish Gradle Kotlin DSL from plain Kotlin Script.</li>
     *   <li>{@code CMakeLists.txt} (base name, case-insensitive) is checked
     *       <em>before</em> the generic {@code .txt} fallback.</li>
     *   <li>{@code .h} maps to C++ header ({@code source.cpp}) — consistent with
     *       VS Code default behaviour. If your project mixes C and C++ headers,
     *       the user can override via "Select language" in the overflow menu.</li>
     * </ol>
     *
     * @param fileName  The file name or full path to resolve.
     * @return          A non-null {@link LanguageSpec} describing how the editor
     *                  should be configured. Never returns {@code null}.
     */
    public static LanguageSpec resolveLanguageSpec(String fileName) {
        if (fileName == null || fileName.isEmpty()) return LanguageSpec.plain();

        final String lower = fileName.toLowerCase();

        // ── Compound extensions (order is critical) ────────────────────────────
        if (lower.endsWith(".gradle.kts")) {
            // Gradle Kotlin DSL uses Kotlin grammar — distinct label
            return LanguageSpec.textmate(
                    LanguageSpec.LANG_KOTLIN_DSL, SCOPE_NAME_KOTLIN, "Gradle Kotlin DSL");
        }

        // ── CMakeLists.txt — special-cased before generic .txt ─────────────────
        final String baseName = lower.contains("/")
                ? lower.substring(lower.lastIndexOf('/') + 1)
                : lower;
        if (baseName.equals("cmakelists.txt") || lower.endsWith(".cmake")) {
            return LanguageSpec.textmate(
                    LanguageSpec.LANG_CMAKE, SCOPE_NAME_CMAKE, "CMake");
        }

        // ── Single extensions ──────────────────────────────────────────────────
        if (lower.endsWith(".java"))        return LanguageSpec.java();
        if (lower.endsWith(".kt")
         || lower.endsWith(".kts"))         return LanguageSpec.textmate(
                                                LanguageSpec.LANG_KOTLIN, SCOPE_NAME_KOTLIN, "Kotlin");
        if (lower.endsWith(".xml"))         return LanguageSpec.textmate(
                                                LanguageSpec.LANG_XML, SCOPE_NAME_XML, "XML");
        if (lower.endsWith(".json"))        return LanguageSpec.textmate(
                                                LanguageSpec.LANG_JSON, SCOPE_NAME_JSON, "JSON");
        if (lower.endsWith(".gradle"))      return LanguageSpec.textmate(
                                                LanguageSpec.LANG_GROOVY, SCOPE_NAME_GROOVY, "Gradle (Groovy)");
        if (lower.endsWith(".c"))           return LanguageSpec.textmate(
                                                LanguageSpec.LANG_C, SCOPE_NAME_C, "C");
        if (lower.endsWith(".cpp")
         || lower.endsWith(".cc")
         || lower.endsWith(".cxx"))         return LanguageSpec.textmate(
                                                LanguageSpec.LANG_CPP, SCOPE_NAME_CPP, "C++");
        // .h maps to C++ — covers both C and C++ headers; consistent with VS Code.
        if (lower.endsWith(".h"))           return LanguageSpec.textmate(
                                                LanguageSpec.LANG_CPP, SCOPE_NAME_CPP, "C/C++ Header");
        if (lower.endsWith(".hpp")
         || lower.endsWith(".hxx"))         return LanguageSpec.textmate(
                                                LanguageSpec.LANG_CPP, SCOPE_NAME_CPP, "C++ Header");
        if (lower.endsWith(".sh"))          return LanguageSpec.textmate(
                                                LanguageSpec.LANG_SHELL, SCOPE_NAME_SHELL, "Shell Script");
        if (lower.endsWith(".md"))          return LanguageSpec.textmate(
                                                LanguageSpec.LANG_MARKDOWN, SCOPE_NAME_MARKDOWN, "Markdown");
        if (lower.endsWith(".properties"))  return LanguageSpec.textmate(
                                                LanguageSpec.LANG_PROPERTIES, SCOPE_NAME_PROPERTIES, "Properties");
        if (lower.endsWith(".txt"))         return LanguageSpec.plain();

        // ── Fallback ───────────────────────────────────────────────────────────
        return LanguageSpec.plain();
    }

    // ── TextMate language loading ──────────────────────────────────────────────

    /**
     * Creates a {@link Language} for the given TextMate scope name.
     *
     * <p>Returns an {@link EmptyLanguage} on failure so the editor remains
     * usable (no crash) even if the grammar asset is missing.
     *
     * @param scopeName  A scope name constant from this class, e.g.
     *                   {@link #SCOPE_NAME_CPP}.
     * @return           A non-null {@link Language} instance.
     */
    public static Language loadTextMateLanguage(String scopeName) {
        try {
            return TextMateLanguage.create(scopeName, true);
        } catch (Exception | NoSuchMethodError e) {
            LogUtil.e(TAG, "Failed to create TextMate language for scope '" +
                    scopeName + "' — falling back to EmptyLanguage", e);
            return new EmptyLanguage();
        }
    }
}
