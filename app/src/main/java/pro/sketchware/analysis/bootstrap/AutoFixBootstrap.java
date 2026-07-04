package pro.sketchware.analysis.bootstrap;

import pro.sketchware.analysis.autofix.AutoFixRegistry;
import pro.sketchware.analysis.autofix.fixes.MissingLauncherActivityFix;
import pro.sketchware.analysis.modules.manifest.MissingLauncherActivityCheck;

public final class AutoFixBootstrap {

    private AutoFixBootstrap() {}

    private static volatile boolean initialized = false;

    public static synchronized void registerBuiltIns() {
        if (initialized) return;

        AutoFixRegistry.register(MissingLauncherActivityCheck.FIX_ID, new MissingLauncherActivityFix());

        initialized = true;
    }
}
