package pro.sketchware.fragments.settings.appearance;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.MaterialColors;

import java.util.ArrayList;

import a.a.a.qA;
import pro.sketchware.R;
import pro.sketchware.databinding.FragmentSettingsAppearanceBinding;
import pro.sketchware.utility.theme.ThemeManager;

public class SettingsAppearanceFragment extends qA {
    private FragmentSettingsAppearanceBinding binding;
    private MaterialCardView selectedThemeCard;
    private MaterialCardView selectedPaletteCard;
    private final ArrayList<MaterialCardView> paletteCards = new ArrayList<>();
    private boolean isInitializing = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupToolbar();
        setupPalettePicker();
        initializeThemeSettings();
        setupClickListeners();
        View content = binding.content;
        int left = content.getPaddingLeft();
        int top = content.getPaddingTop();
        int right = content.getPaddingRight();
        int bottom = content.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left, top, right + bars.right, bottom);
            return insets;
        });
        isInitializing = false;
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (requireActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });
        View appBar = binding.appBarLayout;
        int left = appBar.getPaddingLeft();
        int top = appBar.getPaddingTop();
        int right = appBar.getPaddingRight();
        int bottom = appBar.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(appBar, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(left + bars.left, top + bars.top, right + bars.right, bottom);
            return insets;
        });
    }

    private void setupPalettePicker() {
        GridLayout grid = binding.paletteGrid;
        grid.removeAllViews();
        paletteCards.clear();
        for (ThemeManager.PaletteOption option : ThemeManager.getPaletteOptions()) {
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setCheckable(true);
            card.setClickable(true);
            card.setFocusable(true);
            card.setRadius(dp(18));
            card.setCardElevation(0f);
            card.setStrokeWidth(dp(1));
            card.setStrokeColor(MaterialColors.getColor(card, R.attr.colorOutlineVariant));
            card.setCardBackgroundColor(MaterialColors.getColor(card, R.attr.colorSurfaceContainer));
            card.setContentPadding(dp(10), dp(10), dp(10), dp(10));
            card.setMinimumHeight(dp(58));
            card.setContentDescription(option.name + " color theme");

            LinearLayout content = new LinearLayout(requireContext());
            content.setGravity(Gravity.CENTER_VERTICAL);
            content.setOrientation(LinearLayout.HORIZONTAL);

            View swatch = new View(requireContext());
            GradientDrawable swatchShape = new GradientDrawable();
            swatchShape.setShape(GradientDrawable.OVAL);
            swatchShape.setColor(option.previewColor);
            swatchShape.setStroke(dp(1), MaterialColors.getColor(card, R.attr.colorOutline));
            swatch.setBackground(swatchShape);
            LinearLayout.LayoutParams swatchParams = new LinearLayout.LayoutParams(dp(22), dp(22));
            swatchParams.setMarginEnd(dp(9));
            content.addView(swatch, swatchParams);

            TextView label = new TextView(requireContext());
            label.setText(option.name);
            label.setTextSize(13);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setTextColor(MaterialColors.getColor(card, R.attr.colorOnSurface));
            label.setSingleLine(false);
            content.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            card.addView(content);

            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 0;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            params.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(card, params);
            paletteCards.add(card);
            card.setOnClickListener(v -> selectPalette(option.id));
        }
        updatePaletteSelection();
    }

    private void initializeThemeSettings() {
        boolean systemTheme = ThemeManager.isSystemTheme(requireContext());
        boolean dynamicColors = ThemeManager.isDynamicColorsEnabled(requireContext());
        binding.switchSystem.setChecked(systemTheme);
        binding.switchDynamicColors.setChecked(dynamicColors);
        binding.switchDynamicColors.setEnabled(ThemeManager.isDynamicColorsAvailable());
        updateThemeCardSelection(ThemeManager.getCurrentTheme(requireContext()));
        updatePaletteSelection();
        setThemeCardsEnabled(!systemTheme);
        setPaletteGridEnabled(!dynamicColors);
    }

    private void setupClickListeners() {
        binding.themeSystem.setOnClickListener(v -> binding.switchSystem.setChecked(!binding.switchSystem.isChecked()));
        binding.switchSystem.setOnCheckedChangeListener((buttonView, isChecked) -> {
            unselectSelectedThemeCard();
            setThemeCardsEnabled(!isChecked);
            if (isChecked) {
                if (!isInitializing) ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_SYSTEM);
                return;
            }
            int theme = ThemeManager.getSystemAppliedTheme(requireContext());
            if (!isInitializing) ThemeManager.applyTheme(requireContext(), theme);
            updateThemeCardSelection(theme);
        });

        binding.themeLight.setOnClickListener(v -> {
            if (!binding.switchSystem.isChecked()) {
                updateThemeCardSelection(ThemeManager.THEME_LIGHT);
                if (!isInitializing) ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_LIGHT);
            }
        });

        binding.themeDark.setOnClickListener(v -> {
            if (!binding.switchSystem.isChecked()) {
                updateThemeCardSelection(ThemeManager.THEME_DARK);
                if (!isInitializing) ThemeManager.applyTheme(requireContext(), ThemeManager.THEME_DARK);
            }
        });

        binding.switchDynamicColors.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isInitializing || !ThemeManager.isDynamicColorsAvailable()) return;
            ThemeManager.setDynamicColorsEnabled(requireContext(), isChecked);
            setPaletteGridEnabled(!isChecked);
            requireActivity().recreate();
        });
    }

    private void selectPalette(String paletteId) {
        if (binding.switchDynamicColors.isChecked()) return;
        ThemeManager.setCurrentPalette(requireContext(), paletteId);
        updatePaletteSelection();
        requireActivity().recreate();
    }

    private void updateThemeCardSelection(int theme) {
        unselectSelectedThemeCard();
        MaterialCardView selection = switch (theme) {
            case ThemeManager.THEME_LIGHT -> binding.themeLight;
            case ThemeManager.THEME_DARK -> binding.themeDark;
            default -> null;
        };
        if (selection != null && !binding.switchSystem.isChecked()) {
            selection.setChecked(true);
            selectedThemeCard = selection;
        }
    }

    private void updatePaletteSelection() {
        String selected = ThemeManager.getCurrentPalette(requireContext());
        int primary = MaterialColors.getColor(binding.paletteGrid, R.attr.colorPrimary);
        int outline = MaterialColors.getColor(binding.paletteGrid, R.attr.colorOutlineVariant);
        selectedPaletteCard = null;
        ThemeManager.PaletteOption[] options = ThemeManager.getPaletteOptions();
        for (int i = 0; i < paletteCards.size() && i < options.length; i++) {
            MaterialCardView card = paletteCards.get(i);
            boolean isSelected = options[i].id.equals(selected);
            card.setChecked(isSelected);
            card.setStrokeWidth(dp(isSelected ? 2 : 1));
            card.setStrokeColor(isSelected ? primary : outline);
            if (isSelected) selectedPaletteCard = card;
        }
    }

    private void unselectSelectedThemeCard() {
        if (selectedThemeCard != null) {
            selectedThemeCard.setChecked(false);
            selectedThemeCard = null;
        }
    }

    private void setThemeCardsEnabled(boolean enabled) {
        binding.themeLight.setEnabled(enabled);
        binding.themeDark.setEnabled(enabled);
        float alpha = enabled ? 1f : 0.5f;
        binding.themeLight.animate().alpha(alpha).start();
        binding.themeDark.animate().alpha(alpha).start();
    }

    private void setPaletteGridEnabled(boolean enabled) {
        for (MaterialCardView card : paletteCards) {
            card.setEnabled(enabled);
            card.animate().alpha(enabled ? 1f : 0.48f).start();
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        paletteCards.clear();
        selectedPaletteCard = null;
        selectedThemeCard = null;
        binding = null;
        super.onDestroyView();
    }
}
