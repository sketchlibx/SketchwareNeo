package pro.sketchware.activities.main.fragments.projects_store.classes;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;

public class ZoomableImageView extends AppCompatImageView {

    private static final float MIN_SCALE = 1f;
    private static final float MAX_SCALE = 5f;
    private static final float DOUBLE_TAP_SCALE = 3f;

    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();

    private final ScaleGestureDetector scaleGestureDetector;
    private final GestureDetector gestureDetector;

    private float currentScale = 1f;
    private float lastTouchX, lastTouchY;
    private int activePointerId = MotionEvent.INVALID_POINTER_ID;
    private boolean isPanning;

    private OnImageTapListener onImageTapListener;

    public ZoomableImageView(Context context) {
        this(context, null);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ZoomableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setScaleType(ScaleType.MATRIX);
        scaleGestureDetector = new ScaleGestureDetector(context, new ScaleListener());
        gestureDetector = new GestureDetector(context, new GestureListener());
    }

    public void setOnImageTapListener(OnImageTapListener listener) {
        this.onImageTapListener = listener;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        resetMatrix();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        super.setImageDrawable(drawable);
        resetMatrix();
    }

    private void resetMatrix() {
        matrix.reset();
        currentScale = 1f;

        Drawable drawable = getDrawable();
        if (drawable == null || getWidth() == 0 || getHeight() == 0) {
            setImageMatrix(matrix);
            return;
        }

        int dw = drawable.getIntrinsicWidth();
        int dh = drawable.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) {
            setImageMatrix(matrix);
            return;
        }

        float scale = Math.min((float) getWidth() / dw, (float) getHeight() / dh);
        float dx = (getWidth() - dw * scale) / 2f;
        float dy = (getHeight() - dh * scale) / 2f;

        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        setImageMatrix(matrix);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleGestureDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                activePointerId = event.getPointerId(0);
                isPanning = true;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(currentScale > MIN_SCALE);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isPanning && !scaleGestureDetector.isInProgress() && currentScale > MIN_SCALE) {
                    int pointerIndex = event.findPointerIndex(activePointerId);
                    if (pointerIndex != -1) {
                        float x = event.getX(pointerIndex);
                        float y = event.getY(pointerIndex);
                        matrix.set(savedMatrix);
                        matrix.postTranslate(x - lastTouchX, y - lastTouchY);
                        constrainMatrix();
                        setImageMatrix(matrix);
                    }
                }
                break;

            case MotionEvent.ACTION_POINTER_UP: {
                int pointerIndex = event.getActionIndex();
                int pointerId = event.getPointerId(pointerIndex);
                if (pointerId == activePointerId) {
                    int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    activePointerId = event.getPointerId(newPointerIndex);
                    lastTouchX = event.getX(newPointerIndex);
                    lastTouchY = event.getY(newPointerIndex);
                    savedMatrix.set(matrix);
                }
                break;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                activePointerId = MotionEvent.INVALID_POINTER_ID;
                isPanning = false;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }
        return true;
    }

    private void constrainMatrix() {
        Drawable drawable = getDrawable();
        if (drawable == null) return;

        RectF rect = new RectF(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(rect);

        float deltaX = 0f, deltaY = 0f;
        float viewWidth = getWidth();
        float viewHeight = getHeight();

        if (rect.width() <= viewWidth) {
            deltaX = (viewWidth - rect.width()) / 2f - rect.left;
        } else if (rect.left > 0) {
            deltaX = -rect.left;
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right;
        }

        if (rect.height() <= viewHeight) {
            deltaY = (viewHeight - rect.height()) / 2f - rect.top;
        } else if (rect.top > 0) {
            deltaY = -rect.top;
        } else if (rect.bottom < viewHeight) {
            deltaY = viewHeight - rect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(@NonNull ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            float newScale = currentScale * scaleFactor;

            if (newScale < MIN_SCALE) {
                scaleFactor = MIN_SCALE / currentScale;
                newScale = MIN_SCALE;
            } else if (newScale > MAX_SCALE) {
                scaleFactor = MAX_SCALE / currentScale;
                newScale = MAX_SCALE;
            }

            currentScale = newScale;
            matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
            constrainMatrix();
            setImageMatrix(matrix);
            return true;
        }
    }

    private class GestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDoubleTap(@NonNull MotionEvent e) {
            float targetScale = currentScale > MIN_SCALE + 0.1f ? MIN_SCALE : DOUBLE_TAP_SCALE;
            float scaleFactor = targetScale / currentScale;
            currentScale = targetScale;

            matrix.postScale(scaleFactor, scaleFactor, e.getX(), e.getY());
            constrainMatrix();
            setImageMatrix(matrix);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
            if (onImageTapListener != null) {
                onImageTapListener.onImageTap();
            }
            return true;
        }
    }

    public interface OnImageTapListener {
        void onImageTap();
    }
}
