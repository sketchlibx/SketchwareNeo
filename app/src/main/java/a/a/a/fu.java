package a.a.a;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.editor.manage.image.ManageImageActivity;
import com.besome.sketch.editor.manage.image.ManageImageImportActivity;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;

import pro.sketchware.R;
import pro.sketchware.databinding.FrManageImageListBinding;
import pro.sketchware.databinding.ManageImageListItemBinding;

public class fu extends qA implements View.OnClickListener {

    private final ActivityResultLauncher<Intent> openImageImportDetails = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        var data = result.getData();
        if (result.getResultCode() == Activity.RESULT_OK && data != null) {
            ArrayList<ProjectResourceBean> importedImages;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                importedImages = data.getParcelableArrayListExtra("results", ProjectResourceBean.class);
            } else {
                importedImages = data.getParcelableArrayListExtra("results");
            }
            ArrayList<ProjectResourceBean> newImportedImages = new ArrayList<>();
            for (ProjectResourceBean image : importedImages) {
                newImportedImages.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, image.resName, image.resFullName));
            }
            if (!newImportedImages.isEmpty() && getActivity() instanceof ManageImageActivity) {
                ((ManageImageActivity) requireActivity()).m().a(newImportedImages);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (getActivity() != null && !getActivity().isFinishing()) {
                        ((ManageImageActivity) requireActivity()).f(0);
                    }
                }, 200);
            }
        }
    });

    private FrManageImageListBinding binding;
    private ArrayList<ProjectResourceBean> collectionImages;
    private Adapter adapter;
    private String sc_id;

    private Button btnImport;
    private MaterialCardView layoutBtnImport;
    private boolean isImportButtonHidden = false;

    public void refreshData() {
        collectionImages = Op.g().f();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        updateGuideVisibility();
    }

    public void unselectAll() {
        if (collectionImages != null) {
            for (ProjectResourceBean image : collectionImages) {
                image.isSelected = false;
            }
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
        hideImportButton();
    }

    public boolean isSelecting() {
        if (collectionImages != null) {
            for (ProjectResourceBean image : collectionImages) {
                if (image.isSelected) return true;
            }
        }
        return false;
    }

    public void updateGuideVisibility() {
        if (collectionImages != null && binding != null) {
            boolean isEmpty = collectionImages.isEmpty();
            binding.tvGuide.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.imageList.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    public void importImages() {
        ArrayList<ProjectResourceBean> selectedCollections = new ArrayList<>();
        for (ProjectResourceBean image : collectionImages) {
            if (image.isSelected) {
                selectedCollections.add(new ProjectResourceBean(ProjectResourceBean.PROJECT_RES_TYPE_FILE, image.resName, wq.a() + File.separator + "image" + File.separator + "data" + File.separator + image.resFullName));
            }
        }
        if (!selectedCollections.isEmpty() && getActivity() instanceof ManageImageActivity) {
            Intent intent = new Intent(requireActivity(), ManageImageImportActivity.class);
            intent.putParcelableArrayListExtra("project_images", ((ManageImageActivity) requireActivity()).m().d());
            intent.putParcelableArrayListExtra("selected_collections", selectedCollections);
            openImageImportDetails.launch(intent);
        }
        unselectAll();
    }

    private void onItemSelected() {
        int count = 0;
        for (ProjectResourceBean image : collectionImages) {
            if (image.isSelected) {
                count += 1;
            }
        }
        if (count > 0) {
            btnImport.setText(getString(R.string.common_word_import_count, count).toUpperCase());
            if (layoutBtnImport.getVisibility() != View.VISIBLE || isImportButtonHidden) {
                showImportButton();
            }
        } else {
            hideImportButton();
        }
    }

    private void showImportButton() {
        if (layoutBtnImport != null && (layoutBtnImport.getVisibility() != View.VISIBLE || isImportButtonHidden)) {
            layoutBtnImport.setVisibility(View.VISIBLE);
            layoutBtnImport.animate()
                    .translationY(0f)
                    .alpha(1.0f)
                    .setDuration(200L)
                    .withEndAction(() -> isImportButtonHidden = false)
                    .start();
        }
    }

    private void hideImportButton() {
        if (layoutBtnImport != null && layoutBtnImport.getVisibility() == View.VISIBLE && !isImportButtonHidden) {
            isImportButtonHidden = true;
            layoutBtnImport.animate()
                    .translationY(200f)
                    .alpha(0.0f)
                    .setDuration(200L)
                    .withEndAction(() -> layoutBtnImport.setVisibility(View.GONE))
                    .start();
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sc_id = savedInstanceState != null ? savedInstanceState.getString("sc_id")
                : requireActivity().getIntent().getStringExtra("sc_id");
        refreshData();
    }

    @Override
    public void onClick(View v) {
        if (!mB.a() && v.getId() == R.id.btn_import) {
            hideImportButton();
            importImages();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (binding.imageList.getLayoutManager() instanceof GridLayoutManager manager) {
            manager.setSpanCount(ManageImageActivity.getImageGridColumnCount(requireContext()));
        }
        binding.imageList.requestLayout();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FrManageImageListBinding.inflate(inflater, container, false);
        binding.imageList.setHasFixedSize(true);
        binding.imageList.setLayoutManager(new GridLayoutManager(requireActivity(), ManageImageActivity.getImageGridColumnCount(requireContext())));
        collectionImages = new ArrayList<>();
        adapter = new Adapter();
        binding.imageList.setAdapter(adapter);
        binding.tvGuide.setText(R.string.design_manager_image_description_guide_add_image);
        btnImport = requireActivity().findViewById(R.id.btn_import);
        layoutBtnImport = requireActivity().findViewById(R.id.layout_btn_import);
        btnImport.setOnClickListener(this);

        layoutBtnImport.setVisibility(View.GONE);
        layoutBtnImport.setAlpha(0f);
        layoutBtnImport.setTranslationY(200f);
        isImportButtonHidden = true;

        binding.imageList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (isSelecting()) {
                    if (dy > 10 && !isImportButtonHidden) {
                        hideImportButton();
                    } else if (dy < -10 && isImportButtonHidden) {
                        showImportButton();
                    }
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString("sc_id", sc_id);
        super.onSaveInstanceState(outState);
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.ViewHolder> {

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectResourceBean image = collectionImages.get(position);

            Glide.with(requireActivity()).clear(holder.binding.img);
            holder.binding.img.setImageDrawable(null);

            holder.binding.chkSelect.setVisibility(View.VISIBLE);
            holder.binding.imgNinePatch.setVisibility(image.isNinePatch() ? View.VISIBLE : View.GONE);

            holder.binding.img.setAlpha(image.isSelected ? 0.5f : 1.0f);

            Glide.with(requireActivity())
                    .asBitmap()
                    .load(wq.a() + File.separator + "image" + File.separator + "data" + File.separator + image.resFullName)
                    .centerCrop()
                    .error(R.drawable.ic_remove_grey600_24dp)
                    .into(new BitmapImageViewTarget(holder.binding.img).getView());

            holder.binding.tvImageName.setText(image.resName);
            holder.binding.chkSelect.setChecked(image.isSelected);
        }

        @Override
        @NonNull
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(ManageImageListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public int getItemCount() {
            return collectionImages.size();
        }

        private class ViewHolder extends RecyclerView.ViewHolder {
            public final ManageImageListItemBinding binding;

            public ViewHolder(@NonNull ManageImageListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.chkSelect.setVisibility(View.VISIBLE);

                binding.img.setOnClickListener(v -> {
                    binding.chkSelect.setChecked(!binding.chkSelect.isChecked());
                    collectionImages.get(getLayoutPosition()).isSelected = binding.chkSelect.isChecked();
                    onItemSelected();
                    notifyItemChanged(getLayoutPosition());
                });
            }
        }
    }
}