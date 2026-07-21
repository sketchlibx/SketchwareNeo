package dev.aldi.sayuti.editor.manage;

import androidx.annotation.NonNull;

public class MavenSearchResult {
    private final String group;
    private final String artifact;
    private final String latestVersion;

    public MavenSearchResult(@NonNull String group, @NonNull String artifact, @NonNull String latestVersion) {
        this.group = group;
        this.artifact = artifact;
        this.latestVersion = latestVersion;
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

    public String getCoordinateName() {
        return group + ":" + artifact;
    }

    public String getFullCoordinate() {
        return group + ":" + artifact + ":" + latestVersion;
    }
}
