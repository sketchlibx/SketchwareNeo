package com.besome.sketch.editor.view;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScroller;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import com.besome.sketch.beans.ViewBean;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Objects;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ViewTreeDrawer {

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
        default boolean onMoveRequested(String sourceViewId, String targetParentId) { return false; }
        default boolean onTreeActionRequested(String sourceViewId, String action, String targetViewId) { return false; }
        default ArrayList<ViewBean> onReloadViews() { return null; }
    }

    private final ArrayList<ViewBean> currentViews;
    private FragmentActivity hostActivity;
    private FrameLayout overlay;
    private View drawerRoot;
    private OnBackPressedCallback backCallback;
    private final OnViewSelectedListener listener;
    private final String selectedViewId;
    private final String layoutFileName;

    private final List<TreeNode> rootNodes = new ArrayList<>();
    private final List<TreeNode> displayNodes = new ArrayList<>();
    
    private TreeAdapter adapter;
    private String searchQuery = "";
    private RecyclerView recyclerView;
    private HorizontalScrollView hsvTree;
    private TextView tvNoResults;
    private TextView tvCountBadge;
    private TextView tvTreeSubtitle;
    private ItemTouchHelper itemTouchHelper;
    private TreeNode draggedNode;
    private TreeNode dropTargetNode;
    private int draggedPosition = RecyclerView.NO_POSITION;
    private int dropTargetPosition = RecyclerView.NO_POSITION;

    public ViewTreeDrawer(ArrayList<ViewBean> views, OnViewSelectedListener listener, @Nullable String selectedViewId) {
        this(views, listener, selectedViewId, null);
    }

    public ViewTreeDrawer(ArrayList<ViewBean> views, OnViewSelectedListener listener,
                          @Nullable String selectedViewId, @Nullable String layoutFileName) {
        this.currentViews = views == null ? new ArrayList<>() : new ArrayList<>(views);
        this.listener = listener;
        this.selectedViewId = selectedViewId;
        this.layoutFileName = layoutFileName;
    }

    public void show(@NonNull FragmentActivity activity) {
        if (overlay != null || activity.isFinishing() || activity.isDestroyed()) return;
        hostActivity = activity;
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) return;

        overlay = new FrameLayout(activity);
        overlay.setFocusableInTouchMode(true);
        overlay.setClickable(true);
        View scrim = new View(activity);
        scrim.setBackgroundColor(0x66000000);
        scrim.setOnClickListener(v -> dismiss());
        overlay.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerRoot = createView();
        View sheetHandle = drawerRoot.findViewById(R.id.tree_sheet_handle);
        if (sheetHandle != null) {
            float[] touchStartY = new float[1];
            sheetHandle.setOnTouchListener((view, event) -> {
                if (drawerRoot == null) return true;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        touchStartY[0] = event.getRawY();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        float offset = Math.max(0f, event.getRawY() - touchStartY[0]);
                        drawerRoot.setTranslationY(offset);
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (drawerRoot.getTranslationY() > SketchwareUtil.dpToPx(96)) {
                            dismiss();
                        } else {
                            drawerRoot.animate().translationY(0f).setDuration(180L).start();
                        }
                        return true;
                    }
                    default -> { return true; }
                }
            });
        }
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        int sheetHeight = Math.round(metrics.heightPixels * 0.88f);
        FrameLayout.LayoutParams drawerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, sheetHeight, Gravity.BOTTOM);
        overlay.addView(drawerRoot, drawerParams);
        ((ViewGroup) content).addView(overlay, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        overlay.bringToFront();
        overlay.requestFocus();
        drawerRoot.setTranslationY(sheetHeight);
        drawerRoot.animate().translationY(0f).setDuration(240L).start();

        backCallback = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { dismiss(); }
        };
        activity.getOnBackPressedDispatcher().addCallback(activity, backCallback);
    }

    public boolean isShowing() {
        return overlay != null;
    }

    public void dismiss() {
        if (overlay == null) return;
        FrameLayout oldOverlay = overlay;
        overlay = null;
        if (backCallback != null) {
            backCallback.remove();
            backCallback = null;
        }
        View oldDrawer = drawerRoot;
        drawerRoot = null;
        if (oldDrawer != null && oldDrawer.getHeight() > 0) {
            oldDrawer.animate().translationY(oldDrawer.getHeight()).setDuration(180L)
                    .withEndAction(() -> removeOverlay(oldOverlay)).start();
        } else {
            removeOverlay(oldOverlay);
        }
    }

    private void removeOverlay(FrameLayout oldOverlay) {
        ViewParent parent = oldOverlay.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(oldOverlay);
    }

    private View createView() {
        LayoutInflater inflater = LayoutInflater.from(hostActivity);
        View root = inflater.inflate(R.layout.layout_view_tree_drawer, null, false);

        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopLeftCornerSize(SketchwareUtil.dpToPx(26))
                .setTopRightCornerSize(SketchwareUtil.dpToPx(26))
                .setBottomLeftCornerSize(0)
                .setBottomRightCornerSize(0)
                .build();
        MaterialShapeDrawable bg = new MaterialShapeDrawable(shape);
        bg.setFillColor(ColorStateList.valueOf(ThemeUtils.getColor(hostActivity, R.attr.colorSurfaceContainerLow)));
        root.setBackground(bg);

        tvCountBadge = root.findViewById(R.id.tv_count_badge);
        tvTreeSubtitle = root.findViewById(R.id.tv_tree_subtitle);
        EditText etSearch = root.findViewById(R.id.et_search);
        recyclerView = root.findViewById(R.id.recycler_view);
        hsvTree = root.findViewById(R.id.hsv_tree);
        tvNoResults = root.findViewById(R.id.tv_no_results);

        updateTreeHeader();
        
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

        LinearLayoutManager layoutManager = new LinearLayoutManager(hostActivity) {
            @Override
            public void smoothScrollToPosition(RecyclerView recyclerView, RecyclerView.State state, int position) {
                LinearSmoothScroller scroller = new LinearSmoothScroller(hostActivity) {
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
        DefaultItemAnimator treeAnimator = new DefaultItemAnimator();
        treeAnimator.setAddDuration(0);
        treeAnimator.setRemoveDuration(0);
        treeAnimator.setChangeDuration(0);
        treeAnimator.setMoveDuration(170);
        treeAnimator.setSupportsChangeAnimations(false);
        recyclerView.setItemAnimator(treeAnimator);
        recyclerView.setItemViewCacheSize(30);
        recyclerView.setHasFixedSize(false);
        
        buildTree();
        rebuildDisplayList();
        adapter = new TreeAdapter();
        recyclerView.setAdapter(adapter);
        adapter.submitList(new ArrayList<>(displayNodes));

        ItemTouchHelper.Callback dragCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder sourceHolder,
                                  @NonNull RecyclerView.ViewHolder targetHolder) {
                int sourcePosition = sourceHolder.getBindingAdapterPosition();
                int targetPosition = targetHolder.getBindingAdapterPosition();
                if (sourcePosition == RecyclerView.NO_POSITION || targetPosition == RecyclerView.NO_POSITION
                        || sourcePosition >= adapter.currentList.size() || targetPosition >= adapter.currentList.size()) {
                    dropTargetNode = null;
                    return false;
                }
                if (draggedNode == null) {
                    draggedNode = adapter.currentList.get(sourcePosition);
                    draggedPosition = sourcePosition;
                }
                TreeNode target = adapter.currentList.get(targetPosition);
                boolean moveInto = isValidDropTarget(draggedNode, target)
                        && !target.viewBean.id.equals(draggedNode.viewBean.parent);
                boolean reorderSibling = target != null && target.viewBean != null
                        && draggedNode != null && draggedNode.viewBean != null
                        && !target.viewBean.id.equals(draggedNode.viewBean.id)
                        && normalizeParentId(target.viewBean.parent).equals(normalizeParentId(draggedNode.viewBean.parent));
                if (moveInto || reorderSibling) {
                    dropTargetNode = target;
                    dropTargetPosition = targetPosition;
                    adapter.moveItem(sourcePosition, targetPosition);
                    return true;
                }
                dropTargetNode = null;
                dropTargetPosition = RecyclerView.NO_POSITION;
                return false;
            }

            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

            @Override
            public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    int position = viewHolder.getBindingAdapterPosition();
                    if (position != RecyclerView.NO_POSITION && position < adapter.currentList.size()) {
                        draggedNode = adapter.currentList.get(position);
                        draggedPosition = position;
                    }
                    dropTargetNode = null;
                    dropTargetPosition = RecyclerView.NO_POSITION;
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                TreeNode source = draggedNode;
                TreeNode target = dropTargetNode;
                int sourcePosition = adapter == null ? RecyclerView.NO_POSITION : adapter.currentList.indexOf(source);
                int targetPosition = adapter == null ? RecyclerView.NO_POSITION : adapter.currentList.indexOf(target);
                draggedNode = null;
                dropTargetNode = null;
                draggedPosition = RecyclerView.NO_POSITION;
                dropTargetPosition = RecyclerView.NO_POSITION;
                if (source == null || target == null || source == target
                        || source.viewBean == null || target.viewBean == null
                        || source.viewBean.id == null || target.viewBean.id == null
                        || source.viewBean.id.equals(target.viewBean.id)) return;
                boolean moved;
                if (isValidDropTarget(source, target) && !target.viewBean.id.equals(source.viewBean.parent)) {
                    moved = listener != null && listener.onMoveRequested(source.viewBean.id, target.viewBean.id);
                } else if (normalizeParentId(source.viewBean.parent).equals(normalizeParentId(target.viewBean.parent))
                        && sourcePosition != RecyclerView.NO_POSITION && targetPosition != RecyclerView.NO_POSITION) {
                    String action = sourcePosition > targetPosition ? "MOVE_AFTER" : "MOVE_BEFORE";
                    moved = listener != null && listener.onTreeActionRequested(source.viewBean.id, action, target.viewBean.id);
                } else {
                    moved = false;
                }
                if (moved) {
                    refreshTreeFromSource();
                } else {
                    SketchwareUtil.toastError("The component could not be moved to that position");
                }
            }
        };
        itemTouchHelper = new ItemTouchHelper(dragCallback);
        itemTouchHelper.attachToRecyclerView(recyclerView);

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

        return root;
    }

    private void refreshTreeFromSource() {
        ArrayList<ViewBean> refreshed = listener == null ? null : listener.onReloadViews();
        if (refreshed != null) {
            currentViews.clear();
            currentViews.addAll(refreshed);
        }
        updateTreeHeader();
        buildTree();
        rebuildDisplayList();
        if (adapter != null) adapter.submitList(new ArrayList<>(displayNodes));
    }

    private void updateTreeHeader() {
        if (tvCountBadge != null) tvCountBadge.setText(String.valueOf(currentViews.size()));
        if (tvTreeSubtitle != null) {
            String fileName = layoutFileName == null ? "" : layoutFileName;
            int slash = fileName.lastIndexOf('/');
            if (slash >= 0) fileName = fileName.substring(slash + 1);
            tvTreeSubtitle.setText(currentViews.size() + " views" + (fileName.isEmpty() ? "" : " on " + fileName));
        }
    }

    private void buildTree() {
        rootNodes.clear();
        HashMap<String, List<ViewBean>> childrenMap = new HashMap<>();
        Set<String> knownIds = new HashSet<>();
        List<ViewBean> roots = new ArrayList<>();

        for (ViewBean bean : currentViews) {
            if (bean != null && bean.id != null && !bean.id.isEmpty()) knownIds.add(bean.id);
        }
        for (ViewBean bean : currentViews) {
            if (bean == null || bean.id == null || bean.id.isEmpty()) continue;
            if (bean.parent == null || bean.parent.equals("root") || bean.parent.isEmpty()
                    || !knownIds.contains(bean.parent)) {
                roots.add(bean);
            } else {
                childrenMap.computeIfAbsent(bean.parent, k -> new ArrayList<>()).add(bean);
            }
        }
        roots.sort(Comparator.comparingInt(bean -> bean.index));
        for (List<ViewBean> siblings : childrenMap.values()) {
            siblings.sort(Comparator.comparingInt(bean -> bean.index));
        }

        Set<String> visited = new HashSet<>();
        for (ViewBean root : roots) {
            TreeNode node = createNode(root, childrenMap, 0, true, new ArrayList<>(), new HashSet<>(), visited);
            if (node != null) rootNodes.add(node);
        }

        for (ViewBean bean : currentViews) {
            if (bean == null || bean.id == null || bean.id.isEmpty() || visited.contains(bean.id)) continue;
            TreeNode node = createNode(bean, childrenMap, 0, true, new ArrayList<>(), new HashSet<>(), visited);
            if (node != null) rootNodes.add(node);
        }
        if (tvNoResults != null) tvNoResults.setVisibility(rootNodes.isEmpty() ? View.VISIBLE : View.GONE);
        if (tvNoResults != null && rootNodes.isEmpty()) tvNoResults.setText("No components to display");
    }

    private TreeNode createNode(ViewBean view, HashMap<String, List<ViewBean>> childrenMap,
                                int depth, boolean isLastChild, List<Boolean> parentIsLastList,
                                Set<String> ancestry, Set<String> visited) {
        if (view == null || view.id == null || view.id.isEmpty()
                || ancestry.contains(view.id) || visited.contains(view.id)) return null;
        ancestry.add(view.id);
        visited.add(view.id);
        TreeNode node = new TreeNode(view, depth, isLastChild, parentIsLastList);
        node.isExpanded = true;
        List<ViewBean> children = childrenMap.get(view.id);
        if (children != null) {
            List<Boolean> newParentList = new ArrayList<>(parentIsLastList);
            newParentList.add(isLastChild);
            for (int i = 0; i < children.size(); i++) {
                ViewBean child = children.get(i);
                boolean childIsLast = (i == children.size() - 1);
                TreeNode childNode = createNode(child, childrenMap, depth + 1, childIsLast,
                        newParentList, ancestry, visited);
                if (childNode != null) node.children.add(childNode);
            }
        }
        ancestry.remove(view.id);
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
            if (node == null || node.viewBean == null) continue;
            String id = node.viewBean.id == null ? "" : node.viewBean.id;
            String className = node.viewBean.convert == null ? "" : node.viewBean.convert;
            int classSeparator = className.lastIndexOf('.');
            if (classSeparator >= 0) className = className.substring(classSeparator + 1);
            String displayType = ViewBean.getDisplayViewTypeName(node.viewBean);
            boolean selfMatch = id.toLowerCase(Locale.getDefault()).contains(query)
                    || displayType.toLowerCase(Locale.getDefault()).contains(query)
                    || className.toLowerCase(Locale.getDefault()).contains(query);
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
                TreeNode node = displayNodes.get(finalI);
                
                recyclerView.post(() -> {
                    recyclerView.smoothScrollToPosition(finalI);
                    
                    if (hsvTree != null) {
                        int targetX = node.depth * SketchwareUtil.dpToPx(20) - SketchwareUtil.dpToPx(32);
                        hsvTree.smoothScrollTo(Math.max(targetX, 0), 0);
                    }
                });
                break;
            }
        }
    }

    private String normalizeParentId(String parentId) {
        return parentId == null || parentId.isEmpty() || "root".equals(parentId) ? "root" : parentId;
    }

    private boolean isValidDropTarget(TreeNode source, TreeNode target) {
        if (source == null || target == null || source.viewBean == null || target.viewBean == null) return false;
        if (source.viewBean.id == null || target.viewBean.id == null
                || source.viewBean.id.equals(target.viewBean.id) || source.viewBean.id.startsWith("_")) return false;
        if (!isContainer(target.viewBean)) return false;
        return !containsDescendant(source, target.viewBean.id);
    }

    private boolean containsDescendant(TreeNode source, String id) {
        if (source.viewBean != null && id.equals(source.viewBean.id)) return true;
        for (TreeNode child : source.children) if (containsDescendant(child, id)) return true;
        return false;
    }

    private boolean isContainer(ViewBean bean) {
        String convert = bean.convert == null ? "" : bean.convert;
        String name = convert.substring(convert.lastIndexOf('.') + 1);
        return switch (name) {
            case "LinearLayout", "RelativeLayout", "ConstraintLayout", "CoordinatorLayout", "FrameLayout",
                 "ScrollView", "NestedScrollView", "HorizontalScrollView", "RadioGroup", "TableLayout",
                 "TableRow", "GridLayout", "CardView", "MaterialCardView", "CollapsingToolbarLayout",
                 "TextInputLayout", "SwipeRefreshLayout", "TabLayout", "BottomNavigationView", "ChipGroup",
                 "MaterialButtonToggleGroup", "ViewPager", "ViewPager2", "RecyclerView", "ListView", "GridView" -> true;
            default -> false;
        };
    }

    private int getViewIcon(ViewBean bean) {
        return ViewBean.getViewTypeResId(bean);
    }

    public static class TreeIndentView extends View {
        private final int indentWidth;

        public TreeIndentView(Context context) {
            super(context);
            indentWidth = SketchwareUtil.dpToPx(20);
        }

        public void bind(int depth, boolean isLastChild, List<Boolean> parentIsLastList) {
            ViewGroup.LayoutParams lp = getLayoutParams();
            if (lp != null) {
                lp.width = depth > 0 ? depth * indentWidth : 0;
                setLayoutParams(lp);
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

        void moveItem(int fromPosition, int toPosition) {
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= currentList.size()
                    || toPosition >= currentList.size() || fromPosition == toPosition) return;
            ArrayList<TreeNode> updated = new ArrayList<>(currentList);
            TreeNode moved = updated.remove(fromPosition);
            updated.add(toPosition, moved);
            currentList = updated;
            notifyItemMoved(fromPosition, toPosition);
        }

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
            View view = LayoutInflater.from(hostActivity).inflate(R.layout.item_view_tree_node, parent, false);
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
            ImageView imgArrow, imgIcon, imgMore, imgDrag;
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
                imgMore = v.findViewById(R.id.img_more);
                imgDrag = v.findViewById(R.id.img_drag);
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

                imgIcon.setImageResource(getViewIcon(node.viewBean));
                int iconTint = isSelected ? ThemeUtils.getColor(hostActivity, R.attr.colorPrimary)
                        : ThemeUtils.getColor(hostActivity, R.attr.colorOnSurfaceVariant);
                imgIcon.setColorFilter(iconTint);

                tvId.setText(highlight(node.viewBean.id, query,
                        ThemeUtils.getColor(hostActivity, R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(hostActivity, R.attr.colorPrimary)));

                String typeName = ViewBean.getDisplayViewTypeName(node.viewBean);
                if (node.viewBean.customView != null && !node.viewBean.customView.isEmpty() && !node.viewBean.customView.equals("none")) {
                    typeName += " (" + node.viewBean.customView + ")";
                }
                tvType.setText(highlight(typeName, query,
                        ThemeUtils.getColor(hostActivity, R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(hostActivity, R.attr.colorPrimary)));

                if (isSelected) {
                    tvId.setTextColor(ThemeUtils.getColor(hostActivity, R.attr.colorPrimary));
                    tvType.setTextColor(ThemeUtils.getColor(hostActivity, R.attr.colorPrimary));
                } else {
                    tvId.setTextColor(ThemeUtils.getColor(hostActivity, R.attr.colorOnSurface));
                    tvType.setTextColor(ThemeUtils.getColor(hostActivity, R.attr.colorOnSurfaceVariant));
                }

                rootLayout.setOnClickListener(v -> {
                    if (listener != null && node.viewBean != null && node.viewBean.id != null) {
                        listener.onSelected(node.viewBean.id);
                        dismiss();
                    }
                });
                rootLayout.setOnLongClickListener(null);
                imgMore.setOnClickListener(v -> showNodeActions(v, node));
                imgDrag.setOnLongClickListener(v -> {
                    if (itemTouchHelper != null && getBindingAdapterPosition() != RecyclerView.NO_POSITION) {
                        itemTouchHelper.startDrag(this);
                        return true;
                    }
                    return false;
                });
            }

            private void showNodeActions(View anchor, TreeNode node) {
                PopupMenu popup = new PopupMenu(hostActivity, anchor);
                List<TreeNode> siblings = getSiblings(node);
                int position = siblings.indexOf(node);
                MenuItem moveUp = popup.getMenu().add(0, 1, 0, "Move up");
                MenuItem moveDown = popup.getMenu().add(0, 2, 1, "Move down");
                String parentName = node.viewBean.parent == null || node.viewBean.parent.isEmpty()
                        || "root".equals(node.viewBean.parent) ? "root layout" : node.viewBean.parent;
                MenuItem moveOut = popup.getMenu().add(0, 3, 2, "Move out of " + parentName);
                TreeNode previousContainer = findPreviousContainer(node, siblings, position);
                String previousContainerName = previousContainer == null || previousContainer.viewBean == null
                        ? "layout above" : previousContainer.viewBean.id;
                MenuItem moveInto = popup.getMenu().add(0, 4, 3, "Move into " + previousContainerName);
                MenuItem copy = popup.getMenu().add(0, 5, 4, "Copy");
                MenuItem delete = popup.getMenu().add(0, 6, 5, "Delete");
                moveUp.setIcon(R.drawable.ic_mtrl_arrow_up);
                moveDown.setIcon(R.drawable.ic_mtrl_arrow_down);
                moveOut.setIcon(R.drawable.ic_mtrl_arrow_left);
                moveInto.setIcon(R.drawable.ic_mtrl_arrow_right);
                copy.setIcon(R.drawable.ic_mtrl_copy);
                delete.setIcon(R.drawable.ic_mtrl_delete);
                popup.setForceShowIcon(true);
                moveUp.setEnabled(position > 0);
                moveDown.setEnabled(position >= 0 && position < siblings.size() - 1);
                moveOut.setEnabled(node.viewBean.parent != null && !node.viewBean.parent.isEmpty()
                        && !"root".equals(node.viewBean.parent));
                moveInto.setEnabled(previousContainer != null);
                popup.setOnMenuItemClickListener(item -> {
                    String action;
                    String targetId = null;
                    switch (item.getItemId()) {
                        case 1 -> action = "MOVE_UP";
                        case 2 -> action = "MOVE_DOWN";
                        case 3 -> action = "MOVE_OUT";
                        case 4 -> {
                            action = "MOVE_INTO";
                            targetId = previousContainer == null ? null : previousContainer.viewBean.id;
                        }
                        case 5 -> action = "COPY";
                        case 6 -> action = "DELETE";
                        default -> { return false; }
                    }
                    if ("DELETE".equals(action)) {
                        if (listener != null) listener.onTreeActionRequested(node.viewBean.id, action, targetId);
                        dismiss();
                        return true;
                    }
                    boolean success = listener != null && listener.onTreeActionRequested(node.viewBean.id, action, targetId);
                    if (success) refreshTreeFromSource();
                    return true;
                });
                popup.show();
            }

            private List<TreeNode> getSiblings(TreeNode node) {
                ArrayList<TreeNode> siblings = new ArrayList<>();
                collectSiblings(rootNodes, node, siblings);
                siblings.sort((first, second) -> Integer.compare(first.viewBean.index, second.viewBean.index));
                return siblings;
            }

            private void collectSiblings(List<TreeNode> nodes, TreeNode target, List<TreeNode> result) {
                for (TreeNode candidate : nodes) {
                    if (candidate == target) {
                        result.addAll(nodes);
                        return;
                    }
                    collectSiblings(candidate.children, target, result);
                    if (!result.isEmpty()) return;
                }
            }

            private TreeNode findPreviousContainer(TreeNode node, List<TreeNode> siblings, int position) {
                if (position <= 0) return null;
                for (int index = position - 1; index >= 0; index--) {
                    TreeNode candidate = siblings.get(index);
                    if (isContainer(candidate.viewBean)) return candidate;
                }
                return null;
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
