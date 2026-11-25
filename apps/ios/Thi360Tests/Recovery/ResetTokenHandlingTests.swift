import Foundation
import XCTest
@testable import Thi360

@MainActor
final class ResetTokenHandlingTests: XCTestCase {
    func testResetTokenIsRedactedNotPersistedToDiskAndClearedOnLeavingScreen() async throws {
        let secretResetToken = "reset-secret-token-948271-never-persist"
        let link = ResetLink(token: secretResetToken)

        XCTAssertFalse(String(describing: link).contains(secretResetToken))
        XCTAssertFalse(String(reflecting: link).contains(secretResetToken))
        XCTAssertFalse(String(describing: NewPasswordMode.fromLink(link)).contains(secretResetToken))
        XCTAssertFalse(String(reflecting: NewPasswordMode.fromLink(link)).contains(secretResetToken))

        let sink = InMemoryLogSink()
        let (sessionStore, tokenStore, transport, _) = SessionTestFactory.makeStack(
            environment: .demo,
            logSink: sink
        ) { _ in
            MockClientTransport.emptyResponse(statusCode: 204)
        }

        let devEnv = AppEnvironment(
            kind: .dev,
            apiBaseURL: URL(string: "http://localhost:8080")!,
            webBaseURL: URL(string: "http://localhost:3000")!
        )
        let router = AppRouter()
        let url = URL(string: "https://localhost/reset-password?token=\(secretResetToken)")!
        XCTAssertTrue(router.handleIncomingURL(url, environment: devEnv))

        let vm = router.resetPasswordViewModel(for: try XCTUnwrap(router.activeResetLink), sessionStore: sessionStore)
        XCTAssertTrue(vm.hasActiveResetToken)

        let defaultsDump = String(describing: UserDefaults.standard.dictionaryRepresentation())
        XCTAssertFalse(defaultsDump.contains(secretResetToken), "Reset token must never be written to UserDefaults")
        XCTAssertNil(try tokenStore.read(), "Reset token must never be written to Keychain/TokenStore")

        for directory in [
            FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first,
            FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        ].compactMap({ $0 }) {
            if let enumerator = FileManager.default.enumerator(at: directory, includingPropertiesForKeys: nil) {
                for case let fileURL as URL in enumerator {
                    if let content = try? String(contentsOf: fileURL, encoding: .utf8) {
                        XCTAssertFalse(
                            content.contains(secretResetToken),
                            "Reset token leaked to disk at \(fileURL.path)"
                        )
                    }
                }
            }
        }

        router.clearRecoveryOverlay()
        XCTAssertNil(router.activeResetLink)
        XCTAssertFalse(vm.hasActiveResetToken)

        vm.newPassword = "NewStrong1!"
        vm.confirmPassword = "NewStrong1!"
        await vm.submit()

        XCTAssertTrue(
            transport.recordedRequests.isEmpty,
            "After leaving reset screen, submitting must not send the cleared reset token"
        )
        XCTAssertTrue(vm.isResetLinkInvalid)

        for entry in sink.recordedEntries {
            XCTAssertFalse(entry.message.contains(secretResetToken), "Reset token leaked into log: \(entry.message)")
        }
    }
}
