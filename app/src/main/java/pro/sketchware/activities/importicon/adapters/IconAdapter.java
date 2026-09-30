package pro.sketchware.activities.importicon.adapters;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.databinding.ImportIconListItemBinding;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.SvgUtils;
import pro.sketchware.utility.ThemeUtils;

public class IconAdapter extends ListAdapter<Pair<String, String>, IconAdapter.ViewHolder> {
    private static final DiffUtil.ItemCallback<Pair<String, String>> DIFF_CALLBACK = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return oldItem.first.equals(newItem.first);
        }

        @Override
        public boolean areContentsTheSame(@NonNull Pair<String, String> oldItem, @NonNull Pair<String, String> newItem) {
            return true;
        }
    };

    private final SvgUtils svgUtils;
    private final OnIconSelectedListener listener;
    private String selected_icon_type;
    private int selected_color;

    private final Set<Integer> selectedItems = new HashSet<>();
    private boolean isSelectionMode = false;

    public IconAdapter(Context context, String selected_icon_type, int selected_color, OnIconSelectedListener listener) {
        super(DIFF_CALLBACK);
        svgUtils = new SvgUtils(context);
        this.selected_icon_type = selected_icon_type;
        this.selected_color = selected_color;
        this.listener = listener;
    }

    public void setSelectedIconType(String selected_icon_type) {
        this.selected_icon_type = selected_icon_type;
    }

    public void setSelectedColor(int selected_color) {
        this.selected_color = selected_color;
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(int position) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position);
        } else {
            selectedItems.add(position);
        }
        notifyItemChanged(position);
        
        if (selectedItems.isEmpty()) {
            setSelectionMode(false);
        }
        if (listener != null) {
            listener.onSelectionChanged(selectedItems.size());
        }
    }

    public Set<Integer> getSelectedItems() {
        return selectedItems;
    }

    public void clearSelection() {
        setSelectionMode(false);
        if (listener != null) {
            listener.onSelectionChanged(0);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String filePath = getItem(position).second + File.separator + selected_icon_type + ".svg";
        svgUtils.loadImage(holder.itemBinding.img, filePath);
        holder.itemBinding.img.setColorFilter(selected_color, PorterDuff.Mode.SRC_IN);
        holder.itemBinding.title.setText(getItem(position).first);

        com.google.android.material.card.MaterialCardView card = (com.google.android.material.card.MaterialCardView) holder.itemBinding.getRoot();
        Context ctx = card.getContext();

        if (selectedItems.contains(position)) {
            card.setCardBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorSecondaryContainer));
            card.setStrokeColor(ThemeUtils.getColor(ctx, R.attr.colorPrimary));
            card.setStrokeWidth(SketchwareUtil.dpToPx(2));
            holder.itemBinding.imgSelected.setVisibility(View.VISIBLE);
        } else {
            card.setCardBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorSurface));
            card.setStrokeColor(ThemeUtils.getColor(ctx, R.attr.colorOutlineVariant));
            card.setStrokeWidth(SketchwareUtil.dpToPx(1));
            holder.itemBinding.imgSelected.setVisibility(View.GONE);
        }
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ImportIconListItemBinding binding = ImportIconListItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    public interface OnIconSelectedListener {
        void onIconSelected(int position);
        void onIconLongClicked(int position);
        void onSelectionChanged(int count);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ImportIconListItemBinding itemBinding;

        public ViewHolder(ImportIconListItemBinding binding) {
            super(binding.getRoot());
            itemBinding = binding;
            
            binding.getRoot().setOnClickListener(v -> {
                int position = getLayoutPosition();
                if (position == RecyclerView.NO_POSITION) return;
                
                if (isSelectionMode) {
                    toggleSelection(position);
                } else if (listener != null) {
                    listener.onIconSelected(position);
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                int position = getLayoutPosition();
                if (position == RecyclerView.NO_POSITION) return true;

                if (!isSelectionMode) {
                    setSelectionMode(true);
                    toggleSelection(position);
                    if (listener != null) {
                        listener.onIconLongClicked(position);
                    }
                }
                return true;
            });
        }
    }
}
