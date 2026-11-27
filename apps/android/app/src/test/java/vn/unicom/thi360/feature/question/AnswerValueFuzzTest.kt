package vn.unicom.thi360.feature.question

import java.io.File
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.feature.question.logic.AnswerValues
import vn.unicom.thi360.feature.question.logic.WordCounter
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.ClozeBlankItem
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.TrueFalseStatementItem

class AnswerLimitTest {

    @Test
    fun seventyThousandLatinCharsAreNotTruncatedAndNullCharsAreStripped() {
        val longText = "a".repeat(70_000)
        val shortAns = AnswerValues.shortAnswer(longText)
        assertEquals(70_000, shortAns.text?.length)
        assertEquals(longText, shortAns.text)

        val withNull = "ab\u0000cd\u0000"
        assertEquals("abcd", AnswerValues.shortAnswer(withNull).text)

        val clozeItem = QuestionItem.Cloze(
            id = "c1",
            displayNo = 1,
            stem = "",
            passage = "[[b1]]",
            blanks = listOf(ClozeBlankItem("b1")),
        )
        val cloze = AnswerValues.cloze(clozeItem, mapOf("b1" to "x\u0000y"))
        assertEquals("xy", cloze.blanks?.get("b1"))

        val essay = AnswerValues.essay("<p>Bài\u0000 viết</p>")
        assertFalse(essay.html?.contains('\u0000') == true)
        assertEquals("<p>Bài viết</p>", essay.html)
        assertEquals(2, essay.wordCount)
    }
}

class AnswerValueFuzzTest {

    @Test
    fun thousandSeededRandomOperationsPassBackendValidatorInvariantsAndEmitJson() {
        val rng = Random(20261127)
        val singleItem = QuestionItem.SingleChoice(
            id = "fuzz-sc",
            displayNo = 1,
            stem = "",
            options = listOf("a", "b", "c", "d").map { ChoiceOptionItem(it, "<p>$it</p>") },
        )
        val multiItem = QuestionItem.MultipleChoice(
            id = "fuzz-mc",
            displayNo = 2,
            stem = "",
            options = listOf("m1", "m2", "m3", "m4").map { ChoiceOptionItem(it, "<p>$it</p>") },
        )
        val clozeItem = QuestionItem.Cloze(
            id = "fuzz-cloze",
            displayNo = 3,
            stem = "",
            passage = "[[b1]] [[b2]] [[b3]]",
            blanks = listOf("b1", "b2", "b3").map { ClozeBlankItem(it) },
        )
        val tfItem = QuestionItem.TrueFalse(
            id = "fuzz-tf",
            displayNo = 4,
            stem = "",
            statements = listOf("t1", "t2", "t3", "t4").map { TrueFalseStatementItem(it, "<p>$it</p>") },
        )
        val matchExclusive = QuestionItem.Matching(
            id = "fuzz-match-excl",
            displayNo = 5,
            stem = "",
            left = listOf("l1", "l2", "l3").map { MatchingSideItem(it, "<p>$it</p>") },
            right = listOf("r1", "r2", "r3", "r4").map { MatchingSideItem(it, "<p>$it</p>") },
            reuseRight = false,
        )
        val matchReuse = matchExclusive.copy(id = "fuzz-match-reuse", reuseRight = true)

        val entries = ArrayList<JsonObject>(1_000)

        repeat(1_000) { idx ->
            val kind = idx % 7
            val value: AnswerValue = when (kind) {
                0 -> {
                    val candidates = listOf(null, "a", "b", "c", "d", "invalid-opt")
                    AnswerValues.singleChoice(singleItem, candidates[rng.nextInt(candidates.size)])
                }
                1 -> {
                    val pool = listOf("m1", "m2", "m3", "m4", "invalid", "m1")
                    val picked = List(rng.nextInt(0, 6)) { pool[rng.nextInt(pool.size)] }
                    AnswerValues.multipleChoice(multiItem, picked)
                }
                2 -> {
                    val raw = randomText(rng)
                    AnswerValues.shortAnswer(raw)
                }
                3 -> {
                    val pool = listOf("b1", "b2", "b3", "bad-blank")
                    val map = LinkedHashMap<String, String>()
                    repeat(rng.nextInt(0, 5)) {
                        map[pool[rng.nextInt(pool.size)]] = if (rng.nextBoolean()) "" else randomText(rng)
                    }
                    AnswerValues.cloze(clozeItem, map)
                }
                4 -> {
                    val pool = listOf("t1", "t2", "t3", "t4", "bad-stmt")
                    val map = LinkedHashMap<String, Boolean?>()
                    repeat(rng.nextInt(0, 6)) {
                        val pick = when (rng.nextInt(3)) {
                            0 -> true
                            1 -> false
                            else -> null
                        }
                        map[pool[rng.nextInt(pool.size)]] = pick
                    }
                    AnswerValues.trueFalse(tfItem, map)
                }
                5 -> {
                    val item = if (rng.nextBoolean()) matchExclusive else matchReuse
                    val leftPool = listOf("l1", "l2", "l3", "bad-left")
                    val rightPool = listOf(null, "r1", "r2", "r3", "r4", "bad-right")
                    val map = LinkedHashMap<String, String?>()
                    repeat(rng.nextInt(0, 5)) {
                        map[leftPool[rng.nextInt(leftPool.size)]] = rightPool[rng.nextInt(rightPool.size)]
                    }
                    AnswerValues.matching(item, map)
                }
                else -> {
                    val html = if (rng.nextInt(5) == 0) "" else "<p>${randomText(rng).replace("<", "&lt;").replace(">", "&gt;")}</p>"
                    AnswerValues.essay(html)
                }
            }

            assertValidAgainstBackendRules(
                value = value,
                singleItem = singleItem,
                multiItem = multiItem,
                clozeItem = clozeItem,
                tfItem = tfItem,
                matchExclusive = matchExclusive,
            )

            val encoded = AnswerValues.encodeToJson(value)
            val decoded = AnswerValues.decodeFromJson(encoded)
            assertEquals(value, decoded)
            assertFalse(encoded.contains("\\u0000"))

            entries.add(
                buildJsonObject {
                    put("index", idx)
                    put("json", encoded)
                },
            )
        }

        val outDir = File("build/answer-fuzz")
        outDir.mkdirs()
        val outFile = File(outDir, "android.json")
        outFile.writeText(JsonArray(entries).toString(), Charsets.UTF_8)
        assertTrue(outFile.isFile)
        assertEquals(1_000, entries.size)
    }

    private fun randomText(rng: Random): String {
        val alphabet = listOf("a", "b", " ", "56", "Tiếng Việt", "\u0000", "-", "/", "H2O", "2+", "→")
        val sb = StringBuilder()
        repeat(rng.nextInt(0, 8)) {
            sb.append(alphabet[rng.nextInt(alphabet.size)])
        }
        return sb.toString()
    }

    private fun assertValidAgainstBackendRules(
        value: AnswerValue,
        singleItem: QuestionItem.SingleChoice,
        multiItem: QuestionItem.MultipleChoice,
        clozeItem: QuestionItem.Cloze,
        tfItem: QuestionItem.TrueFalse,
        matchExclusive: QuestionItem.Matching,
    ) {
        when (value) {
            is SingleChoiceAnswer -> {
                val id = value.optionId
                if (id != null) {
                    assertTrue(singleItem.options.any { it.id == id })
                }
            }
            is MultipleChoiceAnswer -> {
                val ids = value.optionIds.orEmpty()
                assertEquals(ids.size, ids.toSet().size)
                assertTrue(ids.all { id -> multiItem.options.any { it.id == id } })
            }
            is ShortAnswerValue -> {
                val txt = value.text.orEmpty()
                assertFalse(txt.contains('\u0000'))
            }
            is ClozeAnswer -> {
                val map = value.blanks.orEmpty()
                assertTrue(map.keys.all { k -> clozeItem.blanks.any { it.id == k } })
                assertTrue(map.values.none { it.contains('\u0000') })
            }
            is TrueFalseAnswer -> {
                val map = value.statements.orEmpty()
                assertTrue(map.keys.all { k -> tfItem.statements.any { it.id == k } })
            }
            is MatchingAnswer -> {
                val map = value.pairs.orEmpty()
                assertTrue(map.keys.all { k -> matchExclusive.left.any { it.id == k } })
                assertTrue(map.values.all { v -> matchExclusive.right.any { it.id == v } })
            }
            is EssayAnswer -> {
                val html = value.html.orEmpty()
                assertFalse(html.contains('\u0000'))
                assertEquals(WordCounter.count(html), value.wordCount)
            }
        }
    }
}
