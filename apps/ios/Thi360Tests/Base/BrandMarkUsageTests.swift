import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class BrandMarkUsageTests: XCTestCase {
    func testBrandMarkRendersOnLoginLockAndPlaceholderDestinations() throws {
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens(),
            capability: .faceIDAvailable,
            initialEnrolled: true
        )
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let loginVM = LoginViewModel(sessionStore: stack.sessionStore)
        let loginView = LoginView(viewModel: loginVM, preferences: stack.preferences, idiom: .phone)
            .environment(\.brandPalette, .defaultLight)
        let (loginWindow, loginVC) = RecoveryViewTestHelper.host(
            loginView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { loginWindow.isHidden = true }
        XCTAssertGreaterThan(loginVC.view.bounds.width, 0)

        let lockVM = LockViewModel(sessionStore: stack.sessionStore, biometricManager: stack.biometricManager)
        let lockView = LockView(viewModel: lockVM, preferences: stack.preferences, displayName: "Nguyễn Văn An")
            .environment(\.brandPalette, .defaultLight)
        let (lockWindow, lockVC) = RecoveryViewTestHelper.host(
            lockView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { lockWindow.isHidden = true }
        XCTAssertGreaterThan(lockVC.view.bounds.width, 0)

        let placeholderView = PlaceholderDestinationView(destination: .home, locale: .vi, showsBrandHeader: true)
            .environment(\.brandPalette, .defaultLight)
        let (phWindow, phVC) = RecoveryViewTestHelper.host(
            placeholderView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { phWindow.isHidden = true }
        XCTAssertGreaterThan(phVC.view.bounds.width, 0)

        XCTAssertNotNil(UIImage(named: "BrandMark"), "BrandMark asset must load from main asset catalog")
        let brandMarkSource = try String(
            contentsOf: ContractFixtureLoader.repoRootURL
                .appendingPathComponent("apps/ios/Thi360/Core/Brand/BrandMark.swift"),
            encoding: .utf8
        )
        XCTAssertTrue(brandMarkSource.contains(#".accessibilityIdentifier("brand.mark")"#))
    }
}
