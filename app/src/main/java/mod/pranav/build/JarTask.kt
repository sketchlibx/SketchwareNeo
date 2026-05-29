package mod.pranav.build

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

object JarBuilder {
    
    @Throws(IOException::class)
    fun generateJar(classesDir: File) {
        // Safe check to avoid empty or missing directories
        if (!classesDir.exists() || !classesDir.isDirectory) {
            throw IOException("Classes directory does not exist or is not a directory: ${classesDir.absolutePath}")
        }

        // Dynamically naming the jar based on the parent folder name
        val outputFile = File(classesDir.parent, "${classesDir.name}.jar")
        
        val manifest = Manifest().apply {
            mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
        }

        JarOutputStream(FileOutputStream(outputFile), manifest).use { out ->
            classesDir.listFiles()?.forEach { file ->
                add(classesDir, file, out)
            }
        }
    }

    @Throws(IOException::class)
    private fun add(rootDir: File, source: File, target: JarOutputStream) {
        var entryName = source.relativeTo(rootDir).invariantSeparatorsPath

        if (source.isDirectory) {
            if (entryName.isNotEmpty()) {
                if (!entryName.endsWith("/")) entryName += "/"

                val entry = JarEntry(entryName).apply {
                    time = source.lastModified()
                }
                target.putNextEntry(entry)
                target.closeEntry()
            }
            
            // Safely iterate through child files
            source.listFiles()?.forEach { nestedFile ->
                add(rootDir, nestedFile, target)
            }
            return
        }

        val entry = JarEntry(entryName).apply {
            time = source.lastModified()
        }
        target.putNextEntry(entry)
        
        FileInputStream(source).use { input ->
            input.copyTo(target)
        }
        
        target.closeEntry()
    }
}
