import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

struct LoadedQuestionSample {
    let key: String
    let item: QuestionItem
    let submittedAnswer: AnswerValue?
    let submittedAnswers: [String: AnswerValue]
    let review: ItemReview
}

enum QuestionTestFixtures {
    static var repoRootURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent() // Question
            .deletingLastPathComponent() // Thi360Tests
            .deletingLastPathComponent() // ios
            .deletingLastPathComponent() // apps
            .deletingLastPathComponent() // repo root
    }

    static var questionSamplesURL: URL {
        repoRootURL.appendingPathComponent("apps/web-student/src/app/dev/question-kit/question-samples.json")
    }

    static var essaysFixtureURL: URL {
        repoRootURL.appendingPathComponent("contracts/mobile/rich-text/essays.json")
    }

    static var wordCountFixtureURL: URL {
        repoRootURL.appendingPathComponent("contracts/mobile/rich-text/word-count.json")
    }

    static var attempt50FixtureURL: URL {
        repoRootURL.appendingPathComponent("contracts/mobile/rich-text/attempt-50.json")
    }

    static var apiFixturesDirURL: URL {
        repoRootURL.appendingPathComponent("contracts/mobile/api")
    }

    static func loadQuestionSamples() throws -> [LoadedQuestionSample] {
        let data = try Data(contentsOf: questionSamplesURL)
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any]
        let rawSamples = root?["samples"] as? [[String: Any]] ?? []
        return try rawSamples.map(parseSample)
    }

    static func sample(key: String) throws -> LoadedQuestionSample {
        let samples = try loadQuestionSamples()
        guard let found = samples.first(where: { $0.key == key }) else {
            throw NSError(domain: "QuestionTestFixtures", code: 1, userInfo: [NSLocalizedDescriptionKey: "Missing sample \(key)"])
        }
        return found
    }

    private static func parseSample(_ dict: [String: Any]) throws -> LoadedQuestionSample {
        let key = dict["key"] as? String ?? ""
        let questionDict = dict["question"] as? [String: Any] ?? [:]
        let childDicts = dict["children"] as? [[String: Any]] ?? []
        let children = try childDicts.map { try parseQuestion($0, children: []) }
        let item = try parseQuestion(questionDict, children: children)

        let submittedDict = dict["submitted"] as? [String: Any] ?? [:]
        var submittedAnswers: [String: AnswerValue] = [:]
        let submittedAnswer: AnswerValue?

        if case let .questionSet(_, _, _, _, _, _, setChildren) = item,
           let childSubs = submittedDict["children"] as? [String: [String: Any]]
        {
            for child in setChildren {
                if let sub = childSubs[child.id], let mapped = parseSubmitted(sub, for: child) {
                    submittedAnswers[child.id] = mapped
                }
            }
            submittedAnswer = nil
        } else {
            submittedAnswer = parseSubmitted(submittedDict, for: item)
            if let submittedAnswer {
                submittedAnswers[item.id] = submittedAnswer
            }
        }

        let reviewDict = dict["review"] as? [String: Any] ?? [:]
        let review = parseReview(reviewDict)

        return LoadedQuestionSample(
            key: key,
            item: item,
            submittedAnswer: submittedAnswer,
            submittedAnswers: submittedAnswers,
            review: review
        )
    }

    private static func parseQuestion(_ dict: [String: Any], children: [QuestionItem]) throws -> QuestionItem {
        let id = dict["id"] as? String ?? ""
        let displayNo = dict["displayNo"] as? Int ?? 1
        let points = (dict["points"] as? NSNumber)?.doubleValue ?? 1.0
        let stem = dict["stem"] as? String
        let optionOrder = dict["optionOrder"] as? [String]
        let body = dict["body"] as? [String: Any] ?? [:]
        let type = QuestionType.parse(body["type"] as? String) ?? .singleChoice

        switch type {
        case .singleChoice:
            let opts = (body["options"] as? [[String: Any]] ?? []).map { opt in
                ChoiceOption(
                    id: opt["id"] as? String ?? "",
                    content: opt["content"] as? String ?? "",
                    correct: opt["correct"] as? Bool
                )
            }
            return .singleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: opts
            )
        case .multipleChoice:
            let opts = (body["options"] as? [[String: Any]] ?? []).map { opt in
                ChoiceOption(
                    id: opt["id"] as? String ?? "",
                    content: opt["content"] as? String ?? "",
                    correct: opt["correct"] as? Bool
                )
            }
            let rule = (body["scoringRule"] as? String).flatMap(MultipleChoiceRule.init(rawValue:))
            return .multipleChoice(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                options: opts,
                scoringRule: rule
            )
        case .shortAnswer:
            let fmt = (body["answerFormat"] as? String).flatMap(AnswerFormat.init(rawValue:))
            return .shortAnswer(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                answerFormat: fmt,
                inputHint: body["inputHint"] as? String,
                answers: body["answers"] as? [String] ?? []
            )
        case .cloze:
            let blanks = (body["blanks"] as? [[String: Any]] ?? []).map { b in
                ClozeBlank(
                    id: b["id"] as? String ?? "",
                    answers: b["answers"] as? [String] ?? [],
                    points: (b["points"] as? NSNumber)?.doubleValue
                )
            }
            return .cloze(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: body["passage"] as? String ?? "",
                blanks: blanks,
                blankNumberStart: body["blankNumberStart"] as? Int
            )
        case .trueFalse:
            let stmts = (body["statements"] as? [[String: Any]] ?? []).map { s in
                TrueFalseStatement(
                    id: s["id"] as? String ?? "",
                    content: s["content"] as? String ?? "",
                    answer: s["answer"] as? Bool,
                    points: (s["points"] as? NSNumber)?.doubleValue
                )
            }
            let method = (body["scoringMethod"] as? String).flatMap(TrueFalseScoringMethod.init(rawValue:))
            return .trueFalse(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                statements: stmts,
                scoringMethod: method
            )
        case .matching:
            let left = (body["left"] as? [[String: Any]] ?? []).map {
                MatchingSideItem(id: $0["id"] as? String ?? "", content: $0["content"] as? String ?? "")
            }
            let right = (body["right"] as? [[String: Any]] ?? []).map {
                MatchingSideItem(id: $0["id"] as? String ?? "", content: $0["content"] as? String ?? "")
            }
            let pairs = (body["pairs"] as? [[String: Any]])?.map {
                MatchingPairKey(
                    leftId: $0["leftId"] as? String ?? "",
                    rightId: $0["rightId"] as? String ?? "",
                    points: ($0["points"] as? NSNumber)?.doubleValue
                )
            }
            return .matching(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                optionOrder: optionOrder,
                left: left,
                right: right,
                reuseRight: body["reuseRight"] as? Bool ?? false,
                pairs: pairs
            )
        case .essay:
            return .essay(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                wordLimit: body["wordLimit"] as? Int
            )
        case .questionSet:
            return .questionSet(
                id: id,
                displayNo: displayNo,
                points: points,
                stem: stem,
                passage: body["passage"] as? String ?? "",
                blankNumberStart: body["blankNumberStart"] as? Int,
                children: children
            )
        }
    }

    private static func parseSubmitted(_ dict: [String: Any], for item: QuestionItem) -> AnswerValue? {
        switch item {
        case .singleChoice:
            return AnswerValues.singleChoice(item: item, optionId: dict["optionId"] as? String)
        case .multipleChoice:
            return AnswerValues.multipleChoice(item: item, optionIds: dict["optionIds"] as? [String] ?? [])
        case .shortAnswer:
            return AnswerValues.shortAnswer(text: dict["text"] as? String ?? "")
        case .cloze:
            let blanks = dict["blanks"] as? [String: String] ?? [:]
            return AnswerValues.cloze(item: item, blanks: blanks)
        case .trueFalse:
            var map: [String: Bool] = [:]
            if let raw = dict["statements"] as? [String: Any] {
                for (k, v) in raw {
                    if let b = v as? Bool {
                        map[k] = b
                    }
                }
            }
            return AnswerValues.trueFalse(item: item, statements: map)
        case .matching:
            var map: [String: String] = [:]
            if let raw = dict["pairs"] as? [String: Any] {
                for (k, v) in raw {
                    if let s = v as? String {
                        map[k] = s
                    }
                }
            }
            return AnswerValues.matching(item: item, pairs: map)
        case .essay:
            let html = dict["html"] as? String ?? ""
            let wc = dict["wordCount"] as? Int
            return AnswerValues.essay(html: html, wordCount: wc)
        case .questionSet:
            return nil
        }
    }

    private static func parseReview(_ dict: [String: Any]) -> ItemReview {
        let outcome = (dict["outcome"] as? String).flatMap(QuestionOutcome.init(rawValue:))
        let earned = (dict["earned"] as? NSNumber)?.doubleValue
        let maxPoints = (dict["max"] as? NSNumber)?.doubleValue
        let showAnswers = dict["showAnswers"] as? Bool ?? true
        let solutionHtml = dict["solutionHtml"] as? String
        let level: ReviewLevel = showAnswers ? (solutionHtml != nil ? .fullSolution : .scoreAndAnswers) : .scoreOnly

        var detail: [String: PartResult] = [:]
        if let rawDetail = dict["gradingDetail"] as? [String: [String: Any]] {
            for (k, partDict) in rawDetail {
                if let pOutcome = (partDict["outcome"] as? String).flatMap(QuestionOutcome.init(rawValue:)) {
                    detail[k] = PartResult(
                        key: partDict["key"] as? String ?? k,
                        outcome: pOutcome,
                        earned: (partDict["earned"] as? NSNumber)?.doubleValue,
                        max: (partDict["max"] as? NSNumber)?.doubleValue
                    )
                }
            }
        }

        var essayDetail: EssayReviewDetail?
        if let essayDict = dict["essay"] as? [String: Any] {
            let state = (essayDict["state"] as? String).flatMap(EssayGradingState.init(rawValue:)) ?? .pending
            let criteria = (essayDict["criteria"] as? [[String: Any]] ?? []).map { c in
                EssayCriterionResult(
                    description: c["description"] as? String ?? "",
                    earned: (c["earned"] as? NSNumber)?.doubleValue,
                    max: (c["max"] as? NSNumber)?.doubleValue ?? 0
                )
            }
            essayDetail = EssayReviewDetail(
                state: state,
                wordCount: essayDict["wordCount"] as? Int,
                criteria: criteria,
                comment: essayDict["comment"] as? String
            )
        }

        var children: [String: ItemReview] = [:]
        if let rawChildren = dict["children"] as? [String: [String: Any]] {
            for (k, childDict) in rawChildren {
                children[k] = parseReview(childDict)
            }
        }

        return ItemReview(
            outcome: outcome,
            earned: earned,
            max: maxPoints,
            level: level,
            showAnswers: showAnswers,
            solutionHtml: solutionHtml,
            detail: detail,
            essay: essayDetail,
            children: children
        )
    }
}

@MainActor
enum QuestionViewTestHelper {
    private static var sinks: [ObjectIdentifier: QuestionInspectionSink] = [:]

    static func sink(for root: UIView) -> QuestionInspectionSink? {
        if let direct = sinks[ObjectIdentifier(root)] {
            return direct
        }
        if let window = root.window, let winSink = sinks[ObjectIdentifier(window)] {
            return winSink
        }
        return nil
    }

    @discardableResult
    static func triggerAction(identifier: String, in root: UIView) -> Bool {
        let performed = sink(for: root)?.performAction(for: identifier) ?? false
        if performed {
            pumpRunLoop(0.1)
        }
        return performed
    }

    static func host<V: View>(
        _ view: V,
        size: CGSize = CGSize(width: 390, height: 844),
        sizeClass: UserInterfaceSizeClass = .compact,
        themeMode: ThemeMode = .light,
        contentSizeCategory: UIContentSizeCategory = .large
    ) -> (window: UIWindow, host: UIHostingController<AnyView>) {
        let palette = BrandPaletteSet.defaultThi360Green.palette(for: themeMode)
        let inspectionSink = QuestionInspectionSink()
        let wrapped = AnyView(
            view
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, StatusPalette.forMode(themeMode))
                .environment(\.horizontalSizeClass, sizeClass)
                .environment(\.colorScheme, themeMode == .dark ? .dark : .light)
                .environment(\.dynamicTypeSize, DynamicTypeSize(contentSizeCategory) ?? .large)
                .environment(\.questionInspector, inspectionSink)
        )
        let host = UIHostingController(rootView: wrapped)
        host.traitOverrides.horizontalSizeClass = (sizeClass == .regular) ? .regular : .compact
        host.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light
        host.traitOverrides.preferredContentSizeCategory = contentSizeCategory
        host.overrideUserInterfaceStyle = themeMode == .dark ? .dark : .light
        host.view.frame = CGRect(origin: .zero, size: size)
        host.view.backgroundColor = UIColor(hex: palette.bgSurface)

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        sinks[ObjectIdentifier(window)] = inspectionSink
        sinks[ObjectIdentifier(host.view)] = inspectionSink
        window.overrideUserInterfaceStyle = themeMode == .dark ? .dark : .light
        window.rootViewController = host
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        pumpRunLoop()
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        pumpRunLoop()
        return (window, host)
    }

    static func pumpRunLoop(_ seconds: TimeInterval = 0.08) {
        RunLoop.main.run(until: Date().addingTimeInterval(seconds))
    }

    static func allViews(in root: UIView) -> [UIView] {
        var result: [UIView] = [root]
        for sub in root.subviews {
            result.append(contentsOf: allViews(in: sub))
        }
        return result
    }

    static func findViews<T: UIView>(ofType _: T.Type, in root: UIView) -> [T] {
        allViews(in: root).compactMap { $0 as? T }
    }

    static func findAccessibilityElements(in root: UIView) -> [NSObject] {
        var found: [NSObject] = []
        var visited = Set<ObjectIdentifier>()

        func visit(_ node: Any) {
            guard let obj = node as? NSObject else { return }
            let oid = ObjectIdentifier(obj)
            guard visited.insert(oid).inserted else { return }

            if let view = obj as? UIView {
                if view.isHidden || view.alpha < 0.01 {
                    return
                }
                found.append(view)
                if let elements = view.accessibilityElements, !elements.isEmpty {
                    for el in elements {
                        visit(el)
                    }
                } else {
                    let count = view.accessibilityElementCount()
                    if count != NSNotFound && count > 0 && count < 5000 {
                        for idx in 0..<count {
                            if let el = view.accessibilityElement(at: idx) {
                                visit(el)
                            }
                        }
                    }
                }
                for sub in view.subviews {
                    visit(sub)
                }
                return
            }

            found.append(obj)
            if let nested = obj.accessibilityElements, !nested.isEmpty {
                for el in nested {
                    visit(el)
                }
            } else {
                let count = obj.accessibilityElementCount()
                if count != NSNotFound && count > 0 && count < 5000 {
                    for idx in 0..<count {
                        if let el = obj.accessibilityElement(at: idx) {
                            visit(el)
                        }
                    }
                }
            }
        }

        visit(root)
        return found
    }

    static func allAccessibilityStrings(in root: UIView) -> [String] {
        var texts: [String] = []
        if let recorded = sink(for: root)?.allRecordedStrings {
            texts.append(contentsOf: recorded)
        }
        for el in findAccessibilityElements(in: root) {
            if let label = el.accessibilityLabel, !label.isEmpty {
                texts.append(label)
            }
            if let value = el.accessibilityValue, !value.isEmpty {
                texts.append(value)
            }
            if let textView = el as? UITextView, let t = textView.text, !t.isEmpty {
                texts.append(t)
            }
            if let textField = el as? UITextField, let t = textField.text, !t.isEmpty {
                texts.append(t)
            }
            if let label = el as? UILabel, let t = label.text, !t.isEmpty {
                texts.append(t)
            }
        }
        return texts
    }

    static func allAccessibilityIdentifiers(in root: UIView) -> Set<String> {
        var ids = Set<String>()
        if let recorded = sink(for: root)?.nodes.keys {
            ids.formUnion(recorded)
        }
        for el in findAccessibilityElements(in: root) {
            if let id = (el as? UIAccessibilityIdentification)?.accessibilityIdentifier, !id.isEmpty {
                ids.insert(id)
            }
        }
        return ids
    }

    static func accessibilityElement(withIdentifier id: String, in root: UIView) -> NSObject? {
        if let node = sink(for: root)?.nodes[id] {
            let proxy = UIAccessibilityElement(accessibilityContainer: root)
            proxy.accessibilityIdentifier = node.identifier
            proxy.accessibilityLabel = node.label
            proxy.accessibilityValue = node.value
            proxy.accessibilityFrame = node.frame
            var traits: UIAccessibilityTraits = []
            if node.isButton { traits.insert(.button) }
            if !node.isEnabled { traits.insert(.notEnabled) }
            if node.isSelected { traits.insert(.selected) }
            proxy.accessibilityTraits = traits
            return proxy
        }
        return findAccessibilityElements(in: root).first {
            ($0 as? UIAccessibilityIdentification)?.accessibilityIdentifier == id
        }
    }

    static func snapshotPNG(window: UIWindow, size: CGSize) -> (UIImage, Data)? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: ctx.cgContext)
            }
        }
        guard let data = image.pngData() else { return nil }
        return (image, data)
    }
}
