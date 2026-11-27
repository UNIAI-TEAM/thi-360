package vn.unicom.thi360.feature.question

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
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

internal data class AndroidQuestionSample(
    val key: String,
    val item: QuestionItem,
    val submitted: AnswerValue?,
    val submittedChildren: Map<String, AnswerValue>,
    val review: ItemReview,
)

internal object QuestionAndroidTestSupport {
    private val json = Json { ignoreUnknownKeys = true }

    fun findAllEditTexts(root: View): List<EditText> {
        val result = ArrayList<EditText>()
        fun walk(v: View) {
            if (v is EditText) result.add(v)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
        }
        walk(root)
        return result
    }

    fun outputDir(subpath: String): File {
        val base = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
            ?: InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        return File(base, "and-q-outputs/$subpath").apply { mkdirs() }
    }

    fun mirrorToHostTmp(sourceFile: File, relativePath: String) {
        val targetPath = "/data/local/tmp/and-q-outputs/$relativePath"
        val parentPath = targetPath.substringBeforeLast('/')
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        ui.executeShellCommand("mkdir -p $parentPath").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
        }
        val err = ui.executeShellCommand("cp ${sourceFile.absolutePath} $targetPath").use { pfd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
        }
        require(err.isBlank()) { "Failed to mirror ${sourceFile.absolutePath} -> $targetPath: $err" }
    }

    fun captureViewBitmap(view: View, backgroundColor: Int = Color.WHITE): Bitmap {
        val w = view.width.coerceAtLeast(1)
        val h = view.height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(backgroundColor)
        view.draw(canvas)
        return bmp
    }

    fun savePng(bitmap: Bitmap, file: File) {
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    fun loadSamples(): List<AndroidQuestionSample> {
        val root = json.parseToJsonElement(QuestionSamplesRawJson.json()).jsonObject
        return root["samples"]!!.jsonArray.map { el ->
            val obj = el.jsonObject
            val key = obj["key"]!!.jsonPrimitive.content
            val qObj = obj["question"]!!.jsonObject
            val childObjs = obj["children"]?.jsonArray?.map { it.jsonObject }.orEmpty()
            val mappedChildren = childObjs.map { parseQuestion(it, emptyList()) }
            val item = parseQuestion(qObj, mappedChildren)
            val submittedObj = obj["submitted"]?.jsonObject
            val submittedChildren = if (item is QuestionItem.QuestionSet && submittedObj != null) {
                val map = LinkedHashMap<String, AnswerValue>()
                val childrenJson = submittedObj["children"]?.jsonObject
                if (childrenJson != null) {
                    for (child in item.children) {
                        val childSub = childrenJson[child.id]?.jsonObject ?: continue
                        parseSubmitted(child, childSub)?.let { map[child.id] = it }
                    }
                }
                map
            } else {
                emptyMap()
            }
            val submitted = if (item !is QuestionItem.QuestionSet && submittedObj != null) {
                parseSubmitted(item, submittedObj)
            } else {
                null
            }
            val review = parseReview(qObj, childObjs, obj["review"]!!.jsonObject)
            AndroidQuestionSample(
                key = key,
                item = item,
                submitted = submitted,
                submittedChildren = submittedChildren,
                review = review,
            )
        }
    }

    private fun parseQuestion(qObj: JsonObject, children: List<QuestionItem>): QuestionItem {
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
                    ChoiceOptionItem(o["id"]!!.jsonPrimitive.content, o["content"]!!.jsonPrimitive.content)
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
                    ChoiceOptionItem(o["id"]!!.jsonPrimitive.content, o["content"]!!.jsonPrimitive.content)
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
                    MatchingSideItem(lObj["id"]!!.jsonPrimitive.content, lObj["content"]!!.jsonPrimitive.content)
                },
                right = body["right"]!!.jsonArray.map { r ->
                    val rObj = r.jsonObject
                    MatchingSideItem(rObj["id"]!!.jsonPrimitive.content, rObj["content"]!!.jsonPrimitive.content)
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
            else -> error("Unsupported type: $type")
        }
    }

    private fun parseSubmitted(item: QuestionItem, obj: JsonObject): AnswerValue? = when (item) {
        is QuestionItem.SingleChoice -> SingleChoiceAnswer(obj["optionId"]?.jsonPrimitive?.contentOrNull)
        is QuestionItem.MultipleChoice -> MultipleChoiceAnswer(
            obj["optionIds"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        )
        is QuestionItem.ShortAnswer -> ShortAnswerValue(obj["text"]?.jsonPrimitive?.contentOrNull.orEmpty())
        is QuestionItem.Cloze -> {
            val map = LinkedHashMap<String, String>()
            obj["blanks"]?.jsonObject?.forEach { (k, v) -> map[k] = v.jsonPrimitive.contentOrNull.orEmpty() }
            ClozeAnswer(map)
        }
        is QuestionItem.TrueFalse -> {
            val map = LinkedHashMap<String, Boolean>()
            obj["statements"]?.jsonObject?.forEach { (k, v) -> v.jsonPrimitive.booleanOrNull?.let { map[k] = it } }
            TrueFalseAnswer(map)
        }
        is QuestionItem.Matching -> {
            val map = LinkedHashMap<String, String>()
            obj["pairs"]?.jsonObject?.forEach { (k, v) -> v.jsonPrimitive.contentOrNull?.let { map[k] = it } }
            MatchingAnswer(map)
        }
        is QuestionItem.Essay -> EssayAnswer(
            html = obj["html"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            wordCount = obj["wordCount"]?.jsonPrimitive?.intOrNull ?: 0,
        )
        is QuestionItem.QuestionSet -> null
    }

    private fun parseReview(qObj: JsonObject, childObjs: List<JsonObject>, reviewObj: JsonObject): ItemReview {
        val showAnswers = reviewObj["showAnswers"]?.jsonPrimitive?.booleanOrNull != false
        val solutionHtml = reviewObj["solutionHtml"]?.jsonPrimitive?.contentOrNull
        val level = when {
            !showAnswers -> ReviewLevel.SCORE_ONLY
            !solutionHtml.isNullOrBlank() -> ReviewLevel.FULL_SOLUTION
            else -> ReviewLevel.SCORE_AND_ANSWERS
        }
        val outcome = reviewObj["outcome"]?.jsonPrimitive?.contentOrNull?.let { ItemOutcome.valueOf(it) }
        val earned = reviewObj["earned"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) }
        val max = reviewObj["max"]?.jsonPrimitive?.doubleOrNull?.let { BigDecimal.valueOf(it) } ?: BigDecimal.ONE
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
                val cid = childQ["id"]!!.jsonPrimitive.content
                val crObj = childrenJson[cid]?.jsonObject ?: continue
                childReviews[cid] = parseReview(childQ, emptyList(), crObj)
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
            val ids = body["options"]!!.jsonArray.mapNotNull {
                val o = it.jsonObject
                if (o["correct"]?.jsonPrimitive?.booleanOrNull == true) o["id"]!!.jsonPrimitive.content else null
            }.toSet()
            ids.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Choice(it) }
        }
        "SHORT_ANSWER" -> body["answers"]?.jsonArray?.map { it.jsonPrimitive.content }
            ?.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.ShortAnswer(it) }
        "CLOZE" -> {
            val map = LinkedHashMap<String, List<String>>()
            body["blanks"]!!.jsonArray.forEach {
                val b = it.jsonObject
                val ans = b["answers"]?.jsonArray?.map { a -> a.jsonPrimitive.content }.orEmpty()
                if (ans.isNotEmpty()) map[b["id"]!!.jsonPrimitive.content] = ans
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Cloze(it) }
        }
        "TRUE_FALSE" -> {
            val map = LinkedHashMap<String, Boolean>()
            body["statements"]!!.jsonArray.forEach {
                val s = it.jsonObject
                s["answer"]?.jsonPrimitive?.booleanOrNull?.let { ans -> map[s["id"]!!.jsonPrimitive.content] = ans }
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.TrueFalse(it) }
        }
        "MATCHING" -> {
            val map = LinkedHashMap<String, String>()
            body["pairs"]?.jsonArray?.forEach {
                val p = it.jsonObject
                map[p["leftId"]!!.jsonPrimitive.content] = p["rightId"]!!.jsonPrimitive.content
            }
            map.takeIf { it.isNotEmpty() }?.let { ItemReviewKey.Matching(it) }
        }
        else -> null
    }
}
