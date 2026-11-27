package vn.unicom.thi360.feature.account

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.Environment
import vn.unicom.thi360.core.image.Thi360ImageLoader
import vn.unicom.thi360.core.network.InMemoryTokenStore
import vn.unicom.thi360.core.network.Thi360ApiFactory
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.shell.AppRoot
import vn.unicom.thi360.shell.Destination
import vn.unicom.thi360.testing.ContractFakeServer

@RunWith(RobolectricTestRunner::class)
class AvatarUploadTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun createPickerRegistry(uriProvider: () -> Uri?): ActivityResultRegistryOwner {
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                if (contract is ActivityResultContracts.PickVisualMedia) {
                    @Suppress("UNCHECKED_CAST")
                    dispatchResult(requestCode, uriProvider() as O)
                }
            }
        }
        return object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
    }

    @Test
    @Config(qualifiers = "w840dp-h720dp")
    fun uploadSendsMultipartFilePartDisablesButtonsWhileBusyAndUpdatesAccountCard() {
        val avatarPath = "/api/v1/avatars/00000000-0000-7000-8000-000000000001"
        val releaseUpload = CountDownLatch(1)
        server.respondDynamic("PUT", "/api/v1/me/avatar") {
            releaseUpload.await(5, TimeUnit.SECONDS)
            ContractFakeServer.apiFixtureResponse(200, "me-avatar-put-200.json")
        }
        server.respond("GET", avatarPath) { pngMockResponse() }

        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val loader = Thi360ImageLoader.create(context, server.url("/"), stack)
        val owner = createPickerRegistry { Uri.parse("content://media/avatar/upload") }

        try {
            composeRule.setContent {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                    AppRoot(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        onSelectLocale = {},
                        onSelectThemeMode = {},
                        sessionManager = stack.sessionManager,
                        validateOnLaunch = false,
                        startDestination = Destination.Account.route,
                        profileGateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/")),
                        imageLoader = loader.imageLoader,
                        apiBaseUrl = server.url("/"),
                        cameraAccess = FakeCameraAccess(hasCamera = false),
                        avatarPreparer = FakeAvatarImagePreparer(),
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_GALLERY).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).assertIsNotEnabled()
                    true
                }.getOrDefault(false)
            }

            releaseUpload.countDown()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.SNACKBAR)
                        .assertTextContains("Đã cập nhật ảnh đại diện.")
                    composeRule.onNodeWithTag(AccountTestTags.PROFILE_AVATAR_IMAGE).assertIsDisplayed()
                    true
                }.getOrDefault(false)
            }

            val req = server.requests("PUT", "/api/v1/me/avatar").single()
            assertTrue(req.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))
            val bodyText = req.body.clone().readUtf8()
            assertTrue("Multipart part name must be 'file'", bodyText.contains("name=\"file\""))
        } finally {
            loader.close()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun errorMatrix() {
        server.respondSequence(
            "PUT",
            "/api/v1/me/avatar",
            { ContractFakeServer.apiFixtureResponse(413, "me-avatar-put-413-avatar-too-large.json") },
            { ContractFakeServer.apiFixtureResponse(413, "me-avatar-put-413-upload-too-large.json") },
            { ContractFakeServer.apiFixtureResponse(422, "me-avatar-put-422-avatar-dimensions-exceeded.json") },
            { ContractFakeServer.apiFixtureResponse(422, "me-avatar-put-422-avatar-invalid-image.json") },
            { MockResponse().setResponseCode(500) },
            { MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START) },
        )

        var unreadableMode = false
        val preparer = FakeAvatarImagePreparer {
            if (unreadableMode) {
                AvatarPrepareResult.Unreadable
            } else {
                AvatarPrepareResult.Prepared(
                    PreparedAvatar(
                        bytes = createSampleJpegBytes(),
                        fileName = "avatar.jpg",
                        mimeType = "image/jpeg",
                    ),
                )
            }
        }
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        val owner = createPickerRegistry { Uri.parse("content://media/avatar/err") }

        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                vn.unicom.thi360.core.theme.Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    ProfileScreen(
                        dependencies = ProfileDependencies(
                            profileStore = store,
                            profileGateway = gateway,
                            accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
                            sessionManager = stack.sessionManager,
                            imageLoader = coil3.SingletonImageLoader.get(context),
                            apiBaseUrl = server.url("/"),
                            cameraAccess = FakeCameraAccess(hasCamera = false),
                            avatarPreparer = preparer,
                        ),
                    )
                }
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }

        fun pickGalleryAndAssertError(expectedSnippet: String, expectedRequests: Int) {
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
            composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_GALLERY).performClick()
            composeRule.waitUntil(timeoutMillis = 5_000) {
                server.requestCount("PUT", "/api/v1/me/avatar") == expectedRequests && runCatching {
                    composeRule.onNodeWithTag(ProfileTestTags.AVATAR_ERROR)
                        .performScrollTo()
                        .assertTextContains(expectedSnippet, substring = true)
                    true
                }.getOrDefault(false)
            }
        }

        unreadableMode = true
        pickGalleryAndAssertError("Không đọc được ảnh", expectedRequests = 0)
        assertEquals("Unreadable image must make 0 API requests", 0, server.requestCount("PUT", "/api/v1/me/avatar"))

        unreadableMode = false
        pickGalleryAndAssertError("Ảnh lớn hơn 5 MB", expectedRequests = 1)
        pickGalleryAndAssertError("Ảnh lớn hơn 5 MB", expectedRequests = 2)
        pickGalleryAndAssertError("4.096 × 4.096 px", expectedRequests = 3)
        pickGalleryAndAssertError("Không đọc được ảnh", expectedRequests = 4)
        pickGalleryAndAssertError("Không cập nhật được ảnh đại diện", expectedRequests = 5)
        pickGalleryAndAssertError("Không cập nhật được ảnh đại diện", expectedRequests = 6)
        assertEquals(6, server.requestCount("PUT", "/api/v1/me/avatar"))
    }
}
