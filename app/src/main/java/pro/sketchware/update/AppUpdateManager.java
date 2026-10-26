package pro.sketchware.update;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import pro.sketchware.BuildConfig;

/** Secure, public-metadata-only updater. The native APK intentionally contains no server API key. */
public final class AppUpdateManager {
    private static final String TAG = "SketchwareUpdate";
    private static final String PREFS = "sketchware_app_updates";
    private static final String KEY_CACHED_RELEASE = "cached_release_json";
    private static final String KEY_DISMISSED_RELEASE = "dismissed_release_id";
    private static final String ENDPOINT = "https://sketchlib.in/api/v1/app-updates/latest";
    private static final int MAX_RESPONSE_BYTES = 128 * 1024;
    private static final Pattern RELEASE_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");
    private static final Pattern SHA256 = Pattern.compile("[a-fA-F0-9]{64}");
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Activity resumedActivity;
    private static volatile boolean dialogShowing;
    private static volatile String deferredReleaseThisProcess = "";

    private AppUpdateManager() { }

    public static void fetchLatest(@NonNull Context context) {
        final Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> fetchBlocking(appContext));
    }

    static void fetchLatestBlocking(@NonNull Context context) {
        fetchBlocking(context.getApplicationContext());
    }

    private static void fetchBlocking(@NonNull Context context) {
        HttpURLConnection connection = null;
        try {
            URL endpoint = new URL(ENDPOINT);
            String body = null;
            for (int redirects = 0; redirects <= 2; redirects++) {
                if (!isAllowedApiUrl(endpoint)) return;
                connection = (HttpURLConnection) endpoint.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(10000);
                connection.setUseCaches(false);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "SketchwareNeo/" + BuildConfig.VERSION_NAME);
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP
                        || status == HttpURLConnection.HTTP_SEE_OTHER || status == 307 || status == 308) {
                    String location = connection.getHeaderField("Location");
                    if (redirects >= 2 || TextUtils.isEmpty(location)) return;
                    URL redirected = new URL(endpoint, location);
                    connection.disconnect();
                    connection = null;
                    if (!isAllowedApiUrl(redirected)) return;
                    endpoint = redirected;
                    continue;
                }
                if (status != HttpURLConnection.HTTP_OK) return;
                try (InputStream input = new BufferedInputStream(connection.getInputStream())) {
                    body = readLimited(input, MAX_RESPONSE_BYTES);
                }
                break;
            }
            if (body == null) return;
            JSONObject envelope = new JSONObject(body);
            if (!envelope.optBoolean("success", false)) return;
            JSONObject data = envelope.optJSONObject("data");
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            if (data == null) {
                clearCachedRelease(prefs);
                return;
            }
            JSONObject release = validateRelease(data);
            if (release == null) return;
            if (release.optInt("version_code", 0) <= BuildConfig.VERSION_CODE) {
                clearCachedRelease(prefs);
                return;
            }
            prefs.edit().putString(KEY_CACHED_RELEASE, release.toString()).apply();
            MAIN.post(() -> showPendingOnForeground(context));
        } catch (Exception e) {
            // A network failure must not erase the last valid cached release.
            Log.w(TAG, "Update check unavailable; keeping cached release metadata.");
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    @Nullable
    private static JSONObject validateRelease(@NonNull JSONObject source) {
        String releaseId = source.optString("release_id", "").trim();
        int versionCode = source.optInt("version_code", 0);
        String versionName = source.optString("version_name", "").trim();
        String title = source.optString("title", "New update available").trim();
        String downloadUrl = source.optString("download_url", "").trim();
        String sha256 = source.optString("sha256", "").trim();
        if (!RELEASE_ID.matcher(releaseId).matches() || versionCode < 1 || versionName.isEmpty()
                || versionName.length() > 80 || title.isEmpty() || title.length() > 255
                || !SHA256.matcher(sha256).matches() || !isAllowedDownloadUrl(downloadUrl)) return null;
        boolean mandatory = source.optBoolean("mandatory", false);
        boolean dismissible = !mandatory && source.optBoolean("dismissible", true);
        JSONObject clean = new JSONObject();
        try {
            clean.put("release_id", releaseId);
            clean.put("version_code", versionCode);
            clean.put("version_name", versionName);
            clean.put("title", title);
            clean.put("changelog", limit(source.optString("changelog", ""), 12000));
            clean.put("download_url", downloadUrl);
            clean.put("sha256", sha256.toLowerCase(Locale.ROOT));
            clean.put("mandatory", mandatory);
            clean.put("dismissible", dismissible);
        } catch (Exception ignored) {
            return null;
        }
        return clean;
    }

    private static boolean isAllowedApiUrl(@NonNull URL value) {
        try {
            Uri uri = Uri.parse(value.toExternalForm());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("sketchlib.in") || host.equals("www.sketchlib.in");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isAllowedDownloadUrl(@NonNull String value) {
        try {
            Uri uri = Uri.parse(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null) return false;
            if (uri.getPort() != -1 && uri.getPort() != 443) return false;
            host = host.toLowerCase(Locale.ROOT);
            return host.equals("sketchlib.in") || host.equals("www.sketchlib.in")
                    || host.equals("github.com") || host.equals("objects.githubusercontent.com")
                    || host.equals("release-assets.githubusercontent.com");
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String readLimited(@NonNull InputStream input, int limit) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > limit) throw new IllegalStateException("Update response is too large");
            output.write(buffer, 0, count);
        }
        return output.toString("UTF-8");
    }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static void clearCachedRelease(@NonNull SharedPreferences prefs) {
        // A successful response with no newer published release is authoritative.
        // Network failures never call this path, so offline launches retain the last valid update.
        prefs.edit().remove(KEY_CACHED_RELEASE).apply();
    }

    public static void onActivityResumed(@NonNull Activity activity) {
        resumedActivity = activity;
        showPendingOnForeground(activity.getApplicationContext());
    }

    public static void onActivityPaused(@NonNull Activity activity) {
        if (resumedActivity == activity) resumedActivity = null;
    }

    private static void showPendingOnForeground(@NonNull Context context) {
        MAIN.post(() -> {
            Activity activity = resumedActivity;
            if (activity == null || activity.isFinishing() || activity.isDestroyed() || dialogShowing) return;
            SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String cached = prefs.getString(KEY_CACHED_RELEASE, null);
            if (TextUtils.isEmpty(cached)) return;
            try {
                JSONObject release = new JSONObject(cached);
                if (release.optInt("version_code", 0) <= BuildConfig.VERSION_CODE) return;
                String releaseId = release.optString("release_id", "");
                String releaseKey = releaseId + ":" + release.optInt("version_code", 0);
                if (releaseId.isEmpty() || releaseKey.equals(prefs.getString(KEY_DISMISSED_RELEASE, ""))
                        || releaseKey.equals(deferredReleaseThisProcess)) return;
                showDialog(activity, prefs, release);
            } catch (Exception e) {
                prefs.edit().remove(KEY_CACHED_RELEASE).apply();
                Log.w(TAG, "Cached update metadata is invalid; cache cleared.");
            }
        });
    }

    private static void showDialog(@NonNull Activity activity, @NonNull SharedPreferences prefs, @NonNull JSONObject release) {
        boolean mandatory = release.optBoolean("mandatory", false);
        boolean dismissible = !mandatory && release.optBoolean("dismissible", true);
        String releaseId = release.optString("release_id", "");
        String releaseKey = releaseId + ":" + release.optInt("version_code", 0);
        String versionName = release.optString("version_name", "");
        String changelog = release.optString("changelog", "").trim();
        String message = "Version " + versionName + " is available.\n\n" + (changelog.isEmpty() ? "Install the latest version to get the latest fixes and improvements." : changelog);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(limit(release.optString("title", "New update available"), 255))
                .setMessage(message)
                .setCancelable(dismissible)
                .setPositiveButton("Update", (dialog, which) -> openDownload(activity, release.optString("download_url", "")));
        if (dismissible) {
            builder.setNegativeButton("Later", (dialog, which) -> deferredReleaseThisProcess = releaseKey);
            builder.setNeutralButton("Don't show again", (dialog, which) -> prefs.edit().putString(KEY_DISMISSED_RELEASE, releaseKey).apply());
        }
        dialogShowing = true;
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(ignored -> dialogShowing = false);
        dialog.setOnCancelListener(ignored -> {
            if (dismissible) deferredReleaseThisProcess = releaseKey;
        });
        try {
            dialog.show();
        } catch (Exception e) {
            dialogShowing = false;
            Log.w(TAG, "Update prompt could not be shown on the current activity.");
        }
    }

    private static void openDownload(@NonNull Activity activity, @NonNull String url) {
        if (!isAllowedDownloadUrl(url)) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Log.w(TAG, "No activity can open the validated download URL.");
        }
    }
}
