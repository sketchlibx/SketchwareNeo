package dev.aldi.sayuti.editor.manage;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import pro.sketchware.databinding.ItemMavenSearchResultBinding;

public class MavenSearchResultAdapter extends RecyclerView.Adapter<MavenSearchResultAdapter.ViewHolder> {

    public interface OnSearchResultClickedListener {
        void onClicked(@NonNull MavenSearchResult result);
    }

    private final List<MavenSearchResult> results = new ArrayList<>();
    private final Set<String> processingCoordinates;
    private final @Nullable OnSearchResultClickedListener listener;

    public MavenSearchResultAdapter(@NonNull Set<String> processingCoordinates, @Nullable OnSearchResultClickedListener listener) {
        this.processingCoordinates = processingCoordinates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemMavenSearchResultBinding binding = ItemMavenSearchResultBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MavenSearchResult result = results.get(position);
        boolean isProcessing = processingCoordinates.contains(result.getCoordinateName());

        holder.binding.searchResultName.setText(result.getCoordinateName());
        holder.binding.searchResultVersion.setText(isProcessing ? "Working..." : "Latest: " + result.getLatestVersion());
        holder.binding.getRoot().setAlpha(isProcessing ? 0.5f : 1f);
        holder.binding.getRoot().setEnabled(!isProcessing);
        holder.binding.getRoot().setOnClickListener(isProcessing ? null : v -> {
            if (listener != null) listener.onClicked(result);
        });
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void setResults(@NonNull List<MavenSearchResult> newResults) {
        results.clear();
        results.addAll(newResults);
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemMavenSearchResultBinding binding;

        public ViewHolder(@NonNull ItemMavenSearchResultBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
