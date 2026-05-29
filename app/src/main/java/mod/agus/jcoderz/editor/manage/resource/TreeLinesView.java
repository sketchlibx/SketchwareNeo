package mod.agus.jcoderz.editor.manage.resource;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * TreeLinesView
 *
 * A lightweight custom {@link View} that renders VS Code / Android Studio–style
 * tree connector lines for a file-explorer list.
 *
 * Visual grammar (left = ancestor depth 0, right = current item depth):
 *
 *   depth 0: (no lines, view has width = 0)
 *
 *   depth 1, last child:
 *     └─  (vertical line from top to centre, then horizontal to right edge)
 *
 *   depth 1, not last child:
 *     ├─  (full vertical line, plus horizontal connector at centre)
 *
 *   depth 2, ancestor[0] has more siblings, this is last child:
 *     │  └─
 *     (vertical at col 0, elbow at col 1)
 *
 * Usage — call {@link #setTreeState} in RecyclerView's onBindViewHolder:
 * <pre>
 *   treeLinesView.setTreeState(node.depth, node.ancestorHasLines, node.isLastChild);
 * </pre>
 *
 * Width is self-measured as {@code depth × COLUMN_WIDTH_DP}.
 * Height is determined by the parent (typically match_parent / constraint stretch).
 */
public class TreeLinesView extends View {

    // ── Constants ─────────────────────────────────────────────────────────────────
    /** Horizontal space allocated per depth level, in dp. */
    private static final float COLUMN_WIDTH_DP = 20f;
    /** Line stroke width in dp. */
    private static final float STROKE_DP = 1.2f;

    // ── State ─────────────────────────────────────────────────────────────────────
    private int depth = 0;
    /**
     * For each ancestor level {@code i} in {@code [0, depth-1)}, {@code true} means
     * that ancestor has more siblings below it → draw a continuing vertical line
     * at column {@code i}.  May be {@code null} when depth ≤ 1.
     */
    private boolean[] ancestorHasLines;
    /**
     * Whether this node is the last child of its parent.
     * Controls whether the vertical segment at the current depth column continues
     * past the row centre (false = T-junction ├, true = elbow └).
     */
    private boolean isLastChild = true;

    // ── Resolved pixel values ─────────────────────────────────────────────────────
    private float colPx;   // COLUMN_WIDTH_DP in px
    private Paint paint;

    // ── Constructors ──────────────────────────────────────────────────────────────

    public TreeLinesView(Context context) {
        this(context, null);
    }

    public TreeLinesView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public TreeLinesView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        colPx = COLUMN_WIDTH_DP * density;
        float strokePx = STROKE_DP * density;

        // Resolve the subtle outline-variant colour from the active Material3 theme.
        // Falls back to a light grey if the attribute isn't available.
        int lineColor = resolveAttrColor(context,
                com.google.android.material.R.attr.colorOutlineVariant, 0xFFCCCCCC);

        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokePx);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(lineColor);

        // No background needed; keep transparent.
        setWillNotDraw(false);
    }

    // ── Public API ────────────────────────────────────────────────────────────────

    /**
     * Configure this view for a specific tree node.
     *
     * @param depth           depth of the node (0 = root level, no lines drawn)
     * @param ancestorHasLines array of length {@code depth}; entry {@code [i]} is
     *                         {@code true} if the ancestor at depth {@code i} still
     *                         has more siblings (i.e. a vertical line should continue
     *                         at column {@code i})
     * @param isLastChild     {@code true} if this is the last child of its parent
     *                         (draws an elbow └ instead of a T-junction ├)
     */
    public void setTreeState(int depth, boolean[] ancestorHasLines, boolean isLastChild) {
        boolean sizeChanged = this.depth != depth;
        this.depth = depth;
        this.ancestorHasLines = ancestorHasLines;
        this.isLastChild = isLastChild;
        if (sizeChanged) requestLayout();
        invalidate();
    }

    // ── Measurement ───────────────────────────────────────────────────────────────

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // Width = depth × column width.  When depth=0 the view is zero-width and
        // takes up no horizontal space at all.
        int w = (int) (depth * colPx);

        // Height: honour EXACTLY and AT_MOST from constraints; use 48dp as default.
        int hMode = MeasureSpec.getMode(heightSpec);
        int hSize = MeasureSpec.getSize(heightSpec);
        int h;
        if (hMode == MeasureSpec.EXACTLY) {
            h = hSize;
        } else {
            int fallback = (int) (48 * getResources().getDisplayMetrics().density);
            h = (hMode == MeasureSpec.AT_MOST) ? Math.min(hSize, fallback) : fallback;
        }

        setMeasuredDimension(w, h);
    }

    // ── Drawing ───────────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        if (depth == 0) return;   // root items — nothing to draw

        float midY = getHeight() * 0.5f;

        // ── Pass 1: vertical lines for ancestor columns 0 … depth-2 ──────────────
        // A full-height vertical line is drawn at column i if that ancestor has
        // more siblings below it.
        for (int i = 0; i < depth - 1; i++) {
            if (ancestorHasLines != null && i < ancestorHasLines.length && ancestorHasLines[i]) {
                float cx = centreX(i);
                canvas.drawLine(cx, 0, cx, getHeight(), paint);
            }
        }

        // ── Pass 2: connector at the current depth column (depth-1) ──────────────
        float cx = centreX(depth - 1);

        // Vertical segment: top → midY always; continues below midY only if this
        // node has more siblings (i.e. is NOT the last child).
        float vertBottom = isLastChild ? midY : getHeight();
        canvas.drawLine(cx, 0, cx, vertBottom, paint);

        // Horizontal segment: midY, from cx → right edge of this view.
        canvas.drawLine(cx, midY, getWidth(), midY, paint);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    /** Returns the horizontal centre of column {@code col}. */
    private float centreX(int col) {
        return (col + 0.5f) * colPx;
    }

    private static int resolveAttrColor(Context context, int attrRes, int fallback) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attrRes, tv, true)) {
            return tv.data;
        }
        return fallback;
    }
}