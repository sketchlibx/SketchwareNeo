package neo.sketchware.plugin;

/**
 * Language of a file open in the editor, detected by extension. Deliberately
 * flat (not per-editor-class) because Java, XML, and C/C++ files are all
 * edited through the same {@code mod.hey.studios.code.SrcCodeEditor} class
 * in the current codebase - there's one editor, not three, so the "generic
 * editor interface" this enum supports doesn't need per-editor-type wrapper
 * classes, just this file-type tag.
 */
public enum NeoEditorLanguage {
    JAVA,
    XML,
    CPP,
    C,
    HEADER,
    OTHER;

    public static NeoEditorLanguage fromFilePath(String filePath) {
        if (filePath == null) return OTHER;
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return JAVA;
        if (lower.endsWith(".xml")) return XML;
        if (lower.endsWith(".cpp") || lower.endsWith(".cc") || lower.endsWith(".cxx")) return CPP;
        if (lower.endsWith(".c")) return C;
        if (lower.endsWith(".h") || lower.endsWith(".hpp")) return HEADER;
        return OTHER;
    }
}
