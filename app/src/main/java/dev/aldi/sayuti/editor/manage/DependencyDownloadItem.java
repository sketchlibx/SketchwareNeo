package dev.aldi.sayuti.editor.manage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Objects;

import org.cosmic.ide.dependency.resolver.api.Artifact;

public class DependencyDownloadItem {

    public enum DownloadState {
        PENDING,
        RESOLVING,
        DOWNLOADING,
        UNZIPPING,
        DEXING,
        COMPLETED,
        ERROR
    }

    private final String name;
    private final String displayName;
    private DownloadState state;
    private String statusMessage;
    private long bytesDownloaded;
    private long totalBytes;
    private int progress;
    private String errorMessage;
    @Nullable
    private String note;
    private final Artifact artifact;

    public DependencyDownloadItem(@NonNull Artifact artifact) {
        this.artifact = artifact;
        this.name = artifact.toString();
        this.displayName = artifact.getArtifactId() + "-" + artifact.getVersion();
        setState(DownloadState.PENDING);
        this.bytesDownloaded = 0;
        this.totalBytes = 0;
        this.progress = 0;
        this.errorMessage = null;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public DownloadState getState() {
        return state;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public int getProgress() {
        return progress;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public void setState(DownloadState state) {
        this.state = state;
        if (state != DownloadState.ERROR) this.errorMessage = null;
        updateStatusMessage();
    }

    public void setProgress(long bytes, long total) {
        this.bytesDownloaded = bytes;
        this.totalBytes = total;
        this.progress = total > 0 ? (int) Math.min(100, (bytes * 100) / total) : 0;
        if (state == DownloadState.DOWNLOADING) updateStatusMessage();
    }

    public void setNote(@Nullable String note) {
        this.note = note;
        updateStatusMessage();
    }

    public void setError(String errorMessage) {
        this.state = DownloadState.ERROR;
        this.errorMessage = errorMessage;
        this.statusMessage = "Error: " + errorMessage;
    }

    private void updateStatusMessage() {
        switch (state) {
            case PENDING:
                statusMessage = "Pending...";
                break;
            case RESOLVING:
                statusMessage = "Resolving dependency...";
                break;
            case DOWNLOADING:
                if (totalBytes > 0) {
                    statusMessage = formatBytes(bytesDownloaded) + " / " + formatBytes(totalBytes);
                } else if (bytesDownloaded > 0) {
                    statusMessage = "Downloading... " + formatBytes(bytesDownloaded);
                } else {
                    statusMessage = "Downloading...";
                }
                break;
            case UNZIPPING:
                statusMessage = "Unzipping...";
                break;
            case DEXING:
                statusMessage = "Processing (DEX)...";
                break;
            case COMPLETED:
                statusMessage = note != null ? note : "Completed";
                break;
            case ERROR:
                statusMessage = "Error: " + (errorMessage != null ? errorMessage : "Unknown error");
                break;
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1048576) {
            return String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0);
        } else {
            return String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0);
        }
    }

    public boolean isCompleted() {
        return state == DownloadState.COMPLETED;
    }

    public boolean isError() {
        return state == DownloadState.ERROR;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DependencyDownloadItem that = (DependencyDownloadItem) obj;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    @NonNull
    public String toString() {
        return "DependencyDownloadItem{"
                + "name='" + name + '\''
                + ", displayName='" + displayName + '\''
                + ", state=" + state
                + ", statusMessage='" + statusMessage + '\''
                + ", bytesDownloaded=" + bytesDownloaded
                + ", totalBytes=" + totalBytes
                + ", progress=" + progress
                + ", errorMessage='" + errorMessage + '\''
                + '}';
    }
}
