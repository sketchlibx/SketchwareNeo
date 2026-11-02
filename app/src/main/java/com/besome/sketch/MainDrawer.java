package com.besome.sketch;

import static com.google.android.material.theme.overlay.MaterialThemeOverlay.wrap;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

import a.a.a.mB;
import dev.chrisbanes.insetter.Insetter;
import dev.chrisbanes.insetter.Side;
import mod.hey.studios.activity.managers.cpp.NativeToolsActivity;
import mod.hilal.saif.activities.tools.AppSettings;
import mod.hilal.saif.activities.tools.BlocksManager;
import mod.sketchlibx.project.backup.CloudBackupManagerActivity;
import neo.sketchware.ai.AiSettingsActivity;
import pro.sketchware.activities.editor.component.ManageCustomComponentActivity;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;
import pro.sketchware.utility.UI;

public class MainDrawer extends NavigationView {
    private static final int DEF_STYLE_RES = R.style.Widget_SketchwarePro_NavigationView_Main;
    private TextView profileName;
    private TextView profileEmail;
    private ImageView profileAvatar;

    public MainDrawer(@NonNull Context context) {
        this(context, null);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.navigationViewStyle);
    }

    public MainDrawer(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(wrap(context, attrs, defStyleAttr, DEF_STYLE_RES), attrs, defStyleAttr);
        context = getContext();

        var layoutDirection = context.getResources().getConfiguration().getLayoutDirection();
        Insetter.builder()
                .margin(
                        WindowInsetsCompat.Type.displayCutout()
                                | WindowInsetsCompat.Type.navigationBars(),
                        Side.create(
                                layoutDirection == LAYOUT_DIRECTION_LTR,
                                false,
                                layoutDirection == LAYOUT_DIRECTION_RTL,
                                false
                        )
                )
                .applyToView(this);

        ShapeAppearanceModel shapeModel = ShapeAppearanceModel.builder()
                .setTopRightCornerSize(SketchwareUtil.dpToPx(16))
                .setBottomRightCornerSize(SketchwareUtil.dpToPx(16))
                .build();

        MaterialShapeDrawable background = new MaterialShapeDrawable(shapeModel);
        background.setFillColor(
                ColorStateList.valueOf(
                        ThemeUtils.getColor(context, R.attr.colorSurfaceContainerLow)
                )
        );
        setBackground(background);

        ViewGroup headerView = (ViewGroup) LayoutInflater.from(context)
                .inflate(R.layout.main_drawer_header, null);
        headerView.findViewById(R.id.status_bar_overlapper)
                .setMinimumHeight(UI.getStatusBarHeight(context));

        profileName = headerView.findViewById(R.id.profile_name);
        profileEmail = headerView.findViewById(R.id.profile_email);
        profileAvatar = headerView.findViewById(R.id.profile_avatar);
        ImageView profileExpand = headerView.findViewById(R.id.profile_expand);
        View.OnClickListener openAccount = v -> openAccountSettings();
        if (profileExpand != null) profileExpand.setOnClickListener(openAccount);
        if (profileAvatar != null) profileAvatar.setOnClickListener(openAccount);
        if (profileName != null) profileName.setOnClickListener(openAccount);
        if (profileEmail != null) profileEmail.setOnClickListener(openAccount);

        View btnClose = headerView.findViewById(R.id.btn_close_drawer);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                Activity activity = unwrap(getContext());
                if (activity instanceof pro.sketchware.activities.main.activities.MainActivity) {
                    DrawerLayout dl = activity.findViewById(R.id.drawer_layout);
                    if (dl != null) dl.closeDrawers();
                }
            });
        }

        addHeaderView(headerView);
        inflateMenu(R.menu.main_drawer_menu);
        setNavigationItemSelectedListener(item -> {
            initializeSocialLinks(item.getItemId());
            initializeDrawerItems(item.getItemId());
            return false;
        });

        refreshProfile();
    }

    public void refreshProfile() {
        if (profileName == null || profileEmail == null) return;
        GoogleSignInAccount account = null;
        try {
            account = GoogleSignIn.getLastSignedInAccount(getContext());
        } catch (Exception ignored) {
        }

        if (account == null) {
            profileName.setText("Sign in");
            profileEmail.setText("Connect Google Drive for cloud backup");
            if (profileAvatar != null) {
                Glide.with(profileAvatar).clear(profileAvatar);
                profileAvatar.setPadding(SketchwareUtil.dpToPx(10), SketchwareUtil.dpToPx(10),
                        SketchwareUtil.dpToPx(10), SketchwareUtil.dpToPx(10));
                profileAvatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                profileAvatar.setBackgroundTintList(ColorStateList.valueOf(
                        ThemeUtils.getColor(getContext(), R.attr.colorPrimaryContainer)));
                profileAvatar.setImageResource(R.drawable.ic_user);
                profileAvatar.setImageTintList(ColorStateList.valueOf(
                        ThemeUtils.getColor(getContext(), R.attr.colorOnPrimaryContainer)));
            }
            return;
        }

        String displayName = account.getDisplayName();
        String email = account.getEmail();
        profileName.setText(displayName == null || displayName.trim().isEmpty() ? "Google Account" : displayName);
        profileEmail.setText(email == null || email.trim().isEmpty() ? "Google Drive connected" : email);

        if (profileAvatar != null) {
            Uri photoUri = account.getPhotoUrl();
            if (photoUri != null) {
                profileAvatar.setPadding(0, 0, 0, 0);
                profileAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
                profileAvatar.setBackgroundTintList(ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
                profileAvatar.setImageTintList(null);
                Glide.with(profileAvatar).load(photoUri).circleCrop()
                        .placeholder(R.drawable.ic_user).error(R.drawable.ic_user).into(profileAvatar);
            } else {
                Glide.with(profileAvatar).clear(profileAvatar);
                profileAvatar.setPadding(SketchwareUtil.dpToPx(10), SketchwareUtil.dpToPx(10),
                        SketchwareUtil.dpToPx(10), SketchwareUtil.dpToPx(10));
                profileAvatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                profileAvatar.setBackgroundTintList(ColorStateList.valueOf(
                        ThemeUtils.getColor(getContext(), R.attr.colorPrimaryContainer)));
                profileAvatar.setImageResource(R.drawable.ic_user);
                profileAvatar.setImageTintList(ColorStateList.valueOf(
                        ThemeUtils.getColor(getContext(), R.attr.colorOnPrimaryContainer)));
            }
        }
    }

    private void openAccountSettings() {
        Activity activity = unwrap(getContext());
        Intent intent = new Intent(activity, CloudBackupManagerActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activity.startActivity(intent);
    }

    private void initializeSocialLinks(@IdRes int id) {
        if (!mB.a()) {
            @StringRes int url = -1;
            if (id == R.id.social_telegram) {
                url = R.string.link_telegram_invite;
            } else if (id == R.id.social_github) {
                url = R.string.link_github_url;
            }

            if (url != -1) {
                openUrl(getContext().getString(url));
            }
        }
    }

    private void initializeDrawerItems(@IdRes int id) {
        Activity activity = unwrap(getContext());
        
        if (id == R.id.app_settings) {
            Intent intent = new Intent(activity, AppSettings.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.ai_agents) {
            Intent intent = new Intent(activity, AiSettingsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.build_tools) {
            Intent intent = new Intent(activity, NativeToolsActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.component_manager) {
            Intent intent = new Intent(activity, ManageCustomComponentActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.block_manager) {
            Intent intent = new Intent(activity, BlocksManager.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.cloud_backup) {
            Intent intent = new Intent(activity, CloudBackupManagerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.system_information) {
            Intent intent = new Intent(activity, com.besome.sketch.help.ProgramInfoActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            activity.startActivity(intent);
        } else if (id == R.id.nav_home) {
            if (activity instanceof pro.sketchware.activities.main.activities.MainActivity) {
                DrawerLayout dl = activity.findViewById(R.id.drawer_layout);
                if (dl != null) dl.closeDrawers();
            }
        }
    }

    private void openUrl(String url) {
        Activity activity = unwrap(getContext());
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        activity.startActivity(intent);
    }

    private Activity unwrap(Context context) {
        while (!(context instanceof Activity) && context instanceof ContextWrapper) {
            context = ((ContextWrapper) context).getBaseContext();
        }
        return (Activity) context;
    }
}
