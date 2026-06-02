package neo.sketchware.utility;

import com.besome.sketch.beans.ImageBean;
import com.besome.sketch.beans.LayoutBean;
import com.besome.sketch.beans.TextBean;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AttributeConstants {

    public static final List<String> BUILT_IN_ATTRIBUTES =
            Arrays.asList(
                    "id",
                    "layout_width",
                    "layout_height",
                    "orientation",
                    "layout_weight",
                    "weightSum",
                    "enabled",
                    "clickable",
                    "rotation",
                    "checked",
                    "progress",
                    "max",
                    "firstDayOfWeek",
                    "spinnerMode",
                    "dividerHeight",
                    "choiceMode",
                    "adSize",
                    "adUnitId",
                    "indeterminate",
                    "padding",
                    "paddingLeft",
                    "paddingTop",
                    "paddingRight",
                    "paddingBottom",
                    "contentPadding",
                    "contentPaddingLeft",
                    "contentPaddingTop",
                    "contentPaddingRight",
                    "contentPaddingBottom",
                    "layout_margin",
                    "layout_marginLeft",
                    "layout_marginTop",
                    "layout_marginRight",
                    "layout_marginBottom",
                    "layout_gravity",
                    "gravity",
                    "background",
                    "cardBackgroundColor",
                    "text",
                    "textSize",
                    "textStyle",
                    "textColor",
                    "hint",
                    "textColorHint",
                    "singleLine",
                    "lines",
                    "inputType",
                    "imeOptions",
                    "src",
                    "scaleType",
                    "scaleX",
                    "scaleY",
                    "translationX",
                    "translationY");

    public static final List<String> RELATIVE_ATTRIBUTES =
            Arrays.asList(
                    "android:layout_centerInParent",
                    "android:layout_centerVertical",
                    "android:layout_centerHorizontal",
                    "android:layout_toStartOf",
                    "android:layout_toLeftOf",
                    "android:layout_toRightOf",
                    "android:layout_toEndOf",
                    "android:layout_above",
                    "android:layout_below",
                    "android:layout_alignStart",
                    "android:layout_alignLeft",
                    "android:layout_alignTop",
                    "android:layout_alignEnd",
                    "android:layout_alignRight",
                    "android:layout_alignBottom",
                    "android:layout_alignParentStart",
                    "android:layout_alignParentLeft",
                    "android:layout_alignParentTop",
                    "android:layout_alignParentEnd",
                    "android:layout_alignParentRight",
                    "android:layout_alignParentBottom",
                    "android:layout_alignBaseline");

    public static final List<String> CONSTRAINT_ATTRIBUTES =
            Arrays.asList(
                    "app:layout_constraintTop_toTopOf",
                    "app:layout_constraintTop_toBottomOf",
                    "app:layout_constraintBottom_toTopOf",
                    "app:layout_constraintBottom_toBottomOf",
                    "app:layout_constraintStart_toStartOf",
                    "app:layout_constraintStart_toEndOf",
                    "app:layout_constraintEnd_toStartOf",
                    "app:layout_constraintEnd_toEndOf",
                    "app:layout_constraintLeft_toLeftOf",
                    "app:layout_constraintLeft_toRightOf",
                    "app:layout_constraintRight_toLeftOf",
                    "app:layout_constraintRight_toRightOf",
                    "app:layout_constraintBaseline_toBaselineOf",
                    "app:layout_constraintCircle",
                    "app:layout_constraintHorizontal_bias", 
                    "app:layout_constraintVertical_bias",
                    "app:layout_constraintDimensionRatio", 
                    "app:layout_constraintWidth_percent",
                    "app:layout_constraintHeight_percent", 
                    "app:layout_constraintHorizontal_weight",
                    "app:layout_constraintVertical_weight", 
                    "app:layout_constraintHorizontal_chainStyle",
                    "app:layout_constraintVertical_chainStyle", 
                    "app:layout_editor_absoluteX",
                    "app:layout_editor_absoluteY", 
                    "app:layout_constraintCircleRadius",
                    "app:layout_constraintCircleAngle",
                    "app:layout_constrainedWidth", 
                    "app:layout_constrainedHeight");

    public static Map<String, Map<String, String>> MAP_ATTR_ENUM = new HashMap<>();
    public static Map<String, Map<String, Integer>> MAP_ATTR_FLAG = new HashMap<>();

    static {
        initializeEnumMap(
                "orientation",
                "vertical",
                String.valueOf(LayoutBean.ORIENTATION_VERTICAL),
                "horizontal",
                String.valueOf(LayoutBean.ORIENTATION_HORIZONTAL));

        String[] layout =
                new String[]{
                        "fill_parent",
                        String.valueOf(LayoutBean.LAYOUT_MATCH_PARENT),
                        "match_parent",
                        String.valueOf(LayoutBean.LAYOUT_MATCH_PARENT),
                        "wrap_content",
                        String.valueOf(LayoutBean.LAYOUT_WRAP_CONTENT)
                };

        initializeEnumMap("layout_width", layout);

        initializeEnumMap("layout_height", layout);

        initializeEnumMap(
                "scaleType",
                "fitXY",
                ImageBean.SCALE_TYPE_FIT_XY,
                "fitStart",
                ImageBean.SCALE_TYPE_FIT_START,
                "fitCenter",
                ImageBean.SCALE_TYPE_FIT_CENTER,
                "fitEnd",
                ImageBean.SCALE_TYPE_FIT_END,
                "center",
                ImageBean.SCALE_TYPE_CENTER,
                "centerCrop",
                ImageBean.SCALE_TYPE_CENTER_CROP,
                "centerInside",
                ImageBean.SCALE_TYPE_CENTER_INSIDE);

        initializeFlagMap(
                "textStyle",
                "normal",
                TextBean.TEXT_TYPE_NORMAL,
                "bold",
                TextBean.TEXT_TYPE_BOLD,
                "italic",
                TextBean.TEXT_TYPE_ITALIC,
                "bold|italic",
                TextBean.TEXT_TYPE_BOLDITALIC);

        initializeFlagMap(
                "inputType",
                "text",
                TextBean.INPUT_TYPE_TEXT,
                "numberSigned",
                TextBean.INPUT_TYPE_NUMBER_SIGNED,
                "numberDecimal",
                TextBean.INPUT_TYPE_NUMBER_DECIMAL,
                "textPassword",
                TextBean.INPUT_TYPE_PASSWORD,
                "phone",
                TextBean.INPUT_TYPE_PHONE);

        var gravity =
                new Object[]{
                        "top",
                        LayoutBean.GRAVITY_TOP,
                        "bottom",
                        LayoutBean.GRAVITY_BOTTOM,
                        "left",
                        LayoutBean.GRAVITY_LEFT,
                        "right",
                        LayoutBean.GRAVITY_RIGHT,
                        "center_vertical",
                        LayoutBean.GRAVITY_CENTER_VERTICAL,
                        "center_horizontal",
                        LayoutBean.GRAVITY_CENTER_HORIZONTAL,
                        "center",
                        LayoutBean.GRAVITY_CENTER
                };
        initializeFlagMap("gravity", gravity);
        initializeFlagMap("layout_gravity", gravity);
    }

    private static void initializeEnumMap(String key, String... values) {
        Map<String, String> map = new HashMap<>();
        MAP_ATTR_ENUM.put(key, map);
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i], values[i + 1]);
        }
    }

    private static void initializeFlagMap(String key, Object... values) {
        Map<String, Integer> map = new HashMap<>();
        MAP_ATTR_FLAG.put(key, map);
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], (Integer) values[i + 1]);
        }
    }
}
