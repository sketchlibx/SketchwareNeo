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
import org.cosmic.ide.dependency.resolver.getArtifact
import org.cosmic.ide.dependency.resolver.repositories
import neo.sketchware.utility.FileUtil
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Pattern
import java.util.zip.ZipFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DependencyResolver(
    private val groupId: String,
    private val artifactId: String,
    private val version: String,
    private val skipDependencies: Boolean,
    private val buildSettings: BuildSettings
) {
    companion object {
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
    }

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
                        return it["name"] as String
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
        
        open fun onDirectDownloadStart(url: String) {}
        open fun onDirectDownloadEnd(fileName: String) {}
        open fun onDirectDownloadError(url: String, error: Throwable) {}
    }

    fun resolveDependency(callback: DependencyResolverCallback) = runBlocking {
        eventReciever = callback

        val isDirectUrl = groupId.startsWith("http://") || groupId.startsWith("https://")
        if (isDirectUrl) {
            handleDirectUrlDownload(groupId, callback)
            return@runBlocking
        }

        val dependency = getArtifact(groupId, artifactId, version) ?: return@runBlocking

        if (dependency.extension != "jar" && dependency.extension != "aar") {
            callback.invalidPackaging(dependency)
            return@runBlocking
        }

        val libraryJars = listOf(
            BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath()
                .resolve("core-lambda-stubs.jar"), Paths.get(
                buildSettings.getValue(
                    BuildSettings.SETTING_ANDROID_JAR_PATH,
                    BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath
                )
            )
        )
        val dependencyClasspath = mutableListOf<Path>()

        val classpath = buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")

        classpath.split(":").forEach {
            if (it.isEmpty()) return@forEach
            dependencyClasspath.add(Paths.get(it))
        }

        dependency.downloadTo(
            File(downloadPath + "/${dependency.artifactId}-v${dependency.version}/classes.${dependency.extension}")
                .apply {
                    parentFile?.mkdirs()
                }
        )

        if (dependency.extension == "aar") {
            callback.unzipping(dependency)
            unzip(
                Paths.get(
                    downloadPath,
                    "${dependency.artifactId}-v${dependency.version}",
                    "classes.aar"
                )
            )
            Files.delete(
                Paths.get(
                    downloadPath,
                    "${dependency.artifactId}-v${dependency.version}",
                    "classes.aar"
                )
            )
            val packageName = findPackageName(
                Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}")
                    .toAbsolutePath().toString(),
                dependency.groupId
            )
            Paths.get(downloadPath, "${dependency.artifactId}-v${dependency.version}", "config")
                .writeText(packageName)
        }

        val jar = Paths.get(
            downloadPath,
            "${dependency.artifactId}-v${dependency.version}",
            "classes.jar"
        )

        callback.dexing(dependency)
        try {
            compileJar(jar, dependencyClasspath, libraryJars)
            callback.onResolutionComplete(dependency)
        } catch (e: Exception) {
            callback.dexingFailed(dependency, e)
        }

        if (skipDependencies) {
            callback.onSkippingResolution(dependency)
            callback.onTaskCompleted(listOf("${dependency.artifactId}-v${dependency.version}"))
            return@runBlocking
        }
        dependency.resolveDependencyTree()

        dependency.getAllDependencies().forEach { dep ->
            println("Resolving dependency: ${dep.artifactId} v${dep.version}")
            if (dep.extension != "jar" && dep.extension != "aar") {
                callback.invalidPackaging(dep)
                return@forEach
            }

            if (dep.version.isEmpty()) {
                callback.onVersionNotFound(dep)
                return@forEach
            }

            val path = Paths.get(
                downloadPath,
                "${dep.artifactId}-v${dep.version}",
                "classes.${dep.extension}"
            )

            Files.createDirectories(path.parent)

            dep.downloadTo(File(path.toString()))

            if (dep.extension == "aar") {
                callback.unzipping(dep)
                unzip(path)
                Files.delete(path)
                val packageName =
                    findPackageName(path.parent.toAbsolutePath().toString(), dep.groupId)
                path.parent.resolve("config").writeText(packageName)
            }

            val targetJar = if (dep.extension == "jar") path else Paths.get(
                downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar"
            )
            if (Files.notExists(targetJar)) {
                callback.onDependenciesNotFound(dep)
                return@forEach
            }

            dependencyClasspath.add(targetJar)
        }

        dependency.getAllDependencies().forEach { dep ->
            val targetJar = Paths.get(downloadPath, "${dep.artifactId}-v${dep.version}", "classes.jar")

            callback.dexing(dep)
            try {
                compileJar(
                    targetJar, dependencyClasspath.toMutableList().apply { remove(targetJar) }, libraryJars
                )
                callback.onResolutionComplete(dep)
            } catch (e: Exception) {
                callback.dexingFailed(dep, e)
                return@forEach
            }
        }

        val completedList = mutableListOf("${dependency.artifactId}-v${dependency.version}")
        completedList.addAll(dependency.getAllDependencies().map { "${it.artifactId}-v${it.version}" })
        callback.onTaskCompleted(completedList)
    }

    private fun handleDirectUrlDownload(urlStr: String, callback: DependencyResolverCallback) {
        val fileName = urlStr.substringAfterLast("/")
        var ext = "jar"
        if (fileName.contains(".aar", true)) {
            ext = "aar"
        }
        
        val folderName = fileName.substringBeforeLast(".")
        
        val directDep = Artifact("direct", folderName).apply {
            version = "1.0"
            extension = ext
        }
        
        callback.onDirectDownloadStart(urlStr)
        callback.onDownloadStart(directDep)
        
        val outFolder = File(downloadPath, folderName)
        outFolder.mkdirs()
        val outFile = File(outFolder, "classes.$ext")

        try {
            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("Server returned HTTP ${connection.responseCode} ${connection.responseMessage}")
            }

            val input = connection.inputStream
            val output = FileOutputStream(outFile)
            
            val buffer = ByteArray(4096)
            var bytesRead = -1
            while (input.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.close()
            input.close()

            callback.onDownloadEnd(directDep)
            callback.onDirectDownloadEnd(fileName)

            if (ext == "aar") {
                callback.unzipping(directDep)
                unzip(outFile.toPath())
                outFile.delete()
                val pkgName = findPackageName(outFolder.absolutePath, "direct.download")
                File(outFolder, "config").writeText(pkgName)
            }

            val targetJar = File(outFolder, "classes.jar")
            
            val libraryJars = listOf(
                BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.toPath().resolve("core-lambda-stubs.jar"), 
                Paths.get(buildSettings.getValue(BuildSettings.SETTING_ANDROID_JAR_PATH, BuiltInLibraries.EXTRACTED_COMPILE_ASSETS_PATH.resolve("android.jar").absolutePath))
            )
            
            val classpath = buildSettings.getValue(BuildSettings.SETTING_CLASSPATH, "")
            val dependencyClasspath = mutableListOf<Path>()
            classpath.split(":").forEach {
                if (it.isNotEmpty()) dependencyClasspath.add(Paths.get(it))
            }

            callback.dexing(directDep)
            try {
                compileJar(targetJar.toPath(), dependencyClasspath, libraryJars)
                callback.onResolutionComplete(directDep)
            } catch (e: Exception) {
                callback.dexingFailed(directDep, e)
                return
            }

            callback.onTaskCompleted(listOf(folderName))

        } catch (e: Exception) {
            callback.onDirectDownloadError(urlStr, e)
            callback.onDownloadError(directDep, e)
        }
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
        val zipFile = ZipFile(path.toFile())
        zipFile.use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val entryDestination = path.parent.resolve(entry.name)
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