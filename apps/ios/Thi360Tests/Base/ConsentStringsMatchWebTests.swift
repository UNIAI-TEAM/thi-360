import Foundation
import XCTest
@testable import Thi360

final class ConsentStringsMatchWebTests: XCTestCase {
    func testRegisterConsentStringsMatchWebMessagesVerbatimIncludingTags() throws {
        let repoRoot = ContractFixtureLoader.repoRootURL
        let viWeb = try CommonStringsMatchWebTests.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/vi.json")
        )
        let enWeb = try CommonStringsMatchWebTests.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/en.json")
        )

        let pairs: [(iosKey: String, webKey: String)] = [
            ("recovery.register.termsLabel", "account.register.termsLabel"),
            ("recovery.register.termsRequired", "account.register.termsRequired")
        ]

        for (iosKey, webKey) in pairs {
            let webVi = try XCTUnwrap(viWeb[webKey], "Missing \(webKey) in web vi.json")
            let webEn = try XCTUnwrap(enWeb[webKey], "Missing \(webKey) in web en.json")

            let iosVi = RecoveryStrings.text(iosKey, locale: .vi)
            let iosEn = RecoveryStrings.text(iosKey, locale: .en)

            XCTAssertEqual(iosVi, webVi, "Vietnamese mismatch between \(iosKey) and web \(webKey)")
            XCTAssertEqual(iosEn, webEn, "English mismatch between \(iosKey) and web \(webKey)")
        }

        let viLabel = RecoveryStrings.text("recovery.register.termsLabel", locale: .vi)
        let enLabel = RecoveryStrings.text("recovery.register.termsLabel", locale: .en)
        XCTAssertTrue(viLabel.contains("<terms>") && viLabel.contains("</terms>"))
        XCTAssertTrue(viLabel.contains("<privacy>") && viLabel.contains("</privacy>"))
        XCTAssertTrue(enLabel.contains("<terms>") && enLabel.contains("</terms>"))
        XCTAssertTrue(enLabel.contains("<privacy>") && enLabel.contains("</privacy>"))
    }
}
