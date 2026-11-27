package vn.unicom.thi360.feature.question

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import vn.unicom.thi360.feature.question.logic.EssayHtml
import vn.unicom.thi360.feature.question.logic.WordCounter

@RunWith(RobolectricTestRunner::class)
class EssayHtmlRoundTripTest {

    @Test
    fun allTenEssayContractSamplesRoundTripWithExactStructureAndWordCount() {
        val raw = QuestionSamplesLoader.richTextFixtureFile("essays.json").readText(Charsets.UTF_8)
        val root = Json.parseToJsonElement(raw).jsonObject
        val samples = root["samples"]!!.jsonArray
        assertEquals(10, samples.size)

        for (entry in samples) {
            val obj = entry.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val inputHtml = obj["input"]!!.jsonPrimitive.content
            val serverHtml = obj["serverHtml"]!!.jsonPrimitive.content
            val expectedWordCount = obj["wordCount"]!!.jsonPrimitive.int

            val loadedFirst = EssayHtml.importToSpannable(inputHtml)
            val exportedFirst = EssayHtml.export(loadedFirst)

            assertEquals("HTML structure mismatch on $id", canonicalHtml(serverHtml), canonicalHtml(exportedFirst))
            assertEquals("Word count mismatch on $id", expectedWordCount, WordCounter.count(exportedFirst))

            val reloaded = EssayHtml.importToSpannable(exportedFirst)
            val exportedSecond = EssayHtml.export(reloaded)
            assertEquals("Second round-trip drifted on $id", exportedFirst, exportedSecond)
        }
    }

    private fun canonicalHtml(html: String): String {
        if (html.isEmpty()) return ""
        val doc = Jsoup.parseBodyFragment(html)
        doc.outputSettings(Document.OutputSettings().prettyPrint(false))
        return doc.body().html()
    }
}
