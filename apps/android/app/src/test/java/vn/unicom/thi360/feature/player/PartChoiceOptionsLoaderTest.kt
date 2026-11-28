package vn.unicom.thi360.feature.player

import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.ApiError
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.storage.AccountScopedStore
import vn.unicom.thi360.core.ui.LoadFailureKind
import vn.unicom.thi360.core.ui.LoadState
import vn.unicom.thi360.feature.prepare.StartContext
import vn.unicom.thi360.feature.prepare.StartContextStore

class PartChoiceOptionsLoaderTest {

    @Test
    fun loadsDirectlyFromStartContextStoreWithoutCallingMyExams() = runTest {
        val tempRoot = Files.createTempDirectory("thi360-choice-loader-").toFile()
        try {
            val store = StartContextStore(AccountScopedStore(tempRoot, "acc-1"))
            val attemptId = UUID.randomUUID()
            val entitlementId = UUID.randomUUID()
            store.save(
                attemptId,
                StartContext(
                    entitlementId = entitlementId.toString(),
                    examTitle = "HSA",
                    examKindLabel = "HSA",
                ),
            )
            val prepare = PlayerTestFixtures.prepareResponse("per-part").copy(entitlementId = entitlementId)
            val api = FakePartChoiceMetadataApi(
                prepareResult = { ApiResult.Success(prepare) },
            )
            val loader = PartChoiceOptionsLoader(store, api)

            val result = loader.load(attemptId)
            assertTrue(result is LoadState.Loaded)
            assertEquals(0, api.listExamsCalls)
            assertEquals(listOf(entitlementId), api.prepareCalls)
            val loaded = (result as LoadState.Loaded).value
            assertEquals(entitlementId, loaded.entitlementId)
            assertTrue(loaded.choices.isNotEmpty())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun fallsBackToMyExamsWhenStartContextMissing() = runTest {
        val myExams = PlayerTestFixtures.myExamsResponse()
        val firstCard = myExams.items.first()
        val attemptId = UUID.randomUUID()
        val entitlementId = firstCard.entitlementId ?: UUID.randomUUID()
        val patchedExams = myExams.copy(
            items = listOf(firstCard.copy(activeAttemptId = attemptId, entitlementId = entitlementId)),
        )
        val prepare = PlayerTestFixtures.prepareResponse("per-part").copy(entitlementId = entitlementId)
        val api = FakePartChoiceMetadataApi(
            examsResult = { ApiResult.Success(patchedExams) },
            prepareResult = { ApiResult.Success(prepare) },
        )
        val loader = PartChoiceOptionsLoader(startContextStore = null, api = api)

        val result = loader.load(attemptId)
        assertTrue(result is LoadState.Loaded)
        assertEquals(1, api.listExamsCalls)
        assertEquals(listOf(entitlementId), api.prepareCalls)
    }

    @Test
    fun returnsFailedStateWhenEntitlementNotFoundOrPrepareFails() = runTest {
        val emptyExams = PlayerTestFixtures.myExamsResponse("empty")
        val apiMissing = FakePartChoiceMetadataApi(
            examsResult = { ApiResult.Success(emptyExams) },
        )
        val missingResult = PartChoiceOptionsLoader(null, apiMissing).load(UUID.randomUUID())
        assertTrue(missingResult is LoadState.Failed)
        assertEquals(LoadFailureKind.NotFound, (missingResult as LoadState.Failed).kind)
        assertTrue(missingResult.canRetry)

        val attemptId = UUID.randomUUID()
        val entitlementId = UUID.randomUUID()
        val myExams = PlayerTestFixtures.myExamsResponse()
        val patchedExams = myExams.copy(
            items = listOf(myExams.items.first().copy(activeAttemptId = attemptId, entitlementId = entitlementId)),
        )
        val apiPrepareFail = FakePartChoiceMetadataApi(
            examsResult = { ApiResult.Success(patchedExams) },
            prepareResult = { ApiResult.HttpFailure(503, ApiError("SERVICE_UNAVAILABLE", "Busy")) },
        )
        val failResult = PartChoiceOptionsLoader(null, apiPrepareFail).load(attemptId)
        assertTrue(failResult is LoadState.Failed)
        assertTrue((failResult as LoadState.Failed).canRetry)
    }
}
