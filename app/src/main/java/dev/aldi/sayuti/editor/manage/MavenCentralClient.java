package dev.aldi.sayuti.editor.manage;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.pranav.dependency.resolver.FailureFormatter;
import mod.pranav.dependency.resolver.HttpFetcher;
import mod.pranav.dependency.resolver.MavenVersions;
import mod.pranav.dependency.resolver.RepositoryProbe;

public final class MavenCentralClient {

    public static final String DEFAULT_SOLR_URL = "https://search.maven.org/solrsearch/select";
    public static final String CENTRAL_REPO_URL = "https://repo1.maven.org/maven2";

    private static final int ROWS = 30;
    private static final int SOLR_LIMIT_BYTES = 4 * 1024 * 1024;
    private static final int METADATA_LIMIT_BYTES = 8 * 1024 * 1024;
    private static final int SOLR_ATTEMPTS = 3;
    private static final int METADATA_ATTEMPTS = 2;
    private static final Pattern PART = Pattern.compile("[A-Za-z0-9_.\\-]+");
    private static final Pattern VERSION_BLOCK = Pattern.compile("<versions>(.*?)</versions>", Pattern.DOTALL);
    private static final Pattern VERSION_ITEM = Pattern.compile("<version>\\s*([^<\\s]+)\\s*</version>");
    private static final Pattern LATEST_TAG = Pattern.compile("<latest>\\s*([^<\\s]+)\\s*</latest>");
    private static final Pattern RELEASE_TAG = Pattern.compile("<release>\\s*([^<\\s]+)\\s*</release>");

    public enum QueryKind {TEXT, GROUP_ARTIFACT, GROUP_ARTIFACT_VERSION}

    public enum SortMode {RELEVANCE, NAME, NEWEST, MOST_VERSIONS}

    public enum TypeFilter {ANY, AAR, JAR}

    public static final class Query {
        public final QueryKind kind;
        public final String text;
        @Nullable
        public final String group;
        @Nullable
        public final String artifact;
        @Nullable
        public final String version;

        Query(QueryKind kind, String text, @Nullable String group, @Nullable String artifact, @Nullable String version) {
            this.kind = kind;
            this.text = text;
            this.group = group;
            this.artifact = artifact;
            this.version = version;
        }

        @NonNull
        public String getCoordinate() {
            return group + ":" + artifact;
        }
    }

    public static final class SearchException extends Exception {
        public enum Kind {NETWORK, HTTP, PARSE, NOT_FOUND, INVALID}

        public final Kind kind;
        public final int httpCode;

        public SearchException(Kind kind, int httpCode, String message, @Nullable Throwable cause) {
            super(message, cause);
            this.kind = kind;
            this.httpCode = httpCode;
        }
    }

    public static final class SearchOutcome {
        public final List<MavenSearchResult> results;
        public final String source;

        SearchOutcome(List<MavenSearchResult> results, String source) {
            this.results = results;
            this.source = source;
        }
    }

    public static final class VersionList {
        public final String source;
        public final List<String> versions;
        @Nullable
        public final String latest;

        VersionList(String source, List<String> versions, @Nullable String latest) {
            this.source = source;
            this.versions = versions;
            this.latest = latest;
        }

        @Nullable
        public String latestStable() {
            return MavenVersions.latestStable(versions);
        }

        @NonNull
        public String latestOrNewest() {
            if (latest != null && versions.contains(latest)) return latest;
            return versions.get(0);
        }
    }

    private final String solrUrl;
    private final List<RepositoryProbe.Repo> metadataRepos;

    public MavenCentralClient(@NonNull List<RepositoryProbe.Repo> configuredRepos) {
        this(DEFAULT_SOLR_URL, CENTRAL_REPO_URL, configuredRepos);
    }

    public MavenCentralClient(@NonNull String solrUrl, @NonNull String centralRepoUrl, @NonNull List<RepositoryProbe.Repo> configuredRepos) {
        this.solrUrl = solrUrl;
        List<RepositoryProbe.Repo> repos = new ArrayList<>();
        repos.add(new RepositoryProbe.Repo("Maven Central", centralRepoUrl));
        for (int index : RepositoryProbe.probeOrder(configuredRepos)) {
            RepositoryProbe.Repo repo = configuredRepos.get(index);
            if (repo.url.contains("jitpack.io")) continue;
            if (repo.url.equals(repos.get(0).url)) continue;
            repos.add(repo);
        }
        this.metadataRepos = repos;
    }

    @NonNull
    public static Query parseQuery(@NonNull String input) {
        String text = input.trim();
        String[] parts = text.split(":", -1);
        if (parts.length == 2 && PART.matcher(parts[0]).matches() && PART.matcher(parts[1]).matches()) {
            return new Query(QueryKind.GROUP_ARTIFACT, text, parts[0], parts[1], null);
        }
        if (parts.length == 3 && PART.matcher(parts[0]).matches() && PART.matcher(parts[1]).matches()
                && PART.matcher(parts[2].replace("+", "")).matches() && !parts[2].isEmpty()) {
            return new Query(QueryKind.GROUP_ARTIFACT_VERSION, text, parts[0], parts[1], parts[2]);
        }
        return new Query(QueryKind.TEXT, text, null, null, null);
    }

    @NonNull
    public String buildSearchUrl(@NonNull Query query) {
        String q = query.kind == QueryKind.TEXT
                ? query.text
                : "g:\"" + query.group + "\" AND a:\"" + query.artifact + "\"";
        try {
            return solrUrl + "?q=" + URLEncoder.encode(q, "UTF-8") + "&rows=" + ROWS + "&wt=json";
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    public SearchOutcome search(@NonNull String input) throws SearchException {
        Query query = parseQuery(input);
        if (query.text.isEmpty()) {
            throw new SearchException(SearchException.Kind.INVALID, 0, "Enter a library name, group:artifact or group:artifact:version.", null);
        }
        if (query.kind == QueryKind.TEXT) {
            return new SearchOutcome(solrSearch(query), "Maven Central");
        }

        SearchException solrFailure = null;
        if (query.kind == QueryKind.GROUP_ARTIFACT) {
            try {
                List<MavenSearchResult> found = solrSearch(query);
                if (!found.isEmpty()) return new SearchOutcome(found, "Maven Central");
            } catch (SearchException e) {
                solrFailure = e;
            }
        }

        try {
            VersionList list = fetchVersions(query.group, query.artifact);
            MavenSearchResult result = new MavenSearchResult(query.group, query.artifact, list.latestOrNewest(),
                    list.versions.size(), 0L, null, Collections.emptyList());
            if (query.kind == QueryKind.GROUP_ARTIFACT_VERSION) {
                if (!list.versions.contains(query.version)) {
                    throw new SearchException(SearchException.Kind.NOT_FOUND, 404,
                            "Version " + query.version + " of " + query.getCoordinate() + " was not found in " + list.source
                                    + ". Newest available: " + list.latestOrNewest() + ".", null);
                }
                result.setSelectedVersion(query.version);
            }
            List<MavenSearchResult> single = new ArrayList<>();
            single.add(result);
            return new SearchOutcome(single, list.source);
        } catch (SearchException e) {
            if (e.kind == SearchException.Kind.NOT_FOUND && e.httpCode == 404
                    && query.kind == QueryKind.GROUP_ARTIFACT && solrFailure == null) {
                return new SearchOutcome(new ArrayList<>(), "Maven Central");
            }
            if (e.kind == SearchException.Kind.NOT_FOUND && e.httpCode == 404 && solrFailure != null) {
                throw solrFailure;
            }
            throw e;
        }
    }

    @NonNull
    private List<MavenSearchResult> solrSearch(Query query) throws SearchException {
        HttpFetcher.Response response = HttpFetcher.getWithRetry(buildSearchUrl(query), SOLR_LIMIT_BYTES, SOLR_ATTEMPTS, null);
        if (response.isNetworkFailure()) {
            throw new SearchException(SearchException.Kind.NETWORK, -1,
                    "Could not reach Maven Central search: " + response.describe(), response.error);
        }
        if (!response.isSuccess()) {
            throw new SearchException(SearchException.Kind.HTTP, response.code,
                    "Maven Central search failed: " + response.describe(), null);
        }
        return parseSolr(response.body);
    }

    @NonNull
    static List<MavenSearchResult> parseSolr(@NonNull String body) throws SearchException {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                throw new SearchException(SearchException.Kind.HTTP, error.optInt("code", 0),
                        "Maven Central search error: " + error.optString("msg", "unknown error"), null);
            }
            JSONObject response = root.optJSONObject("response");
            JSONArray docs = response == null ? null : response.optJSONArray("docs");
            if (docs == null) {
                throw new SearchException(SearchException.Kind.PARSE, 0, "Maven Central returned an unexpected response (no result list).", null);
            }
            List<MavenSearchResult> results = new ArrayList<>();
            for (int i = 0; i < docs.length(); i++) {
                JSONObject doc = docs.optJSONObject(i);
                if (doc == null) continue;
                String group = doc.optString("g", "");
                String artifact = doc.optString("a", "");
                String latest = doc.optString("latestVersion", "");
                if (latest.isEmpty()) latest = doc.optString("v", "");
                if (group.isEmpty() || artifact.isEmpty() || latest.isEmpty()) continue;
                List<String> extensions = new ArrayList<>();
                JSONArray ec = doc.optJSONArray("ec");
                if (ec != null) {
                    for (int j = 0; j < ec.length(); j++) extensions.add(ec.optString(j, ""));
                }
                String packaging = doc.optString("p", "");
                MavenSearchResult result = new MavenSearchResult(group, artifact, latest,
                        doc.optInt("versionCount", 0), doc.optLong("timestamp", 0L),
                        packaging.isEmpty() ? null : packaging, extensions);
                result.setRank(results.size());
                results.add(result);
            }
            return results;
        } catch (JSONException e) {
            throw new SearchException(SearchException.Kind.PARSE, 0,
                    "Maven Central returned a response that could not be read: " + FailureFormatter.describe(e), e);
        }
    }

    @NonNull
    public VersionList fetchVersions(@NonNull String group, @NonNull String artifact) throws SearchException {
        StringBuilder trail = new StringBuilder();
        boolean onlyNotFound = true;
        int lastCode = 404;
        for (RepositoryProbe.Repo repo : metadataRepos) {
            String url = repo.url + "/" + group.replace('.', '/') + "/" + artifact + "/maven-metadata.xml";
            HttpFetcher.Response response = HttpFetcher.getWithRetry(url, METADATA_LIMIT_BYTES, METADATA_ATTEMPTS, null);
            if (response.isSuccess()) {
                VersionList parsed = parseMetadata(repo.name, response.body);
                if (parsed != null) return parsed;
                trail.append("\n- ").append(repo.name).append(": metadata contains no versions");
                continue;
            }
            if (response.code != 404) {
                onlyNotFound = false;
                lastCode = response.code;
            }
            trail.append("\n- ").append(repo.name).append(": ").append(response.describe());
        }
        String coordinate = group + ":" + artifact;
        if (onlyNotFound) {
            throw new SearchException(SearchException.Kind.NOT_FOUND, 404,
                    coordinate + " was not found in Maven Central or the configured repositories." + trail, null);
        }
        throw new SearchException(lastCode < 0 ? SearchException.Kind.NETWORK : SearchException.Kind.HTTP, lastCode,
                "Could not load the version list of " + coordinate + "." + trail, null);
    }

    @Nullable
    static VersionList parseMetadata(@NonNull String source, @NonNull String xml) {
        Matcher block = VERSION_BLOCK.matcher(xml);
        if (!block.find()) return null;
        Matcher item = VERSION_ITEM.matcher(block.group(1));
        List<String> versions = new ArrayList<>();
        while (item.find()) {
            String version = item.group(1);
            if (!versions.contains(version)) versions.add(version);
        }
        if (versions.isEmpty()) return null;
        String latest = null;
        Matcher release = RELEASE_TAG.matcher(xml);
        if (release.find()) latest = release.group(1);
        if (latest == null) {
            Matcher latestTag = LATEST_TAG.matcher(xml);
            if (latestTag.find()) latest = latestTag.group(1);
        }
        return new VersionList(source, MavenVersions.sortNewestFirst(versions), latest);
    }

    @NonNull
    public static List<MavenSearchResult> sortAndFilter(@NonNull List<MavenSearchResult> input, @NonNull SortMode sort, @NonNull TypeFilter filter) {
        List<MavenSearchResult> out = new ArrayList<>();
        for (MavenSearchResult result : input) {
            if (filter == TypeFilter.AAR && !result.isAar()) continue;
            if (filter == TypeFilter.JAR && !result.isJar()) continue;
            out.add(result);
        }
        Comparator<MavenSearchResult> comparator;
        switch (sort) {
            case NAME:
                comparator = (x, y) -> x.getCoordinateName().toLowerCase(Locale.ROOT).compareTo(y.getCoordinateName().toLowerCase(Locale.ROOT));
                break;
            case NEWEST:
                comparator = (x, y) -> Long.compare(y.getTimestamp(), x.getTimestamp());
                break;
            case MOST_VERSIONS:
                comparator = (x, y) -> Integer.compare(y.getVersionCount(), x.getVersionCount());
                break;
            default:
                comparator = (x, y) -> Integer.compare(x.getRank(), y.getRank());
        }
        Collections.sort(out, comparator);
        return out;
    }
}
