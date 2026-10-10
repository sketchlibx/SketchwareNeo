package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class MavenVersions {

    private static final Pattern PRE_RELEASE_TOKEN = Pattern.compile(
            "(?i)^(alpha|beta|a|b|rc|cr|m|snapshot|dev|preview|pre|eap|canary|nightly|milestone)\\d*$");

    private MavenVersions() {
    }

    public static boolean isPreRelease(@NonNull String version) {
        for (String token : tokens(version)) {
            if (PRE_RELEASE_TOKEN.matcher(token).matches()) return true;
        }
        return false;
    }

    public static boolean isSnapshot(@NonNull String version) {
        return version.toUpperCase().endsWith("-SNAPSHOT");
    }

    public static int compare(@NonNull String a, @NonNull String b) {
        List<String> left = tokens(a);
        List<String> right = tokens(b);
        int size = Math.max(left.size(), right.size());
        for (int i = 0; i < size; i++) {
            String x = i < left.size() ? left.get(i) : null;
            String y = i < right.size() ? right.get(i) : null;
            int cmp = compareToken(x, y);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    @NonNull
    public static List<String> sortNewestFirst(@NonNull List<String> versions) {
        List<String> sorted = new ArrayList<>(versions);
        Collections.sort(sorted, (x, y) -> compare(y, x));
        return sorted;
    }

    @Nullable
    public static String latestStable(@NonNull List<String> versionsNewestFirst) {
        for (String version : versionsNewestFirst) {
            if (!isPreRelease(version) && !isSnapshot(version)) return version;
        }
        return null;
    }

    @NonNull
    private static List<String> tokens(String version) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < version.length(); i++) {
            char c = version.charAt(i);
            if (c == '.' || c == '-' || c == '_' || c == '+') {
                if (current.length() > 0) tokens.add(current.toString());
                current.setLength(0);
                continue;
            }
            if (current.length() > 0 && Character.isDigit(c) != Character.isDigit(current.charAt(current.length() - 1))) {
                tokens.add(current.toString());
                current.setLength(0);
            }
            current.append(c);
        }
        if (current.length() > 0) tokens.add(current.toString());
        return tokens;
    }

    private static int compareToken(@Nullable String x, @Nullable String y) {
        if (x == null && y == null) return 0;
        if (x == null) return isNumeric(y) ? Long.compare(0, Long.parseLong(y)) : 1;
        if (y == null) return isNumeric(x) ? Long.compare(Long.parseLong(x), 0) : -1;
        boolean xn = isNumeric(x);
        boolean yn = isNumeric(y);
        if (xn && yn) return Long.compare(Long.parseLong(x), Long.parseLong(y));
        if (xn) return 1;
        if (yn) return -1;
        return Integer.compare(qualifierRank(x), qualifierRank(y));
    }

    private static boolean isNumeric(@Nullable String s) {
        if (s == null || s.isEmpty() || s.length() > 18) return false;
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private static int qualifierRank(String q) {
        switch (q.toLowerCase()) {
            case "alpha":
            case "a":
                return 0;
            case "beta":
            case "b":
                return 1;
            case "m":
            case "milestone":
                return 2;
            case "rc":
            case "cr":
                return 3;
            case "snapshot":
                return 4;
            default:
                return 5;
        }
    }
}
