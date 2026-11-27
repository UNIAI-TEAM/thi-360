import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LegalDocumentViewTests: XCTestCase {
    private static var draftFixtureURL: URL {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Fixtures/legal-draft-fixture.vi.json")
    }

    func testRendersEveryBlock() throws {
        let bundledRepo = LegalRepository(bundle: .main)
        let suite = "vn.unicom.thi360.tests.legal.blocks.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)

        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        defer { window.isHidden = true }

        for id in LegalId.allCases {
            for locale in AppLocale.allCases {
                let result = bundledRepo.load(id, locale: locale)
                guard case let .success(doc) = result else {
                    XCTFail("Expected bundled legal/\(id.rawValue).\(locale.rawValue).json to decode, got \(result)")
                    return
                }
                XCTAssertFalse(doc.blocks.isEmpty, "Document \(id.rawValue).\(locale.rawValue) must contain blocks")

                preferences.locale = locale
                let view = NavigationStack {
                    LegalDocumentView(
                        legalId: id,
                        preferences: preferences,
                        repository: bundledRepo
                    )
                }
                .environment(\.brandPalette, .defaultLight)
                .environment(\.statusPalette, .light)

                let controller = UIHostingController(rootView: view)
                window.rootViewController = controller
                window.makeKeyAndVisible()
                window.layoutIfNeeded()
                RunLoop.main.run(until: Date().addingTimeInterval(0.05))
                XCTAssertGreaterThan(controller.view.bounds.width, 0)
            }
        }
    }

    func testDraftBannerOnFixture() throws {
        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-draft-legal-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let draftData = try Data(contentsOf: Self.draftFixtureURL)
        try draftData.write(to: dir.appendingPathComponent("terms.vi.json"))

        let repo = LegalRepository(directoryOverride: dir)
        let result = repo.load(.terms, locale: .vi)
        guard case let .success(doc) = result else {
            return XCTFail("Expected draft fixture to decode, got \(result)")
        }

        XCTAssertEqual(doc.status, .draft)
        XCTAssertNil(doc.effectiveDate)
        XCTAssertEqual(doc.blocks.count, 4)
        XCTAssertEqual(
            AppStrings.legal("legal.draftBanner", locale: .vi),
            "Bản nháp, chờ đơn vị vận hành xác nhận."
        )

        let suite = "vn.unicom.thi360.tests.legal.draft.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = .vi

        let view = NavigationStack {
            LegalDocumentView(legalId: .terms, preferences: preferences, repository: repo)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)

        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }
        XCTAssertGreaterThan(controller.view.bounds.width, 0)
    }

    func testPublishedShowsEffectiveDate() throws {
        let repo = LegalRepository(bundle: .main)
        for id in LegalId.allCases {
            let result = repo.load(id, locale: .vi)
            guard case let .success(doc) = result else {
                return XCTFail("Expected published legal/\(id.rawValue).vi.json in bundle, got \(result)")
            }
            XCTAssertEqual(doc.status, .published, "\(id.rawValue).vi.json must be published")
            let date = try XCTUnwrap(doc.effectiveDate, "\(id.rawValue).vi.json must declare effectiveDate")
            let formatted = LegalDocument.formatEffectiveDate(date)
            XCTAssertEqual(formatted, "26/11/2026")
            let display = AppStrings.legal("legal.effectiveFrom", locale: .vi, replacements: ["date": formatted])
            XCTAssertEqual(display, "Có hiệu lực từ 26/11/2026")
        }
    }

    func testTableScrollsInFrame() throws {
        let repo = LegalRepository(bundle: .main)
        let suite = "vn.unicom.thi360.tests.legal.table.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)
        preferences.locale = .vi

        for id in LegalId.allCases {
            let view = NavigationStack {
                LegalDocumentView(legalId: id, preferences: preferences, repository: repo)
            }
            .environment(\.brandPalette, .defaultLight)
            .environment(\.statusPalette, .light)
            .environment(\.dynamicTypeSize, .accessibility2)

            let size = CGSize(width: 360, height: 800)
            let (window, controller) = RecoveryViewTestHelper.host(
                view,
                size: size,
                sizeClass: .compact,
                themeMode: .light
            )
            defer { window.isHidden = true }

            let scrollViews = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view)
            XCTAssertGreaterThanOrEqual(
                scrollViews.count,
                2,
                "Expected outer vertical scroll view plus nested horizontal table scroll view for \(id.rawValue)"
            )

            if let outerScroll = scrollViews.first {
                XCTAssertLessThanOrEqual(
                    outerScroll.contentSize.width,
                    size.width + 1,
                    "Outer legal screen scroll view must not scroll horizontally at 360pt for \(id.rawValue)"
                )
            }
        }
    }

    func testHeadingsAreHeaders() throws {
        let legalViewSource = try String(
            contentsOf: ContractFixtureLoader.repoRootURL
                .appendingPathComponent("apps/ios/Thi360/Core/Legal/LegalDocumentView.swift"),
            encoding: .utf8
        )
        XCTAssertTrue(
            legalViewSource.contains(".accessibilityAddTraits(.isHeader)"),
            "LegalDocumentView headings must carry .isHeader accessibility trait"
        )
        let headerCount = legalViewSource.components(separatedBy: ".accessibilityAddTraits(.isHeader)").count - 1
        XCTAssertGreaterThanOrEqual(headerCount, 3, "Document title, section headings, and error title must all carry .isHeader")
    }

    func testEnglishShowsNote() throws {
        let repo = LegalRepository(bundle: .main)
        for id in LegalId.allCases {
            let result = repo.load(id, locale: .en)
            guard case let .success(doc) = result else {
                return XCTFail("Expected legal/\(id.rawValue).en.json in bundle, got \(result)")
            }
            let note = try XCTUnwrap(doc.note?.trimmingCharacters(in: .whitespacesAndNewlines))
            XCTAssertFalse(note.isEmpty, "English \(id.rawValue).en.json must include Vietnamese-precedence note")
        }
    }
}
