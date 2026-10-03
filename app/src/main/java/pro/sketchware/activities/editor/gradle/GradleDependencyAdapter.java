package pro.sketchware.activities.editor.gradle;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Objects;

import pro.sketchware.databinding.ItemGradleDependencyBinding;
import pro.sketchware.utility.ThemeUtils;

public class GradleDependencyAdapter extends ListAdapter<GradleSyncEngine.Row, GradleDependencyAdapter.ViewHolder> {

    public interface Callback {
        void onMore(@NonNull GradleSyncEngine.Row row, @NonNull android.view.View anchor);
    }

    private final Callback callback;

    public GradleDependencyAdapter(@NonNull Callback callback) {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull GradleSyncEngine.Row a, @NonNull GradleSyncEngine.Row b) {
                return a.dep.key().equals(b.dep.key());
            }

            @Override
            public boolean areContentsTheSame(@NonNull GradleSyncEngine.Row a, @NonNull GradleSyncEngine.Row b) {
                return a.state == b.state
                        && a.dep.configuration.equals(b.dep.configuration)
                        && Objects.equals(a.dep.version, b.dep.version)
                        && Objects.equals(a.error, b.error)
                        && a.folders.equals(b.folders);
            }
        });
        this.callback = callback;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemGradleDependencyBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GradleSyncEngine.Row row = getItem(position);
        ItemGradleDependencyBinding b = holder.binding;
        b.depCoordinate.setText(row.dep.key());
        b.depVersion.setText(row.error != null ? row.error : (row.dep.version != null ? row.dep.version : "no version"));
        b.depType.setText(row.dep.configuration);
        bindStatus(holder, row.state);
        b.depMore.setOnClickListener(v -> callback.onMore(row, v));
    }

    private void bindStatus(ViewHolder holder, GradleSyncEngine.DepState state) {
        ItemGradleDependencyBinding b = holder.binding;
        int container;
        int content;
        String label;
        switch (state) {
            case READY -> {
                container = com.google.android.material.R.attr.colorPrimaryContainer;
                content = com.google.android.material.R.attr.colorOnPrimaryContainer;
                label = "Ready";
            }
            case CACHED -> {
                container = com.google.android.material.R.attr.colorTertiaryContainer;
                content = com.google.android.material.R.attr.colorOnTertiaryContainer;
                label = "Cached";
            }
            case SYNCING -> {
                container = com.google.android.material.R.attr.colorSecondaryContainer;
                content = com.google.android.material.R.attr.colorOnSecondaryContainer;
                label = "Syncing";
            }
            case FAILED -> {
                container = com.google.android.material.R.attr.colorErrorContainer;
                content = com.google.android.material.R.attr.colorOnErrorContainer;
                label = "Failed";
            }
            case SKIPPED -> {
                container = com.google.android.material.R.attr.colorSurfaceVariant;
                content = com.google.android.material.R.attr.colorOnSurfaceVariant;
                label = "Skipped";
            }
            default -> {
                container = com.google.android.material.R.attr.colorSurfaceContainerHighest;
                content = com.google.android.material.R.attr.colorOnSurface;
                label = "Pending";
            }
        }
        b.depStatus.setText(label);
        b.depStatus.setBackgroundTintList(ColorStateList.valueOf(ThemeUtils.getColor(b.getRoot().getContext(), container)));
        b.depStatus.setTextColor(ThemeUtils.getColor(b.getRoot().getContext(), content));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        final ItemGradleDependencyBinding binding;

        ViewHolder(ItemGradleDependencyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
