package vn.unicom.thi360.core.network

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class HandwrittenApiCallScanTest {

    private fun resolveMainKotlinDir(): File {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360"),
            File("app/src/main/java/vn/unicom/thi360"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/java/vn/unicom/thi360")
    }

    /**
     * Explicit allowlist for non-generated HTTP usage (each entry requires a concrete justification):
     * - core/appearance/AppearanceRepository.kt: fetches static web appearance-tokens.json (Phase 1, not an /api/v1/ endpoint)
     * - core/image/ImageLoader.kt: binary image loader (Coil 3 OkHttp client, host-scoped Bearer)
     * - core/network/Thi360ApiFactory.kt: constructs OkHttpClient / Retrofit ApiClient for generated APIs
     * - core/network/AuthInterceptors.kt: OkHttp interceptors / TokenAuthenticator for generated APIs
     * - core/network/RedactingHttpLogger.kt: OkHttp redacting logging interceptor
     */
    private val allowlist = setOf(
        "core/appearance/AppearanceRepository.kt",
        "core/image/ImageLoader.kt",
        "core/network/Thi360ApiFactory.kt",
        "core/network/AuthHeaderInterceptor.kt",
        "core/network/RedactingHttpLogger.kt",
    )

    private val forbiddenPatterns = listOf(
        "/api/v1/" to Regex("""/api/v1/"""),
        "Request.Builder" to Regex("""Request\.Builder\s*\("""),
        "HttpURLConnection" to Regex("""\bHttpURLConnection\b"""),
        "URL(...).openConnection" to Regex("""\.openConnection\s*\("""),
        "newCall(" to Regex("""\.newCall\s*\("""),
        "Retrofit.Builder" to Regex("""Retrofit\.Builder\s*\("""),
        "Retrofit HTTP annotation" to Regex("""@(GET|POST|PUT|PATCH|DELETE)\s*\("""),
    )

    @Test
    fun noHandwrittenBusinessApiCallsExistOutsideGeneratedClientAndAllowlist() {
        val root = resolveMainKotlinDir()
        val violations = scanDirectory(root, allowlist)
        assertTrue(
            "Found handwritten HTTP / /api/v1/ usage outside generated client and allowlist:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    private fun scanDirectory(root: File, allowedRelativePaths: Set<String>): List<String> {
        val findings = mutableListOf<String>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val rel = file.relativeTo(root).path.replace('\\', '/')
                if (rel in allowedRelativePaths) return@forEach
                file.readLines(Charsets.UTF_8).forEachIndexed { idx, line ->
                    for ((label, regex) in forbiddenPatterns) {
                        if (regex.containsMatchIn(line)) {
                            findings += "$rel:${idx + 1} [$label]: ${line.trim()}"
                        }
                    }
                }
            }
        return findings
    }
}
