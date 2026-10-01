package mod.sketchlibx.project;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.NestedScrollingChild3;
import androidx.core.view.NestedScrollingChildHelper;
import androidx.core.view.NestedScrollingParent3;
import androidx.core.view.NestedScrollingParentHelper;
import androidx.core.view.ViewCompat;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.loadingindicator.LoadingIndicator;

import pro.sketchware.R;

public class ExpressivePullToRefresh extends ViewGroup implements NestedScrollingParent3, NestedScrollingChild3 {

    public static final int STYLE_UNCONTAINED = 0;
    public static final int STYLE_CONTAINED = 1;

    private static final int ANIMATE_TO_TRIGGER_DURATION = 200;
    private static final int ANIMATE_TO_START_DURATION = 200;
    private static final float DRAG_RATE = 0.8f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2f;

    private static final int DEFAULT_CIRCLE_TARGET = 96;
    private static final int CIRCLE_DIAMETER = 48;

    private View mTarget;
    private LoadingIndicator mLoadingIndicator;

    private int mTouchSlop;
    private int mTotalDragDistance;
    private int mCircleDiameter;
    private int mCurrentTargetOffsetTop;
    private int mOriginalOffsetTop;

    private float mInitialMotionY;
    private float mInitialMotionX;
    private boolean mIsBeingDragged;
    private int mActivePointerId = -1;
    private boolean mThresholdReached;

    private boolean mRefreshing;
    private boolean mNotify;
    private OnRefreshListener mListener;

    private final Interpolator mDecelerateInterpolator;
    private int[] mColors;
    private int mCurrentStyle;
    private ValueAnimator mOffsetAnimator;
    private ValueAnimator mRefreshAnimator;

    private final NestedScrollingParentHelper mNestedScrollingParentHelper;
    private final NestedScrollingChildHelper mNestedScrollingChildHelper;
    private final int[] mParentScrollConsumed = new int[2];
    private final int[] mParentOffsetInWindow = new int[2];
    private boolean mNestedScrollInProgress;
    private float mTotalUnconsumed;

    public ExpressivePullToRefresh(@NonNull Context context) {
        this(context, null);
    }

    public ExpressivePullToRefresh(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);

        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mDecelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);

        mCircleDiameter = (int) (CIRCLE_DIAMETER * getResources().getDisplayMetrics().density);
        mTotalDragDistance = (int) (DEFAULT_CIRCLE_TARGET * getResources().getDisplayMetrics().density);

        mCurrentStyle = STYLE_CONTAINED;
        
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.RefreshView);
            mCurrentStyle = a.getInteger(R.styleable.RefreshView_type, STYLE_CONTAINED);
            a.recycle();
        }
        
        updateColors(context);
        setupProgressIndicator(mCurrentStyle);

        mOriginalOffsetTop = 0;
        mCurrentTargetOffsetTop = mOriginalOffsetTop;

        setWillNotDraw(false);
        setChildrenDrawingOrderEnabled(true);

        mNestedScrollingParentHelper = new NestedScrollingParentHelper(this);
        mNestedScrollingChildHelper = new NestedScrollingChildHelper(this);
        setNestedScrollingEnabled(true);
    }

    private void updateColors(Context context) {
            mColors = new int[]{
                MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0xFF21005D)
            };
        
    }

    private void setupProgressIndicator(int style) {
        if (mLoadingIndicator != null) {
            removeView(mLoadingIndicator);
            mLoadingIndicator = null;
        }

        View root = LayoutInflater.from(getContext()).inflate(R.layout.m3_loading_indicator, this, false);
        if (style == STYLE_CONTAINED) {
            mLoadingIndicator = root.findViewById(R.id.indicator_contained);
        } else {
            mLoadingIndicator = root.findViewById(R.id.indicator_uncontained);
        }
        
        ((ViewGroup) mLoadingIndicator.getParent()).removeView(mLoadingIndicator);
        
        mLoadingIndicator.setIndicatorColor(mColors);
        mLoadingIndicator.setVisibility(GONE);
        
        addView(mLoadingIndicator, new LayoutParams(mCircleDiameter, mCircleDiameter));
    }

    public void setIndicatorStyle(int style) {
        if (mCurrentStyle != style) {
            mCurrentStyle = style;
            updateColors(getContext());
            setupProgressIndicator(style);
            updateIndicatorPosition();
        }
    }

    public int getIndicatorStyle() {
        return mCurrentStyle;
    }

    private void ensureTarget() {
        if (mTarget == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != mLoadingIndicator) {
                    mTarget = child;
                    break;
                }
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = getMeasuredWidth();
        final int height = getMeasuredHeight();
        if (getChildCount() == 0) {
            return;
        }
        ensureTarget();
        if (mTarget == null) {
            return;
        }
        final View child = mTarget;
        final int childLeft = getPaddingLeft();
        final int childTop = getPaddingTop();
        final int childWidth = width - getPaddingLeft() - getPaddingRight();
        final int childHeight = height - getPaddingTop() - getPaddingBottom();
        child.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight);

        layoutIndicator();
    }

    private int mCurrentOffset = 0;

    private void layoutIndicator() {
        if (mLoadingIndicator != null) {
            int width = getMeasuredWidth();
            int indicatorLeft = (width / 2) - (mCircleDiameter / 2);
            int indicatorTop = mCurrentOffset;
            mLoadingIndicator.layout(indicatorLeft, indicatorTop, indicatorLeft + mCircleDiameter, indicatorTop + mCircleDiameter);
        }
    }

    @Override
    protected int getChildDrawingOrder(int childCount, int i) {
        if (mLoadingIndicator == null) {
            return i;
        }
        if (i == childCount - 1) {
            return indexOfChild(mLoadingIndicator);
        }
        int indicatorIndex = indexOfChild(mLoadingIndicator);
        if (i < indicatorIndex) {
            return i;
        }
        return i + 1;
    }

    private void setTargetOffsetTop(int offset) {
        mCurrentTargetOffsetTop = mCurrentTargetOffsetTop + offset;
        if (mTarget != null) {
            float dragPercent = (float) mCurrentTargetOffsetTop / mTotalDragDistance;

            if (dragPercent >= 1.0f && !mThresholdReached && !mRefreshing) {
                mThresholdReached = true;
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
            } else if (dragPercent < 1.0f) {
                mThresholdReached = false;
            }
        }

        mCurrentOffset = (int) (mCurrentTargetOffsetTop * 0.6f);
        updateIndicatorPosition();
        invalidate();
    }

    @Override
    protected android.os.Parcelable onSaveInstanceState() {
        android.os.Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        ss.mRefreshing = mRefreshing;
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(android.os.Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        if (ss.mRefreshing) {
            post(() -> setRefreshing(true, false));
        }
    }

    static class SavedState extends BaseSavedState {
        boolean mRefreshing;

        SavedState(android.os.Parcelable superState) {
            super(superState);
        }

        SavedState(android.os.Parcel android) {
            super(android);
            mRefreshing = android.readInt() == 1;
        }

        @Override
        public void writeToParcel(android.os.Parcel out, int flags) {
            super.writeToParcel(out, flags);
            out.writeInt(mRefreshing ? 1 : 0);
        }

        public static final android.os.Parcelable.Creator<SavedState> CREATOR = new android.os.Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(android.os.Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    private void updateIndicatorPosition() {
        if (mLoadingIndicator != null) {
            float dragPercent = Math.min(1.5f, (float) mCurrentTargetOffsetTop / mTotalDragDistance);
            float rawScale = Math.min(1f, dragPercent / 0.7f);
            float scale = mRefreshing ? 1f : (float) (Math.pow(rawScale, 2) * (3 - 2 * rawScale));

            mLoadingIndicator.setVisibility(mCurrentTargetOffsetTop > 0 || mRefreshing ? VISIBLE : GONE);
            mLoadingIndicator.setScaleX(scale);
            mLoadingIndicator.setScaleY(scale);

            if (!mRefreshing) {
                mLoadingIndicator.setRotation(dragPercent * 360f);
                stopLoadingAnimation();
            } else {
                startLoadingAnimation();
            }
            layoutIndicator();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        ensureTarget();
        if (mTarget != null) {
            mTarget.measure(MeasureSpec.makeMeasureSpec(
                    getMeasuredWidth() - getPaddingLeft() - getPaddingRight(),
                    MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(
                    getMeasuredHeight() - getPaddingTop() - getPaddingBottom(), MeasureSpec.EXACTLY));
        }
        if (mLoadingIndicator != null) {
            mLoadingIndicator.measure(MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(mCircleDiameter, MeasureSpec.EXACTLY));
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        ensureTarget();
        if (!isEnabled() || canChildScrollUp() || mRefreshing || mNestedScrollInProgress) {
            return false;
        }

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                final float initialMotionY = getMotionEventY(ev, mActivePointerId);
                if (initialMotionY == -1) {
                    return false;
                }
                mInitialMotionY = initialMotionY;
                mInitialMotionX = ev.getX(ev.findPointerIndex(mActivePointerId));
                break;

            case MotionEvent.ACTION_MOVE:
                if (mActivePointerId == -1) {
                    return false;
                }
                final float y = getMotionEventY(ev, mActivePointerId);
                if (y == -1) {
                    return false;
                }
                final float yDiff = y - mInitialMotionY;

                final int xIndex = ev.findPointerIndex(mActivePointerId);
                if (xIndex >= 0) {
                    final float xDiff = Math.abs(ev.getX(xIndex) - mInitialMotionX);
                    if (xDiff > Math.abs(yDiff)) {
                        mIsBeingDragged = false;
                        mActivePointerId = -1;
                        return false;
                    }
                }

                if (yDiff > mTouchSlop && !mIsBeingDragged) {
                    mIsBeingDragged = true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                mIsBeingDragged = false;
                mActivePointerId = -1;
                break;
        }

        return mIsBeingDragged;
    }

    private float getMotionEventY(MotionEvent ev, int activePointerId) {
        final int index = ev.findPointerIndex(activePointerId);
        if (index < 0) {
            return -1;
        }
        return ev.getY(index);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || canChildScrollUp() || mRefreshing || mNestedScrollInProgress) {
            return false;
        }

        final int action = ev.getActionMasked();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mActivePointerId = ev.getPointerId(0);
                mIsBeingDragged = false;
                mInitialMotionX = ev.getX(ev.findPointerIndex(mActivePointerId));
                break;

            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    return false;
                }

                final float y = ev.getY(pointerIndex);
                final float yDiff = y - mInitialMotionY;

                final float xDiff = Math.abs(ev.getX(pointerIndex) - mInitialMotionX);
                if (!mIsBeingDragged && xDiff > Math.abs(yDiff)) {
                    return false;
                }
                
                if (!mIsBeingDragged && yDiff > mTouchSlop) {
                    mIsBeingDragged = true;
                }

                if (mIsBeingDragged) {
                    final float scrollTop = yDiff * DRAG_RATE;
                    if (scrollTop > 0) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                        mTotalUnconsumed = yDiff;
                        moveSpinner(mTotalUnconsumed);
                    } else {
                        return false;
                    }
                }
                break;
            }

            case MotionEvent.ACTION_POINTER_DOWN: {
                final int index = ev.getActionIndex();
                mActivePointerId = ev.getPointerId(index);
                break;
            }

            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(ev);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mActivePointerId == -1) {
                    return false;
                }
                final int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    mActivePointerId = -1;
                    return false;
                }
                final float y = ev.getY(pointerIndex);
                final float yDiff = y - mInitialMotionY;
                mIsBeingDragged = false;
                finishSpinner(yDiff);
                mTotalUnconsumed = 0;
                mActivePointerId = -1;
                return false;
            }
        }

        return true;
    }

    private void onSecondaryPointerUp(MotionEvent ev) {
        final int pointerIndex = ev.getActionIndex();
        final int pointerId = ev.getPointerId(pointerIndex);
        if (pointerId == mActivePointerId) {
            final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
            mActivePointerId = ev.getPointerId(newPointerIndex);
        }
    }

    private boolean canChildScrollUp() {
        if (mTarget == null) return false;
        return ViewCompat.canScrollVertically(mTarget, -1);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimations();
    }

    private void stopAnimations() {
        if (mOffsetAnimator != null) {
            mOffsetAnimator.cancel();
            mOffsetAnimator = null;
        }
        if (mRefreshAnimator != null) {
            mRefreshAnimator.cancel();
            mRefreshAnimator = null;
        }
        if (mLoadingIndicator != null) {
            mLoadingIndicator.setVisibility(GONE);
            stopLoadingAnimation();
        }
    }

    private void startAnimations() {
        if (mLoadingIndicator != null) {
            mLoadingIndicator.setVisibility(VISIBLE);
            startLoadingAnimation();
        }
    }

    private void stopLoadingAnimation() {
        if (mLoadingIndicator == null) return;
        
        Object obj = mLoadingIndicator;
        if (obj instanceof Animatable) {
            ((Animatable) obj).stop();
        }

        Drawable background = mLoadingIndicator.getBackground();
        if (background instanceof Animatable) {
            ((Animatable) background).stop();
        }
    }

    private void startLoadingAnimation() {
        if (mLoadingIndicator == null) return;
        
        Object obj = mLoadingIndicator;
        if (obj instanceof Animatable) {
            Animatable anim = (Animatable) obj;
            if (!anim.isRunning()) anim.start();
        }

        Drawable background = mLoadingIndicator.getBackground();
        if (background instanceof Animatable) {
            Animatable anim = (Animatable) background;
            if (!anim.isRunning()) anim.start();
        }
    }

    private void animateOffsetToStartPosition() {
        if (mOffsetAnimator != null) {
            mOffsetAnimator.cancel();
        }
        if (mRefreshAnimator != null) {
            mRefreshAnimator.cancel();
        }
        int from = mCurrentTargetOffsetTop;
        mOffsetAnimator = ValueAnimator.ofInt(from, mOriginalOffsetTop);
        mOffsetAnimator.setDuration(ANIMATE_TO_START_DURATION);
        mOffsetAnimator.setInterpolator(mDecelerateInterpolator);
        mOffsetAnimator.addUpdateListener(animation -> {
            int offset = (int) animation.getAnimatedValue() - mCurrentTargetOffsetTop;
            setTargetOffsetTop(offset);
        });
        mOffsetAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mRefreshing) stopAnimations();
            }
        });
        mOffsetAnimator.start();
    }

    private void setRefreshing(boolean refreshing, boolean notify) {
        if (mRefreshing != refreshing) {
            mNotify = notify;
            mRefreshing = refreshing;
            if (mRefreshing) {
                startAnimations();
                if (mOffsetAnimator != null) mOffsetAnimator.cancel();
                if (mRefreshAnimator != null) mRefreshAnimator.cancel();
                int endTarget = mTotalDragDistance;
                mRefreshAnimator = ValueAnimator.ofInt(mCurrentTargetOffsetTop, endTarget);
                mRefreshAnimator.setDuration(ANIMATE_TO_TRIGGER_DURATION);
                mRefreshAnimator.setInterpolator(mDecelerateInterpolator);
                mRefreshAnimator.addUpdateListener(animation -> {
                    int offset = (int) animation.getAnimatedValue() - mCurrentTargetOffsetTop;
                    setTargetOffsetTop(offset);
                });
                mRefreshAnimator.start();
                if (mNotify && mListener != null) {
                    mListener.onRefresh();
                }
            } else {
                mTotalUnconsumed = 0;
                animateOffsetToStartPosition();
            }
        } else if (!refreshing && mCurrentTargetOffsetTop != mOriginalOffsetTop) {
            animateOffsetToStartPosition();
        }
    }

    public void setOnRefreshListener(OnRefreshListener listener) {
        mListener = listener;
    }

    public void setRefreshing(boolean refreshing) {
        setRefreshing(refreshing, false);
    }

    public boolean isRefreshing() {
        return mRefreshing;
    }

    public interface OnRefreshListener {
        void onRefresh();
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type, @NonNull int[] consumed) {
        onNestedScrollInternal(dyUnconsumed, type, consumed);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull View child, @NonNull View target, int axes, int type) {
        return isEnabled() && (axes & ViewCompat.SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override
    public void onNestedScrollAccepted(@NonNull View child, @NonNull View target, int axes, int type) {
        mNestedScrollingParentHelper.onNestedScrollAccepted(child, target, axes, type);
        startNestedScroll(axes & ViewCompat.SCROLL_AXIS_VERTICAL, type);
        if (type == ViewCompat.TYPE_TOUCH) {
            mTotalUnconsumed = 0;
        }
        mNestedScrollInProgress = true;
    }

    @Override
    public void onStopNestedScroll(@NonNull View target, int type) {
        mNestedScrollingParentHelper.onStopNestedScroll(target, type);
        mNestedScrollInProgress = false;
        if (type == ViewCompat.TYPE_TOUCH && mTotalUnconsumed > 0) {
            if (!mRefreshing) {
                finishSpinner(mTotalUnconsumed);
            } else {
                int endTarget = mTotalDragDistance;
                if (mCurrentTargetOffsetTop != endTarget) {
                    if (mRefreshAnimator != null) mRefreshAnimator.cancel();
                    mRefreshAnimator = ValueAnimator.ofInt(mCurrentTargetOffsetTop, endTarget);
                    mRefreshAnimator.setDuration(ANIMATE_TO_TRIGGER_DURATION);
                    mRefreshAnimator.setInterpolator(mDecelerateInterpolator);
                    mRefreshAnimator.addUpdateListener(animation -> {
                        int offset = (int) animation.getAnimatedValue() - mCurrentTargetOffsetTop;
                        setTargetOffsetTop(offset);
                    });
                    mRefreshAnimator.start();
                }
            }
            mTotalUnconsumed = 0;
        }
        stopNestedScroll(type);
    }

    @Override
    public void onNestedScroll(@NonNull View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int type) {
        onNestedScrollInternal(dyUnconsumed, type, null);
    }

    @Override
    public void onNestedPreScroll(@NonNull View target, int dx, int dy, @NonNull int[] consumed, int type) {
        if (isEnabled() && type == ViewCompat.TYPE_TOUCH && !mRefreshing && dy > 0 && mTotalUnconsumed > 0) {
            if (dy > mTotalUnconsumed) {
                consumed[1] = (int) mTotalUnconsumed;
                mTotalUnconsumed = 0;
            } else {
                mTotalUnconsumed -= dy;
                consumed[1] = dy;
            }
            moveSpinner(mTotalUnconsumed);
        }

        final int[] parentConsumed = mParentScrollConsumed;
        if (dispatchNestedPreScroll(dx - consumed[0], dy - consumed[1], parentConsumed, null, type)) {
            consumed[0] += parentConsumed[0];
            consumed[1] += parentConsumed[1];
        }
    }

    @Override
    public void dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type, @NonNull int[] consumed) {
        mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type, consumed);
    }

    @Override
    public boolean startNestedScroll(int axes, int type) {
        return mNestedScrollingChildHelper.startNestedScroll(axes, type);
    }

    @Override
    public void stopNestedScroll(int type) {
        mNestedScrollingChildHelper.stopNestedScroll(type);
    }

    @Override
    public boolean hasNestedScrollingParent(int type) {
        return mNestedScrollingChildHelper.hasNestedScrollingParent(type);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, @Nullable int[] offsetInWindow, int type) {
        return mNestedScrollingChildHelper.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, type);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, @Nullable int[] consumed, @Nullable int[] offsetInWindow, int type) {
        return mNestedScrollingChildHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type);
    }

    @Override
    public void setNestedScrollingEnabled(boolean enabled) {
        mNestedScrollingChildHelper.setNestedScrollingEnabled(enabled);
    }

    @Override
    public boolean isNestedScrollingEnabled() {
        return mNestedScrollingChildHelper.isNestedScrollingEnabled();
    }

    @Override
    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public void stopNestedScroll() {
        stopNestedScroll(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean hasNestedScrollingParent() {
        return hasNestedScrollingParent(ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed, int[] offsetInWindow) {
        return dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, ViewCompat.TYPE_TOUCH);
    }

    @Override
    public boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) {
        return dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean onNestedPreFling(View target, float velocityX, float velocityY) {
        return dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return mNestedScrollingChildHelper.dispatchNestedFling(velocityX, velocityY, consumed);
    }

    @Override
    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return mNestedScrollingChildHelper.dispatchNestedPreFling(velocityX, velocityY);
    }

    @Override
    public int getNestedScrollAxes() {
        return mNestedScrollingParentHelper.getNestedScrollAxes();
    }

    private void onNestedScrollInternal(int dyUnconsumed, int type, @Nullable int[] consumed) {
        if (isEnabled() && !canChildScrollUp()) {
            if (type == ViewCompat.TYPE_TOUCH && !mRefreshing && dyUnconsumed < 0) {
                mTotalUnconsumed += Math.abs(dyUnconsumed);
                moveSpinner(mTotalUnconsumed);

                if (consumed != null) {
                    consumed[1] += dyUnconsumed;
                }
            }
        }

        dispatchNestedScroll(0, 0, 0, dyUnconsumed, mParentOffsetInWindow, type, consumed == null ? new int[2] : consumed);
    }

    private void moveSpinner(float overscroll) {
        float scrollTop = overscroll * DRAG_RATE;
        float boundedDragPercent = Math.min(1f, Math.abs(scrollTop / mTotalDragDistance));
        float extraOS = Math.abs(scrollTop) - mTotalDragDistance;
        float tensionSlingshotPercent = Math.max(0, Math.min(extraOS, mTotalDragDistance * 2) / mTotalDragDistance);
        float tensionPercent = (float) ((tensionSlingshotPercent / 4) - Math.pow(tensionSlingshotPercent / 4, 2)) * 2f;
        float extraMove = mTotalDragDistance * tensionPercent / 2;
        int targetY = (int) ((mTotalDragDistance * boundedDragPercent) + extraMove);

        setTargetOffsetTop(targetY - mCurrentTargetOffsetTop);
    }

    private void finishSpinner(float overscroll) {
        float scrollTop = overscroll * DRAG_RATE;
        if (scrollTop > mTotalDragDistance) {
            setRefreshing(true, true);
        } else {
            setRefreshing(false, false);
        }
    }
}
