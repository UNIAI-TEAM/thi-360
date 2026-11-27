import Foundation
import XCTest
@testable import Thi360

@MainActor
final class AvatarSourceTests: XCTestCase {
    func testCameraAvailabilityDeniedNotDeterminedCancelAndGalleryWithoutPhotoPermission() async throws {
        let profileData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: profileData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        var openSettingsCount = 0
        let fakeCamera = FakeCameraAccess(isAvailable: false, authorization: .denied, requestResult: false)
        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            cameraAccess: fakeCamera,
            openSettings: { openSettingsCount += 1 }
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()

        XCTAssertFalse(vm.hasCamera)
        vm.requestAvatarSourceSelection()
        XCTAssertTrue(vm.isShowingAvatarSourceDialog)
        vm.cancelMediaPicker()
        XCTAssertFalse(vm.isShowingAvatarSourceDialog)
        XCTAssertEqual(stack.transport.callCount(method: .put, path: "/api/v1/me/avatar"), 0)

        fakeCamera.isAvailable = true
        fakeCamera.authorization = .denied
        XCTAssertTrue(vm.hasCamera)
        await vm.selectCameraSource()
        XCTAssertFalse(vm.isShowingCameraPicker)
        XCTAssertTrue(vm.isShowingCameraDeniedBanner)
        XCTAssertEqual(fakeCamera.requestCallCount, 0)
        XCTAssertEqual(
            ProfileStrings.text("account.profile.avatar.cameraDenied", locale: .vi),
            "Thi360 chưa được dùng máy ảnh. Bật quyền trong Cài đặt để chụp ảnh đại diện."
        )
        XCTAssertEqual(
            ProfileStrings.text("account.profile.avatar.openSettings", locale: .vi),
            "Mở Cài đặt"
        )
        vm.openSystemSettings()
        XCTAssertEqual(openSettingsCount, 1)

        vm.selectGallerySource()
        XCTAssertTrue(vm.isShowingPhotosPicker)
        XCTAssertFalse(vm.isShowingCameraDeniedBanner)
        vm.cancelMediaPicker()
        XCTAssertFalse(vm.isShowingPhotosPicker)
        XCTAssertEqual(stack.transport.callCount(method: .put, path: "/api/v1/me/avatar"), 0)

        fakeCamera.authorization = .notDetermined
        await vm.selectCameraSource()
        XCTAssertEqual(fakeCamera.requestCallCount, 1)
        XCTAssertFalse(vm.isShowingCameraPicker)
        XCTAssertTrue(vm.isShowingCameraDeniedBanner)

        let grantedCamera = FakeCameraAccess(isAvailable: true, authorization: .notDetermined, requestResult: true)
        let accountVM2 = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager,
            cameraAccess: grantedCamera
        )
        let vm2 = accountVM2.ensureProfileViewModel()
        await vm2.selectCameraSource()
        XCTAssertEqual(grantedCamera.requestCallCount, 1)
        XCTAssertTrue(vm2.isShowingCameraPicker)
        XCTAssertFalse(vm2.isShowingCameraDeniedBanner)
        vm2.cancelMediaPicker()
        XCTAssertFalse(vm2.isShowingCameraPicker)

        XCTAssertEqual(
            ProfileStrings.text("account.profile.avatar.hint", locale: .vi),
            "JPG, PNG hoặc WEBP; tối đa 5 MB và 4.096 × 4.096 px. Ảnh được cắt vuông."
        )
    }
}
