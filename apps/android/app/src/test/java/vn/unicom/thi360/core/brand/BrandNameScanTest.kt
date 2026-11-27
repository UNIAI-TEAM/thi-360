package vn.unicom.thi360.core.brand

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandNameScanTest {

    private fun resolveAppDir(): File {
        val candidates = listOf(File("."), File("app"))
        return candidates.firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
            ?: error("Cannot locate apps/android/app directory from ${File(".").canonicalPath}")
    }

    private fun scanForLegacyBrandName(targets: List<File>, baseDir: File): List<String> {
        val hits = mutableListOf<String>()
        val textExtensions = setOf("kt", "java", "xml", "kts", "json", "pro", "txt")

        fun scanFile(file: File) {
            if (file.extension.lowercase() !in textExtensions) return
            val rel = file.relativeToOrSelf(baseDir).path.replace('\\', '/')
            file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                if (line.contains("Thi 360")) {
                    hits += "$rel:${idx + 1}: ${line.trim()}"
                }
            }
        }

        for (target in targets) {
            if (target.isFile) {
                scanFile(target)
            } else if (target.isDirectory) {
                target.walkTopDown().filter { it.isFile }.forEach(::scanFile)
            }
        }
        return hits
    }

    @Test
    fun mainSourcesResourcesAndBuildGradleContainZeroOccurrencesOfLegacyThiSpace360() {
        val appDir = resolveAppDir()
        val hits = scanForLegacyBrandName(
            targets = listOf(File(appDir, "src/main"), File(appDir, "build.gradle.kts")),
            baseDir = appDir,
        )
        assertTrue(
            "Found legacy 'Thi 360' string in app sources/resources/build.gradle.kts:\n${hits.joinToString("\n")}",
            hits.isEmpty(),
        )
    }

    @Test
    fun scannerDetectsInsertedLegacyBrandNameInTempStringCopy() {
        val tempDir = Files.createTempDirectory("thi360-brand-scan-red-").toFile()
        try {
            val copy = File(tempDir, "strings_shell.xml")
            copy.writeText("""<resources><string name="app_name">Thi 360</string></resources>""", Charsets.UTF_8)
            val hits = scanForLegacyBrandName(listOf(tempDir), tempDir)
            assertTrue("Scanner must report inserted 'Thi 360' with filename and line number", hits.isNotEmpty())
            assertTrue(hits.single().contains("strings_shell.xml:1"))
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
