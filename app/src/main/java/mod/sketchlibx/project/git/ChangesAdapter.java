package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Changes tab adapter.
 *
 * Bug fixes vs. the previous version:
 *  - No longer spawns a raw `new Thread()` per tap. Every stage/unstage/discard
 *    action is routed back to GitClientBottomSheet, which funnels ALL Git
 *    operations through a single shared executor. Running JGit commands from
 *    multiple uncoordinated threads against the same Repository is the root
 *    cause behind several of the reported bugs (random freezes, wrong/empty
 *    file lists, index.lock contention).
 *  - GitFile is now immutable and supports a "Renamed" pseudo-status with an
 *    old path, so true renames detected by GitClientBottomSheet are shown as
 *    "old → new" instead of a confusing separate Added + Deleted pair.
 *  - Adds a "Conflict" status (shown as RESOLVE) and a per-row overflow menu
 *    with "View Diff" and "Discard Changes" (feature request #12).
 */
public class ChangesAdapter extends RecyclerView.Adapter<ChangesAdapter.ViewHolder> {

    private final List<GitFile> items = new ArrayList<>();
    private final ChangeActionCallback callback;

    public interface ChangeActionCallback {
        void onStageToggle(GitFile file);
        void onDiscard(GitFile file);
        void onViewDiff(GitFile file);
    }

    public static final class GitFile {
        public final String path;
        public final String filename;
        public final String statusLabel; // Modified | Added | Deleted | Untracked | Renamed | Conflict
        public final boolean isStaged;
        public final int color;
        @Nullable public final String oldPath; // populated only for Renamed entries

        public GitFile(String path, String statusLabel, boolean isStaged, int color) {
            this(path, statusLabel, isStaged, color, null);
        }

        private GitFile(String path, String statusLabel, boolean isStaged, int color, @Nullable String oldPath) {
            this.path = path;
            this.filename = new File(path).getName();
            this.statusLabel = statusLabel;
            this.isStaged = isStaged;
            this.color = color;
            this.oldPath = oldPath;
        }

        public static GitFile renamed(String oldPath, String newPath, boolean isStaged, int color) {
            return new GitFile(newPath, "Renamed", isStaged, color, oldPath);
        }
    }

    public ChangesAdapter(ChangeActionCallback callback) {
        this.callback = callback;
    }

    public void updateData(List<GitFile> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return items.get(o).path.equals(newItems.get(n).path); }
            @Override public boolean areContentsTheSame(int o, int n) {
                GitFile a = items.get(o), b = newItems.get(n);
                return a.statusLabel.equals(b.statusLabel) && a.isStaged == b.isStaged && Objects.equals(a.oldPath, b.oldPath);
            }
        });
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    private static int dp(Context ctx, int v) { return SketchwareUtil.dpToPx(v); }

    private static void applyBorderlessRipple(View v, Context ctx) {
        TypedValue outValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        if (outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 8), dp(ctx, 12));

        TextView tvBadge = new TextView(ctx);
        tvBadge.setTypeface(null, Typeface.BOLD);
        tvBadge.setTextSize(14f);
        tvBadge.setTextColor(Color.WHITE);
        tvBadge.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(ctx, 28), dp(ctx, 28));
        badgeParams.setMarginEnd(dp(ctx, 16));
        root.addView(tvBadge, badgeParams);

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        root.addView(textCol, textLp);

        TextView tvFilename = new TextView(ctx);
        tvFilename.setTextSize(15f);
        tvFilename.setTypeface(null, Typeface.BOLD);
        tvFilename.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurface));
        tvFilename.setSingleLine(true);
        textCol.addView(tvFilename);

        TextView tvPath = new TextView(ctx);
        tvPath.setTextSize(12f);
        tvPath.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        tvPath.setSingleLine(true);
        textCol.addView(tvPath);

        TextView btnAction = new TextView(ctx);
        btnAction.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        btnAction.setTypeface(null, Typeface.BOLD);
        btnAction.setTextSize(12f);
        btnAction.setGravity(Gravity.CENTER);
        btnAction.setAllCaps(true);
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.setMarginEnd(dp(ctx, 4));
        root.addView(btnAction, actionLp);

        TextView more = new TextView(ctx);
        more.setText("\u22EE"); // vertical ellipsis — avoids depending on an unverified drawable resource
        more.setTextSize(18f);
        more.setTypeface(null, Typeface.BOLD);
        more.setGravity(Gravity.CENTER);
        more.setClickable(true);
        more.setFocusable(true);
        applyBorderlessRipple(more, ctx);
        root.addView(more, new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)));

        return new ViewHolder(root, tvBadge, tvFilename, tvPath, btnAction, more);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GitFile file = items.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvFilename.setText(file.filename);
        holder.tvPath.setText(file.oldPath != null ? (file.oldPath + "  \u2192  " + file.path) : file.path);

        holder.tvBadge.setText(file.statusLabel.substring(0, 1));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(file.color);
        gd.setCornerRadius(dp(ctx, 14));
        holder.tvBadge.setBackground(gd);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.TRANSPARENT);
        btnBg.setCornerRadius(dp(ctx, 16));
        btnBg.setStroke(dp(ctx, 1), ThemeUtils.getColor(ctx, R.attr.colorOutlineVariant));
        holder.btnAction.setBackground(btnBg);

        boolean isConflict = "Conflict".equals(file.statusLabel);
        String actionText = isConflict ? "RESOLVE" : (file.isStaged ? "UNSTAGE" : "STAGE");
        int actionColor = isConflict
                ? ThemeUtils.getColor(ctx, R.attr.colorError)
                : ThemeUtils.getColor(ctx, file.isStaged ? R.attr.colorError : R.attr.colorAccent);
        holder.btnAction.setText(actionText);
        holder.btnAction.setTextColor(actionColor);
        holder.btnAction.setOnClickListener(v -> callback.onStageToggle(file));

        holder.more.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        holder.more.setOnClickListener(v -> {
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

    @Override public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvBadge, tvFilename, tvPath, btnAction, more;
        public ViewHolder(View itemView, TextView b, TextView f, TextView p, TextView btn, TextView more) {
            super(itemView);
            tvBadge = b; tvFilename = f; tvPath = p; btnAction = btn; this.more = more;
        }
    }
}
