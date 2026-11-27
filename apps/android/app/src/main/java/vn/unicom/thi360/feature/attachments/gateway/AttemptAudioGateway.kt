package vn.unicom.thi360.feature.attachments.gateway

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Response
import vn.unicom.thi360.core.network.ApiErrorParser
import vn.unicom.thi360.core.network.generated.api.AttemptsApi
import vn.unicom.thi360.core.network.generated.infrastructure.Serializer
import vn.unicom.thi360.core.network.generated.model.AttemptAudioPlayResponse
import vn.unicom.thi360.core.network.generated.model.AudioPlayInProgressResponse
import vn.unicom.thi360.core.network.generated.model.AudioProgressRequest
import vn.unicom.thi360.core.network.generated.model.AudioSeekRejectedResponse
import vn.unicom.thi360.core.network.generated.model.StartAudioPlayRequest
import vn.unicom.thi360.feature.attachments.model.AudioError
import vn.unicom.thi360.feature.attachments.model.AudioGatewayResult
import vn.unicom.thi360.feature.attachments.model.AudioPlaySnapshot

interface AttemptAudioGateway {
    suspend fun openPlay(assetId: UUID, requestId: String): AudioGatewayResult<AudioPlaySnapshot>

    suspend fun resume(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot>

    suspend fun progress(assetId: UUID, playId: UUID, positionMs: Long): AudioGatewayResult<AudioPlaySnapshot>

    suspend fun end(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot>

    suspend fun fetchAudio(assetId: UUID, playId: UUID): AudioGatewayResult<ByteArray>
}

class OpenApiAttemptAudioGateway(
    private val attemptId: UUID,
    private val service: AttemptsApi,
    private val activeSessionIdProvider: () -> UUID?,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AttemptAudioGateway {

    override suspend fun openPlay(assetId: UUID, requestId: String): AudioGatewayResult<AudioPlaySnapshot> {
        val sessionId = activeSessionIdProvider()
            ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
        return executePlayCall {
            service.openAudioPlay(
                attemptId = attemptId,
                assetId = assetId,
                startAudioPlayRequest = StartAudioPlayRequest(requestId = requestId),
                xAttemptSession = sessionId,
            )
        }
    }

    override suspend fun resume(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot> {
        val sessionId = activeSessionIdProvider()
            ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
        return executePlayCall {
            service.resumeAudioPlay(
                attemptId = attemptId,
                assetId = assetId,
                playId = playId,
                xAttemptSession = sessionId,
            )
        }
    }

    override suspend fun progress(
        assetId: UUID,
        playId: UUID,
        positionMs: Long,
    ): AudioGatewayResult<AudioPlaySnapshot> {
        val sessionId = activeSessionIdProvider()
            ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
        return executePlayCall {
            service.updateAudioProgress(
                attemptId = attemptId,
                assetId = assetId,
                playId = playId,
                audioProgressRequest = AudioProgressRequest(positionMs = positionMs.coerceAtLeast(0L)),
                xAttemptSession = sessionId,
            )
        }
    }

    override suspend fun end(assetId: UUID, playId: UUID): AudioGatewayResult<AudioPlaySnapshot> {
        val sessionId = activeSessionIdProvider()
            ?: return AudioGatewayResult.Failure(AudioError.SessionRevoked)
        return executePlayCall {
            service.endAudioPlay(
                attemptId = attemptId,
                assetId = assetId,
                playId = playId,
                xAttemptSession = sessionId,
            )
        }
    }

    override suspend fun fetchAudio(assetId: UUID, playId: UUID): AudioGatewayResult<ByteArray> {
        if (activeSessionIdProvider() == null) {
            return AudioGatewayResult.Failure(AudioError.SessionRevoked)
        }
        return withContext(ioDispatcher) {
            try {
                val response = service.downloadAttemptAsset(
                    attemptId = attemptId,
                    assetId = assetId,
                    playId = playId,
                )
                if (response.isSuccessful) {
                    val bytes = response.body()?.use { it.bytes() }
                    if (bytes != null && bytes.isNotEmpty()) {
                        AudioGatewayResult.Success(bytes)
                    } else {
                        AudioGatewayResult.Failure(
                            AudioError.Other(statusCode = response.code(), message = "Empty audio payload"),
                        )
                    }
                } else {
                    val rawError = response.errorBody()?.string()
                    AudioGatewayResult.Failure(mapHttpError(response.code(), rawError))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (io: IOException) {
                AudioGatewayResult.Failure(AudioError.Network(io))
            } catch (other: Throwable) {
                AudioGatewayResult.Failure(AudioError.Network(other))
            }
        }
    }

    private suspend fun executePlayCall(
        block: suspend () -> Response<AttemptAudioPlayResponse>,
    ): AudioGatewayResult<AudioPlaySnapshot> = withContext(ioDispatcher) {
        try {
            val response = block()
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    AudioGatewayResult.Success(AudioPlaySnapshot.fromResponse(body))
                } else {
                    AudioGatewayResult.Failure(AudioError.Other(statusCode = response.code()))
                }
            } else {
                val rawError = response.errorBody()?.string()
                AudioGatewayResult.Failure(mapHttpError(response.code(), rawError))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: IOException) {
            AudioGatewayResult.Failure(AudioError.Network(io))
        } catch (other: Throwable) {
            AudioGatewayResult.Failure(AudioError.Network(other))
        }
    }

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_CONFLICT = 409

        const val CODE_AUDIO_PLAY_IN_PROGRESS = "AUDIO_PLAY_IN_PROGRESS"
        const val CODE_AUDIO_PLAYS_EXHAUSTED = "AUDIO_PLAYS_EXHAUSTED"
        const val CODE_AUDIO_SEEK_REJECTED = "AUDIO_SEEK_REJECTED"
        const val CODE_AUDIO_PLAY_STALE = "AUDIO_PLAY_STALE"
        const val CODE_PART_CLOSED = "PART_CLOSED"
        const val CODE_TIME_UP = "TIME_UP"
        const val CODE_ATTEMPT_SESSION_REVOKED = "ATTEMPT_SESSION_REVOKED"
        const val CODE_ATTEMPT_OPEN_ELSEWHERE = "ATTEMPT_OPEN_ELSEWHERE"

        fun mapHttpError(statusCode: Int, rawErrorBody: String?): AudioError {
            if (statusCode == HTTP_UNAUTHORIZED) {
                return AudioError.Unauthorized
            }
            val parsed = ApiErrorParser.parse(rawErrorBody)
            val code = parsed?.code
            if (statusCode == HTTP_CONFLICT && code != null) {
                return when (code) {
                    CODE_AUDIO_PLAY_IN_PROGRESS -> {
                        val payload = decodeInProgress(rawErrorBody)
                        AudioError.InProgress(playId = payload?.playId)
                    }
                    CODE_AUDIO_PLAYS_EXHAUSTED -> AudioError.Exhausted
                    CODE_AUDIO_SEEK_REJECTED -> {
                        val payload = decodeSeekRejected(rawErrorBody)
                        AudioError.SeekRejected(positionMs = payload?.positionMs)
                    }
                    CODE_AUDIO_PLAY_STALE -> AudioError.Stale
                    CODE_PART_CLOSED -> AudioError.PartClosed
                    CODE_TIME_UP -> AudioError.TimeUp
                    CODE_ATTEMPT_SESSION_REVOKED,
                    CODE_ATTEMPT_OPEN_ELSEWHERE,
                    -> AudioError.SessionRevoked
                    else -> AudioError.Other(statusCode = statusCode, code = code, message = parsed.message)
                }
            }
            return AudioError.Other(statusCode = statusCode, code = code, message = parsed?.message)
        }

        private fun decodeInProgress(rawBody: String?): AudioPlayInProgressResponse? {
            if (rawBody.isNullOrBlank()) return null
            return runCatching {
                Serializer.kotlinxSerializationJson.decodeFromString(
                    AudioPlayInProgressResponse.serializer(),
                    rawBody,
                )
            }.getOrNull()
        }

        private fun decodeSeekRejected(rawBody: String?): AudioSeekRejectedResponse? {
            if (rawBody.isNullOrBlank()) return null
            return runCatching {
                Serializer.kotlinxSerializationJson.decodeFromString(
                    AudioSeekRejectedResponse.serializer(),
                    rawBody,
                )
            }.getOrNull()
        }
    }
}
