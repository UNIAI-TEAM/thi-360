package vn.unicom.thi360.core.brand

import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.R
import vn.unicom.thi360.Thi360Application

@RunWith(RobolectricTestRunner::class)
class LauncherIconTest {

    private fun resolveAppDir(): File {
        val candidates = listOf(File("."), File("app"))
        return candidates
            .map { it.canonicalFile }
            .firstOrNull { File(it, "build.gradle.kts").isFile && File(it, "src/main/res").isDirectory }
            ?: error("Cannot locate apps/android/app directory")
    }

    @Test
    fun adaptiveLauncherIconsHaveWhiteBackgroundSafeZoneForegroundsAndDeterministicBitmaps() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val appDir = resolveAppDir()
        val resDir = File(appDir, "src/main/res")

        // 1. Both ic_launcher and ic_launcher_round resolve as AdaptiveIconDrawable.
        val launcherDrawable = context.getDrawable(R.mipmap.ic_launcher)
        val roundDrawable = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue("ic_launcher must be AdaptiveIconDrawable", launcherDrawable is AdaptiveIconDrawable)
        assertTrue("ic_launcher_round must be AdaptiveIconDrawable", roundDrawable is AdaptiveIconDrawable)

        // 2. Background drawable is solid #FFFFFF.
        val bgXml = File(resDir, "drawable/ic_launcher_background.xml").readText(Charsets.UTF_8)
        assertTrue("ic_launcher_background.xml must use #FFFFFF background", bgXml.contains("#FFFFFF", ignoreCase = true))

        val densities = linkedMapOf(
            "mdpi" to 1.0,
            "hdpi" to 1.5,
            "xhdpi" to 2.0,
            "xxhdpi" to 3.0,
            "xxxhdpi" to 4.0,
        )

        // 3. Check every density mipmap-*/ic_launcher_foreground.png geometry & safe-zone circle (66dp diameter = 33dp radius).
        for ((qualifier, scale) in densities) {
            val file = File(resDir, "mipmap-$qualifier/ic_launcher_foreground.png")
            assertTrue("Missing adaptive foreground bitmap: ${file.path}", file.isFile)
            val img = ImageIO.read(file)
            val expectedSize = (108.0 * scale).toInt()
            assertEquals("Expected ${expectedSize}px width for mipmap-$qualifier", expectedSize, img.width)
            assertEquals("Expected ${expectedSize}px height for mipmap-$qualifier", expectedSize, img.height)

            val center = expectedSize / 2.0
            val maxAllowedRadius = 33.0 * scale + 0.75
            var minX = img.width
            var minY = img.height
            var maxX = -1
            var maxY = -1
            for (y in 0 until img.height) {
                for (x in 0 until img.width) {
                    val alpha = (img.getRGB(x, y) ushr 24) and 0xFF
                    if (alpha > 0) {
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                        val dist = hypot((x + 0.5) - center, (y + 0.5) - center)
                        assertTrue(
                            "Opaque pixel ($x,$y) in mipmap-$qualifier exceeds 33dp safe radius ($dist > $maxAllowedRadius)",
                            dist <= maxAllowedRadius,
                        )
                    }
                }
            }
            assertTrue("mipmap-$qualifier must contain opaque pixels", maxX >= minX && maxY >= minY)
            val bboxW = maxX - minX + 1
            val bboxH = maxY - minY + 1
            assertTrue("Foreground mark bounding box width $bboxW must not upscale beyond 700px", bboxW <= 700)
            assertTrue("Foreground mark bounding box height $bboxH must not upscale beyond 700px", bboxH <= 700)
        }

        // 4. Verify byte-for-byte determinism by regenerating into a temp directory using the exact algorithm from thi360-icon.png.
        val sourcePng = File(
            appDir.parentFile!!.parentFile!!.parentFile!!,
            "docs/design/app-hoc-vien/brand/thi360-icon.png",
        )
        assertTrue("Missing brand source icon: ${sourcePng.canonicalPath}", sourcePng.isFile)

        val tempOut = Files.createTempDirectory("thi360-brand-regen-").toFile()
        try {
            val isWindows = (System.getProperty("os.name") ?: "").lowercase().contains("win")
            val gradlew = File(appDir.parentFile, if (isWindows) "gradlew.bat" else "gradlew")
            if (gradlew.isFile && System.getProperty("thi360.skipNestedGradleCheck") != "true") {
                val pb = ProcessBuilder(
                    gradlew.canonicalPath,
                    ":app:generateBrandBitmaps",
                    "-PbrandBitmapsOutputDir=${tempOut.canonicalPath}",
                    "--no-daemon",
                    "--console=plain",
                ).directory(appDir.parentFile)
                pb.redirectErrorStream(true)
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText()
                val exitCode = proc.waitFor()
                assertEquals("generateBrandBitmaps task failed:\n$out", 0, exitCode)

                for (qualifier in densities.keys) {
                    val committedFg = File(resDir, "mipmap-$qualifier/ic_launcher_foreground.png")
                    val regenFg = File(tempOut, "mipmap-$qualifier/ic_launcher_foreground.png")
                    assertArrayEquals(
                        "mipmap-$qualifier/ic_launcher_foreground.png differs from generateBrandBitmaps output",
                        committedFg.readBytes(),
                        regenFg.readBytes(),
                    )
                    val committedMark = File(resDir, "drawable-$qualifier/ic_brand_mark.png")
                    val regenMark = File(tempOut, "drawable-$qualifier/ic_brand_mark.png")
                    assertArrayEquals(
                        "drawable-$qualifier/ic_brand_mark.png differs from generateBrandBitmaps output",
                        committedMark.readBytes(),
                        regenMark.readBytes(),
                    )
                }
            }
        } finally {
            tempOut.deleteRecursively()
        }
    }
}
