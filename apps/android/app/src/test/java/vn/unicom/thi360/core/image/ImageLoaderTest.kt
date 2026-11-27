package vn.unicom.thi360.core.image

import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.memory.MemoryCache
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class ImageLoaderTest {

    private lateinit var apiServer: ContractFakeServer
    private lateinit var externalServer: ContractFakeServer

    @Before
    fun setUp() {
        apiServer = ContractFakeServer(installDefaultMeHandler = false).start()
        externalServer = ContractFakeServer(installDefaultMeHandler = false).start()
    }

    @After
    fun tearDown() {
        apiServer.shutdown()
        externalServer.shutdown()
    }

    @Test
    fun bearerAttachedOnlyToApiHostDiskCacheDisabledAndLogoutClearsMemoryCache() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val cleanupDir = Files.createTempDirectory("thi360-img-cleanup-").toFile()
        val pngBytes = ByteArrayOutputStream().apply {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, this)
        }.toByteArray()

        apiServer.respond("GET", "/media/avatar.png") {
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(Buffer().write(pngBytes))
        }
        externalServer.respond("GET", "/cdn/banner.png") {
            MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "image/png")
                .setBody(Buffer().write(pngBytes))
        }

        val sessionCleanup = SessionCleanup(cleanupDir)
        val loader = Thi360ImageLoader.create(
            context = context,
            apiBaseUrl = "http://localhost:${apiServer.port()}",
            baseOkHttpClient = OkHttpClient(),
            accessTokenProvider = { "secret-bearer-token" },
            sessionCleanup = sessionCleanup,
        )

        try {
            // 1. Disk cache must be completely disabled (both OkHttp and Coil).
            assertNull("OkHttp disk cache must be null for authenticated images", loader.okHttpClient.cache)
            assertNull("Coil disk cache must be null for authenticated images", loader.imageLoader.diskCache)

            // 2. Request to API host ("localhost") attaches Authorization: Bearer.
            loader.okHttpClient.newCall(
                Request.Builder().url("http://localhost:${apiServer.port()}/media/avatar.png").build(),
            ).execute().use { resp ->
                assertEquals(200, resp.code)
            }
            assertEquals(
                "Bearer secret-bearer-token",
                apiServer.lastRequest("GET", "/media/avatar.png")?.getHeader("Authorization"),
            )

            // 3. Request to non-API host ("127.0.0.1") MUST NOT attach Authorization header (even if caller tried).
            loader.okHttpClient.newCall(
                Request.Builder()
                    .url("http://127.0.0.1:${externalServer.port()}/cdn/banner.png")
                    .header("Authorization", "Bearer leaked")
                    .build(),
            ).execute().use { resp ->
                assertEquals(200, resp.code)
            }
            assertNull(
                "Non-API host must never receive Authorization header",
                externalServer.lastRequest("GET", "/cdn/banner.png")?.getHeader("Authorization"),
            )

            // 4. Memory cache is cleared automatically when SessionCleanup.onLogout runs.
            val cache = checkNotNull(loader.imageLoader.memoryCache)
            val key = MemoryCache.Key("authenticated-avatar")
            val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
            cache[key] = MemoryCache.Value(bitmap.asImage())
            assertNotNull(cache[key])

            runBlocking { sessionCleanup.onLogout("account-1") }
            assertNull("SessionCleanup.onLogout must clear Coil memory cache", cache[key])
        } finally {
            loader.close()
            cleanupDir.deleteRecursively()
        }
    }

    @Test
    fun applicationImplementsSingletonImageLoaderFactory() {
        val app = ApplicationProvider.getApplicationContext<Thi360Application>()
        assertSame(
            "Coil SingletonImageLoader must return Thi360Application's session-wired ImageLoader",
            app.imageLoader.imageLoader,
            SingletonImageLoader.get(app),
        )
    }
}
