package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.nio.file.Files
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import vn.unicom.thi360.core.network.MutableConnectivityMonitor
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.core.storage.AccountScopedStore

@OptIn(ExperimentalCoroutinesApi::class)
internal class VirtualEngineClock(
    private val testScope: TestScope,
    private val baseElapsedMs: Long = 10_000L,
    initialWallClockMs: Long = 1_763_632_800_000L,
) : EngineClock {
    private var wallOffsetMs: Long = initialWallClockMs

    fun shiftWallClockMs(deltaMs: Long) {
        wallOffsetMs += deltaMs
    }

    override fun elapsedRealtimeMs(): Long = baseElapsedMs + testScope.testScheduler.currentTime

    override fun wallClockMs(): Long = wallOffsetMs + testScope.testScheduler.currentTime
}

internal class ManualEngineClock(
    var elapsedMs: Long = 10_000L,
    var wallMs: Long = 1_763_632_800_000L,
) : EngineClock {
    override fun elapsedRealtimeMs(): Long = elapsedMs
    override fun wallClockMs(): Long = wallMs
}

internal fun createTempStoreRoot(prefix: String = "thi360-and-save-"): File =
    Files.createTempDirectory(prefix).toFile()

internal fun AttemptStateResponse.firstOpenPart(): AttemptPartStateView =
    PartSelection.sortedParts(this).first { it.state == AttemptPartStateView.State.OPEN }

internal fun AttemptStateResponse.firstLeafItem(): AttemptItemView =
    PartSelection.leafItems(firstOpenPart()).first()

internal fun AttemptStateResponse.allLeafItems(): List<AttemptItemView> =
    PartSelection.sortedParts(this).flatMap { PartSelection.leafItems(it) }

internal fun AttemptStateResponse.withFutureDeadlines(
    serverNow: OffsetDateTime = this.serverNow,
    attemptRemainingSeconds: Long = 1_800L,
    partRemainingSeconds: Long = 900L,
): AttemptStateResponse = copy(
    serverNow = serverNow,
    deadlineAt = if (timing == AttemptStateResponse.Timing.SHARED) {
        serverNow.plusSeconds(attemptRemainingSeconds)
    } else {
        deadlineAt
    },
    parts = parts?.map { part ->
        if (part.state == AttemptPartStateView.State.OPEN) {
            part.copy(deadlineAt = serverNow.plusSeconds(partRemainingSeconds))
        } else {
            part
        }
    },
)

@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestScope.newEngineHarness(
    server: FakeAttemptServer,
    storeRoot: File,
    accountId: String = "account-A",
    attemptId: UUID = server.currentAttemptState().attemptId,
    connectivity: MutableConnectivityMonitor = MutableConnectivityMonitor(true),
    uuidGenerator: () -> UUID = UUID::randomUUID,
    startImmediately: Boolean = true,
): Pair<AttemptEngine, VirtualEngineClock> {
    val clock = VirtualEngineClock(this)
    val store = AccountScopedStore(storeRoot, accountId)
    val engine = AttemptEngine(
        attemptId = attemptId,
        accountId = accountId,
        api = server.virtualTimeApi(),
        store = store,
        clock = clock,
        connectivity = connectivity,
        scope = backgroundScope,
        uuidGenerator = uuidGenerator,
        startImmediately = startImmediately,
    )
    return engine to clock
}
