package neo.sketchware.utility.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

public class ThemeManager {

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;
    private static final String THEME_PREF = "themedata";
    private static final String THEME_KEY = "idetheme";
    private static final String DYNAMIC_COLORS_KEY = "dynamic_colors";
    private static final String PURE_BLACK_KEY = "pure_black";

    public static void applyTheme(Context context, int type) {
        saveTheme(context, type);

        switch (type) {
            case THEME_LIGHT:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case THEME_DARK:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
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

    private static void saveTheme(Context context, int theme) {
        getPreferences(context).edit().putInt(THEME_KEY, theme).apply();
    }

    // Dynamic Colors methods (Android 12+ Material You)
    public static boolean isDynamicColorsEnabled(Context context) {
        return getPreferences(context).getBoolean(DYNAMIC_COLORS_KEY, false);
    }

    public static void setDynamicColorsEnabled(Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(DYNAMIC_COLORS_KEY, enabled).apply();
    }

    // Pure Black AMOLED mode methods
    public static boolean isPureBlackEnabled(Context context) {
        return getPreferences(context).getBoolean(PURE_BLACK_KEY, false);
    }

    public static void setPureBlackEnabled(Context context, boolean enabled) {
        getPreferences(context).edit().putBoolean(PURE_BLACK_KEY, enabled).apply();
    }

    private static SharedPreferences getPreferences(Context context) {
        return context.getSharedPreferences(THEME_PREF, Context.MODE_PRIVATE);
    }
}
