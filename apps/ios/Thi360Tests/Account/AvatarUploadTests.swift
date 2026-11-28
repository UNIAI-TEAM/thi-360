import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AvatarUploadTests: XCTestCase {
    func testUploadMultipartLocksButtonsUpdatesStoreShowsToastAndDeduplicatesConcurrentUpload() async throws {
        let initialData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let uploadedData = try ContractFixtureLoader.loadApiFixture("me-avatar-put-200.json")
        let heicURL = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .appendingPathComponent("Resources/sample-avatar.heic")
        let heicData = try Data(contentsOf: heicURL)

        let uploadGate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: initialData)
            transport.register(method: .put, path: "/api/v1/me/avatar") { _ in
                await uploadGate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: uploadedData)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()

        let firstUpload = Task { @MainActor in
            await vm.handleSelectedImageData(heicData, locale: .vi)
        }
        await uploadGate.waitUntilEntered()
        XCTAssertTrue(vm.isUploadingAvatar)
        XCTAssertTrue(vm.isAvatarBusy, "Upload and remove buttons must lock while uploading")

        let secondUpload = Task { @MainActor in
            await vm.handleSelectedImageData(heicData, locale: .vi)
        }
        await uploadGate.release()
        await firstUpload.value
        await secondUpload.value

        let requests = stack.transport.requests(method: .put, path: "/api/v1/me/avatar")
        XCTAssertEqual(requests.count, 1, "Concurrent upload must emit strictly one PUT /api/v1/me/avatar")
        let contentType = try XCTUnwrap(requests.first?.header(.contentType))
        XCTAssertTrue(contentType.hasPrefix("multipart/form-data; boundary="))
        let bodyData = try XCTUnwrap(requests.first?.bodyData)
        let headerPrefix = String(decoding: bodyData.prefix(256), as: UTF8.self)
        XCTAssertTrue(headerPrefix.contains("name=\"file\""), "Multipart form must use part name 'file'")
        XCTAssertTrue(headerPrefix.contains("Content-Type: image/jpeg"), "HEIC upload must be sent as image/jpeg")
        XCTAssertFalse(headerPrefix.contains("image/heic"))

        XCTAssertEqual(vm.activeToast?.message, "Đã cập nhật ảnh đại diện.")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, "/api/v1/avatars/00000000-0000-7000-8000-000000000001")
    }

    func testUploadBetween1MiBAnd5MiBSucceedsThroughAuthenticatedMiddleware() async throws {
        let uploadedData = try ContractFixtureLoader.loadApiFixture("me-avatar-put-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 200, data: uploadedData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let service = OpenAPIProfileService(apiClient: stack.sessionStore.apiClient)
        let payload = Data(repeating: 0xAB, count: 1_800_000)
        let result = await service.uploadAvatar(PreparedAvatar(data: payload, mimeType: "image/jpeg"))
        switch result {
        case let .success(profile):
            XCTAssertEqual(profile.avatarUrl, "/api/v1/avatars/00000000-0000-7000-8000-000000000001")
        case let .failure(error):
            XCTFail("Valid 1.8 MiB avatar upload (<= 5 MiB) must succeed, got \(error)")
        }
        XCTAssertEqual(stack.transport.callCount(method: .put, path: "/api/v1/me/avatar"), 1)
    }

    func testErrorMatrix() async throws {
        let initialWithAvatar = try ContractFixtureLoader.loadApiFixture("me-avatar-put-200.json")
        let tooLarge = try ContractFixtureLoader.loadApiFixture("me-avatar-put-413-avatar-too-large.json")
        let uploadTooLarge = try ContractFixtureLoader.loadApiFixture("me-avatar-put-413-upload-too-large.json")
        let dimensions = try ContractFixtureLoader.loadApiFixture("me-avatar-put-422-avatar-dimensions-exceeded.json")
        let invalidImage = try ContractFixtureLoader.loadApiFixture("me-avatar-put-422-avatar-invalid-image.json")
        let validPNG = try ProfileFixtureHelper.makeSamplePNG()

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: initialWithAvatar)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()
        let originalAvatarURL = accountVM.loadedProfile?.avatarUrl

        await vm.handleSelectedImageData(Data("corrupt".utf8), locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.INVALID_IMAGE")
        XCTAssertEqual(
            ProfileStrings.text(try XCTUnwrap(vm.avatarErrorKey), locale: .vi),
            "Không đọc được ảnh. Hãy chọn tệp JPG, PNG hoặc WEBP hợp lệ."
        )
        XCTAssertEqual(stack.transport.callCount(method: .put, path: "/api/v1/me/avatar"), 0)
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 413, data: tooLarge)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.TOO_LARGE")
        XCTAssertEqual(ProfileStrings.text("account.profile.avatar.errors.TOO_LARGE", locale: .vi), "Ảnh lớn hơn 5 MB. Hãy chọn ảnh nhỏ hơn.")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 413, data: uploadTooLarge)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.TOO_LARGE")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 422, data: dimensions)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.DIMENSIONS_EXCEEDED")
        XCTAssertEqual(ProfileStrings.text("account.profile.avatar.errors.DIMENSIONS_EXCEEDED", locale: .vi), "Ảnh lớn hơn 4.096 × 4.096 px. Hãy chọn ảnh nhỏ hơn.")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerJSON(method: .put, path: "/api/v1/me/avatar", statusCode: 422, data: invalidImage)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.INVALID_IMAGE")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerEmpty(method: .put, path: "/api/v1/me/avatar", statusCode: 500)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.generic")
        XCTAssertEqual(ProfileStrings.text("account.profile.avatar.errors.generic", locale: .vi), "Không cập nhật được ảnh đại diện. Ảnh hiện tại được giữ nguyên.")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)

        stack.transport.registerNetworkError(method: .put, path: "/api/v1/me/avatar", code: .notConnectedToInternet)
        await vm.handleSelectedImageData(validPNG, locale: .vi)
        XCTAssertEqual(vm.avatarErrorKey, "account.profile.avatar.errors.generic")
        XCTAssertEqual(accountVM.loadedProfile?.avatarUrl, originalAvatarURL)
    }
}
