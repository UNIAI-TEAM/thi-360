import Foundation
import HTTPTypes
import OpenAPIRuntime
import UIKit
import XCTest
@testable import Thi360

final class ImageLoaderTests: XCTestCase {
    private static func makeSamplePNGData() throws -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 8, height: 8))
        let image = renderer.image { context in
            UIColor.systemGreen.setFill()
            context.fill(CGRect(x: 0, y: 0, width: 8, height: 8))
        }
        return try XCTUnwrap(image.pngData())
    }

    func testAttachesBearerOnlyForApiHostAndClearsMemoryCacheOnLogout() async throws {
        let pngData = try Self.makeSamplePNGData()
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "img-access-token",
                refreshToken: "img-refresh-token",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                username: "student.demo",
                accountId: "acc-1"
            )
        )

        let transport = ContractFakeTransport()
        transport.register(method: .get, path: "/api/v1/assets/avatar.png") { _ in
            var fields = HTTPFields()
            fields[.contentType] = "image/png"
            return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
        }
        transport.register(method: .get, path: "/public/banner.png") { _ in
            var fields = HTTPFields()
            fields[.contentType] = "image/png"
            return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
        }
        transport.register(method: .get, path: "/api/v1/public/storefront/covers/cover-1.png") { _ in
            var fields = HTTPFields()
            fields[.contentType] = "image/png"
            return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
        }
        transport.register(method: .get, path: "/api/v1/assets/avatar-noauth.png") { _ in
            var fields = HTTPFields()
            fields[.contentType] = "image/png"
            return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
        }

        let apiBase = URL(string: "https://api.thi360.test")!
        let loader = ImageLoader(
            apiBaseURL: apiBase,
            tokenStore: tokenStore,
            fetcher: ClientTransportImageDataFetcher(transport: transport)
        )

        XCTAssertTrue(loader.shouldAttachBearer(for: URL(string: "https://api.thi360.test/api/v1/assets/avatar.png")!))
        XCTAssertFalse(loader.shouldAttachBearer(for: URL(string: "http://api.thi360.test:443/api/v1/assets/avatar.png")!))
        XCTAssertFalse(loader.shouldAttachBearer(for: URL(string: "https://api.thi360.test:8443/api/v1/assets/avatar.png")!))
        XCTAssertFalse(loader.shouldAttachBearer(for: URL(string: "https://api.thi360.test/api/v1/public/storefront/covers/cover-1.png")!))
        XCTAssertFalse(loader.shouldAttachBearer(for: URL(string: "https://cdn.example.com/public/banner.png")!))

        let first = try await loader.loadImage(from: "/api/v1/assets/avatar.png")
        XCTAssertGreaterThan(first.size.width, 0)
        let second = try await loader.loadImage(from: "/api/v1/assets/avatar.png")
        XCTAssertGreaterThan(second.size.width, 0)

        let apiRequests = transport.requests(method: .get, path: "/api/v1/assets/avatar.png")
        XCTAssertEqual(apiRequests.count, 1, "Second read must hit NSCache without extra network call")
        XCTAssertEqual(apiRequests.first?.header(.authorization), "Bearer img-access-token")

        _ = try await loader.loadImage(from: "/api/v1/public/storefront/covers/cover-1.png")
        let publicCoverRequests = transport.requests(method: .get, path: "/api/v1/public/storefront/covers/cover-1.png")
        XCTAssertEqual(publicCoverRequests.count, 1)
        XCTAssertNil(
            publicCoverRequests.first?.header(.authorization),
            "Public storefront cover URL must never receive Authorization header"
        )

        _ = try await loader.loadImage(from: "/api/v1/assets/avatar-noauth.png", authenticated: false)
        let optOutRequests = transport.requests(method: .get, path: "/api/v1/assets/avatar-noauth.png")
        XCTAssertEqual(optOutRequests.count, 1)
        XCTAssertNil(optOutRequests.first?.header(.authorization))

        _ = try await loader.loadImage(from: "https://cdn.example.com/public/banner.png")
        let foreignRequests = transport.requests(method: .get, path: "/public/banner.png")
        XCTAssertEqual(foreignRequests.count, 1)
        XCTAssertNil(foreignRequests.first?.header(.authorization), "Foreign host must never receive Bearer token")

        let cleanup = SessionCleanup(
            storeProvider: AccountScopedStoreProvider(
                rootDirectoryURL: FileManager.default.temporaryDirectory
                    .appendingPathComponent("img-cleanup-\(UUID().uuidString)", isDirectory: true)
            )
        )
        await cleanup.register(loader)
        await cleanup.runLogout(accountId: "acc-1")

        XCTAssertNil(loader.cachedImage(for: URL(string: "https://api.thi360.test/api/v1/assets/avatar.png")!))
        _ = try await loader.loadImage(from: "/api/v1/assets/avatar.png")
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/assets/avatar.png"), 2)
    }

    func testRefreshesOnceAndRetriesOn401ForAuthenticatedImage() async throws {
        let pngData = try Self.makeSamplePNGData()
        let refreshData = try ContractFixtureLoader.loadAuthFixture("refresh-200.json")
        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "expired-img-token",
                refreshToken: "valid-refresh-token",
                accessExpiresIn: 300,
                refreshExpiresIn: 604_800,
                mustChangePassword: false,
                accountId: "acc-1"
            )
        )

        let transport = ContractFakeTransport()
        transport.registerJSON(method: .post, path: "/api/v1/auth/mobile/refresh", statusCode: 200, data: refreshData)
        transport.enqueue(method: .get, path: "/api/v1/assets/question-1.png", handlers: [
            { _ in ContractFakeTransport.emptyResponse(statusCode: 401) },
            { req in
                XCTAssertEqual(req.header(.authorization), "Bearer fixture-access-token")
                var fields = HTTPFields()
                fields[.contentType] = "image/png"
                return (HTTPResponse(status: .ok, headerFields: fields), HTTPBody(pngData))
            }
        ])

        let client = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: { .vi }
        )
        let loader = ImageLoader(
            apiBaseURL: client.serverURL,
            tokenStore: tokenStore,
            refresher: client.refresher,
            fetcher: ClientTransportImageDataFetcher(transport: transport)
        )

        let image = try await loader.loadImage(from: "/api/v1/assets/question-1.png")
        XCTAssertGreaterThan(image.size.width, 0)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/mobile/refresh"), 1)
        XCTAssertEqual(transport.callCount(method: .get, path: "/api/v1/assets/question-1.png"), 2)
    }
}
