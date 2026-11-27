package vn.unicom.thi360

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class StringKeyParityTest {

    private fun resolveResDir(): File {
        val candidates = listOf(
            File("src/main/res"),
            File("app/src/main/res"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Cannot locate src/main/res from working dir: ${File(".").absolutePath}")
    }

    private fun parseResourceKeys(xmlFile: File, tagName: String): Set<String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName(tagName)
        val result = linkedSetOf<String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            assertTrue("Empty $tagName name in ${xmlFile.name}", name.isNotBlank())
            assertTrue("Duplicate $tagName key '$name' in ${xmlFile.name}", result.add(name))
            val value = element.textContent.trim()
            assertTrue("Blank translation for '$name' in ${xmlFile.name}", value.isNotEmpty())
        }
        return result
    }

    @Test
    fun vietnameseAndEnglishStringResourcesHaveIdenticalFilesAndKeys() {
        val resDir = resolveResDir()
        val viDir = File(resDir, "values")
        val enDir = File(resDir, "values-en")

        val viFiles = viDir.listFiles { file -> file.name.startsWith("strings") && file.name.endsWith(".xml") }
            ?.map { it.name }
            ?.toSortedSet()
            ?: emptySet()
        val enFiles = enDir.listFiles { file -> file.name.startsWith("strings") && file.name.endsWith(".xml") }
            ?.map { it.name }
            ?.toSortedSet()
            ?: emptySet()

        assertTrue("Expected at least one strings*.xml file", viFiles.isNotEmpty())
        assertTrue("Expected strings_recovery.xml in values", viFiles.contains("strings_recovery.xml"))
        assertTrue("Expected strings_account.xml in values", viFiles.contains("strings_account.xml"))
        assertTrue("Expected strings_common.xml in values", viFiles.contains("strings_common.xml"))
        assertTrue("Expected strings_legal.xml in values", viFiles.contains("strings_legal.xml"))
        assertEquals("String resource file sets must match between values and values-en", viFiles, enFiles)

        val seenViStrings = linkedMapOf<String, String>()
        val seenEnStrings = linkedMapOf<String, String>()
        val seenViPlurals = linkedMapOf<String, String>()
        val seenEnPlurals = linkedMapOf<String, String>()

        for (fileName in viFiles) {
            val viKeys = parseResourceKeys(File(viDir, fileName), "string")
            val enKeys = parseResourceKeys(File(enDir, fileName), "string")
            assertEquals("String key sets in $fileName must match between values and values-en", viKeys, enKeys)

            for (key in viKeys) {
                val existing = seenViStrings.put(key, fileName)
                assertNull("Duplicate string key '$key' across values/$existing and values/$fileName", existing)
            }
            for (key in enKeys) {
                val existing = seenEnStrings.put(key, fileName)
                assertNull("Duplicate string key '$key' across values-en/$existing and values-en/$fileName", existing)
            }

            val viPlurals = parseResourceKeys(File(viDir, fileName), "plurals")
            val enPlurals = parseResourceKeys(File(enDir, fileName), "plurals")
            assertEquals("Plurals key sets in $fileName must match between values and values-en", viPlurals, enPlurals)

            for (key in viPlurals) {
                val existing = seenViPlurals.put(key, fileName)
                assertNull("Duplicate plurals key '$key' across values/$existing and values/$fileName", existing)
            }
            for (key in enPlurals) {
                val existing = seenEnPlurals.put(key, fileName)
                assertNull("Duplicate plurals key '$key' across values-en/$existing and values-en/$fileName", existing)
            }
        }
    }
}
