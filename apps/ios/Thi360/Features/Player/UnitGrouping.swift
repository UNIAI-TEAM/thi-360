import Foundation

enum DisplayUnitKind: Equatable, Hashable, Sendable {
    case leaf
    case questionSet
}

struct ParsedPartChoice: Equatable, Hashable, Sendable {
    let optionCode: String?
    let subjectCodes: [String]

    init(optionCode: String? = nil, subjectCodes: [String] = []) {
        self.optionCode = optionCode
        self.subjectCodes = subjectCodes
    }
}

struct QuestionDisplayUnit: Identifiable, Equatable, Sendable {
    let key: String
    let kind: DisplayUnitKind
    let parentItem: Components.Schemas.AttemptItemView
    let childItems: [Components.Schemas.AttemptItemView]
    let leafItems: [Components.Schemas.AttemptItemView]
    let questionItem: QuestionItem
    let displayStart: Int
    let displayEnd: Int
    let segment: Components.Schemas.AttemptDisplaySegmentView?
    let positionOffset: Int

    var id: String { key }

    var leafIds: [ItemID] {
        leafItems.compactMap { item in
            guard let id = item.itemId, !id.isEmpty else { return nil }
            return id
        }
    }

    var leafIdSet: Set<ItemID> {
        Set(leafIds)
    }
}

enum UnitGroupHeader: Equatable, Sendable {
    case segment(
        key: String,
        code: String?,
        name: String?,
        fromDisplayNo: Int,
        toDisplayNo: Int,
        commandCount: Int?,
        maxPoints: Double?,
        isUngrouped: Bool
    )
    case subject(
        key: String,
        subjectCode: String,
        fromDisplayNo: Int,
        toDisplayNo: Int,
        questionCount: Int
    )
    case none
}

struct UnitGroup: Identifiable, Equatable, Sendable {
    let key: String
    let header: UnitGroupHeader
    let units: [QuestionDisplayUnit]

    var id: String { key }
}

struct GridGroup: Identifiable, Equatable, Sendable {
    let key: String
    let header: UnitGroupHeader
    let cells: [GridCellUI]

    var id: String { key }
}

enum UnitGrouping {
    static func parseChoice(_ choice: Components.Schemas.AttemptPartStateView.choicePayload?) -> ParsedPartChoice {
        guard let raw = choice?.additionalProperties.value, !raw.isEmpty else {
            return ParsedPartChoice()
        }
        let optionCode = (raw["optionCode"] as? String)
            .flatMap { trimmedNonEmpty($0) }
        let subjectCodes: [String]
        if let rawSubjects = raw["subjectCodes"] as? [Any] {
            subjectCodes = rawSubjects.compactMap { ($0 as? String).flatMap(trimmedNonEmpty) }
        } else {
            subjectCodes = []
        }
        return ParsedPartChoice(optionCode: optionCode, subjectCodes: subjectCodes)
    }

    static func leafItems(in part: Components.Schemas.AttemptPartStateView) -> [Components.Schemas.AttemptItemView] {
        (part.items ?? []).filter { item in
            item.questionType != .QUESTION_SET && (item.itemId?.isEmpty == false)
        }
    }

    static func buildUnits(
        items: [Components.Schemas.AttemptItemView]?,
        bodies: [ItemID: AttemptQuestionBodyValue] = [:],
        displaySegments: [Components.Schemas.AttemptDisplaySegmentView]? = nil
    ) -> [QuestionDisplayUnit] {
        let baseUnits = buildBaseUnits(items ?? [], bodies: bodies)
        guard !baseUnits.isEmpty else { return [] }
        guard let displaySegments, !displaySegments.isEmpty else { return baseUnits }

        let sortedSegments = sortSegments(displaySegments)
        let allLeafNos = baseUnits.flatMap { unit in
            unit.leafItems.map { leaf in Int(leaf.displayNo ?? Int32(unit.displayStart)) }
        }
        let offset = resolvePartPositionOffset(displayNos: allLeafNos, sortedSegments: sortedSegments)

        var result: [QuestionDisplayUnit] = []
        result.reserveCapacity(baseUnits.count)

        for unit in baseUnits {
            if unit.kind == .leaf {
                let pos = Int(unit.parentItem.displayNo ?? Int32(unit.displayStart)) - offset
                result.append(
                    QuestionDisplayUnit(
                        key: unit.key,
                        kind: unit.kind,
                        parentItem: unit.parentItem,
                        childItems: unit.childItems,
                        leafItems: unit.leafItems,
                        questionItem: unit.questionItem,
                        displayStart: unit.displayStart,
                        displayEnd: unit.displayEnd,
                        segment: findSegment(forPosition: pos, in: sortedSegments),
                        positionOffset: offset
                    )
                )
                continue
            }

            var bucketOrder: [String] = []
            var bucketSegments: [String: Components.Schemas.AttemptDisplaySegmentView?] = [:]
            var bucketChildren: [String: [Components.Schemas.AttemptItemView]] = [:]

            for child in unit.childItems {
                let pos = Int(child.displayNo ?? Int32(unit.displayStart)) - offset
                let seg = findSegment(forPosition: pos, in: sortedSegments)
                let segKey = segmentIdentity(seg)
                if bucketChildren[segKey] == nil {
                    bucketOrder.append(segKey)
                    bucketSegments[segKey] = seg
                    bucketChildren[segKey] = []
                }
                bucketChildren[segKey]?.append(child)
            }

            if bucketOrder.count <= 1 {
                let onlySeg = bucketOrder.first.flatMap { bucketSegments[$0] ?? nil }
                result.append(
                    QuestionDisplayUnit(
                        key: unit.key,
                        kind: unit.kind,
                        parentItem: unit.parentItem,
                        childItems: unit.childItems,
                        leafItems: unit.leafItems,
                        questionItem: unit.questionItem,
                        displayStart: unit.displayStart,
                        displayEnd: unit.displayEnd,
                        segment: onlySeg,
                        positionOffset: offset
                    )
                )
                continue
            }

            for (sliceIdx, segKey) in bucketOrder.enumerated() {
                let seg = bucketSegments[segKey] ?? nil
                let sliceChildren = bucketChildren[segKey] ?? []
                let mappedChildren = sliceChildren.map { child in
                    let childId = child.itemId ?? ""
                    return QuestionItemMapper.map(item: child, body: bodies[childId])
                }
                let parentId = unit.parentItem.itemId ?? ""
                let mappedParent = QuestionItemMapper.map(
                    item: unit.parentItem,
                    body: bodies[parentId],
                    children: mappedChildren
                )
                let leafDisplays = sliceChildren.compactMap { item -> Int? in
                    guard let no = item.displayNo, no > 0 else { return nil }
                    return Int(no)
                }
                let displayStart = leafDisplays.min() ?? unit.displayStart
                let displayEnd = leafDisplays.max() ?? displayStart
                let segSuffix = seg?.code ?? "\(sliceIdx)"
                result.append(
                    QuestionDisplayUnit(
                        key: "\(unit.key)::segment::\(segSuffix)",
                        kind: .questionSet,
                        parentItem: unit.parentItem,
                        childItems: sliceChildren,
                        leafItems: sliceChildren,
                        questionItem: mappedParent,
                        displayStart: displayStart,
                        displayEnd: displayEnd,
                        segment: seg,
                        positionOffset: offset
                    )
                )
            }
        }
        return result
    }

    static func make(
        part: Components.Schemas.AttemptPartStateView,
        bodies: [ItemID: AttemptQuestionBodyValue] = [:],
        subjects _: [String: Components.Schemas.ChoiceSubjectView] = [:]
    ) -> [UnitGroup] {
        buildGroups(part: part, bodies: bodies)
    }

    static func buildGroups(
        part: Components.Schemas.AttemptPartStateView,
        bodies: [ItemID: AttemptQuestionBodyValue] = [:]
    ) -> [UnitGroup] {
        let segments = part.displaySegments ?? []
        let units = buildUnits(items: part.items, bodies: bodies, displaySegments: segments)
        guard !units.isEmpty else { return [] }

        if !segments.isEmpty {
            return groupUnitsByDisplaySegment(units)
        }

        let choiceSubjects = parseChoice(part.choice).subjectCodes
        let subjectGroups = groupUnitsBySubject(units, choiceSubjectCodes: choiceSubjects)
        if !subjectGroups.isEmpty {
            return subjectGroups
        }

        return [
            UnitGroup(key: "ungrouped", header: .none, units: units)
        ]
    }

    static func groupGridCells(
        filteredCells: [GridCellUI],
        allPartCells: [GridCellUI],
        part: Components.Schemas.AttemptPartStateView
    ) -> [GridGroup] {
        guard !filteredCells.isEmpty else { return [] }

        let segments = part.displaySegments ?? []
        if !segments.isEmpty {
            let sortedSegments = sortSegments(segments)
            let referenceNos = (allPartCells.isEmpty ? filteredCells : allPartCells).map(\.displayNo)
            let offset = resolvePartPositionOffset(displayNos: referenceNos, sortedSegments: sortedSegments)

            var bucketKeys: [String] = []
            var bucketSegs: [String: Components.Schemas.AttemptDisplaySegmentView] = [:]
            var bucketCells: [String: [GridCellUI]] = [:]

            for (idx, seg) in sortedSegments.enumerated() {
                let key = segmentIdentity(seg, fallbackIdx: idx)
                bucketKeys.append(key)
                bucketSegs[key] = seg
                bucketCells[key] = []
            }
            var ungrouped: [GridCellUI] = []

            for cell in filteredCells {
                let pos = cell.displayNo - offset
                if let matchedIdx = findSegmentIndex(forPosition: pos, in: sortedSegments) {
                    let key = segmentIdentity(sortedSegments[matchedIdx], fallbackIdx: matchedIdx)
                    bucketCells[key, default: []].append(cell)
                } else {
                    ungrouped.append(cell)
                }
            }

            var result: [GridGroup] = []
            for key in bucketKeys {
                guard let seg = bucketSegs[key], let cells = bucketCells[key], !cells.isEmpty else {
                    continue
                }
                let minCell = cells.map(\.displayNo).min() ?? 0
                let maxCell = cells.map(\.displayNo).max() ?? minCell
                let fromNo = seg.fromPosition.map { Int($0) + offset } ?? minCell
                let toNo = seg.toPosition.map { Int($0) + offset } ?? maxCell
                result.append(
                    GridGroup(
                        key: "seg::\(key)",
                        header: .segment(
                            key: key,
                            code: seg.code,
                            name: seg.name,
                            fromDisplayNo: fromNo,
                            toDisplayNo: toNo,
                            commandCount: seg.commandCount.map(Int.init),
                            maxPoints: seg.maxPoints,
                            isUngrouped: false
                        ),
                        cells: cells
                    )
                )
            }
            if !ungrouped.isEmpty {
                result.append(
                    GridGroup(
                        key: "seg::__none__",
                        header: .segment(
                            key: "__none__",
                            code: nil,
                            name: nil,
                            fromDisplayNo: ungrouped.map(\.displayNo).min() ?? 0,
                            toDisplayNo: ungrouped.map(\.displayNo).max() ?? 0,
                            commandCount: nil,
                            maxPoints: nil,
                            isUngrouped: true
                        ),
                        cells: ungrouped
                    )
                )
            }
            return result
        }

        let choiceSubjects = parseChoice(part.choice).subjectCodes
        let distinctCodes = Set(allPartCells.compactMap { trimmedNonEmpty($0.subjectCode) })
        let hasMultiChoiceSubjects = choiceSubjects.count >= 2 && !distinctCodes.isEmpty
        if distinctCodes.count >= 2 || hasMultiChoiceSubjects {
            var groups: [GridGroup] = []
            var currentCode: String?
            var currentCells: [GridCellUI] = []

            func flush() {
                guard let code = currentCode, !currentCells.isEmpty else { return }
                let idx = groups.count
                groups.append(
                    GridGroup(
                        key: "subj::\(code)::\(idx)",
                        header: .subject(
                            key: "\(code)::\(idx)",
                            subjectCode: code,
                            fromDisplayNo: currentCells.map(\.displayNo).min() ?? 0,
                            toDisplayNo: currentCells.map(\.displayNo).max() ?? 0,
                            questionCount: currentCells.count
                        ),
                        cells: currentCells
                    )
                )
            }

            for cell in filteredCells {
                let code = trimmedNonEmpty(cell.subjectCode)
                if code == nil {
                    if currentCode != nil {
                        currentCells.append(cell)
                    } else {
                        currentCode = ""
                        currentCells = [cell]
                    }
                    continue
                }
                if code == currentCode {
                    currentCells.append(cell)
                } else {
                    flush()
                    currentCode = code
                    currentCells = [cell]
                }
            }
            flush()
            if !groups.isEmpty {
                return groups
            }
        }

        return [
            GridGroup(key: "ungrouped", header: .none, cells: filteredCells)
        ]
    }

    static func resolveUnitSubjectCode(_ unit: QuestionDisplayUnit) -> String {
        if let direct = trimmedNonEmpty(unit.parentItem.componentCode) {
            return direct
        }
        for child in unit.childItems {
            if let childCode = trimmedNonEmpty(child.componentCode) {
                return childCode
            }
        }
        return ""
    }

    static func sortSegments(
        _ displaySegments: [Components.Schemas.AttemptDisplaySegmentView]
    ) -> [Components.Schemas.AttemptDisplaySegmentView] {
        displaySegments.sorted { lhs, rhs in
            let lFrom = Int(lhs.fromPosition ?? Int32.max)
            let rFrom = Int(rhs.fromPosition ?? Int32.max)
            if lFrom != rFrom { return lFrom < rFrom }
            let lTo = Int(lhs.toPosition ?? Int32.max)
            let rTo = Int(rhs.toPosition ?? Int32.max)
            return lTo < rTo
        }
    }

    static func resolvePartPositionOffset(
        displayNos: [Int],
        sortedSegments: [Components.Schemas.AttemptDisplaySegmentView]
    ) -> Int {
        guard let minDisplayNo = displayNos.min(), let firstSeg = sortedSegments.first else {
            return 0
        }
        let minSegFrom = Int(firstSeg.fromPosition ?? 1)
        if minSegFrom == 1 && minDisplayNo > 1 {
            return minDisplayNo - 1
        }
        return 0
    }

    static func findSegment(
        forPosition pos: Int,
        in sortedSegments: [Components.Schemas.AttemptDisplaySegmentView]
    ) -> Components.Schemas.AttemptDisplaySegmentView? {
        guard let idx = findSegmentIndex(forPosition: pos, in: sortedSegments) else {
            return nil
        }
        return sortedSegments[idx]
    }

    private static func findSegmentIndex(
        forPosition pos: Int,
        in sortedSegments: [Components.Schemas.AttemptDisplaySegmentView]
    ) -> Int? {
        for (idx, seg) in sortedSegments.enumerated() {
            guard let from = seg.fromPosition, let to = seg.toPosition else { continue }
            if pos >= Int(from) && pos <= Int(to) {
                return idx
            }
        }
        return nil
    }

    private static func buildBaseUnits(
        _ items: [Components.Schemas.AttemptItemView],
        bodies: [ItemID: AttemptQuestionBodyValue]
    ) -> [QuestionDisplayUnit] {
        var setIds = Set<ItemID>()
        for item in items {
            guard let itemId = item.itemId, !itemId.isEmpty else { continue }
            let isBodySet: Bool
            if case .questionSet = bodies[itemId] {
                isBodySet = true
            } else {
                isBodySet = false
            }
            if item.questionType == .QUESTION_SET || isBodySet {
                setIds.insert(itemId)
            }
        }

        var childrenByParent: [ItemID: [Components.Schemas.AttemptItemView]] = [:]
        for item in items {
            if let parentId = item.parentItemId, setIds.contains(parentId) {
                childrenByParent[parentId, default: []].append(item)
            }
        }

        var units: [QuestionDisplayUnit] = []
        units.reserveCapacity(items.count)

        for item in items {
            guard let itemId = item.itemId, !itemId.isEmpty else { continue }
            if let parentId = item.parentItemId, setIds.contains(parentId) {
                continue
            }

            if setIds.contains(itemId) {
                let children = childrenByParent[itemId] ?? []
                let mappedChildren = children.map { child in
                    let childId = child.itemId ?? ""
                    return QuestionItemMapper.map(item: child, body: bodies[childId])
                }
                let mappedSet = QuestionItemMapper.map(item: item, body: bodies[itemId], children: mappedChildren)
                let leafDisplays = children.compactMap { child -> Int? in
                    guard let no = child.displayNo, no > 0 else { return nil }
                    return Int(no)
                }
                let start = leafDisplays.min() ?? Int(item.displayNo ?? 0)
                let end = leafDisplays.max() ?? start
                units.append(
                    QuestionDisplayUnit(
                        key: itemId,
                        kind: .questionSet,
                        parentItem: item,
                        childItems: children,
                        leafItems: children,
                        questionItem: mappedSet,
                        displayStart: start,
                        displayEnd: end,
                        segment: nil,
                        positionOffset: 0
                    )
                )
            } else {
                let mapped = QuestionItemMapper.map(item: item, body: bodies[itemId])
                let start = Int(item.displayNo ?? 0)
                let span = max(1, Int(item.unitCount ?? 1))
                units.append(
                    QuestionDisplayUnit(
                        key: itemId,
                        kind: .leaf,
                        parentItem: item,
                        childItems: [],
                        leafItems: [item],
                        questionItem: mapped,
                        displayStart: start,
                        displayEnd: start + span - 1,
                        segment: nil,
                        positionOffset: 0
                    )
                )
            }
        }

        return units
    }

    private static func groupUnitsByDisplaySegment(_ units: [QuestionDisplayUnit]) -> [UnitGroup] {
        var groups: [(segment: Components.Schemas.AttemptDisplaySegmentView?, units: [QuestionDisplayUnit])] = []
        for unit in units {
            let segId = segmentIdentity(unit.segment)
            if let lastIdx = groups.indices.last, segmentIdentity(groups[lastIdx].segment) == segId {
                groups[lastIdx].units.append(unit)
            } else {
                groups.append((unit.segment, [unit]))
            }
        }

        return groups.enumerated().map { index, entry in
            let seg = entry.segment
            let groupUnits = entry.units
            let offset = groupUnits.first?.positionOffset ?? 0
            let minNo = groupUnits.map(\.displayStart).min() ?? 0
            let maxNo = groupUnits.map(\.displayEnd).max() ?? minNo
            let segId = segmentIdentity(seg, fallbackIdx: index)

            let header: UnitGroupHeader
            if let seg {
                header = .segment(
                    key: "\(segId)::\(index)",
                    code: seg.code,
                    name: seg.name,
                    fromDisplayNo: seg.fromPosition.map { Int($0) + offset } ?? minNo,
                    toDisplayNo: seg.toPosition.map { Int($0) + offset } ?? maxNo,
                    commandCount: seg.commandCount.map(Int.init),
                    maxPoints: seg.maxPoints,
                    isUngrouped: false
                )
            } else {
                header = .segment(
                    key: "__none__::\(index)",
                    code: nil,
                    name: nil,
                    fromDisplayNo: minNo,
                    toDisplayNo: maxNo,
                    commandCount: nil,
                    maxPoints: nil,
                    isUngrouped: true
                )
            }
            return UnitGroup(
                key: "\(segId)::\(index)",
                header: header,
                units: groupUnits
            )
        }
    }

    private static func groupUnitsBySubject(
        _ units: [QuestionDisplayUnit],
        choiceSubjectCodes: [String]
    ) -> [UnitGroup] {
        var distinctCodes = Set<String>()
        for unit in units {
            let code = resolveUnitSubjectCode(unit)
            if !code.isEmpty {
                distinctCodes.insert(code)
            }
        }
        let hasMultiChoiceSubjects = choiceSubjectCodes.count >= 2 && !distinctCodes.isEmpty
        guard distinctCodes.count >= 2 || hasMultiChoiceSubjects else {
            return []
        }

        struct MutableSubjectGroup {
            let subjectCode: String
            var fromDisplayNo: Int
            var toDisplayNo: Int
            var questionCount: Int
            var units: [QuestionDisplayUnit]
        }

        var groups: [MutableSubjectGroup] = []
        for unit in units {
            let code = resolveUnitSubjectCode(unit)
            let count = countUnitQuestions(unit)
            if code.isEmpty {
                if !groups.isEmpty {
                    let last = groups.count - 1
                    groups[last].units.append(unit)
                    groups[last].fromDisplayNo = min(groups[last].fromDisplayNo, unit.displayStart)
                    groups[last].toDisplayNo = max(groups[last].toDisplayNo, unit.displayEnd)
                    groups[last].questionCount += count
                }
                continue
            }

            if let last = groups.indices.last, groups[last].subjectCode == code {
                groups[last].units.append(unit)
                groups[last].fromDisplayNo = min(groups[last].fromDisplayNo, unit.displayStart)
                groups[last].toDisplayNo = max(groups[last].toDisplayNo, unit.displayEnd)
                groups[last].questionCount += count
            } else {
                groups.append(
                    MutableSubjectGroup(
                        subjectCode: code,
                        fromDisplayNo: unit.displayStart,
                        toDisplayNo: unit.displayEnd,
                        questionCount: count,
                        units: [unit]
                    )
                )
            }
        }

        return groups.enumerated().map { idx, group in
            let key = "\(group.subjectCode)::\(idx)"
            return UnitGroup(
                key: key,
                header: .subject(
                    key: key,
                    subjectCode: group.subjectCode,
                    fromDisplayNo: group.fromDisplayNo,
                    toDisplayNo: group.toDisplayNo,
                    questionCount: group.questionCount
                ),
                units: group.units
            )
        }
    }

    private static func countUnitQuestions(_ unit: QuestionDisplayUnit) -> Int {
        if unit.kind == .questionSet {
            return unit.childItems.reduce(0) { $0 + max(1, Int($1.unitCount ?? 1)) }
        }
        return max(1, Int(unit.parentItem.unitCount ?? 1))
    }

    private static func segmentIdentity(
        _ segment: Components.Schemas.AttemptDisplaySegmentView?,
        fallbackIdx: Int = 0
    ) -> String {
        guard let segment else { return "__none__" }
        if let code = trimmedNonEmpty(segment.code) {
            return code
        }
        let fromStr = segment.fromPosition.map(String.init) ?? ""
        let toStr = segment.toPosition.map(String.init) ?? ""
        return "\(fromStr)-\(toStr)-\(fallbackIdx)"
    }

    private static func trimmedNonEmpty(_ value: String?) -> String? {
        guard let trimmed = value?.trimmingCharacters(in: .whitespacesAndNewlines), !trimmed.isEmpty else {
            return nil
        }
        return trimmed
    }
}
