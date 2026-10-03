package mod.sketchlibx.importer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Objects;

public final class GradleDependency {
    public final String configuration;
    public final String group;
    public final String artifact;
    @Nullable
    public final String version;

    public GradleDependency(@NonNull String configuration, @NonNull String group, @NonNull String artifact, @Nullable String version) {
        this.configuration = configuration;
        this.group = group;
        this.artifact = artifact;
        this.version = version;
    }

    @NonNull
    public String key() {
        return group + ":" + artifact;
    }

    @NonNull
    public String coordinate() {
        return version == null || version.isEmpty() ? key() : key() + ":" + version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GradleDependency other)) return false;
        return configuration.equals(other.configuration) && coordinate().equals(other.coordinate());
    }

    @Override
    public int hashCode() {
        return Objects.hash(configuration, coordinate());
    }

    @NonNull
    @Override
    public String toString() {
        return configuration + " " + coordinate();
    }
}
