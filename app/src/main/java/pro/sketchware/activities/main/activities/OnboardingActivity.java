package pro.sketchware.activities.main.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import mod.sketchlibx.project.backup.CloudBackupManagerActivity;
import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class OnboardingActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "onboarding";
    private static final String KEY_COMPLETED = "completed";

    private static final String[] TITLES = {
            "Build apps with blocks",
            "Edit code when you need it",
            "Keep your projects backed up"
    };
    private static final String[] DESCRIPTIONS = {
            "Create screens and add actions by joining blocks. You can start without writing code.",
            "Open Java and XML editors, manage project files, and build your app in one place.",
            "Connect Google Drive to back up and restore projects. You can also skip sign-in and use the app."
    };
    private static final int[] ICONS = {
            R.drawable.ic_mtrl_design,
            R.drawable.ic_mtrl_code,
            R.drawable.ic_mtrl_cloud
    };

    private ViewPager pager;
    private TextView pageIndicator;
    private MaterialButton nextButton;
    private MaterialButton signInButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isCompleted()) {
            finish();
            return;
        }
        setContentView(createContent());
        pager.setAdapter(new OnboardingPagerAdapter());
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                updatePageControls(position);
            }
        });
        updatePageControls(0);
    }

    private View createContent() {
        int surface = color(com.google.android.material.R.attr.colorSurface);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(surface);
        root.setPadding(dp(24), dp(18), dp(24), dp(24));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView brand = new TextView(this);
        brand.setText("SKETCHWARE NEO");
        brand.setTextSize(13);
        brand.setLetterSpacing(0.08f);
        brand.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        brand.setTextColor(color(R.attr.colorPrimary));
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(48), 1f));

        MaterialButton skipButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        skipButton.setText("Skip");
        skipButton.setMinHeight(dp(40));
        skipButton.setInsetTop(0);
        skipButton.setInsetBottom(0);
        skipButton.setOnClickListener(v -> completeOnboarding());
        header.addView(skipButton);
        root.addView(header);

        pager = new ViewPager(this);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        root.addView(pager, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        pageIndicator = new TextView(this);
        pageIndicator.setGravity(Gravity.CENTER);
        pageIndicator.setTextSize(14);
        pageIndicator.setTextColor(color(R.attr.colorPrimary));
        LinearLayout.LayoutParams indicatorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(32));
        indicatorParams.bottomMargin = dp(10);
        root.addView(pageIndicator, indicatorParams);

        signInButton = new MaterialButton(this, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        signInButton.setText("Sign in with Google");
        signInButton.setIconResource(R.drawable.ic_mtrl_cloud);
        signInButton.setOnClickListener(v -> openGoogleSignIn());
        root.addView(signInButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        nextButton = new MaterialButton(this);
        nextButton.setText("Next");
        nextButton.setOnClickListener(v -> {
            int current = pager.getCurrentItem();
            if (current < TITLES.length - 1) pager.setCurrentItem(current + 1, true);
            else completeOnboarding();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        nextParams.topMargin = dp(10);
        root.addView(nextButton, nextParams);
        return root;
    }

    private View createPage(int position) {
        LinearLayout page = new LinearLayout(this);
        page.setGravity(Gravity.CENTER);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(4), dp(18), dp(4), dp(18));

        MaterialCardView iconCard = new MaterialCardView(this);
        iconCard.setRadius(dp(32));
        iconCard.setCardElevation(0);
        iconCard.setCardBackgroundColor(color(com.google.android.material.R.attr.colorPrimaryContainer));
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(dp(144), dp(144));
        FrameLayout iconFrame = new FrameLayout(this);
        ImageView icon = new ImageView(this);
        icon.setImageResource(ICONS[position]);
        icon.setColorFilter(color(com.google.android.material.R.attr.colorOnPrimaryContainer));
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER);
        iconFrame.addView(icon, imageParams);
        iconCard.addView(iconFrame, iconParams);
        page.addView(iconCard, new LinearLayout.LayoutParams(dp(144), dp(144)));

        TextView step = new TextView(this);
        step.setText("STEP 0" + (position + 1) + " OF 03");
        step.setTextSize(12);
        step.setLetterSpacing(0.1f);
        step.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        step.setTextColor(color(R.attr.colorPrimary));
        LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        stepParams.topMargin = dp(34);
        step.setGravity(Gravity.CENTER);
        page.addView(step, stepParams);

        TextView title = new TextView(this);
        title.setText(TITLES[position]);
        title.setTextSize(26);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(color(com.google.android.material.R.attr.colorOnSurface));
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(14);
        page.addView(title, titleParams);

        TextView description = new TextView(this);
        description.setText(DESCRIPTIONS[position]);
        description.setTextSize(16);
        description.setLineSpacing(dp(4), 1f);
        description.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurfaceVariant));
        description.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = dp(12);
        descriptionParams.leftMargin = dp(8);
        descriptionParams.rightMargin = dp(8);
        page.addView(description, descriptionParams);
        return page;
    }

    private void updatePageControls(int position) {
        if (pageIndicator == null || nextButton == null || signInButton == null) return;
        pageIndicator.setText(position == 0 ? "●  ○  ○" : position == 1 ? "○  ●  ○" : "○  ○  ●");
        boolean lastPage = position == TITLES.length - 1;
        signInButton.setVisibility(lastPage ? View.VISIBLE : View.GONE);
        nextButton.setText(lastPage ? "Get started" : "Next");
    }

    private void openGoogleSignIn() {
        markCompleted();
        startActivity(new Intent(this, CloudBackupManagerActivity.class));
        finish();
    }

    private void completeOnboarding() {
        markCompleted();
        finish();
    }

    private boolean isCompleted() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(KEY_COMPLETED, false);
    }

    private void markCompleted() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        preferences.edit().putBoolean(KEY_COMPLETED, true).apply();
    }

    private int color(int attribute) {
        return ThemeUtils.getColor(this, attribute);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private class OnboardingPagerAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return TITLES.length;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            View page = createPage(position);
            container.addView(page, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            return page;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }
    }
}
