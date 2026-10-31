package mod.pranav.build

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.HashSet
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object JarBuilder {
    @Throws(IOException::class)
    fun generateJar(classesDir: File) {
        if (!classesDir.exists() || !classesDir.isDirectory) {
            throw IOException("Classes directory does not exist or is not a directory: ${classesDir.absolutePath}")
        }

        val outputFile = File(classesDir.parentFile, "${classesDir.name}.jar")
        val temporaryFile = File(outputFile.parentFile, "${outputFile.name}.tmp")
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw IOException("Could not clear temporary classes JAR: ${temporaryFile.absolutePath}")
        }
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Could not remove stale classes JAR before validation: ${outputFile.absolutePath}")
        }

        val root = classesDir.canonicalFile
        val classFiles = ArrayList<File>()
        collectClassFiles(root, root, classFiles, HashSet())
        if (classFiles.isEmpty()) {
            throw IOException("No valid .class files were found under ${classesDir.absolutePath}")
        }
        classFiles.sortBy { it.relativeTo(root).invariantSeparatorsPath }

        try {
            val manifest = Manifest().apply {
                mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
            }
            JarOutputStream(FileOutputStream(temporaryFile), manifest).use { output ->
                for (classFile in classFiles) {
                    val entryName = classFile.relativeTo(root).invariantSeparatorsPath
                    output.putNextEntry(JarEntry(entryName).apply { time = classFile.lastModified() })
                    FileInputStream(classFile).use { input -> input.copyTo(output) }
                    output.closeEntry()
                }
            }

            val actualClassCount = JarFile(temporaryFile).use { jar ->
                val entries = jar.entries()
                var count = 0
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (!entry.isDirectory && entry.name.endsWith(".class")) count++
                }
                count
            }
            if (!temporaryFile.isFile || temporaryFile.length() <= 0L || actualClassCount != classFiles.size) {
                throw IOException("Classes JAR validation failed: expected ${classFiles.size} class entries, found $actualClassCount")
            }

            try {
                Files.move(temporaryFile.toPath(), outputFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            if (!outputFile.isFile || outputFile.length() <= 0L) {
                throw IOException("The validated classes JAR could not be committed: ${outputFile.absolutePath}")
            }
        } catch (failure: Exception) {
            temporaryFile.delete()
            outputFile.delete()
            if (failure is IOException) throw failure
            throw IOException("Could not generate a validated classes JAR: ${failure.message}", failure)
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    @Throws(IOException::class)
    private fun collectClassFiles(root: File, directory: File, result: MutableList<File>, visited: MutableSet<String>) {
        val canonicalDirectory = directory.canonicalFile
        val rootPath = root.path + File.separator
        if (canonicalDirectory != root && !canonicalDirectory.path.startsWith(rootPath)) {
            throw IOException("Class output contains a path outside the compiler directory: ${directory.absolutePath}")
        }
        if (!visited.add(canonicalDirectory.path)) {
            throw IOException("Class output contains a recursive directory link: ${directory.absolutePath}")
        }

        val children = directory.listFiles()
            ?: throw IOException("Could not list compiler output directory: ${directory.absolutePath}")
        children.sortBy { it.name }
        for (child in children) {
            if (child.isDirectory) {
                collectClassFiles(root, child, result, visited)
            } else if (child.isFile && child.name.endsWith(".class")) {
                if (!isValidClassFile(child)) {
                    throw IOException("Compiler output contains an invalid or truncated class file: ${child.absolutePath}")
                }
                val canonicalFile = child.canonicalFile
                if (!canonicalFile.path.startsWith(rootPath)) {
                    throw IOException("Class output contains a file outside the compiler directory: ${child.absolutePath}")
                }
                result.add(child)
            }
        }
    }

    private fun isValidClassFile(file: File): Boolean {
        if (!file.isFile || file.length() < 8L) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 &&
                    (magic[0].toInt() and 0xff) == 0xca &&
                    (magic[1].toInt() and 0xff) == 0xfe &&
                    (magic[2].toInt() and 0xff) == 0xba &&
                    (magic[3].toInt() and 0xff) == 0xbe
            }
        } catch (failure: IOException) {
            false
        }
    }
}
