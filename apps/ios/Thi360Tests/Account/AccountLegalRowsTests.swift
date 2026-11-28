import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountLegalRowsTests: XCTestCase {
    func testAccountDisplaysLegalRowsAndOpensTermsAndPrivacyInSingleColumnAndTwoPane() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let phoneRouter = AppRouter(initialDestination: .account)
        let phoneAccountVM = phoneRouter.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await phoneAccountVM.loadProfile()

        let phoneRoot = AppRootView(
            router: phoneRouter,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )
        let (phoneWindow, phoneController) = ProfileFixtureHelper.host(
            phoneRoot,
            size: CGSize(width: 390, height: 1400),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { phoneWindow.isHidden = true }

        let phoneRowsReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let ids = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: phoneController.view)
            let labels = ProfileFixtureHelper.collectAccessibilityLabels(in: phoneController.view)
            return ids.contains("account.legalTermsRow")
                && ids.contains("account.legalPrivacyRow")
                && labels.contains("Điều kiện giao dịch chung")
                && labels.contains("Chính sách bảo mật thông tin")
        }
        XCTAssertTrue(phoneRowsReady)
        let phoneIDs = ProfileFixtureHelper.collectAccessibilityIdentifiers(in: phoneController.view)
        XCTAssertTrue(phoneIDs.contains("account.legalTermsRow"))
        XCTAssertTrue(phoneIDs.contains("account.legalPrivacyRow"))
        let phoneLabels = ProfileFixtureHelper.collectAccessibilityLabels(in: phoneController.view)
        XCTAssertTrue(phoneLabels.contains("Điều kiện giao dịch chung"))
        XCTAssertTrue(phoneLabels.contains("Chính sách bảo mật thông tin"))

        phoneRouter.openLegal(.terms)
        let termsPushed = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            phoneRouter.path(for: .account) == [.legal(.terms)]
                && ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: phoneController)
                    .contains { $0.viewControllers.count == 2 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(termsPushed)
        XCTAssertEqual(phoneRouter.path(for: .account), [.legal(.terms)])
        phoneRouter.back()
        let termsPopped = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let navs = ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: phoneController)
            return phoneRouter.path(for: .account).isEmpty
                && !navs.isEmpty
                && navs.allSatisfy { $0.viewControllers.count <= 1 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(termsPopped)
        XCTAssertTrue(phoneRouter.path(for: .account).isEmpty)

        phoneRouter.openLegal(.privacy)
        let privacyPushed = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            phoneRouter.path(for: .account) == [.legal(.privacy)]
                && ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: phoneController)
                    .contains { $0.viewControllers.count == 2 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(privacyPushed)
        XCTAssertEqual(phoneRouter.path(for: .account), [.legal(.privacy)])
        phoneRouter.back()
        let privacyPopped = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            let navs = ProfileFixtureHelper.findControllers(ofType: UINavigationController.self, in: phoneController)
            return phoneRouter.path(for: .account).isEmpty
                && !navs.isEmpty
                && navs.allSatisfy { $0.viewControllers.count <= 1 && $0.transitionCoordinator == nil }
        }
        XCTAssertTrue(privacyPopped)
        XCTAssertTrue(phoneRouter.path(for: .account).isEmpty)

        let padRouter = AppRouter(initialDestination: .account)
        let padAccountVM = padRouter.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await padAccountVM.loadProfile()

        let padRoot = AppRootView(
            router: padRouter,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .pad
        )
        let (padWindow, padController) = ProfileFixtureHelper.host(
            padRoot,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { padWindow.isHidden = true }

        padAccountVM.selectDetail(.legalTerms)
        let termsDetailReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            padAccountVM.selectedDetail == .legalTerms
                && ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view).contains("account.detail.legalTerms")
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: padController.view).isEmpty
                && RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: padController.view).count >= 3
        }
        XCTAssertTrue(termsDetailReady)
        XCTAssertEqual(padAccountVM.selectedDetail, .legalTerms)
        XCTAssertTrue(
            ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view).contains("account.detail.legalTerms"),
            "iPad detail pane must render LegalDocumentView for Terms"
        )
        XCTAssertTrue(
            RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: padController.view).isEmpty,
            "Selecting Terms in iPad detail pane must replace Profile form"
        )
        XCTAssertGreaterThanOrEqual(
            RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: padController.view).count,
            3,
            "LegalDocumentView for Terms must render vertical and table horizontal scroll views"
        )

        padAccountVM.selectDetail(.legalPrivacy)
        let privacyDetailReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            padAccountVM.selectedDetail == .legalPrivacy
                && ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view).contains("account.detail.legalPrivacy")
                && RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: padController.view).count >= 3
        }
        XCTAssertTrue(privacyDetailReady)
        XCTAssertEqual(padAccountVM.selectedDetail, .legalPrivacy)
        XCTAssertTrue(
            ProfileFixtureHelper.collectAccessibilityIdentifiers(in: padController.view).contains("account.detail.legalPrivacy"),
            "iPad detail pane must render LegalDocumentView for Privacy"
        )
        XCTAssertGreaterThanOrEqual(
            RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: padController.view).count,
            3,
            "LegalDocumentView for Privacy must render vertical and table horizontal scroll views"
        )
    }
}
