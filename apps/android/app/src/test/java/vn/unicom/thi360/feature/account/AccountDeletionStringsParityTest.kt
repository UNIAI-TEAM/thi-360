package vn.unicom.thi360.feature.account

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import vn.unicom.thi360.core.network.ContractFixtureLoader

class AccountDeletionStringsParityTest {

    private val expectedKeys = listOf(
        "title",
        "explanation",
        "unusedAttemptsWarning",
        "pendingGradingWarning",
        "passwordLabel",
        "acknowledge",
        "submit",
        "confirmTitle",
        "confirmAction",
        "confirmCancel",
        "blockedAttempt",
        "openMyExams",
        "blockedOrder",
        "blockedPendingPayment",
        "openMyOrders",
        "wrongPassword",
        "throttled",
        "failed",
        "deletedNotice",
    )

    private fun repoRoot(): File {
        val openApi = ContractFixtureLoader.openApiSpecFile()
        return openApi.parentFile?.parentFile?.parentFile
            ?: error("Cannot resolve repo root from ${openApi.canonicalPath}")
    }

    private fun loadAndroidStrings(resDir: File): Map<String, String> {
        val result = linkedMapOf<String, String>()
        listOf("strings_profile.xml", "strings_common.xml").forEach { fileName ->
            val file = File(resDir, fileName)
            assertTrue("Missing Android resource file: ${file.canonicalPath}", file.isFile)
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as Element
                val name = el.getAttribute("name")
                val raw = el.textContent
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                result[name] = raw
            }
        }
        return result
    }

    private fun assertLocaleParity(localeCode: String, androidValuesDirName: String) {
        val root = repoRoot()
        val webFile = File(root, "apps/web-student/src/messages/$localeCode.json")
        assertTrue("Missing web messages file: ${webFile.canonicalPath}", webFile.isFile)

        val webJson = Json.parseToJsonElement(webFile.readText(Charsets.UTF_8)).jsonObject
        val accountObj = webJson["account"]?.jsonObject
        assertNotNull("Missing 'account' block in $localeCode.json", accountObj)

        val deletionObj = accountObj?.get("deletion")?.jsonObject
        assertNotNull(
            "Missing 'account.deletion' block in apps/web-student/src/messages/$localeCode.json",
            deletionObj,
        )

        val androidDir = File(root, "apps/android/app/src/main/res/$androidValuesDirName")
        val androidStrings = loadAndroidStrings(androidDir)

        val checkedKeys = linkedSetOf<String>().apply {
            addAll(expectedKeys)
            deletionObj?.keys?.let(::addAll)
        }

        for (key in checkedKeys) {
            val webValue = deletionObj?.get(key)?.jsonPrimitive?.content
            assertNotNull(
                "Missing key 'account.deletion.$key' in apps/web-student/src/messages/$localeCode.json",
                webValue,
            )
            val androidKey = "account_deletion_$key"
            val androidValue = androidStrings[androidKey]
            assertNotNull(
                "Missing Android string '$androidKey' in $androidValuesDirName",
                androidValue,
            )
            assertEquals(
                "Copy mismatch for account.deletion.$key ($localeCode)",
                webValue,
                androidValue,
            )
        }
    }

    @Test
    fun vietnameseAccountDeletionStringsMatchWebStudentCharacterForCharacter() {
        assertLocaleParity(localeCode = "vi", androidValuesDirName = "values")
    }

    @Test
    fun englishAccountDeletionStringsMatchWebStudentCharacterForCharacter() {
        assertLocaleParity(localeCode = "en", androidValuesDirName = "values-en")
    }
}
