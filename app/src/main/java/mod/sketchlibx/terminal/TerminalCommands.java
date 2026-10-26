package mod.sketchlibx.terminal;

import java.util.List;

/** Defines the commands available in the terminal. */
public final class TerminalCommands {

    public static final String KEY_COMMAND = "command";
    public static final String KEY_SC_ID = "sc_id";
    public static final String KEY_CONFIRMED = "confirmed";
    public static final String KEY_DRY_RUN = "dry_run";
    public static final String KEY_URL = "url";
    public static final String KEY_ARGC = "argc";
    public static final String ARG_KEY_PREFIX = "arg";
    public static final int MAX_ARGS = 32;
    public static final int MAX_ARG_LENGTH = 1000;

    public static final String COMMAND_STATUS = "status";
    public static final String COMMAND_BUILD = "build";
    public static final String COMMAND_CANCEL_BUILD = "cancel-build";
    public static final String COMMAND_CLEAN_CACHE = "clean-cache";
    public static final String COMMAND_CLEAN_GRADLE_BACKUPS = "clean-gradle-backups";
    public static final String COMMAND_NDK_INSTALL = "ndk-install";
    public static final String COMMAND_GIT = "git";
    public static final String COMMAND_CANCEL_GIT = "cancel-git";
    public static final String COMMAND_GIT_INFO = "git-info";

    public static final int EXIT_NOT_AVAILABLE = 4;
    public static final int EXIT_BUSY = 5;
    public static final int EXIT_CANCELED = 130;

    public static final List<String> HELP_LINES = List.of(
            "Sketchware Neo Terminal commands",
            "--------------------------------",
            "",
            "help, sw_help",
            "    Show this list.",
            "",
            "sw_status",
            "    Check that this terminal can reach Sketchware Neo and show the project editor,",
            "    build and directory state. Run this first if a command below reports an error.",
            "",
            "sw_build",
            "    Build a debug APK of this project with the same pipeline as the Run button.",
            "    Progress and the final result are printed here. Ctrl+C cancels the build",
            "    (press it again to stop waiting). The APK is not installed automatically.",
            "",
            "sw_clean_cache",
            "    Delete the generated build cache of this project (.sketchware/mysc/<project id>)",
            "    after showing its size and asking for confirmation. Projects, sources, manifests",
            "    and settings are not touched; the next build recreates the cache.",
            "",
            "sw_clean_gradle_backups",
            "    Delete the Gradle configuration backups of this project (backup_gradle_*) that",
            "    contain only the known Gradle files, after confirmation. Anything else is skipped.",
            "",
            "sw_project",
            "    Print the project data directory and list its files.",
            "",
            "swdoctor",
            "    Check which common tools are available in this shell and show NDK/CMake status.",
            "",
            "ndk_status",
            "    Show the installed CMake, NDK and Ninja versions.",
            "",
            "ndk_install <url>",
            "    Download and install an NDK/CMake archive from an http(s) URL. Progress and the",
            "    final result are printed here; Ctrl+C stops waiting but the install continues.",
            "",
            "git <command>   (also: sw_git <command>)",
            "    Git through the built-in Git client (JGit) on this project's Git workspace",
            "    (.sketchware/data/<project id>/git_workspace), using the author, username and token",
            "    from Git Client > Settings. Supported: status, init, add, commit, log, branch,",
            "    remote, pull, push and sync. Run 'git help' for the exact syntax. Other git",
            "    commands are not supported. Ctrl+C cancels pull and push. Android has no git",
            "    program; if one is in PATH, 'git' runs that instead and sw_git still uses the",
            "    built-in client.",
            "",
            "gradlew <tasks>",
            "    Run ./gradlew from the current directory, or system Gradle if installed.",
            "    Explains why it cannot run when neither is available.",
            "",
            "ll",
            "    Alias for ls -la.",
            "",
            "Output menu: use the clear icon to clear the screen and scrollback, or the other",
            "items there for command help, diagnostics and recent commands.",
            "Standard Android shell tools (ls, cd, cat, grep, ...) come from the device and",
            "are not managed by Sketchware Neo.");

    private TerminalCommands() {
    }
}
