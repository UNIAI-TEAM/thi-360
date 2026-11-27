package vn.unicom.thi360.feature.account

import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import coil3.SingletonImageLoader
import org.junit.After
import org.junit.Assert.assertEquals
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
class AvatarSourceTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var server: ContractFakeServer

    @Before
    fun setUp() {
        server = ContractFakeServer().start()
        server.respondApiFixture("GET", "/api/v1/me/profile", 200, "me-profile-200.json")
        server.respondApiFixture("PUT", "/api/v1/me/avatar", 200, "me-avatar-put-200.json")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun mountWithRegistry(
        cameraAccess: FakeCameraAccess,
        onPickVisualMedia: () -> Uri? = { null },
        onTakePicture: () -> Boolean = { false },
    ): Pair<IntArray, FakeAvatarImagePreparer> {
        val counts = intArrayOf(0, 0)
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                when (contract) {
                    is ActivityResultContracts.PickVisualMedia -> {
                        counts[0]++
                        @Suppress("UNCHECKED_CAST")
                        dispatchResult(requestCode, onPickVisualMedia() as O)
                    }

                    is ActivityResultContracts.TakePicture -> {
                        counts[1]++
                        @Suppress("UNCHECKED_CAST")
                        dispatchResult(requestCode, onTakePicture() as O)
                    }
                }
            }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
        val stack = Thi360ApiFactory.createSessionStack(
            baseUrl = server.url("/"),
            environment = Environment.DEV,
            tokenStore = InMemoryTokenStore(testSignedInTokens()),
            localeProvider = { AppLocale.VI },
        )
        val gateway = OpenApiProfileGateway.fromSessionStack(stack, server.url("/"))
        val store = AccountProfileStore(gateway)
        val preparer = FakeAvatarImagePreparer()
        val deps = ProfileDependencies(
            profileStore = store,
            profileGateway = gateway,
            accountDeletionGateway = OpenApiAccountDeletionGateway.fromSessionStack(stack, server.url("/")),
            sessionManager = stack.sessionManager,
            imageLoader = SingletonImageLoader.get(ApplicationProvider.getApplicationContext()),
            apiBaseUrl = server.url("/"),
            cameraAccess = cameraAccess,
            avatarPreparer = preparer,
        )

        composeRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    ProfileScreen(dependencies = deps)
                }
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        return counts to preparer
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun noCameraHardwareHidesCameraOptionAndCancelMakesZeroApiCalls() {
        val camera = FakeCameraAccess(hasCamera = false, permission = CameraPermissionStatus.Granted)
        val (counts, _) = mountWithRegistry(cameraAccess = camera, onPickVisualMedia = { null })

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_GALLERY).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_CAMERA).assertDoesNotExist()

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_GALLERY).performClick()
        composeRule.waitForIdle()
        assertEquals(1, counts[0])
        assertEquals(0, server.requestCount("PUT", "/api/v1/me/avatar"))
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun deniedCameraShowsBannerAndOpenSettingsWhileGalleryStillWorks() {
        val camera = FakeCameraAccess(hasCamera = true, permission = CameraPermissionStatus.Denied)
        val (counts, _) = mountWithRegistry(
            cameraAccess = camera,
            onPickVisualMedia = { Uri.parse("content://media/avatar/1") },
        )

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_CAMERA).performClick()

        composeRule.onNodeWithTag(ProfileTestTags.CAMERA_DENIED_BANNER).performScrollTo().assertIsDisplayed()
        assertEquals("Camera must not launch when permission is Denied", 0, counts[1])
        composeRule.onNodeWithTag(ProfileTestTags.CAMERA_OPEN_SETTINGS_BUTTON).performScrollTo().performClick()
        assertEquals(1, camera.openSettingsCalls)

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_GALLERY).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("PUT", "/api/v1/me/avatar") == 1
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun notAskedRequestsPermissionAndLaunchesCameraWhenGranted() {
        val camera = FakeCameraAccess(
            hasCamera = true,
            permission = CameraPermissionStatus.NotAsked,
            requestResult = CameraPermissionStatus.Granted,
        )
        val (counts, _) = mountWithRegistry(cameraAccess = camera, onTakePicture = { true })

        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_UPLOAD_BUTTON).performScrollTo().performClick()
        composeRule.onNodeWithTag(ProfileTestTags.AVATAR_SOURCE_CAMERA).performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            server.requestCount("PUT", "/api/v1/me/avatar") == 1
        }
        assertEquals(1, camera.requestPermissionCalls)
        assertEquals(1, counts[1])
    }
}
