package vn.unicom.thi360.identity.application;

import vn.unicom.thi360.shared.api.BusinessRuleException;

/** Đổi kết quả kiểm mã thành lỗi API (TM 5.3): 422 {@code EMAIL_CODE_INVALID} hoặc {@code EMAIL_CODE_EXPIRED}. */
final class EmailCodeResults {

    private EmailCodeResults() {
    }

    static void throwUnlessAccepted(EmailCodeService.VerifyOutcome outcome) {
        if (outcome instanceof EmailCodeService.VerifyOutcome.Wrong wrong) {
            throw new EmailCodeInvalidException(wrong.remainingAttempts());
        }
        if (outcome instanceof EmailCodeService.VerifyOutcome.Expired) {
            throw new BusinessRuleException("EMAIL_CODE_EXPIRED");
        }
    }
}
