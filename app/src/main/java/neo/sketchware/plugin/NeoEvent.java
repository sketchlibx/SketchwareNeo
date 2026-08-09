package neo.sketchware.plugin;

/**
 * Marker interface for every event published through
 * {@link PluginManager#publishEvent(NeoEvent)} and delivered to plugins via
 * {@link NeoPluginInterface#onEvent(NeoEvent)}.
 *
 * This interface is intentionally left open (not sealed): new event types
 * can be introduced later (block editor events, Add Source Directly / Java
 * editor events, resource manager events, etc.) simply by adding a new
 * record here. Neither this interface, {@link NeoPluginInterface}, nor
 * {@link PluginManager}'s dispatch method ever need to change again to
 * support a new event type — plugins that care about a specific event use
 * `instanceof` / pattern matching inside their {@code onEvent} override.
 *
 * Currently published by PluginManager (Phase 1):
 *  - Project lifecycle: {@link ProjectOpened}, {@link ProjectClosed}, {@link ProjectSaved}, {@link ProjectBuilt}
 *  - Build lifecycle: {@link BuildStarted}, {@link BuildFinished}, {@link CompileError}
 *  - Library: {@link LibraryImported}
 *  - Java editor (Add Source Directly, in {@code mod.hey.studios.code.SrcCodeEditor}): {@link JavaCodeChanged}, {@link JavaCodeSaved}
 *  - Generic editor events, all file types (also in {@code SrcCodeEditor}): {@link FileOpened}, {@link FileClosed}, {@link FileSaved}, {@link TextChanged}, {@link DiagnosticsChanged}
 *  - Block editor (Coding Blocks, in {@code com.besome.sketch.editor.LogicEditorActivity}): {@link BlockAdded}, {@link BlockRemoved}, {@link BlockMoved}, {@link BlockConnected}, {@link BlockDisconnected}
 *
 * Reserved for a later phase, once the resource manager classes have been reviewed:
 *  - Resource events: ResourceAdded, ResourceRemoved
 *  - Activity events: ActivityCreated
 */
public interface NeoEvent {

    /** The project (sc_id) this event relates to. */
    String scId();

    record ProjectOpened(String scId) implements NeoEvent {}

    record ProjectClosed(String scId) implements NeoEvent {}

    record ProjectSaved(String scId) implements NeoEvent {}

    /** Fired once a full build+install cycle for the project completes successfully. */
    record ProjectBuilt(String scId) implements NeoEvent {}

    record BuildStarted(String scId) implements NeoEvent {}

    record BuildFinished(String scId, boolean success) implements NeoEvent {}

    record CompileError(String scId, String rawError, NeoBuildErrorInfo errorInfo) implements NeoEvent {}

    record LibraryImported(String scId, String libraryName) implements NeoEvent {}

    /**
     * Debounced (~800ms after typing pauses) while editing a .java file in
     * Add Source Directly. filePath is the file currently open in the editor.
     */
    record JavaCodeChanged(String scId, String filePath) implements NeoEvent {}

    /** Fired after a .java file is written to disk from Add Source Directly. */
    record JavaCodeSaved(String scId, String filePath) implements NeoEvent {}

    /** Fired when a new block is dropped onto the canvas from the palette. blockId is the new block's BlockBean id. */
    record BlockAdded(String scId, String blockId) implements NeoEvent {}

    /** Fired when a block (and its children) is deleted from the canvas. */
    record BlockRemoved(String scId, String blockId) implements NeoEvent {}

    /** Fired when an existing block is dragged and dropped to a new position/connection. */
    record BlockMoved(String scId, String blockId) implements NeoEvent {}

    /**
     * Fired alongside {@link BlockAdded} or {@link BlockMoved} when the drop
     * landed on a valid connector, i.e. the block joined another block.
     * targetBlockId is the block it connected to.
     */
    record BlockConnected(String scId, String blockId, String targetBlockId) implements NeoEvent {}

    /**
     * Fired when a block is picked up (drag start) and detached from
     * whatever it was previously connected to. Note: fires whenever a
     * type-0 block starts being dragged, even if it turns out it had no
     * parent to detach from (BlockPane no-ops internally in that case).
     */
    record BlockDisconnected(String scId, String blockId) implements NeoEvent {}

    // --- Generic editor events (Phase A) ---
    // Fire for ANY file type (Java, XML, C/C++) since they all go through
    // the same SrcCodeEditor class - one event vocabulary, not one per
    // language. These are ADDITIONAL to (not replacements for) the
    // Java-specific JavaCodeChanged/JavaCodeSaved above, which keep firing
    // exactly as before for .java files so existing plugins don't break.

    /** Fired when a file is opened in the editor. */
    record FileOpened(String scId, String filePath, NeoEditorLanguage language) implements NeoEvent {}

    /** Fired when the editor for a file is closed. */
    record FileClosed(String scId, String filePath, NeoEditorLanguage language) implements NeoEvent {}

    /** Fired after any file (Java, XML, C/C++, ...) is written to disk. */
    record FileSaved(String scId, String filePath, NeoEditorLanguage language) implements NeoEvent {}

    /**
     * Debounced (~800ms after typing pauses), for any file type. For .java
     * files this fires alongside (not instead of) {@link JavaCodeChanged}.
     */
    record TextChanged(String scId, String filePath, NeoEditorLanguage language) implements NeoEvent {}

    /**
     * Live diagnostics for the currently-edited file, refreshed on the same
     * debounce as TextChanged. Currently only published for XML (the only
     * language with a live checker wired up today - SAX parsing); Java and
     * C/C++ live diagnostics need a real parser/checker wired in before this
     * fires for them (see NeoDiagnostic's javadoc). An empty list means the
     * file currently has no diagnostics (plugins should treat this as
     * "errors cleared", not "no update").
     */
    record DiagnosticsChanged(String scId, String filePath, NeoEditorLanguage language, java.util.List<NeoDiagnostic> diagnostics) implements NeoEvent {}

    /**
     * Fired when the cursor moves without an active selection. Verified
     * against io.github.rosemoe.sora.text.Cursor's public API (isSelected,
     * getRightLine, getRightColumn) via the project's pinned sora-editor
     * version (see gradle/libs.versions.toml: sora-editor = "0.23.6") and
     * the library's own subscribeEvent(SelectionChangeEvent.class, ...) API.
     */
    record CursorChanged(String scId, String filePath, int line, int column) implements NeoEvent {}

    /** Fired when the cursor moves WITH an active selection (see CursorChanged). */
    record SelectionChanged(String scId, String filePath, int startLine, int startColumn, int endLine, int endColumn) implements NeoEvent {}

    /** Fired right after the editor's built-in undo() runs (see the Undo toolbar action in SrcCodeEditor). */
    record EditorUndo(String scId, String filePath) implements NeoEvent {}

    /** Fired right after the editor's built-in redo() runs. */
    record EditorRedo(String scId, String filePath) implements NeoEvent {}

    // EditorCopy / EditorCut / EditorPaste are intentionally NOT defined yet.
    // Unlike undo/redo, this project has no existing call site for these
    // (sora-editor 0.23.6 handles copy/cut/paste internally through its own
    // ActionMode/EditorTextActionWindow + Android ClipboardManager, with no
    // public event or callback confirmed in its API for this). Wiring them
    // would mean overriding internal editor UI components rather than
    // observing something that already fires - a bigger, riskier change
    // that needs its own review rather than guessing at it here.
}
