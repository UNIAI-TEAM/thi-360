package vn.unicom.thi360.feature.account

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

enum class CameraPermissionStatus {
    Granted,
    Denied,
    NotAsked,
}

interface CameraAccess {
    val hasCamera: Boolean
    val permission: CameraPermissionStatus
    fun request(onResult: (CameraPermissionStatus) -> Unit)
    fun openAppSettings()
    fun createCaptureUri(): Uri? = null
}

internal data class CameraTempTarget(
    val file: File,
    val uri: Uri,
) {
    fun deleteQuietly() {
        runCatching {
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

internal object CameraCaptureFiles {
    private const val CAPTURE_DIR_NAME = "avatar-captures"

    fun createTempCapture(context: Context): CameraTempTarget {
        val dir = File(context.cacheDir, CAPTURE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        val tempFile = File.createTempFile("avatar_", ".jpg", dir)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile,
        )
        return CameraTempTarget(file = tempFile, uri = uri)
    }
}

@Composable
internal fun rememberAndroidCameraAccess(context: Context): CameraAccess {
    val hasCamera = remember(context) {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    var requestedBefore by remember { mutableStateOf(false) }
    var pendingCallback by remember { mutableStateOf<((CameraPermissionStatus) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        requestedBefore = true
        val status = if (granted) CameraPermissionStatus.Granted else CameraPermissionStatus.Denied
        val callback = pendingCallback
        pendingCallback = null
        callback?.invoke(status)
    }

    return remember(context, hasCamera, launcher) {
        object : CameraAccess {
            override val hasCamera: Boolean = hasCamera

            override val permission: CameraPermissionStatus
                get() {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    return when {
                        granted -> CameraPermissionStatus.Granted
                        requestedBefore -> CameraPermissionStatus.Denied
                        else -> CameraPermissionStatus.NotAsked
                    }
                }

            override fun request(onResult: (CameraPermissionStatus) -> Unit) {
                if (permission == CameraPermissionStatus.Granted) {
                    onResult(CameraPermissionStatus.Granted)
                    return
                }
                pendingCallback = onResult
                launcher.launch(Manifest.permission.CAMERA)
            }

            override fun openAppSettings() {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }
}
