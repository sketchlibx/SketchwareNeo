package mod.hey.studios.project.proguard;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class LibraryInfoAdapter extends RecyclerView.Adapter<LibraryInfoAdapter.ViewHolder> {

    private List<AnalyzeLibrariesActivity.LibraryInfo> items;

    public LibraryInfoAdapter(List<AnalyzeLibrariesActivity.LibraryInfo> items) {
        this.items = items;
    }

    public void update(List<AnalyzeLibrariesActivity.LibraryInfo> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(pro.sketchware.R.layout.item_library_info, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AnalyzeLibrariesActivity.LibraryInfo item = items.get(position);
        holder.name.setText(item.name);
        holder.size.setText(formatSize(item.sizeBytes));
        holder.status.setText(item.referenced ? "Used" : "Not referenced — safe to remove");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static String formatSize(long bytes) {
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView size;
        TextView status;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(pro.sketchware.R.id.text_library_name);
            size = itemView.findViewById(pro.sketchware.R.id.text_library_size);
            status = itemView.findViewById(pro.sketchware.R.id.text_library_status);
        }
    }
}
