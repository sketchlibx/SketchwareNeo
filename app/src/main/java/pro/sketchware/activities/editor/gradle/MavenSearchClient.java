package pro.sketchware.activities.editor.gradle;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.google.gson.Gson;

import dev.aldi.sayuti.editor.manage.MavenCentralClient;
import dev.aldi.sayuti.editor.manage.MavenSearchResult;
import mod.hey.studios.util.Helper;
import mod.pranav.dependency.resolver.RepositoryProbe;
import pro.sketchware.utility.FileUtil;

public final class MavenSearchClient {

    private MavenSearchClient() {
    }

    @NonNull
    public static List<MavenSearchResult> search(@NonNull String query) throws Exception {
        return new MavenCentralClient(configuredRepositories()).search(query).results;
    }

    @NonNull
    private static List<RepositoryProbe.Repo> configuredRepositories() {
        List<RepositoryProbe.Repo> repos = new ArrayList<>();
        try {
            String path = FileUtil.getExternalStorageDir() + "/.sketchware/libs/repositories.json";
            if (FileUtil.isExistFile(path)) {
                ArrayList<HashMap<String, Object>> parsed = new Gson().fromJson(FileUtil.readFile(path), Helper.TYPE_MAP_LIST);
                if (parsed != null) {
                    for (HashMap<String, Object> entry : parsed) {
                        Object url = entry.get("url");
                        Object name = entry.get("name");
                        if (url instanceof String && !((String) url).isEmpty()) {
                            repos.add(new RepositoryProbe.Repo(name instanceof String ? (String) name : (String) url, (String) url));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (repos.isEmpty()) repos.add(new RepositoryProbe.Repo("Google Maven", "https://maven.google.com"));
        return repos;
    }
}
