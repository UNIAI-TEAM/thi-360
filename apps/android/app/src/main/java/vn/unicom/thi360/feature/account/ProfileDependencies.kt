package vn.unicom.thi360.feature.account

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import java.time.Clock
import java.util.WeakHashMap
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.session.AppSessionContainer
import vn.unicom.thi360.core.session.SessionManager

class ProfileDraftStore {
    var baselineUsername by mutableStateOf<String?>(null)
        private set
    var baselineFullName by mutableStateOf("")
        private set
    var baselinePhone by mutableStateOf("")
        private set

    var fullName by mutableStateOf("")
    var phone by mutableStateOf("")
    var isChangeEmailOpen by mutableStateOf(false)
    var newEmail by mutableStateOf("")
    var changeEmailPassword by mutableStateOf("")
    var pendingCode by mutableStateOf("")
    private var preserveNextExitFlag: Boolean = false

    val hasActiveInfoDraft: Boolean
        get() = baselineUsername != null && (fullName != baselineFullName || phone != baselinePhone)

    val hasAnyDraft: Boolean
        get() = hasActiveInfoDraft ||
            (isChangeEmailOpen && (newEmail.isNotEmpty() || changeEmailPassword.isNotEmpty())) ||
            pendingCode.isNotEmpty()

    fun preserveNextExit() {
        preserveNextExitFlag = true
    }

    fun consumePreserveNextExit(): Boolean {
        val current = preserveNextExitFlag
        preserveNextExitFlag = false
        return current
    }

    fun syncWithProfile(profile: UserProfile, forceReset: Boolean = false) {
        val serverFullName = profile.fullName
        val serverPhone = profile.phone.orEmpty()
        val profileChanged = baselineUsername != profile.username ||
            baselineFullName != serverFullName ||
            baselinePhone != serverPhone
        if (baselineUsername == null || forceReset || profileChanged) {
            baselineUsername = profile.username
            baselineFullName = serverFullName
            baselinePhone = serverPhone
            fullName = serverFullName
            phone = serverPhone
        }
    }

    fun discardInfoChanges() {
        fullName = baselineFullName
        phone = baselinePhone
    }

    fun clear() {
        baselineUsername = null
        baselineFullName = ""
        baselinePhone = ""
        fullName = ""
        phone = ""
        isChangeEmailOpen = false
        newEmail = ""
        changeEmailPassword = ""
        pendingCode = ""
        preserveNextExitFlag = false
    }
}

class ProfileDraftViewModel : ViewModel() {
    val draftStore = ProfileDraftStore()

    init {
        synchronized(activeDrafts) {
            activeDrafts[this] = Unit
        }
    }

    override fun onCleared() {
        draftStore.clear()
        synchronized(activeDrafts) {
            activeDrafts.remove(this)
        }
    }

    companion object {
        private val activeDrafts = WeakHashMap<ProfileDraftViewModel, Unit>()

        fun clearAllActiveDrafts() {
            val snapshot = synchronized(activeDrafts) { activeDrafts.keys.toList() }
            snapshot.forEach { it.draftStore.clear() }
        }
    }
}

@Composable
internal fun rememberSessionProfileDraftStore(sessionGeneration: Long): ProfileDraftStore {
    val owner = LocalViewModelStoreOwner.current
    return remember(owner, sessionGeneration) {
        if (owner != null) {
            val vm = ViewModelProvider(owner)["profile_draft_$sessionGeneration", ProfileDraftViewModel::class.java]
            vm.draftStore
        } else {
            ProfileDraftStore()
        }
    }
}

@Immutable
data class ProfileDependencies(
    val profileStore: AccountProfileStore,
    val profileGateway: ProfileGateway,
    val accountDeletionGateway: AccountDeletionGateway,
    val sessionManager: SessionManager,
    val imageLoader: ImageLoader,
    val apiBaseUrl: String = AppEnvironment.apiBaseUrl,
    val clock: Clock = Clock.systemUTC(),
    val cameraAccess: CameraAccess? = null,
    val avatarPreparer: AvatarImagePreparer? = null,
    val draftStore: ProfileDraftStore = ProfileDraftStore(),
    val onAnnounceCooldown: ((String) -> Unit)? = null,
    val onOpenMyExams: (() -> Unit)? = null,
    val onOpenOrders: (() -> Unit)? = null,
    val onSelectAccountPane: ((AccountDetailPane) -> Unit)? = null,
)

val LocalProfileDependencies = staticCompositionLocalOf<ProfileDependencies?> { null }

@Composable
internal fun rememberFallbackProfileDependencies(context: Context = LocalContext.current): ProfileDependencies {
    val existing = LocalProfileDependencies.current
    if (existing != null) return existing

    val app = remember(context) { context.applicationContext as? Thi360Application }
    val stack = remember(context) { AppSessionContainer.get(context) }
    val apiBaseUrl = AppEnvironment.apiBaseUrl
    val profileGateway = remember(stack, apiBaseUrl) {
        OpenApiProfileGateway.fromSessionStack(stack = stack, baseUrl = apiBaseUrl)
    }
    val deletionGateway = remember(stack, apiBaseUrl) {
        OpenApiAccountDeletionGateway.fromSessionStack(stack = stack, baseUrl = apiBaseUrl)
    }
    val profileStore = remember(profileGateway, app) {
        AccountProfileStore(
            gateway = profileGateway,
            isOnlineProvider = { app?.connectivityMonitor?.online?.value ?: true },
            sessionCleanup = app?.sessionCleanup,
        )
    }
    val imageLoader = remember(context, app) {
        app?.imageLoader?.imageLoader ?: SingletonImageLoader.get(context)
    }
    val draftStore = remember { ProfileDraftStore() }

    return remember(
        profileStore,
        profileGateway,
        deletionGateway,
        stack.sessionManager,
        imageLoader,
        apiBaseUrl,
        draftStore,
    ) {
        ProfileDependencies(
            profileStore = profileStore,
            profileGateway = profileGateway,
            accountDeletionGateway = deletionGateway,
            sessionManager = stack.sessionManager,
            imageLoader = imageLoader,
            apiBaseUrl = apiBaseUrl,
            draftStore = draftStore,
        )
    }
}
