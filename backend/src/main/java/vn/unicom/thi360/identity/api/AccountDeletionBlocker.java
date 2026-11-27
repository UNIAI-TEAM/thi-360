package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Điều kiện chặn học viên tự xóa tài khoản")
public enum AccountDeletionBlocker {
    ATTEMPT_IN_PROGRESS,
    ORDER_BEING_CHECKED,
    ORDER_PENDING_PAYMENT
}
