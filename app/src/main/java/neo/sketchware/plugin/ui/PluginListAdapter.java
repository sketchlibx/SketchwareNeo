package neo.sketchware.plugin.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import neo.sketchware.plugin.PluginManager;

public class PluginListAdapter extends RecyclerView.Adapter<PluginListAdapter.ViewHolder> {

    public interface OnDeleteClickListener {
        void onDeleteClick(PluginManager.PluginInfo info);
    }

    public interface OnToggleListener {
        void onToggle(PluginManager.PluginInfo info, boolean enabled);
    }

    private final List<PluginManager.PluginInfo> allItems = new ArrayList<>();
    private final List<PluginManager.PluginInfo> visibleItems = new ArrayList<>();
    private final OnDeleteClickListener deleteClickListener;
    private final OnToggleListener toggleListener;

    public PluginListAdapter(List<PluginManager.PluginInfo> items, OnDeleteClickListener deleteClickListener, OnToggleListener toggleListener) {
        this.allItems.addAll(items);
        this.visibleItems.addAll(items);
        this.deleteClickListener = deleteClickListener;
        this.toggleListener = toggleListener;
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
        holder.meta.setText((info.enabled() ? "Active" : "Disabled") + " · API v" + info.apiVersion() + " · " + info.fileName());

        holder.enabledSwitch.setOnCheckedChangeListener(null);
        holder.enabledSwitch.setChecked(info.enabled());
        holder.enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (toggleListener != null) toggleListener.onToggle(info, isChecked);
        });

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
        MaterialSwitch enabledSwitch;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(pro.sketchware.R.id.text_plugin_name);
            meta = itemView.findViewById(pro.sketchware.R.id.text_plugin_meta);
            deleteButton = itemView.findViewById(pro.sketchware.R.id.btn_delete_plugin);
            enabledSwitch = itemView.findViewById(pro.sketchware.R.id.switch_plugin_enabled);
        }
    }
}
