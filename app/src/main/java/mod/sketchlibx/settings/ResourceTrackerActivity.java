package mod.sketchlibx.settings;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.beans.ProjectFileBean;
import com.besome.sketch.beans.ProjectResourceBean;
import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import a.a.a.By;
import a.a.a.MA;
import a.a.a.jC;
import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ResourceTrackerActivity extends BaseAppCompatActivity {

    private String sc_id;
    private RecyclerView recyclerView;
    private TrackerAdapter adapter;
    private List<ProjectAnalyzerEngine.UnusedResource> currentList = new ArrayList<>();
    
    private View contentLayout;
    private View noContentLayout;
    private ExtendedFloatingActionButton fabCleanAll;

    private Dialog progressDialog;
    private TextView progressDialogText;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_tracker);

        sc_id = getIntent().getStringExtra("sc_id");

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Resource Usage Tracker");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        contentLayout = findViewById(R.id.contentLayout);
        noContentLayout = findViewById(R.id.noContentLayout);
        fabCleanAll = findViewById(R.id.fab_clean_all);
        
        recyclerView = findViewById(R.id.rv_resources);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        fabCleanAll.setOnClickListener(v -> {
            if (currentList.isEmpty()) {
                SketchwareUtil.toast("Nothing to clean!");
                return;
            }
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Clean All Resources?")
                    .setMessage("This will safely remove all " + currentList.size() + " unused resources from your project. This cannot be undone.")
                    .setPositiveButton("Clean All", (dialog, which) -> {
                        showProgress("Cleaning unused resources...");
                        new CleanAllTask(ResourceTrackerActivity.this).execute();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        new Handler(Looper.getMainLooper()).postDelayed(this::loadData, 300L);
    }

    private void showProgress(String message) {
        if (progressDialog == null) {
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setGravity(Gravity.CENTER_VERTICAL);
            container.setPadding(64, 48, 64, 48);

            CircularProgressIndicator progressIndicator = new CircularProgressIndicator(this, null, com.google.android.material.R.style.Widget_Material3Expressive_CircularProgressIndicator_Wavy);
            progressIndicator.setIndeterminate(true);
            
            progressDialogText = new TextView(this);
            progressDialogText.setTextSize(16f);
            progressDialogText.setPadding(48, 0, 0, 0);
            progressDialogText.setTextColor(ThemeUtils.getColor(this, R.attr.colorOnSurface));
            progressDialogText.setTypeface(null, Typeface.BOLD);

            container.addView(progressIndicator);
            container.addView(progressDialogText);

            progressDialog = new MaterialAlertDialogBuilder(this)
                    .setView(container)
                    .setCancelable(false)
                    .create();
        }
        progressDialogText.setText(message);
        if (!progressDialog.isShowing()) progressDialog.show();
    }

    private void hideProgress() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void loadData() {
        showProgress("Scanning for unused resources...");
        new LoadTask(this).execute();
    }

    private void updateUIState() {
        if (currentList.isEmpty()) {
            noContentLayout.setVisibility(View.VISIBLE);
            contentLayout.setVisibility(View.GONE);
            fabCleanAll.hide();
            getSupportActionBar().setSubtitle("Project is fully optimized");
        } else {
            noContentLayout.setVisibility(View.GONE);
            contentLayout.setVisibility(View.VISIBLE);
            fabCleanAll.show();
            getSupportActionBar().setSubtitle(currentList.size() + " Unused Items Found");
        }
    }

    private void performDeletion(ProjectAnalyzerEngine.UnusedResource res) {
        if (res.type == 1) {
            jC.d(sc_id).b.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 2) {
            jC.d(sc_id).c.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 3) {
            jC.d(sc_id).d.remove((ProjectResourceBean) res.reference);
        } else if (res.type == 4) {
            jC.b(sc_id).c().remove((ProjectFileBean) res.reference);
        }
    }

    private void saveAllChanges() {
        jC.d(sc_id).a();
        jC.b(sc_id).l();
        jC.b(sc_id).j();
    }

    private static class LoadTask extends MA {
        private final WeakReference<ResourceTrackerActivity> activityRef;

        public LoadTask(ResourceTrackerActivity activity) {
            super(activity);
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void b() throws By {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null) {
                activity.currentList = ProjectAnalyzerEngine.scanForUnusedResources(activity.sc_id);
            }
        }

        @Override
        public void a() {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                if (activity.adapter == null) {
                    activity.adapter = activity.new TrackerAdapter();
                    activity.recyclerView.setAdapter(activity.adapter);
                } else {
                    activity.adapter.notifyDataSetChanged();
                }
                activity.updateUIState();
            }
        }

        @Override
        public void a(String str) {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                SketchwareUtil.toastError("Load failed: " + str);
            }
        }
    }

    private static class CleanAllTask extends MA {
        private final WeakReference<ResourceTrackerActivity> activityRef;

        public CleanAllTask(ResourceTrackerActivity activity) {
            super(activity);
            this.activityRef = new WeakReference<>(activity);
        }

        @Override
        public void b() throws By {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null) {
                for (ProjectAnalyzerEngine.UnusedResource res : activity.currentList) {
                    activity.performDeletion(res);
                }
                activity.saveAllChanges();
                activity.currentList.clear();
            }
        }

        @Override
        public void a() {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                SketchwareUtil.toast("Optimized! Removed unused items.");
                if (activity.adapter != null) {
                    activity.adapter.notifyDataSetChanged();
                }
                activity.updateUIState();
            }
        }

        @Override
        public void a(String str) {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                SketchwareUtil.toastError("Clean failed: " + str);
            }
        }
    }

    private static class DeleteSingleTask extends MA {
        private final WeakReference<ResourceTrackerActivity> activityRef;
        private final ProjectAnalyzerEngine.UnusedResource res;
        private final int position;

        public DeleteSingleTask(ResourceTrackerActivity activity, ProjectAnalyzerEngine.UnusedResource res, int position) {
            super(activity);
            this.activityRef = new WeakReference<>(activity);
            this.res = res;
            this.position = position;
        }

        @Override
        public void b() throws By {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null) {
                activity.performDeletion(res);
                activity.saveAllChanges();
            }
        }

        @Override
        public void a() {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                activity.currentList.remove(position);
                if (activity.adapter != null) {
                    activity.adapter.notifyItemRemoved(position);
                    activity.adapter.notifyItemRangeChanged(position, activity.currentList.size());
                }
                activity.updateUIState();
            }
        }

        @Override
        public void a(String str) {
            ResourceTrackerActivity activity = activityRef.get();
            if (activity != null && !activity.isFinishing()) {
                activity.hideProgress();
                SketchwareUtil.toastError("Remove failed: " + str);
            }
        }
    }

    private class TrackerAdapter extends RecyclerView.Adapter<TrackerAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_unused_resource, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ProjectAnalyzerEngine.UnusedResource res = currentList.get(position);
            holder.title.setText(res.name);
            holder.subtitle.setText(res.detail);

            if (res.type == 1) holder.icon.setImageResource(R.drawable.ic_mtrl_image);
            else if (res.type == 2) holder.icon.setImageResource(R.drawable.ic_mtrl_music);
            else if (res.type == 3) holder.icon.setImageResource(R.drawable.ic_mtrl_font);
            else if (res.type == 4) holder.icon.setImageResource(R.drawable.ic_mtrl_devices);

            holder.btnDelete.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(ResourceTrackerActivity.this)
                        .setTitle("Remove Unused Item?")
                        .setMessage("Are you sure you want to remove '" + res.name + "' from your project?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            showProgress("Removing " + res.name + "...");
                            new DeleteSingleTask(ResourceTrackerActivity.this, res, position).execute();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return currentList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon, btnDelete;

            ViewHolder(View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_title);
                subtitle = itemView.findViewById(R.id.tv_subtitle);
                icon = itemView.findViewById(R.id.img_icon);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
    }
}
