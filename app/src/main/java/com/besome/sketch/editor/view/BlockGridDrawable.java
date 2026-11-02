package com.besome.sketch.editor.view;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.R;
import pro.sketchware.utility.ThemeUtils;

/** Lightweight dotted canvas background; it draws only dots and never intercepts input. */
public final class BlockGridDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float spacing;
    private final float radius;

    public BlockGridDrawable(android.content.Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        spacing = 18f * density;
        radius = 1.15f * density;
        int color = ThemeUtils.getColor(context, R.attr.colorOutlineVariant);
        paint.setColor((color & 0x00ffffff) | 0x55000000);
        paint.setStyle(Paint.Style.FILL);
    }

    @Override public void draw(@NonNull Canvas canvas) {
        android.graphics.Rect b = getBounds();
        if (b.isEmpty()) return;
        for (float y = b.top + spacing * 0.5f; y < b.bottom; y += spacing) {
            for (float x = b.left + spacing * 0.5f; x < b.right; x += spacing) {
                canvas.drawCircle(x, y, radius, paint);
            }
        }
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) { paint.setColorFilter(colorFilter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
