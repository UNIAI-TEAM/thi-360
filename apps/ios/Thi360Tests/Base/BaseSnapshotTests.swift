import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class BaseSnapshotTests: XCTestCase {
    private struct DeviceSpec {
        let name: String
        let idiom: UIUserInterfaceIdiom
        let size: CGSize
        let sizeClass: UIUserInterfaceSizeClass
    }

    private static let deviceSpecs: [DeviceSpec] = [
        DeviceSpec(name: "iphone17-390x844", idiom: .phone, size: CGSize(width: 390, height: 844), sizeClass: .compact),
        DeviceSpec(name: "ipad-landscape-1180x820", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular),
        DeviceSpec(name: "ipad-portrait-820x1180", idiom: .pad, size: CGSize(width: 820, height: 1180), sizeClass: .regular),
        DeviceSpec(name: "iphone-duo-folded-466x678", idiom: .phone, size: CGSize(width: 466, height: 678), sizeClass: .compact),
        DeviceSpec(name: "iphone-duo-unfolded-951x669", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular)
    ]

    func testListDetailAndLoadStateSnapshotsAcrossDevicesLightDarkAnd130PercentText() throws {
        for spec in Self.deviceSpecs {
            var pngs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let swiftUISizeClass: UserInterfaceSizeClass = (spec.sizeClass == .regular) ? .regular : .compact
                let widthClass = ShellWidth.resolve(
                    idiom: spec.idiom,
                    horizontalSizeClass: swiftUISizeClass,
                    availableWidth: spec.size.width
                )
                let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
                let view = ListDetailLayout(
                    selection: .constant("exam-1"),
                    list: {
                        VStack(alignment: .leading, spacing: 12) {
                            BrandMark(markSize: 32, locale: .vi)
                            LoadStateView(
                                state: .loaded(value: "IELTS Mock Test 01", stale: true),
                                locale: .vi,
                                staleMessage: AppStrings.common("common.offline.title", locale: .vi),
                                content: { title in
                                    Text(title)
                                        .font(.headline)
                                        .foregroundStyle(palette.textColor)
                                        .padding(16)
                                }
                            )
                        }
                        .padding(16)
                    },
                    detail: { selected in
                        LoadStateView(
                            state: LoadState<String>.failed(kind: .server, canRetry: true, retryAfter: nil),
                            locale: .vi,
                            onRetry: {},
                            content: { Text($0 + selected) }
                        )
                    },
                    emptyDetail: {
                        EmptyView()
                    }
                )
                .background(palette.bgPageColor)
                .environment(\.shellWidth, widthClass)
                .environment(\.brandPalette, palette)
                .environment(\.statusPalette, StatusPalette.forMode(mode))
                .environment(\.dynamicTypeSize, .xxLarge)

                let (window, _) = RecoveryViewTestHelper.host(
                    view,
                    size: spec.size,
                    sizeClass: spec.sizeClass,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                    window: window,
                    size: spec.size,
                    name: "base-listdetail-loadstate-\(spec.name)-\(mode.rawValue)",
                    in: self
                )
            }
            XCTAssertNotEqual(pngs[.light], pngs[.dark], "Light/dark base snapshot must differ for \(spec.name)")
        }
    }

    func testLoginAndLockBrandMarkSnapshotsLightAndDark() throws {
        for mode in ThemeMode.allCases {
            let stack = AccountTestFactory.makeContractStack(
                initialTokens: AccountTestFactory.sampleTokens(),
                capability: .faceIDAvailable,
                initialEnrolled: true,
                themeMode: mode
            )
            defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }
            let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)

            let loginView = LoginView(
                viewModel: LoginViewModel(sessionStore: stack.sessionStore),
                preferences: stack.preferences,
                idiom: .phone
            )
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, StatusPalette.forMode(mode))

            let (loginWindow, _) = RecoveryViewTestHelper.host(
                loginView,
                size: CGSize(width: 390, height: 844),
                sizeClass: .compact,
                themeMode: mode
            )
            defer { loginWindow.isHidden = true }
            _ = try RecoveryViewTestHelper.captureSnapshot(
                window: loginWindow,
                size: CGSize(width: 390, height: 844),
                name: "base-brandmark-login-\(mode.rawValue)",
                in: self
            )

            let lockView = LockView(
                viewModel: LockViewModel(sessionStore: stack.sessionStore, biometricManager: stack.biometricManager),
                preferences: stack.preferences,
                displayName: "Nguyễn Văn An"
            )
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, StatusPalette.forMode(mode))

            let (lockWindow, _) = RecoveryViewTestHelper.host(
                lockView,
                size: CGSize(width: 390, height: 844),
                sizeClass: .compact,
                themeMode: mode
            )
            defer { lockWindow.isHidden = true }
            _ = try RecoveryViewTestHelper.captureSnapshot(
                window: lockWindow,
                size: CGSize(width: 390, height: 844),
                name: "base-brandmark-lock-\(mode.rawValue)",
                in: self
            )
        }
    }

    func testLegalDocumentAt360PtAnd200PercentTextSnapshotsLightAndDark() throws {
        let repo = LegalRepository(bundle: .main)
        var pngs: [ThemeMode: Data] = [:]

        for mode in ThemeMode.allCases {
            let suite = "vn.unicom.thi360.tests.legal.snap.\(UUID().uuidString)"
            let defaults = UserDefaults(suiteName: suite)!
            defer { defaults.removePersistentDomain(forName: suite) }
            let preferences = AppPreferences(userDefaults: defaults)
            preferences.locale = .vi
            preferences.themeMode = mode

            let palette = BrandPaletteSet.defaultThi360Green.palette(for: mode)
            let view = NavigationStack {
                LegalDocumentView(legalId: .terms, preferences: preferences, repository: repo)
            }
            .environment(\.brandPalette, palette)
            .environment(\.statusPalette, StatusPalette.forMode(mode))
            .environment(\.dynamicTypeSize, .accessibility2)

            let size = CGSize(width: 360, height: 800)
            let (window, _) = RecoveryViewTestHelper.host(
                view,
                size: size,
                sizeClass: .compact,
                themeMode: mode
            )
            defer { window.isHidden = true }

            pngs[mode] = try RecoveryViewTestHelper.captureSnapshot(
                window: window,
                size: size,
                name: "base-legal-360pt-a11y2-\(mode.rawValue)",
                in: self
            )
        }

        XCTAssertNotEqual(pngs[.light], pngs[.dark])
    }
}
