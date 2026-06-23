package mod.sketchlibx.project.git;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import org.eclipse.jgit.revwalk.RevCommit;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {

    private final List<RevCommit> items = new ArrayList<>();
    private final SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault());

    public void updateData(List<RevCommit> newItems) {
        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return items.size(); }
            @Override public int getNewListSize() { return newItems.size(); }
            @Override public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) { return items.get(oldItemPosition).getName().equals(newItems.get(newItemPosition).getName()); }
            @Override public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) { return true; }
        });
        items.clear(); items.addAll(newItems); diff.dispatchUpdatesTo(this);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        Context ctx = parent.getContext();
        
        LinearLayout root = new LinearLayout(ctx);
        root.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setPadding(SketchwareUtil.dpToPx(16), 0, SketchwareUtil.dpToPx(16), 0);

        LinearLayout timelineCol = new LinearLayout(ctx);
        timelineCol.setOrientation(LinearLayout.VERTICAL);
        timelineCol.setGravity(Gravity.CENTER_HORIZONTAL);
        
        View topLine = new View(ctx);
        topLine.setId(View.generateViewId());
        topLine.setBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorOutlineVariant));
        timelineCol.addView(topLine, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(2), SketchwareUtil.dpToPx(24)));
        
        MaterialCardView dot = new MaterialCardView(ctx);
        dot.setRadius(SketchwareUtil.dpToPx(6));
        dot.setCardBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorPrimary));
        dot.setStrokeWidth(0);
        dot.setCardElevation(0f);
        timelineCol.addView(dot, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(12)));
        
        View bottomLine = new View(ctx);
        bottomLine.setId(View.generateViewId());
        bottomLine.setBackgroundColor(ThemeUtils.getColor(ctx, R.attr.colorOutlineVariant));
        timelineCol.addView(bottomLine, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(2), ViewGroup.LayoutParams.MATCH_PARENT));
        
        root.addView(timelineCol, new LinearLayout.LayoutParams(SketchwareUtil.dpToPx(24), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(ctx);
        LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.setMargins(SketchwareUtil.dpToPx(12), SketchwareUtil.dpToPx(8), 0, SketchwareUtil.dpToPx(8));
        content.setLayoutParams(contentLp);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16), SketchwareUtil.dpToPx(16));

        TextView tvMessage = new TextView(ctx);
        tvMessage.setId(View.generateViewId());
        tvMessage.setTextSize(16f);
        tvMessage.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurface));
        tvMessage.setTypeface(null, Typeface.BOLD);
        content.addView(tvMessage);

        TextView tvDetails = new TextView(ctx);
        tvDetails.setId(View.generateViewId());
        tvDetails.setTextSize(13f);
        tvDetails.setTextColor(ThemeUtils.getColor(ctx, R.attr.colorOnSurfaceVariant));
        tvDetails.setPadding(0, SketchwareUtil.dpToPx(6), 0, 0);
        content.addView(tvDetails);

        root.addView(content);
        return new ViewHolder(root, content, tvMessage, tvDetails, topLine, bottomLine);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RevCommit commit = items.get(position);
        
        holder.tvMessage.setText(commit.getShortMessage());
        
        String hash = commit.getName().substring(0, 7);
        String author = commit.getAuthorIdent().getName();
        String date = sdf.format(new Date(commit.getCommitTime() * 1000L));
        
        holder.tvDetails.setText(String.format("%s • %s • %s", hash, author, date));
        
        holder.topLine.setVisibility(position == 0 ? View.INVISIBLE : View.VISIBLE);
        holder.bottomLine.setVisibility(position == items.size() - 1 ? View.INVISIBLE : View.VISIBLE);

        holder.card.setOnLongClickListener(v -> {
            Context ctx = v.getContext();
            ClipboardManager clipboard = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                ClipData clip = ClipData.newPlainText("Commit Hash", commit.getName());
                clipboard.setPrimaryClip(clip);
                SketchwareUtil.toast("Commit hash copied to clipboard!");
            }
            return true;
        });
    }

    @Override public int getItemCount() { return items.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMessage, tvDetails; View topLine, bottomLine; View card;
        public ViewHolder(View i, View c, TextView m, TextView d, View t, View b) {
            super(i); card = c; tvMessage = m; tvDetails = d; topLine = t; bottomLine = b;
        }
    }
}
