import AuthenticationServices
import XCTest
@testable import Thi360

@MainActor
final class WebAuthPaymentBrowserTests: XCTestCase {
    private final class StubAuthSession: PaymentAuthSessionControlling {
        var prefersEphemeralWebBrowserSession: Bool = false
        weak var presentationContextProvider: (any ASWebAuthenticationPresentationContextProviding)?
        let startResult: Bool
        var onStart: ((StubAuthSession) -> Void)?
        private(set) var startCalledCount = 0
        private(set) var cancelCalledCount = 0

        init(startResult: Bool = true, onStart: ((StubAuthSession) -> Void)? = nil) {
            self.startResult = startResult
            self.onStart = onStart
        }

        func start() -> Bool {
            startCalledCount += 1
            if startResult {
                onStart?(self)
            }
            return startResult
        }

        func cancel() {
            cancelCalledCount += 1
        }
    }

    func testAbsoluteAndRelativePaymentURLResolution() {
        let apiBase = URL(string: "https://api.thi360.test")!

        let absolute = PaymentURLResolver.resolve(
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000",
            apiBaseURL: apiBase
        )
        XCTAssertEqual(
            absolute?.absoluteString,
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000"
        )

        let relative = PaymentURLResolver.resolve(
            "/mock-vnpay/pay?vnp_TxnRef=DH26112600000101",
            apiBaseURL: apiBase
        )
        XCTAssertEqual(
            relative?.absoluteString,
            "https://api.thi360.test/mock-vnpay/pay?vnp_TxnRef=DH26112600000101"
        )

        XCTAssertNil(PaymentURLResolver.resolve("javascript:alert(1)", apiBaseURL: apiBase))
        XCTAssertNil(PaymentURLResolver.resolve("   ", apiBaseURL: apiBase))
    }

    func testOpensASWebAuthenticationSessionWithEphemeralAndHttpsCallback() async {
        var capturedURL: URL?
        var capturedHost: String?
        var capturedPath: String?
        var capturedSession: StubAuthSession?
        let callbackURL = URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1&vnp_ResponseCode=00")!

        let browser = WebAuthPaymentBrowser(
            callbackHost: "thi360.zone17.click",
            callbackPath: "/checkout/result/app",
            authSessionFactory: { url, host, path, completion in
                capturedURL = url
                capturedHost = host
                capturedPath = path
                let stub = StubAuthSession(startResult: true) { _ in
                    completion(callbackURL, nil)
                }
                capturedSession = stub
                return stub
            },
            safariFallbackPresenter: { _, _ in
                XCTFail("Safari fallback must not run when ASWebAuthenticationSession succeeds")
                return false
            }
        )

        let target = URL(string: "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_TxnRef=DH1")!
        let outcome = await browser.open(target)

        XCTAssertEqual(outcome, .callback(callbackURL))
        XCTAssertEqual(capturedURL, target)
        XCTAssertEqual(capturedHost, "thi360.zone17.click")
        XCTAssertEqual(capturedPath, "/checkout/result/app")
        XCTAssertEqual(capturedSession?.prefersEphemeralWebBrowserSession, true)
        XCTAssertNotNil(capturedSession?.presentationContextProvider)
    }

    func testCanceledLoginReturnsDismissedWithoutFallback() async {
        var fallbackCalled = false
        let browser = WebAuthPaymentBrowser(
            callbackHost: "thi360.zone17.click",
            callbackPath: "/checkout/result/app",
            authSessionFactory: { _, _, _, completion in
                StubAuthSession(startResult: true) { _ in
                    completion(nil, ASWebAuthenticationSessionError(.canceledLogin))
                }
            },
            safariFallbackPresenter: { _, _ in
                fallbackCalled = true
                return true
            }
        )

        let outcome = await browser.open(URL(string: "https://sandbox.vnpayment.vn/pay")!)
        XCTAssertEqual(outcome, .dismissed)
        XCTAssertFalse(fallbackCalled)
    }

    func testStartReturningFalseFallsBackToSafariAndKeepsPendingOrder() async throws {
        var fallbackURL: URL?
        let targetURL = URL(string: "http://localhost:3000/mock-vnpay/pay?vnp_TxnRef=DH26112600000101&vnp_SecureHash=fixture-vnpay-hash")!
        let browser = WebAuthPaymentBrowser(
            callbackHost: "localhost",
            callbackPath: "/checkout/result/app",
            authSessionFactory: { _, _, _, _ in
                StubAuthSession(startResult: false)
            },
            safariFallbackPresenter: { url, _ in
                fallbackURL = url
                return true
            }
        )

        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 201,
                relativeFixturePath: "api/orders-create-201-redirect.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await vm.loadAll()
        await vm.createOrder()

        XCTAssertEqual(fallbackURL, targetURL)
        XCTAssertEqual(vm.submit, .idle)
        let pending = await harness.pendingOrderStore.readValid(now: harness.clock.now)
        XCTAssertEqual(pending?.orderCode, "DH261126000001")
    }

    func testBothBrowsersFailingShowsGenericErrorAndClearsPendingOrder() async throws {
        let browser = WebAuthPaymentBrowser(
            callbackHost: "localhost",
            callbackPath: "/checkout/result/app",
            authSessionFactory: { _, _, _, _ in
                StubAuthSession(startResult: false)
            },
            safariFallbackPresenter: { _, _ in false }
        )

        let harness = try PaymentTestFactory.makeHarness { transport in
            try PaymentTestFactory.registerExamCheckoutDefaults(on: transport)
            try transport.registerFixture(
                method: .post,
                path: "/api/v1/me/orders",
                statusCode: 201,
                relativeFixturePath: "api/orders-create-201-redirect.json"
            )
        }
        defer { try? FileManager.default.removeItem(at: harness.storeDirURL) }

        let vm = CheckoutViewModel(
            code: "EXAM-DEMO-01",
            gateway: harness.gateway,
            paymentBrowser: browser,
            pendingOrderStore: harness.pendingOrderStore,
            apiBaseURL: harness.sessionStore.apiClient.serverURL,
            clock: harness.clock
        )
        await vm.loadAll()
        await vm.createOrder()

        XCTAssertEqual(vm.submit, .failed(.generic))
        let pending = await harness.pendingOrderStore.readValid(now: harness.clock.now)
        XCTAssertNil(pending, "Pending order must be cleared when no OS browser surface can open")
    }
}
