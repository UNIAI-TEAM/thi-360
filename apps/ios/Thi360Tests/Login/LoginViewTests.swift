import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class LoginViewTests: XCTestCase {
    func testShowsAllControls() throws {
        XCTAssertEqual(
            LoginLayoutMode.resolve(idiom: .phone, horizontalSizeClass: .compact, height: 844),
            .phonePortrait
        )
        XCTAssertEqual(
            LoginLayoutMode.resolve(idiom: .phone, horizontalSizeClass: .compact, height: 678),
            .phoneCompactCover
        )
        XCTAssertEqual(
            LoginLayoutMode.resolve(idiom: .pad, horizontalSizeClass: .regular, height: 820),
            .padCard
        )
        XCTAssertEqual(
            LoginLayoutMode.resolve(idiom: .pad, horizontalSizeClass: .compact, height: 1180),
            .phonePortrait
        )
        XCTAssertEqual(
            LoginLayoutMode.resolve(idiom: .phone, horizontalSizeClass: .regular, height: 669),
            .phoneTwoPaneBook
        )

        let (sessionStore, _, _, preferences) = try SessionTestFactory.makeContractStack()
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .none))

        let router = AppRouter()
        let rootView = AppRootView(
            router: router,
            preferences: preferences,
            sessionStore: sessionStore,
            idiomOverride: .phone
        )

        let (window, controller) = host(
            rootView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { window.isHidden = true }

        XCTAssertTrue(findViews(ofType: UITabBar.self, in: controller.view).isEmpty, "Signed-out app must gate shell behind LoginView")

        let textFields = findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertEqual(textFields.count, 2, "Login screen must render identifier and password text fields")
        XCTAssertEqual(textFields.filter(\.isSecureTextEntry).count, 1, "Password field must default to secure entry")
        XCTAssertEqual(textFields.filter { !$0.isSecureTextEntry }.count, 1, "Identifier field must be plain text")

        let directVM = LoginViewModel(sessionStore: sessionStore)
        let directLoginView = LoginView(viewModel: directVM, preferences: preferences, idiom: .phone)
            .environment(\.brandPalette, .defaultLight)
        let (toggleWindow, toggleController) = host(
            directLoginView,
            size: CGSize(width: 390, height: 844),
            sizeClass: .compact,
            themeMode: .light
        )
        defer { toggleWindow.isHidden = true }

        XCTAssertEqual(findViews(ofType: UITextField.self, in: toggleController.view).filter(\.isSecureTextEntry).count, 1)
        directVM.togglePasswordVisibility()
        toggleWindow.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        XCTAssertEqual(findViews(ofType: UITextField.self, in: toggleController.view).filter(\.isSecureTextEntry).count, 0)
        directVM.togglePasswordVisibility()
        toggleWindow.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        XCTAssertEqual(findViews(ofType: UITextField.self, in: toggleController.view).filter(\.isSecureTextEntry).count, 1)

        XCTAssertEqual(AuthStrings.text("auth.login.identifierLabel", locale: .vi), "Tên đăng nhập hoặc email")
        XCTAssertEqual(AuthStrings.text("auth.login.passwordLabel", locale: .vi), "Mật khẩu")
        XCTAssertEqual(AuthStrings.text("auth.login.forgotLink", locale: .vi), "Quên mật khẩu?")
        XCTAssertEqual(AuthStrings.text("auth.login.submit", locale: .vi), "Đăng nhập")
        XCTAssertEqual(AuthStrings.text("auth.login.registerLink", locale: .vi), "Đăng ký")
        XCTAssertEqual(AuthStrings.text("auth.login.changeLanguage", locale: .vi), "Đổi ngôn ngữ")

        try renderAndVerifyLoginSnapshots()
    }

    func testValidation() async throws {
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack()

        let viewModel = LoginViewModel(sessionStore: sessionStore)
        await viewModel.submit()

        XCTAssertEqual(viewModel.identifierError, .identifierRequired)
        XCTAssertEqual(viewModel.passwordError, .passwordRequired)
        XCTAssertEqual(
            viewModel.identifierError?.message(locale: .vi),
            "Vui lòng nhập tên đăng nhập hoặc email."
        )
        XCTAssertEqual(
            viewModel.passwordError?.message(locale: .vi),
            "Vui lòng nhập mật khẩu."
        )
        XCTAssertEqual(
            viewModel.identifierError?.message(locale: .en),
            "Please enter your username or email."
        )
        XCTAssertEqual(
            viewModel.passwordError?.message(locale: .en),
            "Please enter your password."
        )
        XCTAssertTrue(transport.recordedRequests.isEmpty, "Invalid form submission must not invoke network")

        viewModel.identifier = "   "
        viewModel.password = "Secret123!"
        await viewModel.submit()

        XCTAssertEqual(viewModel.identifierError, .identifierRequired)
        XCTAssertNil(viewModel.passwordError)
        XCTAssertTrue(transport.recordedRequests.isEmpty)

        viewModel.identifier = "student.demo"
        viewModel.password = ""
        await viewModel.submit()

        XCTAssertNil(viewModel.identifierError)
        XCTAssertEqual(viewModel.passwordError, .passwordRequired)
        XCTAssertTrue(transport.recordedRequests.isEmpty)
    }

    func testSubmitsOnce() async throws {
        let loginFixture = try ContractFixtureLoader.loadAuthFixture("login-200.json")
        let meFixture = try ContractFixtureLoader.loadApiFixture("me-200.json")
        let gate = AsyncGate()

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.register(method: .post, path: "/api/v1/auth/mobile/login") { _ in
                await gate.markEnteredAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: loginFixture)
            }
            transport.registerJSON(method: .get, path: "/api/v1/me", statusCode: 200, data: meFixture)
        }

        let viewModel = LoginViewModel(sessionStore: sessionStore)
        viewModel.identifier = "student.demo"
        viewModel.password = "Password123!"

        let firstTask = Task { @MainActor in
            await viewModel.submit()
        }

        await gate.waitUntilEntered()
        XCTAssertTrue(viewModel.isSubmitting, "Submit button must lock while login request is in flight")

        let secondTask = Task { @MainActor in
            await viewModel.submit()
        }
        await secondTask.value

        XCTAssertEqual(
            transport.callCount(method: .post, path: "/api/v1/auth/mobile/login"),
            1,
            "Second submit while in flight must be ignored"
        )

        await gate.release()
        await firstTask.value

        XCTAssertFalse(viewModel.isSubmitting)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/mobile/login"), 1)
        XCTAssertEqual(sessionStore.state, .active)
        XCTAssertEqual(viewModel.password, "")
    }

    func testErrorMessages() async throws {
        struct ErrorCase {
            let statusCode: Int
            let fixtureName: String?
            let customJSON: String?
            let expectedVi: String
            let expectedEn: String
        }

        let cases: [ErrorCase] = [
            ErrorCase(
                statusCode: 401,
                fixtureName: "login-401-invalid-credentials.json",
                customJSON: nil,
                expectedVi: "Tên đăng nhập hoặc mật khẩu không đúng.",
                expectedEn: "Username or password is incorrect."
            ),
            ErrorCase(
                statusCode: 403,
                fixtureName: "login-403-account-inactive.json",
                customJSON: nil,
                expectedVi: "Tài khoản đã ngừng hoạt động. Vui lòng liên hệ quản trị viên.",
                expectedEn: "Account is inactive. Please contact an administrator."
            ),
            ErrorCase(
                statusCode: 429,
                fixtureName: "login-429-login-throttled.json",
                customJSON: nil,
                expectedVi: "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau 15 phút.",
                expectedEn: "Too many failed attempts. Please try again in 15 minutes."
            ),
            ErrorCase(
                statusCode: 429,
                fixtureName: "login-429-rate-limited.json",
                customJSON: nil,
                expectedVi: "Quá nhiều yêu cầu từ địa chỉ của bạn. Vui lòng thử lại sau ít phút.",
                expectedEn: "Too many requests from your address. Please try again in a few minutes."
            ),
            ErrorCase(
                statusCode: 500,
                fixtureName: nil,
                customJSON: #"{"code":"UNKNOWN_INTERNAL_777","message":"Raw stack detail","fieldErrors":[],"traceId":"trace-1"}"#,
                expectedVi: "Không thể đăng nhập. Vui lòng thử lại.",
                expectedEn: "Unable to sign in. Please try again."
            )
        ]

        for errorCase in cases {
            let bodyData: Data
            if let fixtureName = errorCase.fixtureName {
                bodyData = try ContractFixtureLoader.loadAuthFixture(fixtureName)
            } else {
                bodyData = Data(try XCTUnwrap(errorCase.customJSON).utf8)
            }

            let (sessionStore, tokenStore, _, _) = try SessionTestFactory.makeContractStack { transport in
                transport.registerJSON(
                    method: .post,
                    path: "/api/v1/auth/mobile/login",
                    statusCode: errorCase.statusCode,
                    data: bodyData
                )
            }

            let viewModel = LoginViewModel(sessionStore: sessionStore)
            viewModel.identifier = "student.demo"
            viewModel.password = "WrongPass123!"

            await viewModel.submit()

            XCTAssertEqual(viewModel.identifier, "student.demo", "Identifier must be preserved on error")
            XCTAssertEqual(viewModel.password, "", "Password must be cleared on error")
            XCTAssertNil(try tokenStore.read(), "Failed login must not persist tokens")
            XCTAssertEqual(viewModel.serverBanner?.message(locale: .vi), errorCase.expectedVi)
            XCTAssertEqual(viewModel.serverBanner?.message(locale: .en), errorCase.expectedEn)
            XCTAssertFalse(viewModel.serverBanner?.message(locale: .vi).contains("UNKNOWN_INTERNAL_777") ?? false)
            XCTAssertFalse(viewModel.serverBanner?.message(locale: .en).contains("Raw stack detail") ?? false)
        }
    }

    private func renderAndVerifyLoginSnapshots() throws {
        struct SnapshotSpec {
            let name: String
            let idiom: UIUserInterfaceIdiom
            let size: CGSize
            let sizeClass: UIUserInterfaceSizeClass
        }

        let specs: [SnapshotSpec] = [
            SnapshotSpec(name: "login-iphone17-390x844", idiom: .phone, size: CGSize(width: 390, height: 844), sizeClass: .compact),
            SnapshotSpec(name: "login-ipad-portrait-820x1180", idiom: .pad, size: CGSize(width: 820, height: 1180), sizeClass: .regular),
            SnapshotSpec(name: "login-ipad-landscape-1180x820", idiom: .pad, size: CGSize(width: 1180, height: 820), sizeClass: .regular),
            SnapshotSpec(name: "login-iphone-duo-folded-466x678", idiom: .phone, size: CGSize(width: 466, height: 678), sizeClass: .compact),
            SnapshotSpec(name: "login-iphone-duo-unfolded-portrait-669x951", idiom: .phone, size: CGSize(width: 669, height: 951), sizeClass: .regular),
            SnapshotSpec(name: "login-iphone-duo-unfolded-landscape-951x669", idiom: .phone, size: CGSize(width: 951, height: 669), sizeClass: .regular)
        ]

        let outputDir = ProcessInfo.processInfo.environment["THI360_SNAPSHOT_DIR"]

        for spec in specs {
            var renderedPNGs: [ThemeMode: Data] = [:]
            for mode in ThemeMode.allCases {
                let (sessionStore, _, _, preferences) = try SessionTestFactory.makeContractStack()
                preferences.themeMode = mode

                let rootView = AppRootView(
                    router: AppRouter(),
                    preferences: preferences,
                    sessionStore: sessionStore,
                    idiomOverride: spec.idiom
                )

                let (window, _) = host(
                    rootView,
                    size: spec.size,
                    sizeClass: spec.sizeClass,
                    themeMode: mode
                )
                defer { window.isHidden = true }

                let png = try capturePNG(window: window, size: spec.size, name: "\(spec.name)-\(mode.rawValue)", outputDir: outputDir)
                renderedPNGs[mode] = png
            }

            XCTAssertNotEqual(renderedPNGs[.light], renderedPNGs[.dark], "Light/dark login snapshots must differ for \(spec.name)")
        }
    }

    private func capturePNG(window: UIWindow, size: CGSize, name: String, outputDir: String?) throws -> Data {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2.0
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let image = renderer.image { context in
            let drew = window.drawHierarchy(in: CGRect(origin: .zero, size: size), afterScreenUpdates: true)
            if !drew {
                window.layer.render(in: context.cgContext)
            }
        }
        let png = try XCTUnwrap(image.pngData())
        XCTAssertGreaterThan(png.count, 4096, "Snapshot \(name) unexpectedly small")

        let attachment = XCTAttachment(image: image)
        attachment.name = "\(name).png"
        attachment.lifetime = .keepAlways
        add(attachment)

        if let outputDir, !outputDir.isEmpty {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("\(name).png"))
        }

        return png
    }

    private func host<Content: View>(
        _ view: Content,
        size: CGSize,
        sizeClass: UIUserInterfaceSizeClass,
        themeMode: ThemeMode
    ) -> (UIWindow, UIHostingController<Content>) {
        let controller = UIHostingController(rootView: view)
        controller.traitOverrides.horizontalSizeClass = sizeClass
        controller.traitOverrides.userInterfaceStyle = (themeMode == .dark) ? .dark : .light

        let window: UIWindow
        if let scene = UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first {
            window = UIWindow(windowScene: scene)
            window.frame = CGRect(origin: .zero, size: size)
        } else {
            window = UIWindow(frame: CGRect(origin: .zero, size: size))
        }
        window.rootViewController = controller
        window.overrideUserInterfaceStyle = (themeMode == .dark) ? .dark : .light
        window.makeKeyAndVisible()
        window.setNeedsLayout()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        window.layoutIfNeeded()
        return (window, controller)
    }

    private func findViews<T: UIView>(ofType type: T.Type, in root: UIView) -> [T] {
        var found: [T] = []
        if let typed = root as? T {
            found.append(typed)
        }
        for subview in root.subviews {
            found.append(contentsOf: findViews(ofType: type, in: subview))
        }
        return found
    }
}

private actor AsyncGate {
    private var entered = false
    private var enteredWaiters: [CheckedContinuation<Void, Never>] = []
    private var releaseWaiters: [CheckedContinuation<Void, Never>] = []
    private var released = false

    func markEnteredAndWait() async {
        entered = true
        let waiters = enteredWaiters
        enteredWaiters.removeAll()
        waiters.forEach { $0.resume() }

        guard !released else { return }
        await withCheckedContinuation { continuation in
            releaseWaiters.append(continuation)
        }
    }

    func waitUntilEntered() async {
        guard !entered else { return }
        await withCheckedContinuation { continuation in
            enteredWaiters.append(continuation)
        }
    }

    func release() {
        released = true
        let waiters = releaseWaiters
        releaseWaiters.removeAll()
        waiters.forEach { $0.resume() }
    }
}
