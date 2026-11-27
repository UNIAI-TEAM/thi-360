package vn.unicom.thi360.feature.question

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import vn.unicom.thi360.core.network.generated.model.AnswerValue
import vn.unicom.thi360.core.network.generated.model.ClozeAnswer
import vn.unicom.thi360.core.network.generated.model.EssayAnswer
import vn.unicom.thi360.core.network.generated.model.MatchingAnswer
import vn.unicom.thi360.core.network.generated.model.MultipleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.ShortAnswerValue
import vn.unicom.thi360.core.network.generated.model.SingleChoiceAnswer
import vn.unicom.thi360.core.network.generated.model.TrueFalseAnswer
import vn.unicom.thi360.core.preferences.AppLocale
import vn.unicom.thi360.core.preferences.AppPreferences
import vn.unicom.thi360.core.preferences.ThemeMode
import vn.unicom.thi360.core.theme.Thi360Theme
import vn.unicom.thi360.feature.question.model.AnswerFormat
import vn.unicom.thi360.feature.question.model.ChoiceOptionItem
import vn.unicom.thi360.feature.question.model.ClozeBlankItem
import vn.unicom.thi360.feature.question.model.EssayCriterionReview
import vn.unicom.thi360.feature.question.model.EssayGradingState
import vn.unicom.thi360.feature.question.model.EssayReview
import vn.unicom.thi360.feature.question.model.ItemOutcome
import vn.unicom.thi360.feature.question.model.ItemReview
import vn.unicom.thi360.feature.question.model.ItemReviewKey
import vn.unicom.thi360.feature.question.model.MatchingSideItem
import vn.unicom.thi360.feature.question.model.MultipleChoiceScoringRule
import vn.unicom.thi360.feature.question.model.PartReviewResult
import vn.unicom.thi360.feature.question.model.QuestionItem
import vn.unicom.thi360.feature.question.model.ReviewLevel
import vn.unicom.thi360.feature.question.model.TrueFalseScoringMethod
import vn.unicom.thi360.feature.question.model.TrueFalseStatementItem
import vn.unicom.thi360.feature.richcontent.formula.SourceBoxFormulaEngine
import vn.unicom.thi360.feature.richcontent.ui.LocalFormulaEngine

data class QuestionSampleBundle(
    val key: String,
    val item: QuestionItem,
    val submitted: AnswerValue?,
    val submittedChildren: Map<String, AnswerValue>,
    val review: ItemReview,
)

object QuestionSamplesLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "apps/web-student/src/app/dev/question-kit/question-samples.json").isFile) {
                return dir
            }
            dir = dir.parentFile
        }
        error("Could not locate repository root containing question-samples.json from ${System.getProperty("user.dir")}")
    }

    fun samplesFile(): File =
        File(repoRoot(), "apps/web-student/src/app/dev/question-kit/question-samples.json")

    fun richTextFixtureFile(name: String): File =
        File(repoRoot(), "contracts/mobile/rich-text/$name")

    fun apiFixtureFile(name: String): File =
        File(repoRoot(), "contracts/mobile/api/$name")

    fun loadAll(): List<QuestionSampleBundle> {
        val raw = samplesFile().readText(Charsets.UTF_8)
        return parseSamplesJson(raw)
    }

    fun loadByKey(key: String): QuestionSampleBundle =
        loadAll().first { it.key == key }

    fun parseSamplesJson(rawJson: String): List<QuestionSampleBundle> {
        val root = json.parseToJsonElement(rawJson).jsonObject
        val samples = root["samples"]?.jsonArray.orEmpty()
        return samples.map { element ->
            val obj = element.jsonObject
            val key = obj["key"]!!.jsonPrimitive.content
            val qObj = obj["question"]!!.jsonObject
            val childObjs = obj["children"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            val mappedChildren = childObjs.map { parseQuestionItem(it, emptyList()) }
            val item = parseQuestionItem(qObj, mappedChildren)
            val submittedObj = obj["submitted"]?.jsonObject
            val submittedChildren = if (item is QuestionItem.QuestionSet && submittedObj != null) {
                val map = LinkedHashMap<String, AnswerValue>()
                val childrenJson = submittedObj["children"]?.jsonObject
                if (childrenJson != null) {
                    for (child in item.children) {
                        val childSub = childrenJson[child.id]?.jsonObject ?: continue
                        val parsed = parseSubmittedAnswer(child, childSub) ?: continue
                        map[child.id] = parsed
                    }
                }
                map
            } else {
                emptyMap()
            }
            val submitted = if (item !is QuestionItem.QuestionSet && submittedObj != null) {
                parseSubmittedAnswer(item, submittedObj)
            } else {
                null
            }
            val reviewObj = obj["review"]!!.jsonObject
            val review = parseReview(qObj, childObjs, reviewObj)
            QuestionSampleBundle(
                key = key,
                item = item,
                submitted = submitted,
                submittedChildren = submittedChildren,
                review = review,
            )
        }
    }

    private fun parseQuestionItem(
        qObj: JsonObject,
        children: List<QuestionItem>,
    ): QuestionItem {
        val id = qObj["id"]!!.jsonPrimitive.content
        val displayNo = qObj["displayNo"]!!.jsonPrimitive.intOrNull ?: 0
        val points = qObj["points"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) }
        val stem = qObj["stem"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val optionOrder = qObj["optionOrder"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        val body = qObj["body"]!!.jsonObject
        return when (val type = body["type"]!!.jsonPrimitive.content) {
            "SINGLE_CHOICE" -> QuestionItem.SingleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body["options"]!!.jsonArray.map { opt ->
                    val o = opt.jsonObject
                    ChoiceOptionItem(
                        id = o["id"]!!.jsonPrimitive.content,
                        content = o["content"]!!.jsonPrimitive.content,
                    )
                },
            )
            "MULTIPLE_CHOICE" -> QuestionItem.MultipleChoice(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                options = body["options"]!!.jsonArray.map { opt ->
                    val o = opt.jsonObject
                    ChoiceOptionItem(
                        id = o["id"]!!.jsonPrimitive.content,
                        content = o["content"]!!.jsonPrimitive.content,
                    )
                },
                scoringRule = MultipleChoiceScoringRule.fromWire(body["scoringRule"]?.jsonPrimitive?.contentOrNull),
            )
            "SHORT_ANSWER" -> QuestionItem.ShortAnswer(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                answerFormat = AnswerFormat.fromWire(body["answerFormat"]?.jsonPrimitive?.contentOrNull),
                inputHint = body["inputHint"]?.jsonPrimitive?.contentOrNull,
            )
            "CLOZE" -> QuestionItem.Cloze(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body["passage"]!!.jsonPrimitive.content,
                blanks = body["blanks"]!!.jsonArray.map { b ->
                    val bObj = b.jsonObject
                    ClozeBlankItem(
                        id = bObj["id"]!!.jsonPrimitive.content,
                        points = bObj["points"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
                    )
                },
                blankNumberStart = body["blankNumberStart"]?.jsonPrimitive?.intOrNull,
            )
            "TRUE_FALSE" -> QuestionItem.TrueFalse(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                statements = body["statements"]!!.jsonArray.map { st ->
                    val sObj = st.jsonObject
                    TrueFalseStatementItem(
                        id = sObj["id"]!!.jsonPrimitive.content,
                        content = sObj["content"]!!.jsonPrimitive.content,
                        points = sObj["points"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
                    )
                },
                scoringMethod = TrueFalseScoringMethod.fromWire(body["scoringMethod"]?.jsonPrimitive?.contentOrNull),
            )
            "MATCHING" -> QuestionItem.Matching(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                left = body["left"]!!.jsonArray.map { l ->
                    val lObj = l.jsonObject
                    MatchingSideItem(
                        id = lObj["id"]!!.jsonPrimitive.content,
                        content = lObj["content"]!!.jsonPrimitive.content,
                    )
                },
                right = body["right"]!!.jsonArray.map { r ->
                    val rObj = r.jsonObject
                    MatchingSideItem(
                        id = rObj["id"]!!.jsonPrimitive.content,
                        content = rObj["content"]!!.jsonPrimitive.content,
                    )
                },
                reuseRight = body["reuseRight"]?.jsonPrimitive?.booleanOrNull == true,
            )
            "ESSAY" -> QuestionItem.Essay(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                wordLimit = body["wordLimit"]?.jsonPrimitive?.intOrNull,
            )
            "QUESTION_SET" -> QuestionItem.QuestionSet(
                id = id,
                displayNo = displayNo,
                stem = stem,
                optionOrder = optionOrder,
                points = points,
                passage = body["passage"]!!.jsonPrimitive.content,
                blankNumberStart = body["blankNumberStart"]?.jsonPrimitive?.intOrNull,
                children = children,
            )
            else -> error("Unsupported sample question type: $type")
        }
    }

    private fun parseSubmittedAnswer(item: QuestionItem, obj: JsonObject): AnswerValue? = when (item) {
        is QuestionItem.SingleChoice -> SingleChoiceAnswer(optionId = obj["optionId"]?.jsonPrimitive?.contentOrNull)
        is QuestionItem.MultipleChoice -> MultipleChoiceAnswer(
            optionIds = obj["optionIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        )
        is QuestionItem.ShortAnswer -> ShortAnswerValue(text = obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        is QuestionItem.Cloze -> {
            val map = LinkedHashMap<String, String>()
            obj["blanks"]?.jsonObject?.forEach { (k, v) ->
                map[k] = v.jsonPrimitive.contentOrNull.orEmpty()
            }
            ClozeAnswer(blanks = map)
        }
        is QuestionItem.TrueFalse -> {
            val map = LinkedHashMap<String, Boolean>()
            obj["statements"]?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.booleanOrNull?.let { map[k] = it }
            }
            TrueFalseAnswer(statements = map)
        }
        is QuestionItem.Matching -> {
            val map = LinkedHashMap<String, String>()
            obj["pairs"]?.jsonObject?.forEach { (k, v) ->
                v.jsonPrimitive.contentOrNull?.let { map[k] = it }
            }
            MatchingAnswer(pairs = map)
        }
        is QuestionItem.Essay -> EssayAnswer(
            html = obj["html"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            wordCount = obj["wordCount"]?.jsonPrimitive?.intOrNull ?: 0,
        )
        is QuestionItem.QuestionSet -> null
    }

    private fun parseReview(
        qObj: JsonObject,
        childObjs: List<JsonObject>,
        reviewObj: JsonObject,
    ): ItemReview {
        val showAnswers = reviewObj["showAnswers"]?.jsonPrimitive?.booleanOrNull != false
        val solutionHtml = reviewObj["solutionHtml"]?.jsonPrimitive?.contentOrNull
        val level = when {
            !showAnswers -> ReviewLevel.SCORE_ONLY
            !solutionHtml.isNullOrBlank() -> ReviewLevel.FULL_SOLUTION
            else -> ReviewLevel.SCORE_AND_ANSWERS
        }
        val outcome = reviewObj["outcome"]?.jsonPrimitive?.contentOrNull?.let { ItemOutcome.valueOf(it) }
        val earned = reviewObj["earned"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) }
        val max = reviewObj["max"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) }
            ?: qObj["points"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) }
            ?: BigDecimal.ONE

        val detail = LinkedHashMap<String, PartReviewResult>()
        reviewObj["gradingDetail"]?.jsonObject?.forEach { (k, v) ->
            val pObj = v.jsonObject
            detail[k] = PartReviewResult(
                outcome = ItemOutcome.valueOf(pObj["outcome"]!!.jsonPrimitive.content),
                earned = pObj["earned"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
                max = pObj["max"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
            )
        }

        val body = qObj["body"]!!.jsonObject
        val key = if (showAnswers) parseSampleKey(body) else null

        val essay = reviewObj["essay"]?.jsonObject?.let { eObj ->
            EssayReview(
                state = EssayGradingState.valueOf(eObj["state"]?.jsonPrimitive?.contentOrNull ?: "GRADED"),
                wordCount = eObj["wordCount"]?.jsonPrimitive?.intOrNull,
                comment = eObj["comment"]?.jsonPrimitive?.contentOrNull,
                criteria = eObj["criteria"]?.jsonArray?.map { c ->
                    val cObj = c.jsonObject
                    EssayCriterionReview(
                        description = cObj["description"]!!.jsonPrimitive.content,
                        earned = cObj["earned"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
                        max = cObj["max"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) },
                    )
                }.orEmpty(),
            )
        }

        val childReviews = LinkedHashMap<String, ItemReview>()
        val childrenJson = reviewObj["children"]?.jsonObject
        if (childrenJson != null) {
            for (childQ in childObjs) {
                val childId = childQ["id"]!!.jsonPrimitive.content
                val childReviewObj = childrenJson[childId]?.jsonObject ?: continue
                childReviews[childId] = parseReview(childQ, emptyList(), childReviewObj)
            }
        }

        return ItemReview(
            outcome = outcome,
            earned = earned,
            max = max,
            level = level,
            key = key,
            detail = detail,
            scoringMethod = TrueFalseScoringMethod.fromWire(body["scoringMethod"]?.jsonPrimitive?.contentOrNull),
            solutionHtml = solutionHtml,
            essay = essay,
            children = childReviews,
        )
    }

    private fun parseSampleKey(body: JsonObject): ItemReviewKey? = when (body["type"]!!.jsonPrimitive.content) {
        "SINGLE_CHOICE", "MULTIPLE_CHOICE" -> {
            val correctIds = body["options"]!!.jsonArray.mapNotNull { opt ->
                val o = opt.jsonObject
                if (o["correct"]?.jsonPrimitive?.booleanOrNull == true) {
                    o["id"]!!.jsonPrimitive.content
                } else {
                    null
                }
            }.toSet()
            correctIds.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Choice(it) }
        }
        "SHORT_ANSWER" -> {
            val answers = body["answers"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            answers.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.ShortAnswer(it) }
        }
        "CLOZE" -> {
            val map = LinkedHashMap<String, List<String>>()
            body["blanks"]!!.jsonArray.forEach { b ->
                val bObj = b.jsonObject
                val ans = bObj["answers"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
                if (ans.isNotEmpty()) {
                    map[bObj["id"]!!.jsonPrimitive.content] = ans
                }
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Cloze(it) }
        }
        "TRUE_FALSE" -> {
            val map = LinkedHashMap<String, Boolean>()
            body["statements"]!!.jsonArray.forEach { st ->
                val sObj = st.jsonObject
                val ans = sObj["answer"]?.jsonPrimitive?.booleanOrNull
                if (ans != null) {
                    map[sObj["id"]!!.jsonPrimitive.content] = ans
                }
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.TrueFalse(it) }
        }
        "MATCHING" -> {
            val map = LinkedHashMap<String, String>()
            body["pairs"]?.jsonArray?.forEach { p ->
                val pObj = p.jsonObject
                map[pObj["leftId"]!!.jsonPrimitive.content] = pObj["rightId"]!!.jsonPrimitive.content
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Matching(it) }
        }
        else -> null
    }
}

@Composable
fun QuestionTestHost(
    locale: AppLocale = AppLocale.VI,
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit,
) {
    Thi360Theme(
        preferences = AppPreferences(
            locale = locale,
            themeMode = themeMode,
        ),
    ) {
        CompositionLocalProvider(LocalFormulaEngine provides SourceBoxFormulaEngine()) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Thi360Theme.colors.bgPage,
                contentColor = Thi360Theme.colors.text,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    content()
                }
            }
        }
    }
}
