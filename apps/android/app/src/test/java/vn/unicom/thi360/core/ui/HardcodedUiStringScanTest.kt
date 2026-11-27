package vn.unicom.thi360.core.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HardcodedUiStringScanTest {

    private fun resolveMainKotlinDir(): File {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360"),
            File("app/src/main/java/vn/unicom/thi360"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/java/vn/unicom/thi360")
    }

    // Scans portal UI packages (core/ui, core/brand, core/legal, shell/routes) for hardcoded literals.
    // Allowlist holds only Wave-1 RoutePlaceholderScreen and route placeholder files.
    private val allowlist = setOf(
        "shell/routes/RoutePlaceholderScreen.kt",
        "shell/routes/CatalogRoutes.kt",
        "shell/routes/PaymentRoutes.kt",
        "shell/routes/MyExamsRoutes.kt",
        "shell/routes/PrepareRoutes.kt",
        "shell/routes/AttemptRoutes.kt",
        "shell/routes/ResultRoutes.kt",
        "shell/routes/ProfileRoutes.kt",
    )

    private val scannedSubdirs = listOf(
        "core/ui",
        "core/brand",
        "core/legal",
        "shell/routes",
    )

    private val hardcodedTextCall = Regex("""\bText\s*\(\s*(text\s*=\s*)?"([^"$\\]+)"""")
    private val hardcodedContentDesc = Regex("""contentDescription\s*=\s*"([^"$\\]+)"""")

    @Test
    fun portalUiComponentsUseStringResourcesInsteadOfHardcodedLiterals() {
        val root = resolveMainKotlinDir()
        val violations = mutableListOf<String>()

        for (sub in scannedSubdirs) {
            val dir = File(root, sub)
            if (!dir.isDirectory) continue
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file ->
                    val rel = file.relativeTo(root).path.replace('\\', '/')
                    if (rel in allowlist) return@forEach
                    file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                        val textMatch = hardcodedTextCall.find(line)
                        if (textMatch != null && textMatch.groupValues[2].any { it.isLetter() }) {
                            violations += "$rel:${idx + 1} [hardcoded Text]: ${line.trim()}"
                        }
                        val descMatch = hardcodedContentDesc.find(line)
                        if (descMatch != null && descMatch.groupValues[1].any { it.isLetter() }) {
                            violations += "$rel:${idx + 1} [hardcoded contentDescription]: ${line.trim()}"
                        }
                    }
                }
        }

        assertTrue(
            "Found hardcoded UI string literals in portal UI code:\n${violations.joinToString("\n")}",
            violations.isEmpty(),
        )
    }
}
