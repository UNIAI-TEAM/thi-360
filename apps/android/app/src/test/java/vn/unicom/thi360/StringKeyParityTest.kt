package vn.unicom.thi360

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    private fun parseStringEntries(xmlFile: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile)
        val nodes = doc.getElementsByTagName("string")
        val result = linkedMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            val name = element.getAttribute("name")
            assertTrue("Empty string name in ${xmlFile.name}", name.isNotBlank())
            assertFalse("Duplicate string key '$name' in ${xmlFile.name}", result.containsKey(name))
            val value = element.textContent.trim()
            assertTrue("Blank translation for '$name' in ${xmlFile.name}", value.isNotEmpty())
            result[name] = value
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
        assertEquals("String resource file sets must match between values and values-en", viFiles, enFiles)

        for (fileName in viFiles) {
            val viKeys = parseStringEntries(File(viDir, fileName)).keys
            val enKeys = parseStringEntries(File(enDir, fileName)).keys
            assertEquals("String key sets in $fileName must match between values and values-en", viKeys, enKeys)
        }
    }
}
