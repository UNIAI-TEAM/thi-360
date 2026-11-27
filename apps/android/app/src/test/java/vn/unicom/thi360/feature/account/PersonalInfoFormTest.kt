package vn.unicom.thi360.feature.account

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class PersonalInfoFormTest {

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

    private fun mountProfile(
        initialProfileFixture: String = "me-profile-200.json",
        profileTransform: (String) -> String = { it },
    ): AccountProfileStore {
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, initialProfileFixture, profileTransform)
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        val deps = ProfileDependencies(
            profileStore = store,
            profileGateway = gateway,
            accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
            sessionManager = stack.sessionManager,
            imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
            apiBaseUrl = server.url("/"),
        )
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                ProfileScreen(dependencies = deps)
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.INFO_SECTION).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        return store
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun happyPathOmitsUnchangedPhoneClearsPhoneDiscardsAndDeduplicatesDoubleTap() {
        val releaseFirstPatch = CountDownLatch(1)
        server.respondDynamicSequence(
            "PATCH",
            "/api/v1/me/profile",
            listOf(
                {
                    releaseFirstPatch.await(5, TimeUnit.SECONDS)
                    ContractFakeServer.apiFixtureResponse(200, "me-profile-update-200.json")
                },
                { ContractFakeServer.apiFixtureResponse(200, "me-profile-200.json") },
            ),
        )

        mountProfile("me-profile-200.json")

        composeRule.onNodeWithTag(ProfileTestTags.USERNAME_FIELD).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_EMPTY_HINT).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag(ProfileTestTags.INFO_CANCEL_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Nháp")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_CANCEL_BUTTON).performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().assertTextContains("Nguyễn Văn An")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().assertIsNotEnabled()

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Minh Anh")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        runCatching { composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performClick() }
        releaseFirstPatch.countDown()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.SNACKBAR).assertTextContains("Đã lưu thông tin cá nhân.")
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("PATCH", "/api/v1/me/profile"))
        val firstPatchJson = Json.parseToJsonElement(
            server.requests("PATCH", "/api/v1/me/profile").first().body.clone().readUtf8(),
        ).jsonObject
        assertEquals("Nguyễn Minh Anh", firstPatchJson["fullName"]?.jsonPrimitive?.content)
        assertFalse("Unchanged phone must be omitted from PATCH payload", firstPatchJson.containsKey("phone"))

        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.EMAIL_SECTION).performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).assertIsEnabled().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("PATCH", "/api/v1/me/profile") == 2
        }
        val clearPatchJson = Json.parseToJsonElement(
            server.requests("PATCH", "/api/v1/me/profile").last().body.clone().readUtf8(),
        ).jsonObject
        assertEquals("", clearPatchJson["phone"]?.jsonPrimitive?.content)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun legacyNonMatchingPhoneIsPreservedWhenOnlyEditingFullName() {
        server.respondApiFixture("PATCH", "/api/v1/me/profile", 200, "me-profile-update-200.json") {
            it.replace("0912345678", "+84 912 345 678")
        }
        mountProfile("me-profile-update-200.json") {
            it.replace("0912345678", "+84 912 345 678")
        }

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("Nguyễn Đổi Tên")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("PATCH", "/api/v1/me/profile") == 1
        }
        val body = Json.parseToJsonElement(
            server.lastRequest("PATCH", "/api/v1/me/profile")!!.body.clone().readUtf8(),
        ).jsonObject
        assertEquals("Nguyễn Đổi Tên", body["fullName"]?.jsonPrimitive?.content)
        assertFalse("Legacy unchanged phone must not be sent", body.containsKey("phone"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun refusals() {
        server.respondSequence(
            "PATCH",
            "/api/v1/me/profile",
            { ContractFakeServer.apiFixtureResponse(400, "me-profile-update-400-phone.json") },
            { MockResponse().setResponseCode(500) },
            { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) },
        )

        mountProfile("me-profile-200.json")

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("   ")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_ERROR)
            .performScrollTo()
            .assertTextContains("Vui lòng nhập họ và tên.")
        assertEquals(0, server.requestCount("PATCH", "/api/v1/me/profile"))

        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD).performTextInput("A".repeat(205))
        composeRule.onNodeWithTag(ProfileTestTags.FULL_NAME_FIELD)
            .performScrollTo()
            .assertTextContains("A".repeat(200))

        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().performTextInput("912345678")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_ERROR)
            .performScrollTo()
            .assertTextContains("Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.")
        assertEquals(0, server.requestCount("PATCH", "/api/v1/me/profile"))

        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performTextInput("0912345678")
        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.PHONE_ERROR).performScrollTo().assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        assertEquals(1, server.requestCount("PATCH", "/api/v1/me/profile"))

        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.INFO_ERROR_BANNER)
                    .performScrollTo()
                    .assertTextContains("Không lưu được thay đổi", substring = true)
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag(ProfileTestTags.PHONE_FIELD).performScrollTo().assertTextContains("0912345678")

        composeRule.onNodeWithTag(ProfileTestTags.INFO_SAVE_BUTTON).performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("PATCH", "/api/v1/me/profile") == 3
        }
        composeRule.onNodeWithTag(ProfileTestTags.INFO_ERROR_BANNER).performScrollTo().assertIsDisplayed()
        assertTrue(server.requestCount("PATCH", "/api/v1/me/profile") == 3)
    }
}
