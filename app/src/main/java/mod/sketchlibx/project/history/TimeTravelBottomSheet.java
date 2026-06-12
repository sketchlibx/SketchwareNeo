package mod.sketchlibx.project.history;

import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.design.DesignActivity;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;

public class TimeTravelBottomSheet extends BottomSheetDialogFragment {

    private final String sc_id;
    private final DesignActivity activity;

    public TimeTravelBottomSheet(String sc_id, DesignActivity activity) {
        this.sc_id = sc_id;
        this.activity = activity;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.dialog_version_history, container, false);

        RecyclerView recyclerView = root.findViewById(R.id.rv_history);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        File historyFolder = new File(Environment.getExternalStorageDirectory(), ".sketchware/backups/history/" + sc_id);
        List<File> snapshots = new ArrayList<>();
        
        // Ensure we only read .zip files to prevent cross-project leaking or garbage data
        if (historyFolder.exists() && historyFolder.listFiles() != null) {
            for (File file : historyFolder.listFiles()) {
                if (file.getName().endsWith(".zip")) {
                    snapshots.add(file);
                }
            }
            snapshots.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        }

        if (snapshots.isEmpty()) {
            TextView subtitle = root.findViewById(R.id.subtitle);
            subtitle.setText("No history found. Save or Run the project to create a snapshot.");
        } else {
            recyclerView.setAdapter(new SnapshotAdapter(snapshots));
        }

        return root;
    }

    private class SnapshotAdapter extends RecyclerView.Adapter<SnapshotAdapter.ViewHolder> {
        private final List<File> items;

        public SnapshotAdapter(List<File> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_history, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            File file = items.get(position);
            
            String displayName = file.getName().replace("Snapshot_", "").replace(".zip", "").replace("_", " ");
            holder.title.setText(displayName);
            
            // Format file size nicely
            String fileSize = Formatter.formatShortFileSize(getContext(), file.length());
            holder.subtitle.setText("Size: " + fileSize);

            holder.itemView.setOnClickListener(v -> {
                new MaterialAlertDialogBuilder(activity)
                        .setTitle("Restore Snapshot?")
                        .setMessage("Are you sure you want to travel back in time to this version? Your current blocks and UI will be overwritten.")
                        .setPositiveButton("Restore", (dialog, which) -> {
                            dismiss();
                            if (TimeMachineManager.restoreSnapshot(sc_id, file)) {
                                activity.reloadProjectAfterTimeTravel();
                            } else {
                                SketchwareUtil.toastError("Failed to restore snapshot.");
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title, subtitle;
            ImageView icon;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.tv_title);
                subtitle = itemView.findViewById(R.id.tv_subtitle);
                icon = itemView.findViewById(R.id.img_icon);
            }
        }
    }
}
