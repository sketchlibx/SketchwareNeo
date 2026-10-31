package pro.sketchware.activities.importer;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import mod.sketchlibx.importer.ASProjectImporter;
import mod.sketchlibx.importer.AndroidStudioProjectInspector;
import mod.sketchlibx.importer.ImportLogger;
import mod.sketchlibx.importer.ImportOptions;
import mod.sketchlibx.project.git.GitWorkspace;
import mod.sketchlibx.project.git.GitSourceExporter;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.R;
import pro.sketchware.databinding.ActivityAndroidStudioImportBinding;

public class AndroidStudioImportActivity extends AppCompatActivity {
    private static final int MAX_TREE_ENTRIES = 50000;
    private static final long MAX_TREE_BYTES = 1024L * 1024L * 1024L;
    private static final String[] SKIP_DIRS = {".git", ".gradle", ".idea", "build", ".cxx", "captures", "intermediates", "tmp"};

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private File sourceZip;
    private String repositoryUrl = "";
    private volatile File clonedRepositoryDir;
    private String clonedAccessToken = "";
    private String clonedAuthUsername = "";
    private AndroidStudioProjectInspector.Report report;
    private boolean working;

    private ActivityAndroidStudioImportBinding binding;

    private final ActivityResultLauncher<Intent> zipPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null && result.getData().getData() != null) {
                    copyUriAndInspect(result.getData().getData());
                }
            });
    private final ActivityResultLauncher<Uri> folderPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(), uri -> {
                if (uri != null) copyTreeAndInspect(uri);
            });

    @Override
    public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        binding = ActivityAndroidStudioImportBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setupUi();
    }

    private void setupUi() {
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (working) toast("Please wait for the current operation to finish.");
            else finish();
        });

        binding.cardFolder.setOnClickListener(v -> {
            if (!working) folderPicker.launch(null);
        });

        binding.cardZip.setOnClickListener(v -> {
            if (!working) launchZipPicker();
        });

        binding.gitHeader.setOnClickListener(v -> {
            if (working) return;
            boolean expanded = binding.gitFields.getVisibility() == View.VISIBLE;
            binding.gitFields.setVisibility(expanded ? View.GONE : View.VISIBLE);
            binding.gitExpandIcon.animate().rotation(expanded ? 0f : 180f).setDuration(180).start();
        });

        binding.cloneButton.setOnClickListener(v -> cloneRepository());

        binding.methodsToMoreBlocks.setEnabled(binding.javaToBlocks.isChecked());
        binding.javaToBlocks.setOnCheckedChangeListener((button, checked) -> {
            binding.methodsToMoreBlocks.setEnabled(checked);
            binding.rowMethodsToMoreBlocks.setAlpha(checked ? 1f : 0.5f);
            if (!checked) binding.methodsToMoreBlocks.setChecked(false);
        });

        binding.rowJavaToBlocks.setOnClickListener(v -> {
            if (!working) binding.javaToBlocks.toggle();
        });
        binding.rowXmlToViews.setOnClickListener(v -> {
            if (!working) binding.xmlToViews.toggle();
        });
        binding.rowMethodsToMoreBlocks.setOnClickListener(v -> {
            if (!working && binding.methodsToMoreBlocks.isEnabled()) binding.methodsToMoreBlocks.toggle();
        });
        binding.rowGradleDependencies.setOnClickListener(v -> {
            if (!working) binding.gradleDependencies.toggle();
        });

        binding.importButton.setOnClickListener(v -> startImport());
    }

    private void launchZipPicker() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        pick.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
        try {
            zipPicker.launch(pick);
        } catch (Exception e) {
            toast("No file picker is available.");
        }
    }

    private void copyUriAndInspect(Uri uri) {
        clearPreviousSelection();
        repositoryUrl = "";
        runWorking("Copying selected ZIP…", () -> {
            File dest = new File(getCacheDir(), "as_selected_" + System.nanoTime() + ".zip");
            try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(dest)) {
                if (in == null) throw new java.io.IOException("The selected file could not be opened.");
                copyStream(in, out, MAX_TREE_BYTES);
            } catch (Exception e) {
                deleteRecursively(dest);
                throw e;
            }
            try {
                inspectStagedZip(dest, queryDisplayName(uri), "ZIP archive selected");
            } catch (Exception e) {
                deleteRecursively(dest);
                throw e;
            }
        });
    }

    private void copyTreeAndInspect(Uri treeUri) {
        clearPreviousSelection();
        repositoryUrl = "";
        runWorking("Reading selected project folder…", () -> {
            File staging = new File(getCacheDir(), "as_folder_" + System.nanoTime());
            File zip = new File(getCacheDir(), "as_folder_" + System.nanoTime() + ".zip");
            if (!staging.mkdirs() && !staging.isDirectory()) throw new java.io.IOException("Cannot create temporary project directory.");
            try {
                try {
                    getContentResolver().takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                TreeCopyState state = new TreeCopyState();
                copyDocumentTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri), staging, state, 0);
                if (state.files == 0) throw new java.io.IOException("The selected folder appears to be empty.");
                zipDirectory(staging, zip);
                inspectStagedZip(zip, "Selected Folder", "Project folder selected and inspected");
            } catch (Exception e) {
                deleteRecursively(zip);
                throw e;
            } finally {
                deleteRecursively(staging);
            }
        });
    }

    private void cloneRepository() {
        if (working || binding == null) return;
        String url = value(binding.gitUrl);
        String branch = value(binding.gitBranch);
        String tag = value(binding.gitTag);
        String token = value(binding.gitToken);

        binding.gitUrlLayout.setError(null);
        binding.gitTagLayout.setError(null);

        if (url.isEmpty()) {
            binding.gitUrlLayout.setError("Repository URL is required");
            return;
        }
        if (!url.regionMatches(true, 0, "https://", 0, 8) || url.contains(" ")) {
            binding.gitUrlLayout.setError("Use a valid HTTPS repository URL");
            return;
        }
        if (url.matches("(?i)^https?://[^/]*@.*")) {
            binding.gitUrlLayout.setError("Do not embed credentials in the URL; use the token field");
            return;
        }
        if (!branch.isEmpty() && !tag.isEmpty()) {
            binding.gitTagLayout.setError("Choose a branch or tag, not both");
            return;
        }

        clearPreviousSelection();
        repositoryUrl = safeRepositoryUrl(url);
        clonedAccessToken = token;
        clonedAuthUsername = url.toLowerCase(Locale.ROOT).contains("gitlab") ? "oauth2" : "x-access-token";
        runWorking("Cloning repository…", () -> {
            File repo = new File(getCacheDir(), "as_git_" + System.nanoTime());
            File zip = new File(getCacheDir(), "as_git_archive_" + System.nanoTime() + ".zip");
            try {
                CloneCommand command = Git.cloneRepository().setURI(url).setDirectory(repo)
                        .setTimeout(60)
                        .setCloneAllBranches(!tag.isEmpty());
                if (!branch.isEmpty()) command.setBranch(branch.startsWith("refs/heads/") ? branch : "refs/heads/" + branch);
                if (!token.isEmpty()) {
                    String authUser = url.toLowerCase(Locale.ROOT).contains("gitlab") ? "oauth2" : "x-access-token";
                    command.setCredentialsProvider(new UsernamePasswordCredentialsProvider(authUser, token));
                }
                command.setProgressMonitor(new ProgressMonitor() {
                    @Override public void start(int totalTasks) { updateStatus("Connecting to repository…"); }
                    @Override public void beginTask(String title, int totalWork) { updateStatus(title == null ? "Cloning repository…" : title); }
                    @Override public void update(int completed) { }
                    @Override public void endTask() { }
                    @Override public void showDuration(boolean enabled) { }
                    @Override public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
                });
                try (Git git = command.call()) {
                    if (!tag.isEmpty()) {
                        git.fetch().setTimeout(60).setTagOpt(TagOpt.FETCH_TAGS).call();
                        String ref = tag.startsWith("refs/tags/") ? tag : "refs/tags/" + tag;
                        if (git.getRepository().findRef(ref) == null) throw new java.io.IOException("Tag not found: " + tag);
                        git.checkout().setName(ref).setForced(true).call();
                    }
                }
                clonedRepositoryDir = repo;
                zipDirectory(repo, zip);
                String name = repositoryNameFromUrl(url);
                inspectStagedZip(zip, name, "Cloned: " + name + " (" + safeRepositoryUrl(url) + ")");
            } catch (Exception e) {
                deleteRecursively(zip);
                deleteRecursively(repo);
                if (clonedRepositoryDir == repo) clonedRepositoryDir = null;
                throw e;
            }
        });
    }

    private void clearPreviousSelection() {
        if (clonedRepositoryDir != null) {
            deleteRecursively(clonedRepositoryDir);
            clonedRepositoryDir = null;
        }
        clonedAccessToken = "";
        clonedAuthUsername = "";
        File previous = sourceZip;
        sourceZip = null;
        if (previous != null) deleteRecursively(previous);
        report = null;
        if (binding != null) {
            binding.importButton.setEnabled(false);
            binding.sourceStatus.setText("Preparing project…");
            binding.detailsCard.setVisibility(View.GONE);
        }
    }

    private static String safeRepositoryUrl(String url) {
        if (url == null) return "";
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        int end = url.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return url.substring(0, end);
    }

    private static String repositoryNameFromUrl(String url) {
        String clean = safeRepositoryUrl(url);
        while (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        int slash = clean.lastIndexOf('/');
        String name = slash >= 0 ? clean.substring(slash + 1) : "Android Studio Project";
        if (name.toLowerCase(Locale.ROOT).endsWith(".git")) name = name.substring(0, name.length() - 4);
        return name.isEmpty() ? "Android Studio Project" : name;
    }

    private void inspectStagedZip(File zip, String displayName, String status) throws Exception {
        updateStatus("Inspecting Android project structure…");
        AndroidStudioProjectInspector.Report result = AndroidStudioProjectInspector.inspect(getApplicationContext(), zip, displayName);
        if (!result.valid) throw new java.io.IOException(result.error);
        result.repositoryUrl = repositoryUrl;
        sourceZip = zip;
        report = result;
        main.post(() -> renderReport(status));
    }

    private void renderReport(String status) {
        if (isFinishing() || isDestroyed() || report == null || binding == null) return;
        binding.sourceStatus.setText(status);
        binding.projectName.setText(report.projectName);
        binding.projectMeta.setText(report.packageName + "\nVersion " + report.versionName + " (" + report.versionCode + ") · Compile SDK " + report.compileSdk + " · Min " + report.minSdk + " · Target " + report.targetSdk + " · Java " + report.javaVersion + " · ViewBinding " + (report.viewBinding ? "On" : "Off"));
        binding.componentChips.removeAllViews();
        addChip("Activities", report.activities);
        addChip("Layouts", report.layouts);
        addChip("Drawables", report.drawables);
        addChip("Classes", report.classes);
        addChip("Drawers", report.drawers);
        addChip("Dependencies", report.dependencies.size());
        if (report.fragments > 0) addChip("Fragments", report.fragments);
        if (report.services > 0) addChip("Services", report.services);
        if (report.receivers > 0) addChip("Receivers", report.receivers);

        StringBuilder deps = new StringBuilder("Dependencies found:\n");
        if (report.dependencies.isEmpty()) {
            deps.append("• No direct dependencies detected");
        } else {
            int limit = Math.min(report.dependencies.size(), 40);
            for (int i = 0; i < limit; i++) deps.append("• ").append(report.dependencies.get(i)).append('\n');
            if (report.dependencies.size() > limit) deps.append("• …and ").append(report.dependencies.size() - limit).append(" more");
        }
        binding.dependencyText.setText(deps.toString().trim());

        StringBuilder warnings = new StringBuilder();
        if (report.warnings.isEmpty()) {
            warnings.append("Preflight checks passed. Build compatibility still depends on third-party/native code.");
            binding.warningText.setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant));
        } else {
            warnings.append("Review before import:\n");
            for (String warning : report.warnings) warnings.append("• ").append(warning).append('\n');
            binding.warningText.setTextColor(color(R.attr.colorError));
        }
        binding.warningText.setText(warnings.toString().trim());
        binding.detailsCard.setVisibility(View.VISIBLE);
        binding.statusText.setText("Inspection complete. Unsupported source will be preserved and reported where possible.");
        setWorking(false);
    }

    private void addChip(String label, int count) {
        if (binding == null) return;
        Chip chip = new Chip(this);
        chip.setText(label + "  " + count);
        chip.setCheckable(false);
        chip.setClickable(false);
        chip.setEnsureMinTouchTargetSize(false);
        chip.setTextSize(12f);
        chip.setChipBackgroundColor(ColorStateList.valueOf(color(com.google.android.material.R.attr.colorSecondaryContainer)));
        chip.setTextColor(color(com.google.android.material.R.attr.colorOnSecondaryContainer));
        binding.componentChips.addView(chip);
    }

    private void startImport() {
        if (working || sourceZip == null || report == null || !report.valid || binding == null) return;
        ImportOptions options = new ImportOptions();
        options.convertJavaToBlocks = binding.javaToBlocks.isChecked();
        options.convertXmlLayoutsToVisualViews = binding.xmlToViews.isChecked();
        options.extractCustomMethodsToMoreBlocks = binding.methodsToMoreBlocks.isChecked();
        options.importGradleDependencies = binding.gradleDependencies.isChecked();
        setWorking(true);
        new ASProjectImporter(this, sourceZip.getAbsolutePath(), ImportLogger.create(false), options, new ASProjectImporter.Callback() {
            @Override
            public void onProgress(String message) {
                if (binding != null) {
                    binding.statusText.setText(message);
                    binding.loadingOverlayText.setText(message);
                }
            }

            @Override
            public void onSuccess(String scId) {
                File cloned = clonedRepositoryDir;
                if (cloned == null || !new File(cloned, ".git").isDirectory()) {
                    setWorking(false);
                    setResult(Activity.RESULT_OK, new Intent().putExtra("sc_id", scId));
                    Toast.makeText(AndroidStudioImportActivity.this, "Project imported successfully", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (binding != null) {
                    binding.statusText.setText("Connecting Git repository to the imported project…");
                    binding.loadingOverlayText.setText("Connecting Git repository…");
                }
                io.execute(() -> {
                    String failure = null;
                    File workspace = GitWorkspace.directoryFor(new File(FileUtil.getExternalStorageDir()), scId);
                    try {
                        if (workspace.exists()) throw new java.io.IOException("A Git workspace already exists for this project.");
                        if (!workspace.mkdirs() && !workspace.isDirectory()) throw new java.io.IOException("Cannot create Git workspace.");
                        copyRepositoryFiles(cloned, workspace);
                        copyDirectory(new File(cloned, ".git"), new File(workspace, ".git"));
                        GitSourceExporter.regenerate(AndroidStudioImportActivity.this, scId, workspace);
                        if (!clonedAccessToken.isEmpty()) {
                            getSharedPreferences("git_config", MODE_PRIVATE).edit()
                                    .putString("token", clonedAccessToken)
                                    .putString("git_username", clonedAuthUsername)
                                    .apply();
                        }
                    } catch (Exception e) {
                        failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        FileUtil.deleteFile(workspace.getAbsolutePath());
                    }
                    final String gitError = failure;
                    main.post(() -> {
                        setWorking(false);
                        setResult(Activity.RESULT_OK, new Intent().putExtra("sc_id", scId).putExtra("git_initialized", gitError == null));
                        String message = gitError == null
                                ? "Project imported and Git repository connected. Open Git Client to manage Changes, History, Branches, Remotes, Settings, Pull and Push."
                                : "Project imported, but Git setup failed: " + gitError;
                        Toast.makeText(AndroidStudioImportActivity.this, message, Toast.LENGTH_LONG).show();
                        deleteRecursively(cloned);
                        clonedRepositoryDir = null;
                        finish();
                    });
                });
            }

            @Override
            public void onFailure(String reason) {
                setWorking(false);
                if (binding != null) binding.statusText.setText("Import failed.");
                new MaterialAlertDialogBuilder(AndroidStudioImportActivity.this)
                        .setTitle("Import Failed")
                        .setMessage(reason == null ? "Unknown import error." : reason)
                        .setPositiveButton("OK", null)
                        .show();
            }
        }).execute();
    }

    private void runWorking(String message, RunnableWithException job) {
        if (working) return;
        setWorking(true);
        updateStatus(message);
        io.execute(() -> {
            try {
                job.run();
            } catch (Exception e) {
                main.post(() -> {
                    if (isFinishing() || isDestroyed()) {
                        setWorking(false);
                        return;
                    }
                    setWorking(false);
                    String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    if (binding != null) binding.statusText.setText("Could not prepare project.");
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("Project Preparation Failed")
                            .setMessage(msg)
                            .setPositiveButton("OK", null)
                            .show();
                });
            }
        });
    }

    private void setWorking(boolean active) {
        working = active;
        if (binding == null) return;
        binding.progress.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.spinner.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.loadingOverlay.setVisibility(active ? View.VISIBLE : View.GONE);
        binding.importButton.setEnabled(!active && sourceZip != null && report != null && report.valid);
        binding.cloneButton.setEnabled(!active);
        if (active) {
            String message = "Working… Please keep this screen open.";
            binding.statusText.setText(message);
            binding.loadingOverlayText.setText(message);
        }
    }

    private void updateStatus(String text) {
        main.post(() -> {
            if (!isFinishing() && !isDestroyed() && binding != null) {
                binding.statusText.setText(text);
                binding.loadingOverlayText.setText(text);
            }
        });
    }

    private void copyDocumentTree(Uri treeUri, String documentId, File dest, TreeCopyState state, int depth) throws Exception {
        if (depth > 80) throw new java.io.IOException("Folder nesting is too deep; stopped for safety.");
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
        String[] columns = {DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE};
        try (Cursor cursor = getContentResolver().query(childrenUri, columns, null, null, null)) {
            if (cursor == null) throw new java.io.IOException("Could not list this folder. Try another file provider.");
            while (cursor.moveToNext()) {
                if (++state.entries > MAX_TREE_ENTRIES) throw new java.io.IOException("Folder contains too many entries (limit " + MAX_TREE_ENTRIES + ").");
                String id = cursor.getString(0);
                String name = safeName(cursor.getString(1));
                String mime = cursor.getString(2);
                if (name.isEmpty() || ".".equals(name) || "..".equals(name)) continue;
                boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(mime);
                if (directory && shouldSkipDirectory(name)) continue;
                Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                File out = new File(dest, name);
                String base = dest.getCanonicalPath() + File.separator;
                if (!out.getCanonicalPath().startsWith(base)) throw new java.io.IOException("Unsafe path in selected folder.");
                if (directory) {
                    if (!out.mkdirs() && !out.isDirectory()) throw new java.io.IOException("Could not create folder " + name);
                    copyDocumentTree(treeUri, id, out, state, depth + 1);
                } else {
                    try (InputStream in = getContentResolver().openInputStream(childUri); OutputStream output = new FileOutputStream(out)) {
                        if (in == null) throw new java.io.IOException("Cannot read " + name);
                        state.bytes += copyStream(in, output, MAX_TREE_BYTES - state.bytes);
                    }
                    state.files++;
                }
            }
        }
    }

    private File zipDirectory(File folder, File zipFile) throws Exception {
        String root = folder.getCanonicalPath() + File.separator;
        byte[] buffer = new byte[16384];
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile))) {
            List<File> files = new ArrayList<>();
            gatherFiles(folder, files);
            if (files.size() > MAX_TREE_ENTRIES) throw new java.io.IOException("Project contains too many files (limit " + MAX_TREE_ENTRIES + ").");
            long rawBytes = 0;
            for (File file : files) {
                long length = file.length();
                if (length < 0 || rawBytes > MAX_TREE_BYTES - length) throw new java.io.IOException("Project exceeds the 1 GB preparation limit.");
                rawBytes += length;
            }
            Collections.sort(files, (a, b) -> a.getAbsolutePath().compareToIgnoreCase(b.getAbsolutePath()));
            for (File file : files) {
                String relative = file.getCanonicalPath().substring(root.length()).replace(File.separatorChar, '/');
                if (relative.isEmpty() || relative.startsWith("../")) continue;
                out.putNextEntry(new ZipEntry(relative));
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                }
                out.closeEntry();
            }
        }
        if (!zipFile.isFile() || zipFile.length() == 0) throw new java.io.IOException("Prepared project archive is empty.");
        return zipFile;
    }

    private void gatherFiles(File dir, List<File> files) throws java.io.IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new java.io.IOException("Cannot read project directory " + dir.getName());
        for (File child : children) {
            if (child.isDirectory()) {
                if (!shouldSkipDirectory(child.getName())) gatherFiles(child, files);
            } else if (child.isFile()) {
                files.add(child);
            }
        }
    }

    private static boolean shouldSkipDirectory(String name) {
        for (String skip : SKIP_DIRS) if (skip.equalsIgnoreCase(name)) return true;
        return false;
    }

    private static String safeName(String name) {
        return name == null ? "" : name.replace("/", "_").replace("\\", "_").replace(String.valueOf((char) 0), "").trim();
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{"_display_name"}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (Exception ignored) {
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Project ZIP" : last;
    }

    private long copyStream(InputStream in, OutputStream out, long maxBytes) throws Exception {
        byte[] buffer = new byte[16384];
        long total = 0;
        int count;
        while ((count = in.read(buffer)) != -1) {
            total += count;
            if (total > maxBytes) throw new java.io.IOException("Project exceeds the 1 GB preparation limit.");
            out.write(buffer, 0, count);
        }
        return total;
    }

    private String value(TextInputEditText edit) {
        return edit == null || edit.getText() == null ? "" : edit.getText().toString().trim();
    }

    private int color(int attr) {
        return MaterialColors.getColor(getWindow().getDecorView(), attr);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (working) {
            toast("Please wait for the current operation to finish.");
            return;
        }
        super.onBackPressed();
    }

    @Override
    public void onDestroy() {
        cancelled.set(true);
        io.shutdownNow();
        if (!working && sourceZip != null) deleteRecursively(sourceZip);
        if (clonedRepositoryDir != null && isFinishing()) {
            deleteRecursively(clonedRepositoryDir);
            clonedRepositoryDir = null;
        }
        binding = null;
        super.onDestroy();
    }

    private void copyRepositoryFiles(File source, File target) throws Exception {
        File[] children = source.listFiles();
        if (children == null) throw new java.io.IOException("Cannot read cloned repository files.");
        for (File child : children) {
            if (".git".equals(child.getName()) || (child.isDirectory() && shouldSkipDirectory(child.getName()))) continue;
            File destination = new File(target, child.getName());
            if (child.isDirectory()) {
                if (!destination.exists() && !destination.mkdirs()) throw new java.io.IOException("Cannot create " + child.getName());
                copyRepositoryFiles(child, destination);
            } else {
                File parent = destination.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Cannot create repository directory.");
                try (InputStream in = new BufferedInputStream(new FileInputStream(child)); OutputStream out = new FileOutputStream(destination)) {
                    copyStream(in, out, MAX_TREE_BYTES);
                }
            }
        }
    }

    private void copyDirectory(File source, File target) throws Exception {
        if (!source.exists()) throw new java.io.IOException("Git metadata is missing from the cloned repository.");
        if (source.isDirectory()) {
            if (!target.exists() && !target.mkdirs()) throw new java.io.IOException("Cannot create Git metadata directory.");
            File[] children = source.listFiles();
            if (children == null) throw new java.io.IOException("Cannot read Git metadata directory.");
            for (File child : children) copyDirectory(child, new File(target, child.getName()));
        } else {
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Cannot create Git metadata directory.");
            try (InputStream in = new BufferedInputStream(new FileInputStream(source)); OutputStream out = new FileOutputStream(target)) {
                copyStream(in, out, MAX_TREE_BYTES);
            }
        }
    }

    private static boolean deleteRecursively(File target) {
        if (target == null || !target.exists()) return true;
        boolean success = true;
        if (target.isDirectory()) {
            File[] children = target.listFiles();
            if (children == null) {
                success = false;
            } else {
                for (File child : children) {
                    if (!deleteRecursively(child)) success = false;
                }
            }
        }
        if (target.exists() && !target.delete()) success = false;
        return success;
    }

    private interface RunnableWithException {
        void run() throws Exception;
    }

    private static final class TreeCopyState {
        int entries;
        int files;
        long bytes;
    }
}
