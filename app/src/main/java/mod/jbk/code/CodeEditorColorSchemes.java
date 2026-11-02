package mod.jbk.code;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Color;

import org.eclipse.tm4e.core.registry.IThemeSource;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme;
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;
import mod.jbk.util.LogUtil;
import pro.sketchware.SketchApplication;
import pro.sketchware.utility.ThemeUtils;

/** Shared theme and token-palette provider for every Sora code editor surface. */
public final class CodeEditorColorSchemes {
    public static final String THEME_DRACULA = "dracula.json";
    public static final String THEME_GITHUB = "GitHub.tmTheme";
    public static final String[] THEMES = {THEME_DRACULA, THEME_GITHUB};

    public static final String PREFS_NAME = "code_highlighting";
    public static final String KEY_PROFILE = "profile";
    public static final String KEY_APPEARANCE = "appearance";
    public static final String KEY_CUSTOM_PROFILE_NAME = "custom_profile_name";
    public static final String KEY_CUSTOM_BASE_PROFILE = "custom_base_profile";

    /** PROFILE_SYSTEM is retained to migrate preferences written by older builds. */
    public static final String PROFILE_SYSTEM = "system";
    public static final String PROFILE_MATERIAL = "material";
    public static final String PROFILE_GITHUB = "github";
    public static final String PROFILE_OCEAN = "ocean";
    public static final String PROFILE_MONOKAI = "monokai";
    public static final String PROFILE_DRACULA = "dracula";
    public static final String PROFILE_CUSTOM = "custom";

    public static final String APPEARANCE_SYSTEM = "system";
    public static final String APPEARANCE_LIGHT = "light";
    public static final String APPEARANCE_DARK = "dark";

    public static final String COLOR_BACKGROUND = "background";
    public static final String COLOR_FOREGROUND = "foreground";
    public static final String COLOR_KEYWORD = "keyword";
    public static final String COLOR_STRING = "string";
    public static final String COLOR_NUMBER = "number";
    public static final String COLOR_COMMENT = "comment";
    public static final String COLOR_FUNCTION = "function";
    public static final String COLOR_TYPE = "type";
    public static final String COLOR_TAG = "tag";
    public static final String COLOR_ATTRIBUTE = "attribute";
    public static final String COLOR_LINE_NUMBER = "line_number";
    public static final String COLOR_LINE_BACKGROUND = "line_background";

    private static final String TAG = "CodeEditorColorSchemes";
    private static final Set<String> LOADED_THEMES = new HashSet<>();

    static {
        try {
            AssetManager assets = SketchApplication.getContext().getAssets();
            FileProviderRegistry.getInstance().addFileProvider(new AssetsFileResolver(assets));
            ThemeRegistry registry = ThemeRegistry.getInstance();
            for (String theme : THEMES) {
                String path = "textmate/themes/" + theme;
                try {
                    registry.loadTheme(new ThemeModel(IThemeSource.fromInputStream(
                            FileProviderRegistry.getInstance().tryGetInputStream(path), path, null
                    ), theme));
                } catch (Exception error) {
                    LogUtil.e(TAG, "Unable to load built-in theme " + theme, error);
                }
            }
        } catch (RuntimeException error) {
            LogUtil.e(TAG, "Unable to initialize TextMate theme assets", error);
        }
    }

    private CodeEditorColorSchemes() { }

    public static String getSelectedProfile(Context context) {
        String profile = getPreferences(context).getString(KEY_PROFILE, PROFILE_MATERIAL);
        if (PROFILE_GITHUB.equals(profile) || PROFILE_OCEAN.equals(profile) || PROFILE_MONOKAI.equals(profile)
                || PROFILE_DRACULA.equals(profile) || PROFILE_CUSTOM.equals(profile)) return profile;
        // Migrate legacy/unknown values to the stable Material profile instead of
        // allowing a stale preference to select an unsupported palette.
        return PROFILE_MATERIAL;
    }

    public static String getSelectedAppearance(Context context) {
        String appearance = getPreferences(context).getString(KEY_APPEARANCE, APPEARANCE_SYSTEM);
        if (APPEARANCE_LIGHT.equals(appearance) || APPEARANCE_DARK.equals(appearance)) return appearance;
        return APPEARANCE_SYSTEM;
    }

    public static boolean isDarkAppearance(Context context) {
        String appearance = getSelectedAppearance(context);
        if (APPEARANCE_DARK.equals(appearance)) return true;
        if (APPEARANCE_LIGHT.equals(appearance)) return false;
        return ThemeUtils.isDarkThemeEnabled(context);
    }

    public static EditorColorScheme loadNativeColorScheme(Context context) {
        EditorColorScheme scheme = new EditorColorScheme();
        applyNativeColorProfile(context, scheme);
        return scheme;
    }

    /** Applies the selected palette to Sora's native Java/plain-text token slots. */
    public static void applyNativeColorProfile(Context context, EditorColorScheme scheme) {
        if (context == null || scheme == null) return;
        Palette palette = getPalette(context);
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.background);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, palette.lineNumber);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, palette.foreground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, palette.gutterBackground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, palette.gutterBackground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, palette.foreground);
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, palette.lineDivider);
        scheme.setColor(EditorColorScheme.CURRENT_LINE, palette.lineBackground);
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, palette.foreground);
        scheme.setColor(EditorColorScheme.KEYWORD, palette.keyword);
        scheme.setColor(EditorColorScheme.LITERAL, palette.string);
        scheme.setColor(EditorColorScheme.COMMENT, palette.comment);
        scheme.setColor(EditorColorScheme.FUNCTION_NAME, palette.function);
        scheme.setColor(EditorColorScheme.IDENTIFIER_NAME, palette.type);
        scheme.setColor(EditorColorScheme.IDENTIFIER_VAR, palette.foreground);
        scheme.setColor(EditorColorScheme.OPERATOR, palette.attribute);
        scheme.setColor(EditorColorScheme.ANNOTATION, palette.type);
        scheme.setColor(EditorColorScheme.HTML_TAG, palette.tag);
        scheme.setColor(EditorColorScheme.ATTRIBUTE_NAME, palette.attribute);
        scheme.setColor(EditorColorScheme.ATTRIBUTE_VALUE, palette.string);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, palette.accent);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, withAlpha(palette.accent, 0x28));
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, palette.accent);
    }

    public static EditorColorScheme loadTextMateColorScheme(String fallbackTheme) {
        try {
            Context context = SketchApplication.getContext();
            String json = createTextMateTheme(context);
            String themeId = "sketchneo_" + Integer.toHexString(json.hashCode());
            synchronized (LOADED_THEMES) {
                if (!LOADED_THEMES.contains(themeId)) {
                    ThemeModel model = new ThemeModel(IThemeSource.fromInputStream(
                            new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)),
                            themeId + ".json", null), themeId);
                    ThemeRegistry.getInstance().loadTheme(model);
                    LOADED_THEMES.add(themeId);
                }
            }
            ThemeRegistry.getInstance().setTheme(themeId);
            TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
            applyTextMateViewColors(context, scheme);
            return scheme;
        } catch (Exception error) {
            LogUtil.e(TAG, "Unable to create selected syntax theme; using built-in fallback", error);
            try {
                String fallback = fallbackTheme;
                if (fallback == null || fallback.trim().isEmpty()) {
                    fallback = isDarkAppearance(SketchApplication.getContext()) ? THEME_DRACULA : THEME_GITHUB;
                }
                ThemeRegistry.getInstance().setTheme(fallback);
                TextMateColorScheme scheme = TextMateColorScheme.create(ThemeRegistry.getInstance());
                applyTextMateViewColors(SketchApplication.getContext(), scheme);
                return scheme;
            } catch (Exception ignored) {
                EditorColorScheme scheme = new EditorColorScheme();
                applyNativeColorProfile(SketchApplication.getContext(), scheme);
                return scheme;
            }
        }
    }

    /**
     * TextMate themes define token scopes, while Sora keeps gutter and active-line
     * colors in its EditorColorScheme slots. Apply these after parsing the theme
     * because the TextMate adapter does not consume the common VS Code gutter keys.
     */
    private static void applyTextMateViewColors(Context context, EditorColorScheme scheme) {
        if (context == null || scheme == null) return;
        Palette palette = getPalette(context);
        scheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, palette.background);
        scheme.setColor(EditorColorScheme.TEXT_NORMAL, palette.foreground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER, palette.lineNumber);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, palette.foreground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, palette.gutterBackground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL, palette.gutterBackground);
        scheme.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, palette.foreground);
        scheme.setColor(EditorColorScheme.LINE_DIVIDER, palette.lineDivider);
        scheme.setColor(EditorColorScheme.CURRENT_LINE, palette.lineBackground);
        scheme.setColor(EditorColorScheme.SELECTION_INSERT, palette.accent);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, palette.accent);
        scheme.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_BACKGROUND, withAlpha(palette.accent, 0x28));
    }

    public static int getTokenColor(Context context, String key) {
        Palette palette = getPalette(context);
        switch (key) {
            case COLOR_BACKGROUND: return palette.background;
            case COLOR_FOREGROUND: return palette.foreground;
            case COLOR_KEYWORD: return palette.keyword;
            case COLOR_STRING: return palette.string;
            case COLOR_NUMBER: return palette.number;
            case COLOR_COMMENT: return palette.comment;
            case COLOR_FUNCTION: return palette.function;
            case COLOR_TYPE: return palette.type;
            case COLOR_TAG: return palette.tag;
            case COLOR_ATTRIBUTE: return palette.attribute;
            case COLOR_LINE_NUMBER: return palette.lineNumber;
            case COLOR_LINE_BACKGROUND: return palette.lineBackground;
            default: return palette.foreground;
        }
    }

    public static int getSearchHighlightColor(Context context) {
        return withAlpha(getPalette(context).accent, 0x66);
    }

    public static String getTokenColorHex(Context context, String key) {
        return String.format("#%06X", 0xFFFFFF & getTokenColor(context, key));
    }

    public static String preferenceKey(String key, boolean dark) {
        return key + (dark ? "_dark" : "_light");
    }

    public static void saveCustomColor(Context context, String key, int color) {
        boolean dark = isDarkAppearance(context);
        SharedPreferences prefs = getPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        String selectedProfile = getSelectedProfile(context);
        if (!PROFILE_CUSTOM.equals(selectedProfile)) editor.putString(KEY_CUSTOM_BASE_PROFILE, selectedProfile);
        editor.putString(preferenceKey(key, dark), String.format("#%06X", 0xFFFFFF & color));
        editor.putString(KEY_PROFILE, PROFILE_CUSTOM);
        editor.apply();
    }

    public static int getColor(SharedPreferences prefs, String key, boolean dark) {
        String fallback = defaultColor(key, dark);
        String value = prefs.getString(preferenceKey(key, dark), prefs.getString(key, fallback));
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return Color.parseColor(fallback);
        }
    }

    public static String defaultColor(String key, boolean dark) {
        Palette palette = materialPalette(dark);
        switch (key) {
            case COLOR_BACKGROUND: return hex(palette.background);
            case COLOR_FOREGROUND: return hex(palette.foreground);
            case COLOR_KEYWORD: return hex(palette.keyword);
            case COLOR_STRING: return hex(palette.string);
            case COLOR_NUMBER: return hex(palette.number);
            case COLOR_COMMENT: return hex(palette.comment);
            case COLOR_FUNCTION: return hex(palette.function);
            case COLOR_TYPE: return hex(palette.type);
            case COLOR_TAG: return hex(palette.tag);
            case COLOR_ATTRIBUTE: return hex(palette.attribute);
            case COLOR_LINE_NUMBER: return hex(deriveLineNumber(palette));
            case COLOR_LINE_BACKGROUND: return hex(deriveLineBackground(palette, dark));
            default: return hex(palette.foreground);
        }
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static Palette getPalette(Context context) {
        boolean dark = isDarkAppearance(context);
        String profile = getSelectedProfile(context);
        Palette palette;
        SharedPreferences prefs = getPreferences(context);
        switch (profile) {
            case PROFILE_GITHUB: palette = githubPalette(dark); break;
            case PROFILE_OCEAN: palette = oceanPalette(dark); break;
            case PROFILE_MONOKAI: palette = monokaiPalette(dark); break;
            case PROFILE_DRACULA: palette = draculaPalette(dark); break;
            case PROFILE_CUSTOM: {
                String base = prefs.getString(KEY_CUSTOM_BASE_PROFILE, PROFILE_MATERIAL);
                if (PROFILE_GITHUB.equals(base)) palette = githubPalette(dark);
                else if (PROFILE_OCEAN.equals(base)) palette = oceanPalette(dark);
                else if (PROFILE_MONOKAI.equals(base)) palette = monokaiPalette(dark);
                else if (PROFILE_DRACULA.equals(base)) palette = draculaPalette(dark);
                else palette = materialPalette(dark);
                break;
            }
            case PROFILE_MATERIAL:
            default: palette = materialPalette(dark); break;
        }
        palette.lineNumber = deriveLineNumber(palette);
        palette.lineBackground = deriveLineBackground(palette, dark);
        palette.gutterBackground = blend(palette.background, palette.foreground, dark ? .045f : .025f);
        palette.lineDivider = blend(palette.gutterBackground, palette.lineNumber, dark ? .30f : .18f);
        if (PROFILE_CUSTOM.equals(profile)) {
            palette.background = getSavedColor(prefs, COLOR_BACKGROUND, dark, palette.background);
            palette.foreground = getSavedColor(prefs, COLOR_FOREGROUND, dark, palette.foreground);
            palette.keyword = getSavedColor(prefs, COLOR_KEYWORD, dark, palette.keyword);
            palette.string = getSavedColor(prefs, COLOR_STRING, dark, palette.string);
            palette.number = getSavedColor(prefs, COLOR_NUMBER, dark, palette.number);
            palette.comment = getSavedColor(prefs, COLOR_COMMENT, dark, palette.comment);
            palette.function = getSavedColor(prefs, COLOR_FUNCTION, dark, palette.function);
            palette.type = getSavedColor(prefs, COLOR_TYPE, dark, palette.type);
            palette.tag = getSavedColor(prefs, COLOR_TAG, dark, palette.tag);
            palette.attribute = getSavedColor(prefs, COLOR_ATTRIBUTE, dark, palette.attribute);
            palette.lineNumber = getSavedColor(prefs, COLOR_LINE_NUMBER, dark,
                    deriveLineNumber(palette));
            palette.lineBackground = getSavedColor(prefs, COLOR_LINE_BACKGROUND, dark,
                    deriveLineBackground(palette, dark));
            palette.gutterBackground = blend(palette.background, palette.foreground, dark ? .045f : .025f);
            palette.lineDivider = blend(palette.gutterBackground, palette.lineNumber, dark ? .30f : .18f);
        }
        return palette;
    }

    private static int getSavedColor(SharedPreferences prefs, String key, boolean dark, int fallback) {
        String value = prefs.getString(preferenceKey(key, dark), prefs.getString(key, hex(fallback)));
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static String createTextMateTheme(Context context) throws Exception {
        Palette p = getPalette(context);
        boolean dark = isDarkAppearance(context);
        JSONObject root = new JSONObject();
        root.put("name", "Sketchware Neo " + getSelectedProfile(context));
        root.put("type", dark ? "dark" : "light");
        JSONObject colors = new JSONObject();
        colors.put("editor.background", hex(p.background));
        colors.put("editor.foreground", hex(p.foreground));
        colors.put("editorGutter.background", hex(p.gutterBackground));
        colors.put("editorLineNumber.background", hex(p.gutterBackground));
        colors.put("editorLineNumber.foreground", hex(p.lineNumber));
        colors.put("editorLineNumber.activeForeground", hex(p.foreground));
        colors.put("editorCursor.foreground", hex(p.accent));
        colors.put("editor.selectionBackground", hexWithAlpha(p.accent, 0x55));
        colors.put("editor.selectionHighlightBackground", hexWithAlpha(p.accent, 0x24));
        colors.put("editor.lineHighlightBackground", hex(p.lineBackground));
        colors.put("editor.lineHighlightBorder", hex(p.lineDivider));
        colors.put("editorIndentGuide.background", hex(p.lineDivider));
        colors.put("editorIndentGuide.activeBackground", hex(p.lineNumber));
        colors.put("editorBracketMatch.background", hexWithAlpha(p.accent, 0x28));
        colors.put("editorBracketMatch.border", hex(p.accent));
        colors.put("editorWhitespace.foreground", hexWithAlpha(p.comment, 0x66));
        root.put("colors", colors);

        JSONArray tokenColors = new JSONArray();
        addTokenRule(tokenColors, new String[]{"comment", "punctuation.definition.comment", "comment.block", "comment.line"}, hex(p.comment), "italic");
        addTokenRule(tokenColors, new String[]{"keyword", "storage", "keyword.control", "keyword.operator", "storage.modifier.java", "storage.type.java"}, hex(p.keyword), null);
        addTokenRule(tokenColors, new String[]{"string", "punctuation.definition.string", "string.quoted.double.xml", "string.quoted.single.xml"}, hex(p.string), null);
        addTokenRule(tokenColors, new String[]{"constant.numeric", "constant.language", "constant.numeric.java"}, hex(p.number), null);
        addTokenRule(tokenColors, new String[]{"entity.name.function", "support.function", "meta.function-call", "entity.name.method.java"}, hex(p.function), null);
        addTokenRule(tokenColors, new String[]{"entity.name.type", "entity.name.class", "support.type", "storage.type", "entity.name.class.java", "entity.name.type.java"}, hex(p.type), null);
        addTokenRule(tokenColors, new String[]{"entity.name.tag", "entity.name.tag.xml", "meta.tag", "punctuation.definition.tag", "punctuation.definition.tag.begin", "punctuation.definition.tag.end"}, hex(p.tag), null);
        addTokenRule(tokenColors, new String[]{"entity.other.attribute-name", "entity.other.attribute-name.xml"}, hex(p.attribute), null);
        addTokenRule(tokenColors, new String[]{"constant.character.entity", "constant.character.entity.xml", "variable.language", "constant.language.java"}, hex(p.accent), null);
        addTokenRule(tokenColors, new String[]{"punctuation.separator.key-value.xml", "punctuation.terminator.tag.xml"}, hex(p.comment), null);
        root.put("tokenColors", tokenColors);
        return root.toString();
    }

    private static void addTokenRule(JSONArray target, String[] scopes, String color, String fontStyle) throws Exception {
        JSONObject rule = new JSONObject();
        JSONArray scopeArray = new JSONArray();
        for (String scope : scopes) scopeArray.put(scope);
        rule.put("scope", scopeArray);
        JSONObject settings = new JSONObject();
        settings.put("foreground", color);
        if (fontStyle != null) settings.put("fontStyle", fontStyle);
        rule.put("settings", settings);
        target.put(rule);
    }

    private static int deriveLineNumber(Palette palette) {
        return blend(palette.foreground, palette.comment, .58f);
    }

    private static int deriveLineBackground(Palette palette, boolean dark) {
        return blend(palette.background, palette.foreground, dark ? .095f : .055f);
    }

    private static Palette githubPalette(boolean dark) {
        return dark
                ? new Palette(0xFF0D1117, 0xFFE6EDF3, 0xFFFF7B72, 0xFFA5D6FF, 0xFF79C0FF,
                        0xFF8B949E, 0xFFD2A8FF, 0xFFFFA657, 0xFF7EE787, 0xFF79C0FF, 0xFF58A6FF)
                : new Palette(0xFFFFFFFF, 0xFF24292F, 0xFFCF222E, 0xFF0A3069, 0xFF0550AE,
                        0xFF6E7781, 0xFF8250DF, 0xFF953800, 0xFF116329, 0xFF0550AE, 0xFF0969DA);
    }

    private static Palette materialPalette(boolean dark) {
        return dark
                ? new Palette(0xFF17191C, 0xFFE4E2E6, 0xFFD0BCFF, 0xFFA6D189, 0xFFE6B566, 0xFF858B96, 0xFF82B1FF, 0xFF80CBC4, 0xFF80CBC4, 0xFFD0BCFF, 0xFFD0BCFF)
                : new Palette(0xFFFAF8FF, 0xFF1B1B1F, 0xFF6750A4, 0xFF386A20, 0xFF8C5000, 0xFF73777F, 0xFF005CA9, 0xFF006B62, 0xFF006B62, 0xFF6750A4, 0xFF6750A4);
    }

    private static Palette oceanPalette(boolean dark) {
        return dark
                ? new Palette(0xFF10212B, 0xFFDCEAF2, 0xFF79C0FF, 0xFFA5D6A7, 0xFFFFCB6B, 0xFF78909C, 0xFF80CBC4, 0xFF82AAFF, 0xFF89DDFF, 0xFF89DDFF, 0xFF79C0FF)
                : new Palette(0xFFF2FAFC, 0xFF17313D, 0xFF006C9C, 0xFF287A55, 0xFF925E00, 0xFF647D88, 0xFF006F75, 0xFF375CA8, 0xFF006C9C, 0xFF006C9C, 0xFF006C9C);
    }

    private static Palette monokaiPalette(boolean dark) {
        return dark
                ? new Palette(0xFF272822, 0xFFF8F8F2, 0xFFF92672, 0xFFA6E22E, 0xFFAE81FF, 0xFF858577, 0xFFA6E22E, 0xFF66D9EF, 0xFF66D9EF, 0xFFF4BF75, 0xFFF92672)
                : new Palette(0xFFFFFEF6, 0xFF272822, 0xFFB0004F, 0xFF477A00, 0xFF7040B8, 0xFF77776A, 0xFF477A00, 0xFF006D83, 0xFF006D83, 0xFF885400, 0xFFB0004F);
    }

    private static Palette draculaPalette(boolean dark) {
        return dark
                ? new Palette(0xFF282A36, 0xFFF8F8F2, 0xFFFF79C6, 0xFFF1FA8C, 0xFFBD93F9, 0xFF6272A4, 0xFF50FA7B, 0xFF8BE9FD, 0xFF8BE9FD, 0xFFFFB86C, 0xFFFF79C6)
                : new Palette(0xFFF8F8FF, 0xFF282A36, 0xFFB00070, 0xFF596800, 0xFF6542B4, 0xFF6875A5, 0xFF007A40, 0xFF006B88, 0xFF006B88, 0xFF925000, 0xFFB00070);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int blend(int base, int overlay, float amount) {
        float inverse = 1f - amount;
        int r = Math.round(Color.red(base) * inverse + Color.red(overlay) * amount);
        int g = Math.round(Color.green(base) * inverse + Color.green(overlay) * amount);
        int b = Math.round(Color.blue(base) * inverse + Color.blue(overlay) * amount);
        return Color.rgb(r, g, b);
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    private static String hexWithAlpha(int color, int alpha) {
        return String.format("#%06X%02X", 0xFFFFFF & color, alpha & 0xFF);
    }

    private static final class Palette {
        int background, foreground, keyword, string, number, comment, function, type, tag, attribute, accent;
        int lineNumber, lineBackground, gutterBackground, lineDivider;
        Palette(int background, int foreground, int keyword, int string, int number, int comment,
                int function, int type, int tag, int attribute, int accent) {
            this.background = background; this.foreground = foreground; this.keyword = keyword;
            this.string = string; this.number = number; this.comment = comment; this.function = function;
            this.type = type; this.tag = tag; this.attribute = attribute; this.accent = accent;
        }
    }
}
