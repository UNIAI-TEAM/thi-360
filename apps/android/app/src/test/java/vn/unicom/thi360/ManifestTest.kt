package vn.unicom.thi360

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.w3c.dom.Element
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class ManifestTest {

    private fun resolveMainSourceDir(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull { File(it, "AndroidManifest.xml").isFile }
            ?: error("Cannot locate src/main/AndroidManifest.xml from ${File(".").canonicalPath}")
    }

    @Test
    fun appMetadataMatchesSpec() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(context.packageName, 0)

        assertEquals("vn.unicom.thi360", context.packageName)
        assertEquals("Thi360", packageManager.getApplicationLabel(appInfo).toString())
        assertEquals(26, appInfo.minSdkVersion)
        assertEquals(0, appInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertFalse(
            "Cleartext traffic must be disabled at application level",
            (appInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC) != 0,
        )
    }

    @Test
    fun appLabelIsThi360() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val packageManager = context.packageManager
        val appInfo = packageManager.getApplicationInfo(context.packageName, 0)
        assertEquals("Thi360", packageManager.getApplicationLabel(appInfo).toString())
        assertEquals("Thi360", context.getString(R.string.app_name))
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

    @Test
    fun networkStatePermissionAndFileProvider() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_PROVIDERS,
        )
        val requestedPermissions = packageInfo.requestedPermissions?.toSet().orEmpty()
        assertTrue(
            "App must declare ACCESS_NETWORK_STATE permission",
            Manifest.permission.ACCESS_NETWORK_STATE in requestedPermissions,
        )
        assertTrue(
            "App must declare INTERNET permission",
            Manifest.permission.INTERNET in requestedPermissions,
        )

        // Verify source AndroidManifest.xml declares only INTERNET and ACCESS_NETWORK_STATE (no extra permissions).
        val manifestFile = File(resolveMainSourceDir(), "AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)
        val permissionNodes = doc.getElementsByTagName("uses-permission")
        val declaredPermissions = linkedSetOf<String>()
        for (i in 0 until permissionNodes.length) {
            val element = permissionNodes.item(i) as Element
            declaredPermissions += element.getAttribute("android:name")
        }
        assertEquals(
            "AndroidManifest.xml must only declare INTERNET, ACCESS_NETWORK_STATE, and CAMERA",
            setOf(
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.CAMERA,
            ),
            declaredPermissions,
        )

        val providerInfo = context.packageManager.getProviderInfo(
            ComponentName(context, FileProvider::class.java),
            PackageManager.GET_META_DATA,
        )
        assertEquals("${context.packageName}.fileprovider", providerInfo.authority)
        assertFalse("Shared FileProvider must not be exported", providerInfo.exported)
        assertTrue("Shared FileProvider must enable grantUriPermissions", providerInfo.grantUriPermissions)

        val parser = context.resources.getXml(R.xml.file_provider_paths)
        val childEntries = mutableListOf<Triple<String, String?, String?>>()
        parser.use { xml ->
            var event = xml.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && xml.name != "paths") {
                    childEntries += Triple(
                        xml.name,
                        xml.getAttributeValue(null, "name"),
                        xml.getAttributeValue(null, "path"),
                    )
                }
                event = xml.next()
            }
        }
        assertEquals(
            "file_provider_paths.xml must declare only the avatar camera and attempt assets cache-path entries",
            listOf(
                Triple("cache-path", "avatar_camera", "avatar-captures/"),
                Triple("cache-path", "attempt_assets", "attempt-assets/"),
            ),
            childEntries,
        )
    }
}
