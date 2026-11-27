package vn.unicom.thi360.core.legal

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import vn.unicom.thi360.core.network.ContractFixtureLoader

class ConsentStringsMatchWebTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun repoRoot(): File =
        ContractFixtureLoader.openApiSpecFile().canonicalFile.parentFile!!.parentFile!!.parentFile!!

    private fun resolveResDir(): File {
        val candidates = listOf(File("src/main/res"), File("app/src/main/res"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/res")
    }

    private fun parseStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val map = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as Element
            map[el.getAttribute("name")] = el.textContent
        }
        return map
    }

    private fun lookupDotPath(root: JsonObject, path: String): String? {
        var current: Any = root
        for (part in path.split('.')) {
            if (current !is JsonObject) return null
            current = current[part] ?: return null
        }
        return (current as? JsonPrimitive)?.content
    }

    @Test
    fun registerConsentAndRequiredStringsMatchWebMessagesVerbatimIncludingTagsInViAndEn() {
        val root = repoRoot()
        val resDir = resolveResDir()
        val webVi = json.parseToJsonElement(
            File(root, "apps/web-student/src/messages/vi.json").readText(Charsets.UTF_8),
        ).jsonObject
        val webEn = json.parseToJsonElement(
            File(root, "apps/web-student/src/messages/en.json").readText(Charsets.UTF_8),
        ).jsonObject

        val androidVi = parseStrings(File(resDir, "values/strings_recovery.xml"))
        val androidEn = parseStrings(File(resDir, "values-en/strings_recovery.xml"))

        val webTermsLabelVi = lookupDotPath(webVi, "account.register.termsLabel")
        val webTermsLabelEn = lookupDotPath(webEn, "account.register.termsLabel")
        val webTermsReqVi = lookupDotPath(webVi, "account.register.termsRequired")
        val webTermsReqEn = lookupDotPath(webEn, "account.register.termsRequired")

        assertNotNull("Missing account.register.termsLabel in web vi.json", webTermsLabelVi)
        assertNotNull("Missing account.register.termsLabel in web en.json", webTermsLabelEn)
        assertNotNull("Missing account.register.termsRequired in web vi.json", webTermsReqVi)
        assertNotNull("Missing account.register.termsRequired in web en.json", webTermsReqEn)

        assertTrue(
            "Web VI account.register.termsLabel must contain <terms> and <privacy> tags (waiting for WEB-1 merge); was: $webTermsLabelVi",
            webTermsLabelVi!!.contains("<terms>") && webTermsLabelVi.contains("<privacy>"),
        )
        assertTrue(
            "Web EN account.register.termsLabel must contain <terms> and <privacy> tags (waiting for WEB-1 merge); was: $webTermsLabelEn",
            webTermsLabelEn!!.contains("<terms>") && webTermsLabelEn.contains("<privacy>"),
        )

        assertEquals(webTermsLabelVi, androidVi["register_terms_label"])
        assertEquals(webTermsLabelEn, androidEn["register_terms_label"])
        assertEquals(webTermsReqVi, androidVi["register_terms_required"])
        assertEquals(webTermsReqEn, androidEn["register_terms_required"])

        // Legacy Phase-1 Terms dialog keys must be removed completely.
        val forbiddenOldKeys = listOf(
            "register_terms_title",
            "register_terms_draft",
            "register_terms_body",
            "register_terms_close",
            "register_terms_view",
        )
        for (oldKey in forbiddenOldKeys) {
            assertTrue("Removed key '$oldKey' must not exist in values/strings_recovery.xml", oldKey !in androidVi)
            assertTrue("Removed key '$oldKey' must not exist in values-en/strings_recovery.xml", oldKey !in androidEn)
        }
    }
}
