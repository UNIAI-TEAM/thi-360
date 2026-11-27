package vn.unicom.thi360

import android.content.pm.ActivityInfo
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.shell.OrientationMode
import vn.unicom.thi360.shell.OrientationPolicy

@RunWith(RobolectricTestRunner::class)
class OrientationPolicyTest {

    private fun resolveMainKotlinDir(): File {
        val candidates = listOf(
            File("src/main/java/vn/unicom/thi360"),
            File("app/src/main/java/vn/unicom/thi360"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/java/vn/unicom/thi360")
    }

    @Test
    fun policyLocksPortraitBelow600dpAndUnlocksAtOrAbove600dp() {
        assertEquals(OrientationMode.PORTRAIT_LOCKED, OrientationPolicy.resolve(360))
        assertEquals(OrientationMode.PORTRAIT_LOCKED, OrientationPolicy.resolve(412))
        assertEquals(OrientationMode.PORTRAIT_LOCKED, OrientationPolicy.resolve(599))
        assertEquals(OrientationMode.UNRESTRICTED, OrientationPolicy.resolve(600))
        assertEquals(OrientationMode.UNRESTRICTED, OrientationPolicy.resolve(720))
        assertEquals(OrientationMode.UNRESTRICTED, OrientationPolicy.resolve(800))
    }

    @Test
    @Config(qualifiers = "sw412dp-w412dp-h915dp")
    fun mainActivityLocksPortraitOnPhoneSmallestWidth() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                controller.get().requestedOrientation,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "sw720dp-w840dp-h720dp")
    fun mainActivityAllowsRotationOnLargeScreenSmallestWidth() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                controller.get().requestedOrientation,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    @Config(qualifiers = "sw412dp-w412dp-h915dp")
    fun portalRoutesKeepPolicy() {
        val kotlinRoot = resolveMainKotlinDir()
        val offenders = mutableListOf<String>()
        kotlinRoot.walkTopDown()
            .filter {
                it.isFile &&
                    it.extension == "kt" &&
                    it.name != "MainActivity.kt" &&
                    it.name != "OrientationPolicy.kt"
            }
            .forEach { file ->
                val text = file.readText(Charsets.UTF_8)
                if (text.contains("requestedOrientation") || text.contains("SCREEN_ORIENTATION_")) {
                    offenders += file.relativeTo(kotlinRoot).path
                }
            }
        assertTrue(
            "No portal route or screen may override requestedOrientation; found: $offenders",
            offenders.isEmpty(),
        )

        val manifestFile = File(
            kotlinRoot.canonicalFile.parentFile!!.parentFile!!.parentFile!!.parentFile!!,
            "AndroidManifest.xml",
        )
        val manifestText = manifestFile.readText(Charsets.UTF_8)
        assertTrue(
            "AndroidManifest.xml must not lock activity screenOrientation statically",
            !manifestText.contains("android:screenOrientation"),
        )

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            assertEquals(
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                controller.get().requestedOrientation,
            )
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
