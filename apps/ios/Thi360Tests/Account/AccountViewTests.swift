import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class AccountViewTests: XCTestCase {
    func testAccountDisplaysProfileWithVerifiedUnverifiedAndHidesMissingEmail() async throws {
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let noEmailData = try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")
        let unverifiedData = Data(
            """
            {
              "username": "student.unverified",
              "fullName": "Lê Minh Châu",
              "email": "chau@thi360.vn",
              "emailVerified": false,
              "phone": null,
              "avatarAssetId": null,
              "avatarUrl": null,
              "pendingEmail": null
            }
            """.utf8
        )

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: verifiedData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let viewModel = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        XCTAssertEqual(viewModel.profileState, .loading)

        await viewModel.loadProfile()
        guard case let .loaded(verifiedProfile) = viewModel.profileState else {
            return XCTFail("Expected loaded profile")
        }
        XCTAssertEqual(verifiedProfile.fullName, "Nguyễn Văn An")
        XCTAssertEqual(verifiedProfile.username, "student.demo")
        XCTAssertEqual(verifiedProfile.trimmedEmail, "student.demo@thi360.vn")
        XCTAssertTrue(verifiedProfile.emailVerified)
        XCTAssertTrue(verifiedProfile.hasEmail)
        XCTAssertEqual(AccountStrings.text("account.profile.emailVerified", locale: .vi), "Email đã xác minh")
        XCTAssertEqual(AccountStrings.text("account.profile.emailUnverified", locale: .vi), "Chưa xác minh")

        let router = AppRouter(initialDestination: .account)
        let view = AccountView(
            viewModel: viewModel,
            router: router,
            preferences: stack.preferences,
            effectiveSizeClass: .compact,
            serverDefaultThemeMode: .light,
            onOpenChangePassword: {}
        )
        .environment(\.brandPalette, .defaultLight)
        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        let editableFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertTrue(editableFields.isEmpty, "Account profile details must be read-only with zero text fields")

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedData)
        await viewModel.loadProfile()
        guard case let .loaded(unverifiedProfile) = viewModel.profileState else {
            return XCTFail("Expected unverified profile")
        }
        XCTAssertEqual(unverifiedProfile.trimmedEmail, "chau@thi360.vn")
        XCTAssertFalse(unverifiedProfile.emailVerified)

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: noEmailData)
        await viewModel.loadProfile()
        guard case let .loaded(noEmailProfile) = viewModel.profileState else {
            return XCTFail("Expected no-email profile")
        }
        XCTAssertEqual(noEmailProfile.fullName, "Nguyễn Văn Bình")
        XCTAssertEqual(noEmailProfile.username, "student.noemail")
        XCTAssertNil(noEmailProfile.trimmedEmail)
        XCTAssertFalse(noEmailProfile.hasEmail)
    }

    func testAccountFourStatesAndLocalSettingsRemainUsableDuringErrorAndOffline() async throws {
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let gate = TestAsyncGate()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable
        ) { transport in
            transport.register(method: .get, path: "/api/v1/me/profile") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.emptyResponse(statusCode: 500)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let viewModel = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )

        let loadTask = Task { @MainActor in
            await viewModel.loadProfile()
        }
        await gate.waitUntilEntered()
        XCTAssertEqual(viewModel.profileState, .loading)

        await gate.release()
        await loadTask.value
        XCTAssertEqual(viewModel.profileState, .error)

        stack.preferences.locale = .en
        stack.preferences.themeMode = .dark
        await viewModel.setBiometricEnabled(true, locale: .en)
        XCTAssertEqual(stack.preferences.locale, .en)
        XCTAssertEqual(stack.preferences.themeMode, .dark)
        XCTAssertTrue(viewModel.isBiometricSwitchOn)

        stack.transport.registerNetworkError(method: .get, path: "/api/v1/me/profile", code: .notConnectedToInternet)
        await viewModel.loadProfile()
        XCTAssertEqual(viewModel.profileState, .offline)

        await viewModel.setBiometricEnabled(false, locale: .en)
        XCTAssertFalse(viewModel.isBiometricSwitchOn)

        stack.transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: verifiedData)
        await viewModel.loadProfile()
        guard case let .loaded(profile) = viewModel.profileState else {
            return XCTFail("Retry after offline must load profile")
        }
        XCTAssertEqual(profile.username, "student.demo")
    }

    func testFaceIDUsageDescriptionPresentInBundleAndLocalizedInBothLanguages() throws {
        let usage = Bundle.main.object(forInfoDictionaryKey: "NSFaceIDUsageDescription") as? String
        XCTAssertFalse((usage ?? "").trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

        let infoPlistCatalogURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Thi360/Resources/InfoPlist.xcstrings")
        let data = try Data(contentsOf: infoPlistCatalogURL)
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        let strings = try XCTUnwrap(json["strings"] as? [String: Any])
        let entry = try XCTUnwrap(strings["NSFaceIDUsageDescription"] as? [String: Any])
        let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any])
        XCTAssertNotNil(localizations["vi"])
        XCTAssertNotNil(localizations["en"])
    }

    func testAccountSnapshotsAcrossPhoneIPadAndIPhoneDuo() async throws {
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")

        struct SnapshotCase {
            let name: String
            let idiom: UIUserInterfaceIdiom
            let size: CGSize
            let sizeClass: UIUserInterfaceSizeClass
            let capability: BiometricCapability
        }

        let cases: [SnapshotCase] = [
            SnapshotCase(name: "account-iphone17-390x844", idiom: .phone, size: CGSize(width: 390, height: 844), sizeClass: .compact, capability: .faceIDAvailable),
            SnapshotCase(name: "account-ipad-1180x820", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular, capability: .touchIDAvailable),
            SnapshotCase(name: "account-iphone-duo-open-951x669", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular, capability: .faceIDAvailable)
        ]

        for item in cases {
            var pngs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let stack = AccountTestFactory.makeContractStack(
                    initialTokens: AccountTestFactory.sampleTokens(),
                    capability: item.capability,
                    initialEnrolled: true,
                    themeMode: mode
                ) { transport in
                    transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: verifiedData)
                }
                defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
                stack.sessionStore.unlockSession()

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
                    idiomOverride: item.idiom
                )
                let (window, _) = RecoveryViewTestHelper.host(
                    rootView,
                    size: item.size,
                    sizeClass: item.sizeClass,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                let png = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: item.size,
                    name: "\(item.name)-\(mode.rawValue)",
                    in: self
                )
                pngs[mode] = png
            }
            XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light and dark snapshots must differ for \(item.name)")
        }
    }
}
