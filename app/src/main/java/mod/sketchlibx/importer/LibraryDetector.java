package mod.sketchlibx.importer;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import pro.sketchware.utility.FileUtil;

import java.io.File;

/**
 * Builds the Sketchware "library" data string from:
 *   - ParsedGradle (dependency flags)
 *   - google-services.json (Firebase app ID, API key, DB URL, etc.)
 *   - jniLibs/ folder (native library detection)
 *   - libs/*.aar / libs/*.jar (local library detection)
 *
 * Library file format (confirmed from original importer + library bean analysis):
 *
 *   @firebaseDB
 *   {ProjectLibraryBean JSON libType=0}
 *   @compat
 *   {ProjectLibraryBean JSON libType=1}
 *   @admob
 *   {ProjectLibraryBean JSON libType=2}
 *   @googleMap
 *   {ProjectLibraryBean JSON libType=3}
 *
 * libType=1 (AppCompat) is ALWAYS written with useYn="Y" — Sketchware requires it.
 * libTypes 0,2,3 are written based on detection from gradle / google-services.json.
 * libTypes 4,5 (local libs / native) are written only when detected.
 *
 * Section header names (@firebaseDB, @compat, etc.) are the exact strings
 * used by Sketchware's library reader class (confirmed from original importer).
 */
public class LibraryDetector {

    private static final String TAG = "LibraryDetector";

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * @param gradle        Parsed gradle data (has dependency flags).
     * @param appModuleDir  App module directory (for google-services.json + jniLibs).
     * @return Complete "library" data string ready for AES encryption and write.
     */
    public String buildLibraryData(ParsedGradle gradle, File appModuleDir) {
        StringBuilder sb = new StringBuilder();

        // ── libType 0: Firebase ───────────────────────────────────────────────
        FirebaseData fb = parseGoogleServicesJson(appModuleDir);
        boolean useFirebase = gradle.hasFirebase || fb.found;

        sb.append("@firebaseDB\n");
        sb.append(buildFirebaseBean(useFirebase, fb));
        sb.append("\n");

        // ── libType 1: AppCompat — ALWAYS "Y" ────────────────────────────────
        sb.append("@compat\n");
        sb.append(buildCompatBean(gradle));
        sb.append("\n");

        // ── libType 2: AdMob ──────────────────────────────────────────────────
        sb.append("@admob\n");
        sb.append(buildAdMobBean(gradle.hasAdMob));
        sb.append("\n");

        // ── libType 3: Google Maps ────────────────────────────────────────────
        sb.append("@googleMap\n");
        sb.append(buildGoogleMapBean(gradle.hasGoogleMaps));
        sb.append("\n");

        // ── libType 4: Local .aar / .jar libraries ────────────────────────────
        for (String localLibPath : gradle.localLibPaths) {
            String libFileName = new File(localLibPath).getName();
            sb.append("@locallib_").append(libFileName).append("\n");
            sb.append(buildLocalLibBean(localLibPath));
            sb.append("\n");
            Log.d(TAG, "Added local lib: " + libFileName);
        }

        // ── libType 5: Native (.so) libraries ────────────────────────────────
        if (gradle.hasNativeLibs) {
            sb.append("@nativelib\n");
            sb.append(buildNativeLibBean());
            sb.append("\n");
            Log.d(TAG, "Added native lib entry.");
        }

        return sb.toString();
    }

    // ── Firebase / google-services.json ──────────────────────────────────────

    private FirebaseData parseGoogleServicesJson(File appModuleDir) {
        FirebaseData data = new FirebaseData();
        File gsFile = new File(appModuleDir, "google-services.json");
        if (!gsFile.exists()) return data;

        try {
            JSONObject json       = new JSONObject(FileUtil.readFile(gsFile.getAbsolutePath()));
            JSONObject projectInfo = json.getJSONObject("project_info");

            data.found         = true;
            data.projectId     = projectInfo.optString("project_id", "");
            data.dbUrl         = projectInfo.optString("firebase_url", "");
            data.storageBucket = projectInfo.optString("storage_bucket",
                    data.projectId + ".appspot.com");

            JSONArray clients = json.getJSONArray("client");
            if (clients.length() > 0) {
                JSONObject client0 = clients.getJSONObject(0);
                data.appId = client0.getJSONObject("client_info")
                        .optString("mobilesdk_app_id", "");

                JSONArray apiKeys = client0.getJSONArray("api_key");
                if (apiKeys.length() > 0) {
                    data.apiKey = apiKeys.getJSONObject(0).optString("current_key", "");
                }
            }

            Log.d(TAG, "Parsed google-services.json: projectId=" + data.projectId);

        } catch (Exception e) {
            Log.e(TAG, "Failed to parse google-services.json", e);
        }

        return data;
    }

    // ── Bean JSON builders ────────────────────────────────────────────────────

    /**
     * Builds ProjectLibraryBean JSON for libType=0 (Firebase).
     * Confirmed fields from ProjectLibraryBean @Expose analysis:
     *   adUnits, appId, configurations, data, libType,
     *   reserved1, reserved2, reserved3, testDevices, useYn
     *
     * Field semantics for Firebase (inferred from original importer which was working):
     *   data      = Firebase DB URL (without https://)
     *   reserved1 = mobilesdk_app_id (Firebase App ID)
     *   reserved2 = API key
     *   reserved3 = storage bucket
     */
    private String buildFirebaseBean(boolean useFirebase, FirebaseData fb) {
        String dbUrl = fb.dbUrl.replace("https://", "");
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{},"
             + "\"data\":\"" + escapeJson(dbUrl) + "\","
             + "\"libType\":0,"
             + "\"reserved1\":\"" + escapeJson(fb.appId) + "\","
             + "\"reserved2\":\"" + escapeJson(fb.apiKey) + "\","
             + "\"reserved3\":\"" + escapeJson(fb.storageBucket) + "\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"" + (useFirebase ? "Y" : "N") + "\"}";
    }

    /**
     * libType=1 (AppCompat / Material).
     * Always useYn="Y".
     * configurations carries Material3 / dynamic color / theme flags.
     */
    private String buildCompatBean(ParsedGradle gradle) {
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{"
             +     "\"material3\":"      + gradle.hasMaterial3 + ","
             +     "\"dynamic_colors\":true,"
             +     "\"theme\":\"DayNight\"},"
             + "\"data\":\"\","
             + "\"libType\":1,"
             + "\"reserved1\":\"\","
             + "\"reserved2\":\"\","
             + "\"reserved3\":\"\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"Y\"}";
    }

    /** libType=2 (AdMob). */
    private String buildAdMobBean(boolean useAdMob) {
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{},"
             + "\"data\":\"\","
             + "\"libType\":2,"
             + "\"reserved1\":\"\","
             + "\"reserved2\":\"\","
             + "\"reserved3\":\"\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"" + (useAdMob ? "Y" : "N") + "\"}";
    }

    /** libType=3 (Google Maps). */
    private String buildGoogleMapBean(boolean useMaps) {
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{},"
             + "\"data\":\"\","
             + "\"libType\":3,"
             + "\"reserved1\":\"\","
             + "\"reserved2\":\"\","
             + "\"reserved3\":\"\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"" + (useMaps ? "Y" : "N") + "\"}";
    }

    /** libType=4 (LOCAL_LIB — local .aar / .jar file). */
    private String buildLocalLibBean(String localPath) {
        String fileName = new File(localPath).getName();
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{},"
             + "\"data\":\"" + escapeJson(fileName) + "\","
             + "\"libType\":4,"
             + "\"reserved1\":\"\","
             + "\"reserved2\":\"\","
             + "\"reserved3\":\"\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"Y\"}";
    }

    /** libType=5 (NATIVE_LIB — .so libraries / NDK). */
    private String buildNativeLibBean() {
        return "{\"adUnits\":[],"
             + "\"appId\":\"\","
             + "\"configurations\":{},"
             + "\"data\":\"\","
             + "\"libType\":5,"
             + "\"reserved1\":\"\","
             + "\"reserved2\":\"\","
             + "\"reserved3\":\"\","
             + "\"testDevices\":[],"
             + "\"useYn\":\"Y\"}";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Minimal JSON string escaping for values we embed in manual JSON. */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    // ── Inner data class ──────────────────────────────────────────────────────

    private static class FirebaseData {
        boolean found         = false;
        String  projectId     = "";
        String  dbUrl         = "";
        String  storageBucket = "";
        String  appId         = "";
        String  apiKey        = "";
    }
}
