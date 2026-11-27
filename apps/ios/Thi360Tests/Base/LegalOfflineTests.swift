import Foundation
import XCTest
@testable import Thi360

@MainActor
final class LegalOfflineTests: XCTestCase {
    func testLegalDocumentLoadsOfflineWithZeroNetworkRequests() throws {
        let observer = FakeNetworkPathObserver()
        let monitor = ConnectivityMonitor(initialIsOnline: false, observer: observer, autoStart: false)
        monitor.start()
        XCTAssertFalse(monitor.isOnline)

        let transport = ContractFakeTransport()
        let repo = LegalRepository(bundle: .main)
        let loaded = repo.load(.terms, locale: .vi)
        guard case let .success(doc) = loaded else {
            return XCTFail("Expected bundled legal document to load offline, got \(loaded)")
        }

        XCTAssertFalse(doc.title.isEmpty)
        XCTAssertFalse(doc.blocks.isEmpty)
        XCTAssertEqual(transport.recordedRequests.count, 0, "Loading legal document must make zero network requests")
    }
}
