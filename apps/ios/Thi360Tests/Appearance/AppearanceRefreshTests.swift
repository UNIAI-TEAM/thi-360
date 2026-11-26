import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AppearanceRefreshTests: XCTestCase {
    func testVersionChangeAppliesOnLaunchAndAfterSixtySecondForegroundThrottleAndRespects304() async throws {
        let greenPayload = try AlternatePaletteFixtures.contractGreenPayload()
        let oceanPayload = try AlternatePaletteFixtures.oceanBluePayload(version: 2, mode: .light)

        let clock = ManualClock()
        let cache = InMemoryAppearanceCacheStore()
        let fetcher = MockAppearanceFetcher { _ in
            .updated(greenPayload, etag: "\"0\"")
        }

        let repo = AppearanceRepository(
            fetcher: fetcher,
            cacheStore: cache,
            clock: clock
        )

        // 1. Launch fetches initial version 0.
        let launchResult = await repo.refreshOnLaunch()
        XCTAssertEqual(launchResult, .updated(greenPayload, etag: "\"0\""))
        XCTAssertEqual(fetcher.callCount, 1)
        XCTAssertEqual(repo.currentVersion, 0)
        XCTAssertEqual(repo.current.presetId, "thi360-green")

        // 2. Foreground return after 59 seconds (< 60s throttle) must NOT hit network.
        clock.advance(by: 59)
        let throttled = await repo.refreshOnForegroundIfNeeded()
        XCTAssertNil(throttled, "Foreground return before 60s must be throttled")
        XCTAssertEqual(fetcher.callCount, 1)

        // 3. Foreground return at 60 seconds sends If-None-Match and receives 304 -> no change.
        fetcher.setHandler { ifNoneMatch in
            XCTAssertEqual(ifNoneMatch, "\"0\"")
            return .notModified
        }
        clock.advance(by: 1)
        let notModifiedResult = await repo.refreshOnForegroundIfNeeded()
        XCTAssertEqual(notModifiedResult, .notModified)
        XCTAssertEqual(fetcher.callCount, 2)
        XCTAssertEqual(repo.currentVersion, 0)
        XCTAssertEqual(repo.current.presetId, "thi360-green")

        // 4. Foreground return after another 60 seconds receives version 2 -> applies immediately.
        fetcher.setHandler { ifNoneMatch in
            XCTAssertEqual(ifNoneMatch, "\"0\"")
            return .updated(oceanPayload, etag: "\"2\"")
        }
        clock.advance(by: 60)
        let updatedResult = await repo.refreshOnForegroundIfNeeded()
        XCTAssertEqual(updatedResult, .updated(oceanPayload, etag: "\"2\""))
        XCTAssertEqual(fetcher.callCount, 3)
        XCTAssertEqual(repo.currentVersion, 2)
        XCTAssertEqual(repo.current.presetId, "ocean-blue")
        XCTAssertEqual(repo.current.light.primaryFill, "#1d4ed8")
        XCTAssertEqual(cache.read()?.version, 2)
        XCTAssertEqual(cache.read()?.etag, "\"2\"")

        // 5. Subsequent foreground check after 60s sends new ETag "2".
        fetcher.setHandler { ifNoneMatch in
            XCTAssertEqual(ifNoneMatch, "\"2\"")
            return .notModified
        }
        clock.advance(by: 60)
        _ = await repo.refreshOnForegroundIfNeeded()
        XCTAssertEqual(fetcher.callCount, 4)
        XCTAssertEqual(fetcher.recordedIfNoneMatchHeaders.last ?? nil, "\"2\"")
    }
}
