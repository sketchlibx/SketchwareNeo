package <?package_name?>;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

public class DebugActivity extends Activity {

    private static final Map<String, String> exceptionMap = new HashMap<String, String>() {{
        put("StringIndexOutOfBoundsException", "Invalid string operation");
        put("IndexOutOfBoundsException", "Invalid list operation");
        put("ArithmeticException", "Invalid arithmetical operation");
        put("NumberFormatException", "Invalid toNumber block operation");
        put("ActivityNotFoundException", "Invalid intent operation");
        put("NullPointerException", "Attempted to use a null object reference");
        put("IllegalArgumentException", "Passed an invalid argument to a method");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(Color.parseColor("#16161E"));
        window.setNavigationBarColor(Color.parseColor("#16161E"));

        Intent intent = getIntent();
        String errorMessage = "No error message available.";
        if (intent != null && intent.hasExtra("error")) {
            errorMessage = intent.getStringExtra("error");
        }

        String exceptionType = "Unknown Exception";
        String friendlyMessage = "";
        if (errorMessage != null && !errorMessage.isEmpty()) {
            String[] split = errorMessage.split("\n");
            exceptionType = split[0];
            for (Map.Entry<String, String> entry : exceptionMap.entrySet()) {
                if (exceptionType.contains(entry.getKey())) {
                    friendlyMessage = entry.getValue();
                    break;
                }
            }
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#16161E")); // Deep Modern Dark
        root.setPadding(dpToPx(24), dpToPx(40), dpToPx(24), dpToPx(24));

        TextView iconView = new TextView(this);
        iconView.setText("⚠️");
        iconView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 42);
        iconView.setGravity(Gravity.CENTER);
        root.addView(iconView);
        animateView(iconView, 0);

        TextView title = new TextView(this);
        title.setText("App Crashed");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextColor(Color.parseColor("#F7768E")); // Soft Pastel Red
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dpToPx(8), 0, 0);
        root.addView(title);
        animateView(title, 50);

        TextView exceptionView = new TextView(this);
        exceptionView.setText(exceptionType);
        exceptionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        exceptionView.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        exceptionView.setTextColor(Color.parseColor("#A9B1D6")); // Soft Gray/Blue
        exceptionView.setGravity(Gravity.CENTER);
        exceptionView.setPadding(0, dpToPx(8), 0, dpToPx(16));
        root.addView(exceptionView);
        animateView(exceptionView, 100);

        if (!friendlyMessage.isEmpty()) {
            LinearLayout hintContainer = new LinearLayout(this);
            hintContainer.setPadding(dpToPx(16), dpToPx(12), dpToPx(16), dpToPx(12));
            GradientDrawable hintBg = new GradientDrawable();
            hintBg.setColor(Color.parseColor("#2A2A3A")); // Slightly lighter card
            hintBg.setCornerRadius(dpToPx(12));
            hintContainer.setBackground(hintBg);

            TextView hintView = new TextView(this);
            hintView.setText("💡 Hint: " + friendlyMessage);
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            hintView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            hintView.setTextColor(Color.parseColor("#E0AF68")); // Pastel Yellow
            hintContainer.addView(hintView);
            
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.setMargins(0, 0, 0, dpToPx(16));
            hintContainer.setLayoutParams(hintParams);
            
            root.addView(hintContainer);
            animateView(hintContainer, 150);
        }

        LinearLayout terminalContainer = new LinearLayout(this);
        terminalContainer.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        GradientDrawable terminalBg = new GradientDrawable();
        terminalBg.setColor(Color.parseColor("#1A1B26")); // Deep Terminal Dark
        terminalBg.setCornerRadius(dpToPx(16));
        terminalBg.setStroke(dpToPx(1), Color.parseColor("#292E42")); // Soft Border
        terminalContainer.setBackground(terminalBg);

        TextView errorLog = new TextView(this);
        errorLog.setText(errorMessage);
        errorLog.setTextColor(Color.parseColor("#9ECE6A")); // Pastel Green (Terminal vibe)
        errorLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        errorLog.setTypeface(Typeface.MONOSPACE);
        errorLog.setTextIsSelectable(true);
        errorLog.setLineSpacing(0, 1.2f);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        hScroll.addView(errorLog);
        terminalContainer.addView(hScroll);

        ScrollView vScroll = new ScrollView(this);
        vScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, 0, 0, dpToPx(24));
        vScroll.setLayoutParams(scrollParams);
        vScroll.addView(terminalContainer);
        root.addView(vScroll);
        animateView(vScroll, 200);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnCopy = createSmoothButton("Copy Log", "#292E42", "#3B4261", "#A9B1D6");
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        copyParams.setMarginEnd(dpToPx(8));
        btnCopy.setLayoutParams(copyParams);
        
        final String finalErrorMessage = errorMessage;
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Crash Log", finalErrorMessage);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        buttonRow.addView(btnCopy);

        Button btnRestart = createSmoothButton("Restart App", "#7AA2F7", "#5684E5", "#16161E");
        LinearLayout.LayoutParams restartParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        restartParams.setMarginStart(dpToPx(8));
        btnRestart.setLayoutParams(restartParams);
        
        btnRestart.setOnClickListener(v -> {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
            }
            finish();
            Runtime.getRuntime().exit(0);
        });
        buttonRow.addView(btnRestart);

        root.addView(buttonRow);
        animateView(buttonRow, 250);
        
        setContentView(root);
    }

    private Button createSmoothButton(String text, String bgColorHex, String rippleColorHex, String textColorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.parseColor(textColorHex));
        btn.setAllCaps(false);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btn.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btn.setPadding(0, dpToPx(14), 0, dpToPx(14));
        btn.setElevation(dpToPx(0));
        btn.setStateListAnimator(null);
        
        GradientDrawable defaultBg = new GradientDrawable();
        defaultBg.setColor(Color.parseColor(bgColorHex));
        defaultBg.setCornerRadius(dpToPx(12));
        
        RippleDrawable ripple = new RippleDrawable(
                ColorStateList.valueOf(Color.parseColor(rippleColorHex)),
                defaultBg,
                null
        );

        btn.setBackground(ripple);
        return btn;
    }

    /**
     * Slide-up & Fade-in entry animation for a smooth UX.
     */
    private void animateView(View view, int delay) {
        view.setAlpha(0f);
        view.setTranslationY(dpToPx(30));
        view.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(400)
                .setStartDelay(delay)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .start();
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
