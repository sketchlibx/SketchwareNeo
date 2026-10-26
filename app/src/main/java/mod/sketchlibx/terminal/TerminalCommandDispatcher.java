package mod.sketchlibx.terminal;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import mod.hey.studios.activity.managers.cpp.InbuiltNdkManager;
import mod.sketchlibx.project.git.GitEngine;
import mod.sketchlibx.project.git.GitErrorMapper;
import mod.sketchlibx.project.git.GitSourceExporter;
import mod.sketchlibx.project.git.GitWorkspace;
import mod.sketchlibx.project.git.JGitEngine;

public final class TerminalCommandDispatcher {

    private static final long POLL_MILLIS = 250;
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terminal-command");
        thread.setDaemon(true);
        return thread;
    });
    private static final ExecutorService GIT_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "terminal-git");
        thread.setDaemon(true);
        return thread;
    });
    private static TerminalCommandServer server;

    private TerminalCommandDispatcher() {
    }

    public static File getIpcDirectory(Context context) {
        return new File(new File(context.getApplicationContext().getFilesDir(), "terminal"), "ipc");
    }

    public static synchronized void ensureStarted(Context context) {
        if (server != null && server.isRunning()) return;
        Context appContext = context.getApplicationContext();
        Handler mainHandler = new Handler(Looper.getMainLooper());
        TerminalCommandHandler handler = new TerminalCommandHandler(
                Environment.getExternalStorageDirectory(),
                TerminalSessionManager::hasSessions,
                WORKER,
                mainHandler::post,
                (url, callback) -> InbuiltNdkManager.installNdkAndCmake(appContext, url, new InbuiltNdkManager.InstallCallback() {
                    @Override
                    public void onProgress(String message, int progress, boolean isIndeterminate) {
                        callback.onProgress(message, progress, isIndeterminate);
                    }

                    @Override
                    public void onSuccess() {
                        callback.onSuccess();
                    }

                    @Override
                    public void onError(String error) {
                        callback.onError(error);
                    }
                }),
                new AndroidGitBridge(appContext),
                GIT_WORKER);
        server = new TerminalCommandServer(getIpcDirectory(appContext), handler, POLL_MILLIS);
        server.start();
    }

    private static final class AndroidGitBridge implements TerminalCommandHandler.GitBridge {
        private final Context context;
        private final GitEngine engine = new JGitEngine();

        AndroidGitBridge(Context context) {
            this.context = context;
        }

        private TerminalGitCommands.Settings settings() {
            android.content.SharedPreferences prefs = context.getSharedPreferences("git_config", Context.MODE_PRIVATE);
            return new TerminalGitCommands.Settings() {
                @Override
                public String username() {
                    return prefs.getString("name", "");
                }

                @Override
                public String token() {
                    return prefs.getString("token", "");
                }

                @Override
                public String authorName() {
                    return prefs.getString("name", "");
                }

                @Override
                public String authorEmail() {
                    return prefs.getString("email", "");
                }

                @Override
                public boolean confirmPush() {
                    return prefs.getBoolean("confirm_push", true);
                }
            };
        }

        private File workspace(String scId) {
            return GitWorkspace.directoryFor(Environment.getExternalStorageDirectory(), scId);
        }

        @Override
        public int run(String scId, List<String> args, boolean confirmed, TerminalGitCommands.Output out, TerminalGitCommands.Cancellation cancel) {
            File workspace = workspace(scId);
            TerminalGitCommands commands = new TerminalGitCommands(engine, settings(), GitErrorMapper::map,
                    () -> GitSourceExporter.regenerate(context, scId, workspace));
            return commands.run(workspace, args, confirmed, out, cancel);
        }

        @Override
        public List<String> info(String scId) {
            List<String> lines = new ArrayList<>();
            TerminalGitCommands.Settings settings = settings();
            File workspace = workspace(scId);
            lines.add("Built-in Git client: available (" + engine.describe() + ")");
            lines.add("Git workspace: " + workspace);
            if (!engine.isRepository(workspace)) {
                lines.add("Repository: not initialized (run: git init)");
            } else {
                try {
                    lines.add("Repository: initialized (branch " + engine.currentBranch(workspace) + ")");
                    String origin = "not configured";
                    for (GitEngine.Remote remote : engine.remotes(workspace)) {
                        if (remote.name.equals("origin") && !remote.urls.isEmpty()) {
                            origin = TerminalGitCommands.redact(remote.urls.get(0), settings.token());
                        }
                    }
                    lines.add("Remote origin: " + origin);
                } catch (Exception e) {
                    lines.add("Repository: present but unreadable (" + GitErrorMapper.map(e) + ")");
                }
            }
            lines.add("Credentials: " + (settings.username().isEmpty() ? "username not set" : "username set")
                    + ", " + (settings.token().isEmpty() ? "token not set" : "token set")
                    + " (Git Client > Settings)");
            return lines;
        }
    }

    public static synchronized void stop() {
        if (server != null) server.stop();
        server = null;
    }
}
