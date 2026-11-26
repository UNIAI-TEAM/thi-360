package vn.unicom.thi360.feature.lock

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import vn.unicom.thi360.MainActivity
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.appearance.AppearanceCache
import vn.unicom.thi360.core.appearance.AppearanceFetchResponse
import vn.unicom.thi360.core.appearance.AppearanceRemoteSource
import vn.unicom.thi360.core.appearance.AppearanceRepository
import vn.unicom.thi360.core.appearance.CachedAppearance
import vn.unicom.thi360.core.appearance.PaletteApplicationTest
import vn.unicom.thi360.core.biometric.BiometricAvailability
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.BiometricState
import vn.unicom.thi360.core.biometric.BiometricUnavailableReason
import vn.unicom.thi360.core.biometric.FakeBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.FakeBiometricVault
import vn.unicom.thi360.core.biometric.InMemoryBiometricPromptMemoryStore
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class MainActivityLockLifecycleTest {

    private lateinit var app: Thi360Application
    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        AppSessionContainer.setTestStack(app, null)
        app.setTestOverrides(appearanceRepository = null, biometricController = null)
        server.shutdown()
    }

    private fun createInMemoryAppearanceCache(): AppearanceCache = object : AppearanceCache {
        private var stored: CachedAppearance? = null
        override fun read(): CachedAppearance? = stored
        override fun write(snapshot: CachedAppearance) {
            stored = snapshot
        }
    }

    private fun drainMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun foregroundActivityRecreateKeepsBiometricSessionActiveAndKeepsFlagSecure() {
        val appearanceFetches = AtomicInteger(0)
        val appearanceRepo = AppearanceRepository(
            remoteSource = object : AppearanceRemoteSource {
                override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse {
                    appearanceFetches.incrementAndGet()
                    return AppearanceFetchResponse.Ok(PaletteApplicationTest.NAVY_TEAL_JSON, "\"2\"")
                }
            },
            cache = createInMemoryAppearanceCache(),
            clockMillis = SystemClock::elapsedRealtime,
        )
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = FakeBiometricVault(initialWrappedToken = "refresh-active"),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access-1", "refresh-active", 300, 604800, false, "student.active"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            startLocked = false,
        )
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        app.setTestOverrides(
            appearanceRepository = appearanceRepo,
            biometricController = biometricController,
        )
        AppSessionContainer.setTestStack(app, stack)

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        drainMainLooper()
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        assertTrue(
            "FLAG_SECURE must be set on MainActivity when biometric is On",
            (controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )

        controller.recreate()
        drainMainLooper()

        assertTrue(
            "Foreground Activity recreation must keep session Active",
            stack.sessionManager.state.value is SessionState.Active,
        )
        assertTrue(
            "FLAG_SECURE must remain set synchronously on recreated MainActivity",
            (controller.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )
        controller.pause().stop().destroy()
    }

    @Test
    fun backgroundTimeoutFollowedByActivityRecreationInWarmProcessLocksBeforeShellRenders() {
        val appearanceFetches = AtomicInteger(0)
        val appearanceRepo = AppearanceRepository(
            remoteSource = object : AppearanceRemoteSource {
                override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse {
                    appearanceFetches.incrementAndGet()
                    return AppearanceFetchResponse.Ok(PaletteApplicationTest.NAVY_TEAL_JSON, "\"2\"")
                }
            },
            cache = createInMemoryAppearanceCache(),
            clockMillis = SystemClock::elapsedRealtime,
        )
        val biometricController = BiometricController(
            availabilityChecker = FakeBiometricAvailabilityChecker(BiometricAvailability.Available),
            vault = FakeBiometricVault(initialWrappedToken = "refresh-warm"),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access-warm", "refresh-warm", 300, 604800, false, "student.warm"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            startLocked = false,
        )
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        app.setTestOverrides(
            appearanceRepository = appearanceRepo,
            biometricController = biometricController,
        )
        AppSessionContainer.setTestStack(app, stack)

        val initialController = Robolectric.buildActivity(MainActivity::class.java).setup()
        drainMainLooper()
        assertTrue(stack.sessionManager.state.value is SessionState.Active)
        val initialFetchCount = appearanceFetches.get()

        val savedState = Bundle()
        initialController.pause().stop().saveInstanceState(savedState).destroy()
        drainMainLooper()

        ShadowSystemClock.advanceBy(Duration.ofMinutes(5))

        val recreatedController = Robolectric.buildActivity(MainActivity::class.java).create(savedState)
        assertTrue(
            "Session must already be Locked in MainActivity.onCreate before shell renders",
            stack.sessionManager.state.value is SessionState.Locked,
        )
        assertTrue(
            "FLAG_SECURE must be applied synchronously in onCreate before frame 0",
            (recreatedController.get().window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0,
        )

        recreatedController.start().resume()
        drainMainLooper()
        assertTrue(stack.sessionManager.state.value is SessionState.Locked)
        assertTrue(
            "Recreated MainActivity after >= 60s background must refresh appearance onStart",
            appearanceFetches.get() > initialFetchCount,
        )
        recreatedController.pause().stop().destroy()
    }

    @Test
    fun backgroundOver60SecondsWithRecreatedActivityRefreshesAppearanceAndBiometricAvailability() {
        val appearanceFetches = AtomicInteger(0)
        val receivedIfNoneMatch = mutableListOf<String?>()
        val appearanceRepo = AppearanceRepository(
            remoteSource = object : AppearanceRemoteSource {
                override suspend fun fetch(ifNoneMatch: String?): AppearanceFetchResponse {
                    receivedIfNoneMatch += ifNoneMatch
                    val count = appearanceFetches.incrementAndGet()
                    return if (count == 1) {
                        AppearanceFetchResponse.Ok(PaletteApplicationTest.NAVY_TEAL_JSON, "\"2\"")
                    } else {
                        AppearanceFetchResponse.NotModified("\"2\"")
                    }
                }
            },
            cache = createInMemoryAppearanceCache(),
            clockMillis = SystemClock::elapsedRealtime,
        )
        val availabilityChecker = FakeBiometricAvailabilityChecker(
            BiometricAvailability.Unavailable(BiometricUnavailableReason.NOT_ENROLLED),
        )
        val biometricController = BiometricController(
            availabilityChecker = availabilityChecker,
            vault = FakeBiometricVault(),
            promptMemoryStore = InMemoryBiometricPromptMemoryStore(),
        )
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(
                Tokens("access-2", "refresh-2", 300, 604800, false, "student.avail"),
            ),
            localeProvider = { AppLocale.VI },
            onSessionCleared = { biometricController.clearForSessionEnd() },
            startLocked = false,
        )
        server.respondAuthFixture("POST", "/api/v1/auth/mobile/refresh", 200, "refresh-200.json")
        app.setTestOverrides(
            appearanceRepository = appearanceRepo,
            biometricController = biometricController,
        )
        AppSessionContainer.setTestStack(app, stack)

        val initialController = Robolectric.buildActivity(MainActivity::class.java).setup()
        drainMainLooper()
        assertEquals(1, appearanceFetches.get())
        assertEquals(
            BiometricState.Unavailable(BiometricUnavailableReason.NOT_ENROLLED),
            biometricController.state.value,
        )

        val savedState = Bundle()
        initialController.pause().stop().saveInstanceState(savedState).destroy()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(65))
        availabilityChecker.availability = BiometricAvailability.Available

        val recreatedController = Robolectric.buildActivity(MainActivity::class.java)
            .create(savedState)
            .start()
            .resume()
        drainMainLooper()

        assertEquals(
            "Appearance repository must refresh onStart after >= 60s even when savedInstanceState != null",
            2,
            appearanceFetches.get(),
        )
        assertEquals("\"2\"", receivedIfNoneMatch.last())
        assertEquals(
            "Biometric availability must refresh onStart when returning from OS Settings",
            BiometricState.Off,
            biometricController.state.value,
        )
        runBlocking { stack.sessionManager.clearSession() }
        recreatedController.pause().stop().destroy()
    }
}
