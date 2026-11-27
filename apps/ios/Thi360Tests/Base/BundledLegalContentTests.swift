import Foundation
import XCTest
@testable import Thi360

final class BundledLegalContentTests: XCTestCase {
    func testBundledLegalFilesMatchContractFilesByteForByteAndShareVersion() throws {
        let contractLegalDir = ContractFixtureLoader.contractsMobileURL
            .appendingPathComponent("legal", isDirectory: true)
        let repo = LegalRepository(bundle: .main)

        var termsVersions: [AppLocale: String] = [:]

        for id in LegalId.allCases {
            for locale in AppLocale.allCases {
                let filename = "\(id.rawValue).\(locale.rawValue).json"
                let contractURL = contractLegalDir.appendingPathComponent(filename)
                XCTAssertTrue(
                    FileManager.default.fileExists(atPath: contractURL.path),
                    "Missing contract legal file: \(filename)"
                )
                let contractBytes = try Data(contentsOf: contractURL)

                let bundledBytes = try XCTUnwrap(
                    repo.rawData(for: id, locale: locale),
                    "Missing bundled legal file in app bundle: legal/\(filename)"
                )
                XCTAssertEqual(
                    bundledBytes,
                    contractBytes,
                    "Bundled legal/\(filename) differs from contracts/mobile/legal/\(filename)"
                )

                let decoded = repo.load(id, locale: locale)
                guard case let .success(doc) = decoded else {
                    XCTFail("Bundled legal/\(filename) failed to decode: \(decoded)")
                    continue
                }
                if id == .terms {
                    termsVersions[locale] = doc.version
                }
            }
        }

        XCTAssertEqual(
            termsVersions[.vi],
            termsVersions[.en],
            "terms.vi.json and terms.en.json must share the same version"
        )
        XCTAssertNotEqual(termsVersions[.vi], "2026-11", "Hardcoded phase-1 termsVersion 2026-11 must not be used")
        XCTAssertEqual(repo.termsVersion(), termsVersions[.vi])
    }
}
