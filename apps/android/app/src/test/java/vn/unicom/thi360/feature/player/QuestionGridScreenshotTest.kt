package vn.unicom.thi360.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import java.util.UUID
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h560dp-xhdpi")
class QuestionGridScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun sampleGroups(): List<GridGroup> {
        val cells = listOf(
            // Unflagged levels
            GridCellUi(UUID(0L, 1L), 1, ItemCompletion.Answered, flagged = false, isCurrent = false, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 2L), 2, ItemCompletion.Partial, flagged = false, isCurrent = false, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 3L), 3, ItemCompletion.Blank, flagged = false, isCurrent = true, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 4L), 4, ItemCompletion.Answered, flagged = false, isCurrent = false, isUnsaved = true, subjectCode = null),
            // D-29 Flagged 3 levels (Red solid, Red light + dashed, Red light + solid)
            GridCellUi(UUID(0L, 5L), 5, ItemCompletion.Answered, flagged = true, isCurrent = false, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 6L), 6, ItemCompletion.Partial, flagged = true, isCurrent = false, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 7L), 7, ItemCompletion.Blank, flagged = true, isCurrent = false, isUnsaved = false, subjectCode = null),
            GridCellUi(UUID(0L, 8L), 8, ItemCompletion.Answered, flagged = true, isCurrent = true, isUnsaved = true, subjectCode = null),
        )
        return listOf(GridGroup(key = "sample", header = UnitGroupHeader.None, cells = cells))
    }

    @Test
    fun gridFlaggedThreeLevelsLight() {
        capture("player_grid_flagged_light", ThemeMode.LIGHT)
    }

    @Test
    fun gridFlaggedThreeLevelsDark() {
        capture("player_grid_flagged_dark", ThemeMode.DARK)
    }

    private fun capture(name: String, themeMode: ThemeMode) {
        composeRule.setContent {
            PlayerTestTheme(themeMode = themeMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Thi360Theme.colors.bgSurface),
                ) {
                    QuestionGridBody(
                        gridGroups = sampleGroups(),
                        filter = GridFilter.All,
                        subjectsByCode = emptyMap(),
                        onSelectFilter = {},
                        onSelectItem = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
