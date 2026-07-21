package neo.sketchware.plugin;

public record NeoDrawerEntry(int iconResId, String title, String description, Runnable onClick) {
}
