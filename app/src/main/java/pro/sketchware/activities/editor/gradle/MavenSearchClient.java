package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import dev.aldi.sayuti.editor.manage.MavenSearchResult;

public final class MavenSearchClient {

    private MavenSearchClient() {
    }

    @NonNull
    public static List<MavenSearchResult> search(@NonNull String query) throws Exception {
        URL url = new URL("https://search.maven.org/solrsearch/select?q=" + URLEncoder.encode(query, "UTF-8") + "&rows=20&wt=json");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) response.append(line);
        } finally {
            connection.disconnect();
        }
        JSONArray docs = new JSONObject(response.toString()).getJSONObject("response").getJSONArray("docs");
        List<MavenSearchResult> results = new ArrayList<>();
        for (int i = 0; i < docs.length(); i++) {
            JSONObject doc = docs.getJSONObject(i);
            String group = doc.optString("g");
            String artifact = doc.optString("a");
            String version = doc.optString("latestVersion", doc.optString("v", ""));
            if (!group.isEmpty() && !artifact.isEmpty() && !version.isEmpty()) {
                results.add(new MavenSearchResult(group, artifact, version));
            }
        }
        return results;
    }
}
