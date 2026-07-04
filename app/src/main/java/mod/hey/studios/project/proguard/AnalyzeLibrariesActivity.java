package mod.hey.studios.project.proguard;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import mod.agus.jcoderz.editor.manage.library.locallibrary.ManageLocalLibrary;
import pro.sketchware.utility.FilePathUtil;
import pro.sketchware.utility.FileUtil;

public class AnalyzeLibrariesActivity extends AppCompatActivity {

    public static class LibraryInfo {
        public String name;
        public long sizeBytes;
        public boolean referenced;
    }

    private String sc_id;
    private RecyclerView recyclerView;
    private LibraryInfoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(pro.sketchware.R.layout.activity_analyze_libraries);

        sc_id = getIntent().getStringExtra("sc_id");

        Toolbar toolbar = findViewById(pro.sketchware.R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        recyclerView = findViewById(pro.sketchware.R.id.recycler_libraries);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LibraryInfoAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        runAnalysis();
    }

    private void runAnalysis() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<LibraryInfo> results = analyze();
            runOnUiThread(() -> adapter.update(results));
        });
    }

    private List<LibraryInfo> analyze() {
        List<LibraryInfo> results = new ArrayList<>();

        ManageLocalLibrary mll = new ManageLocalLibrary(sc_id);
        String javaSource = readAllJavaSource();

        for (HashMap<String, Object> lib : mll.list) {
            Object nameObj = lib.get("name");
            Object jarPathObj = lib.get("jarPath");
            if (!(nameObj instanceof String) || !(jarPathObj instanceof String)) continue;

            LibraryInfo info = new LibraryInfo();
            info.name = (String) nameObj;

            File jarFile = new File((String) jarPathObj);
            info.sizeBytes = jarFile.exists() ? jarFile.length() : 0;
            info.referenced = isReferenced(jarFile, javaSource);

            results.add(info);
        }

        return results;
    }

    private String readAllJavaSource() {
        FilePathUtil paths = new FilePathUtil();
        StringBuilder builder = new StringBuilder();
        appendJavaFiles(new File(paths.getPathJava(sc_id)), builder);
        return builder.toString();
    }

    private void appendJavaFiles(File dir, StringBuilder builder) {
        if (!dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (child.isDirectory()) {
                appendJavaFiles(child, builder);
            } else if (child.getName().endsWith(".java") || child.getName().endsWith(".kt")) {
                builder.append(FileUtil.readFile(child.getAbsolutePath())).append("\n");
            }
        }
    }

    private boolean isReferenced(File jarFile, String javaSource) {
        if (!jarFile.exists()) return false;

        try (ZipFile zip = new ZipFile(jarFile)) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.getName().contains("$")) continue;

                String className = entry.getName()
                        .substring(entry.getName().lastIndexOf('/') + 1)
                        .replace(".class", "");

                if (className.isEmpty() || className.equals("R") || className.equals("BuildConfig")) continue;

                if (javaSource.contains(className)) return true;
            }
        } catch (Exception e) {
            return true;
        }

        return false;
    }
}
