package vn.unicom.thi360.identity.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả xử lý từng tài khoản trong lô phân quyền")
public enum BulkAuthzOutcome {
    WILL_CHANGE,
    NO_CHANGE,
    SKIPPED
}
