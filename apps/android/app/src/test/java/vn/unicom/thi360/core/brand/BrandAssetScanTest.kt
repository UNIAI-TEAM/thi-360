package vn.unicom.thi360.core.brand

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetScanTest {

    private fun resolveAppDir(): File {
        val candidates = listOf(File("."), File("app"))
        return candidates.firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main").isDirectory }
            ?: error("Cannot locate apps/android/app directory")
    }

    @Test
    fun noWordmarkOrRawSvgBrandAssetsExistInAppSourcesOrResources() {
        val appDir = resolveAppDir()
        val mainDir = File(appDir, "src/main")
        val forbiddenTerms = listOf("thi360-wordmark", "thi360_wordmark", "wordmark", "thi360-icon.svg")
        val hits = mutableListOf<String>()

        mainDir.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val rel = file.relativeTo(appDir).path.replace('\\', '/')
                if (forbiddenTerms.any { rel.lowercase().contains(it) }) {
                    hits += "Forbidden file path: $rel"
                }
                if (file.extension.lowercase() in setOf("kt", "java", "xml", "json")) {
                    file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                        for (term in forbiddenTerms) {
                            if (line.lowercase().contains(term)) {
                                hits += "$rel:${idx + 1}: ${line.trim()}"
                            }
                        }
                    }
                }
                if (file.name == "ic_brand_mark.png") {
                    val img = ImageIO.read(file)
                    assertTrue(
                        "Brand mark image $rel must be square (icon only, not wide wordmark): ${img.width}x${img.height}",
                        img.width == img.height,
                    )
                }
            }

        val buildGradle = File(appDir, "build.gradle.kts").readText(Charsets.UTF_8)
        assertFalse("build.gradle.kts must not reference thi360-wordmark.png", buildGradle.contains("wordmark", ignoreCase = true))
        assertFalse("build.gradle.kts must not reference thi360-icon.svg", buildGradle.contains("thi360-icon.svg", ignoreCase = true))

        assertTrue("Found forbidden wordmark/svg brand asset references:\n${hits.joinToString("\n")}", hits.isEmpty())
    }
}
