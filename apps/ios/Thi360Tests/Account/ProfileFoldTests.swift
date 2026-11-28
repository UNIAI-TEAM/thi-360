import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ProfileFoldTests: XCTestCase {
    func testUnsavedProfileDraftSurvivesCompactToRegularAndRegularToCompactTransitionsOnIPhoneDuo() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .phone
        )

        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 466, height: 678),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        router.openProfile()
        let profilePushed = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            router.path(for: .account) == [.profile]
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view).count == 2
        }
        XCTAssertTrue(profilePushed)
        XCTAssertEqual(router.path(for: .account), [.profile])

        let profileVM = accountVM.ensureProfileViewModel()
        profileVM.updateFullName("Nguyễn Đang Sửa Dở")
        profileVM.updatePhone("0987654321")
        XCTAssertTrue(profileVM.isInfoDirty)

        window.frame = CGRect(x: 0, y: 0, width: 951, height: 669)
        controller.traitOverrides.horizontalSizeClass = .regular
        window.setNeedsLayout()
        window.layoutIfNeeded()
        let unfoldedReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            accountVM.selectedDetail == .profile
                && router.path(for: .account).isEmpty
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
                    .contains { $0.text == "Nguyễn Đang Sửa Dở" }
        }
        XCTAssertTrue(unfoldedReady)

        XCTAssertEqual(accountVM.selectedDetail, .profile, "Unfolding while editing Profile must keep Profile selected in right pane")
        XCTAssertTrue(router.path(for: .account).isEmpty, "Unfolding promotes pushed Profile route to right pane")
        XCTAssertEqual(profileVM.fullName, "Nguyễn Đang Sửa Dở", "Mid-edit fullName must be preserved when unfolding")
        XCTAssertEqual(profileVM.phone, "0987654321", "Mid-edit phone must be preserved when unfolding")

        let unfoldedFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertTrue(unfoldedFields.contains { $0.text == "Nguyễn Đang Sửa Dở" })

        window.frame = CGRect(x: 0, y: 0, width: 466, height: 678)
        controller.traitOverrides.horizontalSizeClass = .compact
        window.setNeedsLayout()
        window.layoutIfNeeded()
        let foldedReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            router.path(for: .account) == [.profile]
                && RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
                    .contains { $0.text == "Nguyễn Đang Sửa Dở" }
        }
        XCTAssertTrue(foldedReady)

        XCTAssertEqual(router.path(for: .account), [.profile], "Folding back while editing Profile must keep Profile pushed on compact stack")
        XCTAssertEqual(profileVM.fullName, "Nguyễn Đang Sửa Dở", "Mid-edit fullName must be preserved when folding back")
        XCTAssertEqual(profileVM.phone, "0987654321", "Mid-edit phone must be preserved when folding back")
    }

    func testUnsavedProfileDraftSurvivesIPadCompactToRegularRotation() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let router = AppRouter(initialDestination: .account)
        let accountVM = router.accountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()

        let rootView = AppRootView(
            router: router,
            preferences: stack.preferences,
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            idiomOverride: .pad
        )

        let (window, controller) = RecoveryViewTestHelper.host(
            rootView,
            size: CGSize(width: 1180, height: 820),
            sizeClass: .regular,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let profileVM = accountVM.ensureProfileViewModel()
        profileVM.updateFullName("Trần Đang Sửa iPad")
        profileVM.toggleChangeEmailForm()
        profileVM.updateNewEmail("draft@thi360.vn")

        window.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        controller.traitOverrides.horizontalSizeClass = .compact
        window.setNeedsLayout()
        window.layoutIfNeeded()
        let compactedReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            router.path(for: .account) == [.profile]
        }
        XCTAssertTrue(compactedReady)

        XCTAssertEqual(router.path(for: .account), [.profile])
        XCTAssertEqual(profileVM.fullName, "Trần Đang Sửa iPad")
        XCTAssertEqual(profileVM.newEmail, "draft@thi360.vn")
        XCTAssertTrue(profileVM.isShowingChangeEmailForm)

        window.frame = CGRect(x: 0, y: 0, width: 820, height: 1180)
        controller.traitOverrides.horizontalSizeClass = .regular
        window.setNeedsLayout()
        window.layoutIfNeeded()
        let regularReady = await ProfileFixtureHelper.waitUntil(timeout: 5.0) {
            accountVM.selectedDetail == .profile
                && router.path(for: .account).isEmpty
        }
        XCTAssertTrue(regularReady)

        XCTAssertEqual(accountVM.selectedDetail, .profile)
        XCTAssertEqual(profileVM.fullName, "Trần Đang Sửa iPad")
        XCTAssertEqual(profileVM.newEmail, "draft@thi360.vn")
    }
}
