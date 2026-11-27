package vn.unicom.thi360.core.brand

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderLogoScanTest {

    private fun resolveAppDir(): File {
        val candidates = listOf(File("."), File("app"))
        return candidates.firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
            ?: error("Cannot locate apps/android/app directory")
    }

    @Test
    fun legacyPhase1PlaceholderLogoIsDeletedAndNeverReferenced() {
        val appDir = resolveAppDir()
        assertFalse(
            "res/drawable/ic_thi360_logo.xml must be deleted",
            File(appDir, "src/main/res/drawable/ic_thi360_logo.xml").exists(),
        )
        assertFalse(
            "Legacy vector res/drawable/ic_launcher_foreground.xml must be deleted",
            File(appDir, "src/main/res/drawable/ic_launcher_foreground.xml").exists(),
        )

        val violations = mutableListOf<String>()
        File(appDir, "src/main").walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("kt", "java", "xml") }
            .forEach { file ->
                val rel = file.relativeTo(appDir).path.replace('\\', '/')
                file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                    if (line.contains("ic_thi360_logo")) {
                        violations += "$rel:${idx + 1}: ${line.trim()}"
                    }
                }
            }

        assertTrue(
            "Found leftover references to legacy placeholder logo ic_thi360_logo:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
