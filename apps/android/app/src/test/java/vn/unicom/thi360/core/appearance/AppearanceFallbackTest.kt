package vn.unicom.thi360.core.appearance

import java.io.File
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.BrandPalette

class AppearanceFallbackTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun offlineFirstLaunchUsesDefaultLightAndSubsequentOfflineLaunchUsesLastCachedPalette() = runTest {
        val cacheFile = File(tempFolder.newFolder("appearance_fallback"), "cache.json")
        val cache = FileAppearanceCache(cacheFile)

        // First launch with no cache + offline -> default thi360-green light.
        val offlineSource = object : AppearanceRemoteSource {
            override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse =
                AppearanceFetchResponse.NetworkError(IOException("Offline"))
        }
        val repo1 = AppearanceRepository(remoteSource = offlineSource, cache = cache, clockMillis = { 1_000L })
        assertEquals(BrandPalette.Default, repo1.current.value)
        assertEquals(AppearanceRefreshResult.NetworkError, repo1.refresh(force = true))
        assertEquals("thi360-green", repo1.current.value.presetId)
        assertEquals(ThemeMode.LIGHT, repo1.current.value.defaultMode)

        // Successful fetch caches navy-teal.
        val onlineSource = object : AppearanceRemoteSource {
            override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse =
                AppearanceFetchResponse.Ok(PaletteApplicationTest.NAVY_TEAL_JSON, "\"3\"")
        }
        val repo2 = AppearanceRepository(remoteSource = onlineSource, cache = cache, clockMillis = { 2_000L })
        assertEquals(AppearanceRefreshResult.Updated(3), repo2.refresh(force = true))
        assertEquals("navy-teal", repo2.current.value.presetId)

        // New cold launch offline restores navy-teal immediately from cache.
        val repo3 = AppearanceRepository(remoteSource = offlineSource, cache = cache, clockMillis = { 3_000L })
        assertEquals("navy-teal", repo3.current.value.presetId)
        assertEquals(AppearanceRefreshResult.NetworkError, repo3.refresh(force = true))
        assertEquals("navy-teal", repo3.current.value.presetId)
    }

    @Test
    fun http503NetworkFailureAndMalformedJsonKeepCurrentPaletteWithoutError() = runTest {
        val cache = FileAppearanceCache(File(tempFolder.newFolder("appearance_err"), "cache.json"))
        var nextResponse: AppearanceFetchResponse = AppearanceFetchResponse.Ok(
            body = PaletteApplicationTest.NAVY_TEAL_JSON,
            etag = "\"3\"",
        )
        val scriptedSource = object : AppearanceRemoteSource {
            override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse = nextResponse
        }
        val repo = AppearanceRepository(remoteSource = scriptedSource, cache = cache, clockMillis = { 10_000L })
        assertEquals(AppearanceRefreshResult.Updated(3), repo.refresh(force = true))
        val activePalette = repo.current.value
        assertEquals("navy-teal", activePalette.presetId)

        // 503 (e.g. backend returned unknown presetId rejected by web route) keeps active palette.
        nextResponse = AppearanceFetchResponse.HttpError(503)
        assertEquals(AppearanceRefreshResult.HttpError(503), repo.refresh(force = true))
        assertEquals(activePalette, repo.current.value)

        // Network failure keeps active palette.
        nextResponse = AppearanceFetchResponse.NetworkError(IOException("Timeout"))
        assertEquals(AppearanceRefreshResult.NetworkError, repo.refresh(force = true))
        assertEquals(activePalette, repo.current.value)

        // Malformed JSON, invalid mode, blank presetId, and invalid hex colors keep active palette.
        val badPayloads = listOf(
            "{not valid json",
            """{"version":4,"mode":"sepia","presetId":"navy-teal"}""",
            PaletteApplicationTest.NAVY_TEAL_JSON.replace("\"presetId\": \"navy-teal\"", "\"presetId\": \"\""),
            PaletteApplicationTest.NAVY_TEAL_JSON.replace("#0f3c6e", "not-a-hex"),
            PaletteApplicationTest.NAVY_TEAL_JSON.replace("\"dangerBg\": \"#fef2f2\"", ""),
        )
        for (bad in badPayloads) {
            nextResponse = AppearanceFetchResponse.Ok(body = bad, etag = "\"99\"")
            assertEquals(AppearanceRefreshResult.IgnoredInvalid, repo.refresh(force = true))
            assertEquals(activePalette, repo.current.value)
        }
    }
}
