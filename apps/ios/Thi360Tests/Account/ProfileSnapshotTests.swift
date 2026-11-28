import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ProfileSnapshotTests: XCTestCase {
    func testProfileEmailVariantsAndResponsiveSnapshotsLightDarkAndDynamicType130() async throws {
        let verifiedData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let unverifiedPendingData = try ContractFixtureLoader.loadApiFixture("me-profile-200-avatar-pending-email.json")
        let noEmailData = try ContractFixtureLoader.loadApiFixture("me-profile-200-no-email.json")

        let emailVariants: [(name: String, data: Data)] = [
            ("profile-email-verified", verifiedData),
            ("profile-email-unverified-pending", unverifiedPendingData),
            ("profile-email-none", noEmailData)
        ]

        for variant in emailVariants {
            var pngs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let stack = AccountTestFactory.makeContractStack(
                    initialTokens: AccountTestFactory.sampleTokens(),
                    themeMode: mode
                ) { transport in
                    transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: variant.data)
                }
                defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

                let accountVM = AccountViewModel(
                    sessionStore: stack.sessionStore,
                    biometricManager: stack.biometricManager
                )
                await accountVM.loadProfile()
                let profileVM = accountVM.ensureProfileViewModel()
                XCTAssertTrue(profileVM.pendingCode.isEmpty, "Snapshots must never contain OTP digits")
                XCTAssertTrue(profileVM.currentPassword.isEmpty, "Snapshots must never contain passwords")

                let view = NavigationStack {
                    ProfileView(viewModel: profileVM, preferences: stack.preferences)
                }
                .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                .environment(\.statusPalette, StatusPalette.forMode(mode))

                let (window, controller) = RecoveryViewTestHelper.host(
                    view,
                    size: CGSize(width: 390, height: 844),
                    sizeClass: .compact,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: CGSize(width: 390, height: 844),
                    name: "\(variant.name)-iphone-390x844-\(mode.rawValue)",
                    in: self
                )

                if let scrollView = RecoveryViewTestHelper.findViews(ofType: UIScrollView.self, in: controller.view).first {
                    let maxY = max(0, scrollView.contentSize.height - scrollView.bounds.height + scrollView.adjustedContentInset.bottom)
                    scrollView.setContentOffset(CGPoint(x: 0, y: maxY), animated: false)
                    window.layoutIfNeeded()
                    ProfileFixtureHelper.pumpMainRunLoop(seconds: 0.08)
                    _ = try RecoveryViewTestHelper.captureSnapshot(
                        window: window,
                        size: CGSize(width: 390, height: 844),
                        name: "\(variant.name)-emailblock-iphone-390x844-\(mode.rawValue)",
                        in: self
                    )
                }
            }
            XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light/dark snapshots must differ for \(variant.name)")
        }

        let dtStack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            themeMode: .light
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: unverifiedPendingData)
        }
        defer { UserDefaults(suiteName: dtStack.defaultsSuiteName)?.removePersistentDomain(forName: dtStack.defaultsSuiteName) }
        let dtAccountVM = AccountViewModel(sessionStore: dtStack.sessionStore, biometricManager: dtStack.biometricManager)
        await dtAccountVM.loadProfile()
        let dtProfileVM = dtAccountVM.ensureProfileViewModel()
        let dtView = NavigationStack {
            ProfileView(viewModel: dtProfileVM, preferences: dtStack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        .environment(\.dynamicTypeSize, .xxxLarge)

        let (dtWindow, _) = ProfileFixtureHelper.hostWithDynamicType(
            dtView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light,
            contentSizeCategory: .extraExtraExtraLarge
        )
        defer { dtWindow.isHidden = true }
        _ = try RecoveryViewTestHelper.captureSnapshot(
            window: dtWindow,
            size: CGSize(width: 390, height: 844),
            name: "profile-iphone-390x844-dt130-light",
            in: self
        )
    }

    func testAccountTwoPaneSnapshotsIPadAndIPhoneDuoOuterAndInner() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")

        struct Viewport {
            let name: String
            let idiom: UIUserInterfaceIdiom
            let size: CGSize
            let sizeClass: UIUserInterfaceSizeClass
        }

        let viewports: [Viewport] = [
            Viewport(name: "ipad-1180x820", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular),
            Viewport(name: "ipad-820x1180", idiom: .pad, size: CGSize(width: 820, height: 1180), sizeClass: .regular),
            Viewport(name: "iphone-duo-outer-466x678", idiom: .phone, size: CGSize(width: 466, height: 678), sizeClass: .compact),
            Viewport(name: "iphone-duo-inner-951x669", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular)
        ]

        for viewport in viewports {
            var pngs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let stack = AccountTestFactory.makeContractStack(
                    initialTokens: AccountTestFactory.sampleTokens(),
                    themeMode: mode
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
                    idiomOverride: viewport.idiom
                )
                let (window, _) = RecoveryViewTestHelper.host(
                    rootView,
                    size: viewport.size,
                    sizeClass: viewport.sizeClass,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: viewport.size,
                    name: "account-profile-\(viewport.name)-\(mode.rawValue)",
                    in: self
                )
            }
            XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light/dark snapshots must differ for \(viewport.name)")
        }
    }

    func testDeleteAccountSnapshotsLightDarkPhoneAndWideWithEmptyPassword() async throws {
        let readyWarningsData = try ProfileFixtureHelper.mutateJSON("me-account-deletion-check-200-unused-attempts.json") { dict in
            dict["pendingGrading"] = true
        }
        let blockedData = try ContractFixtureLoader.loadApiFixture("me-account-deletion-check-200-blocked.json")

        let states: [(name: String, data: Data?)] = [
            ("delete-account-ready-warnings", readyWarningsData),
            ("delete-account-blocked", blockedData),
            ("delete-account-error", nil)
        ]

        let viewports: [(name: String, size: CGSize, sizeClass: UIUserInterfaceSizeClass, embedded: Bool)] = [
            ("phone-390x844", CGSize(width: 390, height: 844), .compact, false),
            ("wide-1180x820", CGSize(width: 1180, height: 820), .regular, true)
        ]

        for state in states {
            for viewport in viewports {
                var pngs: [ThemeMode: Data] = [:]
                for mode in ThemeMode.allCases {
                    let stack = AccountTestFactory.makeContractStack(
                        initialTokens: AccountTestFactory.sampleTokens(),
                        themeMode: mode
                    ) { transport in
                        if let data = state.data {
                            transport.registerJSON(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 200, data: data)
                        } else {
                            transport.registerEmpty(method: .get, path: "/api/v1/me/account/deletion-check", statusCode: 500)
                        }
                    }
                    defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

                    let service = OpenAPIAccountDeletionService(apiClient: stack.sessionStore.apiClient)
                    let vm = DeleteAccountViewModel(deletionService: service, sessionStore: stack.sessionStore)
                    await vm.loadCheck()
                    XCTAssertTrue(vm.currentPassword.isEmpty, "Delete account snapshots must leave password field empty")

                    let view = NavigationStack {
                        DeleteAccountView(
                            viewModel: vm,
                            preferences: stack.preferences,
                            isEmbeddedPane: viewport.embedded,
                            onOpenMyExams: {},
                            onOpenOrders: {}
                        )
                    }
                    .environment(\.brandPalette, BrandPaletteSet.defaultThi360Green.palette(for: mode))
                    .environment(\.statusPalette, StatusPalette.forMode(mode))

                    let (window, _) = RecoveryViewTestHelper.host(
                        view,
                        size: viewport.size,
                        sizeClass: viewport.sizeClass,
                        themeMode: mode
                    )
                    defer { window.isHidden = true }

                    pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                        window: window,
                        size: viewport.size,
                        name: "\(state.name)-\(viewport.name)-\(mode.rawValue)",
                        in: self
                    )
                }
                XCTAssertNotEqual(pngs[.light], pngs[.dark])
            }
        }
    }
}
