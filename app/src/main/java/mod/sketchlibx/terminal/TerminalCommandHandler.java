package mod.sketchlibx.terminal;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

public final class TerminalCommandHandler implements TerminalCommandServer.Dispatcher {

    public interface NdkInstaller {
        void install(String url, Callback callback);

        interface Callback {
            void onProgress(String message, int progress, boolean indeterminate);

            void onSuccess();

            void onError(String error);
        }
    }

    public interface GitBridge {
        int run(String scId, List<String> args, boolean confirmed, TerminalGitCommands.Output out, TerminalGitCommands.Cancellation cancel);

        List<String> info(String scId);
    }

    private final File storageRoot;
    private final Predicate<String> sessionExists;
    private final Executor worker;
    private final Executor mainThread;
    private final NdkInstaller ndkInstaller;
    private final GitBridge gitBridge;
    private final Executor gitWorker;
    private final Map<String, AtomicBoolean> activeGit = new ConcurrentHashMap<>();
    private final AtomicBoolean ndkInstallRunning = new AtomicBoolean(false);

    public TerminalCommandHandler(File storageRoot, Predicate<String> sessionExists, Executor worker, Executor mainThread, NdkInstaller ndkInstaller,
                                  GitBridge gitBridge, Executor gitWorker) {
        this.storageRoot = storageRoot;
        this.sessionExists = sessionExists;
        this.worker = worker;
        this.mainThread = mainThread;
        this.ndkInstaller = ndkInstaller;
        this.gitBridge = gitBridge;
        this.gitWorker = gitWorker;
    }

    @Override
    public void dispatch(TerminalCommandServer.Request request, TerminalReply reply) {
        String command = request.get(TerminalCommands.KEY_COMMAND);
        String scId = request.get(TerminalCommands.KEY_SC_ID);
        if (!TerminalCleanup.isValidProjectId(scId) || !sessionExists.test(scId)) {
            reply.finish(TerminalCleanup.CODE_INVALID, "This terminal is not attached to an open project.");
            return;
        }
        switch (command) {
            case TerminalCommands.COMMAND_STATUS -> reportStatus(reply, scId);
            case TerminalCommands.COMMAND_BUILD -> mainThread.execute(() -> startBuild(reply, scId));
            case TerminalCommands.COMMAND_CANCEL_BUILD -> mainThread.execute(() -> {
                TerminalBuildBridge.Host host = TerminalBuildBridge.get(scId);
                if (host != null && host.isBuildRunning()) host.cancelBuild();
                reply.finish(TerminalCleanup.CODE_OK, "Cancel requested.");
            });
            case TerminalCommands.COMMAND_CLEAN_CACHE, TerminalCommands.COMMAND_CLEAN_GRADLE_BACKUPS -> {
                boolean dryRun = request.getBoolean(TerminalCommands.KEY_DRY_RUN);
                boolean confirmed = request.getBoolean(TerminalCommands.KEY_CONFIRMED);
                worker.execute(() -> runCleanup(reply, command, scId, dryRun, confirmed));
            }
            case TerminalCommands.COMMAND_NDK_INSTALL -> {
                String url = request.get(TerminalCommands.KEY_URL);
                worker.execute(() -> installNdk(reply, url));
            }
            case TerminalCommands.COMMAND_GIT -> runGit(request, reply, scId);
            case TerminalCommands.COMMAND_CANCEL_GIT -> {
                AtomicBoolean flag = activeGit.get(scId);
                if (flag != null) flag.set(true);
                reply.finish(TerminalCleanup.CODE_OK, "Cancel requested.");
            }
            case TerminalCommands.COMMAND_GIT_INFO -> gitWorker.execute(() -> {
                try {
                    reply.lines(gitBridge.info(scId));
                    reply.finish(TerminalCleanup.CODE_OK);
                } catch (Throwable t) {
                    reply.finish(TerminalCleanup.CODE_FAILED, "Could not read the Git client state: " + t.getMessage());
                }
            });
            default -> reply.finish(TerminalCleanup.CODE_INVALID, "Unknown command: " + command);
        }
    }

    static List<String> parseArgs(TerminalCommandServer.Request request) {
        String count = request.get(TerminalCommands.KEY_ARGC);
        if (count == null || !count.matches("[0-9]{1,2}")) return null;
        int argc = Integer.parseInt(count);
        if (argc > TerminalCommands.MAX_ARGS) return null;
        List<String> args = new ArrayList<>();
        for (int i = 0; i < argc; i++) {
            String value = request.get(TerminalCommands.ARG_KEY_PREFIX + i);
            if (value == null || value.length() > TerminalCommands.MAX_ARG_LENGTH || value.indexOf('\0') >= 0) return null;
            args.add(value);
        }
        return args;
    }

    private void runGit(TerminalCommandServer.Request request, TerminalReply reply, String scId) {
        List<String> args = parseArgs(request);
        if (args == null) {
            reply.finish(TerminalCleanup.CODE_INVALID, "Malformed git arguments.");
            return;
        }
        if (!args.isEmpty() && args.get(0).equals("sync") && TerminalBuildBridge.isBuildRunning(scId)) {
            reply.finish(TerminalCommands.EXIT_BUSY, "A build is running. Wait for it to finish or cancel it first.");
            return;
        }
        AtomicBoolean cancel = new AtomicBoolean(false);
        if (activeGit.putIfAbsent(scId, cancel) != null) {
            reply.finish(TerminalCommands.EXIT_BUSY, "Another git command is still running for this project.");
            return;
        }
        boolean confirmed = request.getBoolean(TerminalCommands.KEY_CONFIRMED);
        try {
            gitWorker.execute(() -> {
                try {
                    int code = gitBridge.run(scId, args, confirmed, reply::line, cancel::get);
                    reply.finish(code);
                } catch (Throwable t) {
                    reply.finish(TerminalCleanup.CODE_FAILED, "Unexpected git error: " + t.getMessage());
                } finally {
                    activeGit.remove(scId, cancel);
                }
            });
        } catch (RuntimeException e) {
            activeGit.remove(scId, cancel);
            reply.finish(TerminalCleanup.CODE_FAILED, "The git command could not be started.");
        }
    }

    private void reportStatus(TerminalReply reply, String scId) {
        TerminalBuildBridge.Host host = TerminalBuildBridge.get(scId);
        File mysc = new File(new File(new File(storageRoot, ".sketchware"), "mysc"), scId);
        File data = new File(new File(new File(storageRoot, ".sketchware"), "data"), scId);
        reply.line("Terminal channel: OK");
        reply.line("Project ID: " + scId);
        reply.line("Project editor: " + (host == null ? "not open" : "open"));
        reply.line("Build: " + (host != null && host.isBuildRunning() ? "running" : "idle"));
        reply.line("Project data directory: " + (data.isDirectory() ? "found" : "not found"));
        reply.line("Build cache directory: " + (mysc.isDirectory() ? "found" : "not found (created by the next build)"));
        reply.finish(TerminalCleanup.CODE_OK);
    }

    private void startBuild(TerminalReply reply, String scId) {
        TerminalBuildBridge.Host host = TerminalBuildBridge.get(scId);
        if (host == null) {
            reply.finish(TerminalCommands.EXIT_NOT_AVAILABLE, "The project editor is not open, so a build cannot be started from here.");
            return;
        }
        if (host.isBuildRunning()) {
            reply.finish(TerminalCommands.EXIT_BUSY, "A build is already running for this project.");
            return;
        }
        reply.line("Starting APK build...");
        boolean started = host.startBuild(new TerminalBuildBridge.Listener() {
            private String lastProgress = "";

            @Override
            public void onProgress(String message) {
                if (message == null) return;
                String text = message.trim();
                if (text.isEmpty() || text.equals(lastProgress)) return;
                lastProgress = text;
                reply.line(text);
            }

            @Override
            public void onSuccess(String apkPath) {
                reply.line("Build succeeded.");
                if (apkPath != null) reply.line("APK: " + apkPath);
                reply.finish(TerminalCleanup.CODE_OK);
            }

            @Override
            public void onFailure(String message) {
                reply.line("Build failed.");
                reply.lines(firstLines(message, 12));
                reply.line("Open the compile log from the project editor for the full output.");
                reply.finish(TerminalCleanup.CODE_FAILED);
            }

            @Override
            public void onCanceled() {
                reply.finish(TerminalCommands.EXIT_CANCELED, "Build canceled.");
            }
        });
        if (!started) reply.finish(TerminalCommands.EXIT_BUSY, "The build could not be started.");
    }

    private void runCleanup(TerminalReply reply, String command, String scId, boolean dryRun, boolean confirmed) {
        try {
            if (TerminalBuildBridge.isBuildRunning(scId)) {
                reply.finish(TerminalCommands.EXIT_BUSY, "A build is running. Wait for it to finish or cancel it first.");
                return;
            }
            TerminalCleanup.Result result = TerminalCommands.COMMAND_CLEAN_CACHE.equals(command)
                    ? TerminalCleanup.clearMyscCache(storageRoot, scId, dryRun, confirmed)
                    : TerminalCleanup.deleteGradleBackups(storageRoot, scId, dryRun, confirmed);
            reply.lines(result.lines);
            reply.finish(result.code);
        } catch (Throwable t) {
            reply.finish(TerminalCleanup.CODE_FAILED, "Unexpected error: " + t.getMessage());
        }
    }

    private void installNdk(TerminalReply reply, String url) {
        if (!isHttpUrl(url)) {
            reply.finish(TerminalCleanup.CODE_INVALID, "Use an http:// or https:// archive URL.");
            return;
        }
        if (!ndkInstallRunning.compareAndSet(false, true)) {
            reply.finish(TerminalCommands.EXIT_BUSY, "An NDK/CMake install is already running.");
            return;
        }
        reply.line("Starting NDK/CMake install...");
        try {
            ndkInstaller.install(url, new NdkInstaller.Callback() {
                private String lastMessage = "";
                private int lastBucket = -1;

                @Override
                public void onProgress(String message, int progress, boolean indeterminate) {
                    if (message == null) return;
                    int bucket = indeterminate ? -1 : progress / 10;
                    if (message.equals(lastMessage) && (indeterminate || bucket <= lastBucket)) return;
                    lastMessage = message;
                    lastBucket = bucket;
                    reply.line(indeterminate ? message : message + " " + progress + "%");
                }

                @Override
                public void onSuccess() {
                    ndkInstallRunning.set(false);
                    reply.line("NDK/CMake install finished. Run ndk_status to see the installed versions.");
                    reply.finish(TerminalCleanup.CODE_OK);
                }

                @Override
                public void onError(String error) {
                    ndkInstallRunning.set(false);
                    reply.line("NDK/CMake install failed: " + error);
                    reply.finish(TerminalCleanup.CODE_FAILED);
                }
            });
        } catch (Throwable t) {
            ndkInstallRunning.set(false);
            reply.finish(TerminalCleanup.CODE_FAILED, "Could not start the install: " + t.getMessage());
        }
    }

    static boolean isHttpUrl(String url) {
        if (url == null || url.isEmpty() || url.length() > 2000) return false;
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> firstLines(String text, int max) {
        if (text == null || text.trim().isEmpty()) return List.of("No error details were provided.");
        String[] all = text.trim().split("\\R");
        return new ArrayList<>(List.of(all).subList(0, Math.min(all.length, max)));
    }
}
