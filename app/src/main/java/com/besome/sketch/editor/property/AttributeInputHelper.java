package com.besome.sketch.editor.property;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import mod.hey.studios.util.Helper;
import mod.hilal.saif.lib.PCP;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.ThemeUtils;

public final class AttributeInputHelper {

    private enum InputType { COLOR, RESOURCE, BACKGROUND_AMBIGUOUS, ENUM, BOOLEAN, DIMENSION, TEXT }

    private static final Map<String, String[]> ENUM_OPTIONS = new HashMap<>();
    private static final Set<String> BOOLEAN_ATTRS = new HashSet<>(Arrays.asList(
            "clickable", "focusable", "focusableintouchmode", "enabled", "selected", "checked",
            "singleline", "textallcaps", "saveenabled", "longclickable", "duplicateparentstate",
            "fitssystemwindows"
    ));

    static {
        ENUM_OPTIONS.put("visibility", new String[]{"visible", "invisible", "gone"});
        ENUM_OPTIONS.put("orientation", new String[]{"horizontal", "vertical"});
        ENUM_OPTIONS.put("scaletype", new String[]{"matrix", "fitXY", "fitStart", "fitCenter", "fitEnd", "center", "centerCrop", "centerInside"});
        ENUM_OPTIONS.put("ellipsize", new String[]{"none", "start", "middle", "end", "marquee"});
        ENUM_OPTIONS.put("textstyle", new String[]{"normal", "bold", "italic"});
    }

    private AttributeInputHelper() {}

    /** Use when the attribute-name field is live-typed and can change (re-classifies on every keystroke). */
    public static void wire(Activity activity, String scId, EditText inputAttr, TextInputLayout inputValueLayout, EditText inputValue) {
        InputType[] currentType = {InputType.TEXT};

        attachColorRefreshWatcher(inputValueLayout, inputValue, currentType);

        inputAttr.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                currentType[0] = configure(activity, scId, s == null ? "" : s.toString(), inputValueLayout, inputValue);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        currentType[0] = configure(activity, scId, Helper.getText(inputAttr), inputValueLayout, inputValue);
    }

    /** Use when the attribute name is already fixed/known and only the value field needs the smart input. */
    public static void wireFixed(Activity activity, String scId, String attrName, TextInputLayout inputValueLayout, EditText inputValue) {
        InputType[] currentType = {InputType.TEXT};
        attachColorRefreshWatcher(inputValueLayout, inputValue, currentType);
        currentType[0] = configure(activity, scId, attrName, inputValueLayout, inputValue);
    }

    private static void attachColorRefreshWatcher(TextInputLayout inputValueLayout, EditText inputValue, InputType[] currentType) {
        inputValue.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (currentType[0] == InputType.COLOR) {
                    refreshColorSwatch(inputValueLayout, inputValue);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private static InputType configure(Activity activity, String scId, String rawAttrName, TextInputLayout inputValueLayout, EditText inputValue) {
        resetToPlainText(inputValueLayout, inputValue);

        InputType type = classify(rawAttrName);
        switch (type) {
            case COLOR:
                setupColorInput(activity, scId, inputValueLayout, inputValue);
                break;
            case RESOURCE:
                setupResourceInput(activity, scId, inputValueLayout, inputValue);
                break;
            case BACKGROUND_AMBIGUOUS:
                setupBackgroundInput(activity, scId, inputValueLayout, inputValue);
                break;
            case ENUM:
                setupChoiceInput(activity, inputValueLayout, inputValue, ENUM_OPTIONS.get(localName(rawAttrName)));
                break;
            case BOOLEAN:
                setupChoiceInput(activity, inputValueLayout, inputValue, new String[]{"true", "false"});
                break;
            case DIMENSION:
                setupDimensionInput(activity, inputValueLayout, inputValue);
                break;
            case TEXT:
            default:
                break;
        }
        return type;
    }

    private static InputType classify(String rawAttrName) {
        String name = localName(rawAttrName);
        if (name.isEmpty()) return InputType.TEXT;

        if (name.equals("background")) return InputType.BACKGROUND_AMBIGUOUS;

        if (name.contains("color") || name.endsWith("tint")) return InputType.COLOR;

        if (name.equals("src") || name.equals("icon") || name.startsWith("drawable")) return InputType.RESOURCE;

        if (ENUM_OPTIONS.containsKey(name)) return InputType.ENUM;

        if (BOOLEAN_ATTRS.contains(name)) return InputType.BOOLEAN;

        if (!name.equals("layout_width") && !name.equals("layout_height")
                && (name.endsWith("width") || name.endsWith("height") || name.endsWith("size")
                || name.endsWith("radius") || name.endsWith("elevation") || name.endsWith("margin")
                || name.endsWith("padding") || name.endsWith("strokewidth") || name.endsWith("cornerradius"))) {
            return InputType.DIMENSION;
        }

        return InputType.TEXT;
    }

    private static String localName(String rawAttrName) {
        if (rawAttrName == null) return "";
        String name = rawAttrName.contains(":") ? rawAttrName.substring(rawAttrName.indexOf(':') + 1) : rawAttrName;
        return name.trim().toLowerCase(Locale.US);
    }

    private static void resetToPlainText(TextInputLayout inputValueLayout, EditText inputValue) {
        inputValueLayout.setEndIconMode(TextInputLayout.END_ICON_NONE);
        inputValue.setFocusable(true);
        inputValue.setFocusableInTouchMode(true);
        inputValue.setClickable(false);
        inputValue.setCursorVisible(true);
        inputValue.setOnClickListener(null);
    }

    private static void makeTapToPick(TextInputLayout inputValueLayout, EditText inputValue, int endIconRes, Runnable onOpen) {
        inputValueLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        try {
            inputValueLayout.setEndIconDrawable(endIconRes);
        } catch (Exception ignored) {
        }
        inputValueLayout.setEndIconOnClickListener(v -> onOpen.run());

        inputValue.setFocusable(false);
        inputValue.setFocusableInTouchMode(false);
        inputValue.setClickable(true);
        inputValue.setCursorVisible(false);
        inputValue.setOnClickListener(v -> onOpen.run());
    }

    private static void setupColorInput(Activity activity, String scId, TextInputLayout inputValueLayout, EditText inputValue) {
        inputValueLayout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        refreshColorSwatch(inputValueLayout, inputValue);

        inputValueLayout.setEndIconOnClickListener(v -> {
            String currentValue = Helper.getText(inputValue);
            com.besome.sketch.lib.ui.ColorPickerDialog colorPickerDialog =
                    new com.besome.sketch.lib.ui.ColorPickerDialog(activity, currentValue, false, false, scId);
            colorPickerDialog.a(new PCP(inputValue));
            colorPickerDialog.showAtLocation(v, android.view.Gravity.CENTER, 0, 0);
        });
    }

    private static void refreshColorSwatch(TextInputLayout inputValueLayout, EditText inputValue) {
        String value = Helper.getText(inputValue);
        int colorInt;
        try {
            colorInt = PropertiesUtil.isHexColor(value) ? Color.parseColor(value) : Color.LTGRAY;
        } catch (Exception e) {
            colorInt = Color.LTGRAY;
        }

        GradientDrawable swatch = new GradientDrawable();
        swatch.setShape(GradientDrawable.OVAL);
        swatch.setColor(colorInt);
        int size = (int) (20 * inputValue.getResources().getDisplayMetrics().density);
        swatch.setSize(size, size);
        swatch.setStroke(1, ThemeUtils.getColor(inputValue.getContext(), com.google.android.material.R.attr.colorOutline));

        inputValueLayout.setEndIconDrawable(swatch);
    }

    private static void setupResourceInput(Activity activity, String scId, TextInputLayout inputValueLayout, EditText inputValue) {
        makeTapToPick(inputValueLayout, inputValue, android.R.drawable.ic_menu_gallery,
                () -> showResourcePicker(activity, scId, inputValue));
    }

    private static void showResourcePicker(Activity activity, String scId, EditText inputValue) {
        PropertyResourceItem picker = new PropertyResourceItem(activity, true, scId, false);
        picker.showPickerDialog("Select drawable", android.R.drawable.ic_menu_gallery, Helper.getText(inputValue),
                selected -> inputValue.setText("@drawable/" + selected));
    }

    private static void setupBackgroundInput(Activity activity, String scId, TextInputLayout inputValueLayout, EditText inputValue) {
        Runnable openChooser = () -> new MaterialAlertDialogBuilder(activity)
                .setTitle("Background type")
                .setItems(new String[]{"Color", "Drawable"}, (dialog, which) -> {
                    if (which == 0) {
                        setupColorInput(activity, scId, inputValueLayout, inputValue);
                    } else {
                        setupResourceInput(activity, scId, inputValueLayout, inputValue);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();

        makeTapToPick(inputValueLayout, inputValue, android.R.drawable.ic_menu_gallery, openChooser);
    }

    private static void setupChoiceInput(Activity activity, TextInputLayout inputValueLayout, EditText inputValue, String[] options) {
        if (options == null) return;

        Runnable openPicker = () -> {
            String current = Helper.getText(inputValue);
            int selectedIndex = -1;
            for (int i = 0; i < options.length; i++) {
                if (options[i].equalsIgnoreCase(current)) {
                    selectedIndex = i;
                    break;
                }
            }
            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Select value")
                    .setSingleChoiceItems(options, selectedIndex, (dialog, which) -> {
                        inputValue.setText(options[which]);
                        dialog.dismiss();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        };

        makeTapToPick(inputValueLayout, inputValue, android.R.drawable.arrow_down_float, openPicker);
    }

    private static void setupDimensionInput(Activity activity, TextInputLayout inputValueLayout, EditText inputValue) {
        Runnable openSlider = () -> {
            float current = parseDpValue(Helper.getText(inputValue));

            Slider slider = new Slider(activity);
            slider.setValueFrom(0f);
            slider.setValueTo(200f);
            slider.setStepSize(1f);
            slider.setValue(Math.max(0f, Math.min(200f, current)));

            int padding = (int) (24 * activity.getResources().getDisplayMetrics().density);
            slider.setPadding(padding, padding, padding, 0);

            new MaterialAlertDialogBuilder(activity)
                    .setTitle("Select value (dp)")
                    .setView(slider)
                    .setPositiveButton("OK", (dialog, which) -> inputValue.setText(((int) slider.getValue()) + "dp"))
                    .setNegativeButton("Cancel", null)
                    .show();
        };

        makeTapToPick(inputValueLayout, inputValue, android.R.drawable.ic_menu_preferences, openSlider);
    }

    private static float parseDpValue(String value) {
        if (value == null || value.isEmpty()) return 0f;
        String numeric = value.replaceAll("[^0-9.]", "");
        try {
            return numeric.isEmpty() ? 0f : Float.parseFloat(numeric);
        } catch (NumberFormatException e) {
            return 0f;
        }
    }
}
