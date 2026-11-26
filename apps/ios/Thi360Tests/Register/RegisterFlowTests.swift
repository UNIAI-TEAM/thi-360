import Foundation
import HTTPTypes
import XCTest
@testable import Thi360

@MainActor
final class RegisterFlowTests: XCTestCase {
    func testSubmitGoesToOtp() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let gate = TestAsyncGate()

        let (sessionStore, _, transport, preferences) = SessionTestFactory.makeStack(locale: .en) { request in
            if request.path.hasSuffix("/api/v1/auth/register") {
                await gate.enterAndWait()
                return MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
            }
            return MockClientTransport.emptyResponse(statusCode: 500)
        }

        let clock = ManualClock()
        let viewModel = RegisterViewModel(sessionStore: sessionStore, clock: clock)
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
        XCTAssertEqual(transport.recordedRequests.count, 1, "Rapid duplicate submit must send only one request")

        await gate.release()
        await first.value

        XCTAssertFalse(viewModel.isSubmitting)
        XCTAssertEqual(transport.recordedRequests.count, 1)

        let req = try XCTUnwrap(transport.recordedRequests.first)
        XCTAssertEqual(req.method, .post)
        XCTAssertTrue(req.path.hasSuffix("/api/v1/auth/register"))
        XCTAssertEqual(req.header(.acceptLanguage), "en")

        let bodyData = try XCTUnwrap(req.bodyData)
        let json = try XCTUnwrap(try JSONSerialization.jsonObject(with: bodyData) as? [String: Any])
        XCTAssertEqual(json["termsVersion"] as? String, "2026-11", "termsVersion must match web-student TERMS_VERSION")
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
        let (viSession, _, viTransport, _) = SessionTestFactory.makeStack(locale: .vi) { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
        }
        let viVM = RegisterViewModel(sessionStore: viSession, clock: clock)
        viVM.fullName = "Tran Minh Anh"
        viVM.username = "minhanh.vi"
        viVM.email = "student.demo@thi360.vn"
        viVM.password = "StrongPass1!"
        viVM.acceptTerms = true
        await viVM.submitRegistration()
        XCTAssertEqual(viTransport.recordedRequests.first?.header(.acceptLanguage), "vi")
    }

    func testBackKeepsData() async throws {
        let registerFixture = try ContractFixtureLoader.loadApiFixture("register-200.json")
        let (sessionStore, _, transport, _) = SessionTestFactory.makeStack { _ in
            MockClientTransport.jsonResponse(statusCode: 200, data: registerFixture)
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

        XCTAssertEqual(transport.recordedRequests.count, 2)
        let secondBody = try XCTUnwrap(transport.recordedRequests[1].bodyData)
        let secondJSON = try XCTUnwrap(try JSONSerialization.jsonObject(with: secondBody) as? [String: Any])
        XCTAssertEqual(
            secondJSON["registrationId"] as? String,
            "019320a0-0000-7000-8000-000000000002",
            "Resubmitting after editing details must pass pending registrationId"
        )
    }
}
