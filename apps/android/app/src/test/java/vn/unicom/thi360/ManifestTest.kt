package vn.unicom.thi360

import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    @Test
    fun appMetadataMatchesSpec() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals("vn.unicom.thi360", context.packageName)
        assertEquals("Thi 360", packageManager.getApplicationLabel(appInfo).toString())
        assertEquals(26, appInfo.minSdkVersion)
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(
            "Cleartext traffic must be disabled at application level",
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
        )
    }

    @Test
    fun mainActivityIsLauncherAndExported() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(context.packageName)
        }
        val resolved = context.packageManager.queryIntentActivities(
            launcherIntent,
            0,
        )

        assertEquals(1, resolved.size)
        val activityInfo = resolved.single().activityInfo
        assertNotNull(activityInfo)
        assertEquals(MainActivity::class.java.name, activityInfo.name)
        assertTrue(activityInfo.exported)
    }
}
