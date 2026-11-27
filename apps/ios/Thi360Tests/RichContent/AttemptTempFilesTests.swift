import Foundation
import XCTest
@testable import Thi360

final class AttemptTempFilesTests: XCTestCase {
    private let attemptId = "22222222-2222-4222-8222-222222222222"
    private let assetId = "9b8f8b1f-9b8f-4b8f-9b8f-9b8f8b8f8b8f"
    private let pdfSentinel = Data("%PDF-1.4 THI360-ATTEMPT-SENTINEL-BANG-SO-LIEU".utf8)
    private let imageSentinel = Data("THI360-ATTEMPT-IMAGE-SENTINEL-PAYLOAD".utf8)

    override func tearDown() {
        RecordingAssetURLProtocol.reset()
        super.tearDown()
    }

    func testImageLoadWritesZeroDiskFilesAndViewerTempFileCleanupLifecycle() async throws {
        let sandboxRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-temp-sandbox-\(UUID().uuidString)", isDirectory: true)
        let fakeCaches = sandboxRoot.appendingPathComponent("Caches", isDirectory: true)
        try FileManager.default.createDirectory(at: fakeCaches, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: sandboxRoot) }

        RecordingAssetURLProtocol.reset()
        RecordingAssetURLProtocol.handler = { req in
            let body = req.url?.path.contains("pdf-asset") == true ? self.pdfSentinel : self.imageSentinel
            let response = HTTPURLResponse(
                url: req.url!,
                statusCode: 200,
                httpVersion: "HTTP/1.1",
                headerFields: ["Content-Type": "application/octet-stream"]
            )!
            return (response, body)
        }

        let tokenStore = InMemoryTokenStore(
            initialTokens: SessionTokens(
                accessToken: "temp-token",
                refreshToken: "temp-refresh",
                accessExpiresIn: 3600,
                refreshExpiresIn: 86_400,
                mustChangePassword: false,
                username: "student1",
                accountId: "acc-temp"
            )
        )
        let refresher = TokenRefresher(
            tokenStore: tokenStore,
            refreshExecutor: { _ in throw MobileApiError.unexpectedResponse(statusCode: 401) }
        )
        let session = AttemptAssetSource.makeNoDiskCacheSession(
            protocolClasses: [RecordingAssetURLProtocol.self]
        )
        let source = AttemptAssetSource(
            attemptId: attemptId,
            serverURL: URL(string: "https://api.thi360.test")!,
            tokenStore: tokenStore,
            refresher: refresher,
            session: session,
            cachesDirectoryURL: fakeCaches
        )

        let loadedImage = try await source.load(.attempt(assetId: assetId))
        XCTAssertEqual(loadedImage, imageSentinel)

        let loadedSolution = try await source.load(.solution(assetId: assetId))
        XCTAssertEqual(loadedSolution, imageSentinel)

        XCTAssertTrue(
            findFilesContaining(imageSentinel, under: sandboxRoot).isEmpty,
            "Loading images must never write files to disk"
        )
        XCTAssertTrue(
            findFilesContaining(imageSentinel, under: appSandboxDirectories()).isEmpty,
            "App Documents/Library/Caches/tmp must contain zero attempt image files"
        )

        let tempFile = try await source.openForViewer(.attempt(assetId: "pdf-asset"), fileExtension: "pdf")
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile.url.path))
        XCTAssertTrue(
            tempFile.url.path.hasPrefix(fakeCaches.appendingPathComponent("AttemptAssets").path),
            "Temp file must live under Caches/AttemptAssets/"
        )
        XCTAssertFalse(
            tempFile.url.lastPathComponent.contains("bang-so-lieu"),
            "Temp filename must be random UUID, not user/original filename"
        )

        let resourceValues = try tempFile.url.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(resourceValues.isExcludedFromBackup, true, "Temp file must be excluded from backup")

        XCTAssertTrue(
            AttemptAssetSource.tempFileWriteOptions.contains(.completeFileProtection),
            "Temp viewer file writes must request .completeFileProtection"
        )
        XCTAssertEqual(
            AttemptAssetSource.tempFileProtection,
            .complete,
            "Temp viewer file protection must be FileProtectionType.complete"
        )
        let attrs = try FileManager.default.attributesOfItem(atPath: tempFile.url.path)
        if let storedProtection = attrs[.protectionKey] as? FileProtectionType {
            XCTAssertEqual(storedProtection, .complete, "Stored protection attribute must be FileProtectionType.complete")
        }

        tempFile.close()
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile.url.path), "close() must delete temp file immediately")
        XCTAssertTrue(findFilesContaining(pdfSentinel, under: sandboxRoot).isEmpty)

        let tempFile2 = try await source.openForViewer(.attempt(assetId: "pdf-asset"), fileExtension: "pdf")
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile2.url.path))
        source.purgeAll()
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile2.url.path), "purgeAll() on leaving screen must delete temp files")
        XCTAssertNil(source.cachedData(for: .attempt(assetId: assetId)), "purgeAll() must clear memory cache")
        XCTAssertTrue(findFilesContaining(pdfSentinel, under: sandboxRoot).isEmpty)

        let tempFile3 = try await source.openForViewer(.attempt(assetId: "pdf-asset"), fileExtension: "pdf")
        XCTAssertTrue(FileManager.default.fileExists(atPath: tempFile3.url.path))
        let storeRoot = sandboxRoot.appendingPathComponent("AccountStore", isDirectory: true)
        let cleanup = SessionCleanup(storeProvider: AccountScopedStoreProvider(rootDirectoryURL: storeRoot))
        await cleanup.register(AttemptAssetTempCleanupParticipant(cachesDirectoryURL: fakeCaches))
        await cleanup.runLogout(accountId: "acc-temp")
        XCTAssertFalse(FileManager.default.fileExists(atPath: tempFile3.url.path), "SessionCleanup.runLogout must delete temp files")

        let orphanDir = fakeCaches.appendingPathComponent("AttemptAssets", isDirectory: true)
        try FileManager.default.createDirectory(at: orphanDir, withIntermediateDirectories: true)
        let orphanFile = orphanDir.appendingPathComponent("\(UUID().uuidString).pdf")
        try pdfSentinel.write(to: orphanFile)
        XCTAssertTrue(FileManager.default.fileExists(atPath: orphanFile.path))

        await cleanup.runAppStart()
        XCTAssertFalse(FileManager.default.fileExists(atPath: orphanFile.path), "SessionCleanup.runAppStart must delete orphaned temp files before first screen")
        XCTAssertTrue(findFilesContaining(pdfSentinel, under: sandboxRoot).isEmpty)
    }

    private func appSandboxDirectories() -> [URL] {
        let fm = FileManager.default
        var dirs: [URL] = [fm.temporaryDirectory]
        for search in [FileManager.SearchPathDirectory.documentDirectory, .libraryDirectory, .cachesDirectory] {
            if let url = fm.urls(for: search, in: .userDomainMask).first {
                dirs.append(url)
            }
        }
        return dirs
    }

    private func findFilesContaining(_ sentinel: Data, under root: URL) -> [URL] {
        findFilesContaining(sentinel, under: [root])
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
                guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else { continue }
                if let data = try? Data(contentsOf: fileURL), data.range(of: sentinel) != nil {
                    matches.append(fileURL)
                }
            }
        }
        return matches
    }
}
