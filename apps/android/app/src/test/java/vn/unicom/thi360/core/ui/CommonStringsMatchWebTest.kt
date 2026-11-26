package vn.unicom.thi360.core.ui

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import vn.unicom.thi360.core.network.ContractFixtureLoader

class CommonStringsMatchWebTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun repoRoot(): File =
        ContractFixtureLoader.openApiSpecFile().canonicalFile.parentFile!!.parentFile!!.parentFile!!

    private fun resolveResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/res")
    }

    private fun parseStringsXml(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = linkedMapOf<String, String>()
        val strings = doc.getElementsByTagName("string")
        for (i in 0 until strings.length) {
            val el = strings.item(i) as Element
            result[el.getAttribute("name")] = el.textContent
        }
        val plurals = doc.getElementsByTagName("plurals")
        for (i in 0 until plurals.length) {
            val el = plurals.item(i) as Element
            val items = el.getElementsByTagName("item")
            for (j in 0 until items.length) {
                val item = items.item(j) as Element
                if (item.getAttribute("quantity") == "other") {
                    result[el.getAttribute("name")] = item.textContent
                }
            }
        }
        return result
    }

    private fun lookupDotPath(root: JsonObject, dotPath: String): String? {
        var current: Any = root
        for (segment in dotPath.split('.')) {
            if (current !is JsonObject) return null
            val child = current[segment] ?: return null
            current = child
        }
        return (current as? kotlinx.serialization.json.JsonPrimitive)?.content
    }

    /**
     * Normalize web ICU placeholders ({traceId}, {count}, {n}, {minutes}, etc.) and Android format specifiers
     * (%1$s, %1$d) to a common canonical marker so literal wording is verified character-for-character.
     */
    private fun normalizePlaceholders(raw: String): String = raw
        .replace(Regex("""\{[a-zA-Z0-9_]+,\s*plural,(?:\s*one\s*\{[^}]*\})?\s*other\s*\{([^}]*)\}\}""")) { match ->
            match.groupValues[1].replace("#", "{var}")
        }
        .replace(Regex("""\{[a-zA-Z0-9_]+\}"""), "{var}")
        .replace(Regex("""%\d+\$[sd]"""), "{var}")
        .trim()

    @Test
    fun commonStringsMatchWebStudentMessagesInViAndEn() {
        val root = repoRoot()
        val resDir = resolveResDir()
        val webVi = json.parseToJsonElement(
            File(root, "apps/web-student/src/messages/vi.json").readText(Charsets.UTF_8),
        ).jsonObject
        val webEn = json.parseToJsonElement(
            File(root, "apps/web-student/src/messages/en.json").readText(Charsets.UTF_8),
        ).jsonObject

        val androidVi = parseStringsXml(File(resDir, "values/strings_common.xml"))
        val androidEn = parseStringsXml(File(resDir, "values-en/strings_common.xml"))

        // Mandatory B1.10 Android-to-Web key mapping.
        val keyMap = linkedMapOf(
            "common_backHome" to "common.backHome",
            "common_closeToast" to "common.closeToast",
            "common_loading" to "common.loading",
            "common_notificationsLabel" to "common.notificationsLabel",
            "common_retry" to "common.retry",
            "errorPage_eyebrow" to "errorPage.eyebrow",
            "errorPage_title" to "errorPage.title",
            "errorPage_description" to "errorPage.description",
            "errorPage_traceLabel" to "errorPage.traceLabel",
            "landing_support_pending" to "landing.support.pending",
            "storefront_format_BUNDLE" to "storefront.format.BUNDLE",
            "storefront_format_FULL" to "storefront.format.FULL",
            "storefront_format_PART" to "storefront.format.PART",
            "storefront_format_SUBJECT" to "storefront.format.SUBJECT",
            "storefront_review" to "storefront.review",
            "storefront_units_attempts" to "storefront.units.attempts",
            "storefront_units_attemptsPerExam" to "storefront.units.attemptsPerExam",
            "storefront_units_minutes" to "storefront.units.minutes",
            "storefront_units_questions" to "storefront.units.questions",
        )

        // Check offline keys separately (mobile-defined or web-aligned).
        assertEquals("Không có kết nối", androidVi["common_offline_title"])
        assertEquals("Kiểm tra kết nối mạng rồi thử lại.", androidVi["common_offline_description"])
        assertEquals("No connection", androidEn["common_offline_title"])
        assertEquals("Check your internet connection and try again.", androidEn["common_offline_description"])

        for ((androidKey, webDotPath) in keyMap) {
            val expectedVi = lookupDotPath(webVi, webDotPath)
            val expectedEn = lookupDotPath(webEn, webDotPath)
            assertNotNull("Missing web VI key '$webDotPath'", expectedVi)
            assertNotNull("Missing web EN key '$webDotPath'", expectedEn)
            val actualVi = androidVi[androidKey]
            val actualEn = androidEn[androidKey]
            assertNotNull("Missing Android VI key '$androidKey' in strings_common.xml", actualVi)
            assertNotNull("Missing Android EN key '$androidKey' in strings_common.xml", actualEn)

            assertEquals(
                "VI wording mismatch between Android '$androidKey' and web '$webDotPath'",
                normalizePlaceholders(expectedVi!!),
                normalizePlaceholders(actualVi!!),
            )
            assertEquals(
                "EN wording mismatch between Android '$androidKey' and web '$webDotPath'",
                normalizePlaceholders(expectedEn!!),
                normalizePlaceholders(actualEn!!),
            )
        }

        // account.deletion.deletedNotice is added in Wave 2 by WEB-2; compare strictly when present in web messages.
        val webDeletedVi = lookupDotPath(webVi, "account.deletion.deletedNotice")
        val webDeletedEn = lookupDotPath(webEn, "account.deletion.deletedNotice")
        assertTrue(
            "account_deletion_deletedNotice must exist in Android strings_common.xml",
            androidVi.containsKey("account_deletion_deletedNotice") &&
                androidEn.containsKey("account_deletion_deletedNotice"),
        )
        if (webDeletedVi != null && webDeletedEn != null) {
            assertEquals(webDeletedVi, androidVi["account_deletion_deletedNotice"])
            assertEquals(webDeletedEn, androidEn["account_deletion_deletedNotice"])
        } else {
            // WEB-2 not yet merged in Wave 1: verify exact B1.15 spec strings directly.
            assertEquals("Tài khoản của bạn đã được xóa.", androidVi["account_deletion_deletedNotice"])
            assertEquals("Your account has been deleted.", androidEn["account_deletion_deletedNotice"])
        }
    }
}
