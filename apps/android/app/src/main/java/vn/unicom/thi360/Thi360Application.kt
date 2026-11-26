package vn.unicom.thi360

import android.app.Application
import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import vn.unicom.thi360.core.appearance.AppearanceRepository
import vn.unicom.thi360.core.biometric.AndroidBiometricAvailabilityChecker
import vn.unicom.thi360.core.biometric.BiometricController
import vn.unicom.thi360.core.biometric.DataStoreBiometricPromptMemoryStore
import vn.unicom.thi360.core.biometric.KeystoreBiometricVault
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.core.network.AndroidConnectivityMonitor
import vn.unicom.thi360.core.network.ConnectivityMonitor
import vn.unicom.thi360.core.preferences.AppPreferencesRepository
import vn.unicom.thi360.core.preferences.DataStoreAppPreferencesRepository
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.routes.InboundLinkRouter
import vn.unicom.thi360.shell.routes.PaymentReturnLinkStore

class Thi360Application : Application() {
    val preferencesRepository: AppPreferencesRepository by lazy {
        DataStoreAppPreferencesRepository.create(this)
    }

    val sessionCleanup: SessionCleanup by lazy {
        SessionCleanup.create(this).also { cleanup ->
            cleanup.register { PaymentReturnLinkStore.Default.clear() }
        }
    }

    val accountScopedStoreProvider: AccountScopedStoreProvider by lazy {
        AccountScopedStoreProvider.create(
            context = this,
            accountIdProvider = { AppSessionContainer.get(this).sessionManager.currentAccountIdOrNull() },
        )
    }

    private var testConnectivityMonitor: ConnectivityMonitor? = null
    private val defaultConnectivityMonitor: ConnectivityMonitor by lazy {
        AndroidConnectivityMonitor(this)
    }
    val connectivityMonitor: ConnectivityMonitor
        get() = testConnectivityMonitor ?: defaultConnectivityMonitor

    private var testInboundLinkRouter: InboundLinkRouter? = null
    private val defaultInboundLinkRouter: InboundLinkRouter by lazy {
        InboundLinkRouter(
            allowedHostProvider = { ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl) },
        )
    }
    val inboundLinkRouter: InboundLinkRouter
        get() = testInboundLinkRouter ?: defaultInboundLinkRouter

    private var testLegalRepository: LegalRepository? = null
    private val defaultLegalRepository: LegalRepository by lazy {
        LegalRepository.create(this)
    }
    val legalRepository: LegalRepository
        get() = testLegalRepository ?: defaultLegalRepository

    val imageLoader: Thi360ImageLoader by lazy {
        Thi360ImageLoader.create(
            context = this,
            apiBaseUrl = AppEnvironment.apiBaseUrl,
            sessionStack = AppSessionContainer.get(this),
            sessionCleanup = sessionCleanup,
        )
    }

    private var testAppearanceRepository: AppearanceRepository? = null
    private val defaultAppearanceRepository: AppearanceRepository by lazy {
        AppearanceRepository.create(this)
    }
    val appearanceRepository: AppearanceRepository
        get() = testAppearanceRepository ?: defaultAppearanceRepository

    private var testBiometricController: BiometricController? = null
    private val defaultBiometricController: BiometricController by lazy {
        BiometricController(
            availabilityChecker = AndroidBiometricAvailabilityChecker(this),
            vault = KeystoreBiometricVault.create(this),
            promptMemoryStore = DataStoreBiometricPromptMemoryStore.create(this),
        )
    }
    val biometricController: BiometricController
        get() = testBiometricController ?: defaultBiometricController

    override fun onCreate() {
        super.onCreate()
        // Finish any interrupted logout cleanup before any screen or store reads account-scoped data.
        runBlocking(Dispatchers.IO) {
            sessionCleanup.onAppStart()
        }
    }

    @VisibleForTesting
    internal fun setTestOverrides(
        appearanceRepository: AppearanceRepository? = null,
        biometricController: BiometricController? = null,
        connectivityMonitor: ConnectivityMonitor? = null,
        inboundLinkRouter: InboundLinkRouter? = null,
        legalRepository: LegalRepository? = null,
    ) {
        testAppearanceRepository = appearanceRepository
        testBiometricController = biometricController
        testConnectivityMonitor = connectivityMonitor
        testInboundLinkRouter = inboundLinkRouter
        testLegalRepository = legalRepository
    }
}
