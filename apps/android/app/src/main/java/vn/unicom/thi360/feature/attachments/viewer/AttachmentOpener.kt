package vn.unicom.thi360.feature.attachments.viewer

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.feature.attachments.controller.AttemptAudioSessionRegistry
import vn.unicom.thi360.feature.attachments.controller.AudioLogoutCleanable
import vn.unicom.thi360.feature.attachments.model.QuestionAttachment
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.assets.TempAssetFile
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

fun interface ViewerIntentLauncher {
    fun launch(intent: Intent, onClosed: () -> Unit)
}

fun interface AttachmentUriResolver {
    fun resolveUri(file: File): Uri
}

sealed interface AttachmentOpenState {
    data object Idle : AttachmentOpenState
    data class Loading(val assetId: UUID) : AttachmentOpenState
    data class OpenFailed(val assetId: UUID) : AttachmentOpenState
}

class AttachmentOpener(
    private val assetSource: AttemptAssetSource,
    private val uriResolver: AttachmentUriResolver,
    private val launcher: ViewerIntentLauncher,
    sessionCleanup: SessionCleanup? = null,
) : Closeable, AudioLogoutCleanable {
    private val openHandles = CopyOnWriteArrayList<TempAssetFile>()
    private var registryHandle: Closeable? = null

    private val _state = MutableStateFlow<AttachmentOpenState>(AttachmentOpenState.Idle)
    val state: StateFlow<AttachmentOpenState> = _state.asStateFlow()

    init {
        registryHandle = AttemptAudioSessionRegistry.register(this, sessionCleanup)
    }

    suspend fun open(attachment: QuestionAttachment): Boolean {
        _state.value = AttachmentOpenState.Loading(attachment.assetId)
        val tempHandle = try {
            assetSource.openForViewer(
                ref = AttemptAssetRef.Attempt(attachment.assetId.toString()),
                extension = attachment.fileExtension,
            )
        } catch (cancelled: CancellationException) {
            _state.value = AttachmentOpenState.Idle
            throw cancelled
        } catch (_: Throwable) {
            _state.value = AttachmentOpenState.OpenFailed(attachment.assetId)
            return false
        }

        openHandles.add(tempHandle)
        return try {
            val uri = uriResolver.resolveUri(tempHandle.file)
            val intent = buildReadOnlyViewerIntent(
                uri = uri,
                mime = attachment.mime,
                fileName = attachment.fileName,
            )
            launcher.launch(intent) {
                releaseHandle(tempHandle)
            }
            _state.value = AttachmentOpenState.Idle
            true
        } catch (cancelled: CancellationException) {
            releaseHandle(tempHandle)
            _state.value = AttachmentOpenState.Idle
            throw cancelled
        } catch (_: ActivityNotFoundException) {
            releaseHandle(tempHandle)
            _state.value = AttachmentOpenState.OpenFailed(attachment.assetId)
            false
        } catch (_: Throwable) {
            releaseHandle(tempHandle)
            _state.value = AttachmentOpenState.OpenFailed(attachment.assetId)
            false
        }
    }

    fun clearError() {
        if (_state.value is AttachmentOpenState.OpenFailed) {
            _state.value = AttachmentOpenState.Idle
        }
    }

    override fun stopAndPurgeForLogout() {
        releaseAllHandles()
        assetSource.purgeAll()
    }

    override fun close() {
        releaseAllHandles()
        registryHandle?.close()
        registryHandle = null
    }

    private fun releaseHandle(handle: TempAssetFile) {
        openHandles.remove(handle)
        runCatching { handle.close() }
    }

    private fun releaseAllHandles() {
        val snapshot = openHandles.toList()
        openHandles.clear()
        for (handle in snapshot) {
            runCatching { handle.close() }
        }
    }

    companion object {
        fun fileProviderAuthority(context: Context): String = "${context.packageName}.fileprovider"

        fun defaultUriResolver(context: Context): AttachmentUriResolver {
            val appContext = context.applicationContext
            val authority = fileProviderAuthority(appContext)
            return AttachmentUriResolver { file ->
                FileProvider.getUriForFile(appContext, authority, file)
            }
        }

        fun buildReadOnlyViewerIntent(
            uri: Uri,
            mime: String,
            fileName: String,
        ): Intent {
            val normalizedMime = mime.trim().ifEmpty { "*/*" }
            return Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, normalizedMime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(fileName, uri)
            }
        }
    }
}
