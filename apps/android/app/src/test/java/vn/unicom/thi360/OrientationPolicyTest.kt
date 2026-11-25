package vn.unicom.thi360

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.shell.OrientationMode
import vn.unicom.thi360.shell.OrientationPolicy

@RunWith(RobolectricTestRunner::class)
class OrientationPolicyTest {

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
}
