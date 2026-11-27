package vn.unicom.thi360.feature.attachments.model

import androidx.compose.runtime.Immutable
import java.util.Locale
import java.util.UUID
import vn.unicom.thi360.core.network.generated.model.AttemptAttachmentView
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayResponse
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.feature.richcontent.assets.AttemptAssetSource
import vn.unicom.thi360.feature.richcontent.model.AttemptAssetRef

@Immutable
data class QuestionAttachment(
    val assetId: UUID,
    val fileName: String,
    val mime: String,
    val url: String? = null,
) {
    val isAudio: Boolean
        get() = isAudioMime(mime)

    val fileExtension: String?
        get() {
            val fromName = fileName
                .substringAfterLast('.', "")
                .trim()
                .lowercase(Locale.ROOT)
                .takeIf { it.isNotEmpty() && SAFE_EXTENSION_REGEX.matches(it) }
            if (fromName != null) return fromName
            return MIME_EXTENSIONS[mime.trim().lowercase(Locale.ROOT)]
        }

    companion object {
        private val SAFE_EXTENSION_REGEX = Regex("^[a-z0-9]{1,10}$")

        private val MIME_EXTENSIONS = mapOf(
            "application/pdf" to "pdf",
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/webp" to "webp",
            "text/plain" to "txt",
            "application/msword" to "doc",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" to "docx",
            "application/vnd.ms-excel" to "xls",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" to "xlsx",
        )

        fun isAudioMime(mime: String?): Boolean =
            mime?.trim()?.lowercase(Locale.ROOT)?.startsWith("audio/") == true
    }
}

object AttachmentMerge {
    fun mergeQuestionAttachments(
        parentAttachments: List<AttemptAttachmentView>?,
        childItems: List<AttemptItemView>? = null,
    ): List<QuestionAttachment> {
        val parent = parentAttachments.orEmpty().mapNotNull(::toQuestionAttachment)
        val children = childItems.orEmpty().map { child ->
            child.attachments.orEmpty().mapNotNull(::toQuestionAttachment)
        }
        return merge(parent = parent, children = children)
    }

    fun merge(
        parent: List<QuestionAttachment>,
        children: List<List<QuestionAttachment>> = emptyList(),
    ): List<QuestionAttachment> {
        val seenAssetIds = LinkedHashSet<UUID>()
        val merged = ArrayList<QuestionAttachment>(parent.size)

        for (attachment in parent) {
            if (seenAssetIds.add(attachment.assetId)) {
                merged.add(attachment)
            }
        }
        // Only audio attachments from child items are hoisted to the set header (R-132, A-115, AC-727).
        for (childList in children) {
            for (attachment in childList) {
                if (attachment.isAudio && seenAssetIds.add(attachment.assetId)) {
                    merged.add(attachment)
                }
            }
        }
        return merged
    }

    private fun toQuestionAttachment(raw: AttemptAttachmentView): QuestionAttachment? {
        val assetId = resolveAssetId(raw) ?: return null
        val displayName = raw.fileName?.trim()?.takeIf { it.isNotEmpty() }
            ?: raw.url?.substringAfterLast('/')?.substringBefore('?')?.takeIf { it.isNotEmpty() }
            ?: assetId.toString()
        return QuestionAttachment(
            assetId = assetId,
            fileName = displayName,
            mime = raw.mime.orEmpty().trim(),
            url = raw.url,
        )
    }

    private fun resolveAssetId(raw: AttemptAttachmentView): UUID? {
        val resolvedRef = AttemptAssetSource.resolveAssetRef(
            src = raw.url,
            dataAssetId = raw.assetId?.toString(),
        )
        return when (resolvedRef) {
            is AttemptAssetRef.Attempt -> runCatching { UUID.fromString(resolvedRef.assetId) }.getOrNull()
            is AttemptAssetRef.Solution -> null
            AttemptAssetRef.Blocked -> raw.assetId?.takeIf { raw.url.isNullOrBlank() }
        }
    }
}

enum class ServerAudioPlayState {
    IDLE,
    PLAYING,
    ENDED,
}

enum class AudioEndReason {
    FINISHED,
    CLOSED,
}

@Immutable
data class AudioPlaySnapshot(
    val playId: UUID? = null,
    val playsUsed: Int = 0,
    val playsLeft: Int = DEFAULT_MAX_PLAYS,
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val state: ServerAudioPlayState = ServerAudioPlayState.IDLE,
    val endReason: AudioEndReason? = null,
) {
    companion object {
        const val DEFAULT_MAX_PLAYS = 2

        fun empty(maxPlays: Int = DEFAULT_MAX_PLAYS): AudioPlaySnapshot {
            val safeMax = maxPlays.coerceAtLeast(0)
            return AudioPlaySnapshot(
                playId = null,
                playsUsed = 0,
                playsLeft = safeMax,
                durationMs = 0L,
                positionMs = 0L,
                state = ServerAudioPlayState.IDLE,
                endReason = null,
            )
        }

        fun fromResponse(response: AttemptAudioPlayResponse): AudioPlaySnapshot = AudioPlaySnapshot(
            playId = response.playId,
            playsUsed = response.playsUsed.coerceAtLeast(0),
            playsLeft = response.playsLeft.coerceAtLeast(0),
            durationMs = response.durationMs.coerceAtLeast(0L),
            positionMs = response.positionMs.coerceAtLeast(0L),
            state = when (response.state) {
                AttemptAudioPlayResponse.State.IDLE -> ServerAudioPlayState.IDLE
                AttemptAudioPlayResponse.State.PLAYING -> ServerAudioPlayState.PLAYING
                AttemptAudioPlayResponse.State.ENDED -> ServerAudioPlayState.ENDED
            },
            endReason = when (response.endReason) {
                AttemptAudioPlayResponse.EndReason.FINISHED -> AudioEndReason.FINISHED
                AttemptAudioPlayResponse.EndReason.CLOSED -> AudioEndReason.CLOSED
                null -> null
            },
        )

        fun fromView(view: AttemptAudioPlayView?, maxPlays: Int = DEFAULT_MAX_PLAYS): AudioPlaySnapshot {
            if (view == null) return empty(maxPlays)
            val used = (view.playsUsed ?: 0).coerceAtLeast(0)
            val left = (view.playsLeft ?: (maxPlays - used)).coerceAtLeast(0)
            return AudioPlaySnapshot(
                playId = view.playId,
                playsUsed = used,
                playsLeft = left,
                durationMs = (view.durationMs ?: 0L).coerceAtLeast(0L),
                positionMs = (view.positionMs ?: 0L).coerceAtLeast(0L),
                state = when (view.state) {
                    AttemptAudioPlayView.State.PLAYING -> ServerAudioPlayState.PLAYING
                    AttemptAudioPlayView.State.ENDED -> ServerAudioPlayState.ENDED
                    AttemptAudioPlayView.State.IDLE, null -> ServerAudioPlayState.IDLE
                },
                endReason = when (view.endReason) {
                    AttemptAudioPlayView.EndReason.FINISHED -> AudioEndReason.FINISHED
                    AttemptAudioPlayView.EndReason.CLOSED -> AudioEndReason.CLOSED
                    null -> null
                },
            )
        }
    }
}

enum class PauseReason {
    Background,
    Interrupted,
    Noisy,
    LeftScreen,
    Headset,
    Error,
}

enum class AudioLockReason {
    Acquiring,
    PartClosed,
    TimeUp,
    Submitted,
    Evicted,
    Disabled,
}

@Immutable
sealed interface AudioPlayerPhase {
    @Immutable
    data class Idle(val playsLeft: Int) : AudioPlayerPhase

    @Immutable
    data class Opening(val requestId: String, val isResuming: Boolean = false) : AudioPlayerPhase

    @Immutable
    data class Playing(val playId: UUID, val positionMs: Long) : AudioPlayerPhase

    @Immutable
    data class Paused(
        val playId: UUID,
        val serverPositionMs: Long,
        val reason: PauseReason,
    ) : AudioPlayerPhase

    @Immutable
    data class WaitingNetwork(val playId: UUID) : AudioPlayerPhase

    @Immutable
    data object Exhausted : AudioPlayerPhase

    @Immutable
    data class Locked(val reason: AudioLockReason) : AudioPlayerPhase
}

@Immutable
data class AudioPlayerUiState(
    val assetId: UUID,
    val fileName: String,
    val phase: AudioPlayerPhase,
    val playsUsed: Int,
    val playsLeft: Int,
    val maxPlays: Int,
    val positionMs: Long,
    val durationMs: Long,
    val isOnline: Boolean = true,
    val hasPlayError: Boolean = false,
) {
    val effectiveMaxPlays: Int
        get() = maxOf(maxPlays, playsUsed + playsLeft)

    val clampedPositionMs: Long
        get() = if (durationMs > 0L) positionMs.coerceIn(0L, durationMs) else positionMs.coerceAtLeast(0L)

    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (clampedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isExhausted: Boolean
        get() = phase is AudioPlayerPhase.Exhausted ||
            (playsLeft <= 0 &&
                phase !is AudioPlayerPhase.Playing &&
                phase !is AudioPlayerPhase.Paused &&
                phase !is AudioPlayerPhase.WaitingNetwork)
}

sealed interface AudioError {
    data class InProgress(val playId: UUID?) : AudioError
    data object Exhausted : AudioError
    data class SeekRejected(val positionMs: Long?) : AudioError
    data object Stale : AudioError
    data object PartClosed : AudioError
    data object TimeUp : AudioError
    data object SessionRevoked : AudioError
    data object Unauthorized : AudioError
    data class Network(val cause: Throwable? = null) : AudioError
    data class Other(
        val statusCode: Int? = null,
        val code: String? = null,
        val message: String? = null,
    ) : AudioError
}

sealed interface AudioGatewayResult<out T> {
    data class Success<T>(val value: T) : AudioGatewayResult<T>
    data class Failure(val error: AudioError) : AudioGatewayResult<Nothing>
}

fun formatAudioTime(ms: Long): String {
    val safeSeconds = (ms.coerceAtLeast(0L)) / 1_000L
    val minutes = safeSeconds / 60L
    val seconds = safeSeconds % 60L
    val minStr = if (minutes < 10L) "0$minutes" else minutes.toString()
    val secStr = if (seconds < 10L) "0$seconds" else seconds.toString()
    return "$minStr:$secStr"
}
