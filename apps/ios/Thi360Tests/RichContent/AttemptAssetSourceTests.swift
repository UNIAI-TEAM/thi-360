import Foundation
import HTTPTypes
import OpenAPIRuntime
import UIKit
import XCTest
@testable import Thi360

final class RecordingAssetURLProtocol: URLProtocol {
    static let lock = NSLock()
    static var recordedRequests: [URLRequest] = []
    static var handler: ((URLRequest) throws -> (HTTPURLResponse, Data))?

    static func reset() {
        lock.lock()
        recordedRequests.removeAll()
        handler = nil
        lock.unlock()
    }

    static func snapshotRequests() -> [URLRequest] {
        lock.lock()
        defer { lock.unlock() }
        return recordedRequests
    }

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lock.lock()
        Self.recordedRequests.append(request)
        let currentHandler = Self.handler
        Self.lock.unlock()

        do {
            guard let currentHandler else {
                throw URLError(.cannotFindHost)
            }
            let (response, data) = try currentHandler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        } catch {
            client?.urlProtocol(self, didFailWithError: error)
        }
    }

    override func stopLoading() {}
}

final class AttemptAssetSourceTests: XCTestCase {
    private let attemptId = "11111111-1111-4111-8111-111111111111"
    private let assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
    private let apiBaseURL = URL(string: "https://api.thi360.test")!

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    private func makeSource(
        accessToken: String = "test-access-token",
        cachesDir: URL? = nil
    ) -> AttemptAssetSource {
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: accessToken,
                refreshToken: "test-refresh-token",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student1",
                accountId: "acc-1"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = AttemptAssetSource.makeNoDiskCacheSession(
            protocolClasses: [RecordingAssetURLProtocol.self]
        )
        return AttemptAssetSource(
            attemptId: attemptId,
            serverURL: apiBaseURL,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session,
            cachesDirectoryURL: cachesDir
        )
    }

    private func samplePNGData() -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 24, height: 24))
        let img = renderer.image { ctx in
            UIColor.systemTeal.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 24, height: 24))
        }
        return img.pngData()!
    }

    func testExternalSrcNeverRequested() async throws {
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (response, self.samplePNGData())
        }

        let source = makeSource()
        defer { source.purgeAll() }

        let externalCases = [
            #"<p><img src="https://evil.example.com/tracker.png" alt="external"></p>"#,
            #"<p><img src="//cdn.evil.example/img.png" alt="protocol-relative"></p>"#,
            #"<p><img src="https://evil.example.com/a.png" data-asset-id="not-a-uuid" alt="bad-id"></p>"#
        ]

        for html in externalCases {
            let doc = RichHTMLParser.parse(html)
            let imageDatas = extractImages(from: doc)
            XCTAssertEqual(imageDatas.count, 1, "Expected 1 img element in \(html)")
            XCTAssertEqual(imageDatas[0].ref, .blocked, "External src without valid data-asset-id must resolve to .blocked")

            do {
                _ = try await source.load(imageDatas[0].ref)
                XCTFail("Expected AttemptAssetError.blocked for external image")
            } catch let error as AttemptAssetError {
                XCTAssertEqual(error, .blocked)
            }
        }

        XCTAssertEqual(
            RecordingAssetURLProtocol.snapshotRequests().count,
            0,
            "Zero network requests may be made for external image URLs"
        )
    }

    func testMapsAssetIdAndAdminPath() async throws {
        let png = samplePNGData()
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (response, png)
        }

        let htmlEmptySrc = #"<p><img src="" data-asset-id="\#(assetId)" alt="empty-src"></p>"#
        let htmlAdminSrc = #"<p><img src="/api/v1/admin/question-assets/\#(assetId)" data-asset-id="\#(assetId)" alt="admin-src"></p>"#

        let emptyRef = try XCTUnwrap(extractImages(from: RichHTMLParser.parse(htmlEmptySrc)).first?.ref)
        let adminRef = try XCTUnwrap(extractImages(from: RichHTMLParser.parse(htmlAdminSrc)).first?.ref)
        XCTAssertEqual(emptyRef, .attempt(assetId: assetId))
        XCTAssertEqual(adminRef, .attempt(assetId: assetId))

        let source1 = makeSource(accessToken: "bearer-token-123")
        defer { source1.purgeAll() }
        let loaded1 = try await source1.load(emptyRef)
        XCTAssertEqual(loaded1, png)

        let source2 = makeSource(accessToken: "bearer-token-456")
        defer { source2.purgeAll() }
        let loaded2 = try await source2.load(adminRef)
        XCTAssertEqual(loaded2, png)

        let requests = RecordingAssetURLProtocol.snapshotRequests()
        XCTAssertEqual(requests.count, 2)
        for req in requests {
            XCTAssertEqual(req.url?.host, "api.thi360.test")
            XCTAssertEqual(req.url?.path, "/api/v1/me/attempts/\(attemptId)/assets/\(assetId)")
        }
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Authorization"), "Bearer bearer-token-123")
        XCTAssertEqual(requests[1].value(forHTTPHeaderField: "Authorization"), "Bearer bearer-token-456")
    }

    func testSolutionAssetPath() async throws {
        let png = samplePNGData()
        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (response, png)
        }

        let html = #"<p><img src="/api/v1/me/attempts/\#(attemptId)/solution-assets/\#(assetId)" alt="solution"></p>"#
        let ref = try XCTUnwrap(extractImages(from: RichHTMLParser.parse(html)).first?.ref)
        XCTAssertEqual(ref, .solution(assetId: assetId))

        let source = makeSource(accessToken: "sol-token-999")
        defer { source.purgeAll() }
        let data = try await source.load(ref)
        XCTAssertEqual(data, png)

        let requests = RecordingAssetURLProtocol.snapshotRequests()
        XCTAssertEqual(requests.count, 1)
        XCTAssertEqual(requests[0].url?.path, "/api/v1/me/attempts/\(attemptId)/solution-assets/\(assetId)")
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Authorization"), "Bearer sol-token-999")
    }

    func test404UsesContractFixtureAndConcurrentCoalescing() async throws {
        let indexEntries = try ContractFakeTransport.loadIndex()
        let notFoundEntry = try XCTUnwrap(
            indexEntries.first { $0.operationId == "downloadAttemptAsset" && $0.status == 404 },
            "Missing downloadAttemptAsset 404 fixture in contracts/mobile/api/index.json"
        )
        let notFoundData = try ContractFixtureLoader.loadData(relativePath: notFoundEntry.file)

        let png = samplePNGData()
        let attemptCounter = NSLock()
        var requestCount = 0

        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            attemptCounter.lock()
            requestCount += 1
            let current = requestCount
            attemptCounter.unlock()

            if req.url?.path.contains("missing-id") == true {
                let response = HTTPURLResponse(
                    url: req.url!,
                    statusCode: 404,
                    httpVersion: "HTTP/1.1",
                    headerFields: ["Content-Type": "application/json"]
                )!
                return (response, notFoundData)
            }
            if req.url?.path.contains("slow-dedup") == true {
                Thread.sleep(forTimeInterval: 0.12)
            }
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png", "X-Request-Seq": "\(current)"]
            )!
            return (response, png)
        }

        let source = makeSource()
        defer { source.purgeAll() }

        do {
            _ = try await source.load(.attempt(assetId: "missing-id"))
            XCTFail("Expected 404 error")
        } catch let error as AttemptAssetError {
            XCTAssertEqual(error, .httpStatus(404))
        }

        async let firstDedup = source.load(.attempt(assetId: "slow-dedup"))
        async let secondDedup = source.load(.attempt(assetId: "slow-dedup"))
        let (d1, d2) = try await (firstDedup, secondDedup)
        XCTAssertEqual(d1, png)
        XCTAssertEqual(d2, png)

        let dedupRequests = RecordingAssetURLProtocol.snapshotRequests().filter {
            $0.url?.path.contains("slow-dedup") == true
        }
        XCTAssertEqual(dedupRequests.count, 1, "Two concurrent retries/requests for same ref must coalesce into 1 network request")

        let results = try await withThrowingTaskGroup(of: Data.self) { group in
            for i in 1...20 {
                let id = String(format: "00000000-0000-4000-8000-%012d", i)
                group.addTask {
                    try await source.load(.attempt(assetId: id))
                }
            }
            var collected: [Data] = []
            for try await item in group {
                collected.append(item)
            }
            return collected
        }
        XCTAssertEqual(results.count, 20)
    }

    private func extractImages(from doc: RichDocument) -> [RichImageData] {
        var found: [RichImageData] = []
        for block in doc.blocks {
            switch block {
            case let .paragraph(inlines):
                for inline in inlines {
                    if case let .image(data) = inline { found.append(data) }
                }
            case let .imageFigure(data, _, _, _, _):
                found.append(data)
            default:
                break
            }
        }
        return found
    }
}
