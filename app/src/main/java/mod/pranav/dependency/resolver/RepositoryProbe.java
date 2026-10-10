package mod.pranav.dependency.resolver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class RepositoryProbe {

    private static final int POM_LIMIT_BYTES = 4 * 1024 * 1024;
    private static final int POM_ATTEMPTS = 2;

    private RepositoryProbe() {
    }

    public static final class Repo {
        public final String name;
        public final String url;

        public Repo(@NonNull String name, @NonNull String url) {
            this.name = name;
            this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        }
    }

    public static final class Attempt {
        public final Repo repo;
        public final String url;
        public final int code;
        public final String detail;

        Attempt(Repo repo, String url, int code, String detail) {
            this.repo = repo;
            this.url = url;
            this.code = code;
            this.detail = detail;
        }

        boolean isNetworkFailure() {
            return code < 0;
        }
    }

    public static final class Result {
        public final int foundIndex;
        @Nullable
        public final String foundUrl;
        public final List<Attempt> attempts;
        public final boolean cancelled;

        Result(int foundIndex, @Nullable String foundUrl, List<Attempt> attempts, boolean cancelled) {
            this.foundIndex = foundIndex;
            this.foundUrl = foundUrl;
            this.attempts = attempts;
            this.cancelled = cancelled;
        }

        public boolean found() {
            return foundIndex >= 0;
        }

        public boolean allNetworkFailures() {
            if (attempts.isEmpty()) return false;
            for (Attempt attempt : attempts) {
                if (!attempt.isNetworkFailure()) return false;
            }
            return true;
        }

        @NonNull
        public String explain(@NonNull String coordinate) {
            StringBuilder out = new StringBuilder();
            if (allNetworkFailures()) {
                out.append("No repository could be reached while looking for ").append(coordinate).append(". Check the internet connection.");
            } else {
                out.append(coordinate).append(" was not found in any configured repository.");
            }
            for (Attempt attempt : attempts) {
                out.append("\n- ").append(attempt.repo.name).append(": ").append(attempt.detail);
            }
            return out.toString();
        }
    }

    @NonNull
    public static String pomUrl(@NonNull String repoUrl, @NonNull String group, @NonNull String artifact, @NonNull String version) {
        String base = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        return base + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + ".pom";
    }

    @NonNull
    public static String fileUrl(@NonNull String repoUrl, @NonNull String group, @NonNull String artifact, @NonNull String version, @NonNull String extension) {
        String base = repoUrl.endsWith("/") ? repoUrl.substring(0, repoUrl.length() - 1) : repoUrl;
        return base + "/" + group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + "." + extension;
    }

    @NonNull
    public static List<Integer> probeOrder(@NonNull List<Repo> repos) {
        List<Integer> order = new ArrayList<>();
        List<Integer> deferred = new ArrayList<>();
        for (int i = 0; i < repos.size(); i++) {
            if (repos.get(i).url.contains("jitpack.io")) deferred.add(i);
            else order.add(i);
        }
        order.addAll(deferred);
        return order;
    }

    @NonNull
    public static Result locate(@NonNull List<Repo> repos, @NonNull String group, @NonNull String artifact,
                                @NonNull String version, @Nullable BooleanSupplier cancelled) {
        List<Attempt> attempts = new ArrayList<>();
        for (int index : probeOrder(repos)) {
            if (cancelled != null && cancelled.getAsBoolean()) {
                return new Result(-1, null, attempts, true);
            }
            Repo repo = repos.get(index);
            String url = pomUrl(repo.url, group, artifact, version);
            HttpFetcher.Response response = HttpFetcher.getWithRetry(url, POM_LIMIT_BYTES, POM_ATTEMPTS, cancelled);
            if (response.isSuccess() && !response.body.trim().isEmpty()) {
                attempts.add(new Attempt(repo, url, response.code, "found"));
                return new Result(index, url, attempts, false);
            }
            String detail = response.isSuccess() ? "empty POM response" : response.describe();
            attempts.add(new Attempt(repo, url, response.isSuccess() ? 0 : response.code, detail));
        }
        return new Result(-1, null, attempts, false);
    }
}
