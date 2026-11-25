import Foundation
import XCTest
@testable import Thi360

final class StringCatalogParityTests: XCTestCase {
    private struct StringCatalogFile: Decodable {
        let sourceLanguage: String
        let strings: [String: StringEntry]
    }

    private struct StringEntry: Decodable {
        let localizations: [String: LocalizationValue]?
    }

    private struct LocalizationValue: Decodable {
        let stringUnit: StringUnit?
    }

    private struct StringUnit: Decodable {
        let state: String?
        let value: String?
    }

    func testAllStringCatalogsHaveCompleteVietnameseAndEnglishParity() throws {
        let resourcesURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Resources", isDirectory: true)

        let files = try FileManager.default.contentsOfDirectory(
            at: resourcesURL,
            includingPropertiesForKeys: nil
        )
        .filter { $0.pathExtension == "xcstrings" }
        .sorted { $0.lastPathComponent < $1.lastPathComponent }

        XCTAssertFalse(files.isEmpty, "Expected at least one .xcstrings file in \(resourcesURL.path)")
        XCTAssertTrue(
            files.contains { $0.lastPathComponent == "Shell.xcstrings" },
            "Shell.xcstrings must exist"
        )
        XCTAssertTrue(
            files.contains { $0.lastPathComponent == "Auth.xcstrings" },
            "Auth.xcstrings must exist"
        )

        let decoder = JSONDecoder()

        for file in files {
            let data = try Data(contentsOf: file)
            let catalog = try decoder.decode(StringCatalogFile.self, from: data)
            XCTAssertFalse(catalog.strings.isEmpty, "\(file.lastPathComponent) must contain keys")

            var viKeys = Set<String>()
            var enKeys = Set<String>()

            for (key, entry) in catalog.strings {
                let localizations = entry.localizations ?? [:]

                if let vi = localizations["vi"]?.stringUnit,
                   let value = vi.value?.trimmingCharacters(in: .whitespacesAndNewlines),
                   !value.isEmpty
                {
                    viKeys.insert(key)
                    XCTAssertEqual(vi.state, "translated", "vi key \(key) in \(file.lastPathComponent) must be translated")
                }

                if let en = localizations["en"]?.stringUnit,
                   let value = en.value?.trimmingCharacters(in: .whitespacesAndNewlines),
                   !value.isEmpty
                {
                    enKeys.insert(key)
                    XCTAssertEqual(en.state, "translated", "en key \(key) in \(file.lastPathComponent) must be translated")
                }
            }

            let missingInEn = viKeys.subtracting(enKeys).sorted()
            let missingInVi = enKeys.subtracting(viKeys).sorted()
            let allKeys = Set(catalog.strings.keys)
            let missingBoth = allKeys.subtracting(viKeys).subtracting(enKeys).sorted()

            XCTAssertTrue(missingInEn.isEmpty, "\(file.lastPathComponent) missing en keys: \(missingInEn)")
            XCTAssertTrue(missingInVi.isEmpty, "\(file.lastPathComponent) missing vi keys: \(missingInVi)")
            XCTAssertTrue(missingBoth.isEmpty, "\(file.lastPathComponent) empty entries: \(missingBoth)")
            XCTAssertEqual(viKeys, enKeys)

            if file.lastPathComponent == "Shell.xcstrings" {
                for key in allKeys.sorted() {
                    let expectedVi = catalog.strings[key]?.localizations?["vi"]?.stringUnit?.value
                    let expectedEn = catalog.strings[key]?.localizations?["en"]?.stringUnit?.value
                    XCTAssertEqual(ShellStrings.text(key, locale: .vi), expectedVi, "Runtime vi mismatch for \(key)")
                    XCTAssertEqual(ShellStrings.text(key, locale: .en), expectedEn, "Runtime en mismatch for \(key)")
                }
            }

            if file.lastPathComponent == "Auth.xcstrings" {
                for key in allKeys.sorted() {
                    let expectedVi = catalog.strings[key]?.localizations?["vi"]?.stringUnit?.value
                    let expectedEn = catalog.strings[key]?.localizations?["en"]?.stringUnit?.value
                    XCTAssertEqual(AuthStrings.text(key, locale: .vi), expectedVi, "Runtime vi mismatch for \(key)")
                    XCTAssertEqual(AuthStrings.text(key, locale: .en), expectedEn, "Runtime en mismatch for \(key)")
                }
            }
        }
    }
}
