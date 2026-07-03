package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;

import org.eclipse.jgit.lib.Ref;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Branches tab adapter.
 *
 * Extracted out of GitClientBottomSheet into its own file (previous version
 * mixed it in as an inner class, which made it harder to reason about and to
 * reuse). Now:
 *  - Always groups results into "Local Branches" / "Remote Branches" section
 *    headers (final-goal requirement: accurate branch management, similar to
 *    Android Studio's Git panel).
 *  - Exposes Checkout / Rename / Delete via a per-row overflow menu.
 *  - Disables Checkout and Delete for the currently checked-out branch
 *    (bug #6 requirement: "Prevent deleting active branch").
 *  - Disables Rename/Delete for remote-tracking refs (only Checkout makes
 *    sense locally for those).
 */
public class GitBranchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface BranchActionCallback {
        void onCheckout(Ref ref);
        void onRename(Ref ref);
        void onDelete(Ref ref);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_BRANCH = 1;

    private abstract static class Row {}
    private static final class HeaderRow extends Row {
        final String title;
        HeaderRow(String t) { title = t; }
    }
    private static final class BranchRow extends Row {
        final Ref ref;
        BranchRow(Ref r) { ref = r; }
    }

    private final List<Ref> allRefs = new ArrayList<>();
    private final List<Row> displayRows = new ArrayList<>();
    private String currentBranch = "";
    private String query = "";
    private final BranchActionCallback callback;

    public GitBranchAdapter(BranchActionCallback callback) {
        this.callback = callback;
    }

    public void updateData(List<Ref> refs, String current) {
        allRefs.clear();
        allRefs.addAll(refs);
        currentBranch = current == null ? "" : current;
        rebuildDisplayRows();
        notifyDataSetChanged();
    }

    public void filter(String q) {
        query = q == null ? "" : q.toLowerCase(Locale.getDefault());
        rebuildDisplayRows();
        notifyDataSetChanged();
    }

    private void rebuildDisplayRows() {
        displayRows.clear();
        List<Ref> local = new ArrayList<>();
        List<Ref> remote = new ArrayList<>();
        for (Ref r : allRefs) {
            String shortName = shortNameOf(r);
            if (!query.isEmpty() && !shortName.toLowerCase(Locale.getDefault()).contains(query)) continue;
            if (r.getName().startsWith("refs/remotes/")) remote.add(r); else local.add(r);
        }
        if (!local.isEmpty()) {
            displayRows.add(new HeaderRow("Local Branches"));
            for (Ref r : local) displayRows.add(new BranchRow(r));
        }
        if (!remote.isEmpty()) {
            displayRows.add(new HeaderRow("Remote Branches"));
            for (Ref r : remote) displayRows.add(new BranchRow(r));
        }
    }

    private static String shortNameOf(Ref r) {
        return r.getName().replace("refs/heads/", "").replace("refs/remotes/", "");
    }

    private boolean isCurrent(Ref r) {
        String shortName = shortNameOf(r);
        return currentBranch.equals(r.getName()) || currentBranch.equals(shortName);
    }

    private static int dp(Context ctx, int v) { return SketchwareUtil.dpToPx(v); }

    private static void applyBorderlessRipple(View v, Context ctx) {
        TypedValue outValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        if (outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId);
    }

    @Override
    public int getItemViewType(int position) {
        return displayRows.get(position) instanceof HeaderRow ? TYPE_HEADER : TYPE_BRANCH;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();

        if (viewType == TYPE_HEADER) {
            TextView header = new TextView(ctx);
            header.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            header.setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 4));
            header.setTextSize(12f);
            header.setTypeface(null, Typeface.BOLD);
            header.setAllCaps(true);
            header.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorPrimary));
            return new HeaderVH(header);
        }

        LinearLayout root = new LinearLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 4), dp(ctx, 12));

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_mtrl_share);
        root.addView(icon, new LinearLayout.LayoutParams(dp(ctx, 24), dp(ctx, 24)));

        TextView title = new TextView(ctx);
        title.setTextSize(16f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMarginStart(dp(ctx, 16));
        root.addView(title, lp);

        TextView more = new TextView(ctx);
        more.setText("\u22EE");
        more.setTextSize(18f);
        more.setTypeface(null, Typeface.BOLD);
        more.setGravity(Gravity.CENTER);
        more.setClickable(true);
        more.setFocusable(true);
        applyBorderlessRipple(more, ctx);
        root.addView(more, new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)));

        return new BranchVH(root, title, icon, more);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = displayRows.get(position);
        if (row instanceof HeaderRow) {
            ((HeaderVH) holder).title.setText(((HeaderRow) row).title);
            return;
        }

        Ref ref = ((BranchRow) row).ref;
        BranchVH vh = (BranchVH) holder;
        Context ctx = vh.itemView.getContext();
        boolean isRemote = ref.getName().startsWith("refs/remotes/");
        boolean current = isCurrent(ref);

        vh.title.setText(shortNameOf(ref) + (current ? "  (current)" : ""));
        int accent = ThemeUtils.getColor(ctx, R.attr.colorAccent);
        int normal = ThemeUtils.getColor(ctx, R.attr.colorOnSurface);
        int normalIcon = ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant);
        vh.title.setTypeface(null, current ? Typeface.BOLD : Typeface.NORMAL);
        vh.title.setTextColor(current ? accent : normal);
        vh.icon.setColorFilter(current ? accent : normalIcon);
        vh.more.setTextColor(normalIcon);

        vh.more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 1, 0, "Checkout").setEnabled(!current);
            if (!isRemote) {
                popup.getMenu().add(0, 2, 1, "Rename");
                popup.getMenu().add(0, 3, 2, "Delete").setEnabled(!current);
            }
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) callback.onCheckout(ref);
                else if (id == 2) callback.onRename(ref);
                else if (id == 3) callback.onDelete(ref);
                return true;
            });
            popup.show();
        });
    }

    @Override public int getItemCount() { return displayRows.size(); }

    static final class HeaderVH extends RecyclerView.ViewHolder {
        final TextView title;
        HeaderVH(View v) { super(v); title = (TextView) v; }
    }

    static final class BranchVH extends RecyclerView.ViewHolder {
        final TextView title, more;
        final ImageView icon;
        BranchVH(View v, TextView t, ImageView i, TextView m) { super(v); title = t; icon = i; more = m; }
    }
}
