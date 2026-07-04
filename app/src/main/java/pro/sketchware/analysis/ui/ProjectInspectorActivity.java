package pro.sketchware.analysis.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import pro.sketchware.analysis.bootstrap.AnalysisModuleBootstrap;
import pro.sketchware.analysis.core.AnalysisEngine;
import pro.sketchware.analysis.model.AnalysisReport;
import pro.sketchware.utility.FilePathUtil;

public class ProjectInspectorActivity extends AppCompatActivity {

    public static final String EXTRA_SC_ID = "sc_id";

    private String scId;
    private TextView scoreText;
    private RecyclerView recyclerView;
    private IssueAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(pro.sketchware.R.layout.activity_project_inspector);

        AnalysisModuleBootstrap.registerBuiltIns();

        scId = getIntent().getStringExtra(EXTRA_SC_ID);

        Toolbar toolbar = findViewById(pro.sketchware.R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> onBackPressed());

        scoreText = findViewById(pro.sketchware.R.id.text_project_score);
        recyclerView = findViewById(pro.sketchware.R.id.recycler_issues);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new IssueAdapter(new ArrayList<>());
        recyclerView.setAdapter(adapter);

        findViewById(pro.sketchware.R.id.btn_rescan).setOnClickListener(v -> runScan());

        runScan();
    }

    private void runScan() {
        scoreText.setText("Scanning...");

        Executors.newSingleThreadExecutor().execute(() -> {
            FilePathUtil paths = new FilePathUtil();
            List<File> roots = new ArrayList<>();
            roots.add(new File(paths.getPathJava(scId)));
            roots.add(new File(paths.getPathResource(scId)));
            roots.add(new File(paths.getPathCpp(scId)));

            AnalysisEngine engine = new AnalysisEngine(scId);
            AnalysisReport report = engine.run(roots);

            new Handler(Looper.getMainLooper()).post(() -> renderReport(report));
        });
    }

    private void renderReport(AnalysisReport report) {
        scoreText.setText("Project Score: " + report.getScore().getOverallPercent() + "%");
        adapter.update(report.getIssues());
    }
}
