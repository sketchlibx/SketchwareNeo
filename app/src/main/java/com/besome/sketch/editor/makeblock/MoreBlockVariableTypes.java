package com.besome.sketch.editor.makeblock;

import android.content.Context;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

import a.a.a.kq;
import pro.sketchware.R;

/**
 * Variable type tables of the legacy creator ({@link VariableItemView}), exposed so the new creator
 * offers exactly the same type codes. Type codes and the {@code type + "." + name} composition are
 * identical to {@code VariableItemView.getSelectedItem()} + {@code MoreBlockBuilderView}.
 */
public final class MoreBlockVariableTypes {
    private static final List<Type> VARIABLES = new ArrayList<>();
    private static final List<Type> VIEWS = new ArrayList<>();
    private static final List<Type> COMPONENTS = new ArrayList<>();

    static {
        VARIABLES.add(new Type("b", "", R.drawable.ic_true_false_color_48dp));
        VARIABLES.add(new Type("d", "", R.drawable.numbers_48));
        // Float / Int are the engine's own variable types (varFloat / varIntNum): Lx declares them as
        // "float" / "int" parameters. The plain "f" / "i" spec tokens are skipped by Lx.getMoreBlockCode.
        VARIABLES.add(new Type("m", "varFloat", R.drawable.numbers_48));
        VARIABLES.add(new Type("m", "varIntNum", R.drawable.numbers_48));
        VARIABLES.add(new Type("s", "", R.drawable.abc_96_color));
        VARIABLES.add(new Type("m", "varMap", R.drawable.ic_map_color_48dp));
        VARIABLES.add(new Type("m", "listInt", R.drawable.ic_list_color_48dp));
        VARIABLES.add(new Type("m", "listStr", R.drawable.ic_list_color_48dp));
        VARIABLES.add(new Type("m", "listMap", R.drawable.ic_list_color_48dp));

        VIEWS.add(new Type("m", "view", R.drawable.layout_48));
        VIEWS.add(new Type("m", "textview", R.drawable.widget_text_view));
        VIEWS.add(new Type("m", "imageview", R.drawable.widget_image_view));
        VIEWS.add(new Type("m", "checkbox", R.drawable.widget_check_box));
        VIEWS.add(new Type("m", "switch", R.drawable.widget_switch));
        VIEWS.add(new Type("m", "listview", R.drawable.widget_list_view));
        VIEWS.add(new Type("m", "spinner", R.drawable.widget_spinner));
        VIEWS.add(new Type("m", "webview", R.drawable.widget_web_view));
        VIEWS.add(new Type("m", "seekbar", R.drawable.widget_seek_bar));
        VIEWS.add(new Type("m", "progressbar", R.drawable.widget_progress_bar));
        VIEWS.add(new Type("m", "calendarview", R.drawable.widget_calendar));
        VIEWS.add(new Type("m", "radiobutton", R.drawable.widget_radio_button));
        VIEWS.add(new Type("m", "ratingbar", R.drawable.star_filled));
        VIEWS.add(new Type("m", "videoview", R.drawable.widget_mediaplayer));
        VIEWS.add(new Type("m", "searchview", R.drawable.ic_search_color_96dp));
        VIEWS.add(new Type("m", "gridview", R.drawable.grid_3_48));
        VIEWS.add(new Type("m", "actv", R.drawable.widget_edit_text));
        VIEWS.add(new Type("m", "mactv", R.drawable.widget_edit_text));
        VIEWS.add(new Type("m", "viewpager", R.drawable.widget_relative_layout));
        VIEWS.add(new Type("m", "badgeview", R.drawable.pro_account_100dp_primary));

        COMPONENTS.add(new Type("m", "intent", R.drawable.widget_intent));
        COMPONENTS.add(new Type("m", "file", R.drawable.widget_shared_preference));
        COMPONENTS.add(new Type("m", "calendar", R.drawable.widget_calendar));
        COMPONENTS.add(new Type("m", "vibrator", R.drawable.widget_vibrator));
        COMPONENTS.add(new Type("m", "timer", R.drawable.widget_timer));
        COMPONENTS.add(new Type("m", "dialog", R.drawable.widget_alertdialog));
        COMPONENTS.add(new Type("m", "mediaplayer", R.drawable.widget_mediaplayer));
        COMPONENTS.add(new Type("m", "soundpool", R.drawable.widget_soundpool));
        COMPONENTS.add(new Type("m", "objectanimator", R.drawable.widget_objectanimator));
        COMPONENTS.add(new Type("m", "firebase", R.drawable.widget_firebase));
        COMPONENTS.add(new Type("m", "firebaseauth", R.drawable.widget_firebase));
        COMPONENTS.add(new Type("m", "firebasestorage", R.drawable.widget_firebase));
        COMPONENTS.add(new Type("m", "camera", R.drawable.widget_camera));
        COMPONENTS.add(new Type("m", "filepicker", R.drawable.widget_file));
        COMPONENTS.add(new Type("m", "requestnetwork", R.drawable.widget_network_request));
        COMPONENTS.add(new Type("m", "texttospeech", R.drawable.widget_text_to_speech));
        COMPONENTS.add(new Type("m", "speechtotext", R.drawable.widget_speech_to_text));
        COMPONENTS.add(new Type("m", "locationmanager", R.drawable.widget_location));
        COMPONENTS.add(new Type("m", "videoad", R.drawable.widget_media_controller));
        COMPONENTS.add(new Type("m", "progressdialog", R.drawable.widget_progress_dialog));
        COMPONENTS.add(new Type("m", "timepickerdialog", R.drawable.widget_timer));
        COMPONENTS.add(new Type("m", "notification", R.drawable.widget_notification));
    }

    private MoreBlockVariableTypes() {
    }

    public record Type(String type, String name, @DrawableRes int icon) {
        /** Full legacy code, e.g. {@code "b"} or {@code "m.varMap"}. */
        public String code() {
            return name.isEmpty() ? type : type + "." + name;
        }
    }

    public static List<Type> variables() {
        return VARIABLES;
    }

    public static List<Type> views() {
        return VIEWS;
    }

    public static List<Type> components() {
        return COMPONENTS;
    }

    /** The six types shown in the dialog's grid, in screenshot order. */
    public static List<Type> primary() {
        List<Type> list = new ArrayList<>();
        for (String code : new String[]{"b", "d", "s", "m.varMap", "m.varFloat", "m.varIntNum"}) {
            Type type = find(code);
            if (type != null) list.add(type);
        }
        return list;
    }

    @Nullable
    public static Type find(@Nullable String code) {
        if (code == null) return null;
        for (List<Type> group : List.of(VARIABLES, VIEWS, COMPONENTS)) {
            for (Type type : group) {
                if (type.code().equals(code)) return type;
            }
        }
        return null;
    }

    /** Same naming rules as {@code VariableItemView.getTypeName}. */
    @NonNull
    public static String title(@NonNull Context context, @NonNull Type item) {
        String name = item.name();
        switch (name) {
            case "varInt":
                return context.getString(R.string.logic_variable_type_number);
            case "varBool":
                return context.getString(R.string.logic_variable_type_boolean);
            case "varStr":
                return context.getString(R.string.logic_variable_type_string);
            case "varFloat":
                return context.getString(R.string.logic_variable_type_float);
            case "varIntNum":
                return context.getString(R.string.logic_variable_type_int_num);
            case "varMap":
                return context.getString(R.string.logic_variable_type_map);
            case "listInt":
                return context.getString(R.string.logic_variable_type_list_number);
            case "listStr":
                return context.getString(R.string.logic_variable_type_list_string);
            case "listMap":
                return context.getString(R.string.logic_variable_type_list_map);
            default:
        }
        switch (item.type()) {
            case "b":
                return context.getString(R.string.logic_variable_type_boolean);
            case "d":
                return context.getString(R.string.logic_variable_type_number);
            case "s":
                return context.getString(R.string.logic_variable_type_string);
            case "f":
                return context.getString(R.string.logic_variable_type_float);
            case "i":
                return context.getString(R.string.logic_variable_type_int_num);
            default:
                return kq.b(name);
        }
    }

    @StringRes
    public static int shortDescription(@NonNull Type item) {
        switch (item.code()) {
            case "b":
                return R.string.nmb_type_desc_boolean;
            case "d":
                return R.string.nmb_type_desc_number;
            case "s":
                return R.string.nmb_type_desc_string;
            case "m.varMap":
                return R.string.nmb_type_desc_map;
            case "m.varFloat":
                return R.string.nmb_type_desc_float;
            case "m.varIntNum":
                return R.string.nmb_type_desc_int;
            default:
                return R.string.nmb_type_desc_other;
        }
    }

    @StringRes
    public static int info(@NonNull Type item) {
        switch (item.code()) {
            case "b":
                return R.string.nmb_type_info_boolean;
            case "d":
                return R.string.nmb_type_info_number;
            case "s":
                return R.string.nmb_type_info_string;
            case "m.varMap":
                return R.string.nmb_type_info_map;
            case "m.varFloat":
                return R.string.nmb_type_info_float;
            case "m.varIntNum":
                return R.string.nmb_type_info_int;
            case "m.listInt":
            case "m.listStr":
            case "m.listMap":
                return R.string.nmb_type_info_list;
            default:
                return R.string.nmb_type_info_component;
        }
    }
}
