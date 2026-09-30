package com.besome.sketch.editor.manage.image;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.lang.ref.WeakReference;

import a.a.a.MA;
import a.a.a.Op;
import a.a.a.fu;
import a.a.a.mB;
import a.a.a.pu;
import pro.sketchware.R;
import pro.sketchware.databinding.ManageImageBinding;

public class ManageImageActivity extends BaseAppCompatActivity implements ViewPager.OnPageChangeListener {
    private String sc_id;
    private pu projectImagesFragment;
    private fu collectionImagesFragment;
    private ManageImageBinding binding;

    public static int getImageGridColumnCount(Context context) {
        var displayMetrics = context.getResources().getDisplayMetrics();
        return (int) (displayMetrics.widthPixels / displayMetrics.density) / 100;
    }

    @Override
    public void onPageScrollStateChanged(int state) {}

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}

    public void f(int i) {
        if (binding != null && binding.viewPager != null) {
            binding.viewPager.setCurrentItem(i, true);
        }
    }

    public fu l() {
        return collectionImagesFragment;
    }

    public pu m() {
        return projectImagesFragment;
    }

    @Override
    public void onBackPressed() {
        if (projectImagesFragment != null && projectImagesFragment.isSelecting) {
            projectImagesFragment.a(false);
            showFabWithAnimation();
        } else if (collectionImagesFragment != null && collectionImagesFragment.isSelecting()) {
            collectionImagesFragment.unselectAll();
            binding.layoutBtnImport.setVisibility(View.GONE);
        } else {
            k();
            new Handler().postDelayed(() -> new SaveImagesAsyncTask(this).execute(), 300L);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ManageImageBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (!super.isStoragePermissionGranted()) {
            finish();
            return;
        }

        setSupportActionBar(binding.topAppBar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.design_actionbar_title_manager_image);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        binding.topAppBar.setNavigationOnClickListener(v -> {
            if (!mB.a()) {
                onBackPressed();
            }
        });

        if (savedInstanceState == null) {
            sc_id = getIntent().getStringExtra("sc_id");
        } else {
            sc_id = savedInstanceState.getString("sc_id");
        }

        PagerAdapter pagerAdapter = new PagerAdapter(getSupportFragmentManager());
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOffscreenPageLimit(2);
        binding.viewPager.addOnPageChangeListener(this);
        binding.tabLayout.setupWithViewPager(binding.viewPager);
        
        binding.fab.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!super.isStoragePermissionGranted()) finish();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPageSelected(int position) {
        binding.layoutBtnGroup.setVisibility(View.GONE);
        binding.layoutBtnImport.setVisibility(View.GONE);

        if (position == 0) {
            showFabWithAnimation();
            if (collectionImagesFragment != null) collectionImagesFragment.unselectAll();
        } else {
            hideFabWithAnimation();
            if (projectImagesFragment != null) projectImagesFragment.a(false);
        }
    }

    private void showFabWithAnimation() {
        binding.fab.setVisibility(View.VISIBLE);
        binding.fab.animate().translationY(0F).alpha(1.0f).setDuration(200L).start();
    }

    private void hideFabWithAnimation() {
        binding.fab.animate().translationY(200F).alpha(0.0f).setDuration(200L).withEndAction(() -> {
            binding.fab.setVisibility(View.GONE);
        }).start();
    }

    public ManageImageBinding getBinding() {
        return binding;
    }

    private static class SaveImagesAsyncTask extends MA {
        private final WeakReference<ManageImageActivity> activity;

        public SaveImagesAsyncTask(ManageImageActivity activity) {
            super(activity);
            this.activity = new WeakReference<>(activity);
            activity.a(this);
        }

        @Override
        public void a() {
            ManageImageActivity act = this.activity.get();
            if (act != null && !act.isFinishing()) {
                act.h();
                act.setResult(Activity.RESULT_OK);
                act.finish();
                Op.g().d();
            }
        }

        @Override
        public void b() {
            ManageImageActivity act = this.activity.get();
            if (act != null && act.projectImagesFragment != null) {
                act.projectImagesFragment.saveImages();
            }
        }

        @Override
        public void a(String str) {
            ManageImageActivity act = this.activity.get();
            if (act != null && !act.isFinishing()) {
                act.h();
            }
        }
    }

    private class PagerAdapter extends FragmentPagerAdapter {
        private final String[] labels;

        public PagerAdapter(FragmentManager manager) {
            super(manager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            labels = new String[]{
                    getString(R.string.design_manager_tab_title_this_project),
                    getString(R.string.design_manager_tab_title_my_collection)
            };
        }

        @Override
        public int getCount() { return 2; }

        @Override
        @NonNull
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            Fragment fragment = (Fragment) super.instantiateItem(container, position);
            if (position == 0) projectImagesFragment = (pu) fragment;
            else collectionImagesFragment = (fu) fragment;
            return fragment;
        }

        @Override
        @NonNull
        public Fragment getItem(int position) {
            return position == 0 ? new pu() : new fu();
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return labels[position];
        }
    }
}
