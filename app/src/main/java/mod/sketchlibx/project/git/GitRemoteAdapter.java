package mod.sketchlibx.project.git;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import org.eclipse.jgit.transport.RemoteConfig;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

/**
 * Remotes tab adapter.
 *
 * Bug fix for #5: always shows whatever RemoteConfig.getAllRemoteConfigs()
 * returns (caller is responsible for force-reloading the StoredConfig before
 * building this list, see GitClientBottomSheet#computeRemoteList).
 * Displays Fetch URL and Push URL separately (only shows a Push URL row when
 * one is actually configured differently from the fetch URL), and exposes
 * Edit / Remove / Test Connection via an overflow menu (feature request #12).
 */
public class GitRemoteAdapter extends RecyclerView.Adapter<GitRemoteAdapter.VH> {

    public interface RemoteActionCallback {
        void onEdit(RemoteConfig remote);
        void onRemove(RemoteConfig remote);
        void onTestConnection(RemoteConfig remote);
    }

    private final List<RemoteConfig> items = new ArrayList<>();
    private final RemoteActionCallback callback;

    public GitRemoteAdapter(RemoteActionCallback callback) {
        this.callback = callback;
    }

    public void updateData(List<RemoteConfig> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int o, int n) { return items.get(o).getName().equals(newItems.get(n).getName()); }
            @Override public boolean areContentsTheSame(int o, int n) {
                return describeUrls(items.get(o)).equals(describeUrls(newItems.get(n)));
            }
        });
        items.clear();
        items.addAll(newItems);
        diff.dispatchUpdatesTo(this);
    }

    private static String describeUrls(RemoteConfig rc) {
        String fetch = rc.getURIs().isEmpty() ? "" : rc.getURIs().get(0).toString();
        String push = rc.getPushURIs().isEmpty() ? "" : rc.getPushURIs().get(0).toString();
        return fetch + "|" + push;
    }

    private static int dp(Context ctx, int v) { return SketchwareUtil.dpToPx(v); }

    private static void applyBorderlessRipple(View v, Context ctx) {
        TypedValue outValue = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
        if (outValue.resourceId != 0) v.setBackgroundResource(outValue.resourceId);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();

        LinearLayout root = new LinearLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(ctx, 16), dp(ctx, 12), dp(ctx, 4), dp(ctx, 12));

        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        root.addView(textCol, textLp);

        TextView title = new TextView(ctx);
        title.setTextSize(16f);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurface));
        textCol.addView(title);

        TextView fetchUrl = new TextView(ctx);
        fetchUrl.setTextSize(13f);
        fetchUrl.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        fetchUrl.setPadding(0, dp(ctx, 4), 0, 0);
        fetchUrl.setSingleLine(true);
        fetchUrl.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(fetchUrl);

        TextView pushUrl = new TextView(ctx);
        pushUrl.setTextSize(13f);
        pushUrl.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        pushUrl.setSingleLine(true);
        pushUrl.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(pushUrl);

        TextView more = new TextView(ctx);
        more.setText("\u22EE");
        more.setTextSize(18f);
        more.setTypeface(null, Typeface.BOLD);
        more.setGravity(Gravity.CENTER);
        more.setClickable(true);
        more.setFocusable(true);
        applyBorderlessRipple(more, ctx);
        root.addView(more, new LinearLayout.LayoutParams(dp(ctx, 36), dp(ctx, 36)));

        return new VH(root, title, fetchUrl, pushUrl, more);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        RemoteConfig rc = items.get(position);
        holder.title.setText(rc.getName());
        holder.fetchUrl.setText(rc.getURIs().isEmpty() ? "Fetch: (no URL)" : "Fetch: " + rc.getURIs().get(0));

        boolean hasSeparatePush = !rc.getPushURIs().isEmpty();
        holder.pushUrl.setVisibility(hasSeparatePush ? View.VISIBLE : View.GONE);
        if (hasSeparatePush) holder.pushUrl.setText("Push: " + rc.getPushURIs().get(0));

        holder.more.setTextColor(ThemeUtils.getColor(holder.itemView.getContext(), R.attr.colorOnSurfaceVariant));
        holder.more.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add(0, 1, 0, "Edit");
            popup.getMenu().add(0, 2, 1, "Test Connection");
            popup.getMenu().add(0, 3, 2, "Remove");
            popup.setOnMenuItemClickListener(item -> {
                int id = item.getItemId();
                if (id == 1) callback.onEdit(rc);
                else if (id == 2) callback.onTestConnection(rc);
                else if (id == 3) callback.onRemove(rc);
                return true;
            });
            popup.show();
        });
    }

    @Override public int getItemCount() { return items.size(); }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title, fetchUrl, pushUrl, more;
        VH(View v, TextView t, TextView f, TextView p, TextView m) { super(v); title = t; fetchUrl = f; pushUrl = p; more = m; }
    }
}
