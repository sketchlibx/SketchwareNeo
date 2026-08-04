package neo.sketchware.plugin;

import android.content.Context;

public record NeoDrawerEntry(int iconResId, String title, String description, Action onClick) {

    public interface Action {
        void onClick(Context activityContext);
    }
}
