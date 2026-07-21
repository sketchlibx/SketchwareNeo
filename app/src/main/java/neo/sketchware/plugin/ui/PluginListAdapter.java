package neo.sketchware.plugin.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import neo.sketchware.plugin.PluginManager;

public class PluginListAdapter extends RecyclerView.Adapter<PluginListAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(PluginManager.PluginInfo info);
    }

    private final List<PluginManager.PluginInfo> allItems = new ArrayList<>();
    private final List<PluginManager.PluginInfo> visibleItems = new ArrayList<>();
    private final OnDeleteClickListener deleteClickListener;

    public PluginListAdapter(List<PluginManager.PluginInfo> items, OnDeleteClickListener deleteClickListener) {
        this.allItems.addAll(items);
        this.visibleItems.addAll(items);
        this.deleteClickListener = deleteClickListener;
    }

    public void update(List<PluginManager.PluginInfo> newItems) {
        allItems.clear();
        allItems.addAll(newItems);
        visibleItems.clear();
        visibleItems.addAll(newItems);
        notifyDataSetChanged();
    }

    public void filter(String query) {
        visibleItems.clear();
        if (query == null || query.trim().isEmpty()) {
            visibleItems.addAll(allItems);
        } else {
            String lower = query.trim().toLowerCase(Locale.ROOT);
            for (PluginManager.PluginInfo info : allItems) {
                if (info.pluginId().toLowerCase(Locale.ROOT).contains(lower)) {
                    visibleItems.add(info);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return allItems.isEmpty();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(pro.sketchware.R.layout.item_plugin, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PluginManager.PluginInfo info = visibleItems.get(position);
        holder.name.setText(info.pluginId());
        holder.meta.setText("API v" + info.apiVersion() + " · " + info.fileName());
        holder.deleteButton.setOnClickListener(v -> {
            if (deleteClickListener != null) deleteClickListener.onDeleteClick(info);
        });
    }

    @Override
    public int getItemCount() {
        return visibleItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView meta;
        ImageButton deleteButton;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(pro.sketchware.R.id.text_plugin_name);
            meta = itemView.findViewById(pro.sketchware.R.id.text_plugin_meta);
            deleteButton = itemView.findViewById(pro.sketchware.R.id.btn_delete_plugin);
        }
    }
}
