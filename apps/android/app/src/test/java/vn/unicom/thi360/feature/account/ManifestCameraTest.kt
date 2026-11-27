package vn.unicom.thi360.feature.account

import android.Manifest
import android.provider.MediaStore
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ManifestCameraTest {

    private fun resolveMainSourceDir(): File {
        val candidates = listOf(File("src/main"), File("app/src/main"))
        return candidates.firstOrNull { File(it, "AndroidManifest.xml").isFile }
            ?: error("Cannot locate src/main/AndroidManifest.xml from ${File(".").canonicalPath}")
    }

    @Test
    fun manifestDeclaresOptionalCameraFeaturePermissionCaptureQueryAndNoStoragePermissions() {
        val mainDir = resolveMainSourceDir()
        val manifestFile = File(mainDir, "AndroidManifest.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifestFile)

        val permissions = mutableSetOf<String>()
        val permNodes = doc.getElementsByTagName("uses-permission")
        for (i in 0 until permNodes.length) {
            permissions += (permNodes.item(i) as Element).getAttribute("android:name")
        }
        assertTrue("CAMERA permission must be declared", Manifest.permission.CAMERA in permissions)
        assertFalse("READ_MEDIA_IMAGES must not be declared", "android.permission.READ_MEDIA_IMAGES" in permissions)
        assertFalse("READ_EXTERNAL_STORAGE must not be declared", "android.permission.READ_EXTERNAL_STORAGE" in permissions)
        assertFalse("WRITE_EXTERNAL_STORAGE must not be declared", "android.permission.WRITE_EXTERNAL_STORAGE" in permissions)

        val featureNodes = doc.getElementsByTagName("uses-feature")
        var hasOptionalCameraFeature = false
        for (i in 0 until featureNodes.length) {
            val el = featureNodes.item(i) as Element
            if (el.getAttribute("android:name") == "android.hardware.camera" &&
                el.getAttribute("android:required") == "false"
            ) {
                hasOptionalCameraFeature = true
            }
        }
        assertTrue("android.hardware.camera must have required=\"false\"", hasOptionalCameraFeature)

        val actionNodes = doc.getElementsByTagName("action")
        var hasImageCaptureQuery = false
        for (i in 0 until actionNodes.length) {
            val el = actionNodes.item(i) as Element
            if (el.getAttribute("android:name") == MediaStore.ACTION_IMAGE_CAPTURE) {
                hasImageCaptureQuery = true
            }
        }
        assertTrue("<queries> must include android.media.action.IMAGE_CAPTURE", hasImageCaptureQuery)

        val pathsFile = File(mainDir, "res/xml/file_provider_paths.xml")
        val pathsDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pathsFile)
        val cachePaths = pathsDoc.getElementsByTagName("cache-path")
        val entries = (0 until cachePaths.length).map { index ->
            val el = cachePaths.item(index) as Element
            el.getAttribute("name") to el.getAttribute("path")
        }
        assertTrue("file_provider_paths.xml must declare avatar_camera -> avatar-captures/", ("avatar_camera" to "avatar-captures/") in entries)
    }
}
