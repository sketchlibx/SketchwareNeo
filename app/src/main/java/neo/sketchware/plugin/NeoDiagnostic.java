package neo.sketchware.plugin;

/**
 * A single diagnostic (error/warning/info) for a position in a file,
 * carried by {@link NeoEvent.DiagnosticsChanged}. This is intentionally a
 * separate type from the host app's internal
 * {@code mod.hey.studios.ide.diagnostics.Diagnostic} class (used by the
 * in-editor diagnostics bottom sheet) rather than reusing it directly -
 * the plugin API should stay stable even if that internal class is
 * refactored later. Producers convert their internal diagnostic objects
 * into this shape at the point they publish the event.
 *
 * Works the same way for Java, XML, and C/C++ (and any future language) -
 * that's the point of keeping it generic instead of per-language.
 */
public record NeoDiagnostic(
        Severity severity,
        String filePath,
        int line,
        int column,
        String message,
        String source,
        boolean quickFixAvailable
) {

    public enum Severity {
        INFO,
        WARNING,
        ERROR
    }

    /** Convenience constructor for the common case of no quick fix available. */
    public NeoDiagnostic(Severity severity, String filePath, int line, int column, String message, String source) {
        this(severity, filePath, line, column, message, source, false);
    }
}
