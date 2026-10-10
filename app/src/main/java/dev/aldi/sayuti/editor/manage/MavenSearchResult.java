package dev.aldi.sayuti.editor.manage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenSearchResult {

    private final String group;
    private final String artifact;
    private final String latestVersion;
    private final int versionCount;
    private final long timestamp;
    @Nullable
    private final String packaging;
    private final List<String> extensions;
    private int rank;
    @Nullable
    private String selectedVersion;

    public MavenSearchResult(@NonNull String group, @NonNull String artifact, @NonNull String latestVersion) {
        this(group, artifact, latestVersion, 0, 0L, null, Collections.emptyList());
    }

    public MavenSearchResult(@NonNull String group, @NonNull String artifact, @NonNull String latestVersion,
                             int versionCount, long timestamp, @Nullable String packaging, @NonNull List<String> extensions) {
        this.group = group;
        this.artifact = artifact;
        this.latestVersion = latestVersion;
        this.versionCount = versionCount;
        this.timestamp = timestamp;
        this.packaging = packaging;
        this.extensions = Collections.unmodifiableList(new ArrayList<>(extensions));
    }

    public String getGroup() {
        return group;
    }

    public String getArtifact() {
        return artifact;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public int getVersionCount() {
        return versionCount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Nullable
    public String getPackaging() {
        return packaging;
    }

    @NonNull
    public List<String> getExtensions() {
        return extensions;
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public String getCoordinateName() {
        return group + ":" + artifact;
    }

    @NonNull
    public String getSelectedVersion() {
        return selectedVersion != null ? selectedVersion : latestVersion;
    }

    public boolean hasExplicitVersion() {
        return selectedVersion != null;
    }

    public void setSelectedVersion(@Nullable String selectedVersion) {
        this.selectedVersion = selectedVersion;
    }

    public String getFullCoordinate() {
        return group + ":" + artifact + ":" + getSelectedVersion();
    }

    public boolean isAar() {
        if ("aar".equalsIgnoreCase(packaging)) return true;
        for (String extension : extensions) {
            if (".aar".equalsIgnoreCase(extension)) return true;
        }
        return false;
    }

    public boolean isJar() {
        if (isAar()) return false;
        if ("jar".equalsIgnoreCase(packaging) || "bundle".equalsIgnoreCase(packaging)) return true;
        for (String extension : extensions) {
            if (".jar".equalsIgnoreCase(extension)) return true;
        }
        return false;
    }
}
