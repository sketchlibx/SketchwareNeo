package pro.sketchware.compiler;

import java.io.PrintWriter;

import org.eclipse.jdt.internal.compiler.batch.Main;

public final class EclipseCompilerBridge {
    private EclipseCompilerBridge() {
    }

    public static CompilationResult compile(String[] arguments, PrintWriter output, PrintWriter errors) {
        Main compiler = new Main(output, errors, false, null, null);
        boolean succeeded = compiler.compile(arguments);
        return new CompilationResult(succeeded, compiler.globalErrorsCount);
    }

    public static final class CompilationResult {
        public final boolean succeeded;
        public final int errorCount;

        private CompilationResult(boolean succeeded, int errorCount) {
            this.succeeded = succeeded;
            this.errorCount = errorCount;
        }
    }
}
