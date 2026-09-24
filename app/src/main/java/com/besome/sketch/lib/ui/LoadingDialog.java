package com.besome.sketch.lib.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;

import pro.sketchware.R;

public class LoadingDialog extends Dialog {

    public LoadingDialog(Context context) {
        super(context, R.style.progress);

        setContentView(R.layout.progress);
        setCancelable(false);
        setCanceledOnTouchOutside(false);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            WindowCompat.setDecorFitsSystemWindows(window, false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.dimAmount = 0.5f;
            window.setAttributes(params);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }
}
