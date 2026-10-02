package neo.sketchware.ai;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

import pro.sketchware.R;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.ThemeUtils;

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
    
    public AiModelAdapter(Listener listener) {
        this.listener = listener;
    }
    
    public void submitList(List<AiModelConfig> newItems, String activeConfigId) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        activeId = activeConfigId;
        notifyDataSetChanged();
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ai_model, parent, false));
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        AiModelConfig c = items.get(position);
        AiProvider p = AiProviderRegistry.get(c.providerId);
        
        h.textModelName.setText(c.displayName);
        h.textModelProvider.setText((p != null ? p.getProviderName() : c.providerId) + " • " + c.modelName);
        
        boolean isActive = c.id.equals(activeId);
        h.textActiveBadge.setVisibility(isActive ? View.VISIBLE : View.GONE);
        
        h.cardModel.setStrokeColor(ColorStateList.valueOf(ThemeUtils.getColor(h.itemView.getContext(), isActive ? R.attr.colorPrimary : R.attr.colorOutlineVariant)));
        h.cardModel.setStrokeWidth(isActive ? SketchwareUtil.dpToPx(2) : SketchwareUtil.dpToPx(1));
        
        h.textStatus.setText("Ready • " + (p != null && p.isLocal() ? "LOCAL" : "CLOUD"));
        h.imageProviderIcon.setImageResource(getProviderIcon(c.providerId));
        
        h.buttonMore.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add("Chat").setOnMenuItemClickListener(item -> { 
                listener.onChatClicked(c); 
                return true; 
            });
            if (!isActive) {
                popup.getMenu().add("Set Active").setOnMenuItemClickListener(item -> { 
                    listener.onItemClicked(c); 
                    return true; 
                });
            }
            popup.getMenu().add("Edit").setOnMenuItemClickListener(item -> { 
                listener.onEditClicked(c); 
                return true; 
            });
            popup.getMenu().add("Duplicate").setOnMenuItemClickListener(item -> { 
                listener.onDuplicateClicked(c); 
                return true; 
            });
            popup.getMenu().add("Delete").setOnMenuItemClickListener(item -> { 
                listener.onDeleteClicked(c); 
                return true; 
            });
            popup.show();
        });
        
        h.itemView.setOnClickListener(v -> listener.onItemClicked(c));
    }
    
    @Override
    public int getItemCount() {
        return items.size();
    }
    
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
        MaterialCardView cardModel;
        TextView textModelName, textModelProvider, textActiveBadge, textStatus;
        ImageView imageProviderIcon, iconStatus;
        ImageButton buttonMore;
        
        ViewHolder(View v) {
            super(v);
            cardModel = v.findViewById(R.id.cardModel);
            textModelName = v.findViewById(R.id.textModelName);
            textModelProvider = v.findViewById(R.id.textModelProvider);
            textActiveBadge = v.findViewById(R.id.textActiveBadge);
            textStatus = v.findViewById(R.id.textStatus);
            iconStatus = v.findViewById(R.id.iconStatus);
            imageProviderIcon = v.findViewById(R.id.imageProviderIcon);
            buttonMore = v.findViewById(R.id.buttonMore);
        }
    }
}
