import Foundation
import XCTest
@testable import Thi360

final class QuestionItemMapperTests: XCTestCase {
    private struct ContractFixtureIndexEntry: Decodable {
        let file: String
        let operationId: String
        let status: Int
        let variant: String?
    }

    private func loadFixtureIndex() throws -> [ContractFixtureIndexEntry] {
        let indexURL = QuestionTestFixtures.apiFixturesDirURL.appendingPathComponent("index.json")
        let data = try Data(contentsOf: indexURL)
        return try JSONDecoder().decode([ContractFixtureIndexEntry].self, from: data)
    }

    private func fixtureData(for entry: ContractFixtureIndexEntry) throws -> Data {
        let contractsRoot = QuestionTestFixtures.apiFixturesDirURL.deletingLastPathComponent()
        return try Data(contentsOf: contractsRoot.appendingPathComponent(entry.file))
    }

    func testMapsAllCertifiedAttemptStateFixturesFromContractIndex() throws {
        let entries = try loadFixtureIndex()
            .filter { $0.operationId == "getState" && $0.status == 200 }
        XCTAssertFalse(entries.isEmpty, "Expected certified getState 200 fixtures in index.json")

        for entry in entries {
            let data = try fixtureData(for: entry)
            let units = try QuestionItemMapper.mapAllUnits(fromAttemptStateData: data)
            let parts = try QuestionItemMapper.mapParts(fromAttemptStateData: data)
            let totalFromParts = parts.values.reduce(0) { $0 + $1.count }
            XCTAssertEqual(units.count, totalFromParts, "Part map count mismatch for \(entry.file)")
        }
    }

    func testMapsAllCertifiedAttemptReviewFixturesFromContractIndex() throws {
        let entries = try loadFixtureIndex()
            .filter { $0.operationId == "getReview" && $0.status == 200 }
        XCTAssertFalse(entries.isEmpty, "Expected certified getReview 200 fixtures in index.json")

        for entry in entries {
            let data = try fixtureData(for: entry)
            let mapped = try ItemReviewMapper.map(reviewData: data)
            XCTAssertFalse(mapped.isEmpty, "Fixture \(entry.file) must map non-empty review entries")

            for reviewEntry in mapped {
                if case .essay = reviewEntry.item, let essay = reviewEntry.review.essay {
                    XCTAssertTrue(
                        essay.criteria.isEmpty,
                        "Default includeEssayCriteria=false must omit essay criteria per Plan B1.7 in \(entry.file)"
                    )
                }
            }
        }
    }

    func testMapsAttempt50FixtureIntoQuestionItems() throws {
        let data = try Data(contentsOf: QuestionTestFixtures.attempt50FixtureURL)
        let mappedUnits = try QuestionItemMapper.mapAllUnits(fromAttemptStateData: data)
        XCTAssertFalse(mappedUnits.isEmpty, "attempt-50.json must map units cleanly")

        var leafCount = 0
        var mappedTypes = Set<QuestionType>()
        for unit in mappedUnits {
            mappedTypes.insert(unit.questionType)
            if case let .questionSet(_, _, _, _, _, _, children) = unit {
                for child in children {
                    mappedTypes.insert(child.questionType)
                    leafCount += 1
                }
            } else {
                leafCount += 1
            }
        }

        XCTAssertGreaterThanOrEqual(leafCount, 40, "50-item fixture should map full part items")
        XCTAssertGreaterThanOrEqual(mappedTypes.count, 5, "50-item fixture should exercise multiple question types")
    }

    func testMapsReviewWithEssayCriteriaWhenExplicitlyEnabled() throws {
        // Plan A6 derived mutation on certified `attempts-review-200-pending-review.json`:
        // mutates the first review item's `questionType`, `studentAnswer`, `outcome`, `earnedPoints`,
        // and `essayFeedback.criteria` to verify `includeEssayCriteria` opt-in while keeping B1.7 default empty.
        let baseEntry = try XCTUnwrap(
            loadFixtureIndex().first { $0.operationId == "getReview" && $0.variant == "pending-review" }
        )
        let baseData = try fixtureData(for: baseEntry)
        var root = try XCTUnwrap(try JSONSerialization.jsonObject(with: baseData) as? [String: Any])
        var items = try XCTUnwrap(root["items"] as? [[String: Any]])
        var first = try XCTUnwrap(items.first)
        first["questionType"] = "ESSAY"
        first["outcome"] = "PARTIAL"
        first["pointsMax"] = 10.0
        first["earnedPoints"] = 8.0
        first["studentAnswer"] = ["type": "ESSAY", "html": "<p>Bài viết mẫu</p>", "wordCount": 3]
        first["essayFeedback"] = [
            "comment": "Tốt",
            "criteria": [
                ["id": "Luận điểm", "points": 8.0]
            ]
        ]
        items[0] = first
        root["items"] = items
        let mutatedData = try JSONSerialization.data(withJSONObject: root)

        let defaultEntries = try ItemReviewMapper.map(reviewData: mutatedData)
        XCTAssertFalse(defaultEntries.isEmpty)
        XCTAssertEqual(defaultEntries[0].review.essay?.criteria.count, 0)
        XCTAssertEqual(defaultEntries[0].studentAnswer?.essayHtml, "<p>Bài viết mẫu</p>")

        let withCriteria = try ItemReviewMapper.map(reviewData: mutatedData, includeEssayCriteria: true)
        XCTAssertEqual(withCriteria[0].review.essay?.criteria.count, 1)
        XCTAssertEqual(withCriteria[0].review.essay?.criteria.first?.description, "Luận điểm")
    }
}
