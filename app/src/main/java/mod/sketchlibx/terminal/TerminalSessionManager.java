package mod.sketchlibx.terminal;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

/**
 * Owns real PTY {@link TerminalSession}s, multiple independent ones per project (sc_id) — each
 * "New Session" creates a genuinely separate subprocess with its own PTY, cwd and scrollback.
 * Sessions outlive any single UI surface: switching tabs, placements, or app recreation reattaches
 * to whichever sessions are still running rather than losing them.
 */
public final class TerminalSessionManager {

    public static final class Entry {
        public final String sessionId;
        public final TerminalSession session;
        public volatile String label;

        private Entry(String sessionId, String label, TerminalSession session) {
            this.sessionId = sessionId;
            this.label = label;
            this.session = session;
        }
    }

    private static final Map<String, List<Entry>> PROJECT_SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicInteger> PROJECT_COUNTERS = new ConcurrentHashMap<>();

    private TerminalSessionManager() {
    }

    public static synchronized List<Entry> getSessions(String scId) {
        return PROJECT_SESSIONS.computeIfAbsent(scId, k -> new CopyOnWriteArrayList<>());
    }

    /** Returns the first still-running session for this project, or creates one if none exist. */
    public static synchronized Entry getOrCreateFirst(Context context, String scId, TerminalSessionClient client) {
        List<Entry> sessions = getSessions(scId);
        for (Entry e : sessions) {
            if (e.session.isRunning()) return e;
        }
        return createSession(context, scId, client);
    }

    /** Always spawns a genuinely new, independent PTY — this is what "+" / "New Session" calls. */
    public static synchronized Entry createSession(Context context, String scId, TerminalSessionClient client) {
        List<Entry> sessions = getSessions(scId);
        int number = PROJECT_COUNTERS.computeIfAbsent(scId, k -> new AtomicInteger(0)).incrementAndGet();
        String sessionId = scId + "#" + number;

        TerminalSession session = spawnSession(context.getApplicationContext(), scId, client);
        Entry entry = new Entry(sessionId, "Session " + number, session);
        sessions.add(entry);
        writeInitScript(context.getApplicationContext(), session, number == 1);
        return entry;
    }

    /** Kills exactly one session; the project's other sessions are unaffected. */
    public static synchronized void killSession(String scId, String sessionId) {
        List<Entry> sessions = PROJECT_SESSIONS.get(scId);
        if (sessions == null) return;
        for (Entry e : sessions) {
            if (e.sessionId.equals(sessionId)) {
                e.session.finishIfRunning();
                sessions.remove(e);
                break;
            }
        }
    }

    public static synchronized void killAllSessions(String scId) {
        List<Entry> sessions = PROJECT_SESSIONS.remove(scId);
        if (sessions != null) {
            for (Entry e : sessions) e.session.finishIfRunning();
        }
        PROJECT_COUNTERS.remove(scId);
    }

    private static TerminalSession spawnSession(Context appContext, String scId, TerminalSessionClient client) {
        String homeDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.sketchware/data/" + scId + "/";
        File home = new File(homeDir);
        if (!home.exists()) home.mkdirs();

        List<String> binDirs = collectToolchainBinDirs(appContext);
        String shellPath = resolveShell(binDirs);
        String[] env = buildEnvironment(appContext, homeDir, binDirs);
        String[] args = {shellPath};

        return new TerminalSession(shellPath, homeDir, args, env, 4000, client);
    }

    /** A real bash from an installed toolchain is used if present; otherwise the real system shell. */
    private static String resolveShell(List<String> binDirs) {
        for (String dir : binDirs) {
            File bash = new File(dir, "bash");
            if (bash.isFile() && bash.canExecute()) return bash.getAbsolutePath();
        }
        return "/system/bin/sh";
    }

    private static List<String> collectToolchainBinDirs(Context appContext) {
        List<String> dirs = new ArrayList<>();
        String filesDir = appContext.getFilesDir().getAbsolutePath();

        for (String bin : findVersionedBinDirs(new File(filesDir, "cmake"))) {
            dirs.add(bin);
        }

        File ndkRoot = new File(filesDir, "ndk");
        File[] ndkVersions = ndkRoot.listFiles(File::isDirectory);
        if (ndkVersions != null) {
            for (File ndkVersion : ndkVersions) {
                File prebuiltRoot = new File(ndkVersion, "toolchains/llvm/prebuilt");
                File[] hostDirs = prebuiltRoot.listFiles(File::isDirectory);
                if (hostDirs != null) {
                    for (File hostDir : hostDirs) {
                        File bin = new File(hostDir, "bin");
                        if (bin.isDirectory()) dirs.add(bin.getAbsolutePath());
                    }
                }
            }
        }
        return dirs;
    }

    private static String[] buildEnvironment(Context appContext, String homeDir, List<String> binDirs) {
        List<String> env = new ArrayList<>();

        StringBuilder path = new StringBuilder();
        for (String dir : binDirs) path.append(dir).append(':');

        String systemPath = System.getenv("PATH");
        path.append(systemPath != null && !systemPath.isEmpty()
                ? systemPath
                : "/sbin:/system/sbin:/system/bin:/system/xbin");

        env.add("PATH=" + path);
        env.add("HOME=" + homeDir);
        env.add("TMPDIR=" + appContext.getCacheDir().getAbsolutePath());
        env.add("TERM=xterm-256color");
        env.add("COLORTERM=truecolor");
        env.add("LANG=en_US.UTF-8");
        env.add("PS1=\\W $ ");

        copyIfSet(env, "ANDROID_ROOT", "/system");
        copyIfSet(env, "ANDROID_DATA", "/data");
        copyIfSet(env, "ANDROID_ASSETS", "/system/app");

        return env.toArray(new String[0]);
    }

    private static void copyIfSet(List<String> env, String name, String fallback) {
        String value = System.getenv(name);
        env.add(name + "=" + (value != null && !value.isEmpty() ? value : fallback));
    }

    private static List<String> findVersionedBinDirs(File toolRoot) {
        List<String> result = new ArrayList<>();
        if (!toolRoot.isDirectory()) return result;
        File[] versionDirs = toolRoot.listFiles(File::isDirectory);
        if (versionDirs == null) return result;
        for (File versionDir : versionDirs) {
            File bin = new File(versionDir, "bin");
            if (bin.isDirectory()) result.add(bin.getAbsolutePath());
        }
        return result;
    }

    /**
     * MOTD + real ndk_status/ndk_uninstall (pure shell, self-contained) plus a genuinely working
     * ndk_install — not a message pretending to install. It sends a broadcast to
     * {@link NdkInstallReceiver}, which is real, running Java code that calls the same
     * {@link InbuiltNdkManager#installNdkAndCmake} the C/C++ Manager's own UI uses. `am` is a real
     * system binary; the broadcast runs at this app's own UID since this shell is a direct child
     * of the app process, so it reaches the receiver without any special permission.
     */
    private static void writeInitScript(Context appContext, TerminalSession session, boolean isFirstSession) {
        if (!isFirstSession) return;

        String filesDir = appContext.getFilesDir().getAbsolutePath();
        String ndkDir = filesDir + "/ndk";
        String cmakeDir = filesDir + "/cmake";
        String pkg = appContext.getPackageName();

        String script =
                "clear\n" +
                "printf '\\033[1;32m'; echo '── Sketchware Neo Terminal ──'; printf '\\033[0m'\n" +
                "command -v cmake >/dev/null 2>&1 && echo \"cmake  : $(cmake --version 2>/dev/null | head -1)\" || echo 'cmake  : not installed'\n" +
                "command -v ninja >/dev/null 2>&1 && echo \"ninja  : $(ninja --version 2>/dev/null)\" || echo 'ninja  : not installed'\n" +
                "command -v clang >/dev/null 2>&1 && echo 'clang  : installed' || echo 'clang  : not installed'\n" +
                "command -v bash  >/dev/null 2>&1 && echo 'shell  : bash'      || echo 'shell  : /system/bin/sh'\n" +
                "echo\n" +
                "NDK_DIR=\"" + ndkDir + "\"\n" +
                "CMAKE_DIR=\"" + cmakeDir + "\"\n" +
                "ndk_status() {\n" +
                "  [ -x \"$CMAKE_DIR/3.22.1/bin/cmake\" ] && echo 'cmake 3.22.1: installed' || echo 'cmake 3.22.1: not installed';\n" +
                "  found=0; for d in \"$NDK_DIR\"/*/; do [ -f \"$d/build/cmake/android.toolchain.cmake\" ] && { echo \"ndk $(basename \"$d\"): installed\"; found=1; }; done; [ \"$found\" -eq 0 ] && echo 'ndk: not installed';\n" +
                "}\n" +
                "ndk_uninstall() {\n" +
                "  rm -rf \"$NDK_DIR\" \"$CMAKE_DIR\";\n" +
                "  echo 'Removed.';\n" +
                "}\n" +
                "ndk_install() {\n" +
                "  if [ -z \"$1\" ]; then echo 'Usage: ndk_install <ndk-zip-url>'; return 1; fi\n" +
                "  am broadcast -a mod.sketchlibx.terminal.ACTION_INSTALL_NDK -n " + pkg + "/mod.sketchlibx.terminal.NdkInstallReceiver --es ndk_url \"$1\" >/dev/null 2>&1\n" +
                "  echo 'Install started in the background — run ndk_status in a bit to check progress.'\n" +
                "}\n" +
                "echo 'Commands: ndk_status, ndk_install <url>, ndk_uninstall'\n" +
                "echo\n";

        session.write(script);
    }
}
