package vn.unicom.thi360.feature.richcontent

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormulaEnginePinTest {

    private fun resolveAndroidRoot(): File {
        val candidates = listOf(File("."), File(".."), File("apps/android"))
        return candidates.map { it.canonicalFile }.firstOrNull {
            File(it, "gradle/libs.versions.toml").isFile && File(it, "app/gradle.lockfile").isFile
        } ?: error("Cannot locate apps/android root from ${File(".").canonicalPath}")
    }

    @Test
    fun ratexVersionIsExactPinInVersionCatalogAndMatchesGradleLockfile() {
        val androidRoot = resolveAndroidRoot()
        val tomlText = File(androidRoot, "gradle/libs.versions.toml").readText(Charsets.UTF_8)
        val lockLines = File(androidRoot, "app/gradle.lockfile").readLines(Charsets.UTF_8)

        val versionMatch = Regex("""(?m)^\s*ratex\s*=\s*"([^"]+)"\s*$""").find(tomlText)
        assertNotNull("Expected ratex version entry in libs.versions.toml", versionMatch)
        val pinnedVersion = versionMatch!!.groupValues[1]

        assertFalse("Pinned version must not contain '+'", pinnedVersion.contains('+'))
        assertFalse("Pinned version must not contain 'latest'", pinnedVersion.contains("latest", ignoreCase = true))
        assertFalse("Pinned version must not be a range", pinnedVersion.any { it in "[]()," })
        assertTrue(
            "Pinned version '$pinnedVersion' must be an exact semver",
            Regex("""^\d+\.\d+\.\d+$""").matches(pinnedVersion),
        )
        assertEquals("0.1.16", pinnedVersion)

        assertTrue(
            "libs.versions.toml must declare io.github.erweixin:ratex-android with version.ref = \"ratex\"",
            Regex("""ratex-android\s*=\s*\{\s*group\s*=\s*"io\.github\.erweixin",\s*name\s*=\s*"ratex-android",\s*version\.ref\s*=\s*"ratex"\s*\}""")
                .containsMatchIn(tomlText),
        )

        val ratexLockLines = lockLines.filter { it.startsWith("io.github.erweixin:ratex-android:") }
        assertEquals(
            "gradle.lockfile must pin exactly one io.github.erweixin:ratex-android version, found: $ratexLockLines",
            1,
            ratexLockLines.size,
        )
        val lockedLine = ratexLockLines.single()
        val lockedVersion = lockedLine.substringAfter("io.github.erweixin:ratex-android:").substringBefore('=')
        assertEquals(pinnedVersion, lockedVersion)
        assertTrue(
            "gradle.lockfile must lock devDebugRuntimeClasspath for ratex-android",
            lockedLine.contains("devDebugRuntimeClasspath"),
        )
        assertTrue(
            "gradle.lockfile must lock demoDebugRuntimeClasspath for ratex-android",
            lockedLine.contains("demoDebugRuntimeClasspath"),
        )
    }
}
