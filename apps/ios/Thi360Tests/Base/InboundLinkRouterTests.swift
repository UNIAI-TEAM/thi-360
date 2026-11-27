import Foundation
import XCTest
@testable import Thi360

@MainActor
final class InboundLinkRouterTests: XCTestCase {
    func testRegisteredParserWaitsForUnlockedActiveSessionWhileResetLinkBypassesLock() async {
        let router = AppRouter()
        var receivedOrders: [String] = []

        router.inboundLinkRouter.register(id: "payment.return", requiresUnlockedSession: true) { url, _ in
            guard url.path == "/payment/vnpay-return",
                  let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
                  let orderCode = components.queryItems?.first(where: { $0.name == "vnp_TxnRef" })?.value
            else {
                return nil
            }
            return {
                receivedOrders.append(orderCode)
                router.openPaymentResult(.byOrder(orderCode: orderCode))
            }
        }

        let returnURL = URL(string: "https://student.thi360.test/payment/vnpay-return?vnp_TxnRef=ORD-55")!
        let env = AppEnvironment(
            kind: .dev,
            apiBaseURL: URL(string: "https://api.thi360.test")!,
            webBaseURL: URL(string: "https://student.thi360.test")!
        )

        await router.inboundLinkRouter.notifySessionStateChanged(.locked)
        XCTAssertTrue(router.handleIncomingURL(returnURL, environment: env))
        XCTAssertTrue(router.inboundLinkRouter.hasPendingLink)
        XCTAssertTrue(receivedOrders.isEmpty)
        XCTAssertEqual(router.path(for: .home), [])

        let resetURL = URL(string: "https://student.thi360.test/reset-password?token=reset-tok-123")!
        XCTAssertTrue(router.handleIncomingURL(resetURL, environment: env))
        XCTAssertEqual(router.activeResetLink?.token, "reset-tok-123")

        await router.inboundLinkRouter.notifySessionStateChanged(.active)
        XCTAssertFalse(router.inboundLinkRouter.hasPendingLink)
        XCTAssertEqual(receivedOrders, ["ORD-55"])
        XCTAssertEqual(router.path(for: .home), [.paymentResult(.byOrder(orderCode: "ORD-55"))])
    }
}
