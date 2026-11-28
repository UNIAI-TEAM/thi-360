import Foundation
import XCTest
@testable import Thi360

final class AccountDeletionStringsParityTests: XCTestCase {
    private static let requiredDeletionKeys: [String] = [
        "title",
        "explanation",
        "unusedAttemptsWarning",
        "pendingGradingWarning",
        "blockedAttempt",
        "blockedOrder",
        "blockedPendingPayment",
        "openMyExams",
        "openMyOrders",
        "passwordLabel",
        "acknowledge",
        "submit",
        "confirmTitle",
        "confirmAction",
        "confirmCancel",
        "wrongPassword",
        "throttled",
        "failed",
        "deletedNotice"
    ]

    func testAllAccountDeletionStringsMatchWebMessagesCharacterForCharacterInViAndEn() throws {
        let repoRoot = ContractFixtureLoader.repoRootURL
        let webViURL = repoRoot.appendingPathComponent("apps/web-student/src/messages/vi.json")
        let webEnURL = repoRoot.appendingPathComponent("apps/web-student/src/messages/en.json")

        let webViRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: webViURL)) as? [String: Any])
        let webEnRoot = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: webEnURL)) as? [String: Any])

        let webViAccount = try XCTUnwrap(webViRoot["account"] as? [String: Any], "Missing 'account' namespace in web vi.json")
        let webEnAccount = try XCTUnwrap(webEnRoot["account"] as? [String: Any], "Missing 'account' namespace in web en.json")

        let webViDeletion = try XCTUnwrap(webViAccount["deletion"] as? [String: String], "Missing 'account.deletion' in web vi.json (requires WEB-2)")
        let webEnDeletion = try XCTUnwrap(webEnAccount["deletion"] as? [String: String], "Missing 'account.deletion' in web en.json (requires WEB-2)")

        let missingInWebVi = Self.requiredDeletionKeys.filter { webViDeletion[$0] == nil }
        let missingInWebEn = Self.requiredDeletionKeys.filter { webEnDeletion[$0] == nil }
        XCTAssertTrue(missingInWebVi.isEmpty, "Missing account.deletion keys in web vi.json: \(missingInWebVi)")
        XCTAssertTrue(missingInWebEn.isEmpty, "Missing account.deletion keys in web en.json: \(missingInWebEn)")

        let allWebKeys = Set(webViDeletion.keys).union(webEnDeletion.keys).sorted()
        XCTAssertEqual(Set(webViDeletion.keys), Set(webEnDeletion.keys), "Web vi.json and en.json account.deletion keys must match")

        for leaf in allWebKeys {
            let fullKey = "account.deletion.\(leaf)"
            let expectedVi = try XCTUnwrap(webViDeletion[leaf], "Missing web vi key \(fullKey)")
            let expectedEn = try XCTUnwrap(webEnDeletion[leaf], "Missing web en key \(fullKey)")

            let iosVi: String
            let iosEn: String
            if leaf == "deletedNotice" {
                iosVi = AppStrings.common(fullKey, locale: .vi)
                iosEn = AppStrings.common(fullKey, locale: .en)
            } else {
                iosVi = ProfileStrings.text(fullKey, locale: .vi)
                iosEn = ProfileStrings.text(fullKey, locale: .en)
            }

            XCTAssertEqual(iosVi, expectedVi, "Vietnamese copy mismatch for \(fullKey)")
            XCTAssertEqual(iosEn, expectedEn, "English copy mismatch for \(fullKey)")
        }

        let rawUnusedVi = ProfileStrings.text("account.deletion.unusedAttemptsWarning", locale: .vi)
        XCTAssertTrue(rawUnusedVi.contains("{n}"), "Source string must preserve {n} placeholder for parity")
        XCTAssertEqual(
            ProfileStrings.text("account.deletion.unusedAttemptsWarning", locale: .vi, replacements: ["n": "3"]),
            "Bạn còn 3 lượt thi chưa dùng. Các lượt này sẽ mất và không được hoàn tiền."
        )
    }
}
