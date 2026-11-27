import Foundation
import XCTest
@testable import Thi360

final class ReviewMapperTests: XCTestCase {
    func testCertifiedFixturesDecodeAndMapCleanly() throws {
        let fullPayload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution")
        let fullModel = ReviewMapper.map(fullPayload)
        XCTAssertEqual(fullModel.resultView, .fullSolution)
        XCTAssertEqual(fullModel.state, .scored)
        XCTAssertEqual(fullModel.parts.count, 1)
        XCTAssertEqual(fullModel.groups.count, 3)
        let setGroup = try XCTUnwrap(fullModel.groups.last)
        XCTAssertEqual(setGroup.badgeText, "3–5")
        if case let .questionSet(_, _, _, _, _, _, children) = setGroup.item {
            XCTAssertEqual(children.count, 3)
        } else {
            XCTFail("Expected last group to be a QUESTION_SET")
        }

        let saPayload = try ResultTestFixtures.decodeReviewPayload(variant: "score-and-answers")
        let saModel = ReviewMapper.map(saPayload)
        XCTAssertEqual(saModel.resultView, .scoreAndAnswers)
        XCTAssertEqual(saModel.groups.first?.outcome, .correct)

        let pendingPayload = try ResultTestFixtures.decodeReviewPayload(variant: "pending-review")
        let pendingModel = ReviewMapper.map(pendingPayload)
        XCTAssertEqual(pendingModel.state, .pendingReview)
        XCTAssertEqual(pendingModel.parts.count, 3)
        XCTAssertTrue(pendingModel.groups(for: pendingModel.parts[0]).isEmpty)
        XCTAssertFalse(pendingModel.groups(for: pendingModel.parts[1]).isEmpty)
        XCTAssertFalse(pendingModel.groups(for: pendingModel.parts[2]).isEmpty)
    }

    func testSetOutcome() throws {
        func makeSetPayload(parentOutcome: String?, childOutcomes: [String]) throws -> ReviewWirePayload {
            try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
                var parent: [String: Any] = [
                    "itemId": "set-1",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 1,
                    "questionType": "QUESTION_SET",
                    "content": "<p>Set</p>",
                    "body": ["passage": "<p>Passage</p>"],
                    "pointsMax": 2.0
                ]
                if let parentOutcome {
                    parent["outcome"] = parentOutcome
                }
                var items: [[String: Any]] = [parent]
                for (idx, outcome) in childOutcomes.enumerated() {
                    items.append([
                        "itemId": "child-\(idx + 1)",
                        "parentItemId": "set-1",
                        "partId": "p1",
                        "partCode": "P1",
                        "ord": idx + 2,
                        "displayNo": idx + 1,
                        "questionType": "SINGLE_CHOICE",
                        "content": "<p>C\(idx + 1)</p>",
                        "body": ["options": [["id": "o1", "content": "<p>A</p>"]]],
                        "outcome": outcome,
                        "pointsMax": 1.0
                    ])
                }
                root["items"] = items
            }
        }

        XCTAssertEqual(ReviewMapper.map(try makeSetPayload(parentOutcome: nil, childOutcomes: ["CORRECT", "CORRECT"])).groups.first?.outcome, .correct)
        XCTAssertEqual(ReviewMapper.map(try makeSetPayload(parentOutcome: nil, childOutcomes: ["BLANK", "BLANK"])).groups.first?.outcome, .blank)
        XCTAssertEqual(ReviewMapper.map(try makeSetPayload(parentOutcome: nil, childOutcomes: ["WRONG", "WRONG"])).groups.first?.outcome, .wrong)
        XCTAssertEqual(ReviewMapper.map(try makeSetPayload(parentOutcome: nil, childOutcomes: ["CORRECT", "WRONG"])).groups.first?.outcome, .partial)
        XCTAssertEqual(ReviewMapper.map(try makeSetPayload(parentOutcome: nil, childOutcomes: ["CORRECT", "BLANK"])).groups.first?.outcome, .partial)
        XCTAssertEqual(
            ReviewMapper.map(try makeSetPayload(parentOutcome: "PENDING_REVIEW", childOutcomes: ["CORRECT", "CORRECT"])).groups.first?.outcome,
            .correct,
            "Parent PENDING_REVIEW outcome must derive from graded visible children"
        )
    }

    func testLeafPointsDefaultMatchingPairMaxTrueFalseAndClozeRange() throws {
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            root["items"] = [
                [
                    "itemId": "default-max",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 1,
                    "displayNo": 1,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>No pointsMax</p>",
                    "optionOrder": ["b", "a"],
                    "body": [
                        "options": [
                            ["id": "a", "content": "<p>A</p>"],
                            ["id": "b", "content": "<p>B</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "a"],
                    "expectedAnswer": ["expectedOptionIds": ["b"]],
                    "outcome": "WRONG"
                ],
                [
                    "itemId": "match-item",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 2,
                    "displayNo": 2,
                    "questionType": "MATCHING",
                    "content": "<p>Match</p>",
                    "body": [
                        "left": [
                            ["id": "l1", "content": "<p>1</p>"],
                            ["id": "l2", "content": "<p>2</p>"],
                            ["id": "l3", "content": "<p>3</p>"]
                        ],
                        "right": [
                            ["id": "r1", "content": "<p>A</p>"],
                            ["id": "r2", "content": "<p>B</p>"],
                            ["id": "r3", "content": "<p>C</p>"]
                        ]
                    ],
                    "studentAnswer": ["type": "MATCHING", "pairs": ["l1": "r1", "l2": "r3"]],
                    "gradingDetail": [
                        "pairResults": [
                            ["leftId": "l1", "matched": true, "pointsEarned": 1.0],
                            ["leftId": "l2", "matched": false, "pointsEarned": 0.0],
                            ["leftId": "l3", "matched": false, "pointsEarned": 0.0]
                        ]
                    ],
                    "outcome": "PARTIAL",
                    "earnedPoints": 1.0,
                    "pointsMax": 3.0
                ],
                [
                    "itemId": "tf-item",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 3,
                    "displayNo": 3,
                    "questionType": "TRUE_FALSE",
                    "content": "<p>TF</p>",
                    "body": [
                        "statements": [
                            ["id": "s1", "content": "<p>S1</p>"],
                            ["id": "s2", "content": "<p>S2</p>"],
                            ["id": "s3", "content": "<p>S3</p>"]
                        ]
                    ],
                    "gradingDetail": [
                        "statementResults": [
                            ["id": "s1", "answered": true, "matched": true],
                            ["id": "s2", "answered": true, "matched": false],
                            ["id": "s3", "answered": false, "matched": false]
                        ]
                    ],
                    "outcome": "PARTIAL",
                    "earnedPoints": 0.25,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "cloze-item",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 4,
                    "displayNo": 4,
                    "questionType": "CLOZE",
                    "content": "<p>Cloze</p>",
                    "body": [
                        "passage": "<p>Fill {{b1}} and {{b2}} and {{b3}}</p>",
                        "blanks": [["id": "b1"], ["id": "b2"], ["id": "b3"]]
                    ],
                    "outcome": "PARTIAL",
                    "earnedPoints": 1.0,
                    "pointsMax": 3.0
                ]
            ]
        }

        let model = ReviewMapper.map(payload)
        XCTAssertEqual(model.groups.count, 4)

        let sc = model.groups[0]
        XCTAssertEqual(sc.mode.reviewData?.max, 1.0, "Missing pointsMax must default to 1")
        XCTAssertNil(sc.mode.reviewData?.earned)
        if case let .singleChoice(_, _, _, _, optionOrder, options) = sc.item {
            XCTAssertEqual(optionOrder, ["b", "a"])
            XCTAssertEqual(options.first(where: { $0.id == "b" })?.correct, true)
            XCTAssertEqual(options.first(where: { $0.id == "a" })?.correct, false)
        } else {
            XCTFail("Expected singleChoice")
        }

        let match = model.groups[1]
        let pairDetail = try XCTUnwrap(match.mode.reviewData?.detail)
        XCTAssertEqual(pairDetail["l1"]?.outcome, .correct)
        XCTAssertEqual(pairDetail["l1"]?.max ?? -1, 1.0, accuracy: 0.0001)
        XCTAssertEqual(pairDetail["l2"]?.outcome, .wrong)
        XCTAssertEqual(pairDetail["l2"]?.max ?? -1, 1.0, accuracy: 0.0001)
        XCTAssertEqual(pairDetail["l3"]?.outcome, .blank)
        XCTAssertEqual(pairDetail["l3"]?.max ?? -1, 1.0, accuracy: 0.0001)

        let tf = model.groups[2]
        let stmtDetail = try XCTUnwrap(tf.mode.reviewData?.detail)
        XCTAssertEqual(stmtDetail["s1"]?.outcome, .correct)
        XCTAssertEqual(stmtDetail["s2"]?.outcome, .wrong)
        XCTAssertEqual(stmtDetail["s3"]?.outcome, .blank)

        let cloze = model.groups[3]
        XCTAssertEqual(cloze.badgeText, "4–6")
    }

    func testPendingReviewFiltered() throws {
        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "pending-review") { root in
            root["parts"] = [[
                "partId": "p1",
                "partCode": "P1",
                "partName": "Tổng hợp",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": true
            ]]
            root["items"] = [
                [
                    "itemId": "essay-pending",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 1,
                    "displayNo": 1,
                    "questionType": "ESSAY",
                    "content": "<p>Tự luận đang chờ chấm</p>",
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 5.0
                ],
                [
                    "itemId": "set-all-pending",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 2,
                    "questionType": "QUESTION_SET",
                    "content": "<p>Chùm chờ chấm toàn bộ</p>",
                    "body": ["passage": "<p>Đoạn ẩn</p>"],
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 4.0
                ],
                [
                    "itemId": "set-all-pending-c1",
                    "parentItemId": "set-all-pending",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 3,
                    "displayNo": 2,
                    "questionType": "ESSAY",
                    "content": "<p>Con chờ 1</p>",
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "set-all-pending-c2",
                    "parentItemId": "set-all-pending",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 4,
                    "displayNo": 3,
                    "questionType": "ESSAY",
                    "content": "<p>Con chờ 2</p>",
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "set-mixed",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 5,
                    "questionType": "QUESTION_SET",
                    "content": "<p>Chùm hỗn hợp</p>",
                    "body": ["passage": "<p>Đoạn hiện</p>"],
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 3.0
                ],
                [
                    "itemId": "set-mixed-graded",
                    "parentItemId": "set-mixed",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 6,
                    "displayNo": 4,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Con đã chấm</p>",
                    "body": ["options": [["id": "o1", "content": "<p>A</p>"]]],
                    "outcome": "CORRECT",
                    "earnedPoints": 1.0,
                    "pointsMax": 1.0
                ],
                [
                    "itemId": "set-mixed-pending",
                    "parentItemId": "set-mixed",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 7,
                    "displayNo": 5,
                    "questionType": "ESSAY",
                    "content": "<p>Con tự luận chờ chấm</p>",
                    "outcome": "PENDING_REVIEW",
                    "pointsMax": 2.0
                ],
                [
                    "itemId": "mc-graded",
                    "partId": "p1",
                    "partCode": "P1",
                    "ord": 8,
                    "displayNo": 6,
                    "questionType": "SINGLE_CHOICE",
                    "content": "<p>Trắc nghiệm đã chấm</p>",
                    "body": ["options": [["id": "o1", "content": "<p>A</p>"]]],
                    "outcome": "WRONG",
                    "earnedPoints": 0.0,
                    "pointsMax": 1.0
                ]
            ]
        }

        let model = ReviewMapper.map(payload)
        XCTAssertEqual(model.groups.map(\.id), ["set-mixed", "mc-graded"])

        let mixedSet = try XCTUnwrap(model.groups.first)
        XCTAssertEqual(mixedSet.outcome, .correct, "Derived from the sole graded child")
        if case let .questionSet(_, _, _, _, _, _, children) = mixedSet.item {
            XCTAssertEqual(children.map(\.id), ["set-mixed-graded"])
        } else {
            XCTFail("Expected set-mixed to remain a QUESTION_SET")
        }

        let summary = ReviewModel.paletteSummary(for: model.groups)
        XCTAssertEqual(summary, ReviewPaletteSummary(correct: 1, partial: 0, wrong: 1, blank: 0))
    }
}
