import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class ReviewAssetCleanupTests: XCTestCase {
    private let attemptId = "55555555-5555-4555-8555-555555555555"
    private let solutionAssetId = "77777777-8888-4999-8aaa-bbbbbbbbbbbb"
    private let solutionSentinel = Data("THI360-SOLUTION-ASSET-SENTINEL-2871-PRIVATE".utf8)

    override func tearDown() {
        ReviewRecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    private func pngWithSentinelPayload() -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 48, height: 48))
        var png = renderer.image { ctx in
            UIColor.systemGreen.setFill()
            ctx.fill(CGRect(x: 0, y: 0, width: 48, height: 48))
        }.pngData()!
        png.append(solutionSentinel)
        return png
    }

    func testLeavingReviewScreenPurgesMemoryCacheAndLeavesZeroSolutionFilesOnDisk() async throws {
        let payloadBytes = pngWithSentinelPayload()

        ReviewRecordingAssetURLProtocol.reset()
        ReviewRecordingAssetURLProtocol.handler = { req in
            let resp = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "image/png"]
            )!
            return (resp, payloadBytes)
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "cleanup-bearer-token",
                refreshToken: "cleanup-refresh-token",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student.cleanup",
                accountId: "acc-cleanup"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = AttemptAssetSource.makeNoDiskCacheSession(
            protocolClasses: [ReviewRecordingAssetURLProtocol.self]
        )
        let assetSource = AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session
        )

        let payload = try ResultTestFixtures.decodeReviewPayload(variant: "full-solution") { root in
            root["attemptId"] = self.attemptId
            root["parts"] = [[
                "partId": "p-clean",
                "partCode": "P-CLEAN",
                "partName": "Dọn tài nguyên",
                "ord": 1,
                "state": "CLOSED",
                "pendingReview": false
            ]]
            root["items"] = [[
                "itemId": "q-clean-1",
                "partId": "p-clean",
                "partCode": "P-CLEAN",
                "ord": 1,
                "displayNo": 1,
                "questionType": "SINGLE_CHOICE",
                "content": "<p>Câu kiểm tra dọn bộ đệm</p>",
                "body": ["options": [["id": "o1", "content": "<p>A</p>"]]],
                "studentAnswer": ["type": "SINGLE_CHOICE", "optionId": "o1"],
                "expectedAnswer": ["expectedOptionIds": ["o1"]],
                "solution": """
                <figure class="image" data-width="medium" data-zoom="false">
                  <img src="/api/v1/me/attempts/\(self.attemptId)/solution-assets/\(self.solutionAssetId)" alt="Lời giải nhạy cảm">
                </figure>
                """,
                "outcome": "CORRECT",
                "earnedPoints": 1.0,
                "pointsMax": 1.0
            ]]
        }

        let (_, _, transport, preferences) = try SessionTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        )
        let apiClient = MobileAPIClient(
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            transport: transport,
            localeProvider: { await MainActor.run { preferences.locale } }
        )
        let repo = ResultRepository(
            api: OpenAPIResultAPI(apiClient: apiClient),
            catalogOptionsCache: CatalogOptionsCache(apiClient: apiClient)
        )
        let vm = ReviewViewModel(
            attemptId: attemptId,
            repository: repo,
            assetSource: assetSource
        )
        vm.setModelForTesting(ReviewMapper.map(payload))

        let (window, controller, _, _) = ResultViewTestHelper.host(
            ReviewView(
                viewModel: vm,
                locale: .vi,
                onBackToResult: {},
                onGoMyExams: {},
                onGoHistory: {},
                autoLoadOnAppear: false
            ),
            size: CGSize(width: 390, height: 900),
            settleSeconds: 0.12
        )

        let solutionRef = AttemptAssetRef.solution(assetId: solutionAssetId)
        let deadline = Date().addingTimeInterval(3.0)
        while Date() < deadline {
            controller.view.layoutIfNeeded()
            if assetSource.cachedData(for: solutionRef) != nil {
                break
            }
            try await Task.sleep(for: .milliseconds(40))
        }
        XCTAssertNotNil(assetSource.cachedData(for: solutionRef), "Solution asset should be fetched into memory while ReviewView is open")

        let viewerHandle = try await assetSource.openForViewer(solutionRef, fileExtension: "png")
        XCTAssertTrue(FileManager.default.fileExists(atPath: viewerHandle.url.path))

        window.rootViewController = UIViewController()
        window.isHidden = true
        try await Task.sleep(for: .milliseconds(150))

        XCTAssertNil(assetSource.cachedData(for: solutionRef), "Leaving ReviewView must purge solution asset from memory cache")
        XCTAssertFalse(
            FileManager.default.fileExists(atPath: viewerHandle.url.path),
            "Leaving ReviewView must delete temporary viewer files from Caches/AttemptAssets/"
        )

        let leftover = findFilesContaining(solutionSentinel, under: appSandboxDirectories())
        XCTAssertTrue(
            leftover.isEmpty,
            "Library/Caches, Library/Application Support, tmp, and Documents must contain zero solution-asset files, found: \(leftover)"
        )
    }

    private func appSandboxDirectories() -> [URL] {
        let fm = FileManager.default
        var roots: [URL] = [fm.temporaryDirectory]
        let searchPaths: [FileManager.SearchPathDirectory] = [
            .cachesDirectory,
            .applicationSupportDirectory,
            .documentDirectory
        ]
        for path in searchPaths {
            if let url = fm.urls(for: path, in: .userDomainMask).first {
                roots.append(url)
            }
        }
        return roots
    }

    private func findFilesContaining(_ sentinel: Data, under roots: [URL]) -> [URL] {
        var matches: [URL] = []
        let fm = FileManager.default
        for root in roots {
            guard let enumerator = fm.enumerator(
                at: root,
                includingPropertiesForKeys: [.isRegularFileKey],
                options: [.skipsPackageDescendants]
            ) else { continue }
            for case let fileURL as URL in enumerator {
                guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else {
                    continue
                }
                if let data = try? Data(contentsOf: fileURL), data.range(of: sentinel) != nil {
                    matches.append(fileURL)
                }
            }
        }
        return matches
    }
}
