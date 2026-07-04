package pro.sketchware.analysis.bootstrap;

import pro.sketchware.analysis.core.AnalysisCheckRegistry;
import pro.sketchware.analysis.modules.dependency.DuplicateBuiltInLibraryCheck;
import pro.sketchware.analysis.modules.dependency.DuplicateLocalLibraryCheck;
import pro.sketchware.analysis.modules.dependency.DuplicateMavenDependencyCheck;
import pro.sketchware.analysis.modules.dependency.PackageNameCheck;
import pro.sketchware.analysis.modules.manifest.DuplicateActivityCheck;
import pro.sketchware.analysis.modules.manifest.DuplicatePermissionCheck;
import pro.sketchware.analysis.modules.manifest.DuplicateReceiverCheck;
import pro.sketchware.analysis.modules.manifest.DuplicateServiceCheck;
import pro.sketchware.analysis.modules.manifest.MissingLauncherActivityCheck;
import pro.sketchware.analysis.modules.native_.NativeLibraryCheck;
import pro.sketchware.analysis.modules.resource.DuplicateResourceCheck;
import pro.sketchware.analysis.modules.resource.UnusedResourceCheck;

public final class AnalysisModuleBootstrap {

    private AnalysisModuleBootstrap() {}

    private static volatile boolean initialized = false;

    public static synchronized void registerBuiltIns() {
        if (initialized) return;

        registerResourceChecks();
        registerManifestChecks();
        registerDependencyChecks();
        registerNativeChecks();

        AutoFixBootstrap.registerBuiltIns();

        initialized = true;
    }

    private static void registerResourceChecks() {
        AnalysisCheckRegistry.register(new DuplicateResourceCheck());
        AnalysisCheckRegistry.register(new UnusedResourceCheck());
    }

    private static void registerManifestChecks() {
        AnalysisCheckRegistry.register(new DuplicateActivityCheck());
        AnalysisCheckRegistry.register(new DuplicatePermissionCheck());
        AnalysisCheckRegistry.register(new DuplicateServiceCheck());
        AnalysisCheckRegistry.register(new DuplicateReceiverCheck());
        AnalysisCheckRegistry.register(new MissingLauncherActivityCheck());
    }

    private static void registerDependencyChecks() {
        AnalysisCheckRegistry.register(new PackageNameCheck());
        AnalysisCheckRegistry.register(new DuplicateBuiltInLibraryCheck());
        AnalysisCheckRegistry.register(new DuplicateLocalLibraryCheck());
        AnalysisCheckRegistry.register(new DuplicateMavenDependencyCheck());
    }

    private static void registerNativeChecks() {
        AnalysisCheckRegistry.register(new NativeLibraryCheck());
    }
}
