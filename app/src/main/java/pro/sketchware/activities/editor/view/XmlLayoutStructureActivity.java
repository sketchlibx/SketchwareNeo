package pro.sketchware.activities.editor.view;

import android.os.Bundle;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.besome.sketch.beans.ViewBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.appbar.MaterialToolbar;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import pro.sketchware.R;
import pro.sketchware.tools.ViewBeanParser;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class XmlLayoutStructureActivity extends BaseAppCompatActivity {

    private LinearLayout structureContainer;
    private int indentSize;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_xml_layout_structure);
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        structureContainer = findViewById(R.id.structure_container);
        indentSize = SketchwareUtil.dpToPx(18);

        String fileName = getIntent().getStringExtra("file_name");
        TextView fileLabel = findViewById(R.id.file_label);
        fileLabel.setText(TextUtils.isEmpty(fileName) ? "Current XML layout" : fileName);

        String xml = getIntent().getStringExtra("xml");
        if (TextUtils.isEmpty(xml)) {
            showEmptyState("No XML content was provided.");
            return;
        }

        try {
            ArrayList<ViewBean> views = new ViewBeanParser(xml).parse();
            if (views == null || views.isEmpty()) {
                showEmptyState("No views found in this XML layout.");
                return;
            }
            LinkedHashMap<String, ArrayList<ViewBean>> children = new LinkedHashMap<>();
            ArrayList<ViewBean> roots = new ArrayList<>();
            for (ViewBean bean : views) {
                if (bean == null) continue;
                String parent = bean.parent;
                if (TextUtils.isEmpty(parent) || "root".equals(parent)) {
                    roots.add(bean);
                } else {
                    children.computeIfAbsent(parent, key -> new ArrayList<>()).add(bean);
                }
            }
            for (ViewBean root : roots) addNode(root, children, 0);
            if (structureContainer.getChildCount() == 0) {
                showEmptyState("The XML layout does not contain a displayable view hierarchy.");
            }
        } catch (XmlPullParserException | IOException | RuntimeException error) {
            SketchwareUtil.toastError("Could not read XML hierarchy: " + error.getMessage());
            showEmptyState("The XML could not be parsed. Fix XML syntax and try again.");
        }
    }

    private void addNode(ViewBean bean, Map<String, ArrayList<ViewBean>> children, int depth) {
        if (bean == null || TextUtils.isEmpty(bean.id)) return;

        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(SketchwareUtil.dpToPx(15));
        card.setCardElevation(0f);
        card.setStrokeWidth(SketchwareUtil.dpToPx(1));
        card.setStrokeColor(ThemeUtils.getColor(this, R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(this, R.attr.colorSurfaceContainerLow));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(SketchwareUtil.dpToPx(64));
        row.setPadding(SketchwareUtil.dpToPx(13), SketchwareUtil.dpToPx(9),
                SketchwareUtil.dpToPx(13), SketchwareUtil.dpToPx(9));

        ImageView icon = new ImageView(this);
        int iconRes = ViewBean.getViewTypeResId(bean);
        if (iconRes != 0) icon.setImageResource(iconRes);
        icon.setColorFilter(ThemeUtils.getColor(this, R.attr.colorPrimary));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                SketchwareUtil.dpToPx(25), SketchwareUtil.dpToPx(25));
        iconParams.rightMargin = SketchwareUtil.dpToPx(13);
        row.addView(icon, iconParams);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setGravity(Gravity.CENTER_VERTICAL);
        TextView id = new TextView(this);
        id.setText(bean.id);
        id.setTextSize(15);
        id.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        id.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
        id.setMaxLines(1);
        id.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(id, new LinearLayout.LayoutParams(-1, -2));

        String typeName = ViewBean.getDisplayViewTypeName(bean);
        if (!TextUtils.isEmpty(bean.customView) && !"none".equals(bean.customView)) {
            typeName += " · " + bean.customView;
        }
        TextView type = new TextView(this);
        type.setText(typeName);
        type.setTextSize(12);
        type.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        type.setMaxLines(1);
        type.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(-1, -2);
        typeParams.topMargin = SketchwareUtil.dpToPx(3);
        labels.addView(type, typeParams);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        card.addView(row);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.leftMargin = 0;
        cardParams.topMargin = SketchwareUtil.dpToPx(8);
        structureContainer.addView(card, cardParams);

        ArrayList<ViewBean> nested = children.get(bean.id);
        if (nested != null && !nested.isEmpty()) {
            LinearLayout childGroup = new LinearLayout(this);
            childGroup.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
            groupParams.leftMargin = indentSize;
            structureContainer.addView(childGroup, groupParams);
            LinearLayout previousContainer = structureContainer;
            structureContainer = childGroup;
            for (ViewBean child : nested) addNode(child, children, depth + 1);
            structureContainer = previousContainer;
        }
    }

    private void showEmptyState(String message) {
        findViewById(R.id.structure_scroll).setVisibility(View.GONE);
        LinearLayout empty = findViewById(R.id.empty_state);
        empty.setVisibility(View.VISIBLE);
        TextView messageView = empty.findViewById(R.id.empty_message);
        if (messageView != null) messageView.setText(message);
    }
}
