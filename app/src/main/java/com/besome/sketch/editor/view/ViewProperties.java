package com.besome.sketch.editor.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.besome.sketch.ctrls.ViewIdSpinnerItem;
import com.besome.sketch.beans.ViewBean;

import java.util.ArrayList;
import java.util.HashMap;

import a.a.a.Jw;
import a.a.a.wB;
import a.a.a.jC;
import mod.hey.studios.util.Helper;
import mod.hilal.saif.activities.tools.ConfigActivity;
import pro.sketchware.R;

public class ViewProperties extends RelativeLayout implements AdapterView.OnItemSelectedListener {

    private final ArrayList<String> viewsIdList = new ArrayList<>();
    private final ArrayList<TreeNode> treeNodesList = new ArrayList<>();
    private SpinnerItemAdapter spinnerItemAdapter;
    private Jw propertyTargetChangeListener = null;
    private boolean isTreeViewEnabled = false;

    public ViewProperties(Context context) {
        super(context);
        initialize(context);
    }

    public ViewProperties(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        initialize(context);
    }

    public void setOnPropertyTargetChangeListener(Jw onPropertyTargetChangeListener) {
        propertyTargetChangeListener = onPropertyTargetChangeListener;
    }

    private void initialize(Context context) {
        wB.a(context, this, R.layout.view_properties);
        ((TextView) findViewById(R.id.btn_editproperties)).setText(Helper.getResString(R.string.design_button_properties));
        Spinner spinner = findViewById(R.id.spn_widget);
        
        isTreeViewEnabled = ConfigActivity.isSettingEnabled(ConfigActivity.SETTING_TREE_VIEW);
        spinnerItemAdapter = new SpinnerItemAdapter(context, viewsIdList, treeNodesList, isTreeViewEnabled);
        
        spinner.setAdapter(spinnerItemAdapter);
        spinner.setSelection(0);
        spinner.setOnItemSelectedListener(this);
    }

    // Call this method whenever the views update in the editor fragment
    public void updateViewList(String sc_id, String xmlName, ArrayList<ViewBean> currentViews) {
        viewsIdList.clear();
        treeNodesList.clear();

        if (isTreeViewEnabled && currentViews != null) {
            // Build tree hierarchy
            HashMap<String, ArrayList<ViewBean>> childrenMap = new HashMap<>();
            ArrayList<ViewBean> rootViews = new ArrayList<>();

            for (ViewBean bean : currentViews) {
                if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()) {
                    rootViews.add(bean);
                } else {
                    childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
                }
            }

            for (ViewBean root : rootViews) {
                buildTreeList(root, childrenMap, 0);
            }
        } else if (currentViews != null) {
            // Flat list
            for (ViewBean bean : currentViews) {
                viewsIdList.add(bean.id);
            }
        }
        
        if (spinnerItemAdapter != null) spinnerItemAdapter.notifyDataSetChanged();
    }

    private void buildTreeList(ViewBean parent, HashMap<String, ArrayList<ViewBean>> childrenMap, int depth) {
        viewsIdList.add(parent.id);
        treeNodesList.add(new TreeNode(parent.id, depth));

        ArrayList<ViewBean> children = childrenMap.get(parent.id);
        if (children != null) {
            for (ViewBean child : children) {
                buildTreeList(child, childrenMap, depth + 1);
            }
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        spinnerItemAdapter.setLayoutPosition(position);
        if (propertyTargetChangeListener != null && !viewsIdList.isEmpty()) {
            propertyTargetChangeListener.a(viewsIdList.get(position));
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    private static class TreeNode {
        String id;
        int depth;
        TreeNode(String id, int depth) {
            this.id = id;
            this.depth = depth;
        }
    }

    private static class SpinnerItemAdapter extends BaseAdapter {

        private final Context context;
        private final ArrayList<String> data;
        private final ArrayList<TreeNode> treeData;
        private final boolean useTree;
        private int layoutPosition;

        public SpinnerItemAdapter(Context context, ArrayList<String> flatData, ArrayList<TreeNode> treeData, boolean useTree) {
            this.context = context;
            this.data = flatData;
            this.treeData = treeData;
            this.useTree = useTree;
        }

        public void setLayoutPosition(int position) {
            layoutPosition = position;
        }

        @Override
        public int getCount() {
            return useTree ? treeData.size() : data.size();
        }

        @Override
        public View getDropDownView(int position, View view, ViewGroup viewGroup) {
            return createSpinnerItemView(position, view, viewGroup, layoutPosition == position, true);
        }

        @Override
        public String getItem(int position) {
            return useTree ? treeData.get(position).id : data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return createSpinnerItemView(position, convertView, parent, false, false);
        }

        private ViewIdSpinnerItem createSpinnerItemView(int position, View convertView, ViewGroup parent, boolean isSelected, boolean isDropDown) {
            ViewIdSpinnerItem viewIdSpinnerItem;
            if (convertView != null) {
                viewIdSpinnerItem = (ViewIdSpinnerItem) convertView;
            } else {
                viewIdSpinnerItem = new ViewIdSpinnerItem(context);
                viewIdSpinnerItem.setTextSize(R.dimen.text_size_body_small);
            }

            String itemId = getItem(position);
            viewIdSpinnerItem.a(0, itemId, isSelected);
            viewIdSpinnerItem.a(false, 0xff404040, 0xff404040);

            // Apply indentation for tree view if it's the dropdown list
            if (useTree && isDropDown) {
                int depth = treeData.get(position).depth;
                int paddingPx = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, depth * 20, context.getResources().getDisplayMetrics());
                // Add a visual tree branch marker if depth > 0
                if (depth > 0) {
                    viewIdSpinnerItem.a(0, " └ " + itemId, isSelected);
                }
                viewIdSpinnerItem.setPadding(paddingPx, viewIdSpinnerItem.getPaddingTop(), viewIdSpinnerItem.getPaddingRight(), viewIdSpinnerItem.getPaddingBottom());
            } else {
                viewIdSpinnerItem.setPadding(0, viewIdSpinnerItem.getPaddingTop(), viewIdSpinnerItem.getPaddingRight(), viewIdSpinnerItem.getPaddingBottom());
            }

            return viewIdSpinnerItem;
        }
    }
}
