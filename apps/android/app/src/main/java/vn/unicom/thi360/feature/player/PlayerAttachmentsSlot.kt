package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptStateResponse
import vn.unicom.thi360.feature.attemptengine.CloseTarget

@Immutable
data class PlayerAttachmentContext(
    val attempt: AttemptStateResponse,
    val item: AttemptItemView,
    val isReadOnly: Boolean,
)

interface PlayerAttachmentHost {
    @Composable
    fun RenderAttachments(context: PlayerAttachmentContext)

    fun onBeforeClose(target: CloseTarget) {}
}

val LocalPlayerAttachmentHost = staticCompositionLocalOf<PlayerAttachmentHost?> { null }
