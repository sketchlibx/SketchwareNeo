package mod.sketchlibx.terminal;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;

import mod.hey.studios.activity.managers.cpp.InbuiltNdkManager;

/**
 * Manages independent terminal sessions per project and preserves running sessions across UI changes and app recreation.
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

    private static String normalizeProjectId(String scId) {
        if (scId == null || scId.trim().isEmpty()) return "unknown";
        String value = scId.trim().replaceAll("[^A-Za-z0-9_-]", "_");
        if (value.length() > 64) value = value.substring(0, 64);
        return value.isEmpty() ? "unknown" : value;
    }

    public static synchronized boolean hasSessions(String scId) {
        List<Entry> sessions = PROJECT_SESSIONS.get(normalizeProjectId(scId));
        return sessions != null && !sessions.isEmpty();
    }
    
    /** Stops all sessions for a project and deletes its startup script. */
    public static synchronized void releaseProject(Context context, String scId) {
        String projectId = normalizeProjectId(scId);
        killAllSessions(projectId);
        deleteInitScript(context.getApplicationContext(), projectId);
    }

    /** Stops sessions belonging to projects other than the active project. */
    public static synchronized void releaseAllExcept(Context context, String activeScId) {
        String activeId = normalizeProjectId(activeScId);
        for (String projectId : new ArrayList<>(PROJECT_SESSIONS.keySet())) {
            if (!projectId.equals(activeId)) releaseProject(context, projectId);
        }
    }

    private static void deleteInitScript(Context appContext, String projectId) {
        File initFile = new File(new File(appContext.getFilesDir(), "terminal"), "session-init-" + projectId + ".sh");
        if (initFile.isFile() && !initFile.delete()) {
            android.util.Log.w("NeoTerminal", "Could not delete terminal helper script " + initFile.getName());
        }
    }

    public static synchronized List<Entry> getSessions(String scId) {
        String projectId = normalizeProjectId(scId);
        return PROJECT_SESSIONS.computeIfAbsent(projectId, k -> new CopyOnWriteArrayList<>());
    }

    /** Returns a running session or creates one if none exists. */
    public static synchronized Entry getOrCreateFirst(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = getSessions(projectId);
        for (Entry e : sessions) {
            if (e.session.isRunning()) return e;
        }
        return createSession(context, projectId, client);
    }

    /** Creates a new independent terminal session. */
    public static synchronized Entry createSession(Context context, String scId, TerminalSessionClient client) {
        String projectId = normalizeProjectId(scId);
        Context appContext = context.getApplicationContext();
        List<Entry> sessions = getSessions(projectId);
        int number = PROJECT_COUNTERS.computeIfAbsent(projectId, k -> new AtomicInteger(0)).incrementAndGet();
        String sessionId = projectId + "#" + number;
        
        // Prepare the startup script before launching the shell to avoid initialization
        File initScript = writeInitScript(appContext, projectId);
        TerminalSession session = spawnSession(appContext, projectId, client, initScript);
        Entry entry = new Entry(sessionId, "Session " + number, session);
        sessions.add(entry);
        TerminalCommandDispatcher.ensureStarted(appContext);
        return entry;
    }

    /** Stops one session without affecting others */
    public static synchronized void killSession(String scId, String sessionId) {
        List<Entry> sessions = PROJECT_SESSIONS.get(normalizeProjectId(scId));
        if (sessions == null) return;
        for (Entry e : sessions) {
            if (e.sessionId.equals(sessionId)) {
                e.session.finishIfRunning();
                sessions.remove(e);
                break;
            }
        }
        stopCommandServerIfIdle();
    }

    private static boolean hasAnySession() {
        for (List<Entry> list : PROJECT_SESSIONS.values()) {
            if (!list.isEmpty()) return true;
        }
        return false;
    }

    private static void stopCommandServerIfIdle() {
        if (!hasAnySession()) TerminalCommandDispatcher.stop();
    }

    public static synchronized void killAllSessions(String scId) {
        String projectId = normalizeProjectId(scId);
        List<Entry> sessions = PROJECT_SESSIONS.remove(projectId);
        if (sessions != null) {
            for (Entry e : sessions) e.session.finishIfRunning();
        }
        PROJECT_COUNTERS.remove(projectId);
        stopCommandServerIfIdle();
    }

    private static TerminalSession spawnSession(Context appContext, String scId, TerminalSessionClient client, File initScript) {
        String homeDir = Environment.getExternalStorageDirectory().getAbsolutePath()
                + "/.sketchware/data/" + scId + "/";
        File home = new File(homeDir);
        if (!home.exists()) home.mkdirs();

        List<String> binDirs = collectToolchainBinDirs(appContext);
        String shellPath = resolveShell(binDirs);
        String[] env = buildEnvironment(appContext, scId, homeDir, binDirs, initScript);
        String[] args = {shellPath, "-i"};

        return new TerminalSession(shellPath, homeDir, args, env, 4000, client);
    }

    /**
 * Creates shell helpers for terminal commands and app requests.
 */
    private static String resolveShell(List<String> binDirs) {
        for (String dir : binDirs) {
            File bash = new File(dir, "bash");
            if (bash.isFile() && bash.canExecute()) return bash.getAbsolutePath();
        }
        return "/system/bin/sh";
    }

    private static List<String> collectToolchainBinDirs(Context appContext) {
        Set<String> dirs = new LinkedHashSet<>();
        File filesDir = appContext.getFilesDir();
        File cmakeRoot = new File(filesDir, "cmake");
        for (String binPath : findVersionedBinDirs(cmakeRoot)) {
            File bin = new File(binPath);
            if (isExecutable(new File(bin, "cmake")) || isExecutable(new File(bin, "ninja"))) {
                dirs.add(bin.getAbsolutePath());
            }
        }

        File ndkRoot = new File(filesDir, "ndk");
        File[] ndkVersions = ndkRoot.listFiles(File::isDirectory);
        if (ndkVersions != null) {
            for (File ndkVersion : ndkVersions) {
                if (!new File(ndkVersion, "build/cmake/android.toolchain.cmake").isFile()) continue;
                File prebuiltRoot = new File(ndkVersion, "toolchains/llvm/prebuilt");
                File[] hostDirs = prebuiltRoot.listFiles(File::isDirectory);
                if (hostDirs == null) continue;
                for (File hostDir : hostDirs) {
                    String host = hostDir.getName().toLowerCase(Locale.ROOT);

                    if (!(host.equals("linux-aarch64") || host.equals("linux-arm64")
                            || host.equals("android-arm64") || host.equals("android-aarch64"))) {
                        continue;
                    }
                    File bin = new File(hostDir, "bin");
                    if (bin.isDirectory() && (isExecutable(new File(bin, "clang"))
                            || isExecutable(new File(bin, "clang++")))) {
                        dirs.add(bin.getAbsolutePath());
                    }
                }
            }
        }
        return new ArrayList<>(dirs);
    }

    private static boolean isExecutable(File file) {
        return file.isFile() && file.canExecute();
    }

    private static String[] buildEnvironment(Context appContext, String scId, String homeDir, List<String> binDirs, File initScript) {
        List<String> env = new ArrayList<>();
        Set<String> basePathEntries = new LinkedHashSet<>();
        String systemPath = System.getenv("PATH");
        if (systemPath != null && !systemPath.trim().isEmpty()) {
            for (String entry : systemPath.split(":")) {
                if (!entry.trim().isEmpty()) basePathEntries.add(entry.trim());
            }
        }

        basePathEntries.add("/system/bin");
        basePathEntries.add("/system/xbin");
        basePathEntries.add("/vendor/bin");
        basePathEntries.add("/vendor/xbin");
        basePathEntries.add("/product/bin");
        basePathEntries.add("/apex/com.android.runtime/bin");
        basePathEntries.add("/sbin");
        basePathEntries.add("/system/sbin");

        Set<String> allPathEntries = new LinkedHashSet<>(binDirs);
        allPathEntries.addAll(basePathEntries);
        env.add("PATH=" + joinPath(allPathEntries));
        env.add("SW_BASE_PATH=" + joinPath(basePathEntries));
        env.add("HOME=" + homeDir);
        env.add("PWD=" + homeDir);
        env.add("SW_PROJECT_DIR=" + homeDir);
        env.add("SW_PROJECT_ID=" + scId);
        env.add("SW_IPC=" + TerminalCommandDispatcher.getIpcDirectory(appContext).getAbsolutePath());
        env.add("TMPDIR=" + appContext.getCacheDir().getAbsolutePath());
        env.add("TERM=xterm-256color");
        env.add("COLORTERM=truecolor");
        env.add("LANG=en_US.UTF-8");
        env.add("PS1=\\W $ ");

        if (initScript != null && initScript.isFile()) env.add("ENV=" + initScript.getAbsolutePath());
        copyIfSet(env, "ANDROID_ROOT", "/system");
        copyIfSet(env, "ANDROID_DATA", "/data");
        copyIfSet(env, "ANDROID_ASSETS", "/system/app");

        File installedNdk = InbuiltNdkManager.getInstalledNdkDir(appContext);
        if (installedNdk != null && new File(installedNdk, "build/cmake/android.toolchain.cmake").isFile()) {
            env.add("ANDROID_NDK_HOME=" + installedNdk.getAbsolutePath());
            env.add("ANDROID_NDK_ROOT=" + installedNdk.getAbsolutePath());
            env.add("NDK_HOME=" + installedNdk.getAbsolutePath());
        }
        File cmake = new File(appContext.getFilesDir(), "cmake/3.22.1");
        if (isExecutable(new File(cmake, "bin/cmake"))) env.add("CMAKE_HOME=" + cmake.getAbsolutePath());
        return env.toArray(new String[0]);
    }

    private static String joinPath(Set<String> entries) {
        StringBuilder path = new StringBuilder();
        for (String entry : entries) {
            if (path.length() > 0) path.append(':');
            path.append(entry);
        }
        return path.toString();
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


    static String buildInitScript(String filesDir, String projectId) {
        StringBuilder help = new StringBuilder();
        for (String line : TerminalCommands.HELP_LINES) {
            help.append("  printf '%s\\n' ").append(shellQuote(line)).append("\n");
        }
        return SCRIPT_TEMPLATE
                .replace("@NDK_DIR@", shellQuote(filesDir + "/ndk"))
                .replace("@CMAKE_DIR@", shellQuote(filesDir + "/cmake"))
                .replace("@PROJECT_ID@", projectId)
                .replace("@HELP_LINES@", help.toString())
                .replace("@BUILD@", TerminalCommands.COMMAND_BUILD)
                .replace("@CANCEL_BUILD@", TerminalCommands.COMMAND_CANCEL_BUILD)
                .replace("@CLEAN_CACHE@", TerminalCommands.COMMAND_CLEAN_CACHE)
                .replace("@CLEAN_GRADLE@", TerminalCommands.COMMAND_CLEAN_GRADLE_BACKUPS)
                .replace("@NDK_INSTALL@", TerminalCommands.COMMAND_NDK_INSTALL)
                .replace("@STATUS@", TerminalCommands.COMMAND_STATUS)
                .replace("@GIT@", TerminalCommands.COMMAND_GIT)
                .replace("@CANCEL_GIT@", TerminalCommands.COMMAND_CANCEL_GIT)
                .replace("@GIT_INFO@", TerminalCommands.COMMAND_GIT_INFO);
    }

    private static File writeInitScript(Context appContext, String projectId) {
        String script = buildInitScript(appContext.getFilesDir().getAbsolutePath(), projectId);

        File scriptsDir = new File(appContext.getFilesDir(), "terminal");
        File initFile = new File(scriptsDir, "session-init-" + projectId + ".sh");
        File ipcDir = TerminalCommandDispatcher.getIpcDirectory(appContext);
        try {
            if (!scriptsDir.isDirectory() && !scriptsDir.mkdirs() && !scriptsDir.isDirectory()) {
                throw new IOException("Could not create terminal helper directory");
            }
            if (!ipcDir.isDirectory() && !ipcDir.mkdirs() && !ipcDir.isDirectory()) {
                throw new IOException("Could not create terminal request directory");
            }
            try (FileOutputStream output = new FileOutputStream(initFile, false)) {
                output.write(script.getBytes(StandardCharsets.UTF_8));
                output.flush();
            }
            return initFile;
        } catch (IOException e) {
            android.util.Log.e("NeoTerminal", "Could not prepare terminal helper script", e);
            return null;
        }
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }


    private static final String SCRIPT_TEMPLATE = """
NDK_DIR=@NDK_DIR@
CMAKE_DIR=@CMAKE_DIR@
clear
printf '\\033[1;32m'; echo 'Sketchware Neo Terminal'; printf '\\033[0m'
echo 'Project ID: @PROJECT_ID@'
echo 'Working directory:'; pwd
sw_ndk_find() {
  SW_NDK_ROOT=; SW_NDK_BIN=
  for sw_nb_d in "$NDK_DIR"/*/; do
    [ -f "$sw_nb_d/build/cmake/android.toolchain.cmake" ] || continue
    sw_nb_pre="${sw_nb_d%/}/toolchains/llvm/prebuilt"
    for sw_nb_c in "$sw_nb_pre" "$sw_nb_pre"/*; do
      [ -d "$sw_nb_c/bin" ] || continue
      for sw_nb_f in "$sw_nb_c"/bin/clang "$sw_nb_c"/bin/clang-[0-9]*; do
        if [ -e "$sw_nb_f" ] || [ -L "$sw_nb_f" ]; then SW_NDK_ROOT=${sw_nb_d%/}; SW_NDK_BIN="$sw_nb_c/bin"; return 0; fi
      done
    done
  done
  return 1
}
sw_refresh_path() {
  PATH=${SW_BASE_PATH:-$PATH}; unset ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME
  cmake_bin="$CMAKE_DIR/3.22.1/bin"; if [ -x "$cmake_bin/cmake" ]; then PATH="$cmake_bin:$PATH"; CMAKE_HOME="$CMAKE_DIR/3.22.1"; fi
  if sw_ndk_find; then PATH="$SW_NDK_BIN:$PATH"; ANDROID_NDK_HOME="$SW_NDK_ROOT"; ANDROID_NDK_ROOT="$SW_NDK_ROOT"; NDK_HOME="$SW_NDK_ROOT"; fi
  export PATH ANDROID_NDK_HOME ANDROID_NDK_ROOT NDK_HOME CMAKE_HOME
}
sw_refresh_path
ndk_status() {
  sw_refresh_path
  if [ -x "$CMAKE_DIR/3.22.1/bin/cmake" ]; then "$CMAKE_DIR/3.22.1/bin/cmake" --version 2>/dev/null | head -1; else echo 'CMake 3.22.1: not installed'; fi
  found=0; for d in "$NDK_DIR"/*/; do [ -f "$d/build/cmake/android.toolchain.cmake" ] || continue; echo "NDK: $(basename "$d")"; found=1; done; [ "$found" -eq 1 ] || echo 'NDK: not installed'
  command -v ninja >/dev/null 2>&1 && echo "Ninja: $(ninja --version 2>/dev/null)" || echo 'Ninja: not installed'
}
ndk_install() {
  if [ -z "$1" ]; then echo 'Usage: ndk_install <https://ndk-archive.zip>'; return 2; fi
  case "$1" in https://*|http://*) ;; *) echo 'Use an http:// or https:// archive URL.'; return 2 ;; esac
  case "$1" in *'
'*) echo 'The URL must be a single line.'; return 2 ;; esac
  sw_call @NDK_INSTALL@ 7200 "url=$1"
}
sw_send() {
  sw_s_file="$SW_IPC/$1.req"; sw_s_cmd=$2
  {
    printf 'command=%s\\n' "$sw_s_cmd"; printf 'sc_id=%s\\n' "$SW_PROJECT_ID"
    shift 2
    if [ "$sw_s_cmd" = "@GIT@" ]; then
      sw_s_i=0
      for sw_s_arg in "$@"; do printf 'arg%s=%s\\n' "$sw_s_i" "$sw_s_arg"; sw_s_i=$((sw_s_i+1)); done
      printf 'argc=%s\\n' "$sw_s_i"
    else
      for sw_s_arg in "$@"; do printf '%s\\n' "$sw_s_arg"; done
    fi
    if [ "${sw_confirmed:-}" = true ]; then printf 'confirmed=true\\n'; fi
  } > "$sw_s_file.tmp" 2>/dev/null && mv "$sw_s_file.tmp" "$sw_s_file" 2>/dev/null || { rm -f "$sw_s_file.tmp"; return 1; }
}
sw_call() {
  sw_c_cmd=$1; sw_c_max=$2; shift 2
  for sw_c_tool in mv rm sleep cat wc sed; do command -v "$sw_c_tool" >/dev/null 2>&1 || { echo "The required tool '$sw_c_tool' is missing from this shell."; return 127; }; done
  if [ ! -d "$SW_IPC" ] || [ ! -w "$SW_IPC" ]; then echo 'The terminal request directory is missing or not writable.'; return 1; fi
  SW_SEQ=$((${SW_SEQ:-0}+1))
  sw_c_id="$$-$SW_SEQ-${RANDOM:-0}"
  sw_c_out="$SW_IPC/$sw_c_id.out"; sw_c_rc="$SW_IPC/$sw_c_id.rc"; sw_c_req="$SW_IPC/$sw_c_id.req"
  rm -f "$sw_c_out" "$sw_c_rc" "$sw_c_req"
  sw_send "$sw_c_id" "$sw_c_cmd" "$@" || { echo 'Could not write the request file.'; return 1; }
  sw_c_shown=0; sw_c_tick=0; sw_c_stop=0; sw_c_seen=0; sw_c_cancelsent=0
  case "$sw_c_cmd" in @BUILD@) sw_c_cancelcmd=@CANCEL_BUILD@ ;; @GIT@) sw_c_cancelcmd=@CANCEL_GIT@ ;; *) sw_c_cancelcmd= ;; esac
  trap 'sw_c_stop=1' INT
  while :; do
    if [ -f "$sw_c_out" ] || [ -f "$sw_c_rc" ]; then sw_c_seen=1; fi
    if [ -f "$sw_c_out" ]; then
      sw_c_lines=$(wc -l < "$sw_c_out" 2>/dev/null); sw_c_lines=$((${sw_c_lines:-0}+0))
      if [ "$sw_c_lines" -gt "$sw_c_shown" ]; then sed -n "$((sw_c_shown+1)),${sw_c_lines}p" "$sw_c_out"; sw_c_shown=$sw_c_lines; fi
    fi
    [ -f "$sw_c_rc" ] && break
    if [ "$sw_c_stop" -eq 1 ]; then
      if [ -n "$sw_c_cancelcmd" ] && [ "$sw_c_cancelsent" -eq 0 ]; then
        sw_c_cancelsent=1; sw_c_stop=0; echo 'Cancel requested. Waiting for the operation to stop (Ctrl+C again to stop waiting)...'
        sw_send "cancel-$sw_c_id" "$sw_c_cancelcmd"
      else
        trap - INT; rm -f "$sw_c_req"; echo 'Stopped waiting. The operation may still be running in Sketchware Neo.'; return 130
      fi
    fi
    if [ "$sw_c_seen" -eq 0 ] && [ "$sw_c_tick" -ge 10 ]; then
      trap - INT
      if [ -f "$sw_c_req" ]; then rm -f "$sw_c_req"; echo 'Sketchware Neo did not pick up the request: the terminal service is not running. Reopen the Terminal tab and try again.'; else echo 'Sketchware Neo picked up the request but sent no answer.'; fi
      return 1
    fi
    if [ "$sw_c_tick" -ge "$sw_c_max" ]; then trap - INT; echo "Stopped waiting after $sw_c_max seconds. The operation may still be running in Sketchware Neo."; return 1; fi
    sleep 1; sw_c_tick=$((sw_c_tick+1))
  done
  sw_c_lines=$(wc -l < "$sw_c_out" 2>/dev/null); sw_c_lines=$((${sw_c_lines:-0}+0))
  if [ "$sw_c_lines" -gt "$sw_c_shown" ]; then sed -n "$((sw_c_shown+1)),${sw_c_lines}p" "$sw_c_out"; fi
  sw_c_status=$(cat "$sw_c_rc" 2>/dev/null)
  case "$sw_c_status" in ''|*[!0-9]*) sw_c_status=1 ;; esac
  rm -f "$sw_c_out" "$sw_c_rc"; trap - INT
  return "$sw_c_status"
}
sw_confirm() {
  printf '%s\\nType yes to continue: ' "$1"; IFS= read -r sw_answer
  [ "$sw_answer" = yes ]
}
sw_status() { sw_call @STATUS@ 30; }
sw_build() { sw_call @BUILD@ 3600; }
sw_git() {
  for sw_g_a in "$@"; do case "$sw_g_a" in *'
'*) echo 'git arguments must be single-line. Use several -m options for a multi-line commit message.'; return 2 ;; esac; done
  sw_confirmed=; sw_call @GIT@ 3600 "$@"; sw_g_rc=$?
  if [ "$sw_g_rc" -eq 10 ]; then
    if sw_confirm 'The operation above changes the repository.'; then sw_confirmed=true; sw_call @GIT@ 3600 "$@"; sw_g_rc=$?; else echo 'Cancelled.'; sw_g_rc=1; fi
  fi
  sw_confirmed=
  return "$sw_g_rc"
}
sw_which() {
  sw_w_p=$(command -v "$1" 2>/dev/null)
  if [ -n "$sw_w_p" ] && [ -f "$sw_w_p" ] && [ -x "$sw_w_p" ]; then printf '%s\\n' "$sw_w_p"; fi
}
SW_REAL_GIT=$(sw_which git)
if [ -z "$SW_REAL_GIT" ]; then git() { sw_git "$@"; }; fi
sw_clean_cache() {
  sw_call @CLEAN_CACHE@ 120 dry_run=true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The build cache shown above will be deleted. Projects and sources are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_CACHE@ 900 confirmed=true
}
sw_clean_gradle_backups() {
  sw_call @CLEAN_GRADLE@ 120 dry_run=true; sw_rc=$?
  [ "$sw_rc" -eq 10 ] || return "$sw_rc"
  sw_confirm 'The Gradle backups listed above will be deleted. The current Gradle files are not touched.' || { echo 'Cancelled.'; return 1; }
  sw_call @CLEAN_GRADLE@ 120 confirmed=true
}
sw_help() {
@HELP_LINES@}
help() { sw_help; }
sw_project() { echo 'Project directory:'; printf '%s\\n' "$SW_PROJECT_DIR"; echo 'Files:'; ls -la; }
gradlew() {
  if [ -f ./gradlew ]; then sh ./gradlew "$@"; return $?; fi
  if command -v gradle >/dev/null 2>&1; then gradle "$@"; return $?; fi
  echo 'No Gradle wrapper was found in this directory, and system Gradle is not installed.'
  echo "Current directory: $PWD"
  case "$PWD" in */.sketchware/data/*) echo 'This is a Sketchware app-project data directory, not a Gradle repository. Build the app with sw_build or the Run button.' ;; *) echo 'Change to a Gradle repository root containing gradlew and gradle/wrapper, then retry.' ;; esac
  return 127
}
sw_doctor_tool() {
  sw_dt_path=$(sw_which "$1")
  if [ -n "$sw_dt_path" ]; then printf '  [OK]   %s: %s\\n' "$1" "$sw_dt_path"; else printf '  [MISS] %s: not found in PATH\\n' "$1"; fi
}
sw_doctor_missing() {
  sw_dm_path=$(sw_which "$1")
  if [ -n "$sw_dm_path" ]; then printf '  [OK]   %s: %s\\n' "$1" "$sw_dm_path"; else printf '  [MISS] %s: %s\\n' "$1" "$2"; fi
}
sw_doctor_compiler() {
  sw_dc_path=$(sw_which "$1")
  if [ -n "$sw_dc_path" ]; then
    if "$sw_dc_path" --version >/dev/null 2>&1; then printf '  [OK]   %s: %s\\n' "$1" "$sw_dc_path"; else printf '  [FAIL] %s: %s exists but "%s --version" fails (wrong CPU or OS build, or no execute permission)\\n' "$1" "$sw_dc_path" "$1"; fi
    return
  fi
  if sw_ndk_find; then
    sw_dc_have=$(ls "$SW_NDK_BIN" 2>/dev/null | grep '^clang' | head -5 | tr '\\n' ' ')
    if [ -e "$SW_NDK_BIN/$1" ] || [ -L "$SW_NDK_BIN/$1" ]; then printf '  [MISS] %s: %s/%s exists but is not an executable file (broken link or lost permissions)\\n' "$1" "$SW_NDK_BIN" "$1"
    else printf '  [MISS] %s: the NDK toolchain at %s has no "%s"; it contains: %s\\n' "$1" "$SW_NDK_BIN" "$1" "${sw_dc_have:-nothing named clang}"; fi
  else
    printf '  [MISS] %s: no NDK toolchain with a bin/clang was found under %s (install one with ndk_install)\\n' "$1" "$NDK_DIR"
  fi
}
swdoctor() {
  echo '--- Sketchware Neo Terminal diagnostics ---'; echo "Directory: $PWD"; echo "UID: $(id 2>/dev/null)"; echo "Android: $(getprop ro.build.version.release 2>/dev/null)"; echo "PATH searched: $PATH"; echo
  echo 'Shell tools:'
  for c in sh ls cat grep find sed awk; do sw_doctor_tool "$c"; done
  echo
  echo 'Git:'
  if [ -n "$SW_REAL_GIT" ]; then printf '  [OK]   Git CLI: %s\\n' "$SW_REAL_GIT"; else echo '  [MISS] Git CLI: no git program in PATH (Android does not include one and Sketchware Neo does not bundle it)'; fi
  sw_call @GIT_INFO@ 30 | sed 's/^/  /'
  echo
  echo 'Developer tools:'
  sw_doctor_missing gradle 'not installed (Android does not include it; use sw_build or the Run button for the app build)'
  sw_doctor_missing npm 'not installed (no Node.js is bundled with Sketchware Neo)'
  sw_doctor_missing node 'not installed (no Node.js is bundled with Sketchware Neo)'
  sw_doctor_missing pkg 'not available (pkg is the Termux package manager; this terminal has no package manager)'
  sw_doctor_tool cmake
  sw_doctor_tool ninja
  sw_doctor_compiler clang
  sw_doctor_compiler clang++
  if [ -f ./gradlew ]; then echo 'Gradle wrapper: present in current directory'; else echo 'Gradle wrapper: not present in current directory'; fi
  echo
  ndk_status
}
alias ll='ls -la'
echo 'Type help to list the available commands.'
ndk_status
echo
""";
}
