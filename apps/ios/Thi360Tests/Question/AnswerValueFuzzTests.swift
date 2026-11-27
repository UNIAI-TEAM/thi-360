import Foundation
import XCTest
@testable import Thi360

final class AnswerValueFuzzTests: XCTestCase {
    private struct SeededRNG {
        var state: UInt64

        init(seed: UInt64) {
            self.state = seed
        }

        mutating func next() -> UInt64 {
            state = state &* 6364136223846793005 &+ 1442695040888963407
            return state
        }

        mutating func int(in range: Range<Int>) -> Int {
            Int(next() % UInt64(range.count)) + range.lowerBound
        }

        mutating func bool() -> Bool {
            (next() & 1) == 1
        }
    }

    func test1000SeededOperationsMatchServerAnswerValueValidatorRulesAndExportReplayCorpus() throws {
        var rng = SeededRNG(seed: 2026_11_27_2865)

        let singleItem = QuestionItem.singleChoice(
            id: "fuzz-single",
            displayNo: 1,
            points: 1,
            stem: "<p>Single</p>",
            optionOrder: ["s3", "s1", "s4", "s2"],
            options: [
                ChoiceOption(id: "s1", content: "<p>1</p>"),
                ChoiceOption(id: "s2", content: "<p>2</p>"),
                ChoiceOption(id: "s3", content: "<p>3</p>"),
                ChoiceOption(id: "s4", content: "<p>4</p>")
            ]
        )
        let multiItem = QuestionItem.multipleChoice(
            id: "fuzz-multi",
            displayNo: 2,
            points: 1,
            stem: "<p>Multi</p>",
            optionOrder: nil,
            options: [
                ChoiceOption(id: "m1", content: "<p>1</p>"),
                ChoiceOption(id: "m2", content: "<p>2</p>"),
                ChoiceOption(id: "m3", content: "<p>3</p>"),
                ChoiceOption(id: "m4", content: "<p>4</p>")
            ],
            scoringRule: .partial
        )
        let shortItem = QuestionItem.shortAnswer(
            id: "fuzz-short",
            displayNo: 3,
            points: 1,
            stem: "<p>Short</p>",
            answerFormat: .text,
            inputHint: nil,
            answers: []
        )
        let clozeItem = QuestionItem.cloze(
            id: "fuzz-cloze",
            displayNo: 4,
            points: 2,
            stem: nil,
            passage: "<p>[[b1]] [[b2]] [[b3]]</p>",
            blanks: [ClozeBlank(id: "b1"), ClozeBlank(id: "b2"), ClozeBlank(id: "b3")],
            blankNumberStart: 4
        )
        let tfItem = QuestionItem.trueFalse(
            id: "fuzz-tf",
            displayNo: 7,
            points: 1,
            stem: "<p>TF</p>",
            statements: [
                TrueFalseStatement(id: "t1", content: "<p>1</p>"),
                TrueFalseStatement(id: "t2", content: "<p>2</p>"),
                TrueFalseStatement(id: "t3", content: "<p>3</p>"),
                TrueFalseStatement(id: "t4", content: "<p>4</p>")
            ],
            scoringMethod: .perStatement
        )
        let matchExclusiveItem = QuestionItem.matching(
            id: "fuzz-match-ex",
            displayNo: 8,
            points: 1,
            stem: "<p>Match Exclusive</p>",
            optionOrder: ["r3", "r1", "r4", "r2"],
            left: [
                MatchingSideItem(id: "l1", content: "<p>L1</p>"),
                MatchingSideItem(id: "l2", content: "<p>L2</p>"),
                MatchingSideItem(id: "l3", content: "<p>L3</p>")
            ],
            right: [
                MatchingSideItem(id: "r1", content: "<p>R1</p>"),
                MatchingSideItem(id: "r2", content: "<p>R2</p>"),
                MatchingSideItem(id: "r3", content: "<p>R3</p>"),
                MatchingSideItem(id: "r4", content: "<p>R4</p>")
            ],
            reuseRight: false,
            pairs: nil
        )
        let matchReuseItem = QuestionItem.matching(
            id: "fuzz-match-reuse",
            displayNo: 9,
            points: 1,
            stem: "<p>Match Reuse</p>",
            optionOrder: nil,
            left: [
                MatchingSideItem(id: "l1", content: "<p>L1</p>"),
                MatchingSideItem(id: "l2", content: "<p>L2</p>")
            ],
            right: [
                MatchingSideItem(id: "r1", content: "<p>R1</p>"),
                MatchingSideItem(id: "r2", content: "<p>R2</p>")
            ],
            reuseRight: true,
            pairs: nil
        )
        let essayItem = QuestionItem.essay(
            id: "fuzz-essay",
            displayNo: 10,
            points: 5,
            stem: "<p>Essay</p>",
            wordLimit: 20
        )

        let items: [QuestionItem] = [
            singleItem, multiItem, shortItem, clozeItem, tfItem, matchExclusiveItem, matchReuseItem, essayItem
        ]

        let sampleTexts = [
            "",
            "  56 ",
            "SO42-",
            "Tiếng Việt có dấu\u{0000}",
            "\"CO2\" -- x",
            "-3/4",
            "a\u{0000}b\u{0000}c",
            "H2O -> H2 + O2"
        ]
        let sampleEssayHTMLs = [
            "",
            "<p>   </p>",
            "<p>Mở bài</p><ol><li><p>Ý một</p></li></ol>",
            "<p>Luận điểm <strong>chính</strong> &amp; <em>phụ</em>\u{0000}</p>",
            "<ul><li><p>Điểm 1</p></li><li><p>Điểm 2</p></li></ul>"
        ]

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]

        var corpus: [[String: Any]] = []
        corpus.reserveCapacity(1000)

        for step in 0..<1000 {
            let item = items[rng.int(in: 0..<items.count)]
            let value: AnswerValue

            switch item {
            case .singleChoice:
                let pool: [String?] = [nil, "s1", "s2", "s3", "s4", "bogus", "s1\u{0000}"]
                value = AnswerValues.singleChoice(item: item, optionId: pool[rng.int(in: 0..<pool.count)])

            case .multipleChoice:
                let pool = ["m1", "m2", "m3", "m4", "m1", "invalid"]
                let count = rng.int(in: 0..<6)
                let chosen = (0..<count).map { _ in pool[rng.int(in: 0..<pool.count)] }
                value = AnswerValues.multipleChoice(item: item, optionIds: chosen)

            case .shortAnswer:
                let text = sampleTexts[rng.int(in: 0..<sampleTexts.count)]
                value = AnswerValues.shortAnswer(text: text)

            case .cloze:
                let keys = ["b1", "b2", "b3", "bogus"]
                var map: [String: String] = [:]
                for key in keys where rng.bool() {
                    map[key] = sampleTexts[rng.int(in: 0..<sampleTexts.count)]
                }
                value = AnswerValues.cloze(item: item, blanks: map)

            case .trueFalse:
                let keys = ["t1", "t2", "t3", "t4", "bogus"]
                var map: [String: Bool?] = [:]
                for key in keys {
                    let roll = rng.int(in: 0..<3)
                    if roll == 0 { map[key] = true }
                    else if roll == 1 { map[key] = false }
                    else { map[key] = nil }
                }
                value = AnswerValues.trueFalse(item: item, statements: map)

            case .matching:
                let leftKeys = ["l1", "l2", "l3", "bogus"]
                let rightKeys: [String?] = [nil, "", "r1", "r2", "r3", "r4", "bogus"]
                var map: [String: String?] = [:]
                for key in leftKeys where rng.bool() {
                    map[key] = rightKeys[rng.int(in: 0..<rightKeys.count)]
                }
                value = AnswerValues.matching(item: item, pairs: map)

            case .essay:
                let html = sampleEssayHTMLs[rng.int(in: 0..<sampleEssayHTMLs.count)]
                value = AnswerValues.essay(html: html)

            case .questionSet:
                fatalError("Unreachable leaf")
            }

            let data = try encoder.encode(value)
            let rawString = try XCTUnwrap(String(data: data, encoding: .utf8))
            XCTAssertFalse(rawString.contains("\\u0000"), "Step \(step): encoded JSON must never contain NUL byte")
            XCTAssertFalse(rawString.contains("\u{0000}"), "Step \(step): encoded JSON must never contain literal NUL")

            let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            validateAgainstServerRules(payload: obj, item: item, step: step)

            corpus.append([
                "step": step,
                "questionType": item.questionType.rawValue,
                "itemId": item.id,
                "answer": obj
            ])
        }

        XCTAssertEqual(corpus.count, 1000)

        let corpusData = try JSONSerialization.data(withJSONObject: ["seed": 2026_11_27_2865, "count": corpus.count, "cases": corpus], options: [.sortedKeys])
        let attachment = XCTAttachment(data: corpusData, uniformTypeIdentifier: "public.json")
        attachment.name = "ios-q-fuzz-1000-payloads.json"
        attachment.lifetime = .keepAlways
        add(attachment)

        if let outPath = ProcessInfo.processInfo.environment["THI360_FUZZ_CORPUS_OUT"], !outPath.isEmpty {
            try corpusData.write(to: URL(fileURLWithPath: outPath))
        }
    }

    private func validateAgainstServerRules(payload: [String: Any], item: QuestionItem, step: Int) {
        let type = payload["type"] as? String
        XCTAssertEqual(type, item.questionType.rawValue, "Step \(step): type mismatch")

        switch item {
        case let .singleChoice(_, _, _, _, _, options):
            let validIds = Set(options.map(\.id))
            if let optionId = payload["optionId"] as? String {
                XCTAssertTrue(validIds.contains(optionId), "Step \(step): unknown optionId \(optionId)")
            }

        case let .multipleChoice(_, _, _, _, _, options, _):
            let validIds = Set(options.map(\.id))
            let optionIds = payload["optionIds"] as? [String] ?? []
            XCTAssertEqual(Set(optionIds).count, optionIds.count, "Step \(step): duplicate optionIds")
            for id in optionIds {
                XCTAssertTrue(validIds.contains(id), "Step \(step): unknown optionId \(id)")
            }

        case .shortAnswer:
            let text = payload["text"] as? String
            XCTAssertNotNil(text, "Step \(step): SHORT_ANSWER text missing")
            XCTAssertFalse(text?.contains("\u{0000}") ?? true)

        case let .cloze(_, _, _, _, _, blanks, _):
            let validIds = Set(blanks.map(\.id))
            let map = payload["blanks"] as? [String: String] ?? [:]
            for (k, v) in map {
                XCTAssertTrue(validIds.contains(k), "Step \(step): unknown blankId \(k)")
                XCTAssertFalse(v.contains("\u{0000}"))
            }

        case let .trueFalse(_, _, _, _, statements, _):
            let validIds = Set(statements.map(\.id))
            let map = payload["statements"] as? [String: Bool] ?? [:]
            for k in map.keys {
                XCTAssertTrue(validIds.contains(k), "Step \(step): unknown statementId \(k)")
            }

        case let .matching(_, _, _, _, _, left, right, reuseRight, _):
            let validLeft = Set(left.map(\.id))
            let validRight = Set(right.map(\.id))
            let map = payload["pairs"] as? [String: String] ?? [:]
            var usedRight = Set<String>()
            for (l, r) in map {
                XCTAssertTrue(validLeft.contains(l), "Step \(step): unknown leftId \(l)")
                XCTAssertTrue(validRight.contains(r), "Step \(step): unknown rightId \(r)")
                if !reuseRight {
                    XCTAssertTrue(usedRight.insert(r).inserted, "Step \(step): duplicate rightId \(r) when reuseRight=false")
                }
            }

        case .essay:
            let html = payload["html"] as? String ?? ""
            let wordCount = payload["wordCount"] as? Int ?? -1
            XCTAssertGreaterThanOrEqual(wordCount, 0, "Step \(step): negative wordCount")
            if html.isEmpty {
                XCTAssertEqual(wordCount, 0)
            }

        case .questionSet:
            XCTFail("QuestionSet is not a leaf answer type")
        }
    }
}
