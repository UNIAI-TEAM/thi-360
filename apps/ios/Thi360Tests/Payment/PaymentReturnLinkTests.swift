import XCTest
@testable import Thi360

final class PaymentReturnLinkTests: XCTestCase {
    private let devEnv = AppEnvironment(
        kind: .dev,
        apiBaseURL: URL(string: "http://localhost:8080")!,
        webBaseURL: URL(string: "http://localhost:3000")!
    )

    private let demoEnv = AppEnvironment(
        kind: .demo,
        apiBaseURL: URL(string: "https://thi360.zone17.click")!,
        webBaseURL: URL(string: "https://thi360.zone17.click")!
    )

    func testValidAndRejectedLinksInDemoAndDev() {
        let validDemo = URL(string: "https://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1&vnp_Amount=20000000&vnp_SecureHash=abc&extra=ignored")!
        let parsedDemo = PaymentReturnLink.parse(validDemo, environment: demoEnv)
        XCTAssertEqual(parsedDemo?.vnpParams, [
            "vnp_TxnRef": "DH1",
            "vnp_Amount": "20000000",
            "vnp_SecureHash": "abc"
        ])

        let rejectedDemoURLs = [
            "http://thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1",
            "https://evil.example.com/checkout/result/app?vnp_TxnRef=DH1",
            "thi360://checkout/result/app?vnp_TxnRef=DH1",
            "https://user:pass@thi360.zone17.click/checkout/result/app?vnp_TxnRef=DH1",
            "https://thi360.zone17.click/checkout/result?vnp_TxnRef=DH1",
            "https://thi360.zone17.click/checkout/result/app/extra?vnp_TxnRef=DH1",
            "https://thi360.zone17.click/checkout/result/app?foo=bar",
            "https://thi360.zone17.click/reset-password?token=abc"
        ]
        for raw in rejectedDemoURLs {
            let url = URL(string: raw)!
            XCTAssertNil(PaymentReturnLink.parse(url, environment: demoEnv), "Demo must reject: \(raw)")
        }

        let validDevHttp = URL(string: "http://localhost:3000/checkout/result/app?vnp_TxnRef=DH1&vnp_OrderInfo=Thanh+toan")!
        let parsedDevHttp = PaymentReturnLink.parse(validDevHttp, environment: devEnv)
        XCTAssertEqual(parsedDevHttp?.vnpParams["vnp_TxnRef"], "DH1")
        XCTAssertEqual(parsedDevHttp?.vnpParams["vnp_OrderInfo"], "Thanh toan")

        let validDevHttps = URL(string: "https://localhost/checkout/result/app?vnp_TxnRef=DH2")!
        XCTAssertNotNil(PaymentReturnLink.parse(validDevHttps, environment: devEnv))

        XCTAssertNil(PaymentReturnLink.parse(URL(string: "http://evil.local/checkout/result/app?vnp_TxnRef=DH1")!, environment: devEnv))
        XCTAssertNil(PaymentReturnLink.parse(URL(string: "http://localhost/checkout/result?vnp_TxnRef=DH1")!, environment: devEnv))
        XCTAssertNil(PaymentReturnLink.parse(URL(string: "http://localhost/checkout/result/app/x?vnp_TxnRef=DH1")!, environment: devEnv))
    }
}
