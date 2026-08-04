package neo.sketchware.plugin;

public record NeoEditorAction(String label, Callback callback) {

    public interface Callback {
        String onInvoke(NeoEditorContext context);
    }
}
