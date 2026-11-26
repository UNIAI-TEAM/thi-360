package vn.unicom.thi360.core.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import vn.unicom.thi360.core.network.ApiResult
import vn.unicom.thi360.core.network.safeApiCall
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class LoadStateViewTest {

    private interface ProbeApi {
        @GET("/api/v1/items")
        suspend fun list(@Query("page") page: Int): Response<String>
    }

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun loading() {
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LoadStateView<String>(
                    state = LoadState.Loading,
                    onRetry = {},
                    emptyContent = { Text("Empty", modifier = Modifier.testTag("empty_slot")) },
                    content = { Text(it, modifier = Modifier.testTag("content_slot")) },
                )
            }
        }

        composeRule.onNodeWithTag(LoadStateTestTags.LOADING)
            .assertIsDisplayed()
            .assertContentDescriptionEquals("Đang tải")
        composeRule.onNodeWithTag("empty_slot").assertDoesNotExist()
        composeRule.onNodeWithTag("content_slot").assertDoesNotExist()
    }

    @Test
    fun serverErrorRetriesSameCall() {
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(ProbeApi::class.java)

        server.respondSequence(
            "GET",
            "/api/v1/items?page=2",
            { ContractFakeServer.jsonResponse(500, """{"code":"INTERNAL_ERROR"}""") },
            { ContractFakeServer.jsonResponse(200, "loaded-page-2") },
        )

        var state by mutableStateOf<LoadState<String>>(LoadState.Loading)

        fun triggerLoad() {
            val res = runBlocking(Dispatchers.IO) { safeApiCall { api.list(page = 2) } }
            state = when (res) {
                is ApiResult.Success -> LoadState.Loaded(res.data)
                is ApiResult.HttpFailure -> res.toLoadFailed()
                is ApiResult.NetworkFailure -> res.toLoadFailed(isOnline = true)
            }
        }

        triggerLoad()

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LoadStateView(
                    state = state,
                    onRetry = { triggerLoad() },
                    emptyContent = {},
                    content = { Text(it, modifier = Modifier.testTag("loaded_data")) },
                )
            }
        }

        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertIsDisplayed()
            .assertTextContains("Đã có lỗi máy chủ")
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_DESCRIPTION)
            .assertIsDisplayed()
            .assertTextContains("Đã xảy ra lỗi không mong muốn khi xử lý yêu cầu. Vui lòng thử lại sau.")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON)
            .assertIsDisplayed()
            .assertTextContains("Thử lại")
            .performClick()

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("loaded_data").assertIsDisplayed().assertTextContains("loaded-page-2")

        val calls = server.requests("GET", "/api/v1/items")
        assertEquals(2, calls.size)
        assertEquals("/api/v1/items?page=2", calls[0].path)
        assertEquals("/api/v1/items?page=2", calls[1].path)
    }

    @Test
    fun offlineNoData() {
        var retries = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LoadStateView<String>(
                    state = LoadState.Failed(kind = LoadFailureKind.Offline, canRetry = true),
                    onRetry = { retries++ },
                    emptyContent = { Text("Empty", modifier = Modifier.testTag("empty_slot")) },
                    content = {},
                )
            }
        }

        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_TITLE)
            .assertIsDisplayed()
            .assertTextContains("Không có kết nối")
        composeRule.onNodeWithTag(LoadStateTestTags.ERROR_DESCRIPTION)
            .assertIsDisplayed()
            .assertTextContains("Kiểm tra kết nối mạng rồi thử lại.")
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON)
            .assertIsDisplayed()
            .assertTextContains("Thử lại")
            .performClick()
        assertEquals(1, retries)
        composeRule.onNodeWithTag("empty_slot").assertDoesNotExist()
    }

    @Test
    fun retryBlockedUntilRetryAfter() {
        var fakeClockMillis by mutableLongStateOf(10_000L)
        var retries = 0

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LoadStateView<String>(
                    state = LoadState.Failed(
                        kind = LoadFailureKind.Server,
                        canRetry = true,
                        retryAfter = 5L,
                    ),
                    onRetry = { retries++ },
                    emptyContent = {},
                    clockMillis = { fakeClockMillis },
                    content = {},
                )
            }
        }

        // Blocked before 5s elapse.
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertIsNotEnabled().performClick()
        assertEquals(0, retries)

        composeRule.runOnIdle { fakeClockMillis += 4_999L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertIsNotEnabled()
        assertEquals(0, retries)

        // Exactly at 5s -> enabled and invokes retry once.
        composeRule.runOnIdle { fakeClockMillis += 1L }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertIsEnabled().performClick()
        assertEquals(1, retries)
    }
}
