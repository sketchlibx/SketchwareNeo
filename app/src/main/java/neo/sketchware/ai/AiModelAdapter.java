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
        void onItemClicked(AiModelConfig config);
    }

    private final List<AiModelConfig> items = new ArrayList<>();
    private String activeId;
    private final Listener listener;

    public AiModelAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submitList(List<AiModelConfig> newItems, String activeConfigId) {
        items.clear();
        items.addAll(newItems);
        this.activeId = activeConfigId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AiModelConfig config = items.get(position);
        AiProvider provider = AiProviderRegistry.get(config.providerId);

        holder.textModelName.setText(config.displayName);
        holder.textModelProvider.setText(
                (provider != null ? provider.getProviderName() : config.providerId) + " · " + config.modelName
        );
        holder.textActiveBadge.setVisibility(config.id.equals(activeId) ? View.VISIBLE : View.GONE);
        holder.imageProviderIcon.setImageResource(getProviderIcon(config.providerId));

        holder.buttonEditModel.setOnClickListener(v -> listener.onEditClicked(config));
        holder.buttonDeleteModel.setOnClickListener(v -> listener.onDeleteClicked(config));
        holder.itemView.setOnClickListener(v -> listener.onItemClicked(config));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int getProviderIcon(String providerId) {
        if (providerId == null) return R.drawable.ic_mtrl_ai;
        switch (providerId) {
            case "openai":
                return R.drawable.ic_mtrl_openai;
            case "gemini":
                return R.drawable.ic_mtrl_gemini;
            case "claude":
                return R.drawable.ic_mtrl_claude;
            case "nvidia":
                return R.drawable.ic_mtrl_customai;
            case "deepseek":
                return R.drawable.ic_mtrl_deepseek;
            case "custom":
                return R.drawable.ic_mtrl_customai;
            default:
                return R.drawable.ic_mtrl_ai;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textModelName;
        TextView textModelProvider;
        TextView textActiveBadge;
        ImageView imageProviderIcon;
        ImageButton buttonEditModel;
        ImageButton buttonDeleteModel;

        ViewHolder(View itemView) {
            super(itemView);
            textModelName = itemView.findViewById(R.id.textModelName);
            textModelProvider = itemView.findViewById(R.id.textModelProvider);
            textActiveBadge = itemView.findViewById(R.id.textActiveBadge);
            imageProviderIcon = itemView.findViewById(R.id.imageProviderIcon);
            buttonEditModel = itemView.findViewById(R.id.buttonEditModel);
            buttonDeleteModel = itemView.findViewById(R.id.buttonDeleteModel);
        }
    }
}
