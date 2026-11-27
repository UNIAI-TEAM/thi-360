import Foundation
import SwiftUI
import XCTest
@testable import Thi360

@MainActor
final class LegalLoadFailureTests: XCTestCase {
    func testMissingAndMalformedLegalFilesShowControlledErrorOpenWebAndDoNotBlockRegister() async throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-corrupt-legal-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        try Data("{not-valid-json".utf8).write(to: dir.appendingPathComponent("privacy.vi.json"))

        let unknownBlockJSON = """
        {"id":"terms","locale":"en","version":"2026-11-26","status":"published","effectiveDate":"2026-11-26","title":"Terms","note":null,"blocks":[{"type":"video","url":"https://example.com"}]}
        """
        try Data(unknownBlockJSON.utf8).write(to: dir.appendingPathComponent("terms.en.json"))

        let repo = LegalRepository(directoryOverride: dir)
        XCTAssertEqual(repo.load(.terms, locale: .vi), .failure(.missing))
        XCTAssertEqual(repo.load(.privacy, locale: .vi), .failure(.malformed))
        XCTAssertEqual(repo.load(.terms, locale: .en), .failure(.malformed))

        let suite = "vn.unicom.thi360.tests.legal.failure.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = .vi

        var openedURLs: [URL] = []
        let webBase = URL(string: "https://demo.thi360.vn")!
        let view = LegalDocumentView(
            legalId: .terms,
            preferences: preferences,
            repository: repo,
            webBaseURL: webBase,
            onOpenOnWeb: { openedURLs.append($0) }
        )

        let (window, _) = RecoveryViewTestHelper.host(
            view.environment(\.brandPalette, .defaultLight).environment(\.statusPalette, .light),
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        view.triggerOpenOnWeb()
        XCTAssertEqual(openedURLs, [URL(string: "https://demo.thi360.vn/dieu-khoan-giao-dich-chung")!])

        let source = try String(
            contentsOf: ContractFixtureLoader.repoRootURL
                .appendingPathComponent("apps/ios/Thi360/Core/Legal/LegalDocumentView.swift"),
            encoding: .utf8
        )
        XCTAssertFalse(source.contains("common.retry"), "LegalDocumentView error state must NOT offer a Retry button")

        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/register", statusCode: 200, data: registerFixture)
        }
        let vm = RegisterViewModel(sessionStore: sessionStore, legalRepository: repo, clock: ManualClock())
        vm.fullName = "Tran Minh Anh"
        vm.username = "minhanh.fallback"
        vm.email = "student.demo@thi360.vn"
        vm.password = "StrongPass1!"
        vm.acceptTerms = true

        await vm.submitRegistration()

        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/register"), 1)
        if case .otp = vm.state {
        } else {
            XCTFail("Registration must still proceed when bundled legal file is missing/malformed")
        }
    }
}
