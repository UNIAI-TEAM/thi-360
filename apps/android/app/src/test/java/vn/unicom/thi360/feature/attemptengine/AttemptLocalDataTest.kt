package vn.unicom.thi360.feature.attemptengine

import java.io.File
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.session.MobileAuthGateway
import vn.unicom.thi360.core.session.RefreshOutcome
import vn.unicom.thi360.core.session.SessionCleanup
import vn.unicom.thi360.core.session.SessionManager
import vn.unicom.thi360.core.session.SessionState
import vn.unicom.thi360.core.session.SignOutReason
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.storage.AccountScopedStoreProvider
import vn.unicom.thi360.core.storage.Tokens

@OptIn(ExperimentalCoroutinesApi::class)
class AttemptLocalDataTest {

    private lateinit var storeRoot: File

    @Before
    fun setUp() {
        storeRoot = createTempStoreRoot("thi360-local-data-")
    }

    @After
    fun tearDown() {
        storeRoot.deleteRecursively()
    }

    @Test
    fun cleanupOnClose() = runTest {
        val closedServer = FakeAttemptServer()
        val submitted = closedServer.loadStateFixture("closed-submitted")
        val attemptId = submitted.attemptId
        val itemId = submitted.allLeafItems().first().itemId!!
        val partId = submitted.parts!!.first().partId!!

        val store = AccountScopedStore(storeRoot, "account-A")
        val draftStore = DraftStore(store)
        draftStore.writeDraft(attemptId, itemId, DraftRecord(seq = 3L, answer = SingleChoiceAnswer("a")))
        draftStore.writePosition(attemptId, EnginePosition(partId, itemId))
        draftStore.writeSessionId(attemptId, UUID.randomUUID())
        store.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.startContextKey(attemptId),
            """{"entitlementId":"ent-1"}""",
        )

        val (closedEngine, _) = newEngineHarness(
            server = closedServer,
            storeRoot = storeRoot,
            accountId = "account-A",
            attemptId = attemptId,
        )
        runCurrent()

        assertNotNull(closedEngine.state.value.closed)
        assertNull(draftStore.readDraft(attemptId, itemId))
        assertNull(draftStore.readPosition(attemptId))
        assertNull(draftStore.readSessionId(attemptId))
        assertNull(
            store.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId)),
        )
        closedEngine.close()

        val abortedServer = FakeAttemptServer()
        val aborted = abortedServer.loadStateFixture("aborted")
        val abortedId = aborted.attemptId
        val orphanItemId = UUID.randomUUID()
        val orphanPartId = UUID.randomUUID()
        val abortedRoot = File(storeRoot, "aborted").apply { mkdirs() }
        val abortedStore = AccountScopedStore(abortedRoot, "account-A")
        val abortedDraftStore = DraftStore(abortedStore)
        abortedDraftStore.writeDraft(abortedId, orphanItemId, DraftRecord(seq = 2L, answer = SingleChoiceAnswer("b")))
        abortedDraftStore.writePosition(abortedId, EnginePosition(orphanPartId, orphanItemId))
        abortedDraftStore.writeSessionId(abortedId, UUID.randomUUID())
        val startCtxPayload = """{"entitlementId":"ent-aborted","examName":"HSA"}"""
        abortedStore.writeString(
            AttemptStorageKeys.NAMESPACE,
            AttemptStorageKeys.startContextKey(abortedId),
            startCtxPayload,
        )

        val (abortedEngine, _) = newEngineHarness(
            server = abortedServer,
            storeRoot = abortedRoot,
            accountId = "account-A",
            attemptId = abortedId,
        )
        runCurrent()

        assertTrue(abortedEngine.state.value.closed is AttemptClosedReason.Aborted)
        assertNull(abortedDraftStore.readDraft(abortedId, orphanItemId))
        assertNull(abortedDraftStore.readPosition(abortedId))
        assertNull(abortedDraftStore.readSessionId(abortedId))
        assertEquals(
            startCtxPayload,
            abortedStore.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(abortedId)),
        )
        abortedEngine.close()

        val reopenedStore = AccountScopedStore(abortedRoot, "account-A")
        assertEquals(
            startCtxPayload,
            reopenedStore.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(abortedId)),
        )
    }

    @Test
    fun accountIsolationMatrix() = runTest {
        val server = FakeAttemptServer()
        val attempt = server.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val attemptId = attempt.attemptId
        val partId = attempt.firstOpenPart().partId!!
        val itemId = attempt.firstLeafItem().itemId!!

        val storeProvider = AccountScopedStoreProvider(storeRoot) { "account-A" }
        val storeA = storeProvider.storeFor("account-A")
        val storeB = storeProvider.storeFor("account-B")
        val draftsA = DraftStore(storeA)
        val draftsB = DraftStore(storeB)

        fun seedAccount(store: AccountScopedStore, ds: DraftStore, option: String, sid: UUID) {
            ds.writeDraft(attemptId, itemId, DraftRecord(seq = 4L, answer = SingleChoiceAnswer(option)))
            ds.writePosition(attemptId, EnginePosition(partId, itemId))
            ds.writeSessionId(attemptId, sid)
            store.writeString(
                AttemptStorageKeys.NAMESPACE,
                AttemptStorageKeys.startContextKey(attemptId),
                """{"owner":"${store.accountId}"}""",
            )
        }

        val sidA = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a")
        val sidB = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b")
        seedAccount(storeA, draftsA, "a", sidA)
        seedAccount(storeB, draftsB, "b", sidB)

        assertEquals(SingleChoiceAnswer("b"), draftsB.readDraft(attemptId, itemId)?.answer)
        assertEquals(sidB, draftsB.readSessionId(attemptId))
        assertEquals(
            """{"owner":"account-B"}""",
            storeB.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId)),
        )
        val (engineB, _) = newEngineHarness(
            server = server,
            storeRoot = storeRoot,
            accountId = "account-B",
            attemptId = attemptId,
        )
        runCurrent()
        assertEquals(SingleChoiceAnswer("b"), engineB.state.value.answers[itemId]?.value)
        assertEquals(EditSessionState.Editing(sidB), engineB.state.value.session)
        engineB.close()

        val cleanup = SessionCleanup(storeRoot)
        AttemptDataCleanup(storeProvider).registerInto(cleanup)

        val tokenStore = InMemoryTokenStore(
            Tokens("acc-a", "ref-a", 300, 604800, false, "userA", accountId = "account-A"),
        )
        var refreshShouldFail401 = false
        val gateway = object : MobileAuthGateway {
            override suspend fun login(identifier: String, password: String): ApiResult<Tokens> {
                val acc = if (identifier == "userB") "account-B" else "account-A"
                return ApiResult.Success(Tokens("acc-$acc", "ref-$acc", 300, 604800, false, identifier, accountId = acc))
            }

            override suspend fun refresh(refreshToken: String): ApiResult<Tokens> =
                if (refreshShouldFail401) {
                    ApiResult.HttpFailure(401, ApiError(code = "SESSION_REVOKED"))
                } else {
                    ApiResult.Success(Tokens("acc-new", refreshToken, 300, 604800, false, "userA", accountId = "account-A"))
                }

            override suspend fun logout(refreshToken: String): ApiResult<Unit> = ApiResult.Success(Unit)
        }
        val sessionManager = SessionManager(
            tokenStore = tokenStore,
            authGateway = gateway,
            startLocked = true,
            backgroundScope = backgroundScope,
            sessionCleanup = cleanup,
        )

        sessionManager.replaceSessionFromLock("userB", "PassB1!")
        assertNotNull(draftsA.readDraft(attemptId, itemId))
        assertEquals(sidA, draftsA.readSessionId(attemptId))
        assertEquals(
            """{"owner":"account-A"}""",
            storeA.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId)),
        )

        sessionManager.login("userA", "PassA1!")
        refreshShouldFail401 = true
        val refreshOutcome = sessionManager.refreshSession()
        assertEquals(RefreshOutcome.Expired, refreshOutcome)
        assertEquals(SessionState.SignedOut(SignOutReason.Expired), sessionManager.state.value)
        assertNotNull("Failed refresh must preserve Account A drafts", draftsA.readDraft(attemptId, itemId))
        assertEquals(sidA, draftsA.readSessionId(attemptId))

        val reLoginServer = FakeAttemptServer()
        reLoginServer.loadStateFixture("in-progress-per-part") { it.withFutureDeadlines() }
        val (reLoginEngineA, _) = newEngineHarness(
            server = reLoginServer,
            storeRoot = storeRoot,
            accountId = "account-A",
            attemptId = attemptId,
        )
        runCurrent()
        assertEquals(1, reLoginServer.callCount(AttemptOperation.SAVE_ANSWERS))
        assertEquals(SingleChoiceAnswer("a"), reLoginServer.currentAttemptState().allLeafItems().first { it.itemId == itemId }.answer)
        reLoginEngineA.close()

        seedAccount(storeA, draftsA, "a", sidA)
        seedAccount(storeB, draftsB, "b", sidB)
        sessionManager.login("userA", "PassA1!")
        val savesBeforeLogout = server.callCount(AttemptOperation.SAVE_ANSWERS)
        sessionManager.logout(SignOutReason.UserInitiated)

        assertEquals(savesBeforeLogout, server.callCount(AttemptOperation.SAVE_ANSWERS))
        val freshStoreA = storeProvider.storeFor("account-A")
        val freshDraftsA = DraftStore(freshStoreA)
        assertNull(freshDraftsA.readDraft(attemptId, itemId))
        assertNull(freshDraftsA.readPosition(attemptId))
        assertNull(freshDraftsA.readSessionId(attemptId))
        assertNull(freshStoreA.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId)))

        assertEquals(SingleChoiceAnswer("b"), draftsB.readDraft(attemptId, itemId)?.answer)
        assertEquals(sidB, draftsB.readSessionId(attemptId))
        assertEquals(
            """{"owner":"account-B"}""",
            storeB.readString(AttemptStorageKeys.NAMESPACE, AttemptStorageKeys.startContextKey(attemptId)),
        )

        val crashRoot = File(storeRoot, "interrupted-logout").apply { mkdirs() }
        val crashProvider = AccountScopedStoreProvider(crashRoot) { "account-A" }
        val crashStoreA = crashProvider.storeFor("account-A")
        val crashStoreB = crashProvider.storeFor("account-B")
        seedAccount(crashStoreA, DraftStore(crashStoreA), "a", sidA)
        seedAccount(crashStoreB, DraftStore(crashStoreB), "b", sidB)

        val pendingDir = File(crashRoot, ".pending_cleanup").apply { mkdirs() }
        File(pendingDir, "interrupted-account-a.pending").writeText("account-A", Charsets.UTF_8)

        val crashCleanup = SessionCleanup(crashRoot)
        AttemptDataCleanup(crashProvider).registerInto(crashCleanup)
        crashCleanup.onAppStart()

        val afterCrashA = DraftStore(crashProvider.storeFor("account-A"))
        val afterCrashB = DraftStore(crashProvider.storeFor("account-B"))
        assertNull(afterCrashA.readDraft(attemptId, itemId))
        assertNull(afterCrashA.readSessionId(attemptId))
        assertEquals(SingleChoiceAnswer("b"), afterCrashB.readDraft(attemptId, itemId)?.answer)
        assertEquals(sidB, afterCrashB.readSessionId(attemptId))
    }
}
