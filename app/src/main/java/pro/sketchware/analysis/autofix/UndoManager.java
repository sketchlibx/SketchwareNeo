package pro.sketchware.analysis.autofix;

import java.util.ArrayDeque;
import java.util.Deque;

public final class UndoManager {

    public interface Snapshot {
        void restore();
    }

    private final Deque<Snapshot> history = new ArrayDeque<>();

    public void push(Snapshot snapshot) {
        history.push(snapshot);
    }

    public boolean canUndo() {
        return !history.isEmpty();
    }

    public void undoLast() {
        if (history.isEmpty()) return;
        history.pop().restore();
    }

    public void clear() {
        history.clear();
    }
}
