import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class StudentOnlyFlowTests: XCTestCase {
    func testLoginWithStudentOnlyShowsRoleNoticeAndStoresNoTokens() async throws {
        let forbiddenFixture = try ContractFixtureLoader.loadAuthFixture("login-403-app-student-only.json")
        let (sessionStore, tokenStore, _, preferences) = SessionTestFactory.makeStack { _ in
            MockClientTransport.jsonResponse(statusCode: 403, data: forbiddenFixture)
        }

        let viewModel = LoginViewModel(sessionStore: sessionStore)
        viewModel.identifier = "quantri.noidung"
        viewModel.password = "AdminPass123!"

        await viewModel.submit()

        XCTAssertEqual(
            sessionStore.state,
            .signedOut(reason: .studentOnly(username: "quantri.noidung")),
            "403 APP_STUDENT_ONLY on login must transition to RoleNotice state with submitted username"
        )
        XCTAssertNil(try tokenStore.read(), "403 APP_STUDENT_ONLY must never persist any token")
        XCTAssertEqual(viewModel.password, "")

        let rootView = AppRootView(
            router: AppRouter(),
            preferences: preferences,
            sessionStore: sessionStore,
            idiomOverride: .phone
        )
        let controller = UIHostingController(rootView: rootView)
        let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 390, height: 844))
        window.rootViewController = controller
        window.makeKeyAndVisible()
        window.layoutIfNeeded()
        RunLoop.main.run(until: Date().addingTimeInterval(0.05))
        defer { window.isHidden = true }

        XCTAssertTrue(
            findViews(ofType: UITextField.self, in: controller.view).isEmpty,
            "RoleNotice screen must replace LoginView inputs"
        )
        XCTAssertTrue(
            findViews(ofType: UITabBar.self, in: controller.view).isEmpty,
            "RoleNotice screen must block authenticated shell"
        )

        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 390, height: 844))
        let image = renderer.image { context in
            if !window.drawHierarchy(in: CGRect(x: 0, y: 0, width: 390, height: 844), afterScreenUpdates: true) {
                window.layer.render(in: context.cgContext)
            }
        }
        let png = try XCTUnwrap(image.pngData())
        XCTAssertGreaterThan(png.count, 4096)

        if let outputDir = ProcessInfo.processInfo.environment["THI360_SNAPSHOT_DIR"], !outputDir.isEmpty {
            let dirURL = URL(fileURLWithPath: outputDir, isDirectory: true)
            try FileManager.default.createDirectory(at: dirURL, withIntermediateDirectories: true)
            try png.write(to: dirURL.appendingPathComponent("role-notice-iphone17-390x844-light.png"))
        }

        XCTAssertEqual(AuthStrings.text("auth.roleNotice.title", locale: .vi), "Ứng dụng này dành cho học viên")
        XCTAssertEqual(AuthStrings.text("auth.roleNotice.switchAccount", locale: .vi), "Đăng nhập bằng tài khoản khác")

        sessionStore.acknowledgeRoleNotice()
        XCTAssertEqual(sessionStore.state, .signedOut(reason: .none))
    }

    func testRefreshWithStudentOnlyClearsSessionAndShowsRoleNotice() async throws {
        let unauthorizedFixture = try ContractFixtureLoader.loadApiFixture("me-401-unauthenticated.json")
        let refreshForbiddenFixture = try ContractFixtureLoader.loadAuthFixture("refresh-403-app-student-only.json")

        let initialTokens = SessionTokens(
            accessToken: "student-access-old",
            refreshToken: "student-refresh-old",
            accessExpiresIn: 300,
            refreshExpiresIn: 604_800,
            mustChangePassword: false,
            username: "hocvien.lenquyen"
        )

        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack(
            initialTokens: initialTokens
        ) { request in
            if request.path.hasSuffix("/api/v1/me") {
                return MockClientTransport.jsonResponse(statusCode: 401, data: unauthorizedFixture)
            }
            if request.path.hasSuffix("/api/v1/auth/mobile/refresh") {
                return MockClientTransport.jsonResponse(statusCode: 403, data: refreshForbiddenFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        XCTAssertEqual(sessionStore.state, .active)

        do {
            _ = try await sessionStore.apiClient.me()
            XCTFail("Expected /api/v1/me to fail after refresh returned 403 APP_STUDENT_ONLY")
        } catch let error as MobileApiError {
            XCTAssertEqual(error.statusCode, 403)
            XCTAssertEqual(error.apiCode, "APP_STUDENT_ONLY")
        }

        XCTAssertNil(try tokenStore.read(), "403 APP_STUDENT_ONLY on refresh must purge stored tokens")
        XCTAssertEqual(
            sessionStore.state,
            .signedOut(reason: .studentOnly(username: "hocvien.lenquyen")),
            "403 APP_STUDENT_ONLY on refresh must present RoleNotice for the revoked account"
        )
        XCTAssertEqual(
            transport.recordedRequests.filter { $0.path.hasSuffix("/api/v1/auth/mobile/refresh") }.count,
            1
        )
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
