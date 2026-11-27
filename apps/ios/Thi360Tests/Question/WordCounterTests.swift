import SwiftUI
import XCTest
@testable import Thi360

final class WordCounterTests: XCTestCase {
    private struct WordCountFixtureFile: Decodable {
        let cases: [WordCountCase]
        struct WordCountCase: Decodable {
            let id: String
            let html: String
            let expected: Int
        }
    }

    func testAll30WordCountFixtureCasesMatchExactExpectedCounts() throws {
        let data = try Data(contentsOf: QuestionTestFixtures.wordCountFixtureURL)
        let fixture = try JSONDecoder().decode(WordCountFixtureFile.self, from: data)
        XCTAssertEqual(fixture.cases.count, 30, "Expected 30 word-count fixture cases")

        for tc in fixture.cases {
            XCTAssertEqual(
                WordCounter.count(html: tc.html),
                tc.expected,
                "WordCounter mismatch on \(tc.id): html=\(tc.html)"
            )
        }
    }

    func testExplicitAC16BoundaryExpressions() {
        XCTAssertEqual(WordCounter.count(html: "<p>a</p><p>b</p>"), 2)
        XCTAssertEqual(WordCounter.count(html: "Xin chào – thế giới !"), 4)
    }
}

@MainActor
final class EssayWordCountTests: XCTestCase {
    func testWordLimitWarningDoesNotBlockAndSwitchesToSuccessAtLimit() {
        let item = QuestionItem.essay(
            id: "essay-limit",
            displayNo: 27,
            points: 10,
            stem: "<p>Viết tối thiểu 200 chữ.</p>",
            wordLimit: 200
        )
        let words150 = (1...150).map { "tu\($0)" }.joined(separator: " ")
        let answer150 = AnswerValues.essay(html: "<p>\(words150)</p>")
        XCTAssertEqual(answer150.essayWordCount, 150)

        let (warnWindow, _) = QuestionViewTestHelper.host(
            QuestionView(item: item, answer: answer150, mode: .answering, onAnswer: { _, _ in }, locale: .vi, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 520)
        )
        defer { warnWindow.isHidden = true }

        let warnStrings = QuestionViewTestHelper.allAccessibilityStrings(in: warnWindow).joined(separator: "\n")
        XCTAssertTrue(warnStrings.contains("150 / tối thiểu 200 chữ"), "Expected 150 / tối thiểu 200 chữ in \(warnStrings)")
        XCTAssertTrue(warnStrings.contains("Còn 50 chữ nữa để đạt yêu cầu."), "Expected warning message")

        let words205 = (1...205).map { "tu\($0)" }.joined(separator: " ")
        let answer205 = AnswerValues.essay(html: "<p>\(words205)</p>")
        XCTAssertEqual(answer205.essayWordCount, 205)

        let (okWindow, _) = QuestionViewTestHelper.host(
            QuestionView(item: item, answer: answer205, mode: .answering, onAnswer: { _, _ in }, locale: .vi, fixedWidth: 358)
                .padding(16),
            size: CGSize(width: 390, height: 520)
        )
        defer { okWindow.isHidden = true }

        let okStrings = QuestionViewTestHelper.allAccessibilityStrings(in: okWindow).joined(separator: "\n")
        XCTAssertTrue(okStrings.contains("205 / tối thiểu 200 chữ"))
        XCTAssertTrue(okStrings.contains("Đã đạt yêu cầu số chữ"))
    }
}
