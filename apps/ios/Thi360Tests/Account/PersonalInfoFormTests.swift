import Foundation
import SwiftUI
import UIKit
import XCTest
@testable import Thi360

@MainActor
final class PersonalInfoFormTests: XCTestCase {
    func testPersonalInfoFormDirtyStatePatchOmissionClearLegacyPhoneDiscardDoubleSubmitAndOverwrite() async throws {
        let legacyProfileData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["phone"] = "+84 912 345 678"
        }
        let savedNameOnlyData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["fullName"] = "Nguyễn Văn Bình"
            dict["phone"] = "+84 912 345 678"
        }
        let clearedPhoneData = try ProfileFixtureHelper.mutateJSON("me-profile-200.json") { dict in
            dict["fullName"] = "Nguyễn Văn Bình"
            dict["phone"] = NSNull()
        }

        let saveGate = TestAsyncGate()
        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: legacyProfileData)
            transport.register(method: .patch, path: "/api/v1/me/profile") { _ in
                await saveGate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: savedNameOnlyData)
            }
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()
        vm.syncFromStoreIfChanged()

        XCTAssertEqual(vm.fullName, "Nguyễn Văn An")
        XCTAssertEqual(vm.phone, "+84 912 345 678")
        XCTAssertFalse(vm.isInfoDirty, "Save and Discard must be disabled when unchanged")

        let view = NavigationStack {
            ProfileView(viewModel: vm, preferences: stack.preferences)
        }
        .environment(\.brandPalette, .defaultLight)
        .environment(\.statusPalette, .light)
        let (window, controller) = RecoveryViewTestHelper.host(
            view,
            size: CGSize(width: 390, height: 1400),
            sizeClass: .compact,
            themeMode: .light
        )
        let textFields = RecoveryViewTestHelper.findViews(ofType: UITextField.self, in: controller.view)
        XCTAssertTrue(textFields.contains { $0.keyboardType == .phonePad }, "Phone field must use phonePad keyboard")
        XCTAssertTrue(textFields.contains { $0.placeholder == "Ví dụ 0912345678" })
        window.isHidden = true

        vm.updateFullName("  Nguyễn Văn Bình  ")
        XCTAssertTrue(vm.isInfoDirty)

        vm.discardInfoChanges()
        XCTAssertEqual(vm.fullName, "Nguyễn Văn An")
        XCTAssertFalse(vm.isInfoDirty)

        vm.updateFullName("  Nguyễn Văn Bình  ")
        let firstSave = Task { @MainActor in await vm.savePersonalInfo(locale: .vi) }
        await saveGate.waitUntilEntered()
        XCTAssertTrue(vm.isSavingInfo)
        let secondSave = Task { @MainActor in await vm.savePersonalInfo(locale: .vi) }
        await saveGate.release()
        await firstSave.value
        await secondSave.value

        let patches = stack.transport.requests(method: .patch, path: "/api/v1/me/profile")
        XCTAssertEqual(patches.count, 1, "Double-tap while saving must send strictly one PATCH")
        let firstBody = try XCTUnwrap(patches.first?.bodyData)
        let firstJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: firstBody) as? [String: Any])
        XCTAssertEqual(firstJSON["fullName"] as? String, "Nguyễn Văn Bình")
        XCTAssertNil(firstJSON["phone"], "Unchanged legacy phone must be omitted from PATCH payload")
        XCTAssertEqual(vm.activeToast?.message, "Đã lưu thông tin cá nhân.")
        XCTAssertEqual(vm.phone, "+84 912 345 678", "Legacy phone stays preserved when only fullName edited")
        XCTAssertFalse(vm.isInfoDirty, "Buttons must lock again after save succeeds")

        stack.transport.registerJSON(method: .patch, path: "/api/v1/me/profile", statusCode: 200, data: clearedPhoneData)
        vm.updatePhone("")
        XCTAssertTrue(vm.isInfoDirty)
        await vm.savePersonalInfo(locale: .vi)

        let allPatches = stack.transport.requests(method: .patch, path: "/api/v1/me/profile")
        XCTAssertEqual(allPatches.count, 2)
        let secondBody = try XCTUnwrap(allPatches[1].bodyData)
        let secondJSON = try XCTUnwrap(JSONSerialization.jsonObject(with: secondBody) as? [String: Any])
        XCTAssertEqual(secondJSON["phone"] as? String, "", "Clearing phone must send phone: \"\"")
        XCTAssertEqual(vm.phone, "")
        XCTAssertFalse(vm.profile?.hasPhone ?? true)
        XCTAssertEqual(ProfileStrings.text("account.profile.info.phoneEmpty", locale: .vi), "Bạn chưa nhập số điện thoại.")
    }

    func testRefusals() async throws {
        let initialData = try ContractFixtureLoader.loadApiFixture("me-profile-200.json")
        let phoneErrorData = try ContractFixtureLoader.loadApiFixture("me-profile-update-400-phone.json")
        let nameSizeErrorData = try ProfileFixtureHelper.mutateJSON("me-profile-update-400-phone.json") { dict in
            dict["fieldErrors"] = [[
                "field": "fullName",
                "code": "SIZE",
                "message": "Họ và tên tối đa 200 ký tự"
            ]]
        }

        let stack = AccountTestFactory.makeContractStack(
            initialTokens: AccountTestFactory.sampleTokens()
        ) { transport in
            transport.registerJSON(method: .get, path: "/api/v1/me/profile", statusCode: 200, data: initialData)
        }
        defer { UserDefaults(suiteName: stack.defaultsSuiteName)?.removePersistentDomain(forName: stack.defaultsSuiteName) }

        let accountVM = AccountViewModel(
            sessionStore: stack.sessionStore,
            biometricManager: stack.biometricManager
        )
        await accountVM.loadProfile()
        let vm = accountVM.ensureProfileViewModel()
        vm.syncFromStoreIfChanged()

        vm.updateFullName("    ")
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertEqual(vm.fullNameError, .required)
        XCTAssertEqual(ProfileStrings.text("account.profile.info.fullNameRequired", locale: .vi), "Vui lòng nhập họ và tên.")
        XCTAssertEqual(stack.transport.callCount(method: .patch, path: "/api/v1/me/profile"), 0)

        vm.updateFullName(String(repeating: "A", count: 201))
        XCTAssertEqual(vm.fullName.count, 200)

        stack.transport.registerJSON(method: .patch, path: "/api/v1/me/profile", statusCode: 400, data: nameSizeErrorData)
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertEqual(vm.fullNameError, .tooLong)
        XCTAssertEqual(ProfileStrings.text("account.profile.info.fullNameTooLong", locale: .vi), "Họ và tên tối đa 200 ký tự.")

        vm.updateFullName("Nguyễn Văn An")
        vm.updatePhone("912345678")
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertEqual(vm.phoneError, .invalid)
        XCTAssertEqual(ProfileStrings.text("account.profile.info.phoneInvalid", locale: .vi), "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0.")
        XCTAssertEqual(stack.transport.callCount(method: .patch, path: "/api/v1/me/profile"), 1)

        stack.transport.registerJSON(method: .patch, path: "/api/v1/me/profile", statusCode: 400, data: phoneErrorData)
        vm.updatePhone("0912345678")
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertEqual(vm.phoneError, .invalid)
        XCTAssertEqual(stack.transport.callCount(method: .patch, path: "/api/v1/me/profile"), 2)

        stack.transport.registerEmpty(method: .patch, path: "/api/v1/me/profile", statusCode: 500)
        vm.updateFullName("Giữ Giá Trị Gõ")
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertTrue(vm.infoGenericError)
        XCTAssertEqual(vm.fullName, "Giữ Giá Trị Gõ")
        XCTAssertEqual(vm.phone, "0912345678")

        stack.transport.registerNetworkError(method: .patch, path: "/api/v1/me/profile", code: .notConnectedToInternet)
        await vm.savePersonalInfo(locale: .vi)
        XCTAssertTrue(vm.infoGenericError)
        XCTAssertEqual(vm.fullName, "Giữ Giá Trị Gõ")
    }
}
