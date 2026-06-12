package mod.hey.studios.ide.diagnostics;

public class Diagnostic {
    public enum Severity { ERROR, WARNING, INFO }
    
    public final Severity severity;
    public final String fileName;
    public final int line;
    public final int column;
    public final String message;
    
    public Diagnostic(Severity severity, String fileName, int line, int column, String message) {
        this.severity = severity;
        this.fileName = fileName;
        this.line = line;
        this.column = column;
        this.message = message;
    }
}
