package dev.aldi.sayuti.editor.manage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class MavenSearchResultAdapter extends RecyclerView.Adapter<MavenSearchResultAdapter.ViewHolder> {

    public interface OnSearchResultClickedListener {
        void onDownloadClicked(@NonNull MavenSearchResult result);
    }

    private final List<MavenSearchResult> results = new ArrayList<>();
    private final Set<String> processingCoordinates;
    private final @Nullable OnSearchResultClickedListener listener;
    private MavenSearchResult selectedResult = null;

    public MavenSearchResultAdapter(@NonNull Set<String> processingCoordinates, @Nullable OnSearchResultClickedListener listener) {
        this.processingCoordinates = processingCoordinates;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_maven_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MavenSearchResult result = results.get(position);
        boolean isProcessing = processingCoordinates.contains(result.getCoordinateName());
        boolean isSelected = result.equals(selectedResult);

        holder.tvName.setText(result.getCoordinateName());
        holder.tvVersion.setText("Latest: " + result.getLatestVersion());

        if (isSelected) {
            holder.cardRoot.setStrokeColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorPrimary));
            holder.cardRoot.setStrokeWidth(pro.sketchware.utility.SketchwareUtil.dpToPx(2));
            holder.cardAction.setCardBackgroundColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorPrimary));
            holder.cardAction.setStrokeWidth(0);
            holder.imgAction.setImageResource(R.drawable.ic_mtrl_check);
            holder.imgAction.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOnPrimary));
        } else {
            holder.cardRoot.setStrokeColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOutlineVariant));
            holder.cardRoot.setStrokeWidth(pro.sketchware.utility.SketchwareUtil.dpToPx(1));
            holder.cardAction.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
            holder.cardAction.setStrokeWidth(pro.sketchware.utility.SketchwareUtil.dpToPx(1));
            holder.imgAction.setImageResource(R.drawable.ic_mtrl_download);
            holder.imgAction.setColorFilter(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOnSurfaceVariant));
        }

        holder.cardRoot.setEnabled(!isProcessing);
        holder.cardRoot.setOnClickListener(isProcessing ? null : v -> {
            if (listener != null) listener.onDownloadClicked(result);
        });
        
        holder.itemView.setAlpha(isProcessing ? 0.6f : 1f);
    }

    @Override
    public int getItemCount() {
        return results.size();
    }

    public void setResults(@NonNull List<MavenSearchResult> newResults) {
        results.clear();
        results.addAll(newResults);
        selectedResult = null;
        notifyDataSetChanged();
    }

    public void setSelected(MavenSearchResult result) {
        this.selectedResult = result;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        MaterialCardView cardAction;
        TextView tvName, tvVersion;
        ImageView imgAction;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.search_result_card);
            cardAction = itemView.findViewById(R.id.card_action_icon);
            tvName = itemView.findViewById(R.id.search_result_name);
            tvVersion = itemView.findViewById(R.id.search_result_version);
            imgAction = itemView.findViewById(R.id.img_action_icon);
        }
    }
}
