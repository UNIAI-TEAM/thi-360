import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LegalOpenOnWebTests: XCTestCase {
    func testOpenOnWebBuildsExpectedURLsFromCurrentBuildEnvironment() {
        let env = AppEnvironment.current
        let baseString = env.webBaseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))

        let suite = "vn.unicom.thi360.tests.legal.web.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: suite)!
        defer { defaults.removePersistentDomain(forName: suite) }
        let preferences = AppPreferences(userDefaults: defaults)

        var capturedTermsURL: URL?
        let termsView = LegalDocumentView(
            legalId: .terms,
            preferences: preferences,
            webBaseURL: env.webBaseURL,
            onOpenOnWeb: { capturedTermsURL = $0 }
        )
        termsView.triggerOpenOnWeb()
        XCTAssertEqual(capturedTermsURL?.absoluteString, "\(baseString)/dieu-khoan-giao-dich-chung")

        var capturedPrivacyURL: URL?
        let privacyView = LegalDocumentView(
            legalId: .privacy,
            preferences: preferences,
            webBaseURL: env.webBaseURL,
            onOpenOnWeb: { capturedPrivacyURL = $0 }
        )
        privacyView.triggerOpenOnWeb()
        XCTAssertEqual(capturedPrivacyURL?.absoluteString, "\(baseString)/chinh-sach-bao-mat-thong-tin")
    }
}
