package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

public class ChangesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_FILE = 1;

    private final List<Object> displayItems = new ArrayList<>();
    private final Set<String> selectedPaths = new HashSet<>();
    private final ChangeActionCallback callback;

    public interface ChangeActionCallback {
        void onStageToggle(GitFile file);
        void onDiscard(GitFile file);
        void onViewDiff(GitFile file);
        void onSelectionChanged(Set<String> selectedPaths);
    }

    public static final class GitFile {
        public final String path;
        public final String filename;
        public final String statusLabel; // Modified | Added | Deleted | Untracked | Renamed | Conflict
        public final boolean isStaged;
        @Nullable public final String oldPath;

        public GitFile(String path, String statusLabel, boolean isStaged) {
            this(path, statusLabel, isStaged, null);
        }

        private GitFile(String path, String statusLabel, boolean isStaged, @Nullable String oldPath) {
            this.path = path;
            this.filename = new File(path).getName();
            this.statusLabel = statusLabel;
            this.isStaged = isStaged;
            this.oldPath = oldPath;
        }

        public static GitFile renamed(String oldPath, String newPath, boolean isStaged) {
            return new GitFile(newPath, "Renamed", isStaged, oldPath);
        }
    }

    public static class HeaderItem {
        public final String title;
        public HeaderItem(String title) { this.title = title; }
    }

    public ChangesAdapter(ChangeActionCallback callback) {
        this.callback = callback;
    }

    public void updateData(List<GitFile> newFiles) {
        List<Object> newItems = new ArrayList<>();
        
        List<GitFile> staged = new ArrayList<>();
        List<GitFile> unstaged = new ArrayList<>();
        List<GitFile> untracked = new ArrayList<>();
        List<GitFile> conflicts = new ArrayList<>();

        for (GitFile f : newFiles) {
            if ("Conflict".equals(f.statusLabel)) conflicts.add(f);
            else if ("Untracked".equals(f.statusLabel)) untracked.add(f);
            else if (f.isStaged) staged.add(f);
            else unstaged.add(f);
        }

        if (!conflicts.isEmpty()) {
            newItems.add(new HeaderItem("Conflicts"));
            newItems.addAll(conflicts);
        }
        if (!staged.isEmpty()) {
            newItems.add(new HeaderItem("Staged Changes"));
            newItems.addAll(staged);
        }
        if (!unstaged.isEmpty()) {
            newItems.add(new HeaderItem("Unstaged Changes"));
            newItems.addAll(unstaged);
        }
        if (!untracked.isEmpty()) {
            newItems.add(new HeaderItem("Untracked Files"));
            newItems.addAll(untracked);
        }

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return displayItems.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) {
                Object oldItem = displayItems.get(o);
                Object newItem = newItems.get(n);
                if (oldItem.getClass() != newItem.getClass()) return false;
                if (oldItem instanceof HeaderItem) return ((HeaderItem) oldItem).title.equals(((HeaderItem) newItem).title);
                return ((GitFile) oldItem).path.equals(((GitFile) newItem).path);
            }
            @Override public boolean areContentsTheSame(int o, int n) {
                if (displayItems.get(o) instanceof HeaderItem) return true;
                GitFile a = (GitFile) displayItems.get(o), b = (GitFile) newItems.get(n);
                return a.statusLabel.equals(b.statusLabel) && a.isStaged == b.isStaged && Objects.equals(a.oldPath, b.oldPath);
            }
        });

        displayItems.clear();
        displayItems.addAll(newItems);
        
        // Remove selection for files that no longer exist
        selectedPaths.removeIf(path -> newFiles.stream().noneMatch(f -> f.path.equals(path)));
        callback.onSelectionChanged(selectedPaths);
        
        diff.dispatchUpdatesTo(this);
    }

    public void selectAll(boolean select) {
        if (select) {
            for (Object o : displayItems) {
                if (o instanceof GitFile) selectedPaths.add(((GitFile) o).path);
            }
        } else {
            selectedPaths.clear();
        }
        notifyDataSetChanged();
        callback.onSelectionChanged(selectedPaths);
    }

    public List<GitFile> getSelectedFiles() {
        List<GitFile> result = new ArrayList<>();
        for (Object o : displayItems) {
            if (o instanceof GitFile && selectedPaths.contains(((GitFile) o).path)) {
                result.add((GitFile) o);
            }
        }
        return result;
    }

    @Override
    public int getItemViewType(int position) {
        return displayItems.get(position) instanceof HeaderItem ? TYPE_HEADER : TYPE_FILE;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            TextView tv = new TextView(parent.getContext());
            tv.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            tv.setPadding(32, 24, 32, 8);
            tv.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
            tv.setTextColor(ThemeUtils.getColor(parent.getContext(), R.attr.colorPrimary));
            return new RecyclerView.ViewHolder(tv) {};
        }
        return new FileVH(inflater.inflate(R.layout.item_git_change, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder.getItemViewType() == TYPE_HEADER) {
            ((TextView) holder.itemView).setText(((HeaderItem) displayItems.get(position)).title);
            return;
        }

        GitFile file = (GitFile) displayItems.get(position);
        FileVH vh = (FileVH) holder;
        Context ctx = vh.itemView.getContext();

        vh.tvFilename.setText(file.filename);
        vh.tvFilepath.setText(file.oldPath != null ? (file.oldPath + "  \u2192  " + file.path) : file.path);

        // Icon Logic
        int iconRes = R.drawable.ic_mtrl_file;
        int bgColor = Color.parseColor("#455A64"); // default grey-blue
        if (file.filename.endsWith(".java")) { iconRes = R.drawable.ic_mtrl_java; bgColor = Color.parseColor("#E65100"); }
        else if (file.filename.endsWith(".xml")) { iconRes = R.drawable.ic_mtrl_code; bgColor = Color.parseColor("#2E7D32"); }
        else if (file.filename.endsWith(".gradle")) { iconRes = R.drawable.ic_mtrl_settings_applications; bgColor = Color.parseColor("#0277BD"); }
        else if (file.filename.endsWith(".png") || file.filename.endsWith(".jpg")) { iconRes = R.drawable.ic_mtrl_image; bgColor = Color.parseColor("#6A1B9A"); }
        
        vh.imgIcon.setImageResource(iconRes);
        vh.cardIcon.setCardBackgroundColor(bgColor);

        // Status Button
        boolean isConflict = "Conflict".equals(file.statusLabel);
        vh.btnStatus.setText(isConflict ? "Resolve" : (file.isStaged ? "Staged" : "Unstaged"));
        
        if (isConflict) {
            vh.btnStatus.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorError));
            vh.btnStatus.setStrokeColorResource(R.color.color_error);
        } else if (file.isStaged) {
            vh.btnStatus.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorPrimary));
            vh.btnStatus.setStrokeColorResource(R.color.color_primary);
        } else {
            vh.btnStatus.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorError));
            vh.btnStatus.setStrokeColorResource(R.color.color_error);
        }

        vh.btnStatus.setOnClickListener(v -> callback.onStageToggle(file));

        // Selection
        vh.checkbox.setOnCheckedChangeListener(null);
        vh.checkbox.setChecked(selectedPaths.contains(file.path));
        vh.checkbox.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) selectedPaths.add(file.path); else selectedPaths.remove(file.path);
            callback.onSelectionChanged(selectedPaths);
        });
        vh.itemView.setOnClickListener(v -> vh.checkbox.setChecked(!vh.checkbox.isChecked()));

        // More Menu
        vh.btnMore.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 1, 0, "View Diff");
            popup.getMenu().add(0, 2, 1, "Discard Changes");
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) callback.onViewDiff(file);
                else if (id == 2) callback.onDiscard(file);
                return true;
            });
            popup.show();
        });
    }

    @Override public int getItemCount() { return displayItems.size(); }

    static class FileVH extends RecyclerView.ViewHolder {
        CheckBox checkbox; MaterialCardView cardIcon; ImageView imgIcon;
        TextView tvFilename, tvFilepath; MaterialButton btnStatus; ImageButton btnMore;
        FileVH(View v) {
            super(v);
            checkbox = v.findViewById(R.id.checkbox_file);
            cardIcon = v.findViewById(R.id.card_file_icon);
            imgIcon = v.findViewById(R.id.img_file_icon);
            tvFilename = v.findViewById(R.id.tv_filename);
            tvFilepath = v.findViewById(R.id.tv_filepath);
            btnStatus = v.findViewById(R.id.btn_stage_status);
            btnMore = v.findViewById(R.id.btn_more);
        }
    }
}
