package dev.aldi.sayuti.editor.manage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class MavenSearchResultAdapter extends RecyclerView.Adapter<MavenSearchResultAdapter.ViewHolder> {

    public interface OnSearchResultClickedListener {
        void onDownloadClicked(@NonNull MavenSearchResult result);
    }

    public interface SelectionListener {
        void onResultClicked(@NonNull MavenSearchResult result);

        void onVersionClicked(@NonNull MavenSearchResult result);
    }

    private final List<MavenSearchResult> results = new ArrayList<>();
    private final Set<String> processingCoordinates;
    private final @Nullable OnSearchResultClickedListener listener;
    private final @Nullable Map<String, MavenSearchResult> selection;
    private final @Nullable SelectionListener selectionListener;
    private MavenSearchResult selectedResult = null;

    public MavenSearchResultAdapter(@NonNull Set<String> processingCoordinates, @Nullable OnSearchResultClickedListener listener) {
        this.processingCoordinates = processingCoordinates;
        this.listener = listener;
        this.selection = null;
        this.selectionListener = null;
    }

    public MavenSearchResultAdapter(@NonNull Set<String> processingCoordinates, @NonNull Map<String, MavenSearchResult> selection,
                                    @NonNull SelectionListener selectionListener) {
        this.processingCoordinates = processingCoordinates;
        this.listener = null;
        this.selection = selection;
        this.selectionListener = selectionListener;
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
        boolean multi = selection != null;
        MavenSearchResult effective = result;
        boolean isSelected;
        if (multi) {
            MavenSearchResult chosen = selection.get(result.getCoordinateName());
            isSelected = chosen != null;
            if (chosen != null) effective = chosen;
        } else {
            isSelected = result.equals(selectedResult);
        }
        boolean isProcessing = processingCoordinates.contains(result.getCoordinateName());
        final MavenSearchResult shown = effective;

        holder.tvName.setText(result.getCoordinateName());
        if (multi && isSelected) {
            String selected = shown.getSelectedVersion();
            String suffix = selected.equals(shown.getLatestVersion()) ? " (latest)" : " (latest " + shown.getLatestVersion() + ")";
            holder.tvVersion.setText("Version " + selected + suffix);
            holder.btnVersion.setVisibility(View.VISIBLE);
            holder.btnVersion.setEnabled(!isProcessing);
            holder.btnVersion.setOnClickListener(v -> selectionListener.onVersionClicked(shown));
        } else {
            StringBuilder info = new StringBuilder("Latest: ").append(result.getLatestVersion());
            if (result.getPackaging() != null) info.append("  \u00b7  ").append(result.getPackaging());
            if (result.getVersionCount() > 0) info.append("  \u00b7  ").append(result.getVersionCount()).append(" versions");
            holder.tvVersion.setText(info);
            holder.btnVersion.setVisibility(View.GONE);
            holder.btnVersion.setOnClickListener(null);
        }

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
            if (multi) selectionListener.onResultClicked(result);
            else if (listener != null) listener.onDownloadClicked(result);
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
        if (selection == null) selectedResult = null;
        notifyDataSetChanged();
    }

    @NonNull
    public List<MavenSearchResult> getResults() {
        return new ArrayList<>(results);
    }

    public void setSelected(MavenSearchResult result) {
        this.selectedResult = result;
        notifyDataSetChanged();
    }

    public void refreshResult(@NonNull String coordinateName) {
        for (int i = 0; i < results.size(); i++) {
            if (results.get(i).getCoordinateName().equals(coordinateName)) {
                notifyItemChanged(i);
            }
        }
    }

    public void refreshAll() {
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardRoot;
        MaterialCardView cardAction;
        TextView tvName, tvVersion;
        ImageView imgAction;
        MaterialButton btnVersion;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRoot = itemView.findViewById(R.id.search_result_card);
            cardAction = itemView.findViewById(R.id.card_action_icon);
            tvName = itemView.findViewById(R.id.search_result_name);
            tvVersion = itemView.findViewById(R.id.search_result_version);
            imgAction = itemView.findViewById(R.id.img_action_icon);
            btnVersion = itemView.findViewById(R.id.btn_version);
        }
    }
}
