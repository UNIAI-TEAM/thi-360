package vn.unicom.thi360.testing

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.legal.LegalAssetSource
import vn.unicom.thi360.core.legal.LegalRepository
import vn.unicom.thi360.core.network.AppLogger
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.network.Thi360SessionStack
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.storage.Tokens
import vn.unicom.thi360.feature.recovery.OpenApiRecoveryGateway
import vn.unicom.thi360.feature.recovery.ResetLink
import vn.unicom.thi360.shell.routes.InboundLinkRouter

internal class PortalTestHarness(
    val server: ContractFakeServer,
    val tokenStore: InMemoryTokenStore,
    val storeRootDir: File,
    val accountScopedStoreProvider: AccountScopedStoreProvider,
    val sessionCleanup: SessionCleanup,
    val connectivityMonitor: MutableConnectivityMonitor,
    val inboundLinkRouter: InboundLinkRouter,
    val legalRepository: LegalRepository,
    val stack: Thi360SessionStack,
    val recoveryGateway: OpenApiRecoveryGateway,
    val logs: MutableList<String>,
) : AutoCloseable {

    override fun close() {
        runCatching { server.shutdown() }
        runCatching { storeRootDir.deleteRecursively() }
    }

    companion object {
        fun create(
            initialTokens: Tokens? = null,
            environment: Environment = Environment.DEV,
            localeProvider: () -> AppLocale = { AppLocale.VI },
            initialOnline: Boolean = true,
            startLocked: Boolean = false,
            resolveAccountIdOnAuth: Boolean = true,
            installDefaultMeHandler: Boolean = true,
            legalAssetSource: LegalAssetSource = ContractRepoLegalAssetSource(),
            onSessionCleared: () -> Unit = {},
            serverSetup: ContractFakeServer.() -> Unit = {},
        ): PortalTestHarness {
            val server = ContractFakeServer(installDefaultMeHandler = installDefaultMeHandler)
            server.serverSetup()
            server.start()

            val rootDir = Files.createTempDirectory("thi360-portal-harness-").toFile()
            val tokenStore = InMemoryTokenStore(initialTokens)
            val sessionCleanup = SessionCleanup(rootDir)
            val storeProvider = AccountScopedStoreProvider(
                rootDir = rootDir,
                accountIdProvider = { tokenStore.read()?.accountId },
            )
            val logs = mutableListOf<String>()
            val logger = object : AppLogger {
                override fun debug(tag: String, message: String) {
                    logs += "D/$tag: $message"
                }

                override fun warn(tag: String, message: String) {
                    logs += "W/$tag: $message"
                }
            }
            val stack = Thi360ApiFactory.createSessionStack(
                baseUrl = server.url("/"),
                environment = environment,
                tokenStore = tokenStore,
                localeProvider = localeProvider,
                logger = logger,
                onSessionCleared = onSessionCleared,
                startLocked = startLocked,
                sessionCleanup = sessionCleanup,
                resolveAccountIdOnAuth = resolveAccountIdOnAuth,
            )
            val recoveryGateway = OpenApiRecoveryGateway(
                publicAuthApi = stack.publicAuthApi,
                authenticatedAuthApi = stack.authenticatedAuthApi,
            )

            return PortalTestHarness(
                server = server,
                tokenStore = tokenStore,
                storeRootDir = rootDir,
                accountScopedStoreProvider = storeProvider,
                sessionCleanup = sessionCleanup,
                connectivityMonitor = MutableConnectivityMonitor(initialOnline),
                inboundLinkRouter = InboundLinkRouter {
                    ResetLink.allowedHostFromWebBaseUrl(AppEnvironment.webBaseUrl)
                },
                legalRepository = LegalRepository(legalAssetSource),
                stack = stack,
                recoveryGateway = recoveryGateway,
                logs = logs,
            )
        }
    }
}

class ContractRepoLegalAssetSource : LegalAssetSource {
    private val legalDir: File by lazy {
        val specDir = vn.unicom.thi360.core.network.ContractFixtureLoader.openApiSpecFile().parentFile
            ?: error("Cannot locate contracts/mobile directory")
        File(specDir, "legal")
    }

    override fun open(assetPath: String): InputStream {
        val name = assetPath.removePrefix("legal/")
        val file = File(legalDir, name)
        if (!file.isFile) {
            throw FileNotFoundException("Missing legal asset: $assetPath")
        }
        return file.inputStream()
    }
}
