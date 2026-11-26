import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class RegisterFlowTests: XCTestCase {
    func testSubmitGoesToOtp() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let gate = TestAsyncGate()

        let (sessionStore, _, transport, preferences) = try SessionTestFactory.makeContractStack(locale: .en) { transport in
            transport.register(method: .post, path: "/api/v1/auth/register") { _ in
                await gate.enterAndWait()
                return ContractFakeTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
        }

        let legalDir = FileManager.default.temporaryDirectory
            .appendingPathComponent("thi360-reg-legal-\(UUID().uuidString)", isDirectory: true)
        try FileManager.default.createDirectory(at: legalDir, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: legalDir) }
        let sampleTerms = """
        {"id":"terms","locale":"vi","version":"2026-11-26","status":"published","effectiveDate":"2026-11-26","title":"Điều kiện giao dịch chung","note":null,"blocks":[{"type":"paragraph","text":"Nội dung"}]}
        """
        try Data(sampleTerms.utf8).write(to: legalDir.appendingPathComponent("terms.vi.json"))
        let legalRepo = LegalRepository(directoryOverride: legalDir)

        let clock = ManualClock()
        let viewModel = RegisterViewModel(sessionStore: sessionStore, legalRepository: legalRepo, clock: clock)
        viewModel.fullName = "Tran Minh Anh"
        viewModel.username = "minhanh.tran"
        viewModel.email = "student.demo@thi360.vn"
        viewModel.password = "StrongPass1!"
        viewModel.acceptTerms = true

        let first = Task { @MainActor in
            await viewModel.submitRegistration()
        }
        await gate.waitUntilEntered()
        XCTAssertTrue(viewModel.isSubmitting, "Register form must lock during in-flight submit")

        let second = Task { @MainActor in
            await viewModel.submitRegistration()
        }
        await second.value
        XCTAssertEqual(
            transport.callCount(method: .post, path: "/api/v1/auth/register"),
            1,
            "Rapid duplicate submit must send only one request"
        )

        await gate.release()
        await first.value

        XCTAssertFalse(viewModel.isSubmitting)
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/register"), 1)

        let req = try XCTUnwrap(transport.requests(method: .post, path: "/api/v1/auth/register").first)
        XCTAssertEqual(req.method, .post)
        XCTAssertTrue(req.path.hasSuffix("/api/v1/auth/register"))
        XCTAssertEqual(req.header(.acceptLanguage), "en")

        let bodyData = try XCTUnwrap(req.bodyData)
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: bodyData) as? [String: Any])
        XCTAssertEqual(json["termsVersion"] as? String, legalRepo.termsVersion(), "termsVersion must come from LegalRepository")
        XCTAssertEqual(json["acceptTerms"] as? Bool, true)
        XCTAssertEqual(json["username"] as? String, "minhanh.tran")
        XCTAssertEqual(json["fullName"] as? String, "Tran Minh Anh")
        XCTAssertEqual(json["email"] as? String, "student.demo@thi360.vn")

        guard case let .otp(registrationId, email, resendAt, _) = viewModel.state else {
            XCTFail("Expected RegistrationState.otp, got \(viewModel.state)")
            return
        }
        XCTAssertEqual(registrationId, "019320a0-0000-7000-8000-000000000002")
        XCTAssertEqual(email, "student.demo@thi360.vn")
        XCTAssertEqual(Int(resendAt.timeIntervalSince(clock.now)), 60)
        XCTAssertEqual(viewModel.password, "", "Password must be cleared after moving to OTP step")

        preferences.locale = .vi
        let (viSession, _, viTransport, _) = try SessionTestFactory.makeContractStack(locale: .vi) { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/register", statusCode: 200, data: registerFixture)
        }
        let viVM = RegisterViewModel(sessionStore: viSession, legalRepository: legalRepo, clock: clock)
        viVM.fullName = "Tran Minh Anh"
        viVM.username = "minhanh.vi"
        viVM.email = "student.demo@thi360.vn"
        viVM.password = "StrongPass1!"
        viVM.acceptTerms = true
        await viVM.submitRegistration()
        XCTAssertEqual(viTransport.requests(method: .post, path: "/api/v1/auth/register").first?.header(.acceptLanguage), "vi")
    }

    func testBackKeepsData() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/register", statusCode: 200, data: registerFixture)
        }

        let clock = ManualClock()
        let viewModel = RegisterViewModel(sessionStore: sessionStore, clock: clock)
        viewModel.fullName = "Trần Minh Anh"
        viewModel.username = "minhanh.tran"
        viewModel.email = "student.demo@thi360.vn"
        viewModel.password = "StrongPass1!"
        viewModel.acceptTerms = true

        await viewModel.submitRegistration()
        guard case .otp = viewModel.state else {
            XCTFail("Expected OTP step")
            return
        }

        viewModel.backToEdit()

        guard case let .form(data, pendingId) = viewModel.state else {
            XCTFail("Expected return to form state")
            return
        }
        XCTAssertEqual(pendingId, "019320a0-0000-7000-8000-000000000002")
        XCTAssertEqual(viewModel.fullName, "Trần Minh Anh")
        XCTAssertEqual(viewModel.username, "minhanh.tran")
        XCTAssertEqual(viewModel.email, "student.demo@thi360.vn")
        XCTAssertTrue(viewModel.acceptTerms)
        XCTAssertEqual(viewModel.password, "", "Back to edit must clear password")
        XCTAssertEqual(data.password, "")

        viewModel.password = "NewStrongPass2!"
        await viewModel.submitRegistration()

        let registerCalls = transport.requests(method: .post, path: "/api/v1/auth/register")
        XCTAssertEqual(registerCalls.count, 2)
        let secondBody = try XCTUnwrap(registerCalls[1].bodyData)
        let secondJSON = try XCTUnwrap(try JSONSerialization.jsonObject(with: secondBody) as? [String: Any])
        XCTAssertEqual(
            secondJSON["registrationId"] as? String,
            "019320a0-0000-7000-8000-000000000002",
            "Resubmitting after editing details must pass pending registrationId"
        )
    }

    func testTermsVersionFromBundledTerms() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let bundledRepo = LegalRepository(bundle: .main)
        let loaded = bundledRepo.load(.terms, locale: .vi)
        guard case let .success(doc) = loaded else {
            XCTFail("Bundled legal/terms.vi.json must exist and decode cleanly, got \(loaded)")
            return
        }

        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/register", statusCode: 200, data: registerFixture)
        }

        let vm = RegisterViewModel(sessionStore: sessionStore, legalRepository: bundledRepo, clock: ManualClock())
        vm.fullName = "Tran Minh Anh"
        vm.username = "minhanh.bundled"
        vm.email = "student.demo@thi360.vn"
        vm.password = "StrongPass1!"
        vm.acceptTerms = true

        await vm.submitRegistration()

        let req = try XCTUnwrap(transport.requests(method: .post, path: "/api/v1/auth/register").first)
        let body = try XCTUnwrap(req.bodyData)
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(json["termsVersion"] as? String, doc.version)
        XCTAssertNotEqual(json["termsVersion"] as? String, "2026-11")
    }

    func testTermsRequiredBlocksSubmit() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let (sessionStore, _, transport, _) = try SessionTestFactory.makeContractStack { transport in
            transport.registerJSON(method: .post, path: "/api/v1/auth/register", statusCode: 200, data: registerFixture)
        }

        let vm = RegisterViewModel(sessionStore: sessionStore, clock: ManualClock())
        vm.fullName = "Tran Minh Anh"
        vm.username = "minhanh.noterms"
        vm.email = "student.demo@thi360.vn"
        vm.password = "StrongPass1!"
        vm.acceptTerms = false

        await vm.submitRegistration()

        XCTAssertEqual(vm.termsError, .termsRequired)
        XCTAssertEqual(
            vm.termsError?.message(locale: .vi),
            "Bạn cần đồng ý với Điều kiện giao dịch chung và Chính sách bảo mật thông tin để đăng ký."
        )
        XCTAssertEqual(transport.callCount(method: .post, path: "/api/v1/auth/register"), 0)
    }
}
