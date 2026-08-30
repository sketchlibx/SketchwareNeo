package mod.sketchlibx.project.history;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DiffUtils {
    public enum DiffType { ADDED, REMOVED, UNCHANGED }

    public static class DiffLine {
        public DiffType type;
        public String text;
        public DiffLine(DiffType type, String text) {
            this.type = type;
            this.text = text;
        }
    }

    public static List<DiffLine> getDiff(String oldText, String newText) {
        List<DiffLine> diffs = new ArrayList<>();
        List<String> oldLines = Arrays.asList((oldText == null ? "" : oldText).split("\n"));
        List<String> newLines = Arrays.asList((newText == null ? "" : newText).split("\n"));

        int i = 0, j = 0;
        while (i < oldLines.size() && j < newLines.size()) {
            if (oldLines.get(i).equals(newLines.get(j))) {
                diffs.add(new DiffLine(DiffType.UNCHANGED, oldLines.get(i)));
                i++; j++;
            } else {
                // Lookahead to find sync point (Greedy approach for mobile performance)
                int syncOld = oldLines.subList(i, oldLines.size()).indexOf(newLines.get(j));
                int syncNew = newLines.subList(j, newLines.size()).indexOf(oldLines.get(i));

                if (syncOld != -1 && (syncNew == -1 || syncOld <= syncNew)) {
                    for (int k = 0; k < syncOld; k++) diffs.add(new DiffLine(DiffType.REMOVED, oldLines.get(i++)));
                } else if (syncNew != -1) {
                    for (int k = 0; k < syncNew; k++) diffs.add(new DiffLine(DiffType.ADDED, newLines.get(j++)));
                } else {
                    diffs.add(new DiffLine(DiffType.REMOVED, oldLines.get(i++)));
                    diffs.add(new DiffLine(DiffType.ADDED, newLines.get(j++)));
                }
            }
        }
        while (i < oldLines.size()) diffs.add(new DiffLine(DiffType.REMOVED, oldLines.get(i++)));
        while (j < newLines.size()) diffs.add(new DiffLine(DiffType.ADDED, newLines.get(j++)));

        return diffs;
    }
}
