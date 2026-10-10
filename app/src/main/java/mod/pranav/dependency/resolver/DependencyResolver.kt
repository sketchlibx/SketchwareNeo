package mod.pranav.dependency.resolver

import android.os.Environment
import com.android.tools.r8.CompilationMode
import com.android.tools.r8.D8
import com.android.tools.r8.D8Command
import com.android.tools.r8.OutputMode
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import mod.hey.studios.build.BuildSettings
import mod.hey.studios.util.Helper
import mod.jbk.build.BuiltInLibraries
import org.cosmic.ide.dependency.resolver.api.Artifact
import org.cosmic.ide.dependency.resolver.api.EventReciever
import org.cosmic.ide.dependency.resolver.api.Repository
import org.cosmic.ide.dependency.resolver.eventReciever
import org.cosmic.ide.dependency.resolver.repositories
import pro.sketchware.utility.FileUtil
import java.io.File
import java.io.IOException
import java.net.URL
import java.net.URLDecoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.Collections
import java.util.function.BooleanSupplier
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

private class ResolverAbort(val failure: ResolverFailure) : RuntimeException(failure.message)

private class Fetched(val folder: String, val cached: Boolean)

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
        const val IDENTITY_FILE = "maven.txt"

        private val DEFAULT_REPOS = """
          |[
          |    {"url": "https://jitpack.io", "name": "JitPack"},
          |    {"url": "https://maven.google.com", "name": "Google Maven"},
          |    {"url": "https://jcenter.bintray.com", "name": "JCenter"},
          |    {"url": "https://repo.maven.apache.org/maven2", "name": "Apache Maven Central"},
          |    {"url": "https://oss.sonatype.org/content/repositories/releases", "name": "Sonatype"},
          |    {"url": "https://repo.spring.io/plugins-release", "name": "Spring Plugins"},
          |    {"url": "https://repo.spring.io/libs-milestone", "name": "Spring Milestone"},
          |    {"url": "https://maven.atlassian.com/content/repositories/atlassian-public", "name": "Atlassian"},
          |    {"url": "https://repo.hortonworks.com/content/repositories/releases", "name": "HortanWorks"}
          |]
        """.trimMargin()

        private val COORDINATE_PART = Regex("[A-Za-z0-9_.\\-]+")
        private val VERSION_PART = Regex("[A-Za-z0-9_.\\-+]+")
        private val VERSION_FORBIDDEN = Regex("[\\[\\](),\\s]")
    }

    var providedBy: ((Artifact) -> String?)? = null

    var rootArtifact: Artifact? = null
        private set

    var rootFolder: String? = null
        private set

    @Volatile
    private var cancelled = false

    private val downloadPath: String =
        FileUtil.getExternalStorageDir() + "/.sketchware/libs/local_libs"

    private val repositoriesJson = Paths.get(
        Environment.getExternalStorageDirectory().absolutePath,
        ".sketchware",
        "libs",
        "repositories.json"
    )

    init {
        if (Files.notExists(repositoriesJson)) {
            Files.createDirectories(repositoriesJson.parent)
            repositoriesJson.writeText(DEFAULT_REPOS)
        }

        val reposString = repositoriesJson.readText()
        if (!reposString.contains("jitpack.io") || !reposString.contains("maven.google.com")) {
            repositoriesJson.writeText(DEFAULT_REPOS)
        }

        repositories.clear()
        Gson().fromJson(repositoriesJson.readText(), Helper.TYPE_MAP_LIST).forEach {
            val url: String? = it["url"] as String?
            if (url != null) {
                repositories.add(object : Repository {
                    override fun getName(): String {
                        return (it["name"] as? String) ?: url
                    }

                    override fun getURL(): String {
                        return if (url.endsWith("/")) {
                            url.substringBeforeLast("/")
                        } else {
                            url
                        }
                    }
                })
            }
        }
    }

    open class DependencyResolverCallback : EventReciever() {
        override fun artifactFound(artifact: Artifact) {}
        override fun onArtifactNotFound(artifact: Artifact) {}
        override fun onFetchingLatestVersion(artifact: Artifact) {}
        override fun onFetchedLatestVersion(artifact: Artifact, version: String) {}
        override fun onResolving(artifact: Artifact, dependency: Artifact) {}
        override fun onResolutionComplete(artifact: Artifact) {}
        override fun onSkippingResolution(artifact: Artifact) {}
        override fun onVersionNotFound(artifact: Artifact) {}
        override fun onDependenciesNotFound(artifact: Artifact) {}
        override fun onInvalidScope(artifact: Artifact, scope: String) {}
        override fun onInvalidPOM(artifact: Artifact) {}
        override fun onDownloadStart(artifact: Artifact) {}
        override fun onDownloadEnd(artifact: Artifact) {}
        override fun onDownloadError(artifact: Artifact, error: Throwable) {}
        open fun unzipping(artifact: Artifact) {}
        open fun dexing(artifact: Artifact) {}
        open fun onTaskCompleted(artifacts: List<String>) {}
        open fun dexingFailed(artifact: Artifact, e: Exception) {}
        open fun invalidPackaging(artifact: Artifact) {}

        open fun onCacheHit(artifact: Artifact) {}
        open fun onProvided(artifact: Artifact, provider: String) {}
        open fun onFolderResolved(artifact: Artifact, folder: String) {}
        open fun onDownloadProgress(artifact: Artifact, bytes: Long, total: Long) {}
        open fun onDependencySkipped(artifact: Artifact, reason: String) {}
        open fun onFailure(failure: ResolverFailure) {}

        open fun onDirectDownloadStart(url: String) {}
        open fun onDirectDownloadEnd(fileName: String) {}
        open fun onDirectDownloadError(url: String, error: Throwable) {}
    }

    private class RecordingCallback(private val delegate: DependencyResolverCallback) :
        DependencyResolverCallback() {
        val notFound: MutableList<Artifact> = Collections.synchronizedList(ArrayList())

        override fun artifactFound(artifact: Artifact) = delegate.artifactFound(artifact)

        override fun onArtifactNotFound(artifact: Artifact) {
            notFound.add(artifact)
            delegate.onArtifactNotFound(artifact)
        }

        override fun onFetchingLatestVersion(artifact: Artifact) =
            delegate.onFetchingLatestVersion(artifact)

        override fun onFetchedLatestVersion(artifact: Artifact, version: String) =
            delegate.onFetchedLatestVersion(artifact, version)

        override fun onResolving(artifact: Artifact, dependency: Artifact) =
            delegate.onResolving(artifact, dependency)

        override fun onResolutionComplete(artifact: Artifact) =
            delegate.onResolutionComplete(artifact)

        override fun onSkippingResolution(artifact: Artifact) =
            delegate.onSkippingResolution(artifact)

        override fun onVersionNotFound(artifact: Artifact) = delegate.onVersionNotFound(artifact)

        override fun onDependenciesNotFound(artifact: Artifact) =
            delegate.onDependenciesNotFound(artifact)

        override fun onInvalidScope(artifact: Artifact, scope: String) =
            delegate.onInvalidScope(artifact, scope)

        override fun onInvalidPOM(artifact: Artifact) = delegate.onInvalidPOM(artifact)
    }

    fun cancel() {
        cancelled = true
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback
        try {
            resolveInternal(callback)
        } catch (abort: ResolverAbort) {
            callback.onFailure(abort.failure)
        } catch (t: Throwable) {
            callback.onFailure(
                ResolverFailure(
                    ResolverFailure.Stage.UNEXPECTED,
                    label(),
                    FailureFormatter.describe(t),
                    t
                )
            )
        }
    }

    private fun isDirectUrl(): Boolean =
        groupId.startsWith("http://") || groupId.startsWith("https://")

    private fun label(): String = if (isDirectUrl()) groupId else "$groupId:$artifactId:$version"

    private fun abort(
        stage: ResolverFailure.Stage,
        coordinate: String?,
        message: String,
        cause: Throwable? = null
    ): ResolverAbort = ResolverAbort(ResolverFailure(stage, coordinate, message, cause))

    private fun checkCancelled(artifact: Artifact?) {
        if (cancelled) {
            throw abort(
                ResolverFailure.Stage.CANCELLED,
                artifact?.toString() ?: label(),
                "Cancelled by user"
            )
        }
    }

    private suspend fun resolveInternal(callback: DependencyResolverCallback) {
        if (isDirectUrl()) {
            handleDirectUrlDownload(groupId, callback)
            return
        }

        validateCoordinate()
        val root = locate(groupId, artifactId, version, callback)
        rootArtifact = root

        val extension = root.extension
        if (extension != "jar" && extension != "aar") {
            callback.invalidPackaging(root)
            throw abort(
                ResolverFailure.Stage.PACKAGING,
                root.toString(),
                "Packaging '$extension' cannot be used as a library (only jar and aar are supported)."
            )
        }

        val transitive: List<Artifact> = if (skipDependencies) {
            emptyList()
        } else {
            val recording = RecordingCallback(callback)
            eventReciever = recording
            try {
                resolveTransitive(root, recording, callback)
            } finally {
                eventReciever = callback
            }
        }

        val targets = ArrayList<Artifact>()
        targets.add(root)
        for (dep in transitive) {
            if (dep != root && !targets.contains(dep)) targets.add(dep)
        }

        val fetched = LinkedHashMap<Artifact, Fetched>()
        for (artifact in targets) {
            fetched[artifact] = fetch(artifact, callback)
        }

        val classpath = baseClasspath()
        for (info in fetched.values) {
            classpath.add(Paths.get(downloadPath, info.folder, "classes.jar"))
        }
        val libraryJars = libraryJars()

        val completed = ArrayList<String>()
        for ((artifact, info) in fetched) {
            checkCancelled(artifact)
            if (info.cached) {
                callback.onFolderResolved(artifact, info.folder)
                callback.onResolutionComplete(artifact)
                completed.add(info.folder)
                continue
            }
            if (dex(artifact, info.folder, classpath, libraryJars, callback)) {
                completed.add(info.folder)
            } else if (artifact == root) {
                throw abort(
                    ResolverFailure.Stage.MISSING_CLASSES,
                    root.toString(),
                    "The downloaded archive contains no classes, so there is nothing to add to the project."
                )
            }
        }

        rootFolder = fetched[root]?.folder
        callback.onTaskCompleted(completed)
    }

    private fun validateCoordinate() {
        val coordinate = "$groupId:$artifactId:$version"
        if (!COORDINATE_PART.matches(groupId) || !COORDINATE_PART.matches(artifactId)) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "Group and artifact may only contain letters, digits, '.', '_' and '-'."
            )
        }
        if (version.isBlank()) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "A version is required (group:artifact:version)."
            )
        }
        if (version == "+" || VERSION_FORBIDDEN.containsMatchIn(version) || !VERSION_PART.matches(version)) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                coordinate,
                "The version must be one fixed version, not '$version'."
            )
        }
    }

    private fun locate(
        group: String,
        artifact: String,
        artifactVersion: String,
        callback: DependencyResolverCallback
    ): Artifact {
        val coordinate = "$group:$artifact:$artifactVersion"
        val repoList = repositories.toList()
        if (repoList.isEmpty()) {
            throw abort(
                ResolverFailure.Stage.REPOSITORY,
                coordinate,
                "No repositories are configured. Add one in the repositories list."
            )
        }
        val repos = repoList.map { RepositoryProbe.Repo(it.getName(), it.getURL()) }
        val result = RepositoryProbe.locate(
            repos,
            group,
            artifact,
            artifactVersion,
            BooleanSupplier { cancelled }
        )
        if (result.cancelled) {
            throw abort(ResolverFailure.Stage.CANCELLED, coordinate, "Cancelled by user")
        }
        if (!result.found()) {
            val stage = if (result.allNetworkFailures()) {
                ResolverFailure.Stage.NETWORK
            } else {
                ResolverFailure.Stage.REPOSITORY
            }
            throw abort(stage, coordinate, result.explain(coordinate))
        }

        val located = Artifact(group, artifact, artifactVersion)
        located.repository = repoList[result.foundIndex]
        callback.artifactFound(located)

        val pom = located.getPOM()
        if (pom == null) {
            throw abort(
                ResolverFailure.Stage.POM,
                coordinate,
                "The POM was found at ${result.foundUrl} but could not be read (invalid XML or unexpected structure)."
            )
        }
        val packaging = pom.packaging
        located.extension = if (packaging != null && packaging != "bundle") packaging else "jar"
        return located
    }

    private suspend fun resolveTransitive(
        root: Artifact,
        recording: RecordingCallback,
        callback: DependencyResolverCallback
    ): List<Artifact> {
        checkCancelled(root)
        val all: Set<Artifact> = try {
            root.resolveDependencyTree()
            root.getAllDependencies()
        } catch (e: ResolverAbort) {
            throw e
        } catch (e: Exception) {
            throw abort(
                ResolverFailure.Stage.POM,
                root.toString(),
                "Resolving the dependency tree failed: ${FailureFormatter.describe(e)}",
                e
            )
        }

        val resolvedKeys = HashSet<String>()
        for (dep in all) resolvedKeys.add("${dep.groupId}:${dep.artifactId}")
        val rootKey = "${root.groupId}:${root.artifactId}"
        val dropped = recording.notFound.toList().filter {
            val key = "${it.groupId}:${it.artifactId}"
            key != rootKey && !resolvedKeys.contains(key)
        }.distinct()
        if (dropped.isNotEmpty()) {
            throw abort(
                ResolverFailure.Stage.REPOSITORY,
                root.toString(),
                "Required dependencies were not found in any repository: ${dropped.joinToString()}. " +
                        "Enable \"Skip downloading sub-dependencies\" to download only $root."
            )
        }

        val result = ArrayList<Artifact>()
        for (dep in all) {
            checkCancelled(dep)
            if (dep == root) continue
            val provider = providedBy?.invoke(dep)
            if (provider != null) {
                callback.onProvided(dep, provider)
                continue
            }
            val usable = repairTransitive(root, dep, callback)
            if (usable != null) result.add(usable)
        }
        return result
    }

    private fun repairTransitive(
        root: Artifact,
        dep: Artifact,
        callback: DependencyResolverCallback
    ): Artifact? {
        if (dep.version.isEmpty() || !VERSION_PART.matches(dep.version)) {
            callback.onVersionNotFound(dep)
            callback.onDependencySkipped(dep, "no usable version")
            return null
        }

        if (dep.repository == null || dep.pom == null) {
            val located = locate(dep.groupId, dep.artifactId, dep.version, callback)
            dep.repository = located.repository
            dep.pom = located.pom
            dep.extension = located.extension
        }

        if (dep.extension != "jar" && dep.extension != "aar") {
            callback.invalidPackaging(dep)
            callback.onDependencySkipped(dep, "packaging '${dep.extension}' has no classes")
            return null
        }
        return dep
    }

    private fun identityOf(artifact: Artifact): String =
        "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"

    private fun readIdentity(dir: File): String? {
        val file = File(dir, IDENTITY_FILE)
        if (!file.isFile) return null
        val text = file.readText().trim()
        return if (text.isEmpty()) null else text
    }

    private fun writeIdentity(dir: File, identity: String) {
        File(dir, IDENTITY_FILE).writeText(identity)
    }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        val builder = StringBuilder()
        for (byte in digest) builder.append(String.format("%02x", byte))
        return builder.toString().substring(0, 8)
    }

    private fun folderFor(artifact: Artifact): String {
        val base = "${artifact.artifactId}-v${artifact.version}"
        val existing = readIdentity(File(downloadPath, base))
        if (existing == null || existing == identityOf(artifact)) return base
        return base + "-" + shortHash(artifact.groupId)
    }

    private fun isFolderComplete(folder: Path, extension: String): Boolean {
        val jar = folder.resolve("classes.jar")
        val dex = folder.resolve("classes.dex")
        if (!Files.isRegularFile(jar) || Files.size(jar) == 0L) return false
        if (!Files.isRegularFile(dex) || Files.size(dex) == 0L) return false
        if (extension == "aar" && !Files.isRegularFile(folder.resolve("config"))) return false
        return try {
            ZipFile(jar.toFile()).use { true }
        } catch (e: Exception) {
            false
        }
    }

    private fun isCacheValid(folder: Path, extension: String): Boolean {
        if (!buildSettings.isOfflineCacheEnabled) return false
        return isFolderComplete(folder, extension)
    }

    private fun deleteDirectory(folder: Path) {
        if (Files.exists(folder)) FileUtil.deleteFile(folder.toString())
    }

    private fun discardIncomplete(folder: Path) {
        if (!Files.exists(folder)) return
        val extension = if (Files.exists(folder.resolve("config"))) "aar" else "jar"
        if (!isFolderComplete(folder, extension)) deleteDirectory(folder)
    }

    private fun fetch(artifact: Artifact, callback: DependencyResolverCallback): Fetched {
        checkCancelled(artifact)
        val folder = folderFor(artifact)
        val dir = Paths.get(downloadPath, folder)
        val extension = artifact.extension

        if (isCacheValid(dir, extension)) {
            callback.onCacheHit(artifact)
            return Fetched(folder, true)
        }

        discardIncomplete(dir)
        Files.createDirectories(dir)

        val repository = artifact.repository
            ?: throw abort(
                ResolverFailure.Stage.REPOSITORY,
                artifact.toString(),
                "No repository was selected for this artifact."
            )
        val url = RepositoryProbe.fileUrl(
            repository.getURL(),
            artifact.groupId,
            artifact.artifactId,
            artifact.version,
            extension
        )
        val target = dir.resolve("classes.$extension").toFile()

        callback.onDownloadStart(artifact)
        try {
            FileDownloader.download(
                url,
                target,
                FileDownloader.Progress { bytes, total ->
                    callback.onDownloadProgress(artifact, bytes, total)
                },
                BooleanSupplier { cancelled }
            )
        } catch (e: IOException) {
            deleteDirectory(dir)
            if (cancelled) {
                throw abort(ResolverFailure.Stage.CANCELLED, artifact.toString(), "Cancelled by user")
            }
            callback.onDownloadError(artifact, e)
            throw abort(
                ResolverFailure.Stage.DOWNLOAD,
                artifact.toString(),
                FailureFormatter.describe(e),
                e
            )
        }
        callback.onDownloadEnd(artifact)

        if (extension == "aar") {
            callback.unzipping(artifact)
            try {
                unzip(target.toPath())
            } catch (e: Exception) {
                deleteDirectory(dir)
                throw abort(
                    ResolverFailure.Stage.UNZIP,
                    artifact.toString(),
                    "The downloaded AAR could not be extracted: ${FailureFormatter.describe(e)}",
                    e
                )
            }
            target.delete()
            val packageName = findPackageName(dir.toAbsolutePath().toString(), artifact.groupId)
            dir.resolve("config").writeText(packageName)
        }

        validateClassesJar(artifact, dir)
        return Fetched(folder, false)
    }

    private fun validateClassesJar(artifact: Artifact, dir: Path) {
        val jar = dir.resolve("classes.jar")
        if (!Files.isRegularFile(jar) || Files.size(jar) == 0L) {
            deleteDirectory(dir)
            throw abort(
                ResolverFailure.Stage.MISSING_CLASSES,
                artifact.toString(),
                "The ${artifact.extension.uppercase()} does not contain a classes.jar " +
                        "(resource-only libraries are not supported)."
            )
        }
        try {
            ZipFile(jar.toFile()).close()
        } catch (e: Exception) {
            deleteDirectory(dir)
            throw abort(
                ResolverFailure.Stage.UNZIP,
                artifact.toString(),
                "classes.jar is not a valid archive: ${FailureFormatter.describe(e)}",
                e
            )
        }
    }

    private fun dex(
        artifact: Artifact,
        folder: String,
        classpath: List<Path>,
        libraryJars: List<Path>,
        callback: DependencyResolverCallback
    ): Boolean {
        val dir = Paths.get(downloadPath, folder)
        val jar = dir.resolve("classes.jar")

        if (!jarHasClasses(jar.toFile())) {
            deleteDirectory(dir)
            callback.onResolutionComplete(artifact)
            return false
        }

        callback.dexing(artifact)
        try {
            compileJar(jar, classpath.filter { it != jar }, libraryJars)
        } catch (e: Exception) {
            deleteDirectory(dir)
            callback.dexingFailed(artifact, e)
            throw abort(
                ResolverFailure.Stage.DEX,
                artifact.toString(),
                FailureFormatter.describe(e),
                e
            )
        }

        val dex = dir.resolve("classes.dex")
        if (!Files.isRegularFile(dex) || Files.size(dex) == 0L) {
            deleteDirectory(dir)
            val error = IllegalStateException("D8 finished without producing classes.dex")
            callback.dexingFailed(artifact, error)
            throw abort(ResolverFailure.Stage.DEX, artifact.toString(), FailureFormatter.describe(error), error)
        }

        writeIdentity(dir.toFile(), identityOf(artifact))
        callback.onFolderResolved(artifact, folder)
        callback.onResolutionComplete(artifact)
        return true
    }

    private fun jarHasClasses(jar: File): Boolean {
        return try {
            ZipFile(jar).use { zip ->
                zip.entries().asSequence().any {
                    !it.isDirectory && it.name.endsWith(".class") && !it.name.endsWith("module-info.class")
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun baseClasspath(): MutableList<Path> {
        val list = mutableListOf<Path>()
        buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "").split(":").forEach {
            if (it.isNotEmpty()) list.add(Paths.get(it))
        }
        return list
    }

    private fun libraryJars(): List<Path> = listOf(
        BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"),
        Paths.get(
            buildSettings.getValue(
                BuildSettings.SETTING_ANDROID_JAR_PATH,
                BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
            )
        )
    )

    private suspend fun handleDirectUrlDownload(urlStr: String, callback: DependencyResolverCallback) {
        val parsed = try {
            URL(urlStr)
        } catch (e: Exception) {
            throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                urlStr,
                "Not a valid URL: ${FailureFormatter.describe(e)}",
                e
            )
        }
        val rawName = (parsed.path ?: "").substringAfterLast('/')
        val decoded = try {
            URLDecoder.decode(rawName, "UTF-8")
        } catch (e: Exception) {
            rawName
        }
        val lower = decoded.lowercase()
        val ext = when {
            lower.endsWith(".aar") -> "aar"
            lower.endsWith(".jar") -> "jar"
            else -> throw abort(
                ResolverFailure.Stage.INVALID_COORDINATE,
                urlStr,
                "A direct URL must point to a .aar or .jar file (found '${decoded.ifEmpty { "/" }}')."
            )
        }
        val baseName = decoded.substring(0, decoded.length - 4)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifEmpty { "direct-library" }

        val identity = "direct:$urlStr"
        val existing = readIdentity(File(downloadPath, baseName))
        val folderName = if (existing == null || existing == identity) {
            baseName
        } else {
            baseName + "-" + shortHash(urlStr)
        }

        val directDep = Artifact("direct", baseName).apply {
            version = "1.0"
            extension = ext
        }
        val outFolder = File(downloadPath, folderName)
        val outFile = File(outFolder, "classes.$ext")

        callback.onDirectDownloadStart(urlStr)
        callback.onDownloadStart(directDep)
        outFolder.mkdirs()
        try {
            FileDownloader.download(
                urlStr,
                outFile,
                FileDownloader.Progress { bytes, total ->
                    callback.onDownloadProgress(directDep, bytes, total)
                },
                BooleanSupplier { cancelled }
            )
        } catch (e: IOException) {
            deleteDirectory(outFolder.toPath())
            if (cancelled) {
                throw abort(ResolverFailure.Stage.CANCELLED, urlStr, "Cancelled by user")
            }
            callback.onDirectDownloadError(urlStr, e)
            callback.onDownloadError(directDep, e)
            throw abort(ResolverFailure.Stage.DOWNLOAD, urlStr, FailureFormatter.describe(e), e)
        }
        callback.onDownloadEnd(directDep)
        callback.onDirectDownloadEnd(decoded)

        if (ext == "aar") {
            callback.unzipping(directDep)
            try {
                unzip(outFile.toPath())
            } catch (e: Exception) {
                deleteDirectory(outFolder.toPath())
                throw abort(
                    ResolverFailure.Stage.UNZIP,
                    urlStr,
                    "The downloaded AAR could not be extracted: ${FailureFormatter.describe(e)}",
                    e
                )
            }
            outFile.delete()
            val pkgName = findPackageName(outFolder.absolutePath, "direct.download")
            File(outFolder, "config").writeText(pkgName)
        }

        validateClassesJar(directDep, outFolder.toPath())
        checkCancelled(directDep)

        val classpath = baseClasspath()
        if (!jarHasClasses(File(outFolder, "classes.jar"))) {
            deleteDirectory(outFolder.toPath())
            throw abort(
                ResolverFailure.Stage.MISSING_CLASSES,
                urlStr,
                "The downloaded archive contains no classes, so there is nothing to add to the project."
            )
        }

        callback.dexing(directDep)
        try {
            compileJar(File(outFolder, "classes.jar").toPath(), classpath, libraryJars())
        } catch (e: Exception) {
            deleteDirectory(outFolder.toPath())
            callback.dexingFailed(directDep, e)
            throw abort(ResolverFailure.Stage.DEX, urlStr, FailureFormatter.describe(e), e)
        }
        val dex = File(outFolder, "classes.dex")
        if (!dex.isFile || dex.length() == 0L) {
            deleteDirectory(outFolder.toPath())
            val error = IllegalStateException("D8 finished without producing classes.dex")
            callback.dexingFailed(directDep, error)
            throw abort(ResolverFailure.Stage.DEX, urlStr, FailureFormatter.describe(error), error)
        }

        writeIdentity(outFolder, identity)
        callback.onResolutionComplete(directDep)
        rootFolder = folderName
        callback.onTaskCompleted(listOf(folderName))
    }

    private fun findPackageName(path: String, defaultValue: String): String {
        val manifest =
            File(path).walk().filter { it.isFile && it.name == "AndroidManifest.xml" }.firstOrNull()
        val content = manifest?.readText() ?: return defaultValue
        val p = Pattern.compile("<manifest.*package=\"(.*?)\"", Pattern.DOTALL)
        val m = p.matcher(content)
        if (m.find()) {
            return m.group(1)!!
        }
        return defaultValue
    }

    private fun unzip(path: Path) {
        val base = path.parent.toAbsolutePath().normalize()
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = base.resolve(entry.name).normalize()
                if (!entryDestination.startsWith(base)) {
                    throw IOException("Blocked unsafe archive entry '${entry.name}'")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(entryDestination)
                } else {
                    Files.createDirectories(entryDestination.parent)
                    zip.getInputStream(entry).use { input ->
                        Files.newOutputStream(entryDestination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        }
    }

    private fun compileJar(jarFile: Path, jars: List<Path>, libraryJars: List<Path>) {
        Files.createDirectories(jarFile.parent)
        D8.run(
            D8Command.builder().setIntermediate(true).setMode(CompilationMode.RELEASE)
                .addProgramFiles(jarFile).addLibraryFiles(libraryJars).addClasspathFiles(jars)
                .setOutput(jarFile.parent, OutputMode.DexIndexed).build()
        )
    }
}
