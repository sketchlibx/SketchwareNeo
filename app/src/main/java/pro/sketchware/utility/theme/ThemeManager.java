package pro.sketchware.utility.theme;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;

import pro.sketchware.R;

public final class ThemeManager {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    public static final String PALETTE_DEFAULT = "default";
    public static final String PALETTE_BLUE = "blue";
    public static final String PALETTE_RED = "red";
    public static final String PALETTE_GREEN = "green";
    public static final String PALETTE_PURPLE = "purple";
    public static final String PALETTE_AMBER = "amber";
    public static final String PALETTE_TEAL = "teal";
    public static final String PALETTE_INDIGO = "indigo";
    public static final String PALETTE_PINK = "pink";
    public static final String PALETTE_ORANGE = "orange";
    public static final String PALETTE_ANTIQUE = "antique";
    private static final String THEME_PREF = "themedata";
    private static final String THEME_KEY = "idetheme";
    private static final String PALETTE_KEY = "ide_palette";
    private static final String DYNAMIC_COLORS_KEY = "dynamic_colors";
    private static final String PURE_BLACK_KEY = "pure_black";

    private static final PaletteOption[] PALETTES = {
            new PaletteOption(PALETTE_DEFAULT, "Default", 0xFF006493),
            new PaletteOption(PALETTE_BLUE, "Material Blue", 0xFF1565C0),
            new PaletteOption(PALETTE_RED, "Material Red", 0xFFBA1A1A),
            new PaletteOption(PALETTE_GREEN, "Material Green", 0xFF006D3A),
            new PaletteOption(PALETTE_PURPLE, "Material Purple", 0xFF6750A4),
            new PaletteOption(PALETTE_AMBER, "Material Amber", 0xFF775A00),
            new PaletteOption(PALETTE_TEAL, "Material Teal", 0xFF006A60),
            new PaletteOption(PALETTE_INDIGO, "Material Indigo", 0xFF4F5DA8),
            new PaletteOption(PALETTE_PINK, "Material Pink", 0xFFA00056),
            new PaletteOption(PALETTE_ORANGE, "Material Orange", 0xFF8C4F00),
            new PaletteOption(PALETTE_ANTIQUE, "Material Antique", 0xFF765B32)
    };

    private ThemeManager() {
    }

    public static void applyTheme(Context context, int type) {
        saveTheme(context, type);
        switch (type) {
            case THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            case THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            default -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    public static int getCurrentTheme(Context context) {
        return getPreferences(context).getInt(THEME_KEY, THEME_SYSTEM);
    }

    public static boolean isSystemTheme(Context context) {
        return getCurrentTheme(context) == THEME_SYSTEM;
    }

    public static int getSystemAppliedTheme(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
        return switch (nightModeFlags) {
            case Configuration.UI_MODE_NIGHT_NO -> THEME_LIGHT;
            case Configuration.UI_MODE_NIGHT_YES -> THEME_DARK;
            default -> THEME_SYSTEM;
        };
    }

    public static String getCurrentPalette(Context context) {
        String palette = getPreferences(context).getString(PALETTE_KEY, PALETTE_DEFAULT);
        return isKnownPalette(palette) ? palette : PALETTE_DEFAULT;
    }

    public static void setCurrentPalette(Context context, String palette) {
        String safePalette = isKnownPalette(palette) ? palette : PALETTE_DEFAULT;
        getPreferences(context).edit().putString(PALETTE_KEY, safePalette).apply();
    }

    public static PaletteOption[] getPaletteOptions() {
        return PALETTES.clone();
    }

    public static void applyPaletteToActivity(Activity activity) {
        if (isDynamicColorsEnabled(activity) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColors.applyToActivityIfAvailable(activity);
            return;
        }
        int overlay = getPaletteOverlay(getCurrentPalette(activity));
        if (overlay != 0) activity.getTheme().applyStyle(overlay, true);
    }

    public static boolean isDynamicColorsAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S;
    }

    public static boolean isDynamicColorsEnabled(Context context) {
        return isDynamicColorsAvailable()
                && getPreferences(context).getBoolean(DYNAMIC_COLORS_KEY, false);
    }

    public static void setDynamicColorsEnabled(Context context, boolean enabled) {
        getPreferences(context).edit()
                .putBoolean(DYNAMIC_COLORS_KEY, enabled && isDynamicColorsAvailable())
                .apply();
    }

    public static boolean isPureBlackEnabled(Context context) {
        return getPreferences(context).getBoolean(PURE_BLACK_KEY, false);
    }

    public static void setPureBlackEnabled(Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(PURE_BLACK_KEY, enabled).apply();
    }

    private static int getPaletteOverlay(String palette) {
        return switch (palette) {
            case PALETTE_BLUE -> R.style.ThemeOverlay_SketchwarePro_Palette_Blue;
            case PALETTE_RED -> R.style.ThemeOverlay_SketchwarePro_Palette_Red;
            case PALETTE_GREEN -> R.style.ThemeOverlay_SketchwarePro_Palette_Green;
            case PALETTE_PURPLE -> R.style.ThemeOverlay_SketchwarePro_Palette_Purple;
            case PALETTE_AMBER -> R.style.ThemeOverlay_SketchwarePro_Palette_Amber;
            case PALETTE_TEAL -> R.style.ThemeOverlay_SketchwarePro_Palette_Teal;
            case PALETTE_INDIGO -> R.style.ThemeOverlay_SketchwarePro_Palette_Indigo;
            case PALETTE_PINK -> R.style.ThemeOverlay_SketchwarePro_Palette_Pink;
            case PALETTE_ORANGE -> R.style.ThemeOverlay_SketchwarePro_Palette_Orange;
            case PALETTE_ANTIQUE -> R.style.ThemeOverlay_SketchwarePro_Palette_Antique;
            default -> 0;
        };
    }

    private static boolean isKnownPalette(String palette) {
        if (palette == null) return false;
        for (PaletteOption option : PALETTES) {
            if (option.id.equals(palette)) return true;
        }
        return false;
    }

    private static void saveTheme(Context context, int theme) {
        getPreferences(context).edit().putInt(THEME_KEY, theme).apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(THEME_PREF, Context.MODE_PRIVATE);
    }

    public static final class PaletteOption {
        public final String id;
        public final String name;
        public final int previewColor;

        private PaletteOption(String id, String name, int previewColor) {
            this.id = id;
            this.name = name;
            this.previewColor = previewColor;
        }
    }
}
