package vn.unicom.thi360.feature.catalog

import android.os.Looper
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.navigation.compose.rememberNavController
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import vn.unicom.thi360.core.brand.BrandMarkTestTags
import vn.unicom.thi360.core.config.SupportContact
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.ui.LoadStateTestTags
import vn.unicom.thi360.shell.Thi360App
import vn.unicom.thi360.shell.routes.AppNavigator
import vn.unicom.thi360.shell.routes.RoutePlaceholderTestTags
import vn.unicom.thi360.testing.ContractFakeServer
import vn.unicom.thi360.testing.PortalTestHarness

@RunWith(RobolectricTestRunner::class)
class ExamDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun rendersAllSevenSectionsMergesQuestionSetDetectsChoiceEssayFallsBackMaxPointsAndShowsPolicy() {
        val richDetail = CatalogTestFixtures.examDetailJson { root ->
            root["objectives"] = JsonNull
            root["audience"] = JsonPrimitive("")
            root["parts"] = buildJsonArray {
                add(
                    buildJsonObject {
                        put("order", 1)
                        put("name", "Tư duy định lượng")
                        put("required", true)
                        put("questionCount", buildJsonObject { put("min", 30); put("max", 30) })
                        put("durationMinutes", buildJsonObject { put("min", 45); put("max", 45) })
                        put("maxPoints", JsonNull)
                        put(
                            "answerTypes",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("questionType", "SINGLE_CHOICE")
                                        put("questionCount", 20)
                                        put("childAnswerType", JsonNull)
                                    },
                                )
                                add(
                                    buildJsonObject {
                                        put("questionType", "QUESTION_SET")
                                        put("questionCount", 10)
                                        put("childAnswerType", "SINGLE_CHOICE")
                                    },
                                )
                            },
                        )
                    },
                )
                add(
                    buildJsonObject {
                        put("order", 2)
                        put("name", "Tự chọn ngôn ngữ")
                        put("required", false)
                        put("questionCount", JsonNull)
                        put("durationMinutes", JsonNull)
                        put("maxPoints", JsonNull)
                        put("answerTypes", buildJsonArray {})
                    },
                )
            }
            root["choices"] = buildJsonArray {
                add(
                    buildJsonObject {
                        put("partName", "Tự chọn ngôn ngữ")
                        put(
                            "options",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("kind", "SUBJECT_CHOICE")
                                        put("code", "OPT-ESSAY")
                                        put("name", "Bài luận tiếng Anh")
                                        put("branch", JsonNull)
                                        put("chooseSubjects", 1)
                                        put("countsByPick", buildJsonArray { add(JsonPrimitive(2)) })
                                        put(
                                            "subjects",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put("code", "ANH")
                                                        put("nameVi", "Tiếng Anh")
                                                        put("nameEn", "English")
                                                    },
                                                )
                                            },
                                        )
                                        put("answerTypes", buildJsonArray {})
                                        put(
                                            "subjectAnswerTypes",
                                            buildJsonArray {
                                                add(
                                                    buildJsonObject {
                                                        put(
                                                            "subject",
                                                            buildJsonObject {
                                                                put("code", "ANH")
                                                                put("nameVi", "Tiếng Anh")
                                                                put("nameEn", "English")
                                                            },
                                                        )
                                                        put(
                                                            "answerTypes",
                                                            buildJsonArray {
                                                                add(
                                                                    buildJsonObject {
                                                                        put("questionType", "ESSAY")
                                                                        put("questionCount", 1)
                                                                        put("childAnswerType", JsonNull)
                                                                    },
                                                                )
                                                            },
                                                        )
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
            root["scoring"] = buildJsonObject {
                put("timing", "PER_PART")
                put("maxScore", JsonNull)
                put("referenceMax", JsonNull)
                put("practiceScoring", "RAW")
            }
            root["omittedEssayGroups"] = buildJsonArray {
                add(
                    buildJsonObject {
                        put("name", "Văn tự luận")
                        put("points", 20)
                    },
                )
                add(
                    buildJsonObject {
                        put("name", "Viết luận")
                        put("points", 15)
                    },
                )
            }
        }

        val failFirst = AtomicBoolean(true)
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01") {
                if (failFirst.getAndSet(false)) {
                    ContractFakeServer.jsonResponse(429, CatalogTestFixtures.rateLimited429Json())
                } else {
                    ContractFakeServer.jsonResponse(200, richDetail)
                }
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(
                harness = harness,
                supportContact = SupportContact(phone = "", email = "", hours = ""),
            )
            var navRef: AppNavigator? = null
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }
            composeRule.runOnIdle { navRef!!.openProduct("EXAM-DEMO-01") }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1 &&
                    composeRule.onAllNodesWithTag(LoadStateTestTags.RETRY_BUTTON).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(LoadStateTestTags.RETRY_BUTTON).assertIsDisplayed().performClick()
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01") == 2 &&
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.PRODUCT_CODE).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.RIGHTS_SECTION).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_CODE)
                .assertTextContains("EXAM-DEMO-01", substring = true)
            composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_NAME)
                .assertTextContains("Đề thi HSA tổng hợp DEMO")
            assertTrue(
                "Top bar and hero should display product name",
                composeRule.onAllNodesWithText("Đề thi HSA tổng hợp DEMO").fetchSemanticsNodes().size >= 2,
            )
            assertTrue(
                "Exam detail hero kind chip must use examKind.code (HSA)",
                composeRule.onAllNodesWithText("HSA").fetchSemanticsNodes().size >= 2,
            )
            assertTrue(composeRule.onAllNodesWithText("Mục tiêu").fetchSemanticsNodes().isEmpty())
            assertTrue(composeRule.onAllNodesWithText("Đối tượng phù hợp").fetchSemanticsNodes().isEmpty())

            composeRule.onNodeWithTag(ProductDetailTestTags.STRUCTURE_SECTION).performScrollTo().assertIsDisplayed()
            composeRule.onAllNodesWithText("—").onFirst().assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.CHOICES_SECTION).performScrollTo().assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.HOW_SECTION).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Trắc nghiệm một đáp án: 30", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("100 điểm", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("75 phút · Tính giờ từng phần", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Chuyển phần").assertIsDisplayed()
            composeRule.onNodeWithText("Phần tự luận chấm tay", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Không có phần tự luận: Văn tự luận · 20 điểm").assertIsDisplayed()
            composeRule.onNodeWithText("Không có phần tự luận: Viết luận · 15 điểm").assertIsDisplayed()

            composeRule.onNodeWithTag(ProductDetailTestTags.RIGHTS_SECTION).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Sau khi người chấm chấm xong").assertIsDisplayed()
            val rightsTexts = collectAllText(
                composeRule.onNodeWithTag(ProductDetailTestTags.RIGHTS_SECTION).fetchSemanticsNode(),
            )
            assertTrue(
                "Rights section must display '3 lượt thi mỗi lần mua', got $rightsTexts",
                rightsTexts.any { it.contains("3 lượt thi mỗi lần mua") },
            )
            val aiIndex = rightsTexts.indexOf("Giải thích bằng AI")
            val strengthsIndex = rightsTexts.indexOf("Đánh giá điểm mạnh, điểm yếu")
            val practiceIndex = rightsTexts.indexOf("Ôn luyện bổ sung")
            assertTrue(
                "Rights benefits order must be AI -> Strengths -> Practice, got $rightsTexts",
                aiIndex in 0 until strengthsIndex && strengthsIndex < practiceIndex,
            )

            composeRule.onNodeWithTag(ProductDetailTestTags.POLICY_SECTION).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("theo chính sách do đơn vị vận hành công bố", substring = true).assertIsDisplayed()
            composeRule.onNodeWithText("Thông tin liên hệ đang được cập nhật.").assertIsDisplayed()
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun offlineRecoveryAutoRetriesProductDetail() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
            initialOnline = false,
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            injectNetworkError("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01", count = 1)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            var navRef: AppNavigator? = null
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }
            composeRule.runOnIdle { navRef!!.openProduct("EXAM-DEMO-01") }
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(LoadStateTestTags.ERROR_CONTAINER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Không có kết nối").assertIsDisplayed()

            harness.connectivityMonitor.setOnline(true)
            composeRule.waitUntilIdle {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.PRODUCT_CODE).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag(ProductDetailTestTags.PRODUCT_CODE)
                .assertTextContains("EXAM-DEMO-01", substring = true)
        }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun coverPlaceholderBrandMark() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            var navRef: AppNavigator? = null
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }
            composeRule.runOnIdle { navRef!!.openProduct("EXAM-DEMO-01") }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                harness.server.requestCount("GET", "/api/v1/public/storefront/products/EXAM-DEMO-01") == 1 &&
                    composeRule.onAllNodesWithTag(ProductDetailTestTags.HERO_BRAND_PLACEHOLDER).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.HERO_BRAND_PLACEHOLDER).assertIsDisplayed()
            composeRule.onNodeWithTag(BrandMarkTestTags.ICON, useUnmergedTree = true).assertIsDisplayed()
            val placeholderRoot = composeRule.onNodeWithTag(
                ProductDetailTestTags.HERO_BRAND_PLACEHOLDER,
                useUnmergedTree = true,
            ).fetchSemanticsNode()
            val placeholderTexts = collectAllText(placeholderRoot)
            assertTrue(
                "Cover placeholder must NOT render hardcoded '360' text, found=$placeholderTexts",
                placeholderTexts.none { it.contains("360") },
            )
            assertTrue(
                "Cover placeholder must display examKind code HSA, found=$placeholderTexts",
                placeholderTexts.any { it == "HSA" },
            )
        }
    }

    private fun collectAllText(node: SemanticsNode): List<String> {
        val own = node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        return own + node.children.flatMap { collectAllText(it) }
    }

    @Test
    @Config(qualifiers = "w412dp-h915dp")
    fun pricePanel() {
        PortalTestHarness.create(
            initialTokens = CatalogTestFixtures.activeStudentTokens(),
        ) {
            CatalogTestFixtures.installDefaultStorefrontRoutes(this)
            respondDynamic("POST", "/api/v1/me/exam-access/query") { req ->
                val reqCodes = CatalogTestFixtures.parseAccessRequestCodes(req.body.clone().readUtf8())
                val body = CatalogTestFixtures.accessResponseForCodes(reqCodes) { _, obj ->
                    obj["state"] = JsonPrimitive("EXHAUSTED")
                    obj["attemptsLeft"] = JsonPrimitive(0)
                }
                ContractFakeServer.jsonResponse(200, body)
            }
        }.use { harness ->
            val deps = CatalogTestFixtures.createDependencies(harness)
            var navRef: AppNavigator? = null
            composeRule.setContent {
                val navController = rememberNavController()
                val appNav = AppNavigator(navController)
                navRef = appNav
                CompositionLocalProvider(LocalCatalogDependencies provides deps) {
                    Thi360App(
                        preferences = AppPreferences(locale = AppLocale.VI),
                        navController = navController,
                        navigator = appNav,
                    )
                }
            }
            composeRule.runOnIdle { navRef!!.openProduct("EXAM-DEMO-01") }
            composeRule.waitUntil(10_000) {
                Shadows.shadowOf(Looper.getMainLooper()).idle()
                composeRule.onAllNodesWithTag(ProductDetailTestTags.PRICE_PANEL).fetchSemanticsNodes().isNotEmpty() &&
                    composeRule.onAllNodesWithText("Bạn đã dùng hết lượt của bài thi này.").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithTag(ProductDetailTestTags.PRICE_PANEL).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Giá bán").assertIsDisplayed()
            composeRule.onNodeWithText("Đã gồm thuế, phí").assertIsDisplayed()
            composeRule.onNodeWithText("Bạn đã dùng hết lượt của bài thi này.").assertIsDisplayed()
            composeRule.onNodeWithTag(CatalogTestTags.productPrimaryAction("EXAM-DEMO-01"))
                .assertTextContains("Mua lại")
                .performClick()

            composeRule.waitForIdle()
            composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_NAME).assertTextContains("checkout")
            composeRule.onNodeWithTag(RoutePlaceholderTestTags.ROUTE_PARAMS)
                .assertTextContains("code=EXAM-DEMO-01", substring = true)
                .assertTextContains("seenVersionNo=1", substring = true)
                .assertTextContains("seenPrice=200000", substring = true)
        }
    }
}
