import Foundation
import XCTest
@testable import Thi360

final class AccountScopedStoreTests: XCTestCase {
    func testAccountsAreIsolated() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-isolated-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let storeA = try XCTUnwrap(provider.store(for: "account-A"))
        let storeB = try XCTUnwrap(provider.store(for: "account-B"))

        let secretA = Data("account-A-answers".utf8)
        let secretB = Data("account-B-answers".utf8)
        try await storeA.write(secretA, namespace: "attempts", key: "att-1")
        try await storeB.write(secretB, namespace: "attempts", key: "att-1")

        let readByB = await storeB.read(namespace: "attempts", key: "att-1")
        XCTAssertEqual(readByB, secretB)
        XCTAssertNotEqual(readByB, secretA)

        let readMissingByB = await storeB.read(namespace: "attempts", key: "only-in-A")
        XCTAssertNil(readMissingByB)

        try await storeA.write(Data("only-a".utf8), namespace: "drafts", key: "only-in-A")
        let stillNilForB = await storeB.read(namespace: "drafts", key: "only-in-A")
        XCTAssertNil(stillNilForB, "Account B must never read Account A's namespace or keys")

        let tricky = try XCTUnwrap(provider.store(for: "../account-A"))
        let traversalRead = await tricky.read(namespace: "../drafts", key: "../only-in-A")
        XCTAssertNil(traversalRead)
    }

    func testAtomicWriteExcludedFromBackupAndDeleteNamespace() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-atomic-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let store = try AccountScopedStore(accountId: "account-atomic", rootDirectoryURL: rootDir)
        let v1 = Data("version-1-payload".utf8)
        let v2 = Data("version-2-payload-longer".utf8)

        try await store.write(v1, namespace: "drafts", key: "k1")
        let readV1 = await store.read(namespace: "drafts", key: "k1")
        XCTAssertEqual(readV1, v1)

        try await store.write(v2, namespace: "drafts", key: "k1")
        let readV2 = await store.read(namespace: "drafts", key: "k1")
        XCTAssertEqual(readV2, v2)

        let dirURL = await store.accountDirectoryURL
        let dirValues = try dirURL.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(dirValues.isExcludedFromBackup, true, "AccountScopedStore directory must be excluded from backup")

        let fileURL = try await store.fileURLForTesting(namespace: "drafts", key: "k1")
        let fileValues = try fileURL.resourceValues(forKeys: [.isExcludedFromBackupKey])
        XCTAssertEqual(fileValues.isExcludedFromBackup, true, "AccountScopedStore files must be excluded from backup")

        try await store.write(Data("other".utf8), namespace: "other", key: "k2")
        try await store.deleteNamespace("drafts")
        let afterDelete = await store.read(namespace: "drafts", key: "k1")
        XCTAssertNil(afterDelete)
        let untouched = await store.read(namespace: "other", key: "k2")
        XCTAssertEqual(untouched, Data("other".utf8))
    }

    func testNilAccountIdReturnsNilAndNoOpsWrite() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-nil-acc-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        XCTAssertNil(provider.store(for: nil))
        XCTAssertNil(provider.store(for: "   "))
        let wrote = try await provider.write(Data("ignored".utf8), accountId: nil, namespace: "drafts", key: "k1")
        XCTAssertFalse(wrote)
        let read = await provider.read(accountId: nil, namespace: "drafts", key: "k1")
        XCTAssertNil(read)
    }

    func testConcurrentWriteAndWipe() async throws {
        let rootDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-concurrent-\(UUID().uuidString)", isDirectory: true)
        defer { try? FileManager.default.removeItem(at: rootDir) }

        let provider = AccountScopedStoreProvider(rootDirectoryURL: rootDir)
        let storeA = try XCTUnwrap(provider.store(for: "account-race-A"))

        try await withThrowingTaskGroup(of: Void.self) { group in
            group.addTask {
                for i in 0..<40 {
                    try? await storeA.write(Data("writer1-\(i)".utf8), namespace: "drafts", key: "shared")
                }
            }
            group.addTask {
                for i in 0..<40 {
                    try? await storeA.write(Data("writer2-\(i)".utf8), namespace: "drafts", key: "shared")
                }
            }
            group.addTask {
                try? await Task.sleep(nanoseconds: 5_000_000)
                try await provider.wipeAccount("account-race-A")
            }
            try await group.waitForAll()
        }

        XCTAssertFalse(
            provider.accountDirectoryExists(for: "account-race-A"),
            "No files for account A may remain on disk after wipeAccount races with concurrent writes"
        )
    }
}
