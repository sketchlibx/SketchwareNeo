package pro.sketchware.activities.editor.gradle;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.File;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mod.hey.studios.ide.diagnostics.GradleLogParser;
import mod.sketchlibx.importer.GradleDependency;
import mod.sketchlibx.importer.GradleParser;
import pro.sketchware.utility.FileUtil;

public class GradleManagerViewModel extends AndroidViewModel {

    public enum Status {IDLE, SYNCED, STALE, SYNCING, FAILED, OFFLINE, INVALID}

    public static final class SyncUi {
        public final Status status;
        public final String message;
        public final String agpVersion;
        public final int done;
        public final int total;

        SyncUi(Status status, String message, @Nullable String agpVersion, int done, int total) {
            this.status = status;
            this.message = message;
            this.agpVersion = agpVersion;
            this.done = done;
            this.total = total;
        }
    }

    private static final Pattern AGP_CLASSPATH = Pattern.compile("com\\.android\\.tools\\.build:gradle:([0-9][A-Za-z0-9.\\-]*)");
    private static final Pattern AGP_PLUGIN = Pattern.compile("com\\.android\\.(?:application|library)['\"]\\)?\\s*version\\s*['\"]?([0-9][A-Za-z0-9.\\-]*)");
    private static final int MAX_LOG_CHARS = 200_000;

    private final MutableLiveData<SyncUi> syncUi = new MutableLiveData<>();
    private final MutableLiveData<List<GradleSyncEngine.Row>> rows = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> log = new MutableLiveData<>("");
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private final StringBuilder logBuffer = new StringBuilder();
    private final List<GradleSyncEngine.Row> liveRows = new ArrayList<>();
    private String scId;

    public GradleManagerViewModel(@NonNull Application application) {
        super(application);
    }

    public void init(String scId) {
        if (this.scId != null) return;
        this.scId = scId;
        reload();
    }

    public LiveData<SyncUi> getSyncUi() {
        return syncUi;
    }

    public LiveData<List<GradleSyncEngine.Row>> getRows() {
        return rows;
    }

    public LiveData<String> getLog() {
        return log;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void cancelSync() {
        cancel.set(true);
    }

    public void reload() {
        if (running.get()) return;
        executor.execute(() -> {
            if (running.get()) return;
            File dir = CustomGradleBuildManager.getDirectory(scId);
            File app = new File(dir, CustomGradleBuildManager.FILE_APP_BUILD);
            String content = app.isFile() ? FileUtil.readFile(app.getAbsolutePath()) : "";
            List<GradleDependency> deps = new GradleParser().parseDependencyList(content);
            List<GradleSyncEngine.Row> inspected = GradleSyncEngine.inspect(scId, deps);
            synchronized (liveRows) {
                liveRows.clear();
                liveRows.addAll(inspected);
            }
            rows.postValue(new ArrayList<>(inspected));
            syncUi.postValue(computeIdleStatus(dir, content, inspected));
        });
    }

    private SyncUi computeIdleStatus(File dir, String appContent, List<GradleSyncEngine.Row> inspected) {
        String agp = detectAgp(dir);
        List<GradleFileValidator.Issue> issues = GradleFileValidator.validate(dir);
        for (GradleFileValidator.Issue issue : issues) {
            if (issue.severity == GradleFileValidator.Severity.ERROR) {
                return new SyncUi(Status.INVALID, issue.toString(), agp, 0, 0);
            }
        }
        int needSync = 0;
        for (GradleSyncEngine.Row row : inspected) {
            if (row.isConsumed() && row.state != GradleSyncEngine.DepState.READY) needSync++;
        }
        GradleSyncEngine.Store store = GradleSyncEngine.loadStore(scId);
        boolean hashMatches = store.appHash.equals(GradleSyncEngine.hash(appContent));
        if (needSync > 0) {
            return new SyncUi(Status.STALE, needSync + " dependenc" + (needSync == 1 ? "y needs" : "ies need") + " syncing", agp, 0, 0);
        }
        if (store.status.equals("SUCCESS") && hashMatches) {
            String when = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(store.time));
            return new SyncUi(Status.SYNCED, "Last synced " + when, agp, 0, 0);
        }
        if (store.status.equals("SUCCESS")) {
            return new SyncUi(Status.STALE, "app/build.gradle changed since the last sync", agp, 0, 0);
        }
        return new SyncUi(Status.IDLE, "Project files are valid. Run Gradle Sync to verify dependencies.", agp, 0, 0);
    }

    @Nullable
    private String detectAgp(File dir) {
        for (String name : new String[]{CustomGradleBuildManager.FILE_BUILD, CustomGradleBuildManager.FILE_APP_BUILD}) {
            File file = new File(dir, name);
            if (!file.isFile()) continue;
            String text = FileUtil.readFile(file.getAbsolutePath());
            Matcher m = AGP_CLASSPATH.matcher(text);
            if (m.find()) return m.group(1);
            m = AGP_PLUGIN.matcher(text);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    public boolean startSync(@Nullable Set<String> onlyKeys) {
        if (!running.compareAndSet(false, true)) return false;
        cancel.set(false);
        executor.execute(() -> {
            File dir = CustomGradleBuildManager.getDirectory(scId);
            File app = new File(dir, CustomGradleBuildManager.FILE_APP_BUILD);
            String content = app.isFile() ? FileUtil.readFile(app.getAbsolutePath()) : "";
            String agp = detectAgp(dir);
            try {
                appendLog("--- Gradle Sync ---");
                List<GradleFileValidator.Issue> issues = GradleFileValidator.validate(dir);
                GradleFileValidator.Issue firstError = null;
                for (GradleFileValidator.Issue issue : issues) {
                    appendLog(issue.toString());
                    if (issue.severity == GradleFileValidator.Severity.ERROR && firstError == null) firstError = issue;
                }
                if (firstError != null) {
                    GradleSyncEngine.Store store = GradleSyncEngine.loadStore(scId);
                    store.status = "FAILED";
                    store.message = firstError.toString();
                    store.time = System.currentTimeMillis();
                    GradleSyncEngine.saveStore(scId, store);
                    appendLog("Sync aborted: " + GradleLogParser.parseLogs(firstError.toString()).size() + " configuration error(s)");
                    syncUi.postValue(new SyncUi(Status.INVALID, firstError.toString(), agp, 0, 0));
                    return;
                }

                List<GradleDependency> deps = new GradleParser().parseDependencyList(content);
                List<GradleSyncEngine.Row> initial = GradleSyncEngine.inspect(scId, deps);
                synchronized (liveRows) {
                    liveRows.clear();
                    liveRows.addAll(initial);
                }
                rows.postValue(new ArrayList<>(initial));
                syncUi.postValue(new SyncUi(Status.SYNCING, "Starting sync", agp, 0, deps.size()));

                GradleSyncEngine.Result result = GradleSyncEngine.sync(getApplication(), scId, deps, onlyKeys, content,
                        new GradleSyncEngine.Listener() {
                            @Override
                            public void onStatus(String message, int done, int total) {
                                syncUi.postValue(new SyncUi(Status.SYNCING, message, agp, done, total));
                            }

                            @Override
                            public void onDependencyState(String key, GradleSyncEngine.DepState state, @Nullable String error) {
                                synchronized (liveRows) {
                                    for (int i = 0; i < liveRows.size(); i++) {
                                        if (liveRows.get(i).dep.key().equals(key)) {
                                            liveRows.set(i, liveRows.get(i).withState(state, error));
                                        }
                                    }
                                    rows.postValue(new ArrayList<>(liveRows));
                                }
                            }

                            @Override
                            public void onLog(String line) {
                                appendLog(line);
                            }
                        }, cancel);

                List<GradleSyncEngine.Row> finalRows = GradleSyncEngine.inspect(scId, deps);
                synchronized (liveRows) {
                    liveRows.clear();
                    liveRows.addAll(finalRows);
                }
                rows.postValue(new ArrayList<>(finalRows));
                if (result.success) {
                    syncUi.postValue(computeIdleStatus(dir, content, finalRows));
                } else if (result.cancelled) {
                    syncUi.postValue(new SyncUi(Status.STALE, "Sync cancelled", agp, 0, 0));
                } else {
                    syncUi.postValue(new SyncUi(result.offline ? Status.OFFLINE : Status.FAILED, result.message, agp, 0, 0));
                }
            } catch (Throwable t) {
                appendLog("error: " + t);
                syncUi.postValue(new SyncUi(Status.FAILED, String.valueOf(t.getMessage() != null ? t.getMessage() : t), agp, 0, 0));
            } finally {
                running.set(false);
            }
        });
        return true;
    }

    public boolean retryFailed() {
        Set<String> keys = new HashSet<>();
        synchronized (liveRows) {
            for (GradleSyncEngine.Row row : liveRows) {
                if (row.state == GradleSyncEngine.DepState.FAILED) keys.add(row.dep.key());
            }
        }
        if (keys.isEmpty()) return false;
        return startSync(keys);
    }

    private void appendLog(String line) {
        synchronized (logBuffer) {
            logBuffer.append(line).append('\n');
            if (logBuffer.length() > MAX_LOG_CHARS) {
                logBuffer.delete(0, logBuffer.length() - MAX_LOG_CHARS / 2);
            }
            log.postValue(logBuffer.toString());
        }
    }

    @Override
    protected void onCleared() {
        cancel.set(true);
        executor.shutdown();
    }
}
