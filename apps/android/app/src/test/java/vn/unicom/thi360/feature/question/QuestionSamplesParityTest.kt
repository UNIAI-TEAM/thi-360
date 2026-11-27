package vn.unicom.thi360.feature.question

import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class QuestionSamplesParityTest {

    @Test
    fun androidTestEmbeddedFixtureMatchesWebQuestionSamplesJsonByteForByte() {
        val sourceJson = QuestionSamplesLoader.samplesFile().readText(Charsets.UTF_8)
        val rawKt = File(
            QuestionSamplesLoader.repoRoot(),
            "apps/android/app/src/androidTest/java/vn/unicom/thi360/feature/question/QuestionSamplesRawJson.kt",
        ).readText(Charsets.UTF_8)
        val chunks = Regex("\"([A-Za-z0-9+/=]+)\"")
            .findAll(rawKt)
            .map { it.groupValues[1] }
            .joinToString("")
        val decoded = String(Base64.getDecoder().decode(chunks), Charsets.UTF_8)
        assertEquals(sourceJson, decoded)
    }
}
