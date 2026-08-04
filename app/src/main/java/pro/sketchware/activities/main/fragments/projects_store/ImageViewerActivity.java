package pro.sketchware.activities.main.fragments.projects_store;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.besome.sketch.lib.base.BaseAppCompatActivity;

import java.util.ArrayList;

import pro.sketchware.activities.main.fragments.projects_store.classes.ZoomableImageView;
import pro.sketchware.databinding.ActivityImageViewerBinding;
import pro.sketchware.utility.UI;

public class ImageViewerActivity extends BaseAppCompatActivity {

    public static final String EXTRA_IMAGES = "extra_images";
    public static final String EXTRA_START_INDEX = "extra_start_index";

    private ActivityImageViewerBinding binding;
    private boolean isChromeVisible = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);

        binding = ActivityImageViewerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        ArrayList<String> images = getIntent().getStringArrayListExtra(EXTRA_IMAGES);
        int startIndex = getIntent().getIntExtra(EXTRA_START_INDEX, 0);

        if (images == null || images.isEmpty()) {
            finish();
            return;
        }

        binding.pager.setAdapter(new ImagePagerAdapter(images, this::toggleChrome));
        binding.pager.setCurrentItem(startIndex, false);

        binding.btnClose.setOnClickListener(v -> finish());

        if (images.size() > 1) {
            binding.counter.setVisibility(View.VISIBLE);
            updateCounter(startIndex, images.size());
            binding.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                @Override
                public void onPageSelected(int position) {
                    updateCounter(position, images.size());
                }
            });
        } else {
            binding.counter.setVisibility(View.GONE);
        }

        UI.addSystemWindowInsetToMargin(binding.btnClose, true, true, false, false);
        UI.addSystemWindowInsetToMargin(binding.counter, false, false, false, true);
    }

    private void updateCounter(int position, int total) {
        binding.counter.setText((position + 1) + " / " + total);
    }

    private void toggleChrome() {
        isChromeVisible = !isChromeVisible;
        float targetAlpha = isChromeVisible ? 1f : 0f;

        binding.btnClose.animate()
                .alpha(targetAlpha)
                .withStartAction(() -> binding.btnClose.setVisibility(View.VISIBLE))
                .withEndAction(() -> binding.btnClose.setVisibility(isChromeVisible ? View.VISIBLE : View.INVISIBLE))
                .setDuration(150)
                .start();

        binding.counter.animate()
                .alpha(targetAlpha)
                .setDuration(150)
                .start();
    }

    private static class ImagePagerAdapter extends RecyclerView.Adapter<ImagePagerAdapter.ViewHolder> {

        private final ArrayList<String> images;
        private final Runnable onTap;

        ImagePagerAdapter(ArrayList<String> images, Runnable onTap) {
            this.images = images;
            this.onTap = onTap;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            ZoomableImageView imageView = new ZoomableImageView(parent.getContext());
            imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            imageView.setOnImageTapListener(onTap::run);
            return new ViewHolder(imageView);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            UI.loadImageFromUrl(holder.imageView, images.get(position));
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            final ZoomableImageView imageView;

            ViewHolder(ZoomableImageView imageView) {
                super(imageView);
                this.imageView = imageView;
            }
        }
    }
}
