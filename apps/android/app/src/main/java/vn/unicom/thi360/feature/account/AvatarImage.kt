package vn.unicom.thi360.feature.account

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.ImageRequest.Builder as CoilImageRequestBuilder
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.unicom.thi360.R
import vn.unicom.thi360.core.config.AppEnvironment
import vn.unicom.thi360.core.theme.Thi360Theme

private sealed interface AvatarLoadState {
    data object Initials : AvatarLoadState
    data class Loaded(val bitmap: androidx.compose.ui.graphics.ImageBitmap) : AvatarLoadState
}

internal object AvatarRequestCoalescer {
    private val inFlight = ConcurrentHashMap<String, Deferred<ImageResult>>()

    suspend fun execute(
        imageLoader: ImageLoader,
        request: ImageRequest,
        coalesceKey: String,
    ): ImageResult {
        val created = CompletableDeferred<ImageResult>()
        val existing = inFlight.putIfAbsent(coalesceKey, created)
        if (existing != null) {
            return existing.await()
        }
        return try {
            val result = withContext(Dispatchers.IO) {
                imageLoader.execute(request)
            }
            created.complete(result)
            result
        } catch (t: Throwable) {
            created.completeExceptionally(t)
            throw t
        } finally {
            inFlight.remove(coalesceKey, created)
        }
    }
}

internal fun resolveAvatarFullUrl(apiBaseUrl: String, avatarUrl: String?): String? {
    val raw = avatarUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        return raw
    }
    val base = apiBaseUrl.trimEnd('/')
    val path = if (raw.startsWith("/")) raw else "/$raw"
    return "$base$path"
}

@Composable
internal fun AuthenticatedAvatar(
    fullName: String?,
    username: String?,
    avatarAssetId: String?,
    avatarUrl: String?,
    size: Dp,
    initialsStyle: TextStyle,
    imageTestTag: String,
    initialsTestTag: String,
    includeSemantics: Boolean,
    modifier: Modifier = Modifier,
    imageLoaderOverride: ImageLoader? = null,
    apiBaseUrlOverride: String? = null,
) {
    val context = LocalContext.current
    val deps = LocalProfileDependencies.current
    val imageLoader = imageLoaderOverride
        ?: deps?.imageLoader
        ?: remember(context) { SingletonImageLoader.get(context) }
    val apiBaseUrl = apiBaseUrlOverride ?: deps?.apiBaseUrl ?: AppEnvironment.apiBaseUrl
    val fullUrl = remember(apiBaseUrl, avatarUrl) {
        resolveAvatarFullUrl(apiBaseUrl, avatarUrl)
    }
    val cacheKey = remember(avatarAssetId, fullUrl) {
        avatarAssetId?.takeIf { it.isNotBlank() } ?: fullUrl
    }

    var loadState by remember(fullUrl, imageLoader) {
        val cached = cacheKey?.let { key ->
            imageLoader.memoryCache?.get(coil3.memory.MemoryCache.Key(key))?.image
        }
        mutableStateOf<AvatarLoadState>(
            if (cached != null) {
                AvatarLoadState.Loaded(cached.toBitmap().asImageBitmap())
            } else {
                AvatarLoadState.Initials
            },
        )
    }

    LaunchedEffect(fullUrl, cacheKey, imageLoader) {
        if (fullUrl == null) {
            loadState = AvatarLoadState.Initials
            return@LaunchedEffect
        }
        val cached = cacheKey?.let { key ->
            imageLoader.memoryCache?.get(coil3.memory.MemoryCache.Key(key))?.image
        }
        if (cached != null) {
            loadState = AvatarLoadState.Loaded(cached.toBitmap().asImageBitmap())
            return@LaunchedEffect
        }
        loadState = AvatarLoadState.Initials
        val request = CoilImageRequestBuilder(context)
            .data(fullUrl)
            .apply {
                if (cacheKey != null) {
                    memoryCacheKey(cacheKey)
                }
            }
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        val coalesceKey = "${System.identityHashCode(imageLoader)}|$fullUrl"
        val result = runCatching {
            AvatarRequestCoalescer.execute(
                imageLoader = imageLoader,
                request = request,
                coalesceKey = coalesceKey,
            )
        }.getOrNull()
        loadState = if (result is SuccessResult) {
            AvatarLoadState.Loaded(result.image.toBitmap().asImageBitmap())
        } else {
            AvatarLoadState.Initials
        }
    }

    val colors = Thi360Theme.colors
    val currentDesc = stringResource(R.string.account_profile_avatar_current)
    val emptyDesc = stringResource(R.string.account_profile_avatar_currentEmpty)

    when (val state = loadState) {
        is AvatarLoadState.Loaded -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.primaryContainerStrong)
                    .then(
                        if (includeSemantics) {
                            Modifier.semantics {
                                role = Role.Image
                                contentDescription = currentDesc
                            }
                        } else {
                            Modifier
                        },
                    )
                    .testTag(imageTestTag),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = state.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        AvatarLoadState.Initials -> {
            Box(
                modifier = modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.primaryContainerStrong)
                    .then(
                        if (includeSemantics) {
                            Modifier.semantics(mergeDescendants = true) {
                                role = Role.Image
                                contentDescription = emptyDesc
                            }
                        } else {
                            Modifier
                        },
                    )
                    .testTag(initialsTestTag),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatAvatarInitials(fullName, username),
                    style = initialsStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.primaryText,
                )
            }
        }
    }
}
