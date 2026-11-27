package vn.unicom.thi360.core.brand

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.R

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w140dp-h140dp-xxhdpi")
class LauncherIconScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun captureMask(name: String, maskShape: Shape) {
        composeRule.setContent {
            AdaptiveIconMaskPreview(maskShape = maskShape)
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }

    @Composable
    private fun AdaptiveIconMaskPreview(maskShape: Shape) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE5E7EB)),
            contentAlignment = Alignment.Center,
        ) {
            // Adaptive icon masks reveal the central 72dp viewport of the 108dp canvas.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(maskShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_foreground),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(108.dp),
                )
            }
        }
    }

    @Test
    fun icon_mask_circle() = captureMask("icon_mask_circle", CircleShape)

    @Test
    fun icon_mask_rounded() = captureMask("icon_mask_rounded", RoundedCornerShape(18.dp))

    @Test
    fun icon_mask_squircle() {
        val squircle = GenericShape { size, _ ->
            val w = size.width
            val h = size.height
            moveTo(0f, h / 2f)
            cubicTo(0f, h * 0.08f, w * 0.08f, 0f, w / 2f, 0f)
            cubicTo(w * 0.92f, 0f, w, h * 0.08f, w, h / 2f)
            cubicTo(w, h * 0.92f, w * 0.92f, h, w / 2f, h)
            cubicTo(w * 0.08f, h, 0f, h * 0.92f, 0f, h / 2f)
            close()
        }
        captureMask("icon_mask_squircle", squircle)
    }

    @Test
    fun icon_mask_teardrop() {
        val teardrop = RoundedCornerShape(
            topStart = 36.dp,
            topEnd = 36.dp,
            bottomStart = 36.dp,
            bottomEnd = 8.dp,
        )
        captureMask("icon_mask_teardrop", teardrop)
    }
}
