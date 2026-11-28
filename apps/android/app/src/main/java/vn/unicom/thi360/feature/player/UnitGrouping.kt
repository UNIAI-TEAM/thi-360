package vn.unicom.thi360.feature.player

import androidx.compose.runtime.Immutable
import java.math.BigDecimal
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import vn.unicom.thi360.core.network.generated.model.AttemptDisplaySegmentView
import vn.unicom.thi360.core.network.generated.model.AttemptItemView
import vn.unicom.thi360.core.network.generated.model.AttemptPartStateView
import vn.unicom.thi360.core.network.generated.model.QuestionSet
import vn.unicom.thi360.feature.question.mapper.QuestionItemMapper
import vn.unicom.thi360.feature.question.model.QuestionItem

enum class DisplayUnitKind {
    Leaf,
    QuestionSet,
}

@Immutable
data class ParsedPartChoice(
    val optionCode: String? = null,
    val subjectCodes: List<String> = emptyList(),
)

@Immutable
data class QuestionDisplayUnit(
    val key: String,
    val kind: DisplayUnitKind,
    val parentItem: AttemptItemView,
    val childItems: List<AttemptItemView>,
    val leafItems: List<AttemptItemView>,
    val questionItem: QuestionItem,
    val displayStart: Int,
    val displayEnd: Int,
    val segment: AttemptDisplaySegmentView?,
    val positionOffset: Int,
) {
    val leafIds: Set<UUID>
        get() = leafItems.mapNotNullTo(LinkedHashSet()) { it.itemId }
}

@Immutable
sealed interface UnitGroupHeader {
    @Immutable
    data class Segment(
        val key: String,
        val code: String?,
        val name: String?,
        val fromDisplayNo: Int,
        val toDisplayNo: Int,
        val commandCount: Int?,
        val maxPoints: BigDecimal?,
        val isUngrouped: Boolean = false,
    ) : UnitGroupHeader

    @Immutable
    data class Subject(
        val key: String,
        val subjectCode: String,
        val fromDisplayNo: Int,
        val toDisplayNo: Int,
        val questionCount: Int,
    ) : UnitGroupHeader

    @Immutable
    data object None : UnitGroupHeader
}

@Immutable
data class UnitGroup(
    val key: String,
    val header: UnitGroupHeader,
    val units: List<QuestionDisplayUnit>,
)

@Immutable
data class GridGroup(
    val key: String,
    val header: UnitGroupHeader,
    val cells: List<GridCellUi>,
)

object UnitGrouping {

    fun parseChoice(raw: Map<String, JsonElement>?): ParsedPartChoice {
        if (raw.isNullOrEmpty()) return ParsedPartChoice()
        val optionCode = (raw["optionCode"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        val subjectCodes = (raw["subjectCodes"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank) }
            .orEmpty()
        return ParsedPartChoice(optionCode = optionCode, subjectCodes = subjectCodes)
    }

    fun buildUnits(
        items: List<AttemptItemView>?,
        displaySegments: List<AttemptDisplaySegmentView>? = null,
    ): List<QuestionDisplayUnit> {
        val baseUnits = buildBaseUnits(items.orEmpty())
        if (baseUnits.isEmpty()) return emptyList()
        if (displaySegments.isNullOrEmpty()) return baseUnits

        val sortedSegments = sortSegments(displaySegments)
        val allLeafNos = baseUnits.flatMap { unit ->
            unit.leafItems.map { leaf -> leaf.displayNo ?: unit.displayStart }
        }
        val offset = resolvePartPositionOffset(allLeafNos, sortedSegments)

        val result = ArrayList<QuestionDisplayUnit>(baseUnits.size)
        for (unit in baseUnits) {
            if (unit.kind == DisplayUnitKind.Leaf) {
                val pos = (unit.parentItem.displayNo ?: unit.displayStart) - offset
                result.add(
                    unit.copy(
                        segment = findSegmentForPosition(pos, sortedSegments),
                        positionOffset = offset,
                    ),
                )
                continue
            }

            val buckets = LinkedHashMap<String, Pair<AttemptDisplaySegmentView?, MutableList<AttemptItemView>>>()
            for (child in unit.childItems) {
                val pos = (child.displayNo ?: unit.displayStart) - offset
                val seg = findSegmentForPosition(pos, sortedSegments)
                val segKey = segmentIdentity(seg)
                val bucket = buckets[segKey]
                if (bucket != null) {
                    bucket.second.add(child)
                } else {
                    buckets[segKey] = seg to mutableListOf(child)
                }
            }

            if (buckets.size <= 1) {
                val onlySeg = buckets.values.firstOrNull()?.first
                result.add(unit.copy(segment = onlySeg, positionOffset = offset))
                continue
            }

            var sliceIdx = 0
            for ((seg, sliceChildren) in buckets.values) {
                val mappedChildren = sliceChildren.mapNotNull { QuestionItemMapper.mapAttemptItem(it) }
                val mappedParent = QuestionItemMapper.mapAttemptItem(unit.parentItem, children = mappedChildren)
                    ?: continue
                val leafDisplays = sliceChildren.mapNotNull { it.displayNo?.takeIf { n -> n > 0 } }
                val displayStart = leafDisplays.minOrNull() ?: unit.displayStart
                val displayEnd = leafDisplays.maxOrNull() ?: displayStart
                result.add(
                    QuestionDisplayUnit(
                        key = "${unit.key}::segment::${seg?.code ?: sliceIdx}",
                        kind = DisplayUnitKind.QuestionSet,
                        parentItem = unit.parentItem,
                        childItems = sliceChildren,
                        leafItems = sliceChildren,
                        questionItem = mappedParent,
                        displayStart = displayStart,
                        displayEnd = displayEnd,
                        segment = seg,
                        positionOffset = offset,
                    ),
                )
                sliceIdx += 1
            }
        }
        return result
    }

    fun buildGroups(part: AttemptPartStateView): List<UnitGroup> {
        val segments = part.displaySegments.orEmpty()
        val units = buildUnits(part.items, segments)
        if (units.isEmpty()) return emptyList()

        if (segments.isNotEmpty()) {
            return groupUnitsByDisplaySegment(units)
        }

        val choiceSubjects = parseChoice(part.choice).subjectCodes
        val subjectGroups = groupUnitsBySubject(units, choiceSubjects)
        if (subjectGroups.isNotEmpty()) {
            return subjectGroups
        }

        return listOf(
            UnitGroup(
                key = "ungrouped",
                header = UnitGroupHeader.None,
                units = units,
            ),
        )
    }

    fun groupGridCells(
        filteredCells: List<GridCellUi>,
        allPartCells: List<GridCellUi>,
        part: AttemptPartStateView,
    ): List<GridGroup> {
        if (filteredCells.isEmpty()) return emptyList()

        val segments = part.displaySegments.orEmpty()
        if (segments.isNotEmpty()) {
            val sortedSegments = sortSegments(segments)
            val referenceNos = (if (allPartCells.isNotEmpty()) allPartCells else filteredCells).map { it.displayNo }
            val offset = resolvePartPositionOffset(referenceNos, sortedSegments)

            val buckets = LinkedHashMap<String, Pair<AttemptDisplaySegmentView, MutableList<GridCellUi>>>()
            sortedSegments.forEachIndexed { idx, seg ->
                buckets[segmentIdentity(seg, idx)] = seg to mutableListOf()
            }
            val ungrouped = mutableListOf<GridCellUi>()

            for (cell in filteredCells) {
                val pos = cell.displayNo - offset
                val matched = findSegmentForPosition(pos, sortedSegments)
                if (matched == null) {
                    ungrouped.add(cell)
                } else {
                    val idx = sortedSegments.indexOf(matched)
                    buckets[segmentIdentity(matched, idx)]?.second?.add(cell)
                }
            }

            val result = mutableListOf<GridGroup>()
            buckets.forEach { (key, entry) ->
                val (seg, cells) = entry
                if (cells.isNotEmpty()) {
                    val fromNo = (seg.fromPosition ?: cells.minOf { it.displayNo }) + offset
                    val toNo = (seg.toPosition ?: cells.maxOf { it.displayNo }) + offset
                    result.add(
                        GridGroup(
                            key = "seg::$key",
                            header = UnitGroupHeader.Segment(
                                key = key,
                                code = seg.code,
                                name = seg.name,
                                fromDisplayNo = fromNo,
                                toDisplayNo = toNo,
                                commandCount = seg.commandCount,
                                maxPoints = seg.maxPoints,
                                isUngrouped = false,
                            ),
                            cells = cells,
                        ),
                    )
                }
            }
            if (ungrouped.isNotEmpty()) {
                result.add(
                    GridGroup(
                        key = "seg::__none__",
                        header = UnitGroupHeader.Segment(
                            key = "__none__",
                            code = null,
                            name = null,
                            fromDisplayNo = ungrouped.minOf { it.displayNo },
                            toDisplayNo = ungrouped.maxOf { it.displayNo },
                            commandCount = null,
                            maxPoints = null,
                            isUngrouped = true,
                        ),
                        cells = ungrouped,
                    ),
                )
            }
            return result
        }

        val choiceSubjects = parseChoice(part.choice).subjectCodes
        val distinctCodes = allPartCells.mapNotNull { it.subjectCode?.takeIf(String::isNotBlank) }.toSet()
        val hasMultiChoiceSubjects = choiceSubjects.size >= 2 && distinctCodes.isNotEmpty()
        if (distinctCodes.size >= 2 || hasMultiChoiceSubjects) {
            val groups = mutableListOf<GridGroup>()
            var currentCode: String? = null
            var currentCells = mutableListOf<GridCellUi>()

            fun flush() {
                val code = currentCode ?: return
                if (currentCells.isEmpty()) return
                val idx = groups.size
                groups.add(
                    GridGroup(
                        key = "subj::$code::$idx",
                        header = UnitGroupHeader.Subject(
                            key = "$code::$idx",
                            subjectCode = code,
                            fromDisplayNo = currentCells.minOf { it.displayNo },
                            toDisplayNo = currentCells.maxOf { it.displayNo },
                            questionCount = currentCells.size,
                        ),
                        cells = currentCells.toList(),
                    ),
                )
            }

            for (cell in filteredCells) {
                val code = cell.subjectCode?.takeIf(String::isNotBlank)
                if (code == null) {
                    if (currentCode != null) {
                        currentCells.add(cell)
                    } else {
                        currentCode = ""
                        currentCells = mutableListOf(cell)
                    }
                    continue
                }
                if (code == currentCode) {
                    currentCells.add(cell)
                } else {
                    flush()
                    currentCode = code
                    currentCells = mutableListOf(cell)
                }
            }
            flush()
            if (groups.isNotEmpty()) {
                return groups
            }
        }

        return listOf(
            GridGroup(
                key = "ungrouped",
                header = UnitGroupHeader.None,
                cells = filteredCells,
            ),
        )
    }

    fun resolveUnitSubjectCode(unit: QuestionDisplayUnit): String {
        val direct = unit.parentItem.componentCode?.trim()
        if (!direct.isNullOrEmpty()) return direct
        for (child in unit.childItems) {
            val childCode = child.componentCode?.trim()
            if (!childCode.isNullOrEmpty()) return childCode
        }
        return ""
    }

    internal fun sortSegments(
        displaySegments: List<AttemptDisplaySegmentView>,
    ): List<AttemptDisplaySegmentView> = displaySegments.sortedWith(
        compareBy<AttemptDisplaySegmentView>(
            { it.fromPosition ?: Int.MAX_VALUE },
            { it.toPosition ?: Int.MAX_VALUE },
        ),
    )

    internal fun resolvePartPositionOffset(
        displayNos: List<Int>,
        sortedSegments: List<AttemptDisplaySegmentView>,
    ): Int {
        if (displayNos.isEmpty() || sortedSegments.isEmpty()) return 0
        val minDisplayNo = displayNos.min()
        val minSegFrom = sortedSegments.first().fromPosition ?: 1
        return if (minSegFrom == 1 && minDisplayNo > 1) {
            minDisplayNo - 1
        } else {
            0
        }
    }

    internal fun findSegmentForPosition(
        pos: Int,
        sortedSegments: List<AttemptDisplaySegmentView>,
    ): AttemptDisplaySegmentView? {
        for (seg in sortedSegments) {
            val from = seg.fromPosition ?: continue
            val to = seg.toPosition ?: continue
            if (pos in from..to) {
                return seg
            }
        }
        return null
    }

    private fun buildBaseUnits(items: List<AttemptItemView>): List<QuestionDisplayUnit> {
        val setIds = items
            .filter {
                it.questionType == AttemptItemView.QuestionType.QUESTION_SET ||
                    it.questionBody is QuestionSet
            }
            .mapNotNullTo(HashSet()) { it.itemId }

        val childrenByParent = LinkedHashMap<UUID, MutableList<AttemptItemView>>()
        for (item in items) {
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) {
                childrenByParent.getOrPut(parentId) { ArrayList() }.add(item)
            }
        }

        val units = ArrayList<QuestionDisplayUnit>(items.size)
        for (item in items) {
            val itemId = item.itemId ?: continue
            val parentId = item.parentItemId
            if (parentId != null && parentId in setIds) continue

            val isSet = itemId in setIds
            if (isSet) {
                val children = childrenByParent[itemId].orEmpty()
                val mappedChildren = children.mapNotNull { QuestionItemMapper.mapAttemptItem(it) }
                val mappedSet = QuestionItemMapper.mapAttemptItem(item, children = mappedChildren) ?: continue
                val leafDisplays = children.mapNotNull { it.displayNo?.takeIf { n -> n > 0 } }
                val start = leafDisplays.minOrNull() ?: item.displayNo ?: 0
                val end = leafDisplays.maxOrNull() ?: start
                units.add(
                    QuestionDisplayUnit(
                        key = itemId.toString(),
                        kind = DisplayUnitKind.QuestionSet,
                        parentItem = item,
                        childItems = children,
                        leafItems = children,
                        questionItem = mappedSet,
                        displayStart = start,
                        displayEnd = end,
                        segment = null,
                        positionOffset = 0,
                    ),
                )
            } else {
                val mapped = QuestionItemMapper.mapAttemptItem(item) ?: continue
                val start = item.displayNo ?: 0
                val span = maxOf(1, item.unitCount ?: 1)
                units.add(
                    QuestionDisplayUnit(
                        key = itemId.toString(),
                        kind = DisplayUnitKind.Leaf,
                        parentItem = item,
                        childItems = emptyList(),
                        leafItems = listOf(item),
                        questionItem = mapped,
                        displayStart = start,
                        displayEnd = start + span - 1,
                        segment = null,
                        positionOffset = 0,
                    ),
                )
            }
        }
        return units
    }

    private fun groupUnitsByDisplaySegment(units: List<QuestionDisplayUnit>): List<UnitGroup> {
        val groups = mutableListOf<Pair<AttemptDisplaySegmentView?, MutableList<QuestionDisplayUnit>>>()
        for (unit in units) {
            val segId = segmentIdentity(unit.segment)
            val prev = groups.lastOrNull()
            if (prev != null && segmentIdentity(prev.first) == segId) {
                prev.second.add(unit)
            } else {
                groups.add(unit.segment to mutableListOf(unit))
            }
        }
        return groups.mapIndexed { index, (seg, groupUnits) ->
            val offset = groupUnits.firstOrNull()?.positionOffset ?: 0
            val minNo = groupUnits.minOf { it.displayStart }
            val maxNo = groupUnits.maxOf { it.displayEnd }
            val segId = segmentIdentity(seg, index)
            val header = if (seg != null) {
                UnitGroupHeader.Segment(
                    key = "$segId::$index",
                    code = seg.code,
                    name = seg.name,
                    fromDisplayNo = (seg.fromPosition ?: (minNo - offset)) + offset,
                    toDisplayNo = (seg.toPosition ?: (maxNo - offset)) + offset,
                    commandCount = seg.commandCount,
                    maxPoints = seg.maxPoints,
                    isUngrouped = false,
                )
            } else {
                UnitGroupHeader.Segment(
                    key = "__none__::$index",
                    code = null,
                    name = null,
                    fromDisplayNo = minNo,
                    toDisplayNo = maxNo,
                    commandCount = null,
                    maxPoints = null,
                    isUngrouped = true,
                )
            }
            UnitGroup(
                key = "$segId::$index",
                header = header,
                units = groupUnits,
            )
        }
    }

    private fun groupUnitsBySubject(
        units: List<QuestionDisplayUnit>,
        choiceSubjectCodes: List<String>,
    ): List<UnitGroup> {
        val distinctCodes = LinkedHashSet<String>()
        for (unit in units) {
            val code = resolveUnitSubjectCode(unit)
            if (code.isNotEmpty()) distinctCodes.add(code)
        }
        val hasMultiChoiceSubjects = choiceSubjectCodes.size >= 2 && distinctCodes.isNotEmpty()
        if (distinctCodes.size < 2 && !hasMultiChoiceSubjects) {
            return emptyList()
        }

        data class MutableSubjectGroup(
            val subjectCode: String,
            var fromDisplayNo: Int,
            var toDisplayNo: Int,
            var questionCount: Int,
            val units: MutableList<QuestionDisplayUnit>,
        )

        val groups = mutableListOf<MutableSubjectGroup>()
        for (unit in units) {
            val code = resolveUnitSubjectCode(unit)
            val count = countUnitQuestions(unit)
            if (code.isEmpty()) {
                val prev = groups.lastOrNull()
                if (prev != null) {
                    prev.units.add(unit)
                    prev.fromDisplayNo = minOf(prev.fromDisplayNo, unit.displayStart)
                    prev.toDisplayNo = maxOf(prev.toDisplayNo, unit.displayEnd)
                    prev.questionCount += count
                }
                continue
            }
            val prev = groups.lastOrNull()
            if (prev != null && prev.subjectCode == code) {
                prev.units.add(unit)
                prev.fromDisplayNo = minOf(prev.fromDisplayNo, unit.displayStart)
                prev.toDisplayNo = maxOf(prev.toDisplayNo, unit.displayEnd)
                prev.questionCount += count
            } else {
                groups.add(
                    MutableSubjectGroup(
                        subjectCode = code,
                        fromDisplayNo = unit.displayStart,
                        toDisplayNo = unit.displayEnd,
                        questionCount = count,
                        units = mutableListOf(unit),
                    ),
                )
            }
        }

        return groups.mapIndexed { idx, group ->
            val key = "${group.subjectCode}::$idx"
            UnitGroup(
                key = key,
                header = UnitGroupHeader.Subject(
                    key = key,
                    subjectCode = group.subjectCode,
                    fromDisplayNo = group.fromDisplayNo,
                    toDisplayNo = group.toDisplayNo,
                    questionCount = group.questionCount,
                ),
                units = group.units,
            )
        }
    }

    private fun countUnitQuestions(unit: QuestionDisplayUnit): Int =
        if (unit.kind == DisplayUnitKind.QuestionSet) {
            unit.childItems.sumOf { maxOf(1, it.unitCount ?: 1) }
        } else {
            maxOf(1, unit.parentItem.unitCount ?: 1)
        }

    private fun segmentIdentity(segment: AttemptDisplaySegmentView?, fallbackIdx: Int = 0): String {
        if (segment == null) return "__none__"
        return segment.code?.takeIf { it.isNotBlank() }
            ?: "${segment.fromPosition ?: ""}-${segment.toPosition ?: ""}-$fallbackIdx"
    }
}
