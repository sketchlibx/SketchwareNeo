package mod.pranav.build

import a.a.a.yq
import android.util.Log
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.OutputMode
import com.android.tools.r8.R8
import com.android.tools.r8.R8Command
import com.android.tools.r8.origin.Origin
import com.android.tools.r8.DiagnosticsHandler
import com.android.tools.r8.Diagnostic
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.Executors

class R8Compiler(
    private val rules: MutableList<String>,
    private val configs: Array<String>,
    val libs: Array<String>,
    private val inputs: Array<String>,
    private val minApi: Int,
    private val multiDexEnabled: Boolean,
    val yq: yq
) {

    companion object {
        private const val TAG = "R8Compiler"
    }

    /**
     * Bounded, adaptive thread count for R8's own internal executor.
     *
     * R8.run(command) alone lets R8 create its own executor sized off
     * Runtime.getRuntime().availableProcessors(), which on a modern device can mean 6-8
     * concurrent worker threads during the shaking/Enqueuer phase, each holding its own
     * working set of IR/graph structures. That is what was actually driving peak heap usage
     * during the OutOfMemoryError seen in production, not project size alone.
     *
     * This never exceeds the device's actual CPU count, never goes below 1, and is throttled
     * further by two independent, conservative signals rather than CPU count alone:
     *  - maxHeapMb: this process's actual heap ceiling (reflects largeHeap + device limits)
     *  - programSizeMb: total size of R8's program inputs, a proxy for shaking graph size —
     *    a large program input gets capped at 2 threads regardless of available heap, since
     *    each thread's working set is heavier the more classes are in play.
     */
    private fun computeSafeThreadCount(programByteSize: Long): Int {
        val runtime = Runtime.getRuntime()
        val maxHeapMb = runtime.maxMemory() / (1024 * 1024)
        val cpuCount = runtime.availableProcessors()

        val heapCap = when {
            maxHeapMb < 300 -> 1
            maxHeapMb < 500 -> 2
            maxHeapMb < 800 -> 3
            else -> 4
        }

        val programSizeMb = programByteSize / (1024 * 1024)
        val sizeAdjustedCap = if (programSizeMb > 40) minOf(heapCap, 2) else heapCap

        return minOf(sizeAdjustedCap, cpuCount).coerceAtLeast(1)
    }

    fun compile() {
        val output = Paths.get(yq.binDirectoryPath, "dex")
        Files.createDirectories(output)

        val errorLog = StringBuilder()
        val diagnosticsHandler = object : DiagnosticsHandler {
            override fun error(diagnostic: Diagnostic) {
                errorLog.append("ERROR: ").append(diagnostic.diagnosticMessage).append("\n")
            }
            override fun warning(diagnostic: Diagnostic) {
                // Warnings are ignored to keep logs clean
            }
            override fun info(diagnostic: Diagnostic) {}
        }

        val builder = R8Command.builder(diagnosticsHandler)
            .addProgramFiles(inputs.map { Paths.get(it) })
            .addProguardConfiguration(rules, Origin.unknown())
            .addProguardConfigurationFiles(configs.map { Paths.get(it) })
            .setProguardMapOutputPath(Paths.get(yq.proguardMappingPath))
            .setMinApiLevel(minApi)
            .addLibraryFiles(libs.map { Paths.get(it) })
            .setOutput(output, OutputMode.DexIndexed)
            .setMode(CompilationMode.RELEASE)

        if (multiDexEnabled) {
            // R8 automatically multi-dexes when OutputMode.DexIndexed is used without a main dex list
        }

        // --- Diagnostics: cheap, logcat-only (filter on tag "R8Compiler"), no persisted
        // file, no UI impact. Exists to answer, from real runs, whether Enqueuer/shaking is
        // the actual bottleneck, whether the input graph is unexpectedly large, and whether
        // the selected thread count correlates with success/failure and duration. ---
        val programBytes = inputs.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
        val threadCount = computeSafeThreadCount(programBytes)
        val runtime = Runtime.getRuntime()
        val freeHeapMbBefore =
            (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
        Log.d(
            TAG,
            "R8 starting: programInputs=${inputs.size} (${programBytes / 1024}KB), " +
                "libraryInputs=${libs.size}, configFiles=${configs.size}, rules=${rules.size}, " +
                "threads=$threadCount, maxHeapMB=${runtime.maxMemory() / (1024 * 1024)}, " +
                "freeHeapMB=$freeHeapMbBefore"
        )
        val startTime = System.currentTimeMillis()

        val executor = Executors.newFixedThreadPool(threadCount)
        try {
            R8.run(builder.build(), executor)
        } catch (e: Exception) {
            // Throw the exact captured error back to the Service
            if (errorLog.isNotEmpty()) {
                throw RuntimeException("R8 Error:\n$errorLog", e)
            } else {
                throw e
            }
        } finally {
            executor.shutdown()
            val duration = System.currentTimeMillis() - startTime
            val freeHeapMbAfter =
                (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
            Log.d(TAG, "R8 finished in ${duration}ms, freeHeapMB after=$freeHeapMbAfter")
        }
    }
}
