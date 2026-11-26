package vn.unicom.thi360.core.legal

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import vn.unicom.thi360.Thi360Application
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LegalDocumentViewTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun loadDraftFixture(): LegalDocument {
        val stream = javaClass.classLoader!!.getResourceAsStream("legal/draft-fixture.vi.json")
            ?: error("Missing test resource legal/draft-fixture.vi.json")
        val raw = stream.use { it.readBytes().toString(Charsets.UTF_8) }
        return LegalRepository.legalJson.decodeFromString(LegalDocument.serializer(), raw)
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun draftBannerOnFixture() {
        val draftDoc = loadDraftFixture()
        assertEquals(LegalStatus.DRAFT, draftDoc.status)
        assertEquals(null, draftDoc.effectiveDate)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentView(document = draftDoc, onOpenOnWeb = {})
            }
        }

        composeRule.onNodeWithTag(LegalTestTags.TITLE).assertIsDisplayed().assertTextContains(draftDoc.title)
        composeRule.onNodeWithTag(LegalTestTags.DRAFT_BANNER)
            .assertIsDisplayed()
            .assertTextContains("Bản nháp, chờ đơn vị vận hành xác nhận.")
        composeRule.onNodeWithTag(LegalTestTags.EFFECTIVE_DATE).assertDoesNotExist()
        draftDoc.blocks.indices.forEach { idx ->
            composeRule.onNodeWithTag(LegalTestTags.block(idx)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun publishedShowsEffectiveDate() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val repo = LegalRepository.create(context)

        for (id in listOf(LegalId.TERMS, LegalId.PRIVACY)) {
            val doc = repo.load(id, AppLocale.VI).getOrThrow()
            assertEquals(LegalStatus.PUBLISHED, doc.status)

            composeRule.setContent {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                    LegalDocumentView(document = doc, onOpenOnWeb = {})
                }
            }
            composeRule.onNodeWithTag(LegalTestTags.EFFECTIVE_DATE)
                .assertIsDisplayed()
                .assertTextContains("Có hiệu lực từ 26/11/2026")
            composeRule.onNodeWithTag(LegalTestTags.DRAFT_BANNER).assertDoesNotExist()
            return
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun publishedPrivacyShowsEffectiveDateAndNoDraftBanner() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val repo = LegalRepository.create(context)
        val doc = repo.load(LegalId.PRIVACY, AppLocale.VI).getOrThrow()
        assertEquals(LegalStatus.PUBLISHED, doc.status)

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentView(document = doc, onOpenOnWeb = {})
            }
        }

        composeRule.onNodeWithTag(LegalTestTags.EFFECTIVE_DATE)
            .assertIsDisplayed()
            .assertTextContains("Có hiệu lực từ 26/11/2026")
        composeRule.onNodeWithTag(LegalTestTags.DRAFT_BANNER).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun rendersEveryBlock() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val repo = LegalRepository.create(context)
        val pairs = listOf(
            LegalId.TERMS to AppLocale.VI,
            LegalId.TERMS to AppLocale.EN,
            LegalId.PRIVACY to AppLocale.VI,
            LegalId.PRIVACY to AppLocale.EN,
        )
        // Verify all 4 bundled documents decode and preserve exact block count and vertical order.
        val docs = pairs.map { (id, locale) -> locale to repo.load(id, locale).getOrThrow() }
        docs.forEach { (_, doc) ->
            assertTrue("Document ${doc.id}.${doc.locale} must have blocks", doc.blocks.isNotEmpty())
        }

        val (initialLocale, initialDoc) = docs.first()
        val activePair = androidx.compose.runtime.mutableStateOf(initialLocale to initialDoc)
        composeRule.setContent {
            val (locale, doc) = activePair.value
            androidx.compose.runtime.key(doc.id, doc.locale) {
                Thi360Theme(preferences = AppPreferences(locale = locale)) {
                    LegalDocumentView(document = doc, onOpenOnWeb = {})
                }
            }
        }
        docs.forEach { (locale, doc) ->
            composeRule.runOnIdle { activePair.value = locale to doc }
            composeRule.waitForIdle()
            val tops = doc.blocks.indices.map { idx ->
                composeRule.onNodeWithTag(LegalTestTags.block(idx)).getUnclippedBoundsInRoot().top
            }
            for (idx in 1 until tops.size) {
                assertTrue(
                    "Block $idx (${tops[idx]}) must render below block ${idx - 1} (${tops[idx - 1]}) in ${doc.id}.${doc.locale}",
                    tops[idx] > tops[idx - 1],
                )
            }
            composeRule.onNodeWithTag(LegalTestTags.block(doc.blocks.size)).assertDoesNotExist()
            composeRule.onNodeWithTag(LegalTestTags.block(0)).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithTag(LegalTestTags.block(doc.blocks.lastIndex)).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun tableScrollsInFrame() {
        val doc = loadDraftFixture()
        val tableIndex = doc.blocks.indexOfFirst { it is LegalBlock.Table }
        assertTrue("Draft fixture must include a table block", tableIndex >= 0)

        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2.0f)) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = ThemeMode.LIGHT)) {
                    LegalDocumentView(document = doc, onOpenOnWeb = {})
                }
            }
        }
        composeRule.waitForIdle()

        val rootNode = composeRule.onRoot().fetchSemanticsNode()
        val rootBounds = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "Root document must not expose horizontal scroll",
            !rootNode.config.contains(SemanticsProperties.HorizontalScrollAxisRange),
        )

        val tableScrollNode = composeRule.onNodeWithTag(LegalTestTags.tableScroll(tableIndex))
            .performScrollTo()
            .assertIsDisplayed()
            .fetchSemanticsNode()
        assertTrue(
            "Table wrapper must expose horizontalScroll semantics",
            tableScrollNode.config.contains(SemanticsProperties.HorizontalScrollAxisRange) ||
                tableScrollNode.config.contains(SemanticsActions.ScrollBy),
        )
        val tableBounds = composeRule.onNodeWithTag(LegalTestTags.block(tableIndex)).getUnclippedBoundsInRoot()
        assertTrue(
            "Table outer frame must stay within 360dp viewport (right=${tableBounds.right}, rootRight=${rootBounds.right})",
            tableBounds.right <= rootBounds.right + 1.dp,
        )
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun headingsAreHeadings() {
        val doc = loadDraftFixture()
        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI)) {
                LegalDocumentView(document = doc, onOpenOnWeb = {})
            }
        }

        val titleSemantics = composeRule.onNodeWithTag(LegalTestTags.TITLE).fetchSemanticsNode()
        assertTrue("Document title must have heading semantics", titleSemantics.config.contains(SemanticsProperties.Heading))

        doc.blocks.forEachIndexed { idx, block ->
            if (block is LegalBlock.Heading) {
                val node = composeRule.onNodeWithTag(LegalTestTags.block(idx))
                    .performScrollTo()
                    .fetchSemanticsNode()
                assertTrue(
                    "Heading block $idx ('${block.text}') must expose TalkBack heading() semantics",
                    node.config.contains(SemanticsProperties.Heading),
                )
            }
        }
    }

    @Test
    @Config(qualifiers = "w390dp-h844dp")
    fun englishShowsNote() {
        val context = ApplicationProvider.getApplicationContext<Thi360Application>()
        val repo = LegalRepository.create(context)
        val enDoc = repo.load(LegalId.TERMS, AppLocale.EN).getOrThrow()
        assertTrue("English legal document must have a non-blank note", !enDoc.note.isNullOrBlank())

        composeRule.setContent {
            Thi360Theme(preferences = AppPreferences(locale = AppLocale.EN)) {
                LegalDocumentView(document = enDoc, onOpenOnWeb = {})
            }
        }

        composeRule.onNodeWithTag(LegalTestTags.NOTE)
            .assertIsDisplayed()
            .assertTextContains(enDoc.note!!)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun legal_360x640_fs200_light() {
        captureLegalSnapshot("legal_360x640_fs200_light", ThemeMode.LIGHT)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun legal_360x640_fs200_dark() {
        captureLegalSnapshot("legal_360x640_fs200_dark", ThemeMode.DARK)
    }

    private fun captureLegalSnapshot(name: String, themeMode: ThemeMode) {
        val doc = loadDraftFixture()
        composeRule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = 2.0f)) {
                Thi360Theme(preferences = AppPreferences(locale = AppLocale.VI, themeMode = themeMode)) {
                    LegalDocumentView(document = doc, onOpenOnWeb = {})
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/snapshots/$name.png")
    }
}
