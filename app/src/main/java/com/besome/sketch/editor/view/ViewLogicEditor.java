package com.besome.sketch.editor.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.besome.sketch.editor.logic.BlockPane;

public class ViewLogicEditor extends LogicEditorScrollView {
    private final BlockPane blockPane;
    private final int[] posArea = new int[2];
    private boolean isFirst = true;
    private Runnable editorScrollChangedListener;

    public ViewLogicEditor(Context context) {
        this(context, null);
    }

    public ViewLogicEditor(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public ViewLogicEditor(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public ViewLogicEditor(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        blockPane = new BlockPane(context);
        setBackground(new BlockGridDrawable(context));
        setClipToPadding(false);
        setClipChildren(false);
        blockPane.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(blockPane);
    }

    public BlockPane getBlockPane() {
        return blockPane;
    }

    public void setOnEditorScrollChangedListener(Runnable listener) {
        editorScrollChangedListener = listener;
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (editorScrollChangedListener != null) post(editorScrollChangedListener);
    }

    @Override
    public void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (isFirst) {
            blockPane.getLayoutParams().width = right - left;
            blockPane.getLayoutParams().height = bottom - top;
            blockPane.b();
            isFirst = false;
        }
        if (editorScrollChangedListener != null) post(editorScrollChangedListener);
    }

    public boolean hitTest(float x, float y) {
        getLocationOnScreen(posArea);
        if (!(x > posArea[0])) return false;
        if (!(x < posArea[0] + getWidth())) return false;
        if (!(y > posArea[1])) return false;
        if (!(y < posArea[1] + getHeight())) return false;
        return true;
    }
}
