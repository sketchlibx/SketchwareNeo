package <?package_name?>;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
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
        root.setBackgroundColor(Color.parseColor("#121212")); // Dark Surface
        root.setPadding(dpToPx(24), dpToPx(32), dpToPx(24), dpToPx(24));

        TextView title = new TextView(this);
        title.setText("⚠️ Application Crashed");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.parseColor("#EF5350")); // Material Red
        root.addView(title);

        TextView exceptionView = new TextView(this);
        exceptionView.setText(exceptionType);
        exceptionView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        exceptionView.setTypeface(null, Typeface.BOLD);
        exceptionView.setTextColor(Color.parseColor("#E0E0E0"));
        exceptionView.setPadding(0, dpToPx(8), 0, 0);
        root.addView(exceptionView);

        if (!friendlyMessage.isEmpty()) {
            TextView hintView = new TextView(this);
            hintView.setText("Hint: " + friendlyMessage);
            hintView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            hintView.setTextColor(Color.parseColor("#FFCA28")); // Amber
            hintView.setPadding(0, dpToPx(4), 0, 0);
            root.addView(hintView);
        }

        LinearLayout terminalContainer = new LinearLayout(this);
        terminalContainer.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
        GradientDrawable terminalBg = new GradientDrawable();
        terminalBg.setColor(Color.parseColor("#1E1E1E"));
        terminalBg.setCornerRadius(dpToPx(12));
        terminalBg.setStroke(dpToPx(1), Color.parseColor("#333333"));
        terminalContainer.setBackground(terminalBg);

        TextView errorLog = new TextView(this);
        errorLog.setText(errorMessage);
        errorLog.setTextColor(Color.parseColor("#AEEA00")); // Light Green for logs
        errorLog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        errorLog.setTypeface(Typeface.MONOSPACE);
        errorLog.setTextIsSelectable(true);

        HorizontalScrollView hScroll = new HorizontalScrollView(this);
        hScroll.addView(errorLog);
        terminalContainer.addView(hScroll);

        ScrollView vScroll = new ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        scrollParams.setMargins(0, dpToPx(24), 0, dpToPx(24));
        vScroll.setLayoutParams(scrollParams);
        vScroll.addView(terminalContainer);
        root.addView(vScroll);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button btnCopy = createButton("Copy Log", "#2196F3", "#1976D2");
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

        Button btnRestart = createButton("Restart App", "#4CAF50", "#388E3C");
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
        
        setContentView(root);
    }


    private Button createButton(String text, String colorHex, String pressedColorHex) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setAllCaps(false);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setElevation(dpToPx(2));
        btn.setPadding(0, dpToPx(12), 0, dpToPx(12));

        GradientDrawable defaultBg = new GradientDrawable();
        defaultBg.setColor(Color.parseColor(colorHex));
        defaultBg.setCornerRadius(dpToPx(24));

        btn.setBackground(defaultBg);
        return btn;
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
