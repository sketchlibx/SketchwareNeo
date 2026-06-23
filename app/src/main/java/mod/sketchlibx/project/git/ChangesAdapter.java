package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.eclipse.jgit.api.Git;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class ChangesAdapter extends RecyclerView.Adapter<ChangesAdapter.ViewHolder> {

    private final List<GitFile> items = new ArrayList<>();
    private final Git git;
    private final ChangeActionCallback callback;

    public interface ChangeActionCallback {
        void onActionStart(String message);
        void onActionSuccess();
        void onActionError(String title, String message);
    }

    public static class GitFile {
        public String path;
        public String filename;
        public String statusLabel;
        public boolean isStaged;
        public int color;

        public GitFile(String path, String statusLabel, boolean isStaged, int color) {
            this.path = path;
            this.filename = new File(path).getName();
            this.statusLabel = statusLabel;
            this.isStaged = isStaged;
            this.color = color;
        }
    }

    public ChangesAdapter(Git git, ChangeActionCallback callback) {
        this.git = git;
        this.callback = callback;
    }

    public void updateData(List<GitFile> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return items.get(o).path.equals(newItems.get(n).path); }
            @Override public boolean areContentsTheSame(int o, int n) { return items.get(o).statusLabel.equals(newItems.get(n).statusLabel) && items.get(o).isStaged == newItems.get(n).isStaged; }
        });
        items.clear(); 
        items.addAll(newItems); 
        diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        
        LinearLayout root = new LinearLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(12));

        TextView tvBadge = new TextView(ctx);
        tvBadge.setId(View.generateViewId());
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setTextSize(14f);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(28), SketchwareUtil.dpToPx(28));
        badgeParams.setMarginEnd(SketchwareUtil.dpToPx(16));
        root.addView(tvBadge, badgeParams);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_mtrl_file);
        icon.setColorFilter(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        root.addView(icon, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), SketchwareUtil.dpToPx(24)));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        textLp.setMarginStart(SketchwareUtil.dpToPx(16));
        root.addView(textCol, textLp);

        TextView tvFilename = new TextView(ctx);
        tvFilename.setId(View.generateViewId());
        tvFilename.setTextSize(15f);
        tvFilename.setTypeface(null, Typeface.BOLD);
        tvFilename.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurface));
        tvFilename.setSingleLine(true);
        textCol.addView(tvFilename);

        TextView tvPath = new TextView(ctx);
        tvPath.setId(View.generateViewId());
        tvPath.setTextSize(12f);
        tvPath.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        tvPath.setSingleLine(true);
        textCol.addView(tvPath);

        TextView btnAction = new TextView(ctx);
        btnAction.setPadding(SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(6), SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(6));
        btnAction.setTypeface(null, Typeface.BOLD);
        btnAction.setTextSize(12f);
        btnAction.setGravity(Gravity.CENTER);
        btnAction.setAllCaps(true);
        root.addView(btnAction);

        return new ViewHolder(root, tvBadge, tvFilename, tvPath, btnAction);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GitFile file = items.get(position);
        holder.tvFilename.setText(file.filename);
        holder.tvPath.setText(file.path);
        
        holder.tvBadge.setText(file.statusLabel.substring(0, 1));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(file.color);
        gd.setCornerRadius(SketchwareUtil.dpToPx(14));
        holder.tvBadge.setBackground(gd);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.TRANSPARENT);
        btnBg.setCornerRadius(SketchwareUtil.dpToPx(16));
        btnBg.setStroke(SketchwareUtil.dpToPx(1), ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOutlineVariant));
        holder.btnAction.setBackground(btnBg);

        holder.btnAction.setText(file.isStaged ? "UNSTAGE" : "STAGE");
        holder.btnAction.setTextColor(ThemeUtils.getColor(holder.itemView.getContext(), file.isStaged ? R.attr.colorError : R.attr.colorAccent));
        
        holder.btnAction.setOnClickListener(v -> {
            if (git == null) return;
            callback.onActionStart(file.isStaged ? "Unstaging file..." : "Staging file...");
            new Thread(() -> {
                try {
                    if (file.isStaged) git.reset().addPath(file.path).call();
                    else if (file.statusLabel.equals("Deleted")) git.rm().addFilepattern(file.path).call();
                    else git.add().addFilepattern(file.path).call();
                    callback.onActionSuccess();
                } catch (Exception e) { callback.onActionError("Git Action Failed", e.getMessage()); }
            }).start();
        });
    }

    @Override public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge, tvFilename, tvPath; 
        TextView btnAction;
        public ViewHolder(View itemView, TextView b, TextView f, TextView p, TextView btn) {
            super(itemView); tvBadge = b; tvFilename = f; tvPath = p; btnAction = btn;
        }
    }
}
