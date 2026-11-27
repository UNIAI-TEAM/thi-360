package vn.unicom.thi360.feature.account

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import vn.unicom.thi360.core.storage.Tokens

internal class MutableTestClock(
    initialInstant: Instant = Instant.parse("2026-04-20T10:00:00Z"),
    private val zoneId: ZoneId = ZoneId.of("UTC"),
) : Clock() {
    private val current = AtomicReference(initialInstant)

    override fun getZone(): ZoneId = zoneId

    override fun withZone(zone: ZoneId): Clock = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = this@MutableTestClock.withZone(z)
        override fun instant(): Instant = current.get()
    }

    override fun instant(): Instant = current.get()

    fun setInstant(instant: Instant) {
        current.set(instant)
    }

    fun advanceSeconds(seconds: Long) {
        current.updateAndGet { it.plusSeconds(seconds) }
    }

    fun advance(duration: Duration) {
        current.updateAndGet { it.plus(duration) }
    }
}

internal class FakeCameraAccess(
    override val hasCamera: Boolean = true,
    override var permission: CameraPermissionStatus = CameraPermissionStatus.Granted,
    var openSettingsCalls: Int = 0,
    var requestResult: CameraPermissionStatus = CameraPermissionStatus.Granted,
    var requestPermissionCalls: Int = 0,
) : CameraAccess {
    override fun request(onResult: (CameraPermissionStatus) -> Unit) {
        requestPermissionCalls++
        permission = requestResult
        onResult(requestResult)
    }

    override fun openAppSettings() {
        openSettingsCalls++
    }

    override fun createCaptureUri(): Uri =
        Uri.parse("content://vn.unicom.thi360.fileprovider/avatar_camera/test_capture.jpg")
}

internal class FakeAvatarImagePreparer(
    private val handler: suspend (Uri) -> AvatarPrepareResult = {
        AvatarPrepareResult.Prepared(
            PreparedAvatar(
                bytes = createSampleJpegBytes(),
                fileName = "avatar.jpg",
                mimeType = "image/jpeg",
            ),
        )
    },
) : AvatarImagePreparer {
    var calls: Int = 0
        private set

    override suspend fun prepare(uri: Uri): AvatarPrepareResult {
        calls++
        return handler(uri)
    }
}

internal fun createSamplePngBytes(color: Int = Color.BLUE, width: Int = 24, height: Int = 24): ByteArray =
    ByteArrayOutputStream().use { baos ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.PNG, 100, baos)
        }
        baos.toByteArray()
    }

internal fun createSampleJpegBytes(color: Int = Color.GREEN, width: Int = 32, height: Int = 32): ByteArray =
    ByteArrayOutputStream().use { baos ->
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
            compress(Bitmap.CompressFormat.JPEG, 90, baos)
        }
        baos.toByteArray()
    }

internal fun pngMockResponse(color: Int = Color.BLUE): MockResponse =
    MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "image/png")
        .setBody(Buffer().write(createSamplePngBytes(color)))

internal fun testSignedInTokens(
    username: String = "student.demo",
    accountId: String = "019320a0-0000-7000-8000-000000000001",
    accessToken: String = "test-access-token",
    refreshToken: String = "test-refresh-token",
) = Tokens(
    accessToken = accessToken,
    refreshToken = refreshToken,
    accessExpiresIn = 300,
    refreshExpiresIn = 604800,
    mustChangePassword = false,
    username = username,
    accountId = accountId,
)
