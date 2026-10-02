package neo.sketchware.ai;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import pro.sketchware.R;

public class AiModelAdapter extends RecyclerView.Adapter<AiModelAdapter.ViewHolder> {
    public interface Listener {
        void onEditClicked(AiModelConfig config);
        void onDeleteClicked(AiModelConfig config);
        void onDuplicateClicked(AiModelConfig config);
        void onItemClicked(AiModelConfig config);
        default void onChatClicked(AiModelConfig config) {}
    }
    private final List<AiModelConfig> items = new ArrayList<>();
    private String activeId;
    private final Listener listener;
    public AiModelAdapter(Listener listener) { this.listener = listener; }
    public void submitList(List<AiModelConfig> newItems, String activeConfigId) {
        items.clear(); if (newItems != null) items.addAll(newItems); activeId = activeConfigId; notifyDataSetChanged();
    }
    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AiModelConfig c = items.get(position);
        AiProvider p = AiProviderRegistry.get(c.providerId);
        h.textModelName.setText(c.displayName);
        h.textModelProvider.setText((p != null ? p.getProviderName() : c.providerId) + " · " + c.modelName);
        h.textActiveBadge.setVisibility(c.id.equals(activeId) ? View.VISIBLE : View.GONE);
        h.textRuntime.setText(p != null && p.isLocal() ? "LOCAL" : "CLOUD");
        h.textRuntime.setVisibility(View.VISIBLE);
        h.imageProviderIcon.setImageResource(getProviderIcon(c.providerId));
        h.buttonEditModel.setOnClickListener(v -> listener.onEditClicked(c));
        h.buttonDeleteModel.setOnClickListener(v -> listener.onDeleteClicked(c));
        h.buttonDuplicateModel.setOnClickListener(v -> listener.onDuplicateClicked(c));
        h.buttonChatModel.setVisibility(c.enableChat ? View.VISIBLE : View.GONE);
        h.buttonChatModel.setOnClickListener(v -> listener.onChatClicked(c));
        h.itemView.setOnClickListener(v -> listener.onItemClicked(c));
    }
    @Override public int getItemCount() { return items.size(); }
    private int getProviderIcon(String id) {
        if (id == null) return R.drawable.ic_mtrl_ai;
        switch (id) {
            case "openai": return R.drawable.ic_mtrl_openai;
            case "gemini": return R.drawable.ic_mtrl_gemini;
            case "grok": return R.drawable.ic_mtrl_customai;
            case "claude": return R.drawable.ic_mtrl_claude;
            case "nvidia": return R.drawable.ic_mtrl_customai;
            case "deepseek": return R.drawable.ic_mtrl_deepseek;
            case "custom": case "local": return R.drawable.ic_mtrl_customai;
            default: return R.drawable.ic_mtrl_ai;
        }
    }
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textModelName, textModelProvider, textActiveBadge, textRuntime;
        ImageView imageProviderIcon; ImageButton buttonEditModel, buttonDeleteModel, buttonDuplicateModel, buttonChatModel;
        ViewHolder(View v) {
            super(v);
            textModelName=v.findViewById(R.id.textModelName); textModelProvider=v.findViewById(R.id.textModelProvider);
            textActiveBadge=v.findViewById(R.id.textActiveBadge); textRuntime=v.findViewById(R.id.textRuntime);
            imageProviderIcon=v.findViewById(R.id.imageProviderIcon); buttonEditModel=v.findViewById(R.id.buttonEditModel);
            buttonDeleteModel=v.findViewById(R.id.buttonDeleteModel); buttonDuplicateModel=v.findViewById(R.id.buttonDuplicateModel);
            buttonChatModel=v.findViewById(R.id.buttonChatModel);
        }
    }
}
