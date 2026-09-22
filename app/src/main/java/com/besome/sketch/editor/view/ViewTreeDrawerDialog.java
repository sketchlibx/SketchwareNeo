package com.besome.sketch.editor.view;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ViewTreeDrawerDialog extends DialogFragment {

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
    }

    private final ArrayList<ViewBean> currentViews;
    private final OnViewSelectedListener listener;
    private final String selectedViewId;

    private final List<TreeNode> rootNodes = new ArrayList<>();
    private final List<TreeNode> displayNodes = new ArrayList<>();
    
    private TreeAdapter adapter;
    private String searchQuery = "";
    private RecyclerView recyclerView;
    private TextView tvNoResults;

    public ViewTreeDrawerDialog(ArrayList<ViewBean> views, OnViewSelectedListener listener, @Nullable String selectedViewId) {
        this.currentViews = views;
        this.listener = listener;
        this.selectedViewId = selectedViewId;
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
        Window window = getDialog() != null ? getDialog().getWindow() : null;
        if (window != null) {
            window.setLayout(SketchwareUtil.dpToPx(320), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog);

            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.4f;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.layout_view_tree_dialog, container, false);

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopRightCornerSize(SketchwareUtil.dpToPx(24))
                .setBottomRightCornerSize(SketchwareUtil.dpToPx(24))
                .build();
        MaterialShapeDrawable bg = new MaterialShapeDrawable(shape);
        bg.setFillColor(android.content.res.ColorStateList.valueOf(ThemeUtils.getColor(requireContext(), R.attr.colorSurfaceContainerLow)));
        root.setBackground(bg);

        TextView tvCountBadge = root.findViewById(R.id.tv_count_badge);
        EditText etSearch = root.findViewById(R.id.et_search);
        recyclerView = root.findViewById(R.id.recycler_view);
        tvNoResults = root.findViewById(R.id.tv_no_results);

        tvCountBadge.setText(String.valueOf(currentViews.size()));
        
        ((View) root.findViewById(R.id.tv_count_badge).getParent()).setOnLongClickListener(v -> {
            boolean anyExpanded = false;
            for (TreeNode node : rootNodes) {
                if (hasExpandedNode(node)) { anyExpanded = true; break; }
            }
            setAllExpanded(rootNodes, !anyExpanded);
            rebuildDisplayList();
            adapter.submitList(new ArrayList<>(displayNodes));
            return true;
        });

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext()) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller scroller = new LinearSmoothScroller(requireContext()) {
                    @Override
                    protected float calculateSpeedPerPixel(DisplayMetrics displayMetrics) {
                        return 0.2f; 
                    }
                };
                scroller.setTargetPosition(position);
                startSmoothScroll(scroller);
            }
        };
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemViewCacheSize(30);
        recyclerView.setHasFixedSize(true);
        
        buildTree();
        rebuildDisplayList();
        adapter = new TreeAdapter();
        recyclerView.setAdapter(adapter);
        adapter.submitList(new ArrayList<>(displayNodes));
        
        scrollToSelected();

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                applyFilter();
            }
        });

        recyclerView.setAlpha(0f);
        recyclerView.animate().alpha(1f).setDuration(250).setStartDelay(50).start();

        return root;
    }

    private void buildTree() {
        rootNodes.clear();
        HashMap<String, List<ViewBean>> childrenMap = new HashMap<>();
        List<ViewBean> roots = new ArrayList<>();

        for (ViewBean bean : currentViews) {
            if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()) {
                roots.add(bean);
            } else {
                childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
            }
        }
        for (int i = 0; i < roots.size(); i++) {
            boolean isLast = (i == roots.size() - 1);
            rootNodes.add(createNode(roots.get(i), childrenMap, 0, isLast, new ArrayList<>()));
        }
    }

    private TreeNode createNode(ViewBean view, HashMap<String, List<ViewBean>> childrenMap, int depth, boolean isLastChild, List<Boolean> parentIsLastList) {
        TreeNode node = new TreeNode(view, depth, isLastChild, parentIsLastList);
        node.isExpanded = true;
        List<ViewBean> children = childrenMap.get(view.id);
        if (children != null) {
            List<Boolean> newParentList = new ArrayList<>(parentIsLastList);
            newParentList.add(isLastChild);
            for (int i = 0; i < children.size(); i++) {
                boolean childIsLast = (i == children.size() - 1);
                node.children.add(createNode(children.get(i), childrenMap, depth + 1, childIsLast, newParentList));
            }
        }
        return node;
    }

    private void rebuildDisplayList() {
        displayNodes.clear();
        for (TreeNode root : rootNodes) addNodeToDisplay(root);
    }

    private void addNodeToDisplay(TreeNode node) {
        displayNodes.add(node);
        if (node.isExpanded) {
            for (TreeNode child : node.children) addNodeToDisplay(child);
        }
    }

    private void applyFilter() {
        if (searchQuery.isEmpty()) {
            rebuildDisplayList();
        } else {
            markMatchingNodes(rootNodes, searchQuery);
            displayNodes.clear();
            for (TreeNode root : rootNodes) addFilteredNode(root);
        }

        boolean empty = displayNodes.isEmpty();
        tvNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        adapter.submitList(new ArrayList<>(displayNodes));
    }

    private boolean markMatchingNodes(List<TreeNode> nodes, String query) {
        boolean anyMatch = false;
        for (TreeNode node : nodes) {
            boolean selfMatch = node.viewBean.id.toLowerCase(Locale.getDefault()).contains(query)
                    || ViewBean.getViewTypeName(node.viewBean.type).toLowerCase(Locale.getDefault()).contains(query);
            boolean childMatch = markMatchingNodes(node.children, query);
            node.matchesSearch = selfMatch || childMatch;
            if (node.matchesSearch) {
                node.isExpanded = true; 
                anyMatch = true;
            }
        }
        return anyMatch;
    }

    private void addFilteredNode(TreeNode node) {
        if (!node.matchesSearch) return;
        displayNodes.add(node);
        if (node.isExpanded) {
            for (TreeNode child : node.children) addFilteredNode(child);
        }
    }

    private boolean hasExpandedNode(TreeNode node) {
        if (node.isExpanded && node.hasChildren()) return true;
        for (TreeNode child : node.children) {
            if (hasExpandedNode(child)) return true;
        }
        return false;
    }

    private void setAllExpanded(List<TreeNode> nodes, boolean expanded) {
        for (TreeNode node : nodes) {
            node.isExpanded = expanded;
            setAllExpanded(node.children, expanded);
        }
    }

    private void scrollToSelected() {
        if (selectedViewId == null || selectedViewId.isEmpty()) return;
        for (int i = 0; i < displayNodes.size(); i++) {
            if (selectedViewId.equals(displayNodes.get(i).viewBean.id)) {
                int finalI = i;
                recyclerView.post(() -> recyclerView.smoothScrollToPosition(finalI));
                break;
            }
        }
    }

    public static class TreeIndentView extends View {
        private int depth = 0;
        private boolean isLastChild = false;
        private List<Boolean> parentIsLastList = new ArrayList<>();
        private final Paint paint;
        private final int indentWidth;

        public TreeIndentView(Context context) {
            super(context);
            paint = new Paint();
            paint.setColor(ThemeUtils.getColor(context, R.attr.colorOutlineVariant));
            paint.setStrokeWidth(SketchwareUtil.dpToPx(1.5f));
            paint.setStyle(Paint.Style.STROKE);
            paint.setAntiAlias(true);
            indentWidth = SketchwareUtil.dpToPx(24); 
        }

        public void bind(int depth, boolean isLastChild, List<Boolean> parentIsLastList) {
            this.depth = depth;
            this.isLastChild = isLastChild;
            this.parentIsLastList = parentIsLastList;
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null) {
                lp.width = depth > 0 ? depth * indentWidth : 0;
                setLayoutParams(lp);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (depth == 0) return;
            int h = getHeight();
            int halfH = h / 2;
            int w = indentWidth;

            for (int i = 0; i < depth; i++) {
                int x = i * w + w / 2;
                if (i == depth - 1) {
                    canvas.drawLine(x, 0, x, halfH, paint);
                    if (!isLastChild) {
                        canvas.drawLine(x, halfH, x, h, paint);
                    }
                    canvas.drawLine(x, halfH, x + w / 2, halfH, paint);
                } else {
                    if (parentIsLastList.size() > i && !parentIsLastList.get(i)) {
                        canvas.drawLine(x, 0, x, h, paint);
                    }
                }
            }
        }
    }

    private static class TreeNode {
        final ViewBean viewBean;
        final int depth;
        final boolean isLastChild;
        final List<Boolean> parentIsLastList;
        boolean isExpanded;
        boolean matchesSearch = true;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(ViewBean viewBean, int depth, boolean isLastChild, List<Boolean> parentIsLastList) {
            this.viewBean = viewBean;
            this.depth = depth;
            this.isLastChild = isLastChild;
            this.parentIsLastList = parentIsLastList;
        }
        boolean hasChildren() { return !children.isEmpty(); }
    }

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.VH> {
        private List<TreeNode> currentList = new ArrayList<>();

        void submitList(List<TreeNode> newList) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return currentList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    return currentList.get(oldPos).viewBean.id.equals(newList.get(newPos).viewBean.id);
                }
                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    TreeNode o = currentList.get(oldPos);
                    TreeNode n = newList.get(newPos);
                    return o.isExpanded == n.isExpanded && o.depth == n.depth && o.matchesSearch == n.matchesSearch;
                }
            });
            currentList = newList;
            diff.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(requireContext()).inflate(R.layout.item_view_tree_node, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TreeNode node = currentList.get(position);
            boolean isSelected = selectedViewId != null && selectedViewId.equals(node.viewBean.id);
            holder.bind(node, isSelected, searchQuery);
        }

        @Override
        public int getItemCount() { return currentList.size(); }

        class VH extends RecyclerView.ViewHolder {
            View rootLayout;
            FrameLayout indentContainer;
            TreeIndentView indentView;
            ImageView imgArrow, imgIcon;
            TextView tvId, tvType;

            VH(View v) {
                super(v);
                rootLayout = v;
                indentContainer = v.findViewById(R.id.view_indent_container);
                indentView = new TreeIndentView(v.getContext());
                indentContainer.addView(indentView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                
                imgArrow = v.findViewById(R.id.img_expand);
                imgIcon = v.findViewById(R.id.img_icon);
                tvId = v.findViewById(R.id.tv_title);
                tvType = v.findViewById(R.id.tv_subtitle);
            }

            void bind(TreeNode node, boolean isSelected, String query) {
                indentView.bind(node.depth, node.isLastChild, node.parentIsLastList);

                if (node.hasChildren()) {
                    imgArrow.setVisibility(View.VISIBLE);
                    imgArrow.setRotation(node.isExpanded ? 90f : 0f);
                    imgArrow.setOnClickListener(v -> {
                        boolean expanding = !node.isExpanded;
                        node.isExpanded = expanding;

                        ObjectAnimator.ofFloat(imgArrow, "rotation", expanding ? 0f : 90f, expanding ? 90f : 0f)
                                .setDuration(200)
                                .start();

                        if (searchQuery.isEmpty()) {
                            rebuildDisplayList();
                        } else {
                            markMatchingNodes(rootNodes, searchQuery);
                            displayNodes.clear();
                            for (TreeNode r : rootNodes) addFilteredNode(r);
                        }
                        submitList(new ArrayList<>(displayNodes));
                    });
                } else {
                    imgArrow.setVisibility(View.INVISIBLE);
                    imgArrow.setOnClickListener(null);
                }

                imgIcon.setImageResource(ViewBean.getViewTypeResId(node.viewBean.type));
                int iconTint = isSelected ? ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)
                        : ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant);
                imgIcon.setColorFilter(iconTint);

                tvId.setText(highlight(node.viewBean.id, query,
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)));

                String typeName = ViewBean.getViewTypeName(node.viewBean.type);
                if (node.viewBean.customView != null && !node.viewBean.customView.isEmpty() && !node.viewBean.customView.equals("none")) {
                    typeName += " (" + node.viewBean.customView + ")";
                }
                tvType.setText(highlight(typeName, query,
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)));

                if (isSelected) {
                    tvId.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
                    tvType.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
                } else {
                    tvId.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
                    tvType.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
                }

                rootLayout.setOnClickListener(v -> {
                    listener.onSelected(node.viewBean.id);
                    dismiss();
                });
            }

            private CharSequence highlight(String text, String query, int bgColor, int fgColor) {
                if (query.isEmpty() || text == null) return text != null ? text : "";
                SpannableString ss = new SpannableString(text);
                String lower = text.toLowerCase(Locale.getDefault());
                int idx = 0;
                while ((idx = lower.indexOf(query, idx)) != -1) {
                    ss.setSpan(new BackgroundColorSpan(bgColor), idx, idx + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ss.setSpan(new ForegroundColorSpan(fgColor), idx, idx + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    idx += query.length();
                }
                return ss;
            }
        }
    }
}
