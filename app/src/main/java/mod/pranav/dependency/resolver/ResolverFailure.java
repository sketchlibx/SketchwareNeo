package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ResolverFailure {

    public enum Stage {
        INVALID_COORDINATE("Invalid coordinate"),
        REPOSITORY("Artifact not found"),
        NETWORK("Network error"),
        POM("POM error"),
        PACKAGING("Unsupported packaging"),
        DOWNLOAD("Download failed"),
        UNZIP("Extraction failed"),
        MISSING_CLASSES("Missing classes.jar"),
        DEX("D8 failed"),
        CANCELLED("Cancelled"),
        UNEXPECTED("Unexpected error");

        public final String title;

        Stage(String title) {
            this.title = title;
        }
    }

    public final Stage stage;
    @Nullable
    public final String coordinate;
    public final String message;
    @Nullable
    public final Throwable cause;

    public ResolverFailure(@NonNull Stage stage, @Nullable String coordinate, @NonNull String message, @Nullable Throwable cause) {
        this.stage = stage;
        this.coordinate = coordinate;
        this.message = message;
        this.cause = cause;
    }

    @NonNull
    public String toUserMessage() {
        StringBuilder out = new StringBuilder(stage.title);
        if (coordinate != null && !coordinate.isEmpty()) out.append(" (").append(coordinate).append(')');
        out.append(": ").append(message);
        return out.toString();
    }

    @NonNull
    @Override
    public String toString() {
        return toUserMessage();
    }
}
