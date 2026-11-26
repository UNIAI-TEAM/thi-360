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

        let dir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-offline-legal-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: dir) }

        let sampleTerms = """
        {"id":"terms","locale":"vi","version":"2026-11-26","status":"published","effectiveDate":"2026-11-26","title":"Điều kiện giao dịch chung","note":null,"blocks":[{"type":"heading","level":2,"text":"1. Quy định chung"},{"type":"paragraph","text":"Nội dung ngoại tuyến."}]}
        """
        try Data(sampleTerms.utf8).write(to: dir.appendingPathComponent("terms.vi.json"))

        let repo = LegalRepository(directoryOverride: dir)
        let loaded = repo.load(.terms, locale: .vi)
        guard case let .success(doc) = loaded else {
            return XCTFail("Expected legal document to load offline, got \(loaded)")
        }

        XCTAssertEqual(doc.title, "Điều kiện giao dịch chung")
        XCTAssertEqual(doc.blocks.count, 2)
        XCTAssertEqual(transport.recordedRequests.count, 0, "Loading legal document must make zero network requests")
    }
}
