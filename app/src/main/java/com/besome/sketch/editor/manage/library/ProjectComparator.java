package com.besome.sketch.editor.manage.library;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import a.a.a.yB;

public class ProjectComparator implements Comparator<HashMap<String, Object>> {

    public static final int SORT_BY_NAME = 1;
    public static final int SORT_BY_ID = 2;
    public static final int SORT_ORDER_ASCENDING = 4;
    public static final int SORT_ORDER_DESCENDING = 8;
    public static final int DEFAULT = SORT_BY_ID | SORT_ORDER_DESCENDING;

    private int sortBy = 0;
    private Set<String> pinnedScids = Collections.emptySet();

    public ProjectComparator() {
    }

    public ProjectComparator(int sortBy, String pinnedScids) {
        this.sortBy = sortBy;
        this.pinnedScids = parsePinnedScids(pinnedScids);
    }

    private Set<String> parsePinnedScids(String value) {
        Set<String> result = new LinkedHashSet<>();
        if (value == null || value.trim().isEmpty() || "-1".equals(value.trim())) {
            return result;
        }
        for (String item : value.split(",")) {
            String scId = item.trim();
            if (!scId.isEmpty() && !"-1".equals(scId)) {
                result.add(scId);
            }
        }
        return result;
    }

    @Override
    public int compare(HashMap<String, Object> first, HashMap<String, Object> second) {
        boolean isSortOrderAscending = (sortBy & SORT_ORDER_ASCENDING) == SORT_ORDER_ASCENDING;
        boolean firstPinned = pinnedScids.contains(yB.c(first, "sc_id"));
        boolean secondPinned = pinnedScids.contains(yB.c(second, "sc_id"));

        if (firstPinned != secondPinned) {
            return firstPinned ? -1 : 1;
        }

        if ((sortBy & SORT_BY_ID) == SORT_BY_ID) {
            return compareIds(yB.c(first, "sc_id"), yB.c(second, "sc_id")) * (isSortOrderAscending ? 1 : -1);
        } else if ((sortBy & SORT_BY_NAME) == SORT_BY_NAME) {
            return yB.c(first, "my_ws_name").compareTo(yB.c(second, "my_ws_name")) * (isSortOrderAscending ? 1 : -1);
        } else {
            return compareIds(yB.c(first, "sc_id"), yB.c(second, "sc_id")) * -1;
        }
    }

    private int compareIds(String first, String second) {
        try {
            return Integer.compare(Integer.parseInt(first), Integer.parseInt(second));
        } catch (NumberFormatException ignored) {
            return first.compareTo(second);
        }
    }
}
