package neo.sketchware.plugin;

public final class NeoEditorContext {

    private final String filePath;
    private final String fullText;
    private final int selectionStart;
    private final int selectionEnd;

    public NeoEditorContext(String filePath, String fullText, int selectionStart, int selectionEnd) {
        this.filePath = filePath;
        this.fullText = fullText;
        this.selectionStart = Math.max(0, Math.min(selectionStart, fullText.length()));
        this.selectionEnd = Math.max(this.selectionStart, Math.min(selectionEnd, fullText.length()));
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFullText() {
        return fullText;
    }

    public String getSelectedText() {
        return fullText.substring(selectionStart, selectionEnd);
    }

    public int getSelectionStart() {
        return selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }
}
