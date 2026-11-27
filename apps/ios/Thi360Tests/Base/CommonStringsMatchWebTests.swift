import Foundation
import XCTest
@testable import Thi360

final class CommonStringsMatchWebTests: XCTestCase {
    // Keys deferred to WEB-2 (account deletion) are compared only once web defines them;
    // strict parity for account.deletion.* is enforced in IOS-PROFILE once WEB-2 merges.
    private static let optionalUntilWeb2Keys: Set<String> = [
        "account.deletion.deletedNotice"
    ]

    func testCommonCatalogKeysMatchWebMessagesWordForWordInViAndEn() throws {
        let repoRoot = ContractFixtureLoader.repoRootURL
        let viWeb = try Self.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/vi.json")
        )
        let enWeb = try Self.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/en.json")
        )

        let commonCatalogURL = repoRoot.appendingPathComponent("apps/ios/Thi360/Resources/Common.xcstrings")
        let data = try Data(contentsOf: commonCatalogURL)
        let root = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(root["strings"] as? [String: [String: Any]])

        let requiredCommonKeys = [
            "common.appName",
            "common.loading",
            "common.retry",
            "common.offline.title",
            "common.offline.description",
            "errorPage.title",
            "errorPage.description",
            "landing.support.pending",
            "storefront.format.FULL",
            "storefront.format.PART",
            "storefront.format.SUBJECT",
            "storefront.format.BUNDLE",
            "storefront.review",
            "storefront.units.minutes",
            "storefront.units.questions",
            "storefront.units.attempts",
            "storefront.units.attemptsPerExam"
        ]
        for key in requiredCommonKeys {
            XCTAssertNotNil(strings[key], "Common.xcstrings must contain required B1.10 key '\(key)'")
        }

        for (key, entry) in strings.sorted(by: { $0.key < $1.key }) {
            let localizations = try XCTUnwrap(entry["localizations"] as? [String: [String: Any]])
            let iosVi = try XCTUnwrap((localizations["vi"]?["stringUnit"] as? [String: Any])?["value"] as? String)
            let iosEn = try XCTUnwrap((localizations["en"]?["stringUnit"] as? [String: Any])?["value"] as? String)

            if Self.optionalUntilWeb2Keys.contains(key), viWeb[key] == nil, enWeb[key] == nil {
                continue
            }

            let webVi = try XCTUnwrap(viWeb[key], "Web vi.json missing key '\(key)' referenced by Common.xcstrings")
            let webEn = try XCTUnwrap(enWeb[key], "Web en.json missing key '\(key)' referenced by Common.xcstrings")

            XCTAssertEqual(iosVi, webVi, "Common.xcstrings vi mismatch for key '\(key)'")
            XCTAssertEqual(iosEn, webEn, "Common.xcstrings en mismatch for key '\(key)'")
        }
    }

    func testLegalCatalogKeysMatchWebMessagesInViAndEn() throws {
        let repoRoot = ContractFixtureLoader.repoRootURL
        let viWeb = try Self.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/vi.json")
        )
        let enWeb = try Self.loadFlattenedWebMessages(
            at: repoRoot.appendingPathComponent("apps/web-student/src/messages/en.json")
        )

        let sharedLegalKeys = [
            "legal.draftBanner",
            "legal.effectiveFrom",
            "legal.terms.title",
            "legal.privacy.title"
        ]
        for key in sharedLegalKeys {
            let webVi = try XCTUnwrap(viWeb[key], "Missing \(key) in web vi.json")
            let webEn = try XCTUnwrap(enWeb[key], "Missing \(key) in web en.json")
            XCTAssertEqual(AppStrings.legal(key, locale: .vi), webVi, "Legal.xcstrings vi mismatch for '\(key)'")
            XCTAssertEqual(AppStrings.legal(key, locale: .en), webEn, "Legal.xcstrings en mismatch for '\(key)'")
        }
    }

    static func loadFlattenedWebMessages(at url: URL) throws -> [String: String] {
        let data = try Data(contentsOf: url)
        let raw = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        var flat: [String: String] = [:]
        flatten(raw, prefix: "", into: &flat)
        return flat
    }

    private static func flatten(_ dict: [String: Any], prefix: String, into output: inout [String: String]) {
        for (key, value) in dict {
            let fullKey = prefix.isEmpty ? key : "\(prefix).\(key)"
            if let str = value as? String {
                output[fullKey] = str
            } else if let nested = value as? [String: Any] {
                flatten(nested, prefix: fullKey, into: &output)
            }
        }
    }
}
