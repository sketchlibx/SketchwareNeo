package pro.sketchware.analysis.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import pro.sketchware.analysis.model.Issue;
import pro.sketchware.analysis.model.Severity;

public class IssueAdapter extends RecyclerView.Adapter<IssueAdapter.ViewHolder> {

    private List<Issue> issues;

    public IssueAdapter(List<Issue> issues) {
        this.issues = issues;
    }

    public void update(List<Issue> newIssues) {
        this.issues = newIssues;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(pro.sketchware.R.layout.item_issue, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Issue issue = issues.get(position);
        holder.message.setText(issue.getMessage());
        holder.category.setText(issue.getCategory() + " · " + issue.getSeverity());
        holder.severityDot.setBackgroundColor(colorFor(issue.getSeverity()));
    }

    @Override
    public int getItemCount() {
        return issues.size();
    }

    private static int colorFor(Severity severity) {
        switch (severity) {
            case CRITICAL: return Color.parseColor("#D32F2F");
            case ERROR: return Color.parseColor("#F57C00");
            case WARNING: return Color.parseColor("#FBC02D");
            default: return Color.parseColor("#757575");
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView message;
        TextView category;
        View severityDot;

        ViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(pro.sketchware.R.id.text_issue_message);
            category = itemView.findViewById(pro.sketchware.R.id.text_issue_category);
            severityDot = itemView.findViewById(pro.sketchware.R.id.severity_dot);
        }
    }
}
