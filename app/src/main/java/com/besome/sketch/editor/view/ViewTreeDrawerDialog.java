package com.besome.sketch.editor.view;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ViewBean;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * ViewTreeDrawerDialog — Professional Android-IDE-style Component Tree.
 *
 * Features:
 *  • Full-screen height side drawer (START gravity)
 *  • Live search with highlighted matches
 *  • Expand / collapse with smooth rotation + height animation
 *  • Currently-selected view is highlighted with a tinted card
 *  • No text truncation — HorizontalScrollView handles deep nesting
 *  • DiffUtil for smooth, flicker-free list updates
 *  • "Expand All" / "Collapse All" via long-press on header
 */
public class ViewTreeDrawerDialog extends DialogFragment {

    // ─── Public interface ───────────────────────────────────────────────────

    public interface OnViewSelectedListener {
        void onSelected(String viewId);
    }

    // ─── Fields ─────────────────────────────────────────────────────────────

    private final ArrayList<ViewBean> currentViews;
    private final OnViewSelectedListener listener;
    private final String selectedViewId;          // currently active view — may be null

    private final List<TreeNode>  rootNodes    = new ArrayList<>();
    private final List<TreeNode>  displayNodes = new ArrayList<>();    // flat list fed to adapter
    private final List<TreeNode>  filteredNodes = new ArrayList<>();   // after search filter

    private TreeAdapter adapter;
    private String      searchQuery = "";

    private RecyclerView recyclerView;
    private TextView     tvNoResults;

    // ─── Constructor ────────────────────────────────────────────────────────

    public ViewTreeDrawerDialog(ArrayList<ViewBean> views,
                                OnViewSelectedListener listener,
                                @Nullable String selectedViewId) {
        this.currentViews   = views;
        this.listener       = listener;
        this.selectedViewId = selectedViewId;
    }

    /** Backwards-compatible constructor (no pre-selected view). */
    public ViewTreeDrawerDialog(ArrayList<ViewBean> views, OnViewSelectedListener listener) {
        this(views, listener, null);
    }

    // ─── Dialog window setup ────────────────────────────────────────────────

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
            window.setLayout(SketchwareUtil.dpToPx(300), ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.START);
            window.setWindowAnimations(R.style.Animation_Design_BottomSheetDialog);

            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount  = 0.4f;
            params.gravity    = Gravity.START;
            window.setAttributes(params);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
    }

    // ─── View creation ──────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Root container
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // Rounded right-side background
        ShapeAppearanceModel shape = ShapeAppearanceModel.builder()
                .setTopRightCornerSize(SketchwareUtil.getDip(20))
                .setBottomRightCornerSize(SketchwareUtil.getDip(20))
                .build();
        MaterialShapeDrawable bg = new MaterialShapeDrawable(shape);
        bg.setFillColor(ColorStateList.valueOf(
                ThemeUtils.getColor(requireContext(), R.attr.colorSurfaceContainerLow)));
        bg.initializeElevationOverlay(requireContext());
        root.setBackground(bg);
        root.setElevation(SketchwareUtil.dpToPx(6));

        // ── Header ──────────────────────────────────────────────────────────
        root.addView(buildHeader());

        // ── Search bar ──────────────────────────────────────────────────────
        root.addView(buildSearchBar());

        // ── Divider ─────────────────────────────────────────────────────────
        root.addView(buildDivider());

        // ── Tree (HorizontalScrollView → RecyclerView) ──────────────────────
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        LinearLayout.LayoutParams hsvParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        hsv.setLayoutParams(hsvParams);
        hsv.setFillViewport(true);
        hsv.setHorizontalScrollBarEnabled(true);

        FrameLayout hsvContent = new FrameLayout(requireContext());

        recyclerView = new RecyclerView(requireContext());
        FrameLayout.LayoutParams rvParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        recyclerView.setLayoutParams(rvParams);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setMinimumWidth(SketchwareUtil.dpToPx(290));
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(0, SketchwareUtil.dpToPx(4), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(24));
        recyclerView.setHasFixedSize(false);
        // Smooth scroll-in entrance
        recyclerView.setAlpha(0f);
        recyclerView.animate().alpha(1f).setDuration(220).setStartDelay(80).start();

        // "No results" overlay
        tvNoResults = new TextView(requireContext());
        tvNoResults.setText("No matching views");
        tvNoResults.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
        tvNoResults.setTextSize(14f);
        tvNoResults.setTypeface(null, Typeface.ITALIC);
        tvNoResults.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams noResParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        noResParams.topMargin = SketchwareUtil.dpToPx(40);
        tvNoResults.setLayoutParams(noResParams);
        tvNoResults.setVisibility(View.GONE);

        hsvContent.addView(recyclerView);
        hsvContent.addView(tvNoResults);
        hsv.addView(hsvContent, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(hsv);

        // ── Build data & adapter ─────────────────────────────────────────────
        buildTree();
        rebuildDisplayList();

        adapter = new TreeAdapter();
        recyclerView.setAdapter(adapter);

        // ✅ Initial population — without this, list stays empty until search is typed
        adapter.submitList(new ArrayList<>(displayNodes));

        // Scroll to selected item
        scrollToSelected();

        return root;
    }

    // ─── Header ─────────────────────────────────────────────────────────────

    private View buildHeader() {
        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        int ph = SketchwareUtil.dpToPx(16);
        int pv = SketchwareUtil.dpToPx(14);
        header.setPadding(ph, pv, ph, pv);

        // Icon
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(R.drawable.ic_mtrl_devices);
        icon.setColorFilter(ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                SketchwareUtil.dpToPx(22), SketchwareUtil.dpToPx(22));
        iconParams.setMarginEnd(SketchwareUtil.dpToPx(10));
        icon.setLayoutParams(iconParams);
        header.addView(icon);

        // Title
        TextView title = new TextView(requireContext());
        title.setText("Component Tree");
        title.setTextSize(16f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        title.setLayoutParams(titleParams);
        header.addView(title);

        // Count badge
        TextView countBadge = new TextView(requireContext());
        countBadge.setText(String.valueOf(currentViews.size()));
        countBadge.setTextSize(11f);
        countBadge.setTypeface(null, Typeface.BOLD);
        countBadge.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnPrimaryContainer));
        countBadge.setBackground(buildBadgeBackground());
        int bp = SketchwareUtil.dpToPx(6);
        int bpv = SketchwareUtil.dpToPx(2);
        countBadge.setPadding(bp, bpv, bp, bpv);
        header.addView(countBadge);

        // Expand-all / Collapse-all on long-press
        header.setOnLongClickListener(v -> {
            boolean anyExpanded = false;
            for (TreeNode node : rootNodes) {
                if (hasExpandedNode(node)) { anyExpanded = true; break; }
            }
            setAllExpanded(rootNodes, !anyExpanded);
            rebuildDisplayList();
            adapter.submitList(new ArrayList<>(displayNodes));
            return true;
        });

        return header;
    }

    private android.graphics.drawable.GradientDrawable buildBadgeBackground() {
        android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
        gd.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        gd.setCornerRadius(SketchwareUtil.dpToPx(10));
        gd.setColor(ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer));
        return gd;
    }

    // ─── Search bar ─────────────────────────────────────────────────────────

    private View buildSearchBar() {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int ph = SketchwareUtil.dpToPx(12);
        wrapper.setPadding(ph, 0, ph, SketchwareUtil.dpToPx(8));

        // Simple EditText wrapped in a card
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(SketchwareUtil.dpToPx(10));
        card.setCardElevation(0f);
        card.setStrokeWidth(SketchwareUtil.dpToPx(1));
        card.setStrokeColor(ThemeUtils.getColor(requireContext(), R.attr.colorOutlineVariant));
        card.setCardBackgroundColor(ThemeUtils.getColor(requireContext(), R.attr.colorSurfaceContainer));

        LinearLayout innerRow = new LinearLayout(requireContext());
        innerRow.setOrientation(LinearLayout.HORIZONTAL);
        innerRow.setGravity(Gravity.CENTER_VERTICAL);
        int ip = SketchwareUtil.dpToPx(10);
        innerRow.setPadding(ip, 0, ip, 0);

        ImageView searchIcon = new ImageView(requireContext());
        searchIcon.setImageResource(R.drawable.ic_mtrl_search);
        searchIcon.setColorFilter(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
        LinearLayout.LayoutParams siParams = new LinearLayout.LayoutParams(
                SketchwareUtil.dpToPx(18), SketchwareUtil.dpToPx(18));
        siParams.setMarginEnd(SketchwareUtil.dpToPx(8));
        searchIcon.setLayoutParams(siParams);
        innerRow.addView(searchIcon);

        android.widget.EditText searchEt = new android.widget.EditText(requireContext());
        searchEt.setHint("Search views…");
        searchEt.setHintTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
        searchEt.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
        searchEt.setTextSize(14f);
        searchEt.setBackground(null);
        searchEt.setSingleLine(true);
        searchEt.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0,
                SketchwareUtil.dpToPx(40), 1f);
        searchEt.setLayoutParams(etParams);
        innerRow.addView(searchEt);

        // Clear button
        ImageView clearBtn = new ImageView(requireContext());
        clearBtn.setImageResource(R.drawable.ic_mtrl_clear_all);
        clearBtn.setColorFilter(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
        clearBtn.setVisibility(View.GONE);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                SketchwareUtil.dpToPx(18), SketchwareUtil.dpToPx(18));
        clearBtn.setLayoutParams(cbParams);
        clearBtn.setOnClickListener(v -> {
            searchEt.setText("");
            clearBtn.setVisibility(View.GONE);
        });
        innerRow.addView(clearBtn);

        card.addView(innerRow, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        wrapper.addView(card);

        // Watcher
        searchEt.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                searchQuery = s.toString().trim().toLowerCase(Locale.getDefault());
                clearBtn.setVisibility(searchQuery.isEmpty() ? View.GONE : View.VISIBLE);
                applyFilter();
            }
        });

        return wrapper;
    }

    // ─── Divider ────────────────────────────────────────────────────────────

    private View buildDivider() {
        View divider = new View(requireContext());
        divider.setBackgroundColor(
                ThemeUtils.getColor(requireContext(), R.attr.colorOutlineVariant));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, SketchwareUtil.dpToPx(1));
        int margin = SketchwareUtil.dpToPx(12);
        p.setMargins(margin, 0, margin, SketchwareUtil.dpToPx(4));
        divider.setLayoutParams(p);
        return divider;
    }

    // ─── Tree building ──────────────────────────────────────────────────────

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
        for (ViewBean root : roots) {
            rootNodes.add(createNode(root, childrenMap, 0));
        }
    }

    private TreeNode createNode(ViewBean view,
                                HashMap<String, List<ViewBean>> childrenMap,
                                int depth) {
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

    // ─── Display list & filter ───────────────────────────────────────────────

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

    /** Apply search filter — expands all matching paths. */
    private void applyFilter() {
        if (searchQuery.isEmpty()) {
            // Restore normal display
            rebuildDisplayList();
        } else {
            // Expand all nodes that match or contain a match
            markMatchingNodes(rootNodes, searchQuery);
            displayNodes.clear();
            for (TreeNode root : rootNodes) addFilteredNode(root);
        }

        boolean empty = displayNodes.isEmpty();
        tvNoResults.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);

        List<TreeNode> newList = new ArrayList<>(displayNodes);
        adapter.submitList(newList);
    }

    /**
     * Returns true if this node or any descendant matches the query.
     * Also sets node.matchesSearch and forces expansion.
     */
    private boolean markMatchingNodes(List<TreeNode> nodes, String query) {
        boolean anyMatch = false;
        for (TreeNode node : nodes) {
            boolean selfMatch = node.viewBean.id.toLowerCase(Locale.getDefault()).contains(query)
                    || ViewBean.getViewTypeName(node.viewBean.type)
                        .toLowerCase(Locale.getDefault()).contains(query);
            boolean childMatch = markMatchingNodes(node.children, query);
            node.matchesSearch = selfMatch || childMatch;
            if (node.matchesSearch) {
                node.isExpanded = true; // expand to show matches
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

    // ─── Helpers ────────────────────────────────────────────────────────────

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
                recyclerView.post(() -> {
                    LinearLayoutManager lm = (LinearLayoutManager)
                            recyclerView.getLayoutManager();
                    if (lm != null) lm.scrollToPositionWithOffset(finalI,
                            SketchwareUtil.dpToPx(60));
                });
                break;
            }
        }
    }

    // ─── Data model ─────────────────────────────────────────────────────────

    private static class TreeNode {
        final ViewBean viewBean;
        final int depth;
        boolean isExpanded;
        boolean matchesSearch = true;
        final List<TreeNode> children = new ArrayList<>();

        TreeNode(ViewBean viewBean, int depth) {
            this.viewBean = viewBean;
            this.depth    = depth;
        }

        boolean hasChildren() { return !children.isEmpty(); }
    }

    // ─── Adapter ────────────────────────────────────────────────────────────

    private class TreeAdapter extends RecyclerView.Adapter<TreeAdapter.VH> {

        private List<TreeNode> currentList = new ArrayList<>();

        void submitList(List<TreeNode> newList) {
            DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
                @Override public int getOldListSize() { return currentList.size(); }
                @Override public int getNewListSize() { return newList.size(); }
                @Override
                public boolean areItemsTheSame(int oldPos, int newPos) {
                    return currentList.get(oldPos).viewBean.id
                            .equals(newList.get(newPos).viewBean.id);
                }
                @Override
                public boolean areContentsTheSame(int oldPos, int newPos) {
                    TreeNode o = currentList.get(oldPos);
                    TreeNode n = newList.get(newPos);
                    return o.isExpanded == n.isExpanded
                            && o.depth == n.depth
                            && o.matchesSearch == n.matchesSearch;
                }
            });
            currentList = newList;
            diff.dispatchUpdatesTo(this);
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Build item view programmatically to keep layout consistent
            return new VH(buildItemView());
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            TreeNode node = currentList.get(position);
            boolean isSelected = selectedViewId != null
                    && selectedViewId.equals(node.viewBean.id);
            holder.bind(node, isSelected, searchQuery);
        }

        @Override
        public int getItemCount() { return currentList.size(); }

        // ── Build item view ──────────────────────────────────────────────────

        private View buildItemView() {
            // Outer card (shows selection highlight)
            MaterialCardView card = new MaterialCardView(requireContext());
            card.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            card.setCardElevation(0f);
            card.setRadius(SketchwareUtil.dpToPx(8));
            card.setUseCompatPadding(false);
            int cardMarginH = SketchwareUtil.dpToPx(6);
            int cardMarginV = SketchwareUtil.dpToPx(1);
            RecyclerView.LayoutParams clp = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            clp.setMargins(cardMarginH, cardMarginV, cardMarginH, cardMarginV);
            card.setLayoutParams(clp);
            card.setTag("card");

            // Inner row
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, SketchwareUtil.dpToPx(6), SketchwareUtil.dpToPx(8), SketchwareUtil.dpToPx(6));
            row.setTag("row");

            // Tree-line / indent spacer (will be set in bind)
            View indent = new View(requireContext());
            indent.setTag("indent");
            row.addView(indent, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            // Expand arrow
            ImageView arrow = new ImageView(requireContext());
            arrow.setImageResource(R.drawable.ic_mtrl_arrow_right);
            arrow.setColorFilter(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
            arrow.setTag("arrow");
            arrow.setBackground(makeRippleBackground());
            arrow.setClickable(true);
            arrow.setFocusable(true);
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(
                    SketchwareUtil.dpToPx(22), SketchwareUtil.dpToPx(22));
            arrowParams.setMarginEnd(SketchwareUtil.dpToPx(2));
            arrow.setLayoutParams(arrowParams);
            row.addView(arrow);

            // Type icon
            ImageView icon = new ImageView(requireContext());
            icon.setTag("icon");
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                    SketchwareUtil.dpToPx(20), SketchwareUtil.dpToPx(20));
            iconParams.setMarginEnd(SketchwareUtil.dpToPx(10));
            icon.setLayoutParams(iconParams);
            row.addView(icon);

            // Text block
            LinearLayout textBlock = new LinearLayout(requireContext());
            textBlock.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams tbParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            textBlock.setLayoutParams(tbParams);

            TextView tvId = new TextView(requireContext());
            tvId.setTag("tvId");
            tvId.setTextSize(14f);
            tvId.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
            tvId.setTypeface(null, Typeface.BOLD);
            tvId.setSingleLine(false);   // never truncate
            tvId.setMaxLines(2);
            textBlock.addView(tvId);

            TextView tvType = new TextView(requireContext());
            tvType.setTag("tvType");
            tvType.setTextSize(11.5f);
            tvType.setTextColor(ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant));
            tvType.setSingleLine(false);
            tvType.setMaxLines(2);
            textBlock.addView(tvType);

            row.addView(textBlock);
            card.addView(row, new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            return card;
        }

        private android.graphics.drawable.RippleDrawable makeRippleBackground() {
            int rippleColor = ThemeUtils.getColor(requireContext(), R.attr.colorControlHighlight);
            android.graphics.drawable.GradientDrawable mask =
                    new android.graphics.drawable.GradientDrawable();
            mask.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            mask.setColor(Color.WHITE);
            return new android.graphics.drawable.RippleDrawable(
                    ColorStateList.valueOf(rippleColor), null, mask);
        }

        // ── ViewHolder ───────────────────────────────────────────────────────

        class VH extends RecyclerView.ViewHolder {
            MaterialCardView card;
            View             indent;
            ImageView        arrow, icon;
            TextView         tvId, tvType;
            LinearLayout     row;

            VH(View v) {
                super(v);
                card    = (MaterialCardView) v;
                row     = card.findViewWithTag("row");
                indent  = card.findViewWithTag("indent");
                arrow   = card.findViewWithTag("arrow");
                icon    = card.findViewWithTag("icon");
                tvId    = card.findViewWithTag("tvId");
                tvType  = card.findViewWithTag("tvType");
            }

            void bind(TreeNode node, boolean isSelected, String query) {

                // ── Indent ───────────────────────────────────────────────────
                int indentPx = SketchwareUtil.dpToPx(16)
                             + SketchwareUtil.dpToPx(node.depth * 20);
                LinearLayout.LayoutParams indentLp =
                        (LinearLayout.LayoutParams) indent.getLayoutParams();
                indentLp.width = indentPx;
                indent.setLayoutParams(indentLp);

                // ── Arrow ────────────────────────────────────────────────────
                if (node.hasChildren()) {
                    arrow.setVisibility(View.VISIBLE);
                    arrow.setRotation(node.isExpanded ? 90f : 0f);
                    arrow.setOnClickListener(v -> {
                        boolean expanding = !node.isExpanded;
                        node.isExpanded = expanding;

                        // Animate arrow rotation
                        ObjectAnimator rotAnim = ObjectAnimator.ofFloat(
                                arrow, "rotation",
                                expanding ? 0f : 90f,
                                expanding ? 90f : 0f);
                        rotAnim.setDuration(180);
                        rotAnim.setInterpolator(new DecelerateInterpolator());
                        rotAnim.start();

                        // Rebuild and submit
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
                    arrow.setVisibility(View.INVISIBLE);
                    arrow.setOnClickListener(null);
                }

                // ── Icon ─────────────────────────────────────────────────────
                icon.setImageResource(ViewBean.getViewTypeResId(node.viewBean.type));
                int iconTint = isSelected
                        ? ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)
                        : ThemeUtils.getColor(requireContext(), R.attr.colorOnSurfaceVariant);
                icon.setColorFilter(iconTint);

                // ── Text (with search highlight) ─────────────────────────────
                tvId.setText(highlight(node.viewBean.id, query,
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)));

                String typeName = ViewBean.getViewTypeName(node.viewBean.type);
                if (node.viewBean.customView != null
                        && !node.viewBean.customView.isEmpty()
                        && !node.viewBean.customView.equals("none")
                        && !node.viewBean.customView.equals("NONE")) {
                    typeName += " (" + node.viewBean.customView + ")";
                }
                tvType.setText(highlight(typeName, query,
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer),
                        ThemeUtils.getColor(requireContext(), R.attr.colorPrimary)));

                // ── Selected highlight ────────────────────────────────────────
                if (isSelected) {
                    card.setCardBackgroundColor(
                            ThemeUtils.getColor(requireContext(), R.attr.colorPrimaryContainer));
                    card.setStrokeWidth(SketchwareUtil.dpToPx(1));
                    card.setStrokeColor(
                            ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
                    tvId.setTextColor(
                            ThemeUtils.getColor(requireContext(), R.attr.colorPrimary));
                } else {
                    card.setCardBackgroundColor(Color.TRANSPARENT);
                    card.setStrokeWidth(0);
                    tvId.setTextColor(
                            ThemeUtils.getColor(requireContext(), R.attr.colorOnSurface));
                }

                // ── Click: select ────────────────────────────────────────────
                card.setOnClickListener(v -> {
                    listener.onSelected(node.viewBean.id);
                    dismiss();
                });
            }

            /** Highlights all occurrences of {@code query} inside {@code text}. */
            private CharSequence highlight(String text, String query,
                                           int bgColor, int fgColor) {
                if (query.isEmpty() || text == null) return text != null ? text : "";
                SpannableString ss = new SpannableString(text);
                String lower = text.toLowerCase(Locale.getDefault());
                int idx = 0;
                while ((idx = lower.indexOf(query, idx)) != -1) {
                    ss.setSpan(new BackgroundColorSpan(bgColor),
                            idx, idx + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    ss.setSpan(new ForegroundColorSpan(fgColor),
                            idx, idx + query.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    idx += query.length();
                }
                return ss;
            }
        }
    }
}