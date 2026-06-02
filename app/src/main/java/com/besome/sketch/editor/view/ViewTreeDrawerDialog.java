package com.besome.sketch.editor.view;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import neo.sketchware.R;
import neo.sketchware.utility.SketchwareUtil;
import neo.sketchware.utility.ThemeUtils;

public class ViewTreeDrawerDialog extends DialogFragment {

    private final ArrayList<ViewBean> currentViews;
    private final OnViewSelectedListener listener;

    private final List<TreeNode> rootNodes = new ArrayList<>();
    private final List<TreeNode> displayNodes = new ArrayList<>();
    private TreeAdapter adapter;

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
    }

    public ViewTreeDrawerDialog(ArrayList<ViewBean> views, OnViewSelectedListener listener) {
        this.currentViews = views;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        Window window = dialog.getWindow();
        if (window != null) {
            window.requestFeature(Window.FEATURE_NO_TITLE);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Window window = getDialog().getWindow();
        if (window != null) {
            window.setLayout(SketchwareUtil.dpToPx(300), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog);
            
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.5f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopRightCornerSize(SketchwareUtil.getDip(24))
                .setBottomRightCornerSize(SketchwareUtil.getDip(24))
                .build();
                
        MaterialShapeDrawable background = new MaterialShapeDrawable(shape);
        background.setFillColor(ColorStateList.valueOf(ThemeUtils.getColor(requireContext(), R.attr.colorSurfaceContainerLow)));
        background.initializeElevationOverlay(requireContext());
        root.setBackground(background);
        root.setElevation(SketchwareUtil.dpToPx(3));

        TextView header = new TextView(requireContext());
        header.setText("Component Tree");
        header.setTextSize(18);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
        int padding = SketchwareUtil.dpToPx(20);
        header.setPadding(padding, padding + SketchwareUtil.dpToPx(8), padding, padding);
        root.addView(header);

        View divider = new View(requireContext());
        divider.setBackgroundColor(ThemeUtils.getColor(requireContext(), R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, SketchwareUtil.dpToPx(1));
        dividerParams.setMargins(padding, 0, padding, 0);
        root.addView(divider, dividerParams);

        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        hsv.setFillViewport(true);

        RecyclerView rv = new RecyclerView(requireContext());
        rv.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        rv.setMinimumWidth(SketchwareUtil.dpToPx(300));
        rv.setClipToPadding(false);
        rv.setPadding(0, SketchwareUtil.dpToPx(8), padding, padding);

        buildTree();
        refreshDisplayList();

        adapter = new TreeAdapter();
        rv.setAdapter(adapter);

        hsv.addView(rv);
        root.addView(hsv);

        return root;
    }

    private void buildTree() {
        HashMap<String, List<ViewBean>> childrenMap = new HashMap<>();
        List<ViewBean> roots = new ArrayList<>();

        for (ViewBean bean : currentViews) {
            if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()) {
                roots.add(bean);
            } else {
                childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
            }
        }

        for (ViewBean root : roots) {
            rootNodes.add(createNode(root, childrenMap, 0));
        }
    }

    private TreeNode createNode(ViewBean view, HashMap<String, List<ViewBean>> childrenMap, int depth) {
        TreeNode node = new TreeNode(view, depth);
        node.isExpanded = true;

        List<ViewBean> children = childrenMap.get(view.id);
        if (children != null) {
            for (ViewBean child : children) {
                node.children.add(createNode(child, childrenMap, depth + 1));
            }
        }
        return node;
    }

    private void refreshDisplayList() {
        displayNodes.clear();
        for (TreeNode root : rootNodes) {
            addNodeToDisplay(root);
        }
    }

    private void addNodeToDisplay(TreeNode node) {
        displayNodes.add(node);
        if (node.isExpanded) {
            for (TreeNode child : node.children) {
                addNodeToDisplay(child);
            }
        }
    }

    private static class TreeNode {
        ViewBean viewBean;
        int depth;
        boolean isExpanded;
        List<TreeNode> children = new ArrayList<>();

        TreeNode(ViewBean viewBean, int depth) {
            this.viewBean = viewBean;
            this.depth = depth;
        }

        boolean hasChildren() {
            return !children.isEmpty();
        }
    }

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_view_tree_node, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            TreeNode node = displayNodes.get(position);

            holder.tvTitle.setText(node.viewBean.id);

            String typeName = ViewBean.getViewTypeName(node.viewBean.type);
            
            if (node.viewBean.customView != null && !node.viewBean.customView.isEmpty() && !node.viewBean.customView.equals("none") && !node.viewBean.customView.equals("NONE")) {
                typeName += " (" + node.viewBean.customView + ")";
            }
            holder.tvSubtitle.setText(typeName);
            
            holder.imgIcon.setImageResource(ViewBean.getViewTypeResId(node.viewBean.type)); 

            int paddingBase = SketchwareUtil.dpToPx(16);
            int paddingDepth = SketchwareUtil.dpToPx(node.depth * 24);
            holder.rootLayout.setPadding(paddingBase + paddingDepth, holder.rootLayout.getPaddingTop(),
                    holder.rootLayout.getPaddingRight(), holder.rootLayout.getPaddingBottom());

            if (node.hasChildren()) {
                holder.imgExpand.setVisibility(View.VISIBLE);
                holder.imgExpand.setRotation(node.isExpanded ? 90f : 0f);
            } else {
                holder.imgExpand.setVisibility(View.INVISIBLE);
            }

            holder.imgExpand.setOnClickListener(v -> {
                node.isExpanded = !node.isExpanded;
                holder.imgExpand.animate().rotation(node.isExpanded ? 90f : 0f).setDuration(200).start();
                refreshDisplayList();
                notifyDataSetChanged();
            });

            holder.rootLayout.setOnClickListener(v -> {
                listener.onSelected(node.viewBean.id);
                dismiss();
            });
        }

        @Override
        public int getItemCount() {
            return displayNodes.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout rootLayout;
            TextView tvTitle, tvSubtitle;
            ImageView imgExpand, imgIcon;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                rootLayout = itemView.findViewById(R.id.root_layout);
                imgExpand = itemView.findViewById(R.id.img_expand);
                imgIcon = itemView.findViewById(R.id.img_icon);
                tvTitle = itemView.findViewById(R.id.tv_title);
                tvSubtitle = itemView.findViewById(R.id.tv_subtitle);
            }
        }
    }
}
