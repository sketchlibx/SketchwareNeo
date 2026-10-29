package neo.sketchware.ai;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;

import pro.sketchware.R;
import pro.sketchware.utility.ThemeUtils;

/** Shared provider identity so agent cards, chat headers and local-model rows stay consistent. */
public final class AiProviderVisuals {
    private AiProviderVisuals() {}

    public static int iconFor(String providerId) {
        if (providerId == null) return R.drawable.ic_mtrl_ai;
        switch (providerId.toLowerCase(java.util.Locale.ROOT)) {
            case "openai": return R.drawable.ic_mtrl_openai;
            case "gemini": return R.drawable.ic_mtrl_gemini;
            case "claude": return R.drawable.ic_mtrl_claude;
            case "deepseek": return R.drawable.ic_mtrl_deepseek;
            case "nvidia": return R.drawable.ic_mtrl_nvidia;
            case "grok": return R.drawable.ic_mtrl_grok;
            case "local": return R.drawable.ic_mtrl_local;
            case "custom": return R.drawable.ic_mtrl_cloud;
            default: return R.drawable.ic_mtrl_ai;
        }
    }

    public static int accentFor(Context context, String providerId) {
        if (providerId == null) return ThemeUtils.getColor(context, R.attr.colorPrimary);
        switch (providerId.toLowerCase(java.util.Locale.ROOT)) {
            case "openai": return Color.rgb(16, 163, 127);
            case "gemini": return Color.rgb(66, 133, 244);
            case "claude": return Color.rgb(191, 105, 73);
            case "deepseek": return Color.rgb(77, 107, 254);
            case "nvidia": return Color.rgb(118, 185, 0);
            case "grok": return Color.rgb(35, 35, 35);
            case "local": return ThemeUtils.getColor(context, R.attr.colorSecondary);
            case "custom": return ThemeUtils.getColor(context, R.attr.colorTertiary);
            default: return ThemeUtils.getColor(context, R.attr.colorPrimary);
        }
    }

    /** Sets both the real provider drawable and a readable, non-grey identity background. */
    public static void bind(ImageView image, String providerId) {
        if (image == null) return;
        Context context = image.getContext();
        int accent = accentFor(context, providerId);
        image.setImageResource(iconFor(providerId));
        image.setImageTintList(ColorStateList.valueOf(accent));
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(blend(accent, ThemeUtils.getColor(context, R.attr.colorSurface), 0.88f));
        image.setBackground(background);
        image.setContentDescription(labelFor(providerId) + " icon");
    }


    /** Explains setup state instead of labelling incomplete agents as ready. */
    public static String statusLabel(AiModelConfig config) {
        if (config == null) return "Needs setup";
        AiProvider provider = AiProviderRegistry.get(config.providerId);
        if (provider == null) return "Unsupported provider · edit agent";
        if (config.modelName == null || config.modelName.trim().isEmpty()) return "Needs setup · model ID";
        boolean endpointMissing = config.customEndpoint == null || config.customEndpoint.trim().isEmpty();
        if (provider.requiresCustomEndpoint() && endpointMissing &&
                (provider.getDefaultEndpoint() == null || provider.getDefaultEndpoint().trim().isEmpty())) {
            return "Needs setup · endpoint";
        }
        if (provider.isLocal() && endpointMissing) return "Runtime needed · LOCAL";
        if (provider.requiresApiKey() && (config.apiKey == null || config.apiKey.trim().isEmpty())) {
            return "Needs setup · API key";
        }
        return provider.isLocal() ? "Configured · LOCAL" : "Configured · CLOUD";
    }

    public static String labelFor(String providerId) {
        AiProvider provider = AiProviderRegistry.get(providerId);
        return provider != null ? provider.getProviderName() : "AI provider";
    }

    private static int blend(int foreground, int background, float backgroundWeight) {
        float foregroundWeight = 1f - backgroundWeight;
        int r = Math.round(Color.red(foreground) * foregroundWeight + Color.red(background) * backgroundWeight);
        int g = Math.round(Color.green(foreground) * foregroundWeight + Color.green(background) * backgroundWeight);
        int b = Math.round(Color.blue(foreground) * foregroundWeight + Color.blue(background) * backgroundWeight);
        return Color.rgb(r, g, b);
    }
}
