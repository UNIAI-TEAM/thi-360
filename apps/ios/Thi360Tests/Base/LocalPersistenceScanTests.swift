import Foundation
import XCTest

final class LocalPersistenceScanTests: XCTestCase {
    // Allowlist for direct local storage APIs, each with an explicit reason per Plan B1.4 / A-30:
    // - AccountScopedStore / Provider / SessionCleanup: the account-scoped storage layer itself.
    // - TokenStore: Keychain session token persistence + first-run Keychain cleaner.
    // - AppPreferences / AppearanceRepository / AppearanceCacheStore: phase-1 locale & theme preferences.
    // - BiometricManager / BiometricStores: phase-1 biometric enrollment & prompt state.
    // - SessionStore: UI-test isolated defaults suite wiring for first-run cleaner.
    // - LegalRepository: read-only check of bundled legal JSON files.
    private static let allowedRelativePaths: Set<String> = [
        "Core/Storage/AccountScopedStore.swift",
        "Core/Storage/AccountScopedStoreProvider.swift",
        "Core/Storage/TokenStore.swift",
        "Core/Session/SessionCleanup.swift",
        "Core/Session/SessionStore.swift",
        "Core/Preferences/AppPreferences.swift",
        "Core/Appearance/AppearanceRepository.swift",
        "Core/Appearance/AppearanceModels.swift",
        "Core/Biometric/BiometricManager.swift",
        "Core/Biometric/BiometricKeychainStore.swift",
        "Core/Biometric/BiometricModels.swift",
        "Core/Legal/LegalRepository.swift",
        "Features/RichContent/Assets/AttemptAssetSource.swift"
    ]

    func testNoDirectLocalPersistenceOutsideAccountScopedStoreAndAllowlist() throws {
        let sourceRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360", isDirectory: true)

        let enumerator = try XCTUnwrap(FileManager.default.enumerator(at: sourceRoot, includingPropertiesForKeys: nil))
        let forbiddenTokens = [
            "UserDefaults",
            "@AppStorage",
            "FileManager",
            ".write(to:",
            "SecItemAdd",
            "SecItemUpdate",
            "SecItemCopyMatching",
            "SecItemDelete"
        ]
        var violations: [String] = []

        for case let fileURL as URL in enumerator where fileURL.pathExtension == "swift" {
            let relativePath = fileURL.path.replacingOccurrences(of: sourceRoot.path + "/", with: "")
            if Self.allowedRelativePaths.contains(relativePath) {
                continue
            }
            let content = try String(contentsOf: fileURL, encoding: .utf8)
            for (index, line) in content.components(separatedBy: .newlines).enumerated() {
                let trimmed = line.trimmingCharacters(in: .whitespaces)
                if trimmed.hasPrefix("//") { continue }
                for token in forbiddenTokens where line.contains(token) {
                    violations.append("\(relativePath):\(index + 1) uses '\(token)'")
                }
            }
        }

        XCTAssertTrue(
            violations.isEmpty,
            "Direct local persistence outside AccountScopedStore/allowlist:\n" + violations.joined(separator: "\n")
        )
    }
}
