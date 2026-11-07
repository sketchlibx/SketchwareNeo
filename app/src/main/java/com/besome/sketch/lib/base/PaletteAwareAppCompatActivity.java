package com.besome.sketch.lib.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import pro.sketchware.utility.theme.ThemeManager;

public abstract class PaletteAwareAppCompatActivity extends AppCompatActivity {

    private String appliedPaletteId;
    private boolean appliedDynamicColors;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.applyPaletteToActivity(this);
        appliedPaletteId = ThemeManager.getCurrentPalette(this);
        appliedDynamicColors = ThemeManager.isDynamicColorsEnabled(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String currentPalette = ThemeManager.getCurrentPalette(this);
        boolean currentDynamicColors = ThemeManager.isDynamicColorsEnabled(this);
        if (appliedPaletteId != null && (!appliedPaletteId.equals(currentPalette)
                || appliedDynamicColors != currentDynamicColors)) {
            appliedPaletteId = currentPalette;
            appliedDynamicColors = currentDynamicColors;
            recreate();
        }
    }
}
